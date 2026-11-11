package com.jyotirmoyb.udhaar.data.repository

import android.content.Context
import com.jyotirmoyb.udhaar.data.AuditEventType
import com.jyotirmoyb.udhaar.data.Installment
import com.jyotirmoyb.udhaar.data.InstallmentPlan
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.cloud.GuestWebCloudRepository
import com.jyotirmoyb.udhaar.data.cloud.GuestWebLinkCreateResult
import com.jyotirmoyb.udhaar.data.getParsedDateProposal
import com.jyotirmoyb.udhaar.data.getParsedPaymentClaim
import com.jyotirmoyb.udhaar.notification.ReminderScheduler
import com.jyotirmoyb.udhaar.util.AuditTrailHelper
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.SafeLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    @ApplicationContext private val context: Context?
) {

    fun getTransactionsWithGuestUpdates(): Flow<List<UdhaarTransaction>> {
        return dao.getTransactionsWithGuestUpdates()
    }

    suspend fun createGuestWebLink(
        transactionId: Long,
        expiryDays: Int = 30,
        trustLevelRequired: String = "WHATSAPP_OTP",
        allowNote: Boolean = true,
        allowAttachments: Boolean = false,
        showUpiQr: Boolean = true,
        allowedActions: List<String> = listOf("view", "confirm", "dispute", "pay_claim", "propose_date", "set_upi", "show_pay_qr")
    ): GuestWebLinkCreateResult {
        val tx = dao.getTransactionById(transactionId) ?: return GuestWebLinkCreateResult(
            success = false,
            errorMessage = "Record not found"
        )
        val settings = settingsRepository.userSettings.first()
        val myName = settings.payeeName.ifBlank { "Udhaar User" }
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
            creatorPhone = settings.phoneNumber
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
                actorName = settings.payeeName.ifBlank { "You" },
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
                actorName = settings.payeeName.ifBlank { "You" },
                summaryText = "Web link revoked"
            )
        }
        return ok
    }

    suspend fun acceptPaymentClaim(transactionId: Long): Boolean {
        val tx = dao.getTransactionById(transactionId) ?: return false
        val claim = tx.getParsedPaymentClaim() ?: return false
        val settings = settingsRepository.userSettings.first()

        paymentRepository.recordPartialPaymentPaise(
            transaction = tx,
            paymentAmountPaise = claim.amountPaise,
            paymentMode = claim.paymentMode,
            note = if (claim.reference.isNotBlank()) "Claim verified (Ref: ${claim.reference})" else "Payment verified",
            totalAccruedAmountPaise = tx.principalAmountPaise,
            actorName = tx.partyName.ifBlank { "Guest" }
        )

        val freshTx = dao.getTransactionById(transactionId) ?: tx
        val updatedTx = freshTx.copy(
            pendingPaymentClaim = "",
            lastGuestAction = "PAYMENT_CONFIRMED"
        )
        dao.updateTransaction(updatedTx)

        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = tx.id,
            eventType = AuditEventType.PAYMENT_ACKNOWLEDGED,
            actorUserId = settings.cloudUserId,
            actorName = settings.payeeName.ifBlank { "You" },
            summaryText = "Payment claim of ${InterestCalculator.formatCurrencyPaise(claim.amountPaise, tx.currency)} accepted"
        )
        return true
    }

    suspend fun rejectPaymentClaim(transactionId: Long, reason: String = "Not received"): Boolean {
        val tx = dao.getTransactionById(transactionId) ?: return false
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
            actorName = settings.payeeName.ifBlank { "You" },
            summaryText = "Payment claim marked not received (${reason.ifBlank { "No funds received" }})"
        )
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

        // Reschedule reminders
        context?.let { ctx ->
            ReminderScheduler.scheduleReminder(ctx, updatedTx)
        }

        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = tx.id,
            eventType = AuditEventType.EXTENSION_ACCEPTED,
            actorUserId = settings.cloudUserId,
            actorName = settings.payeeName.ifBlank { "You" },
            summaryText = "Proposed return date accepted (new due date: ${InterestCalculator.formatDate(newDueDate)})"
        )
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
            actorName = settings.payeeName.ifBlank { "You" },
            summaryText = "Proposed return date declined"
        )
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
}
