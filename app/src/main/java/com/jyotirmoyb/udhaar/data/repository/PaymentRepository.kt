package com.jyotirmoyb.udhaar.data.repository

import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.AuditEventType
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.data.cloud.CloudPartialPayment
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.util.AuditTrailHelper
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.SafeLogger
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

open class PaymentRepository(
    private val dao: UdhaarDao,
    private val settingsRepository: SettingsRepository,
    private val cloudRepository: VerificationCloudRepository? = null,
    private val installmentRepository: InstallmentRepository? = null
) {
    val allPartialPayments: Flow<List<PartialPayment>> = dao.getAllPartialPayments().catch { e ->
        SafeLogger.e("PaymentRepository", "Failed reading partial payments flow", e)
        emit(emptyList())
    }

    fun getPartialPayments(transactionId: Long): Flow<List<PartialPayment>> =
        dao.getPartialPaymentsForTransaction(transactionId)

    suspend fun recordPartialPayment(
        transaction: UdhaarTransaction,
        paymentAmount: Double,
        paymentMode: PaymentMode = PaymentMode.CASH,
        note: String,
        totalAccruedAmount: Double,
        actorName: String = "You",
        proofAttachment: Attachment? = null
    ): Long {
        val cleanPayment = paymentAmount.coerceAtLeast(0.0)
        if (cleanPayment <= 0.0) return -1L

        val settings = settingsRepository.userSettings.first()
        val effectiveActor = actorName.trim().ifEmpty { settings.payeeName.ifBlank { "You" } }
        val remotePaymentId = "pay_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val nowMillis = System.currentTimeMillis()
        val isSharedRecord = transaction.verificationStatus != VerificationStatus.LOCAL_ONLY

        val insertedPaymentId = dao.insertPartialPayment(
            PartialPayment(
                transactionId = transaction.id,
                amount = cleanPayment,
                paymentMode = paymentMode,
                paidAtMillis = nowMillis,
                note = note.trim(),
                remotePaymentId = remotePaymentId,
                recordedByUserId = settings.cloudUserId,
                recordedByName = effectiveActor,
                isAcknowledged = !isSharedRecord,
                acknowledgedAtMillis = if (!isSharedRecord) nowMillis else null,
                needsMyAcknowledgement = false
            )
        )

        if (proofAttachment != null) {
            dao.insertAttachment(
                proofAttachment.copy(
                    id = 0L,
                    transactionId = transaction.id,
                    paymentId = insertedPaymentId
                )
            )
        }

        val plan = dao.getInstallmentPlanForTransactionSnapshot(transaction.id)
        val planSettled = if (plan != null) {
            val paymentPaise = com.jyotirmoyb.udhaar.util.InstallmentCalculator.toPaise(cleanPayment)
            val allocResult = installmentRepository?.applyPaymentToInstallments(
                txId = transaction.id,
                paymentAmountPaise = paymentPaise,
                paymentId = insertedPaymentId,
                paidAtMillis = nowMillis
            )
            allocResult?.isPlanFullySettled ?: false
        } else {
            false
        }

        val updatedPaid = transaction.amountPaid + cleanPayment
        val nowSettled = planSettled || (updatedPaid >= (totalAccruedAmount - 0.01))
        val updatedTx = transaction.copy(
            amountPaid = updatedPaid,
            isSettled = nowSettled,
            settledAtMillis = if (nowSettled) nowMillis else transaction.settledAtMillis
        )
        dao.updateTransaction(updatedTx)

        val formattedAmt = "${transaction.currency.symbol}${InterestCalculator.formatAmount(cleanPayment)}"
        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = transaction.id,
            eventType = AuditEventType.PAYMENT_RECORDED,
            actorUserId = settings.cloudUserId,
            actorName = effectiveActor,
            summaryText = "$effectiveActor recorded partial payment of $formattedAmt via ${paymentMode.label}" +
                if (isSharedRecord) " (awaiting counterparty acknowledgement)" else "",
            contentHashSnapshot = transaction.contentHash,
            isAcknowledged = !isSharedRecord
        )

        if (nowSettled) {
            AuditTrailHelper.appendLocalAuditEvent(
                dao = dao,
                transactionId = transaction.id,
                eventType = AuditEventType.SETTLED,
                actorUserId = settings.cloudUserId,
                actorName = effectiveActor,
                summaryText = "Record settled in full ($formattedAmt final payment)",
                contentHashSnapshot = transaction.contentHash,
                isAcknowledged = true
            )
        }

        if (settings.cloudVerificationConsent && settings.cloudUserId.isNotBlank() && transaction.remoteId.isNotBlank()) {
            runCatching {
                val participants = listOfNotNull(
                    settings.cloudUserId.takeIf { it.isNotBlank() },
                    transaction.counterpartyUserId.takeIf { it.isNotBlank() }
                ).distinct()
                val encNote = VerificationCryptoHelper.encryptE2eeField(
                    note.trim(),
                    transaction.remoteId,
                    transaction.shareToken
                )
                cloudRepository?.createPartialPayment(
                    CloudPartialPayment(
                        paymentId = remotePaymentId,
                        remoteId = transaction.remoteId,
                        recordedByUid = settings.cloudUserId,
                        recordedByName = effectiveActor,
                        participantUids = participants,
                        amount = cleanPayment,
                        paymentMode = paymentMode.name,
                        paidAtMillis = nowMillis,
                        encryptedNote = encNote,
                        isAcknowledged = false
                    )
                )
                cloudRepository?.updateSharedRecordTermsOrStatus(
                    remoteId = transaction.remoteId,
                    updates = mapOf(
                        "amountPaid" to updatedPaid,
                        "isSettled" to nowSettled
                    )
                )
            }
        }
        return insertedPaymentId
    }

    suspend fun acknowledgePartialPayment(
        transaction: UdhaarTransaction,
        payment: PartialPayment,
        actorName: String = "You"
    ) {
        val settings = settingsRepository.userSettings.first()
        val effectiveActor = actorName.trim().ifEmpty { settings.payeeName.ifBlank { "You" } }
        val nowMillis = System.currentTimeMillis()

        val updatedPayment = payment.copy(
            isAcknowledged = true,
            acknowledgedAtMillis = nowMillis,
            needsMyAcknowledgement = false
        )
        dao.updatePartialPayment(updatedPayment)

        val formattedAmt = "${transaction.currency.symbol}${InterestCalculator.formatAmount(payment.amount)}"
        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = transaction.id,
            eventType = AuditEventType.PAYMENT_ACKNOWLEDGED,
            actorUserId = settings.cloudUserId,
            actorName = effectiveActor,
            summaryText = "$effectiveActor confirmed partial payment of $formattedAmt recorded by ${payment.recordedByName.ifBlank { transaction.partyName }}",
            contentHashSnapshot = transaction.contentHash,
            isAcknowledged = true
        )

        if (settings.cloudVerificationConsent &&
            settings.cloudUserId.isNotBlank() &&
            transaction.remoteId.isNotBlank() &&
            payment.remotePaymentId.isNotBlank()
        ) {
            runCatching {
                cloudRepository?.acknowledgePartialPayment(
                    remoteId = transaction.remoteId,
                    paymentId = payment.remotePaymentId,
                    acknowledgedByUid = settings.cloudUserId,
                    acknowledgedAtMillis = nowMillis
                )
            }
        }
    }

    suspend fun deletePartialPayment(
        transaction: UdhaarTransaction,
        payment: PartialPayment,
        totalAccruedAmount: Double
    ) {
        dao.deletePartialPayment(payment)
        val updatedPaid = (transaction.amountPaid - payment.amount).coerceAtLeast(0.0)
        val planResult = installmentRepository?.recalculateInstallmentsForTotalPaid(
            txId = transaction.id,
            totalPaidPaise = com.jyotirmoyb.udhaar.util.InstallmentCalculator.toPaise(updatedPaid)
        )
        val nowSettled = (planResult?.isPlanFullySettled == true) ||
            (updatedPaid >= (totalAccruedAmount - 0.01) && totalAccruedAmount > 0.0)
        dao.updateTransaction(
            transaction.copy(
                amountPaid = updatedPaid,
                isSettled = nowSettled,
                settledAtMillis = if (nowSettled) transaction.settledAtMillis else null
            )
        )
    }
}
