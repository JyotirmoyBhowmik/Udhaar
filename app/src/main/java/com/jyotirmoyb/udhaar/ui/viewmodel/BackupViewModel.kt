package com.jyotirmoyb.udhaar.ui.viewmodel

import android.app.PendingIntent
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.udhaar.BuildConfig
import com.jyotirmoyb.udhaar.backup.BackupPreviewSummary
import com.jyotirmoyb.udhaar.backup.DriveBackupCrypto
import com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode
import com.jyotirmoyb.udhaar.backup.DriveBackupWorker
import com.jyotirmoyb.udhaar.backup.DriveConnectionTestReport
import com.jyotirmoyb.udhaar.backup.DriveErrorType
import com.jyotirmoyb.udhaar.backup.DriveRemoteBackup
import com.jyotirmoyb.udhaar.backup.DriveResult
import com.jyotirmoyb.udhaar.data.AutoBackupFrequency
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.data.repository.BackupRepository
import com.jyotirmoyb.udhaar.ui.PartyKhataSummary
import com.jyotirmoyb.udhaar.ui.state.AppStateHolder
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.util.AppSigningCertificateHelper
import com.jyotirmoyb.udhaar.util.AppSigningInfo
import com.jyotirmoyb.udhaar.util.FullLedgerSnapshot
import com.jyotirmoyb.udhaar.util.LedgerBackupManager
import com.jyotirmoyb.udhaar.util.ParsedLedgerBackup
import com.jyotirmoyb.udhaar.util.SafeLogger
import com.jyotirmoyb.udhaar.util.UdhaarAppError
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class BackupUiState(
    val driveRemoteBackups: List<DriveRemoteBackup> = emptyList(),
    val isDriveOperationInProgress: Boolean = false,
    val driveOperationStatusMessage: String? = null,
    val driveOperationError: String? = null,
    val driveResolutionPendingIntent: PendingIntent? = null,
    val driveConnectionTestReport: DriveConnectionTestReport? = null,
    val pendingRestoreBackup: DriveRemoteBackup? = null,
    val pendingRestoreParsed: ParsedLedgerBackup? = null,
    val showRestorePreviewDialog: Boolean = false,
    val showBackupPassphraseDialog: Boolean = false,
    val showRestorePassphraseDialog: Boolean = false,
    val restoreSelectedBackup: DriveRemoteBackup? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val appStateHolder: AppStateHolder,
    @ApplicationContext private val appContext: Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun setDriveAccountState(initialEmail: String = "") {
        viewModelScope.launch {
            val emailToUse = if (initialEmail.isNotBlank()) {
                initialEmail
            } else {
                backupRepository.driveServiceHelper.fetchConnectedAccountEmail().orEmpty()
            }
            if (emailToUse.isNotBlank()) {
                backupRepository.updateDriveAccount(emailToUse)
                refreshDriveBackups()
            }
        }
    }

    fun handleDriveNeedsResolution(pendingIntent: PendingIntent?, message: String) {
        _uiState.update {
            it.copy(
                driveResolutionPendingIntent = pendingIntent,
                driveOperationError = message
            )
        }
    }

    fun clearDriveAccountState() {
        viewModelScope.launch {
            backupRepository.clearDriveAccount()
            _uiState.update {
                it.copy(
                    driveRemoteBackups = emptyList(),
                    driveOperationError = null,
                    driveOperationStatusMessage = null,
                    driveResolutionPendingIntent = null
                )
            }
        }
    }

    fun openBackupPassphraseDialog() {
        _uiState.update { it.copy(showBackupPassphraseDialog = true) }
    }

    fun closeBackupPassphraseDialog() {
        _uiState.update { it.copy(showBackupPassphraseDialog = false) }
    }

    fun openRestorePassphraseDialog(backup: DriveRemoteBackup) {
        _uiState.update {
            it.copy(
                restoreSelectedBackup = backup,
                showRestorePassphraseDialog = true
            )
        }
    }

    fun closeRestorePassphraseDialog() {
        _uiState.update {
            it.copy(
                restoreSelectedBackup = null,
                showRestorePassphraseDialog = false
            )
        }
    }

    fun closeRestorePreviewDialog() {
        _uiState.update {
            it.copy(
                showRestorePreviewDialog = false,
                pendingRestoreBackup = null,
                pendingRestoreParsed = null
            )
        }
    }

    fun clearDriveOperationState() {
        _uiState.update {
            it.copy(
                isDriveOperationInProgress = false,
                driveOperationStatusMessage = null,
                driveOperationError = null
            )
        }
    }

    fun clearDriveOperationError() {
        _uiState.update { it.copy(driveOperationError = null) }
    }

    fun refreshDriveBackups() {
        viewModelScope.launch {
            val settings = backupRepository.userSettings.first()
            if (settings.driveUserEmail.isBlank()) return@launch

            _uiState.update {
                it.copy(
                    isDriveOperationInProgress = true,
                    driveOperationStatusMessage = "Checking Google Drive backups...",
                    driveOperationError = null
                )
            }

            when (val res = backupRepository.driveServiceHelper.listBackups()) {
                is DriveResult.Success -> {
                    if (!settings.driveUserEmail.contains("@")) {
                        val fetchedEmail = backupRepository.driveServiceHelper.fetchConnectedAccountEmail()
                        if (!fetchedEmail.isNullOrBlank()) {
                            backupRepository.updateDriveAccount(fetchedEmail)
                        }
                    }
                    _uiState.update {
                        it.copy(
                            driveRemoteBackups = res.data,
                            isDriveOperationInProgress = false,
                            driveOperationStatusMessage = null,
                            driveResolutionPendingIntent = null
                        )
                    }
                }
                is DriveResult.Error -> {
                    if (res.needsReconnect || res.errorType == DriveErrorType.EXPIRED_SIGN_IN) {
                        backupRepository.setDriveNeedsReconnect(true, res.message)
                    }
                    _uiState.update {
                        it.copy(
                            isDriveOperationInProgress = false,
                            driveOperationStatusMessage = null,
                            driveOperationError = res.message,
                            driveResolutionPendingIntent = res.resolutionPendingIntent ?: it.driveResolutionPendingIntent
                        )
                    }
                }
            }
        }
    }

    fun runDriveConnectionTest() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDriveOperationInProgress = true,
                    driveOperationStatusMessage = "Running Google Drive connection test (Step 1: Auth, Step 2: Drive API)...",
                    driveOperationError = null
                )
            }

            val report = backupRepository.driveServiceHelper.runConnectionTest()
            if (report.passed && !report.connectedEmail.isNullOrBlank()) {
                backupRepository.updateDriveAccount(report.connectedEmail)
            } else if (report.needsReconnect) {
                backupRepository.setDriveNeedsReconnect(true, report.errorMessage)
            }
            _uiState.update {
                it.copy(
                    isDriveOperationInProgress = false,
                    driveOperationStatusMessage = null,
                    driveConnectionTestReport = report,
                    driveResolutionPendingIntent = report.resolutionPendingIntent ?: it.driveResolutionPendingIntent
                )
            }
            if (report.passed) {
                refreshDriveBackups()
            }
        }
    }

    fun clearDriveConnectionTestReport() {
        _uiState.update { it.copy(driveConnectionTestReport = null) }
    }

    fun getAppSigningInfo(): AppSigningInfo =
        appContext?.let { AppSigningCertificateHelper.getSigningInfo(it) }
            ?: AppSigningInfo(
                packageName = BuildConfig.APPLICATION_ID,
                sha1 = "Unavailable",
                sha256 = "Unavailable"
            )

    fun deleteAllDriveBackups(onComplete: ((Result<Int>) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDriveOperationInProgress = true,
                    driveOperationStatusMessage = "Deleting all backups from Google Drive AppData...",
                    driveOperationError = null
                )
            }

            when (val result = backupRepository.driveServiceHelper.deleteAllBackups()) {
                is DriveResult.Success -> {
                    backupRepository.clearDriveBackupMetadata()
                    _uiState.update {
                        it.copy(
                            isDriveOperationInProgress = false,
                            driveOperationStatusMessage = "Deleted ${result.data} backup file(s) from Google Drive.",
                            driveRemoteBackups = emptyList(),
                            driveOperationError = null
                        )
                    }
                    onComplete?.invoke(Result.success(result.data))
                }
                is DriveResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isDriveOperationInProgress = false,
                            driveOperationStatusMessage = null,
                            driveOperationError = result.message
                        )
                    }
                    onComplete?.invoke(Result.failure(IllegalStateException(result.message)))
                }
            }
        }
    }

    private suspend fun prepareBackupJson(settings: UserSettings): Pair<String, BackupPreviewSummary> {
        val snapshot = backupRepository.getFullLedgerBackupSnapshot()
        val attachments = backupRepository.attachmentRepository.getLocalAttachmentsMap()
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
            parties = snapshot.parties,
            transactions = snapshot.transactions,
            partialPayments = snapshot.partialPayments,
            chatThreads = snapshot.chatThreads,
            chatMessages = snapshot.chatMessages,
            settings = settingsMap,
            attachments = attachments,
            attachmentRecords = snapshot.attachments
        )
        val preview = BackupPreviewSummary(
            partiesCount = snapshot.parties.size,
            transactionsCount = snapshot.transactions.size,
            paymentsCount = snapshot.partialPayments.size,
            attachmentsCount = attachments.size,
            appVersion = BuildConfig.VERSION_NAME,
            createdAtMillis = System.currentTimeMillis()
        )
        return Pair(plainJson, preview)
    }

    fun uploadDriveBackup(
        passphrase: String,
        savePassphraseForAutoBackup: Boolean = false,
        onComplete: ((Result<DriveRemoteBackup>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val settings = backupRepository.userSettings.first()
            if (settings.driveUserEmail.isBlank()) {
                val err = DriveBackupWorker.plainErrorDescription(DriveBackupErrorCode.NOT_CONNECTED)
                backupRepository.recordDriveBackupFailure(DriveBackupErrorCode.NOT_CONNECTED, err)
                _uiState.update { it.copy(driveOperationError = err) }
                onComplete?.invoke(Result.failure(IllegalStateException(err)))
                return@launch
            }

            _uiState.update {
                it.copy(
                    isDriveOperationInProgress = true,
                    driveOperationStatusMessage = "Encrypting ledger on device with AES-256...",
                    driveOperationError = null,
                    showBackupPassphraseDialog = false
                )
            }

            try {
                val (plainJson, preview) = prepareBackupJson(settings)
                val encryptedBytes = DriveBackupCrypto.encrypt(
                    plainBytes = plainJson.toByteArray(Charsets.UTF_8),
                    passphrase = passphrase,
                    preview = preview
                )

                _uiState.update { it.copy(driveOperationStatusMessage = "Authorizing & uploading encrypted backup to Google Drive AppData...") }

                val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "udhaar_backup_${dateStr}.udhaar"

                when (val uploadRes = backupRepository.driveServiceHelper.uploadBackup(fileName = fileName, encryptedBytes = encryptedBytes, preview = preview)) {
                    is DriveResult.Success -> {
                        backupRepository.recordDriveBackupSuccess(System.currentTimeMillis(), uploadRes.data.sizeBytes)
                        if (savePassphraseForAutoBackup) {
                            backupRepository.saveAutoBackupPassphrase(passphrase)
                        }
                        _uiState.update {
                            it.copy(
                                isDriveOperationInProgress = false,
                                driveOperationStatusMessage = "Backup completed successfully! (${uploadRes.data.sizeBytes / 1024} KB)",
                                driveResolutionPendingIntent = null
                            )
                        }
                        refreshDriveBackups()
                        onComplete?.invoke(Result.success(uploadRes.data))
                    }
                    is DriveResult.Error -> {
                        val mappedCode = when (uploadRes.errorType) {
                            DriveErrorType.NO_NETWORK -> DriveBackupErrorCode.NO_NETWORK
                            DriveErrorType.EXPIRED_SIGN_IN -> DriveBackupErrorCode.AUTH_EXPIRED
                            DriveErrorType.QUOTA_EXCEEDED -> DriveBackupErrorCode.QUOTA
                            DriveErrorType.NOT_FOUND, DriveErrorType.UNKNOWN -> DriveBackupErrorCode.OTHER
                        }
                        backupRepository.recordDriveBackupFailure(mappedCode, uploadRes.message)
                        _uiState.update {
                            it.copy(
                                isDriveOperationInProgress = false,
                                driveOperationStatusMessage = null,
                                driveOperationError = uploadRes.message,
                                driveResolutionPendingIntent = uploadRes.resolutionPendingIntent ?: it.driveResolutionPendingIntent
                            )
                        }
                        onComplete?.invoke(Result.failure(IOException(uploadRes.message)))
                    }
                }
            } catch (e: Exception) {
                val appError = UdhaarAppError.fromThrowable(e)
                SafeLogger.w("BackupViewModel", "Drive backup upload failed (${appError.code})", e)
                backupRepository.recordDriveBackupFailure(DriveBackupErrorCode.OTHER, appError.userMessage)
                _uiState.update {
                    it.copy(
                        isDriveOperationInProgress = false,
                        driveOperationStatusMessage = null,
                        driveOperationError = appError.userMessage
                    )
                }
                onComplete?.invoke(Result.failure(e))
            }
        }
    }

    fun downloadAndDecryptDriveBackup(
        backup: DriveRemoteBackup,
        passphrase: String,
        onPreviewReady: ((Result<ParsedLedgerBackup>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val settings = backupRepository.userSettings.first()
            if (settings.driveUserEmail.isBlank()) {
                val err = DriveBackupWorker.plainErrorDescription(DriveBackupErrorCode.NOT_CONNECTED)
                _uiState.update { it.copy(driveOperationError = err) }
                onPreviewReady?.invoke(Result.failure(IllegalStateException(err)))
                return@launch
            }

            _uiState.update {
                it.copy(
                    isDriveOperationInProgress = true,
                    driveOperationStatusMessage = "Downloading encrypted backup from Google Drive...",
                    driveOperationError = null,
                    showRestorePassphraseDialog = false
                )
            }

            when (val dlRes = backupRepository.driveServiceHelper.downloadBackup(fileId = backup.fileId)) {
                is DriveResult.Success -> {
                    _uiState.update { it.copy(driveOperationStatusMessage = "Decrypting with AES-256 on device...") }
                    try {
                        val decryptedBytes = DriveBackupCrypto.decrypt(dlRes.data, passphrase)
                        val plainJson = String(decryptedBytes, Charsets.UTF_8)
                        val parsed = LedgerBackupManager.parseBackupJson(plainJson)

                        _uiState.update {
                            it.copy(
                                isDriveOperationInProgress = false,
                                driveOperationStatusMessage = null,
                                pendingRestoreBackup = backup,
                                pendingRestoreParsed = parsed,
                                showRestorePreviewDialog = true
                            )
                        }
                        onPreviewReady?.invoke(Result.success(parsed))
                    } catch (e: Exception) {
                        val appError = UdhaarAppError.fromThrowable(e)
                        SafeLogger.w("BackupViewModel", "Drive backup decrypt failed (${appError.code})", e)
                        _uiState.update {
                            it.copy(
                                isDriveOperationInProgress = false,
                                driveOperationStatusMessage = null,
                                driveOperationError = appError.userMessage
                            )
                        }
                        onPreviewReady?.invoke(Result.failure(e))
                    }
                }
                is DriveResult.Error -> {
                    if (dlRes.needsReconnect || dlRes.errorType == DriveErrorType.EXPIRED_SIGN_IN) {
                        backupRepository.setDriveNeedsReconnect(true, dlRes.message)
                    }
                    _uiState.update {
                        it.copy(
                            isDriveOperationInProgress = false,
                            driveOperationStatusMessage = null,
                            driveOperationError = dlRes.message,
                            driveResolutionPendingIntent = dlRes.resolutionPendingIntent ?: it.driveResolutionPendingIntent
                        )
                    }
                    onPreviewReady?.invoke(Result.failure(IOException(dlRes.message)))
                }
            }
        }
    }

    fun executeDriveRestore(
        replaceExisting: Boolean,
        onComplete: ((Result<Int>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val parsed = _uiState.value.pendingRestoreParsed
            if (parsed == null) {
                val err = "No pending backup to restore."
                _uiState.update { it.copy(driveOperationError = err) }
                onComplete?.invoke(Result.failure(IllegalStateException(err)))
                return@launch
            }

            _uiState.update {
                it.copy(
                    isDriveOperationInProgress = true,
                    driveOperationStatusMessage = if (replaceExisting) "Replacing ledger with backup..." else "Merging backup into ledger...",
                    showRestorePreviewDialog = false
                )
            }

            try {
                val restoredCount = backupRepository.restoreFullLedgerBackup(parsed, replaceExisting)
                _uiState.update {
                    it.copy(
                        isDriveOperationInProgress = false,
                        driveOperationStatusMessage = "Restored successfully: $restoredCount transactions, ${parsed.parties.size} parties.",
                        pendingRestoreBackup = null,
                        pendingRestoreParsed = null
                    )
                }
                onComplete?.invoke(Result.success(restoredCount))
            } catch (e: Exception) {
                val appError = UdhaarAppError.fromThrowable(e)
                SafeLogger.w("BackupViewModel", "Drive backup restore failed (${appError.code})", e)
                _uiState.update {
                    it.copy(
                        isDriveOperationInProgress = false,
                        driveOperationStatusMessage = null,
                        driveOperationError = appError.userMessage
                    )
                }
                onComplete?.invoke(Result.failure(e))
            }
        }
    }

    fun deleteDriveBackup(backup: DriveRemoteBackup) {
        viewModelScope.launch {
            val settings = backupRepository.userSettings.first()
            if (settings.driveUserEmail.isBlank()) return@launch

            val delRes = backupRepository.driveServiceHelper.deleteBackup(fileId = backup.fileId)
            if (delRes is DriveResult.Error && (delRes.needsReconnect || delRes.errorType == DriveErrorType.EXPIRED_SIGN_IN)) {
                backupRepository.setDriveNeedsReconnect(true, delRes.message)
                _uiState.update {
                    it.copy(
                        driveOperationError = delRes.message,
                        driveResolutionPendingIntent = delRes.resolutionPendingIntent ?: it.driveResolutionPendingIntent
                    )
                }
            } else {
                refreshDriveBackups()
            }
        }
    }

    fun updateDriveAutoBackupSettings(
        frequency: AutoBackupFrequency,
        wifiOnly: Boolean,
        passphraseToSave: String? = null
    ) {
        viewModelScope.launch {
            backupRepository.updateDriveAutoBackupSettings(frequency, wifiOnly, passphraseToSave)
        }
    }

    fun generateFullLedgerBackupJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val settings = backupRepository.userSettings.first()
            val (json, _) = prepareBackupJson(settings)
            onReady(json)
        }
    }

    fun generateLocalEncryptedUdhaarBackup(
        passphrase: String,
        onReady: (Result<ByteArray>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val settings = backupRepository.userSettings.first()
                val (plainJson, preview) = prepareBackupJson(settings)
                val encryptedBytes = DriveBackupCrypto.encrypt(
                    plainBytes = plainJson.toByteArray(Charsets.UTF_8),
                    passphrase = passphrase,
                    preview = preview
                )
                onReady(Result.success(encryptedBytes))
            } catch (e: Exception) {
                SafeLogger.w("BackupViewModel", "Failed to generate local encrypted backup", e)
                onReady(Result.failure(e))
            }
        }
    }

    fun generateSinglePartyStatementJson(khata: PartyKhataSummary, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val (_, _, allPayments) = backupRepository.getFullLedgerSnapshot()
            val txs = khata.transactions.map { it.transaction }
            val txIds = txs.map { it.id }.toSet()
            val partyPayments = allPayments.filter { it.transactionId in txIds }
            val json = LedgerBackupManager.buildBackupJson(
                parties = listOf(khata.party),
                transactions = txs,
                partialPayments = partyPayments,
                exportType = "SINGLE_PARTY_STATEMENT",
                statementPartyName = khata.party.name
            )
            onReady(json)
        }
    }

    fun importLedgerBackupJson(
        rawContent: String,
        onComplete: (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val parsed = LedgerBackupManager.parseBackupJson(rawContent)
                val count = backupRepository.importLedgerData(
                    parties = parsed.parties,
                    transactions = parsed.transactions,
                    partialPayments = parsed.partialPayments,
                    attachments = parsed.attachmentRecords
                )
                onComplete(Result.success(count))
            } catch (e: Exception) {
                SafeLogger.w("BackupViewModel", "Failed to parse/import backup JSON", e)
                onComplete(Result.failure(e))
            }
        }
    }
}
