package com.jyotirmoyb.udhaar.data.repository

import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.AuditTrailEntry
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.util.SafeLogger
import com.jyotirmoyb.udhaar.util.UdhaarAppError
import com.jyotirmoyb.udhaar.util.UdhaarResult
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository responsible for ledger transactions, parties, partial payments,
 * attachments, and local audit trail events.
 */
class LedgerRepository(
    private val delegate: UdhaarRepository
) {
    val allParties: Flow<List<PartyEntity>> = delegate.allParties
    val allTransactions: Flow<List<UdhaarTransaction>> = delegate.allTransactions
    val allPartialPayments: Flow<List<PartialPayment>> = delegate.allPartialPayments
    val allAuditTrailEntries: Flow<List<AuditTrailEntry>> = delegate.allAuditTrailEntries
    val allAttachments: Flow<List<Attachment>> = delegate.allAttachments

    suspend fun saveParty(party: PartyEntity): UdhaarResult<Long> {
        return try {
            val id = delegate.saveParty(party)
            UdhaarResult.Success(id)
        } catch (t: Throwable) {
            SafeLogger.e("LedgerRepository", "Failed saving party", t)
            UdhaarResult.Failure(UdhaarAppError.DatabaseFailure("Could not save contact profile."), t)
        }
    }

    suspend fun deleteParty(party: PartyEntity): UdhaarResult<Unit> {
        return try {
            delegate.deleteParty(party)
            UdhaarResult.Success(Unit)
        } catch (t: Throwable) {
            SafeLogger.e("LedgerRepository", "Failed deleting party", t)
            UdhaarResult.Failure(UdhaarAppError.DatabaseFailure("Could not delete contact profile."), t)
        }
    }

    suspend fun mergeParties(primaryParty: PartyEntity, secondaryParty: PartyEntity): UdhaarResult<PartyEntity> {
        return try {
            val merged = delegate.mergeParties(primaryParty, secondaryParty)
            UdhaarResult.Success(merged)
        } catch (t: Throwable) {
            SafeLogger.e("LedgerRepository", "Failed merging parties", t)
            UdhaarResult.Failure(UdhaarAppError.DatabaseFailure("Could not merge contacts."), t)
        }
    }

    suspend fun saveTransaction(
        transaction: UdhaarTransaction,
        myProfileName: String = "You"
    ): UdhaarResult<Long> {
        return try {
            val id = delegate.saveTransaction(transaction, myProfileName)
            UdhaarResult.Success(id)
        } catch (t: Throwable) {
            SafeLogger.e("LedgerRepository", "Failed saving transaction", t)
            UdhaarResult.Failure(UdhaarAppError.DatabaseFailure("Could not save transaction record."), t)
        }
    }

    suspend fun deleteTransaction(transaction: UdhaarTransaction): UdhaarResult<Unit> {
        return try {
            delegate.deleteTransaction(transaction)
            UdhaarResult.Success(Unit)
        } catch (t: Throwable) {
            SafeLogger.e("LedgerRepository", "Failed deleting transaction", t)
            UdhaarResult.Failure(UdhaarAppError.DatabaseFailure("Could not delete transaction record."), t)
        }
    }

    suspend fun recordPartialPayment(
        transaction: UdhaarTransaction,
        paymentAmount: Double,
        paymentMode: PaymentMode = PaymentMode.CASH,
        note: String = "",
        totalAccruedAmount: Double,
        actorName: String = "You",
        proofAttachment: Attachment? = null
    ): UdhaarResult<Long> {
        return try {
            val id = delegate.recordPartialPayment(
                transaction = transaction,
                paymentAmount = paymentAmount,
                paymentMode = paymentMode,
                note = note,
                totalAccruedAmount = totalAccruedAmount,
                actorName = actorName,
                proofAttachment = proofAttachment
            )
            UdhaarResult.Success(id)
        } catch (t: Throwable) {
            SafeLogger.e("LedgerRepository", "Failed recording partial payment", t)
            UdhaarResult.Failure(UdhaarAppError.DatabaseFailure("Could not record partial payment."), t)
        }
    }

    suspend fun acknowledgePartialPayment(
        transaction: UdhaarTransaction,
        payment: PartialPayment,
        actorName: String = "You"
    ): UdhaarResult<Unit> {
        return try {
            delegate.acknowledgePartialPayment(transaction, payment, actorName)
            UdhaarResult.Success(Unit)
        } catch (t: Throwable) {
            SafeLogger.e("LedgerRepository", "Failed acknowledging partial payment", t)
            UdhaarResult.Failure(UdhaarAppError.DatabaseFailure("Could not confirm partial payment."), t)
        }
    }

    suspend fun deletePartialPayment(
        transaction: UdhaarTransaction,
        payment: PartialPayment,
        totalAccruedAmount: Double
    ): UdhaarResult<Unit> {
        return try {
            delegate.deletePartialPayment(transaction, payment, totalAccruedAmount)
            UdhaarResult.Success(Unit)
        } catch (t: Throwable) {
            SafeLogger.e("LedgerRepository", "Failed deleting partial payment", t)
            UdhaarResult.Failure(UdhaarAppError.DatabaseFailure("Could not delete partial payment."), t)
        }
    }

    suspend fun toggleFullSettlement(
        transaction: UdhaarTransaction,
        totalAccruedAmount: Double
    ): UdhaarResult<Unit> {
        return try {
            delegate.toggleFullSettlement(transaction, totalAccruedAmount)
            UdhaarResult.Success(Unit)
        } catch (t: Throwable) {
            SafeLogger.e("LedgerRepository", "Failed toggling settlement", t)
            UdhaarResult.Failure(UdhaarAppError.DatabaseFailure("Could not update settlement state."), t)
        }
    }

    suspend fun addAttachment(attachment: Attachment): UdhaarResult<Long> {
        return try {
            val id = delegate.insertAttachment(attachment)
            UdhaarResult.Success(id)
        } catch (t: Throwable) {
            SafeLogger.e("LedgerRepository", "Failed adding attachment", t)
            UdhaarResult.Failure(UdhaarAppError.DatabaseFailure("Could not attach file."), t)
        }
    }

    suspend fun deleteAttachment(attachment: Attachment): UdhaarResult<Unit> {
        return try {
            delegate.deleteAttachment(attachment)
            UdhaarResult.Success(Unit)
        } catch (t: Throwable) {
            SafeLogger.e("LedgerRepository", "Failed deleting attachment", t)
            UdhaarResult.Failure(UdhaarAppError.DatabaseFailure("Could not remove attachment."), t)
        }
    }
}
