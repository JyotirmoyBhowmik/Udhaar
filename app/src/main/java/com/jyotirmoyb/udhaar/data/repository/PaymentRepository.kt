package com.jyotirmoyb.udhaar.data.repository

import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.AuditEventType
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
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
    ): Long = recordPartialPaymentPaise(
        transaction = transaction,
        paymentAmountPaise = InterestCalculator.rupeesToPaise(paymentAmount),
        paymentMode = paymentMode,
        note = note,
        totalAccruedAmountPaise = InterestCalculator.rupeesToPaise(totalAccruedAmount),
        actorName = actorName,
        proofAttachment = proofAttachment
    )

    suspend fun recordPartialPaymentPaise(
        transaction: UdhaarTransaction,
        paymentAmountPaise: Long,
        paymentMode: PaymentMode = PaymentMode.CASH,
        note: String,
        totalAccruedAmountPaise: Long,
        actorName: String = "You",
        proofAttachment: Attachment? = null
    ): Long {
        val cleanPaymentPaise = paymentAmountPaise.coerceAtLeast(0L)
        if (cleanPaymentPaise <= 0L) return -1L

        val settings = settingsRepository.userSettings.first()
        val effectiveActor = actorName.trim().ifEmpty { settings.payeeName.ifBlank { "You" } }
        val remotePaymentId = "pay_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val nowMillis = System.currentTimeMillis()
        val isSharedRecord = transaction.verificationStatus != VerificationStatus.LOCAL_ONLY

        val insertedPaymentId = dao.insertPartialPayment(
            PartialPayment(
                transactionId = transaction.id,
                amountPaise = cleanPaymentPaise,
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
            val allocResult = installmentRepository?.applyPaymentToInstallments(
                txId = transaction.id,
                paymentAmountPaise = cleanPaymentPaise,
                paymentId = insertedPaymentId,
                paidAtMillis = nowMillis
            )
            allocResult?.isPlanFullySettled ?: false
        } else {
            false
        }

        val updatedPaidPaise = transaction.amountPaidPaise + cleanPaymentPaise
        val nowSettled = planSettled || (updatedPaidPaise >= totalAccruedAmountPaise && totalAccruedAmountPaise > 0L)
        val updatedTx = transaction.copy(
            amountPaidPaise = updatedPaidPaise,
            isSettled = nowSettled,
            settledAtMillis = if (nowSettled) nowMillis else transaction.settledAtMillis
        )
        dao.updateTransaction(updatedTx)

        val formattedAmt = "${transaction.currency.symbol}${InterestCalculator.formatPaise(cleanPaymentPaise)}"
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
                        amount = InterestCalculator.paiseToRupees(cleanPaymentPaise),
                        paymentMode = paymentMode.name,
                        paidAtMillis = nowMillis,
                        encryptedNote = encNote,
                        isAcknowledged = false
                    )
                )
                cloudRepository?.updateSharedRecordTermsOrStatus(
                    remoteId = transaction.remoteId,
                    updates = mapOf(
                        "amountPaid" to InterestCalculator.paiseToRupees(updatedPaidPaise),
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

        val formattedAmt = "${transaction.currency.symbol}${InterestCalculator.formatPaise(payment.amountPaise)}"
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
    ) = deletePartialPaymentPaise(
        transaction = transaction,
        payment = payment,
        totalAccruedAmountPaise = InterestCalculator.rupeesToPaise(totalAccruedAmount)
    )

    suspend fun deletePartialPaymentPaise(
        transaction: UdhaarTransaction,
        payment: PartialPayment,
        totalAccruedAmountPaise: Long
    ) {
        dao.deletePartialPayment(payment)
        val updatedPaidPaise = (transaction.amountPaidPaise - payment.amountPaise).coerceAtLeast(0L)
        val planResult = installmentRepository?.recalculateInstallmentsForTotalPaid(
            txId = transaction.id,
            totalPaidPaise = updatedPaidPaise
        )
        val nowSettled = (planResult?.isPlanFullySettled == true) ||
            (updatedPaidPaise >= totalAccruedAmountPaise && totalAccruedAmountPaise > 0L)
        dao.updateTransaction(
            transaction.copy(
                amountPaidPaise = updatedPaidPaise,
                isSettled = nowSettled,
                settledAtMillis = if (nowSettled) transaction.settledAtMillis else null
            )
        )
    }
}
