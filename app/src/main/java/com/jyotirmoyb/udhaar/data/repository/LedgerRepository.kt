package com.jyotirmoyb.udhaar.data.repository

import android.content.Context
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.AuditEventType
import com.jyotirmoyb.udhaar.data.AuditTrailEntry
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.ReminderChannel
import com.jyotirmoyb.udhaar.data.ReminderEvent
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.data.cloud.CloudAuditEvent
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.reminder.ReminderEngine
import com.jyotirmoyb.udhaar.util.AttachmentManager
import com.jyotirmoyb.udhaar.util.AuditTrailHelper
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.SafeLogger
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

open class LedgerRepository(
    private val dao: UdhaarDao,
    private val settingsRepository: SettingsRepository,
    private val cloudRepository: VerificationCloudRepository? = null,
    @ApplicationContext private val context: Context? = null
) {
    val allTransactions: Flow<List<UdhaarTransaction>> = dao.getAllTransactions().catch { e ->
        SafeLogger.e("LedgerRepository", "Failed reading transactions flow", e)
        emit(emptyList())
    }

    val allParties: Flow<List<PartyEntity>> = dao.getAllParties().catch { e ->
        SafeLogger.e("LedgerRepository", "Failed reading parties flow", e)
        emit(emptyList())
    }

    val allPartialPayments: Flow<List<PartialPayment>> = dao.getAllPartialPayments().catch { e ->
        SafeLogger.e("LedgerRepository", "Failed reading partial payments flow", e)
        emit(emptyList())
    }

    val allAttachments: Flow<List<Attachment>> = dao.getAllAttachmentsFlow().catch { e ->
        SafeLogger.e("LedgerRepository", "Failed reading attachments flow", e)
        emit(emptyList())
    }

    val allAuditTrailEntries: Flow<List<AuditTrailEntry>> = dao.getAllAuditTrailEntries().catch { e ->
        SafeLogger.e("LedgerRepository", "Failed reading audit trail flow", e)
        emit(emptyList())
    }

    fun getAuditTrail(transactionId: Long): Flow<List<AuditTrailEntry>> =
        dao.getAuditTrailForTransaction(transactionId)

    suspend fun getTransactionById(id: Long): UdhaarTransaction? =
        dao.getTransactionById(id)

    suspend fun getTransactionByRemoteId(remoteId: String): UdhaarTransaction? =
        dao.getTransactionByRemoteId(remoteId)

    suspend fun snoozeTransaction(recordId: Long, days: Int) {
        val targetMillis = ReminderEngine.calculateSnoozeDate(days)
        dao.updateTransactionSnooze(recordId, targetMillis)
    }

    suspend fun clearTransactionSnooze(recordId: Long) {
        dao.updateTransactionSnooze(recordId, null)
    }

    suspend fun stopTransactionReminders(recordId: Long) {
        dao.updateTransactionRemindersDisabled(recordId, true)
    }

    suspend fun enableTransactionReminders(recordId: Long) {
        dao.updateTransactionRemindersDisabled(recordId, false)
    }

    suspend fun recordReminderEvent(recordId: Long, level: Int, channel: ReminderChannel) {
        val now = System.currentTimeMillis()
        dao.insertReminderEvent(
            ReminderEvent(
                recordId = recordId,
                level = level,
                sentTimeMillis = now,
                channel = channel
            )
        )
        dao.updateTransactionLastReminder(recordId, now, level)
    }

    fun getReminderEvents(recordId: Long): Flow<List<ReminderEvent>> =
        dao.getReminderEventsForRecord(recordId)

    suspend fun saveTransaction(
        transaction: UdhaarTransaction,
        myProfileName: String = "You"
    ): Long {
        val formattedPhone = InterestCalculator.formatPhoneWithCountryCode(
            transaction.phoneNumber,
            transaction.currency
        )
        val linkedPartyId = if (transaction.partyId != null && transaction.partyId > 0L) {
            transaction.partyId
        } else {
            val existingParty = dao.getPartyByName(transaction.partyName.trim())
            if (existingParty != null) {
                if (existingParty.phoneNumber.isBlank() && formattedPhone.isNotBlank()) {
                    dao.updateParty(existingParty.copy(phoneNumber = formattedPhone))
                }
                existingParty.partyId
            } else {
                dao.insertParty(
                    PartyEntity(
                        name = transaction.partyName.trim(),
                        phoneNumber = formattedPhone,
                        preferredCurrency = transaction.currency
                    )
                )
            }
        }

        val existingTx = if (transaction.id > 0L) dao.getTransactionById(transaction.id) else null
        val effectiveRemoteId = transaction.remoteId.ifBlank {
            existingTx?.remoteId?.ifBlank { UUID.randomUUID().toString() } ?: UUID.randomUUID().toString()
        }
        val effectiveShareToken = transaction.shareToken.ifBlank {
            existingTx?.shareToken?.ifBlank { UUID.randomUUID().toString().replace("-", "") }
                ?: UUID.randomUUID().toString().replace("-", "")
        }

        val (lenderName, borrowerName) = VerificationCryptoHelper.resolveLenderAndBorrowerNames(
            type = transaction.transactionType,
            partyName = transaction.partyName,
            myProfileName = myProfileName,
            explicitLenderName = transaction.lenderName.ifBlank { existingTx?.lenderName.orEmpty() },
            explicitBorrowerName = transaction.borrowerName.ifBlank { existingTx?.borrowerName.orEmpty() }
        )

        val computedHash = VerificationCryptoHelper.computeContentHash(
            lenderName = lenderName,
            borrowerName = borrowerName,
            principalAmount = transaction.principalAmount,
            currencyCode = transaction.currency.code,
            startDateMillis = transaction.startDateMillis,
            expiryDateMillis = transaction.expiryDateMillis,
            isInterestEnabled = transaction.isInterestEnabled,
            interestType = transaction.interestType,
            interestRate = transaction.interestRate,
            interestRatePeriod = transaction.interestRatePeriod,
            compoundFrequency = transaction.compoundFrequency
        )

        val termsChanged = existingTx != null && existingTx.contentHash.isNotBlank() &&
            existingTx.contentHash != computedHash

        val nextVerificationStatus = when {
            existingTx == null -> transaction.verificationStatus
            termsChanged && (
                existingTx.verificationStatus == VerificationStatus.CONFIRMED ||
                    existingTx.verificationStatus == VerificationStatus.DISPUTED
                ) -> VerificationStatus.EDITED_AFTER_CONFIRM
            else -> transaction.verificationStatus
        }

        val preparedTx = transaction.copy(
            partyId = linkedPartyId,
            partyName = transaction.partyName.trim(),
            phoneNumber = formattedPhone,
            remoteId = effectiveRemoteId,
            shareToken = effectiveShareToken,
            lenderName = lenderName,
            borrowerName = borrowerName,
            contentHash = computedHash,
            verificationStatus = nextVerificationStatus,
            needsMyConfirmation = if (termsChanged) false else transaction.needsMyConfirmation
        )

        val savedId = dao.insertTransaction(preparedTx)
        val actorDisplay = myProfileName.trim().ifEmpty { "You" }

        if (existingTx == null) {
            AuditTrailHelper.appendLocalAuditEvent(
                dao = dao,
                transactionId = savedId,
                eventType = AuditEventType.CREATED,
                actorName = actorDisplay,
                summaryText = "$actorDisplay created ${preparedTx.transactionType.label} record of ${InterestCalculator.formatCurrencyPaise(preparedTx.principalAmountPaise, preparedTx.currency)} with ${preparedTx.partyName}",
                contentHashSnapshot = computedHash
            )
        } else if (termsChanged) {
            AuditTrailHelper.appendLocalAuditEvent(
                dao = dao,
                transactionId = savedId,
                eventType = AuditEventType.EDITED,
                actorName = actorDisplay,
                summaryText = "$actorDisplay edited record terms (new hash ${computedHash.take(8)}…). Re-confirmation required.",
                contentHashSnapshot = computedHash
            )
            syncEditedTermsToCloudIfEnabled(preparedTx.copy(id = savedId), actorDisplay)
        }

        return savedId
    }

    suspend fun updateTransaction(transaction: UdhaarTransaction) =
        dao.updateTransaction(transaction)

    suspend fun deleteTransaction(transaction: UdhaarTransaction) {
        val txId = transaction.id
        if (txId > 0L) {
            val txAttachments = dao.getAttachmentsForTransactionSnapshot(txId)
            context?.let { ctx ->
                for (att in txAttachments) {
                    AttachmentManager.deleteAttachmentFile(ctx, att.localPath)
                }
            }
            dao.deleteAttachmentsForTransaction(txId)
            dao.deletePartialPaymentsForTransaction(txId)
            dao.deleteAuditTrailForTransaction(txId)
            dao.deleteChatMessagesForTransaction(txId)
            dao.deleteChatThreadsForTransaction(txId)
            dao.deleteTransactionById(txId)
        } else {
            dao.deleteTransaction(transaction)
        }

        val settings = settingsRepository.userSettings.first()
        if (settings.cloudVerificationConsent && settings.cloudUserId.isNotBlank() && transaction.remoteId.isNotBlank()) {
            runCatching {
                val safeRemoteId = transaction.remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                cloudRepository?.deleteSharedRecord(safeRemoteId)
            }
        }
    }

    suspend fun toggleFullSettlement(transaction: UdhaarTransaction, totalAccruedAmount: Double) {
        val settings = settingsRepository.userSettings.first()
        val actorName = settings.payeeName.ifBlank { "You" }
        if (transaction.isSettled) {
            dao.updateTransaction(
                transaction.copy(
                    isSettled = false,
                    settledAtMillis = null
                )
            )
            AuditTrailHelper.appendLocalAuditEvent(
                dao = dao,
                transactionId = transaction.id,
                eventType = AuditEventType.EDITED,
                actorUserId = settings.cloudUserId,
                actorName = actorName,
                summaryText = "$actorName reopened settled record",
                contentHashSnapshot = transaction.contentHash
            )
        } else {
            val now = System.currentTimeMillis()
            dao.updateTransaction(
                transaction.copy(
                    isSettled = true,
                    settledAtMillis = now
                )
            )
            AuditTrailHelper.appendLocalAuditEvent(
                dao = dao,
                transactionId = transaction.id,
                eventType = AuditEventType.SETTLED,
                actorUserId = settings.cloudUserId,
                actorName = actorName,
                summaryText = "$actorName marked record settled",
                contentHashSnapshot = transaction.contentHash,
                isAcknowledged = true
            )
        }
    }

    private suspend fun syncEditedTermsToCloudIfEnabled(
        tx: UdhaarTransaction,
        actorName: String
    ) {
        val settings = settingsRepository.userSettings.first()
        if (!settings.cloudVerificationConsent || settings.cloudUserId.isBlank() || tx.remoteId.isBlank()) return
        runCatching {
            val safeRemoteId = tx.remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            cloudRepository?.updateSharedRecordTermsOrStatus(
                remoteId = safeRemoteId,
                updates = mapOf(
                    "principalAmount" to tx.principalAmount,
                    "currency" to tx.currency.code,
                    "isInterestEnabled" to tx.isInterestEnabled,
                    "interestType" to tx.interestType.name,
                    "interestRate" to tx.interestRate,
                    "interestRatePeriod" to tx.interestRatePeriod.name,
                    "compoundFrequency" to tx.compoundFrequency.name,
                    "startDateMillis" to tx.startDateMillis,
                    "expiryDateMillis" to (tx.expiryDateMillis ?: -1L),
                    "contentHash" to tx.contentHash,
                    "verificationStatus" to VerificationStatus.EDITED_AFTER_CONFIRM.name
                )
            )
            val participants = listOfNotNull(
                settings.cloudUserId.takeIf { it.isNotBlank() },
                tx.counterpartyUserId.takeIf { it.isNotBlank() }
            ).distinct()
            cloudRepository?.appendAuditEvent(
                CloudAuditEvent(
                    eventId = "evt_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    remoteId = safeRemoteId,
                    eventType = AuditEventType.EDITED.name,
                    actorUid = settings.cloudUserId,
                    actorName = actorName,
                    participantUids = participants,
                    summaryText = "$actorName edited terms; re-confirmation required",
                    contentHashSnapshot = tx.contentHash,
                    acknowledged = false,
                    eventTimeMillis = System.currentTimeMillis()
                )
            )
        }
    }
}
