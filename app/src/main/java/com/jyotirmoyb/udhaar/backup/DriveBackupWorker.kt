package com.jyotirmoyb.udhaar.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.util.LedgerBackupManager
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DriveBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = UdhaarDatabase.getInstance(applicationContext)
        val repository = UdhaarRepository(database.udhaarDao(), applicationContext)
        val settings = repository.userSettings.first()

        val token = settings.driveAccessToken
        val email = settings.driveUserEmail
        if (token.isBlank() || email.isBlank()) {
            return Result.success()
        }

        val passphrase = repository.getAutoBackupPassphrase()
        if (passphrase.isNullOrBlank()) {
            return Result.success()
        }

        val snapshot = repository.getFullLedgerBackupSnapshot()
        val parties = snapshot.parties
        val transactions = snapshot.transactions
        val payments = snapshot.partialPayments
        val threads = snapshot.chatThreads
        val messages = snapshot.chatMessages
        val attachments = repository.getLocalAttachmentsMap()

        val settingsMap = mapOf(
            "upiId" to settings.upiId,
            "payeeName" to settings.payeeName,
            "phoneNumber" to settings.phoneNumber,
            "profilePhotoUri" to settings.profilePhotoUri,
            "defaultCurrency" to settings.defaultCurrency.name,
            "inrEnabled" to settings.inrEnabled,
            "nprEnabled" to settings.nprEnabled,
            "themeMode" to settings.themeMode.name,
            "fontScale" to settings.fontScale.name
        )

        val plainJson = LedgerBackupManager.buildBackupJson(
            parties = parties,
            transactions = transactions,
            partialPayments = payments,
            chatThreads = threads,
            chatMessages = messages,
            settings = settingsMap,
            attachments = attachments,
            attachmentRecords = snapshot.attachments
        )

        val preview = BackupPreviewSummary(
            partiesCount = parties.size,
            transactionsCount = transactions.size,
            paymentsCount = payments.size,
            attachmentsCount = attachments.size,
            appVersion = "7.0",
            createdAtMillis = System.currentTimeMillis()
        )

        val encryptedBytes = try {
            DriveBackupCrypto.encrypt(plainJson.toByteArray(Charsets.UTF_8), passphrase, preview)
        } catch (_: Exception) {
            return Result.failure()
        }

        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "udhaar_backup_${dateStr}.udhaar"

        val driveHelper = DriveServiceHelper()
        val uploadResult = driveHelper.uploadBackup(token, fileName, encryptedBytes, preview)

        return when (uploadResult) {
            is DriveResult.Success -> {
                repository.updateDriveLastBackup(System.currentTimeMillis(), uploadResult.data.sizeBytes)
                Result.success()
            }
            is DriveResult.Error -> {
                if (uploadResult.errorType == DriveErrorType.NO_NETWORK) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }
}
