package com.jyotirmoyb.udhaar.data.repository

import android.content.Context
import android.util.Base64
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.AuditEventType
import com.jyotirmoyb.udhaar.data.Installment
import com.jyotirmoyb.udhaar.data.InstallmentPlan
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.SyncOutboxDao
import com.jyotirmoyb.udhaar.data.SyncOutboxEntity
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.cloud.GuestWebCloudRepository
import com.jyotirmoyb.udhaar.data.cloud.GuestWebLinkCreateResult
import com.jyotirmoyb.udhaar.data.getParsedDateProposal
import com.jyotirmoyb.udhaar.data.getParsedPaymentClaim
import com.jyotirmoyb.udhaar.di.FirebaseModule
import com.jyotirmoyb.udhaar.notification.ReminderScheduler
import com.jyotirmoyb.udhaar.sync.GuestActionPollingWorker
import com.jyotirmoyb.udhaar.util.AttachmentManager
import com.jyotirmoyb.udhaar.util.AuditTrailHelper
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.SafeLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class GuestWebRepository @Inject constructor(
    private val dao: UdhaarDao,
    private val settingsRepository: SettingsRepository,
    private val paymentRepository: PaymentRepository,
    private val installmentRepository: InstallmentRepository,
    private val guestWebCloudRepository: GuestWebCloudRepository,
    @ApplicationContext private val context: Context?,
    private val syncOutboxDao: SyncOutboxDao? = null,
    private val firestoreInstance: com.google.firebase.firestore.FirebaseFirestore? = null
) {
    protected open fun getFirestore(): com.google.firebase.firestore.FirebaseFirestore? =
        firestoreInstance ?: FirebaseModule.getFirestore(context)

    fun getTransactionsWithGuestUpdates(): Flow<List<UdhaarTransaction>> {
        return dao.getTransactionsWithGuestUpdates()
    }

    suspend fun createGuestWebLink(
        transactionId: Long,
        expiryDays: Int = 30,
        trustLevelRequired: String = "PRIVATE_LINK",
        allowNote: Boolean = true,
        allowAttachments: Boolean = false,
        showUpiQr: Boolean = true,
        allowedActions: List<String> = listOf("view", "confirm", "dispute", "pay_claim", "propose_date", "set_upi", "show_pay_qr"),
        expectedGuestEmail: String = ""
    ): GuestWebLinkCreateResult {
        val tx = dao.getTransactionById(transactionId) ?: return GuestWebLinkCreateResult(
            success = false,
            errorMessage = "Record not found"
        )
        val settings = settingsRepository.userSettings.first()
        val myName = settingsRepository.resolveSelfDisplayName(settings)
        val remoteId = tx.remoteId.ifBlank { com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.generateShortId(10) }
        val role = if (tx.transactionType == TransactionType.GIVEN) "BORROWER" else "LENDER"
        val (lenderName, borrowerName) = com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.resolveLenderAndBorrowerNames(
            type = tx.transactionType,
            partyName = tx.partyName,
            myProfileName = myName,
            explicitLenderName = tx.lenderName,
            explicitBorrowerName = tx.borrowerName
        )
        val computedHash = tx.contentHash.ifBlank {
            com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.computeTransactionContentHash(
                tx = tx,
                myProfileName = myName
            )
        }

        // Ensure remoteId and canonical names/hash are saved locally
        val preparedTx = if (tx.remoteId != remoteId || tx.contentHash != computedHash) {
            val updated = tx.copy(
                remoteId = remoteId,
                lenderName = lenderName,
                borrowerName = borrowerName,
                contentHash = computedHash
            )
            dao.updateTransaction(updated)
            updated
        } else tx

        val result = guestWebCloudRepository.createGuestWebLink(
            recordId = remoteId,
            counterpartyRole = role,
            intendedPhone = preparedTx.phoneNumber,
            expiryDays = expiryDays,
            trustLevelRequired = trustLevelRequired,
            allowedActions = allowedActions,
            allowNote = allowNote,
            allowAttachments = allowAttachments,
            showUpiQr = showUpiQr,
            lenderName = lenderName,
            borrowerName = borrowerName,
            principalAmountPaise = preparedTx.principalAmountPaise,
            amountPaidPaise = preparedTx.amountPaidPaise,
            currency = preparedTx.currency.code,
            startDateMillis = preparedTx.startDateMillis,
            expiryDateMillis = preparedTx.expiryDateMillis,
            note = if (allowNote) preparedTx.notes else "",
            contentHash = computedHash,
            lenderUpiId = if (showUpiQr) settings.upiId else "",
            creatorPhone = settings.phoneNumber,
            expectedGuestEmail = expectedGuestEmail
        )

        if (result.success) {
            val updatedTx = preparedTx.copy(
                webLinkStatus = "ACTIVE",
                trustLevel = result.trustLevel.ifBlank { trustLevelRequired },
                shareToken = if (result.secret.isNotBlank()) result.secret else preparedTx.shareToken
            )
            dao.updateTransaction(updatedTx)

            AuditTrailHelper.appendLocalAuditEvent(
                dao = dao,
                transactionId = tx.id,
                eventType = AuditEventType.SHARED,
                actorUserId = settings.cloudUserId,
                actorName = settingsRepository.resolveSelfDisplayName(settings),
                summaryText = "Web link created (${result.trustLevel}, expires in $expiryDays days)"
            )
        }

        return result
    }

    suspend fun revokeGuestWebLink(transactionId: Long): Boolean {
        val tx = dao.getTransactionById(transactionId) ?: return false
        val targetTokenOrId = tx.shareToken.ifBlank { tx.remoteId }
        val ok = if (targetTokenOrId.isNotBlank()) {
            guestWebCloudRepository.revokeGuestWebLink(targetTokenOrId)
        } else true

        if (ok) {
            val updatedTx = tx.copy(webLinkStatus = "REVOKED")
            dao.updateTransaction(updatedTx)

            val settings = settingsRepository.userSettings.first()
            AuditTrailHelper.appendLocalAuditEvent(
                dao = dao,
                transactionId = tx.id,
                eventType = AuditEventType.EDITED,
                actorUserId = settings.cloudUserId,
                actorName = settingsRepository.resolveSelfDisplayName(settings),
                summaryText = "Web link revoked"
            )
        }
        return ok
    }

    suspend fun acceptPaymentClaim(transactionId: Long): Boolean {
        val tx = dao.getTransactionById(transactionId) ?: return false
        val claim = tx.getParsedPaymentClaim() ?: return false
        val settings = settingsRepository.userSettings.first()

        val cleanPaymentPaise = claim.amountPaise.coerceAtLeast(0L)
        if (cleanPaymentPaise <= 0L) return false

        val remoteActionId = claim.actionId.ifBlank { "claim_${System.currentTimeMillis()}" }
        val createdAtMs = if (claim.dateMillis > 0L) claim.dateMillis else System.currentTimeMillis()
        val uniqueKey = "${remoteActionId}_$createdAtMs"

        // Check if proof attachment was saved locally
        val attachments = dao.getAttachmentsForTransactionSnapshot(transactionId)
        val proofAttachment = attachments.firstOrNull { it.addedBy.contains("Guest") }

        val payment = com.jyotirmoyb.udhaar.data.PartialPayment(
            id = 0L,
            transactionId = transactionId,
            amountPaise = cleanPaymentPaise,
            paymentMode = claim.paymentMode,
            paidAtMillis = createdAtMs,
            note = if (claim.reference.isNotBlank()) {
                if (claim.note.isNotBlank()) "${claim.note} (Ref: ${claim.reference})" else "Ref: ${claim.reference}"
            } else claim.note.ifBlank { "Claim verified" },
            remotePaymentId = uniqueKey,
            syncId = "guest_action_$uniqueKey",
            recordedByUserId = settings.cloudUserId,
            recordedByName = tx.partyName.ifBlank { "Guest" },
            isAcknowledged = true,
            acknowledgedAtMillis = System.currentTimeMillis()
        )

        val formattedAmt = "${tx.currency.symbol}${InterestCalculator.formatPaise(cleanPaymentPaise)}"
        val auditEntry = com.jyotirmoyb.udhaar.data.AuditTrailEntry(
            id = 0L,
            transactionId = transactionId,
            remoteEventId = uniqueKey,
            eventType = AuditEventType.PAYMENT_RECORDED,
            actorUserId = settings.cloudUserId,
            actorName = settingsRepository.resolveSelfDisplayName(settings),
            summaryText = "Payment claim of $formattedAmt accepted via ${claim.paymentMode.label}${if (claim.reference.isNotBlank()) " (Ref: ${claim.reference})" else ""}",
            contentHashSnapshot = tx.contentHash,
            isAcknowledged = true,
            eventTimeMillis = System.currentTimeMillis()
        )

        // ONE Room @Transaction: inserts payment, recomputes paidPaise, updates transaction, writes audit entry
        val insertedPaymentId = dao.applyGuestPaymentClaimAtomic(
            transactionId = transactionId,
            payment = payment,
            proofAttachmentId = proofAttachment?.id,
            auditEntry = auditEntry
        )

        if (insertedPaymentId <= 0L) return false

        // Fetch fresh recomputed balances
        val freshTx = dao.getTransactionById(transactionId) ?: tx
        val totalPaidPaise = freshTx.amountPaidPaise
        val payablePaise = (freshTx.principalAmountPaise - totalPaidPaise).coerceAtLeast(0L)

        // Update Firestore: action status=ACCEPTED, link paidPaise/payableAmountPaise
        val linkId = claim.linkId.ifBlank {
            if (tx.shareToken.isNotBlank()) GuestWebCloudRepository.sha256Hex(tx.shareToken) else ""
        }

        var cloudUpdateSuccess = false
        if (linkId.isNotBlank()) {
            val firestore = getFirestore()
            if (firestore != null) {
                try {
                    withTimeoutOrNull(3000L) {
                        firestore.collection("guestLinks").document(linkId)
                            .collection("actions").document(remoteActionId)
                            .update(
                                mapOf(
                                    "status" to "ACCEPTED",
                                    "reviewedAtMs" to System.currentTimeMillis()
                                )
                            ).await()

                        firestore.collection("guestLinks").document(linkId)
                            .update(
                                mapOf(
                                    "paidPaise" to totalPaidPaise,
                                    "payableAmountPaise" to payablePaise,
                                    "updatedAtMs" to System.currentTimeMillis()
                                )
                            ).await()

                        // Cleanup: delete action doc (and proof) so guest can submit again and quota stays low
                        runCatching {
                            firestore.collection("guestLinks").document(linkId)
                                .collection("actions").document(remoteActionId)
                                .collection("proof").document("image")
                                .delete().await()
                        }
                        runCatching {
                            firestore.collection("guestLinks").document(linkId)
                                .collection("actions").document(remoteActionId)
                                .delete().await()
                        }
                        cloudUpdateSuccess = true
                    }
                } catch (e: Exception) {
                    if (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                        e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                        SafeLogger.w("GuestWebRepo", "Spark Firestore quota exhausted on payment accept. Queuing outbox sync.")
                    } else {
                        SafeLogger.w("GuestWebRepo", "Firestore update failed on accept: ${e.message}")
                    }
                    SafeLogger.recordNonFatal("GuestWebRepo", "ACCEPT_PAYMENT_FAILED", e, mapOf("linkId" to linkId, "actionId" to remoteActionId))
                }
            }
        }

        if (!cloudUpdateSuccess && linkId.isNotBlank()) {
            // Keep local "pendingCloudSync" flag and retry with WorkManager
            val outbox = syncOutboxDao ?: context?.let {
                runCatching { UdhaarDatabase.getInstance(it).syncOutboxDao() }.getOrNull()
            }
            if (outbox != null) {
                outbox.enqueue(
                    SyncOutboxEntity(
                        entity = "guest_action_sync",
                        syncId = "$linkId/$remoteActionId",
                        op = "ACCEPT_PAYMENT",
                        payloadJson = JSONObject().apply {
                            put("linkId", linkId)
                            put("actionId", remoteActionId)
                            put("paidPaise", totalPaidPaise)
                            put("payableAmountPaise", payablePaise)
                        }.toString(),
                        idempotencyKey = uniqueKey,
                        status = "PENDING"
                    )
                )
                context?.let { ctx ->
                    GuestActionPollingWorker.enqueueImmediate(ctx)
                }
            }
        }

        return true
    }

    suspend fun acceptConfirmation(transactionId: Long): Boolean {
        val tx = dao.getTransactionById(transactionId) ?: return false
        val settings = settingsRepository.userSettings.first()
        val isGoogle = tx.trustLevel.equals("GOOGLE_VERIFIED", ignoreCase = true)
        val ackLabel = if (isGoogle) "Acknowledged via Google" else "Acknowledged via private link"

        val updatedTx = tx.copy(
            verificationStatus = com.jyotirmoyb.udhaar.data.VerificationStatus.CONFIRMED,
            verifiedAtMillis = System.currentTimeMillis(),
            lastGuestAction = "CONFIRMED"
        )
        dao.updateTransaction(updatedTx)

        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = tx.id,
            eventType = AuditEventType.CONFIRMED,
            actorUserId = settings.cloudUserId,
            actorName = tx.partyName.ifBlank { "Guest" },
            summaryText = ackLabel
        )

        val linkId = if (tx.shareToken.isNotBlank()) GuestWebCloudRepository.sha256Hex(tx.shareToken) else ""
        if (linkId.isNotBlank()) {
            val firestore = getFirestore()
            if (firestore != null) {
                try {
                    withTimeoutOrNull(3000L) {
                        runCatching {
                            firestore.collection("guestLinks").document(linkId)
                                .collection("actions").document("CONFIRM")
                                .update(
                                    mapOf(
                                        "status" to "ACCEPTED",
                                        "reviewedAtMs" to System.currentTimeMillis()
                                    )
                                ).await()
                        }
                        runCatching {
                            firestore.collection("guestLinks").document(linkId)
                                .collection("actions").document("CONFIRM")
                                .delete().await()
                        }
                    }
                } catch (e: Exception) {
                    if (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                        e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                        SafeLogger.w("GuestWebRepo", "Spark Firestore quota exhausted on confirm.")
                    } else {
                        SafeLogger.w("GuestWebRepo", "Firestore update failed on confirm: ${e.message}")
                    }
                    SafeLogger.recordNonFatal("GuestWebRepo", "ACCEPT_CONFIRM_FAILED", e, mapOf("linkId" to linkId))
                }
            }
        }
        return true
    }

    suspend fun rejectPaymentClaim(transactionId: Long, reason: String = "Not received"): Boolean {
        val tx = dao.getTransactionById(transactionId) ?: return false
        val claim = tx.getParsedPaymentClaim()
        val settings = settingsRepository.userSettings.first()

        val updatedTx = tx.copy(
            pendingPaymentClaim = "",
            lastGuestAction = "PAYMENT_REJECTED"
        )
        dao.updateTransaction(updatedTx)

        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = tx.id,
            eventType = AuditEventType.DISPUTED,
            actorUserId = settings.cloudUserId,
            actorName = settingsRepository.resolveSelfDisplayName(settings),
            summaryText = "Payment claim marked not received (${reason.ifBlank { "No funds received" }})"
        )

        val linkId = claim?.linkId?.ifBlank {
            if (tx.shareToken.isNotBlank()) GuestWebCloudRepository.sha256Hex(tx.shareToken) else ""
        } ?: if (tx.shareToken.isNotBlank()) GuestWebCloudRepository.sha256Hex(tx.shareToken) else ""
        val actionId = claim?.actionId?.ifBlank { "CLAIM_PAYMENT" } ?: "CLAIM_PAYMENT"

        if (linkId.isNotBlank()) {
            val firestore = getFirestore()
            if (firestore != null) {
                try {
                    withTimeoutOrNull(3000L) {
                        runCatching {
                            firestore.collection("guestLinks").document(linkId)
                                .collection("actions").document(actionId)
                                .update(
                                    mapOf(
                                        "status" to "REJECTED",
                                        "rejectReason" to reason.ifBlank { "Not received" },
                                        "reviewedAtMs" to System.currentTimeMillis()
                                    )
                                ).await()
                        }
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
                    }
                } catch (e: Exception) {
                    if (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                        e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                        SafeLogger.w("GuestWebRepo", "Spark Firestore quota exhausted on reject claim.")
                    } else {
                        SafeLogger.w("GuestWebRepo", "Firestore update failed on reject: ${e.message}")
                    }
                    SafeLogger.recordNonFatal("GuestWebRepo", "REJECT_CLAIM_FAILED", e, mapOf("linkId" to linkId, "actionId" to actionId))
                }
            }
        }
        return true
    }

    suspend fun acceptDateProposal(transactionId: Long): Boolean {
        val tx = dao.getTransactionById(transactionId) ?: return false
        val proposal = tx.getParsedDateProposal() ?: return false
        val newDueDate = proposal.proposedDateMillis
        if (newDueDate <= 0L) return false

        val settings = settingsRepository.userSettings.first()

        val updatedTx = tx.copy(
            expiryDateMillis = newDueDate,
            pendingDateProposal = "",
            lastGuestAction = "DATE_ACCEPTED"
        )
        dao.updateTransaction(updatedTx)

        context?.let { ctx ->
            ReminderScheduler.scheduleReminder(ctx, updatedTx)
        }

        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = tx.id,
            eventType = AuditEventType.EXTENSION_ACCEPTED,
            actorUserId = settings.cloudUserId,
            actorName = settingsRepository.resolveSelfDisplayName(settings),
            summaryText = "Proposed return date accepted (new due date: ${InterestCalculator.formatDate(newDueDate)})"
        )

        val linkId = if (tx.shareToken.isNotBlank()) GuestWebCloudRepository.sha256Hex(tx.shareToken) else ""
        if (linkId.isNotBlank()) {
            val firestore = getFirestore()
            if (firestore != null) {
                try {
                    withTimeoutOrNull(3000L) {
                        runCatching {
                            firestore.collection("guestLinks").document(linkId).update(
                                mapOf(
                                    "dueDateMs" to newDueDate,
                                    "updatedAtMs" to System.currentTimeMillis()
                                )
                            ).await()
                        }
                        runCatching {
                            firestore.collection("guestLinks").document(linkId)
                                .collection("actions").document("PROPOSE_DATE")
                                .update(
                                    mapOf(
                                        "status" to "ACCEPTED",
                                        "reviewedAtMs" to System.currentTimeMillis()
                                    )
                                ).await()
                        }
                        runCatching {
                            firestore.collection("guestLinks").document(linkId)
                                .collection("actions").document("PROPOSE_DATE")
                                .delete().await()
                        }
                    }
                } catch (e: Exception) {
                    if (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                        e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                        SafeLogger.w("GuestWebRepo", "Spark Firestore quota exhausted on acceptDateProposal.")
                    } else {
                        SafeLogger.w("GuestWebRepo", "Firestore update failed on acceptDateProposal: ${e.message}")
                    }
                    SafeLogger.recordNonFatal("GuestWebRepo", "ACCEPT_DATE_FAILED", e, mapOf("linkId" to linkId))
                }
            }
        }
        return true
    }

    suspend fun declineDateProposal(transactionId: Long, reason: String = ""): Boolean {
        val tx = dao.getTransactionById(transactionId) ?: return false
        val settings = settingsRepository.userSettings.first()

        val updatedTx = tx.copy(
            pendingDateProposal = "",
            lastGuestAction = "DATE_DECLINED"
        )
        dao.updateTransaction(updatedTx)

        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = tx.id,
            eventType = AuditEventType.EXTENSION_DECLINED,
            actorUserId = settings.cloudUserId,
            actorName = settingsRepository.resolveSelfDisplayName(settings),
            summaryText = "Proposed return date declined"
        )

        val linkId = if (tx.shareToken.isNotBlank()) GuestWebCloudRepository.sha256Hex(tx.shareToken) else ""
        if (linkId.isNotBlank()) {
            val firestore = getFirestore()
            if (firestore != null) {
                try {
                    withTimeoutOrNull(3000L) {
                        runCatching {
                            firestore.collection("guestLinks").document(linkId)
                                .collection("actions").document("PROPOSE_DATE")
                                .update(
                                    mapOf(
                                        "status" to "REJECTED",
                                        "rejectReason" to reason.ifBlank { "Declined" },
                                        "reviewedAtMs" to System.currentTimeMillis()
                                    )
                                ).await()
                        }
                        runCatching {
                            firestore.collection("guestLinks").document(linkId)
                                .collection("actions").document("PROPOSE_DATE")
                                .delete().await()
                        }
                    }
                } catch (e: Exception) {
                    if (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                        e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                        SafeLogger.w("GuestWebRepo", "Spark Firestore quota exhausted on declineDateProposal.")
                    } else {
                        SafeLogger.w("GuestWebRepo", "Firestore update failed on declineDateProposal: ${e.message}")
                    }
                    SafeLogger.recordNonFatal("GuestWebRepo", "DECLINE_DATE_FAILED", e, mapOf("linkId" to linkId))
                }
            }
        }
        return true
    }

    suspend fun rejectDispute(transactionId: Long, reason: String = "Dispute rejected"): Boolean {
        val tx = dao.getTransactionById(transactionId) ?: return false
        val settings = settingsRepository.userSettings.first()

        val updatedTx = tx.copy(
            verificationStatus = com.jyotirmoyb.udhaar.data.VerificationStatus.LOCAL_ONLY,
            disputeReason = "",
            lastGuestAction = "DISPUTE_REJECTED"
        )
        dao.updateTransaction(updatedTx)

        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = tx.id,
            eventType = AuditEventType.DISPUTED,
            actorUserId = settings.cloudUserId,
            actorName = settingsRepository.resolveSelfDisplayName(settings),
            summaryText = "Dispute rejected by lender (${reason.ifBlank { "Dispute rejected" }})"
        )

        val linkId = if (tx.shareToken.isNotBlank()) GuestWebCloudRepository.sha256Hex(tx.shareToken) else ""
        if (linkId.isNotBlank()) {
            val firestore = getFirestore()
            if (firestore != null) {
                try {
                    withTimeoutOrNull(3000L) {
                        runCatching {
                            firestore.collection("guestLinks").document(linkId)
                                .collection("actions").document("DISPUTE")
                                .update(
                                    mapOf(
                                        "status" to "REJECTED",
                                        "rejectReason" to reason.ifBlank { "Dispute rejected" },
                                        "reviewedAtMs" to System.currentTimeMillis()
                                    )
                                ).await()
                        }
                        runCatching {
                            firestore.collection("guestLinks").document(linkId)
                                .collection("actions").document("DISPUTE")
                                .delete().await()
                        }
                    }
                } catch (e: Exception) {
                    if (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                        e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                        SafeLogger.w("GuestWebRepo", "Spark Firestore quota exhausted on rejectDispute.")
                    } else {
                        SafeLogger.w("GuestWebRepo", "Firestore update failed on rejectDispute: ${e.message}")
                    }
                    SafeLogger.recordNonFatal("GuestWebRepo", "REJECT_DISPUTE_FAILED", e, mapOf("linkId" to linkId))
                }
            }
        }
        return true
    }

    suspend fun revokeAllGuestLinks(): Int {
        val transactions = dao.getAllTransactionsSnapshot()
        val activeRecords = transactions.filter { it.webLinkStatus == "ACTIVE" }
        val remoteIds = activeRecords.map { it.remoteId }.filter { it.isNotBlank() }
        if (remoteIds.isNotEmpty()) {
            guestWebCloudRepository.revokeAllGuestWebLinks(remoteIds)
        }

        for (tx in activeRecords) {
            dao.updateTransaction(tx.copy(webLinkStatus = "REVOKED"))
        }
        return activeRecords.size
    }

    suspend fun claimGuestActions(recordId: String, secret: String): Boolean {
        return guestWebCloudRepository.claimGuestActions(recordId, secret)
    }

    suspend fun fetchGuestLinkBySecret(
        secret: String,
        fallbackRecordId: String = ""
    ): com.jyotirmoyb.udhaar.data.cloud.GuestLinkFetchResult {
        return guestWebCloudRepository.fetchGuestLinkBySecret(secret, fallbackRecordId)
    }

    suspend fun submitGuestActionFromApp(
        linkId: String,
        actionType: String,
        reason: String = ""
    ): Result<Unit> {
        return guestWebCloudRepository.submitGuestActionFromApp(linkId, actionType, reason)
    }

    suspend fun fetchPaymentClaimProof(linkId: String): Pair<String, String>? {
        if (linkId.isBlank()) return null
        val firestore = getFirestore() ?: return null
        return try {
            withTimeoutOrNull(3000L) {
                val doc = firestore.collection("guestLinks").document(linkId)
                    .collection("actions").document("CLAIM_PAYMENT")
                    .collection("proof").document("image")
                    .get()
                    .await()
                if (doc.exists()) {
                    val b64 = doc.getString("dataB64") ?: return@withTimeoutOrNull null
                    val mime = doc.getString("mime") ?: "image/webp"
                    Pair(mime, b64)
                } else null
            }
        } catch (e: Exception) {
            SafeLogger.e("GuestWebRepo", "Failed to fetch proof: ${e.javaClass.simpleName}")
            null
        }
    }

    suspend fun saveClaimProofToLedgerAndDeleteCloudDoc(
        ctx: Context,
        transactionId: Long,
        linkId: String,
        mime: String,
        dataB64: String
    ): Boolean {
        if (dataB64.isBlank()) return false
        val cleanB64 = if (dataB64.contains(",")) dataB64.substringAfter(",") else dataB64
        val bytes = try {
            Base64.decode(cleanB64, Base64.DEFAULT)
        } catch (_: Exception) {
            return false
        }
        if (bytes == null || bytes.isEmpty()) return false

        val ext = if (mime.contains("webp", ignoreCase = true)) "webp" else "jpg"
        val fileName = "proof_claim_${System.currentTimeMillis()}.$ext"
        val destFile = File(AttachmentManager.getAttachmentsDir(ctx), fileName)
        try {
            destFile.writeBytes(bytes)
        } catch (e: Exception) {
            SafeLogger.e("GuestWebRepo", "Failed to write local attachment: ${e.javaClass.simpleName}")
            return false
        }

        val sha256 = AttachmentManager.calculateSha256(bytes)
        val attachment = Attachment(
            id = 0L,
            transactionId = transactionId,
            paymentId = null,
            fileName = fileName,
            mimeType = mime,
            sizeBytes = destFile.length(),
            localPath = destFile.absolutePath,
            remoteUrl = null,
            addedAtMillis = System.currentTimeMillis(),
            addedBy = "Guest (Web Proof)",
            sha256 = sha256
        )
        dao.insertAttachment(attachment)

        // Delete cloud proof doc to free quota
        if (linkId.isNotBlank()) {
            val firestore = getFirestore()
            if (firestore != null) {
                try {
                    withTimeoutOrNull(3000L) {
                        firestore.collection("guestLinks").document(linkId)
                            .collection("actions").document("CLAIM_PAYMENT")
                            .collection("proof").document("image")
                            .delete()
                            .await()
                    }
                } catch (e: Exception) {
                    SafeLogger.w("GuestWebRepo", "Failed to delete cloud proof doc: ${e.javaClass.simpleName}")
                }
            }
        }
        return true
    }
}
