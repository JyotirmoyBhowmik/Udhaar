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
import com.google.firebase.firestore.DocumentSnapshot
import com.jyotirmoyb.udhaar.data.GuestActionV1
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.cloud.GuestWebCloudRepository
import com.jyotirmoyb.udhaar.di.FirebaseModule
import com.jyotirmoyb.udhaar.notification.GuestActionNotificationHelper
import com.jyotirmoyb.udhaar.util.SafeLogger
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Spark Plan Phase 5 (`SP-5`): `GuestActionPollingWorker`.
 *
 * Polls `/guestLinks/{linkId}/actions/{actionId}` for active links owned by the current user:
 *   `firestore.collection("guestLinks").whereEqualTo("ownerUid", uid).limit(50)`
 *
 * Actions are parsed through `GuestActionV1`. Invalid payloads are marked REJECTED with
 * `rejectReason = "invalid_payload"` on Firestore and never applied locally.
 *
 * Staged proposals are deduplicated and recorded in Room without modifying core ledger state
 * until explicitly accepted by the lender.
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

        if (!currentUser.isEmailVerified && !currentUser.isAnonymous) {
            return Result.success()
        }

        val ownerUid = currentUser.uid
        if (ownerUid.isBlank()) return Result.success()

        return try {
            // 1. Process any pending cloud sync retries from local outbox
            processPendingCloudSyncOutbox(applicationContext)

            val firestore = FirebaseModule.getFirestore(applicationContext)
                ?: return Result.success()

            // 2. Query the lender's ACTIVE links (limit 50 as permitted by rules)
            val linksSnapshot = firestore.collection("guestLinks")
                .whereEqualTo("ownerUid", ownerUid)
                .whereEqualTo("status", "ACTIVE")
                .limit(50)
                .get()
                .await()

            val now = System.currentTimeMillis()
            val activeLinks = linksSnapshot.documents.filter { doc ->
                val status = doc.getString("status")
                val expiresAtMs = doc.getLong("expiresAtMs") ?: Long.MAX_VALUE
                status == "ACTIVE" && expiresAtMs > now
            }

            // 3. For each active link, query actions where status == "PENDING"
            for (linkDoc in activeLinks) {
                val actionsSnap = linkDoc.reference.collection("actions")
                    .whereEqualTo("status", "PENDING")
                    .get()
                    .await()

                for (doc in actionsSnap.documents) {
                    val action = GuestActionV1.fromDocument(doc)
                    if (action == null) {
                        // Invalid document: mark REJECTED with rejectReason "invalid_payload"
                        runCatching {
                            doc.reference.update(
                                mapOf(
                                    "status" to "REJECTED",
                                    "rejectReason" to "invalid_payload",
                                    "reviewedAtMs" to System.currentTimeMillis()
                                )
                            ).await()
                        }
                        continue
                    }

                    processSingleAction(
                        context = applicationContext,
                        action = action,
                        ownerUid = ownerUid
                    )
                }
            }

            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_POLL_MS, System.currentTimeMillis()).apply()

            Result.success()
        } catch (e: Exception) {
            SafeLogger.w("GuestActionPollingWorker", "Poll failed: ${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "udhaar_guest_action_polling_periodic"
        const val WORK_NAME_ONE_SHOT = "udhaar_guest_action_polling_oneshot"
        const val PREFS_NAME = "udhaar_guest_action_polling_prefs"
        const val KEY_PROCESSED_ACTION_IDS = "processed_action_ids"
        const val KEY_PENDING_ACTIONS_JSON = "pending_actions_json"
        const val KEY_LAST_POLL_MS = "last_poll_ms"

        suspend fun processSingleAction(
            context: Context,
            action: GuestActionV1,
            ownerUid: String
        ): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val processedIds = prefs.getStringSet(KEY_PROCESSED_ACTION_IDS, emptySet())
                ?.toMutableSet() ?: mutableSetOf()

            val compositeKey = action.compositeKey
            if (processedIds.contains(compositeKey)) return false

            val dao = runCatching {
                UdhaarDatabase.getInstance(context).udhaarDao()
            }.getOrNull() ?: return false

            val allTransactions = dao.getAllTransactionsSnapshot()

            val linkId = action.linkId
            val matchedTx = allTransactions.firstOrNull { tx ->
                (tx.shareToken.isNotBlank() && GuestWebCloudRepository.sha256Hex(tx.shareToken) == linkId)
            } ?: run {
                val firestore = FirebaseModule.getFirestore(context)
                val linkDoc = runCatching {
                    firestore?.collection("guestLinks")?.document(linkId)?.get()?.await()
                }.getOrNull()
                val resolvedRecordId = linkDoc?.getString("recordId")
                allTransactions.firstOrNull { tx ->
                    resolvedRecordId != null && (tx.remoteId == resolvedRecordId || tx.id.toString() == resolvedRecordId)
                }
            }

            if (matchedTx == null) return false

            val updatedTx = when (action) {
                is GuestActionV1.Confirm -> {
                    matchedTx.copy(
                        lastGuestAction = "CONFIRM_PENDING"
                    )
                }
                is GuestActionV1.Dispute -> {
                    matchedTx.copy(
                        disputeReason = action.reason.ifBlank { "Disputed via web link" },
                        lastGuestAction = "DISPUTE"
                    )
                }
                is GuestActionV1.ProposeDate -> {
                    val proposalJson = JSONObject().apply {
                        put("proposedDateMillis", action.proposedDateMs)
                        put("message", action.reason.ifBlank { "Proposed new due date via web link" })
                    }.toString()
                    matchedTx.copy(
                        pendingDateProposal = proposalJson,
                        lastGuestAction = "DATE_PROPOSED"
                    )
                }
                is GuestActionV1.ClaimPayment -> {
                    val claimJson = JSONObject().apply {
                        put("amountPaise", action.amountPaise)
                        put("dateMillis", action.createdAtMs)
                        // [LEGACY-DISABLED START] reason: hardcoded fallback obscured invalid guest action payloads | restore: remove marker if legacy un-typed fallback is ever needed
                        // put("paymentMode", "UPI")
                        // [LEGACY-DISABLED END]
                        put("paymentMode", action.paymentMode.name)
                        put("reference", action.referenceNumber)
                        put("note", action.note)
                        put("hasProof", action.hasProof)
                        put("linkId", action.linkId)
                        put("actionId", action.actionId)
                    }.toString()
                    matchedTx.copy(
                        pendingPaymentClaim = claimJson,
                        lastGuestAction = "PAYMENT_CLAIMED"
                    )
                }
            }

            dao.updateTransaction(updatedTx)

            // Local notification (no FCM server required)
            GuestActionNotificationHelper.showPendingActionNotification(
                context = context,
                action = action,
                partyName = matchedTx.partyName,
                currency = matchedTx.currency,
                transactionId = matchedTx.id
            )

            processedIds.add(compositeKey)
            prefs.edit()
                .putStringSet(KEY_PROCESSED_ACTION_IDS, processedIds)
                .apply()

            return true
        }

        suspend fun processPendingCloudSyncOutbox(context: Context) {
            val db = runCatching { UdhaarDatabase.getInstance(context) }.getOrNull() ?: return
            val outboxDao = db.syncOutboxDao()
            val firestore = FirebaseModule.getFirestore(context) ?: return

            val pendingSyncs = runCatching {
                outboxDao.getPendingMutations().filter { it.entity == "guest_action_sync" }
            }.getOrDefault(emptyList())

            for (entry in pendingSyncs) {
                try {
                    val payload = JSONObject(entry.payloadJson)
                    val linkId = payload.getString("linkId")
                    val actionId = payload.getString("actionId")
                    val paidPaise = payload.getLong("paidPaise")
                    val payableAmountPaise = payload.getLong("payableAmountPaise")

                    // Update action status=ACCEPTED
                    runCatching {
                        firestore.collection("guestLinks").document(linkId)
                            .collection("actions").document(actionId)
                            .update(
                                mapOf(
                                    "status" to "ACCEPTED",
                                    "reviewedAtMs" to System.currentTimeMillis()
                                )
                            ).await()
                    }

                    // Update link balances
                    firestore.collection("guestLinks").document(linkId)
                        .update(
                            mapOf(
                                "paidPaise" to paidPaise,
                                "payableAmountPaise" to payableAmountPaise,
                                "updatedAtMs" to System.currentTimeMillis()
                            )
                        ).await()

                    // Cleanup: delete action doc and proof doc to free quota
                    runCatching {
                        firestore.collection("guestLinks").document(linkId)
                            .collection("actions").document(actionId)
                            .collection("proof").document("image")
                            .delete().await()
                    }
                    runCatching {
                        firestore.collection("guestLinks").document(linkId)
                            .collection("actions").document(actionId)
                            .delete().await()
                    }

                    outboxDao.deleteById(entry.id)
                } catch (e: Exception) {
                    outboxDao.update(
                        entry.copy(
                            attempts = entry.attempts + 1,
                            status = "FAILED",
                            nextRetryAtMs = System.currentTimeMillis() + 15 * 60 * 1000L
                        )
                    )
                }
            }
        }

        fun enqueuePeriodicPolling(context: Context) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                // Minimum WorkManager periodic interval is 15 minutes
                val request = PeriodicWorkRequestBuilder<GuestActionPollingWorker>(
                    15, TimeUnit.MINUTES
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
