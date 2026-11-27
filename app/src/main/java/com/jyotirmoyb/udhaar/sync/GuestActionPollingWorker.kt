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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.jyotirmoyb.udhaar.data.AuditEventType
import com.jyotirmoyb.udhaar.data.GuestActionV1
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.data.cloud.GuestWebCloudRepository
import com.jyotirmoyb.udhaar.di.FirebaseModule
import com.jyotirmoyb.udhaar.notification.GuestActionNotificationHelper
import com.jyotirmoyb.udhaar.util.AuditTrailHelper
import com.jyotirmoyb.udhaar.util.SafeLogger
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
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
open class GuestActionPollingWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    protected val authOverride: FirebaseAuth? = null,
    protected val firestoreOverride: FirebaseFirestore? = null
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val currentUser = try {
            authOverride?.currentUser ?: FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        } ?: return Result.success()

        if (!currentUser.isEmailVerified && !currentUser.isAnonymous) {
            return Result.success()
        }

        val ownerUid = currentUser.uid
        if (ownerUid.isBlank()) return Result.success()

        return try {
            val firestore = firestoreOverride ?: FirebaseModule.getFirestore(applicationContext)

            // 1. Process any pending cloud sync retries from local outbox
            processPendingCloudSyncOutbox(applicationContext, firestore)

            if (firestore == null) return Result.success()

            // 2. Query the lender's ACTIVE links (limit 50 as permitted by rules)
            val linksSnapshot = withTimeoutOrNull(10000L) {
                firestore.collection("guestLinks")
                    .whereEqualTo("ownerUid", ownerUid)
                    .whereEqualTo("status", "ACTIVE")
                    .limit(50)
                    .get()
                    .await()
            } ?: return Result.success()

            val now = System.currentTimeMillis()
            val activeLinks = linksSnapshot.documents.filter { doc ->
                val status = doc.getString("status")
                val expiresAtMs = doc.getLong("expiresAtMs") ?: Long.MAX_VALUE
                status == "ACTIVE" && expiresAtMs > now
            }

            // 3. For each active link, query actions where status == "PENDING"
            for (linkDoc in activeLinks) {
                val actionsSnap = withTimeoutOrNull(5000L) {
                    linkDoc.reference.collection("actions")
                        .whereEqualTo("status", "PENDING")
                        .get()
                        .await()
                } ?: continue

                for (doc in actionsSnap.documents) {
                    val action = GuestActionV1.fromDocument(doc)
                    if (action == null) {
                        // Invalid document: mark REJECTED with rejectReason "invalid_payload"
                        runCatching {
                            withTimeoutOrNull(5000L) {
                                doc.reference.update(
                                    mapOf(
                                        "status" to "REJECTED",
                                        "rejectReason" to "invalid_payload",
                                        "reviewedAtMs" to System.currentTimeMillis()
                                    )
                                ).await()
                            }
                        }
                        continue
                    }

                    processSingleAction(
                        context = applicationContext,
                        action = action,
                        ownerUid = ownerUid,
                        firestoreOverride = firestore
                    )
                }
            }

            // 4. Also sync two-party shared_records status & counterparty UIDs back to local Room
            val sharedSnap = runCatching {
                withTimeoutOrNull(10000L) {
                    firestore.collection("shared_records")
                        .whereArrayContains("participantUids", ownerUid)
                        .limit(50)
                        .get()
                        .await()
                }
            }.getOrNull()
            if (sharedSnap != null) {
                for (sharedDoc in sharedSnap.documents) {
                    syncSharedRecordSnapshotToLocal(
                        context = applicationContext,
                        ownerUid = ownerUid,
                        doc = sharedDoc
                    )
                }
            }

            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_POLL_MS, System.currentTimeMillis()).apply()

            Result.success()
        } catch (e: Exception) {
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                SafeLogger.w("GuestActionPollingWorker", "Spark quota exhausted during guest action poll. Backing off.")
                return Result.success()
            }
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
            ownerUid: String,
            firestoreOverride: FirebaseFirestore? = null
        ): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val processedIds = prefs.getStringSet(KEY_PROCESSED_ACTION_IDS, emptySet())
                ?.toMutableSet() ?: mutableSetOf()

            val compositeKey = action.compositeKey
            val alreadyProcessed = processedIds.contains(compositeKey)
            if (alreadyProcessed && action !is GuestActionV1.Confirm) return false

            val dao = runCatching { UdhaarDatabase.getInstance(context).udhaarDao() }.getOrNull() ?: return false
            val matchedTx = findMatchedTransactionForAction(context, dao, action.linkId, firestoreOverride) ?: return false

            val alreadyConfirmed = matchedTx.verificationStatus == VerificationStatus.CONFIRMED &&
                matchedTx.lastGuestAction != "CONFIRM_PENDING"
            if (alreadyProcessed && alreadyConfirmed) return false

            val resolvedGuestUid = action.guestUid.trim().takeIf { it.isNotBlank() && it != ownerUid }
            val updatedTx = applyGuestActionToTransaction(matchedTx, action, resolvedGuestUid)
            dao.updateTransaction(updatedTx)

            if (resolvedGuestUid != null) {
                linkPartyCounterpartyUid(dao, matchedTx, resolvedGuestUid)
            }
            if (action is GuestActionV1.Confirm) {
                finalizeConfirmAction(context, dao, matchedTx, action, resolvedGuestUid, firestoreOverride)
            }

            GuestActionNotificationHelper.showPendingActionNotification(
                context = context,
                action = action,
                partyName = matchedTx.partyName,
                currency = matchedTx.currency,
                transactionId = matchedTx.id
            )

            processedIds.add(compositeKey)
            prefs.edit().putStringSet(KEY_PROCESSED_ACTION_IDS, processedIds).apply()
            return true
        }

        private suspend fun findMatchedTransactionForAction(
            context: Context,
            dao: com.jyotirmoyb.udhaar.data.UdhaarDao,
            linkId: String,
            firestoreOverride: FirebaseFirestore?
        ): com.jyotirmoyb.udhaar.data.UdhaarTransaction? {
            val allTransactions = dao.getAllTransactionsSnapshot()
            allTransactions.firstOrNull { tx ->
                tx.shareToken.isNotBlank() && GuestWebCloudRepository.sha256Hex(tx.shareToken) == linkId
            }?.let { return it }

            val firestore = firestoreOverride ?: FirebaseModule.getFirestore(context)
            val linkDoc = runCatching {
                firestore?.collection("guestLinks")?.document(linkId)?.get()?.await()
            }.getOrNull()
            val resolvedRecordId = linkDoc?.getString("recordId") ?: return null
            return allTransactions.firstOrNull { tx ->
                tx.remoteId == resolvedRecordId || tx.id.toString() == resolvedRecordId
            }
        }

        private fun applyGuestActionToTransaction(
            matchedTx: com.jyotirmoyb.udhaar.data.UdhaarTransaction,
            action: GuestActionV1,
            resolvedGuestUid: String?
        ): com.jyotirmoyb.udhaar.data.UdhaarTransaction {
            val effectiveCounterpartyUid = resolvedGuestUid ?: matchedTx.counterpartyUserId
            return when (action) {
                is GuestActionV1.Confirm -> matchedTx.copy(
                    verificationStatus = VerificationStatus.CONFIRMED,
                    verifiedAtMillis = action.createdAtMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    needsMyConfirmation = false,
                    disputeReason = "",
                    counterpartyUserId = effectiveCounterpartyUid,
                    lastGuestAction = "CONFIRMED"
                )
                is GuestActionV1.Dispute -> matchedTx.copy(
                    verificationStatus = VerificationStatus.DISPUTED,
                    disputeReason = action.reason.ifBlank { "Disputed via web link" },
                    counterpartyUserId = effectiveCounterpartyUid,
                    lastGuestAction = "DISPUTE"
                )
                is GuestActionV1.ProposeDate -> {
                    val proposalJson = JSONObject().apply {
                        put("proposedDateMillis", action.proposedDateMs)
                        put("message", action.reason.ifBlank { "Proposed new due date via web link" })
                    }.toString()
                    matchedTx.copy(
                        pendingDateProposal = proposalJson,
                        counterpartyUserId = effectiveCounterpartyUid,
                        lastGuestAction = "DATE_PROPOSED"
                    )
                }
                is GuestActionV1.ClaimPayment -> {
                    val claimJson = JSONObject().apply {
                        put("amountPaise", action.amountPaise)
                        put("dateMillis", action.createdAtMs)
                        put("paymentMode", action.paymentMode.name)
                        put("reference", action.referenceNumber)
                        put("note", action.note)
                        put("hasProof", action.hasProof)
                        put("linkId", action.linkId)
                        put("actionId", action.actionId)
                    }.toString()
                    matchedTx.copy(
                        pendingPaymentClaim = claimJson,
                        counterpartyUserId = effectiveCounterpartyUid,
                        lastGuestAction = "PAYMENT_CLAIMED"
                    )
                }
            }
        }

        private suspend fun linkPartyCounterpartyUid(
            dao: com.jyotirmoyb.udhaar.data.UdhaarDao,
            matchedTx: com.jyotirmoyb.udhaar.data.UdhaarTransaction,
            otherUid: String
        ) {
            val party = matchedTx.partyId?.takeIf { it > 0L }?.let { dao.getPartyById(it) }
                ?: dao.getPartyByName(matchedTx.partyName.trim())
            if (party != null && party.linkedUserId.isBlank()) {
                dao.updateParty(party.copy(linkedUserId = otherUid))
            }
        }

        private suspend fun finalizeConfirmAction(
            context: Context,
            dao: com.jyotirmoyb.udhaar.data.UdhaarDao,
            matchedTx: com.jyotirmoyb.udhaar.data.UdhaarTransaction,
            action: GuestActionV1.Confirm,
            resolvedGuestUid: String?,
            firestoreOverride: FirebaseFirestore?
        ) {
            appendConfirmedAuditIfMissing(
                dao = dao,
                matchedTx = matchedTx,
                actorUid = resolvedGuestUid.orEmpty(),
                viaGoogle = action.verificationLevel.equals("GOOGLE_VERIFIED", ignoreCase = true)
            )
            val firestore = firestoreOverride ?: FirebaseModule.getFirestore(context) ?: return
            if (action.linkId.isBlank() || action.actionId.isBlank()) return
            val actionRef = firestore.collection("guestLinks").document(action.linkId)
                .collection("actions").document(action.actionId)
            runCatching {
                withTimeoutOrNull(3000L) {
                    actionRef.update(mapOf("status" to "ACCEPTED", "reviewedAtMs" to System.currentTimeMillis())).await()
                }
            }
            runCatching { withTimeoutOrNull(3000L) { actionRef.delete().await() } }
        }

        private suspend fun appendConfirmedAuditIfMissing(
            dao: com.jyotirmoyb.udhaar.data.UdhaarDao,
            matchedTx: com.jyotirmoyb.udhaar.data.UdhaarTransaction,
            actorUid: String,
            viaGoogle: Boolean
        ) {
            val existingAudits = dao.getAuditTrailForTransactionSnapshot(matchedTx.id)
            if (existingAudits.any { it.eventType == AuditEventType.CONFIRMED && it.isAcknowledged }) return
            val actorName = matchedTx.partyName.ifBlank { "Counterparty" }
            val summary = if (viaGoogle) "$actorName confirmed via Google" else "$actorName confirmed the record"
            AuditTrailHelper.appendLocalAuditEvent(
                dao = dao,
                transactionId = matchedTx.id,
                eventType = AuditEventType.CONFIRMED,
                actorUserId = actorUid,
                actorName = actorName,
                summaryText = summary,
                contentHashSnapshot = matchedTx.contentHash,
                isAcknowledged = true
            )
        }

        suspend fun syncSharedRecordSnapshotToLocal(
            context: Context,
            ownerUid: String,
            doc: DocumentSnapshot
        ): Boolean {
            if (!doc.exists()) return false
            val remoteId = doc.getString("remoteId")?.trim().orEmpty().ifBlank { doc.id }
            if (remoteId.isBlank()) return false

            val dao = runCatching { UdhaarDatabase.getInstance(context).udhaarDao() }.getOrNull() ?: return false
            val matchedTx = dao.getTransactionByRemoteId(remoteId)
                ?: dao.getAllTransactionsSnapshot().firstOrNull { tx ->
                    tx.remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_") == remoteId
                }
                ?: return false

            val otherUid = extractOtherParticipantUid(doc, ownerUid)
            val (updatedTx, upgradedToConfirmed) = mergeSharedRecordIntoTransaction(matchedTx, doc, otherUid)
            val changed = updatedTx != matchedTx
            if (changed) {
                dao.updateTransaction(updatedTx)
            }
            if (otherUid.isNotBlank()) {
                linkPartyCounterpartyUid(dao, matchedTx, otherUid)
            }
            if (upgradedToConfirmed) {
                appendConfirmedAuditIfMissing(dao, matchedTx, otherUid, viaGoogle = false)
            }
            return changed
        }

        private fun extractOtherParticipantUid(doc: DocumentSnapshot, ownerUid: String): String {
            val creatorUid = doc.getString("creatorUid").orEmpty().trim()
            val counterpartyUid = doc.getString("counterpartyUid").orEmpty().trim()
            val participants = (doc.get("participantUids") as? List<*>)
                ?.mapNotNull { (it as? String)?.trim() }
                .orEmpty()
            return when {
                counterpartyUid.isNotBlank() && counterpartyUid != ownerUid -> counterpartyUid
                creatorUid.isNotBlank() && creatorUid != ownerUid -> creatorUid
                else -> participants.firstOrNull { it.isNotBlank() && it != ownerUid }.orEmpty()
            }
        }

        private fun mergeSharedRecordIntoTransaction(
            matchedTx: com.jyotirmoyb.udhaar.data.UdhaarTransaction,
            doc: DocumentSnapshot,
            otherUid: String
        ): Pair<com.jyotirmoyb.udhaar.data.UdhaarTransaction, Boolean> {
            val remoteStatus = runCatching {
                VerificationStatus.valueOf(doc.getString("verificationStatus").orEmpty().trim())
            }.getOrNull()
            val shouldUpgrade = remoteStatus != null &&
                remoteStatus != matchedTx.verificationStatus &&
                !(matchedTx.verificationStatus == VerificationStatus.CONFIRMED && remoteStatus == VerificationStatus.PENDING)
            val nextStatus = if (shouldUpgrade) remoteStatus!! else matchedTx.verificationStatus
            val remoteDispute = doc.getString("disputeReason").orEmpty().trim()
            val nextDispute = when (nextStatus) {
                VerificationStatus.DISPUTED -> remoteDispute.ifBlank { matchedTx.disputeReason }
                VerificationStatus.CONFIRMED -> ""
                else -> matchedTx.disputeReason
            }
            val updated = matchedTx.copy(
                counterpartyUserId = otherUid.ifBlank { matchedTx.counterpartyUserId },
                verificationStatus = nextStatus,
                verifiedAtMillis = doc.getLong("verifiedAtMillis")?.takeIf { it > 0L } ?: matchedTx.verifiedAtMillis,
                disputeReason = nextDispute,
                lastGuestAction = if (nextStatus == VerificationStatus.CONFIRMED && matchedTx.lastGuestAction == "CONFIRM_PENDING") {
                    "CONFIRMED"
                } else {
                    matchedTx.lastGuestAction
                },
                needsMyConfirmation = if (nextStatus == VerificationStatus.CONFIRMED) false else matchedTx.needsMyConfirmation
            )
            return updated to (shouldUpgrade && nextStatus == VerificationStatus.CONFIRMED)
        }

        suspend fun processPendingCloudSyncOutbox(
            context: Context,
            firestore: FirebaseFirestore? = FirebaseModule.getFirestore(context)
        ) {
            if (firestore == null) return
            val db = runCatching { UdhaarDatabase.getInstance(context) }.getOrNull() ?: return
            val outboxDao = db.syncOutboxDao()

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
                        withTimeoutOrNull(5000L) {
                            firestore.collection("guestLinks").document(linkId)
                                .collection("actions").document(actionId)
                                .update(
                                    mapOf(
                                        "status" to "ACCEPTED",
                                        "reviewedAtMs" to System.currentTimeMillis()
                                    )
                                ).await()
                        }
                    }

                    // Update link balances
                    withTimeoutOrNull(5000L) {
                        firestore.collection("guestLinks").document(linkId)
                            .update(
                                mapOf(
                                    "paidPaise" to paidPaise,
                                    "payableAmountPaise" to payableAmountPaise,
                                    "updatedAtMs" to System.currentTimeMillis()
                                )
                            ).await()
                    }

                    // Cleanup: delete action doc and proof doc to free quota
                    runCatching {
                        withTimeoutOrNull(5000L) {
                            firestore.collection("guestLinks").document(linkId)
                                .collection("actions").document(actionId)
                                .collection("proof").document("image")
                                .delete().await()
                        }
                    }
                    runCatching {
                        withTimeoutOrNull(5000L) {
                            firestore.collection("guestLinks").document(linkId)
                                .collection("actions").document(actionId)
                                .delete().await()
                        }
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
