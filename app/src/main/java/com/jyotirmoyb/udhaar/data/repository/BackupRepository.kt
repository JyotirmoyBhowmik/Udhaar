package com.jyotirmoyb.udhaar.data.repository

import com.jyotirmoyb.udhaar.backup.BackupPreviewSummary
import com.jyotirmoyb.udhaar.backup.DriveBackupCrypto
import com.jyotirmoyb.udhaar.backup.DriveErrorType
import com.jyotirmoyb.udhaar.backup.DriveRemoteBackup
import com.jyotirmoyb.udhaar.backup.DriveResult
import com.jyotirmoyb.udhaar.backup.DriveServiceHelper
import com.jyotirmoyb.udhaar.data.AutoBackupFrequency
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.util.LedgerBackupManager
import com.jyotirmoyb.udhaar.util.ParsedLedgerBackup
import com.jyotirmoyb.udhaar.util.SafeLogger
import com.jyotirmoyb.udhaar.util.UdhaarAppError
import com.jyotirmoyb.udhaar.util.UdhaarResult

/**
 * Domain repository managing offline JSON exports, encrypted `.udhaar` files,
 * and Google Drive (`drive.appdata`) backups using `UdhaarResult` and `UdhaarAppError`.
 */
class BackupRepository(
    private val delegate: UdhaarRepository,
    private val driveServiceHelper: DriveServiceHelper = DriveServiceHelper()
) {
    suspend fun buildFullLedgerJson(): UdhaarResult<String> {
        return try {
            val snap = delegate.getFullLedgerBackupSnapshot()
            val attachmentsMap = delegate.getLocalAttachmentsMap()
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
            val count = delegate.restoreFullLedgerBackup(backup, replaceExisting)
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
                    delegate.recordDriveBackupSuccess(
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
    ): UdhaarResult<ParsedLedgerBackup> {
        return when (val downloadRes = driveServiceHelper.downloadBackup(accessToken, fileId)) {
            is DriveResult.Success -> {
                try {
                    val decryptedBytes = DriveBackupCrypto.decrypt(downloadRes.data, passphrase)
                    val plainJson = String(decryptedBytes, Charsets.UTF_8)
                    val parsed = LedgerBackupManager.parseBackupJson(plainJson)
                    UdhaarResult.Success(parsed)
                } catch (t: Throwable) {
                    SafeLogger.w("BackupRepository", "Failed decrypting or parsing Drive backup", t)
                    UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Decryption failed"), t)
                }
            }
            is DriveResult.Error -> UdhaarResult.Failure(mapDriveError(downloadRes))
        }
    }

    suspend fun updateDriveAutoBackupSettings(
        frequency: AutoBackupFrequency,
        wifiOnly: Boolean,
        passphraseToSave: String? = null
    ): UdhaarResult<Unit> {
        return try {
            delegate.updateDriveAutoBackupSettings(frequency, wifiOnly, passphraseToSave)
            UdhaarResult.Success(Unit)
        } catch (t: Throwable) {
            SafeLogger.e("BackupRepository", "Failed updating Drive auto-backup settings", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Could not update auto-backup settings"), t)
        }
    }

    private fun mapDriveError(error: DriveResult.Error): UdhaarAppError {
        return when (error.errorType) {
            DriveErrorType.NO_NETWORK -> UdhaarAppError.NoNetwork
            DriveErrorType.EXPIRED_SIGN_IN -> UdhaarAppError.SignedOut
            DriveErrorType.QUOTA_EXCEEDED -> UdhaarAppError.QuotaExceeded
            DriveErrorType.NOT_FOUND -> UdhaarAppError.CorruptedFile
            DriveErrorType.UNKNOWN -> UdhaarAppError.Unknown(error.message)
        }
    }
}
