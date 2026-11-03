package com.jyotirmoyb.udhaar.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.jyotirmoyb.udhaar.backup.BackupPreviewSummary
import com.jyotirmoyb.udhaar.backup.DriveBackupCrypto
import com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode
import com.jyotirmoyb.udhaar.backup.DriveBackupScheduler
import com.jyotirmoyb.udhaar.backup.DriveErrorType
import com.jyotirmoyb.udhaar.backup.DriveRemoteBackup
import com.jyotirmoyb.udhaar.backup.DriveResult
import com.jyotirmoyb.udhaar.backup.DriveServiceHelper
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.AutoBackupFrequency
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatThreadMetadata
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.FullLedgerSnapshot
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.util.LedgerBackupManager
import com.jyotirmoyb.udhaar.util.ParsedLedgerBackup
import com.jyotirmoyb.udhaar.util.SafeLogger
import com.jyotirmoyb.udhaar.util.UdhaarAppError
import com.jyotirmoyb.udhaar.util.UdhaarResult
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

open class BackupRepository(
    private val dao: UdhaarDao,
    private val settingsRepository: SettingsRepository,
    private val attachmentRepository: AttachmentRepository,
    private val driveServiceHelper: DriveServiceHelper,
    @ApplicationContext private val context: Context? = null
) {
    private var inMemoryAutoBackupPassphrase: String? = null

    suspend fun getFullLedgerSnapshot(): Triple<List<PartyEntity>, List<UdhaarTransaction>, List<PartialPayment>> {
        val parties = dao.getAllPartiesSnapshot()
        val transactions = dao.getAllTransactionsSnapshot()
        val payments = dao.getAllPartialPaymentsSnapshot()
        return Triple(parties, transactions, payments)
    }

    open suspend fun getFullLedgerBackupSnapshot(): FullLedgerSnapshot {
        val parties = dao.getAllPartiesSnapshot()
        val transactions = dao.getAllTransactionsSnapshot()
        val payments = dao.getAllPartialPaymentsSnapshot()
        val threads = dao.getAllChatThreadsSnapshot()
        val messages = dao.getAllChatMessagesSnapshot()
        val attachments = dao.getAllAttachmentsSnapshot()
        return FullLedgerSnapshot(parties, transactions, payments, threads, messages, attachments)
    }

    suspend fun importLedgerData(
        parties: List<PartyEntity>,
        transactions: List<UdhaarTransaction>,
        partialPayments: List<PartialPayment>,
        attachments: List<Attachment> = emptyList()
    ): Int {
        val partyIdMap = mutableMapOf<Long, Long>()
        for (party in parties) {
            val existing = dao.getPartyByName(party.name)
            val newId = if (existing != null) {
                dao.updateParty(
                    existing.copy(
                        phoneNumber = party.phoneNumber.ifBlank { existing.phoneNumber },
                        upiId = party.upiId.ifBlank { existing.upiId },
                        email = party.email.ifBlank { existing.email },
                        tagOrNotes = party.tagOrNotes.ifBlank { existing.tagOrNotes }
                    )
                )
                existing.partyId
            } else {
                dao.insertParty(party.copy(partyId = 0L))
            }
            if (party.partyId > 0L) {
                partyIdMap[party.partyId] = newId
            }
        }

        val txIdMap = mutableMapOf<Long, Long>()
        var importedTxCount = 0
        for (tx in transactions) {
            val mappedPartyId = tx.partyId?.let { partyIdMap[it] }
                ?: dao.getPartyByName(tx.partyName)?.partyId
            val computedHash = tx.contentHash.ifBlank {
                VerificationCryptoHelper.computeTransactionContentHash(tx)
            }
            val insertedTxId = dao.insertTransaction(
                tx.copy(
                    id = 0L,
                    partyId = mappedPartyId,
                    remoteId = tx.remoteId.ifBlank { UUID.randomUUID().toString() },
                    contentHash = computedHash
                )
            )
            if (tx.id > 0L) {
                txIdMap[tx.id] = insertedTxId
            }
            importedTxCount++
        }

        val paymentIdMap = mutableMapOf<Long, Long>()
        for (payment in partialPayments) {
            val targetTxId = txIdMap[payment.transactionId]
            if (targetTxId != null) {
                val insertedPaymentId = dao.insertPartialPayment(
                    payment.copy(
                        id = 0L,
                        transactionId = targetTxId
                    )
                )
                if (payment.id > 0L) {
                    paymentIdMap[payment.id] = insertedPaymentId
                }
            }
        }

        for (att in attachments) {
            val targetTxId = txIdMap[att.transactionId]
            if (targetTxId != null) {
                val targetPaymentId = att.paymentId?.let { paymentIdMap[it] }
                dao.insertAttachment(
                    att.copy(
                        id = 0L,
                        transactionId = targetTxId,
                        paymentId = targetPaymentId
                    )
                )
            }
        }

        return importedTxCount
    }

    open fun getAutoBackupPassphrase(): String? =
        settingsRepository.pinSecurityManager?.getAutoBackupPassphrase() ?: inMemoryAutoBackupPassphrase

    open fun saveAutoBackupPassphrase(passphrase: String) {
        inMemoryAutoBackupPassphrase = passphrase
        settingsRepository.pinSecurityManager?.saveAutoBackupPassphrase(passphrase)
    }

    open suspend fun updateDriveAccount(email: String) {
        settingsRepository.updateDriveAccount(email)
    }

    open suspend fun clearDriveAccount() {
        inMemoryAutoBackupPassphrase = null
        settingsRepository.clearDriveAccount()
    }

    open suspend fun updateDriveLastBackup(timeMillis: Long, sizeBytes: Long) {
        settingsRepository.updateDriveLastBackup(timeMillis, sizeBytes)
    }

    open suspend fun clearDriveBackupMetadata() {
        settingsRepository.clearDriveBackupMetadata()
    }

    open suspend fun recordDriveBackupSuccess(timeMillis: Long, sizeBytes: Long) {
        settingsRepository.recordDriveBackupSuccess(timeMillis, sizeBytes)
    }

    open suspend fun recordDriveBackupFailure(
        errorCode: DriveBackupErrorCode,
        errorMessage: String = "",
        timeMillis: Long = System.currentTimeMillis()
    ): Int = settingsRepository.recordDriveBackupFailure(errorCode, errorMessage, timeMillis)

    open suspend fun setDriveNeedsReconnect(needsReconnect: Boolean, message: String = "") {
        settingsRepository.setDriveNeedsReconnect(needsReconnect, message)
    }

    open suspend fun updateDriveAutoBackupSettings(
        frequency: AutoBackupFrequency,
        wifiOnly: Boolean,
        passphraseToSave: String? = null
    ) {
        if (!passphraseToSave.isNullOrBlank()) {
            inMemoryAutoBackupPassphrase = passphraseToSave
        } else if (frequency == AutoBackupFrequency.OFF) {
            inMemoryAutoBackupPassphrase = null
        }
        settingsRepository.updateDriveAutoBackupSettings(frequency, wifiOnly, passphraseToSave)
        context?.let { ctx ->
            DriveBackupScheduler.scheduleOrCancel(ctx, frequency, wifiOnly)
        }
    }

    suspend fun restoreFullLedgerBackup(
        backup: ParsedLedgerBackup,
        replaceExisting: Boolean
    ): Int {
        if (replaceExisting) {
            dao.clearAllLedgerData()
        }

        if (backup.settings.isNotEmpty()) {
            val s = backup.settings
            val upi = s["upiId"] as? String
            val payee = s["payeeName"] as? String
            val phone = s["phoneNumber"] as? String
            val currStr = s["defaultCurrency"] as? String
            val curr = currStr?.let { runCatching { CurrencyType.valueOf(it) }.getOrNull() } ?: CurrencyType.INR
            val inr = s["inrEnabled"] as? Boolean
            val npr = s["nprEnabled"] as? Boolean
            val themeStr = s["themeMode"] as? String
            val theme = themeStr?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
            val fontStr = s["fontScale"] as? String
            val font = fontStr?.let { runCatching { AppFontScale.valueOf(it) }.getOrNull() }

            settingsRepository.updateSettings(
                upiId = upi.orEmpty(),
                payeeName = payee.orEmpty(),
                defaultCurrency = curr,
                inrEnabled = inr,
                nprEnabled = npr,
                themeMode = theme,
                fontScale = font,
                phoneNumber = phone
            )
        }

        context?.filesDir?.let { fDir ->
            val attachDir = File(fDir, "attachments").apply { mkdirs() }
            for ((name, b64) in backup.attachments) {
                runCatching {
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                    File(attachDir, name).writeBytes(bytes)
                }
            }
        }

        val importedTxCount = importLedgerData(
            parties = backup.parties,
            transactions = backup.transactions,
            partialPayments = backup.partialPayments,
            attachments = backup.attachmentRecords
        )

        for (th in backup.chatThreads) {
            dao.upsertChatThreadMetadata(th)
        }
        for (msg in backup.chatMessages) {
            dao.insertChatMessage(msg.copy(id = 0L))
        }

        return importedTxCount
    }

    suspend fun buildFullLedgerJson(): UdhaarResult<String> {
        return try {
            val snap = getFullLedgerBackupSnapshot()
            val attachmentsMap = attachmentRepository.getLocalAttachmentsMap()
            val json = LedgerBackupManager.buildBackupJson(
                parties = snap.parties,
                transactions = snap.transactions,
                partialPayments = snap.partialPayments,
                chatThreads = snap.chatThreads,
                chatMessages = snap.chatMessages,
                attachments = attachmentsMap,
                attachmentRecords = snap.attachments
            )
            UdhaarResult.Success(json)
        } catch (t: Throwable) {
            SafeLogger.e("BackupRepository", "Failed building full ledger JSON", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Failed to generate backup JSON"), t)
        }
    }

    suspend fun restoreParsedBackup(
        backup: ParsedLedgerBackup,
        replaceExisting: Boolean
    ): UdhaarResult<Int> {
        return try {
            val count = restoreFullLedgerBackup(backup, replaceExisting)
            UdhaarResult.Success(count)
        } catch (t: Throwable) {
            SafeLogger.e("BackupRepository", "Failed restoring parsed ledger backup", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Restore failed"), t)
        }
    }

    suspend fun listDriveBackups(accessToken: String = ""): UdhaarResult<List<DriveRemoteBackup>> {
        return when (val res = driveServiceHelper.listBackups(accessToken)) {
            is DriveResult.Success -> UdhaarResult.Success(res.data)
            is DriveResult.Error -> UdhaarResult.Failure(mapDriveError(res))
        }
    }

    suspend fun encryptAndUploadToDrive(
        accessToken: String = "",
        plainJson: String,
        passphrase: String,
        preview: BackupPreviewSummary
    ): UdhaarResult<DriveRemoteBackup> {
        return try {
            val encryptedBytes = DriveBackupCrypto.encrypt(
                plainBytes = plainJson.toByteArray(Charsets.UTF_8),
                passphrase = passphrase,
                preview = preview
            )
            val fileName = "udhaar_backup_${System.currentTimeMillis()}.udhaar"
            when (
                val uploadRes = driveServiceHelper.uploadBackup(
                    accessToken = accessToken,
                    fileName = fileName,
                    encryptedBytes = encryptedBytes,
                    preview = preview
                )
            ) {
                is DriveResult.Success -> {
                    recordDriveBackupSuccess(
                        timeMillis = uploadRes.data.createdTimeMillis,
                        sizeBytes = uploadRes.data.sizeBytes
                    )
                    UdhaarResult.Success(uploadRes.data)
                }
                is DriveResult.Error -> UdhaarResult.Failure(mapDriveError(uploadRes))
            }
        } catch (t: Throwable) {
            SafeLogger.e("BackupRepository", "Failed encrypting/uploading backup to Drive", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Drive backup failed"), t)
        }
    }

    suspend fun downloadAndDecryptDriveBackup(
        accessToken: String = "",
        fileId: String,
        passphrase: String
    ): ParsedLedgerBackup {
        val downloadRes = if (accessToken.isNotBlank()) {
            driveServiceHelper.downloadBackup(accessToken, fileId)
        } else {
            driveServiceHelper.downloadBackup(fileId)
        }
        return when (downloadRes) {
            is DriveResult.Success -> {
                val decryptedBytes = DriveBackupCrypto.decrypt(downloadRes.data, passphrase)
                val plainJson = String(decryptedBytes, Charsets.UTF_8)
                LedgerBackupManager.parseBackupJson(plainJson)
            }
            is DriveResult.Error -> throw IllegalStateException(downloadRes.message)
        }
    }

    private fun mapDriveError(error: DriveResult.Error): UdhaarAppError {
        return when (error.errorType) {
            DriveErrorType.EXPIRED_SIGN_IN -> UdhaarAppError.SignedOut
            DriveErrorType.NO_NETWORK -> UdhaarAppError.NoNetwork
            DriveErrorType.QUOTA_EXCEEDED -> UdhaarAppError.QuotaExceeded
            DriveErrorType.NOT_FOUND -> UdhaarAppError.CorruptedFile
            DriveErrorType.UNKNOWN -> UdhaarAppError.UnknownError(error.message.ifBlank { "Google Drive sync error" })
        }
    }
}
