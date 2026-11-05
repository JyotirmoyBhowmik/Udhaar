package com.jyotirmoyb.udhaar.util

import android.content.Context
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.data.cloud.CloudSharedRecord
import com.jyotirmoyb.udhaar.data.cloud.CloudUserProfile
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.data.repository.SettingsRepository
import com.jyotirmoyb.udhaar.security.EncryptedStorageManager
import kotlinx.coroutines.flow.first
import java.util.UUID

object CloudSyncHelper {

    suspend fun syncProfileAndRecordsToCloud(
        dao: UdhaarDao,
        settingsRepository: SettingsRepository,
        cloudRepository: VerificationCloudRepository?,
        context: Context?,
        userIdOverride: String? = null
    ): Result<Long> {
        val current = settingsRepository.userSettings.first()
        val uid = userIdOverride?.trim()?.ifBlank { current.cloudUserId } ?: current.cloudUserId
        if (!current.cloudVerificationConsent || uid.isBlank()) {
            return Result.failure(IllegalStateException("Google Sign-In required for cloud backup."))
        }

        settingsRepository.setCloudAccountState(
            consentGranted = current.cloudVerificationConsent,
            userId = uid,
            email = current.cloudUserEmail
        )

        return runCatching {
            val cloud = cloudRepository
                ?: throw IllegalStateException("Cloud backup service is unavailable.")
            val fcmToken = settingsRepository.resolveCurrentFcmToken()
            val myName = current.payeeName.ifBlank { "Udhaar User" }

            cloud.upsertUserProfile(
                CloudUserProfile(
                    userId = uid,
                    displayName = myName.take(100),
                    phoneNumber = current.phoneNumber.take(30),
                    upiId = current.upiId.take(120),
                    photoUri = current.profilePhotoUri.take(1024),
                    fcmToken = fcmToken.take(512)
                )
            )

            val allTxs = dao.getAllTransactionsSnapshot()
            for (tx in allTxs) {
                val remoteId = tx.remoteId.ifBlank { UUID.randomUUID().toString() }
                val shareToken = tx.shareToken.ifBlank { UUID.randomUUID().toString().replace("-", "") }
                val safeRemoteId = remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
                if (safeRemoteId.isBlank()) continue

                val (lender, borrower) = VerificationCryptoHelper.resolveLenderAndBorrowerNames(
                    type = tx.transactionType,
                    partyName = tx.partyName,
                    myProfileName = myName,
                    explicitLenderName = tx.lenderName,
                    explicitBorrowerName = tx.borrowerName
                )
                val safeLender = lender.ifBlank { "Lender" }.take(120)
                val safeBorrower = borrower.ifBlank { "Borrower" }.take(120)
                val hash = tx.contentHash.takeIf { it.length == 64 }
                    ?: VerificationCryptoHelper.computeContentHash(
                        lenderName = safeLender,
                        borrowerName = safeBorrower,
                        principalAmount = InterestCalculator.paiseToRupees(tx.principalAmountPaise),
                        currencyCode = tx.currency.code,
                        startDateMillis = tx.startDateMillis,
                        expiryDateMillis = tx.expiryDateMillis,
                        isInterestEnabled = tx.isInterestEnabled,
                        interestType = tx.interestType,
                        interestRate = tx.interestRate,
                        interestRatePeriod = tx.interestRatePeriod,
                        compoundFrequency = tx.compoundFrequency
                    )

                if (tx.remoteId.isBlank() || tx.shareToken.isBlank() || tx.contentHash.isBlank()) {
                    dao.updateTransaction(
                        tx.copy(
                            remoteId = remoteId,
                            shareToken = shareToken,
                            lenderName = safeLender,
                            borrowerName = safeBorrower,
                            contentHash = hash
                        )
                    )
                }

                val encryptedNote = VerificationCryptoHelper.encryptE2eeField(
                    plainText = tx.notes,
                    remoteId = remoteId,
                    shareToken = shareToken
                ).take(8000)

                val participants = listOfNotNull(
                    uid,
                    tx.counterpartyUserId.takeIf { it.isNotBlank() }
                ).distinct()

                val statusForCloud = if (tx.verificationStatus == VerificationStatus.LOCAL_ONLY) {
                    VerificationStatus.PENDING.name
                } else {
                    tx.verificationStatus.name
                }

                val cloudRecord = CloudSharedRecord(
                    remoteId = safeRemoteId,
                    creatorUid = uid,
                    counterpartyUid = tx.counterpartyUserId.take(128),
                    participantUids = participants,
                    lenderName = safeLender,
                    borrowerName = safeBorrower,
                    creatorRole = tx.transactionType.name,
                    currency = tx.currency.code,
                    principalAmount = InterestCalculator.paiseToRupees(tx.principalAmountPaise).coerceIn(0.01, 1_000_000_000.0),
                    amountPaid = InterestCalculator.paiseToRupees(tx.amountPaidPaise).coerceIn(0.0, 1_000_000_000.0),
                    paymentMode = tx.paymentMode.name,
                    isInterestEnabled = tx.isInterestEnabled,
                    interestType = tx.interestType.name,
                    interestRate = tx.interestRate.coerceIn(0.0, 1000.0),
                    interestRatePeriod = tx.interestRatePeriod.name,
                    compoundFrequency = tx.compoundFrequency.name,
                    startDateMillis = tx.startDateMillis.coerceAtLeast(1L),
                    expiryDateMillis = (tx.expiryDateMillis ?: -1L).coerceAtLeast(-1L),
                    contentHash = hash,
                    verificationStatus = statusForCloud,
                    verifiedAtMillis = (tx.verifiedAtMillis ?: -1L).coerceAtLeast(-1L),
                    disputeReason = tx.disputeReason.take(500),
                    encryptedNotes = encryptedNote,
                    attachmentRefs = emptyList(),
                    isSettled = tx.isSettled
                )
                cloud.upsertSharedRecordForBackup(cloudRecord)
            }

            val completedAtMillis = System.currentTimeMillis()
            context?.let { ctx ->
                runCatching {
                    EncryptedStorageManager.getEncryptedPreferences(ctx)
                        .edit()
                        .putLong("cloud_last_backup_time", completedAtMillis)
                        .apply()
                }
            }
            completedAtMillis
        }
    }

