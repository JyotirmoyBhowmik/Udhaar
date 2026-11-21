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
    val settingsRepository: SettingsRepository,
    val attachmentRepository: AttachmentRepository,
    val driveServiceHelper: DriveServiceHelper,
    @ApplicationContext private val context: Context? = null
) {
    val userSettings get() = settingsRepository.userSettings
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
        val plans = dao.getAllInstallmentPlansSnapshot()
        val installments = dao.getAllInstallmentsSnapshot()
        return FullLedgerSnapshot(parties, transactions, payments, threads, messages, attachments, plans, installments)
    }

    suspend fun importLedgerData(
        parties: List<PartyEntity>,
        transactions: List<UdhaarTransaction>,
        partialPayments: List<PartialPayment>,
        attachments: List<Attachment> = emptyList(),
        installmentPlans: List<com.jyotirmoyb.udhaar.data.InstallmentPlan> = emptyList(),
        installments: List<com.jyotirmoyb.udhaar.data.Installment> = emptyList()
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

        val planIdMap = mutableMapOf<Long, Long>()
        for (plan in installmentPlans) {
            val targetTxId = txIdMap[plan.transactionId]
            if (targetTxId != null) {
                val insertedPlanId = dao.insertInstallmentPlan(
                    plan.copy(
                        id = 0L,
                        transactionId = targetTxId
                    )
                )
                if (plan.id > 0L) {
                    planIdMap[plan.id] = insertedPlanId
                }
            }
        }

        for (inst in installments) {
            val targetPlanId = planIdMap[inst.planId]
            if (targetPlanId != null) {
                val targetPaymentId = inst.paymentId?.let { paymentIdMap[it] }
                dao.insertInstallment(
                    inst.copy(
                        id = 0L,
                        planId = targetPlanId,
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

    companion object {
        const val MAX_BACKUP_ATTACHMENTS = 500
        const val MAX_BACKUP_ATTACHMENT_BYTES = 50 * 1024 * 1024L // 50 MB
        const val MAX_SUPPORTED_SCHEMA_VERSION = 13

        fun sanitizeAttachmentPath(targetDir: File, fileName: String): File {
            val trimmed = fileName.trim()
            if (trimmed.isBlank() || trimmed.contains("..") || trimmed.startsWith("/") || trimmed.startsWith("\\") || trimmed.contains(":")) {
                throw SecurityException("Unsafe attachment path rejected in backup: $fileName")
            }
            val safeName = File(trimmed).name
            val dest = File(targetDir, safeName)
            val canonicalDest = dest.canonicalPath
            val canonicalTarget = targetDir.canonicalPath + File.separator
            if (!canonicalDest.startsWith(canonicalTarget)) {
                throw SecurityException("Zip-slip path traversal rejected for attachment: $fileName")
            }
            return dest
        }

        private fun takeSafetySnapshot(ctx: Context?): List<File> {
            val c = ctx ?: return emptyList()
            val dbFile = c.getDatabasePath("udhaar_ledger.db")
            if (!dbFile.exists() || dbFile.length() == 0L) return emptyList()
            val safetyList = mutableListOf<File>()
            listOf(
                dbFile,
                File(dbFile.parentFile, "${dbFile.name}-wal"),
                File(dbFile.parentFile, "${dbFile.name}-shm")
            ).forEach { f ->
                if (f.exists() && f.length() > 0L) {
                    runCatching {
                        val safetyCopy = File(f.parentFile, "${f.name}.pre_restore_safety")
                        f.copyTo(safetyCopy, overwrite = true)
                        safetyList.add(safetyCopy)
                    }
                }
            }
            return safetyList
        }

        private fun rollbackSafetySnapshot(ctx: Context?, safetyList: List<File>) {
            safetyList.forEach { safety ->
                runCatching {
                    val origName = safety.name.removeSuffix(".pre_restore_safety")
                    val origFile = File(safety.parentFile, origName)
                    safety.copyTo(origFile, overwrite = true)
                    safety.delete()
                }
            }
        }

        private fun cleanupSafetySnapshot(safetyList: List<File>) {
            safetyList.forEach { runCatching { it.delete() } }
        }
    }

    suspend fun restoreFullLedgerBackup(
        backup: ParsedLedgerBackup,
        replaceExisting: Boolean
    ): Int {
        // 1. Validation BEFORE touching live data
        if (backup.schemaVersion < 1 || backup.schemaVersion > MAX_SUPPORTED_SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported backup schema version: ${backup.schemaVersion}")
        }
        if (backup.attachments.size > MAX_BACKUP_ATTACHMENTS) {
            throw SecurityException("Backup attachment count (${backup.attachments.size}) exceeds limit ($MAX_BACKUP_ATTACHMENTS)")
        }

        var totalDecodedBytes = 0L
        val decodedAttachments = mutableListOf<Pair<String, ByteArray>>()
        for ((name, b64) in backup.attachments) {
            // Verify path safety up-front
            val fakeDir = File("validation_temp")
            sanitizeAttachmentPath(fakeDir, name)

            val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
            totalDecodedBytes += bytes.size
            if (totalDecodedBytes > MAX_BACKUP_ATTACHMENT_BYTES) {
                throw SecurityException("Backup total attachment size ($totalDecodedBytes bytes) exceeds safety limit")
            }
            decodedAttachments.add(name to bytes)
        }

        // 2. Pre-restore safety snapshot
        val safetyFiles = if (replaceExisting) takeSafetySnapshot(context) else emptyList()

        return try {
            // 3. Atomically restore all database entities in ONE Room transaction
            val importedTxCount = dao.restoreLedgerDataAtomic(
                replaceExisting = replaceExisting,
                parties = backup.parties,
                transactions = backup.transactions,
                partialPayments = backup.partialPayments,
                attachments = backup.attachmentRecords,
                installmentPlans = backup.installmentPlans,
                installments = backup.installments,
                chatThreads = backup.chatThreads,
                chatMessages = backup.chatMessages
            )

            // 4. Update user settings if present
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

            // 5. Write attachments using sanitized path resolution
            context?.filesDir?.let { fDir ->
                val attachDir = File(fDir, "attachments").apply { mkdirs() }
                for ((name, bytes) in decodedAttachments) {
                    val safeFile = sanitizeAttachmentPath(attachDir, name)
                    safeFile.writeBytes(bytes)
                }
            }

            cleanupSafetySnapshot(safetyFiles)
            importedTxCount
        } catch (t: Throwable) {
            SafeLogger.e("BackupRepository", "Restore failed. Rolling back to pre-restore safety snapshot.", t)
            rollbackSafetySnapshot(context, safetyFiles)
            throw t
        }
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
                attachmentRecords = snap.attachments,
                installmentPlans = snap.installmentPlans,
                installments = snap.installments
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
