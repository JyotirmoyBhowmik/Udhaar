package com.jyotirmoyb.udhaar.data.repository

import android.content.Context
import com.jyotirmoyb.udhaar.data.AuditEventType
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.CloudBackupState
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.data.cloud.CloudAuditEvent
import com.jyotirmoyb.udhaar.data.cloud.CloudSharedRecord
import com.jyotirmoyb.udhaar.data.cloud.CloudUserProfile
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.security.EncryptedStorageManager
import com.jyotirmoyb.udhaar.util.AuditTrailHelper
import com.jyotirmoyb.udhaar.util.CloudSyncHelper
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

open class VerificationRepository(
    private val dao: UdhaarDao,
    private val settingsRepository: SettingsRepository,
    private val cloudRepository: VerificationCloudRepository? = null,
    @ApplicationContext private val context: Context? = null
) {
    suspend fun prepareVerificationShare(transaction: UdhaarTransaction): Pair<UdhaarTransaction, String> {
        val settings = settingsRepository.userSettings.first()
        val myName = settings.payeeName.ifBlank { "Udhaar User" }
        val remoteId = transaction.remoteId.ifBlank { VerificationCryptoHelper.generateShortId(10) }
        val shareToken = transaction.shareToken.ifBlank { VerificationCryptoHelper.generateShareToken() }
        val (lender, borrower) = VerificationCryptoHelper.resolveLenderAndBorrowerNames(
            type = transaction.transactionType,
            partyName = transaction.partyName,
            myProfileName = myName,
            explicitLenderName = transaction.lenderName,
            explicitBorrowerName = transaction.borrowerName
        )
        val hash = VerificationCryptoHelper.computeContentHash(
            lenderName = lender,
            borrowerName = borrower,
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

        val nextStatus = when (transaction.verificationStatus) {
            VerificationStatus.LOCAL_ONLY -> VerificationStatus.PENDING
            else -> transaction.verificationStatus
        }

        val updatedTx = transaction.copy(
            remoteId = remoteId,
            shareToken = shareToken,
            lenderName = lender,
            borrowerName = borrower,
            contentHash = hash,
            verificationStatus = nextStatus
        )
        dao.updateTransaction(updatedTx)

        val existingAudit = dao.getAuditTrailForTransactionSnapshot(transaction.id)
        if (existingAudit.none { it.eventType == AuditEventType.SHARED && it.contentHashSnapshot == hash }) {
            AuditTrailHelper.appendLocalAuditEvent(
                dao = dao,
                transactionId = transaction.id,
                eventType = AuditEventType.SHARED,
                actorUserId = settings.cloudUserId,
                actorName = myName,
                summaryText = "$myName requested verification from ${transaction.partyName} (SHA-256: ${hash.take(10)}…)",
                contentHashSnapshot = hash
            )
        }

        val encryptedNote = VerificationCryptoHelper.encryptE2eeField(
            plainText = updatedTx.notes,
            remoteId = remoteId,
            shareToken = shareToken
        )

        if (settings.cloudVerificationConsent && settings.cloudUserId.isNotBlank()) {
            runCatching {
                val safeRemoteId = remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                val participants = listOfNotNull(
                    settings.cloudUserId.takeIf { it.isNotBlank() },
                    updatedTx.counterpartyUserId.takeIf { it.isNotBlank() }
                ).distinct()
                val cloudRecord = CloudSharedRecord(
                    remoteId = safeRemoteId,
                    creatorUid = settings.cloudUserId,
                    counterpartyUid = updatedTx.counterpartyUserId,
                    participantUids = participants,
                    lenderName = lender,
                    borrowerName = borrower,
                    creatorRole = updatedTx.transactionType.name,
                    currency = updatedTx.currency.code,
                    principalAmount = updatedTx.principalAmount,
                    amountPaid = updatedTx.amountPaid,
                    paymentMode = updatedTx.paymentMode.name,
                    isInterestEnabled = updatedTx.isInterestEnabled,
                    interestType = updatedTx.interestType.name,
                    interestRate = updatedTx.interestRate,
                    interestRatePeriod = updatedTx.interestRatePeriod.name,
                    compoundFrequency = updatedTx.compoundFrequency.name,
                    startDateMillis = updatedTx.startDateMillis,
                    expiryDateMillis = updatedTx.expiryDateMillis ?: -1L,
                    contentHash = hash,
                    verificationStatus = nextStatus.name,
                    verifiedAtMillis = updatedTx.verifiedAtMillis ?: -1L,
                    disputeReason = updatedTx.disputeReason,
                    encryptedNotes = encryptedNote,
                    attachmentRefs = emptyList(),
                    isSettled = updatedTx.isSettled
                )
                cloudRepository?.createSharedRecord(cloudRecord)
                cloudRepository?.appendAuditEvent(
                    CloudAuditEvent(
                        eventId = "evt_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                        remoteId = safeRemoteId,
                        eventType = AuditEventType.SHARED.name,
                        actorUid = settings.cloudUserId,
                        actorName = myName,
                        participantUids = participants,
                        summaryText = "$myName shared record for two-party verification",
                        contentHashSnapshot = hash,
                        acknowledged = false,
                        eventTimeMillis = System.currentTimeMillis()
                    )
                )
            }
        }

        val payload = VerificationCryptoHelper.SharedVerificationPayload(
            remoteId = remoteId,
            shareToken = shareToken,
            creatorUserId = settings.cloudUserId,
            creatorRole = updatedTx.transactionType,
            lenderName = lender,
            borrowerName = borrower,
            creatorPhone = settings.phoneNumber,
            counterpartyPhone = updatedTx.phoneNumber,
            principalAmount = updatedTx.principalAmount,
            currencyCode = updatedTx.currency.code,
            startDateMillis = updatedTx.startDateMillis,
            expiryDateMillis = updatedTx.expiryDateMillis,
            isInterestEnabled = updatedTx.isInterestEnabled,
            interestType = updatedTx.interestType,
            interestRate = updatedTx.interestRate,
            interestRatePeriod = updatedTx.interestRatePeriod,
            compoundFrequency = updatedTx.compoundFrequency,
            contentHash = hash,
            encryptedNote = encryptedNote
        )

        val link = VerificationCryptoHelper.buildVerificationLink(payload)
        return updatedTx to link
    }

    suspend fun processIncomingVerificationResponse(
        payload: VerificationCryptoHelper.SharedVerificationPayload,
        confirm: Boolean,
        disputeReason: String = ""
    ): UdhaarTransaction {
        val settings = settingsRepository.userSettings.first()
        val myName = settings.payeeName.ifBlank {
            if (payload.creatorRole == TransactionType.GIVEN) payload.borrowerName else payload.lenderName
        }
        val nowMillis = System.currentTimeMillis()
        val existing = dao.getTransactionByRemoteId(payload.remoteId)
        val currency = runCatching { CurrencyType.valueOf(payload.currencyCode) }.getOrDefault(CurrencyType.INR)
        val decryptedNote = VerificationCryptoHelper.decryptE2eeField(
            cipherText = payload.encryptedNote,
            remoteId = payload.remoteId,
            shareToken = payload.shareToken
        )

        val canonicalHash = VerificationCryptoHelper.computeContentHash(
            lenderName = payload.lenderName,
            borrowerName = payload.borrowerName,
            principalAmount = payload.principalAmount,
            currencyCode = currency.code,
            startDateMillis = payload.startDateMillis,
            expiryDateMillis = payload.expiryDateMillis,
            isInterestEnabled = payload.isInterestEnabled,
            interestType = payload.interestType,
            interestRate = payload.interestRate,
            interestRatePeriod = payload.interestRatePeriod,
            compoundFrequency = payload.compoundFrequency
        )

        val newStatus = if (confirm) VerificationStatus.CONFIRMED else VerificationStatus.DISPUTED

        val resultingTx: UdhaarTransaction = if (existing != null) {
            val updated = existing.copy(
                verificationStatus = newStatus,
                verifiedAtMillis = if (confirm) nowMillis else existing.verifiedAtMillis,
                needsMyConfirmation = false,
                disputeReason = if (confirm) "" else disputeReason.trim(),
                contentHash = canonicalHash,
                lenderName = payload.lenderName,
                borrowerName = payload.borrowerName
            )
            dao.updateTransaction(updated)
            updated
        } else {
            val mirroredType = if (payload.creatorRole == TransactionType.GIVEN) {
                TransactionType.TAKEN
            } else {
                TransactionType.GIVEN
            }
            val counterpartyName = if (mirroredType == TransactionType.TAKEN) {
                payload.lenderName
            } else {
                payload.borrowerName
            }
            val counterpartyPhone = payload.creatorPhone

            val linkedParty = dao.getPartyByName(counterpartyName)
            val partyId = linkedParty?.partyId ?: dao.insertParty(
                PartyEntity(
                    name = counterpartyName,
                    phoneNumber = counterpartyPhone,
                    preferredCurrency = currency
                )
            )

            val mirroredTx = UdhaarTransaction(
                partyId = partyId,
                transactionType = mirroredType,
                partyName = counterpartyName,
                phoneNumber = counterpartyPhone,
                currency = currency,
                principalAmount = payload.principalAmount,
                amountPaid = 0.0,
                paymentMode = PaymentMode.UPI,
                isInterestEnabled = payload.isInterestEnabled,
                interestType = payload.interestType,
                interestRate = payload.interestRate,
                interestRatePeriod = payload.interestRatePeriod,
                compoundFrequency = payload.compoundFrequency,
                startDateMillis = payload.startDateMillis,
                expiryDateMillis = payload.expiryDateMillis,
                notes = decryptedNote,
                remoteId = payload.remoteId,
                verificationStatus = newStatus,
                verifiedAtMillis = if (confirm) nowMillis else null,
                counterpartyUserId = payload.creatorUserId,
                contentHash = canonicalHash,
                needsMyConfirmation = false,
                shareToken = payload.shareToken,
                disputeReason = if (confirm) "" else disputeReason.trim(),
                lenderName = payload.lenderName,
                borrowerName = payload.borrowerName
            )
            val newId = dao.insertTransaction(mirroredTx)
            AuditTrailHelper.appendLocalAuditEvent(
                dao = dao,
                transactionId = newId,
                eventType = AuditEventType.VIEWED,
                actorUserId = settings.cloudUserId,
                actorName = myName,
                summaryText = "$myName opened shared verification link from $counterpartyName",
                contentHashSnapshot = canonicalHash
            )
            mirroredTx.copy(id = newId)
        }

        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = resultingTx.id,
            eventType = if (confirm) AuditEventType.CONFIRMED else AuditEventType.DISPUTED,
            actorUserId = settings.cloudUserId,
            actorName = myName,
            summaryText = if (confirm) {
                "$myName confirmed mirrored record (SHA-256: ${canonicalHash.take(10)}…)"
            } else {
                "$myName disputed record: ${disputeReason.ifBlank { "Terms disputed" }}"
            },
            contentHashSnapshot = canonicalHash,
            isAcknowledged = confirm
        )

        if (settings.cloudVerificationConsent &&
            settings.cloudUserId.isNotBlank() &&
            payload.remoteId.isNotBlank() &&
            payload.creatorUserId.isNotBlank() &&
            payload.creatorUserId != settings.cloudUserId
        ) {
            runCatching {
                val safeRemoteId = payload.remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                cloudRepository?.claimAndRespondSharedRecord(
                    remoteId = safeRemoteId,
                    creatorUid = payload.creatorUserId,
                    counterpartyUid = settings.cloudUserId,
                    verificationStatus = newStatus.name,
                    verifiedAtMillis = if (confirm) nowMillis else -1L,
                    disputeReason = if (confirm) "" else disputeReason.trim()
                )
                cloudRepository?.appendAuditEvent(
                    CloudAuditEvent(
                        eventId = "evt_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                        remoteId = safeRemoteId,
                        eventType = if (confirm) AuditEventType.CONFIRMED.name else AuditEventType.DISPUTED.name,
                        actorUid = settings.cloudUserId,
                        actorName = myName,
                        participantUids = listOf(payload.creatorUserId, settings.cloudUserId).distinct(),
                        summaryText = if (confirm) "$myName confirmed the record" else "$myName disputed: $disputeReason",
                        contentHashSnapshot = canonicalHash,
                        acknowledged = confirm,
                        eventTimeMillis = nowMillis
                    )
                )
            }
        }

        return resultingTx
    }

    suspend fun confirmOrDisputeExistingTransaction(
        transaction: UdhaarTransaction,
        confirm: Boolean,
        disputeReason: String = ""
    ) {
        val settings = settingsRepository.userSettings.first()
        val myName = settings.payeeName.ifBlank { "You" }
        val nowMillis = System.currentTimeMillis()
        val nextStatus = if (confirm) VerificationStatus.CONFIRMED else VerificationStatus.DISPUTED
        val updated = transaction.copy(
            verificationStatus = nextStatus,
            verifiedAtMillis = if (confirm) nowMillis else transaction.verifiedAtMillis,
            needsMyConfirmation = false,
            disputeReason = if (confirm) "" else disputeReason.trim()
        )
        dao.updateTransaction(updated)

        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = transaction.id,
            eventType = if (confirm) AuditEventType.CONFIRMED else AuditEventType.DISPUTED,
            actorUserId = settings.cloudUserId,
            actorName = myName,
            summaryText = if (confirm) {
                "$myName confirmed record (SHA-256: ${transaction.contentHash.take(10)}…)"
            } else {
                "$myName disputed record: ${disputeReason.ifBlank { "Terms disputed" }}"
            },
            contentHashSnapshot = transaction.contentHash,
            isAcknowledged = confirm
        )

        if (settings.cloudVerificationConsent && settings.cloudUserId.isNotBlank() && transaction.remoteId.isNotBlank()) {
            runCatching {
                val safeRemoteId = transaction.remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                cloudRepository?.updateSharedRecordTermsOrStatus(
                    remoteId = safeRemoteId,
                    updates = mapOf(
                        "verificationStatus" to nextStatus.name,
                        "verifiedAtMillis" to (if (confirm) nowMillis else -1L),
                        "disputeReason" to (if (confirm) "" else disputeReason.trim())
                    )
                )
            }
        }
    }

    suspend fun acceptSharedPaymentProfile(
        profile: VerificationCryptoHelper.SharedPaymentProfilePayload,
        targetPartyId: Long? = null
    ): PartyEntity {
        val existingParty = when {
            targetPartyId != null && targetPartyId > 0L -> dao.getPartyById(targetPartyId)
            profile.displayName.isNotBlank() -> dao.getPartyByName(profile.displayName.trim())
            else -> null
        }

        return if (existingParty != null) {
            val updated = existingParty.copy(
                upiId = profile.upiId.trim().ifBlank { existingParty.upiId },
                phoneNumber = profile.phoneNumber.trim().ifBlank { existingParty.phoneNumber }
            )
            dao.updateParty(updated)
            updated
        } else {
            val newParty = PartyEntity(
                name = profile.displayName.trim().ifBlank { "Shared Contact" },
                phoneNumber = profile.phoneNumber.trim(),
                upiId = profile.upiId.trim(),
                preferredCurrency = CurrencyType.INR
            )
            val newId = dao.insertParty(newParty)
            newParty.copy(partyId = newId)
        }
    }

    suspend fun syncProfileAndRecordsToCloud(userIdOverride: String? = null): Result<Long> {
        return CloudSyncHelper.syncProfileAndRecordsToCloud(
            dao = dao,
            settingsRepository = settingsRepository,
            cloudRepository = cloudRepository,
            context = context,
            userIdOverride = userIdOverride
        )
    }

    suspend fun deleteMyCloudData() {
        CloudSyncHelper.deleteMyCloudData(
            dao = dao,
            settingsRepository = settingsRepository,
            cloudRepository = cloudRepository
        )
    }
}