    suspend fun deleteMyCloudData(
        dao: UdhaarDao,
        settingsRepository: SettingsRepository,
        cloudRepository: VerificationCloudRepository?
    ) {
        val current = settingsRepository.userSettings.first()
        val allTxs = dao.getAllTransactionsSnapshot()
        val remoteIds = allTxs.mapNotNull { tx ->
            tx.remoteId.takeIf { it.isNotBlank() }?.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        }
        val allThreads = dao.getAllChatThreadsSnapshot()
        val createdThreadIds = allThreads.mapNotNull { t ->
            val remoteId = t.remoteThreadId
            if (remoteId.isNotBlank()) {
                "tx_${remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}"
            } else if (t.threadScope == ChatThreadScope.PARTY_GENERAL && t.partyName.isNotBlank()) {
                "party_${t.partyName.trim().lowercase().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}"
            } else null
        }.distinct()
        if (current.cloudUserId.isNotBlank()) {
            runCatching {
                cloudRepository?.deleteAllMyCloudData(
                    userId = current.cloudUserId,
                    createdRemoteIds = remoteIds,
                    createdThreadIds = createdThreadIds
                )
            }
        }
        for (tx in allTxs) {
            if (tx.verificationStatus != VerificationStatus.LOCAL_ONLY) {
                dao.updateTransaction(
                    tx.copy(
                        verificationStatus = VerificationStatus.LOCAL_ONLY,
                        verifiedAtMillis = null,
                        needsMyConfirmation = false,
                        disputeReason = ""
                    )
                )
            }
        }
        settingsRepository.setCloudAccountState(
            consentGranted = false,
            userId = "",
            email = ""
        )
    }
}
