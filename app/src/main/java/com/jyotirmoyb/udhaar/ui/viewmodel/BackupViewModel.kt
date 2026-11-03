package com.jyotirmoyb.udhaar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.udhaar.backup.BackupPreviewSummary
import com.jyotirmoyb.udhaar.backup.DriveRemoteBackup
import com.jyotirmoyb.udhaar.data.repository.BackupRepository
import com.jyotirmoyb.udhaar.util.ParsedLedgerBackup
import com.jyotirmoyb.udhaar.util.UdhaarAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupDomainUiState(
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val remoteBackups: List<DriveRemoteBackup> = emptyList(),
    val pendingRestoreBackup: DriveRemoteBackup? = null,
    val pendingRestoreParsed: ParsedLedgerBackup? = null,
    val lastError: UdhaarAppError? = null
)

/**
 * Domain ViewModel managing offline JSON export/import and encrypted Google Drive backups (`UdhaarResult`).
 */
class BackupViewModel(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BackupDomainUiState())
    val state: StateFlow<BackupDomainUiState> = _state.asStateFlow()

    fun refreshDriveBackups(accessToken: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, lastError = null) }
            backupRepository.listDriveBackups(accessToken)
                .onSuccess { backups ->
                    _state.update { it.copy(isBusy = false, remoteBackups = backups) }
                }
                .onFailure { err ->
                    _state.update { it.copy(isBusy = false, lastError = err) }
                }
        }
    }

    fun uploadEncryptedDriveBackup(
        accessToken: String,
        plainJson: String,
        passphrase: String,
        preview: BackupPreviewSummary,
        onComplete: (DriveRemoteBackup) -> Unit = {}
    ) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isBusy = true,
                    statusMessage = "Encrypting with AES-256-GCM and uploading to Google Drive...",
                    lastError = null
                )
            }
            backupRepository.encryptAndUploadToDrive(accessToken, plainJson, passphrase, preview)
                .onSuccess { uploaded ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Encrypted backup uploaded (${uploaded.fileName}).",
                            remoteBackups = listOf(uploaded) + it.remoteBackups
                        )
                    }
                    onComplete(uploaded)
                }
                .onFailure { err ->
                    _state.update { it.copy(isBusy = false, statusMessage = null, lastError = err) }
                }
        }
    }

    fun downloadAndPreviewDriveBackup(
        accessToken: String,
        backup: DriveRemoteBackup,
        passphrase: String
    ) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isBusy = true,
                    statusMessage = "Downloading & decrypting backup...",
                    lastError = null
                )
            }
            backupRepository.downloadAndDecryptDriveBackup(accessToken, backup.fileId, passphrase)
                .onSuccess { parsed ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = null,
                            pendingRestoreBackup = backup,
                            pendingRestoreParsed = parsed
                        )
                    }
                }
                .onFailure { err ->
                    _state.update { it.copy(isBusy = false, statusMessage = null, lastError = err) }
                }
        }
    }

    fun executePendingRestore(replaceExisting: Boolean, onRestored: (Int) -> Unit = {}) {
        val parsed = _state.value.pendingRestoreParsed ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, lastError = null) }
            backupRepository.restoreParsedBackup(parsed, replaceExisting)
                .onSuccess { count ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Restored $count records.",
                            pendingRestoreBackup = null,
                            pendingRestoreParsed = null
                        )
                    }
                    onRestored(count)
                }
                .onFailure { err ->
                    _state.update { it.copy(isBusy = false, statusMessage = null, lastError = err) }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(lastError = null) }
    }

    class Factory(
        private val backupRepository: BackupRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BackupViewModel(backupRepository) as T
        }
    }
}
