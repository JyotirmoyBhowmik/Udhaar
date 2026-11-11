package com.jyotirmoyb.udhaar.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.data.cloud.GuestWebCloudRepository
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Spark Plan Phase 5 (`SP-5`): `GuestActionPollingWorker`.
 *
 * Polls `/guestLinks/{linkId}/actions/{actionId}` using a Firestore collection-group query
 * filtered by `ownerUid == currentUser.uid`:
 *   `firestore.collectionGroup("actions").whereEqualTo("ownerUid", uid).get()`
 *
 * Deduplicates discovered actions locally and applies them to the matching local Room
 * `UdhaarTransaction` so the lender sees confirmations, disputes, payment claims, and
 * proposed repayment dates in `UpdatesScreen` and transaction details.
 */
class GuestActionPollingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val currentUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        } ?: return Result.success()

        if (!currentUser.isEmailVerified) {
            return Result.success()
        }

        val ownerUid = currentUser.uid
        if (ownerUid.isBlank()) return Result.success()

        return try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collectionGroup("actions")
                .whereEqualTo("ownerUid", ownerUid)
                .get()
                .await()

            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val processedIds = prefs.getStringSet(KEY_PROCESSED_ACTION_IDS, emptySet())
                ?.toMutableSet() ?: mutableSetOf()
            val existingJson = prefs.getString(KEY_PENDING_ACTIONS_JSON, "[]") ?: "[]"
            val pendingArray = runCatching { JSONArray(existingJson) }.getOrElse { JSONArray() }

            val dao = runCatching {
                UdhaarDatabase.getInstance(applicationContext).udhaarDao()
            }.getOrNull()
            val allTransactions = runCatching {
                dao?.getAllTransactionsSnapshot().orEmpty()
            }.getOrDefault(emptyList())

            var newlyDiscovered = 0
            for (doc in snapshot.documents) {
                val linkId = doc.getString("linkId")
                    ?: doc.reference.parent.parent?.id
                    ?: continue
                val actionId = doc.id
                val compositeKey = "$linkId/$actionId"
                if (processedIds.contains(compositeKey)) continue

                val actionType = doc.getString("actionType")
                    ?: doc.getString("type")
                    ?: inferActionType(actionId)
                val recordIdStr = doc.getString("recordId")
                val reason = doc.getString("reason") ?: doc.getString("note") ?: ""
                val utrLast4 = doc.getString("utrLast4") ?: doc.getString("utrSuffix") ?: ""
                val amountPaise = doc.getLong("amountPaise") ?: doc.getLong("claimedAmountPaise") ?: 0L
                val proposedDateMs = doc.getLong("proposedDateMs") ?: doc.getLong("proposedDueDateMillis") ?: 0L
                val createdAtMs = doc.getLong("createdAtMs") ?: System.currentTimeMillis()

                val actionObj = JSONObject().apply {
                    put("compositeKey", compositeKey)
                    put("linkId", linkId)
                    put("actionId", actionId)
                    put("actionType", actionType)
                    put("ownerUid", ownerUid)
                    put("recordId", recordIdStr ?: "")
                    put("reason", reason)
                    put("utrLast4", utrLast4)
                    put("amountPaise", amountPaise)
                    put("proposedDateMs", proposedDateMs)
                    put("createdAtMs", createdAtMs)
                }
                pendingArray.put(actionObj)
                processedIds.add(compositeKey)
                newlyDiscovered++

                // Match local transaction by shareToken hash, remoteId, or id
                if (dao != null) {
                    val matchedTx = allTransactions.firstOrNull { tx ->
                        (tx.shareToken.isNotBlank() && GuestWebCloudRepository.sha256Hex(tx.shareToken) == linkId) ||
                            (recordIdStr != null && (tx.remoteId == recordIdStr || tx.id.toString() == recordIdStr))
                    } ?: run {
                        val linkDoc = runCatching {
                            firestore.collection("guestLinks").document(linkId).get().await()
                        }.getOrNull()
                        val resolvedRecordId = linkDoc?.getString("recordId")
                        allTransactions.firstOrNull { tx ->
                            resolvedRecordId != null && (tx.remoteId == resolvedRecordId || tx.id.toString() == resolvedRecordId)
                        }
                    }

                    if (matchedTx != null) {
                        val updatedTx = when (actionType.uppercase()) {
                            "CONFIRM" -> matchedTx.copy(
                                verificationStatus = VerificationStatus.CONFIRMED,
                                verifiedAtMillis = createdAtMs,
                                lastGuestAction = "CONFIRMED"
                            )
                            "DISPUTE" -> matchedTx.copy(
                                verificationStatus = VerificationStatus.DISPUTED,
                                disputeReason = reason.ifBlank { "Disputed via web link" },
                                lastGuestAction = "DISPUTED"
                            )
                            "CLAIM_PAYMENT" -> {
                                val claimJson = JSONObject().apply {
                                    put("amountPaise", amountPaise)
                                    put("dateMillis", createdAtMs)
                                    put("paymentMode", "UPI")
                                    put("reference", if (utrLast4.isNotBlank()) "UTR *$utrLast4" else "")
                                    put("note", reason)
                                }.toString()
                                matchedTx.copy(
                                    pendingPaymentClaim = claimJson,
                                    lastGuestAction = "PAYMENT_CLAIMED"
                                )
                            }
                            "PROPOSE_DATE" -> {
                                val proposalJson = JSONObject().apply {
                                    put("proposedDateMillis", proposedDateMs)
                                    put("message", reason.ifBlank { "Proposed new due date via web link" })
                                }.toString()
                                matchedTx.copy(
                                    pendingDateProposal = proposalJson,
                                    lastGuestAction = "DATE_PROPOSED"
                                )
                            }
                            else -> matchedTx
                        }
                        dao.updateTransaction(updatedTx)
                    }
                }
            }

            if (newlyDiscovered > 0) {
                prefs.edit()
                    .putStringSet(KEY_PROCESSED_ACTION_IDS, processedIds)
                    .putString(KEY_PENDING_ACTIONS_JSON, pendingArray.toString())
                    .putLong(KEY_LAST_POLL_MS, System.currentTimeMillis())
                    .apply()
            } else {
                prefs.edit()
                    .putLong(KEY_LAST_POLL_MS, System.currentTimeMillis())
                    .apply()
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun inferActionType(actionId: String): String {
        return when {
            actionId == "confirm" -> "CONFIRM"
            actionId == "dispute" -> "DISPUTE"
            actionId.startsWith("claim_") -> "CLAIM_PAYMENT"
            actionId.startsWith("date_") -> "PROPOSE_DATE"
            else -> "CONFIRM"
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "udhaar_guest_action_polling_periodic"
        const val WORK_NAME_ONE_SHOT = "udhaar_guest_action_polling_oneshot"
        const val PREFS_NAME = "udhaar_guest_action_polling_prefs"
        const val KEY_PROCESSED_ACTION_IDS = "processed_action_ids"
        const val KEY_PENDING_ACTIONS_JSON = "pending_actions_json"
        const val KEY_LAST_POLL_MS = "last_poll_ms"

        fun enqueuePeriodicPolling(context: Context) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<GuestActionPollingWorker>(
                    6, TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }
        }

        fun triggerImmediatePoll(context: Context) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequestBuilder<GuestActionPollingWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME_ONE_SHOT,
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    request
                )
            }
        }

        fun enqueuePeriodic(context: Context) = enqueuePeriodicPolling(context)

        fun enqueueImmediate(context: Context) = triggerImmediatePoll(context)

        fun cancel(context: Context) {
            runCatching {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_ONE_SHOT)
            }
        }
    }
}
