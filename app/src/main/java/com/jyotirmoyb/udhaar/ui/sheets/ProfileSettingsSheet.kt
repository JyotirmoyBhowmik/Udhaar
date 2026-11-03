package com.jyotirmoyb.udhaar.ui.sheets

import androidx.compose.runtime.Composable
import com.jyotirmoyb.udhaar.backup.DriveRemoteBackup
import com.jyotirmoyb.udhaar.data.AutoBackupFrequency
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.security.EncryptedStorageManager
import com.jyotirmoyb.udhaar.ui.components.ProfileSettingsModal
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode

/**
 * Modular sheet entrypoint for the 5-section Profile & Settings screen
 * (Profile & UPI, Security, Currency & Theme, Backup & Sync, and About / Privacy Policy / Check Update).
 */
@Composable
fun ProfileSettingsSheet(
    currentSettings: UserSettings,
    onSave: (
        upiId: String,
        payeeName: String,
        defaultCurrency: CurrencyType,
        inrEnabled: Boolean,
        nprEnabled: Boolean,
        themeMode: AppThemeMode,
        fontScale: AppFontScale,
        biometricLockEnabled: Boolean,
        securityPin: String,
        closeModal: Boolean
    ) -> Unit,
    onSaveExtendedProfile: (phoneNumber: String, profilePhotoUri: String, cloudVerificationConsent: Boolean) -> Unit = { _, _, _ -> },
    onShareMyPaymentDetails: () -> Unit = {},
    onOpenReceivedVerificationLink: (String) -> Unit = {},
    onSignInWithGoogleForVerification: () -> Unit = {},
    onDeleteMyCloudData: () -> Unit = {},
    onLockAppNow: () -> Unit = {},
    onOpenGlobalQrModal: () -> Unit = {},
    onOpenShareAppModal: () -> Unit = {},
    onExportFullLedgerSaf: () -> Unit,
    onShareFullLedgerFile: () -> Unit,
    onImportLedgerSaf: () -> Unit,
    driveRemoteBackups: List<DriveRemoteBackup> = emptyList(),
    isDriveOperationInProgress: Boolean = false,
    driveOperationStatusMessage: String? = null,
    driveOperationError: String? = null,
    hasPendingDriveResolution: Boolean = false,
    driveConnectionTestReport: com.jyotirmoyb.udhaar.backup.DriveConnectionTestReport? = null,
    onSignInWithGoogleForDrive: () -> Unit = {},
    onReconnectGoogleDrive: () -> Unit = onSignInWithGoogleForDrive,
    onRunDriveConnectionTest: () -> Unit = {},
    onClearDriveConnectionTestReport: () -> Unit = {},
    onSignOutFromDrive: () -> Unit = {},
    onBackupNowToDrive: (passphrase: String, savePassphraseForAuto: Boolean) -> Unit = { _, _ -> },
    onRestoreDriveBackup: (DriveRemoteBackup, passphrase: String) -> Unit = { _, _ -> },
    onDeleteDriveBackup: (DriveRemoteBackup) -> Unit = {},
    onUpdateAutoBackupSettings: (AutoBackupFrequency, Boolean, String?) -> Unit = { _, _, _ -> },
    onRefreshDriveBackups: () -> Unit = {},
    onClearDriveOperationError: () -> Unit = {},
    dbEncryptionState: EncryptedStorageManager.DbEncryptionState =
        EncryptedStorageManager.DbEncryptionState.ENCRYPTED,
    onRetryDatabaseEncryption: () -> Unit = {},
    onExportLocalUdhaarSaf: (passphrase: String) -> Unit = {},
    onRetryCloudBackup: () -> Unit = {},
    onOpenContactPicker: () -> Unit = {},
    onDismiss: () -> Unit
) {
    ProfileSettingsModal(
        currentSettings = currentSettings,
        onSave = onSave,
        onSaveExtendedProfile = onSaveExtendedProfile,
        onShareMyPaymentDetails = onShareMyPaymentDetails,
        onOpenReceivedVerificationLink = onOpenReceivedVerificationLink,
        onSignInWithGoogleForVerification = onSignInWithGoogleForVerification,
        onDeleteMyCloudData = onDeleteMyCloudData,
        onLockAppNow = onLockAppNow,
        onOpenGlobalQrModal = onOpenGlobalQrModal,
        onOpenShareAppModal = onOpenShareAppModal,
        onExportFullLedgerSaf = onExportFullLedgerSaf,
        onShareFullLedgerFile = onShareFullLedgerFile,
        onImportLedgerSaf = onImportLedgerSaf,
        driveRemoteBackups = driveRemoteBackups,
        isDriveOperationInProgress = isDriveOperationInProgress,
        driveOperationStatusMessage = driveOperationStatusMessage,
        driveOperationError = driveOperationError,
        hasPendingDriveResolution = hasPendingDriveResolution,
        driveConnectionTestReport = driveConnectionTestReport,
        onSignInWithGoogleForDrive = onSignInWithGoogleForDrive,
        onReconnectGoogleDrive = onReconnectGoogleDrive,
        onRunDriveConnectionTest = onRunDriveConnectionTest,
        onClearDriveConnectionTestReport = onClearDriveConnectionTestReport,
        onSignOutFromDrive = onSignOutFromDrive,
        onBackupNowToDrive = onBackupNowToDrive,
        onRestoreDriveBackup = onRestoreDriveBackup,
        onDeleteDriveBackup = onDeleteDriveBackup,
        onUpdateAutoBackupSettings = onUpdateAutoBackupSettings,
        onRefreshDriveBackups = onRefreshDriveBackups,
        onClearDriveOperationError = onClearDriveOperationError,
        dbEncryptionState = dbEncryptionState,
        onRetryDatabaseEncryption = onRetryDatabaseEncryption,
        onExportLocalUdhaarSaf = onExportLocalUdhaarSaf,
        onRetryCloudBackup = onRetryCloudBackup,
        onOpenContactPicker = onOpenContactPicker,
        onDismiss = onDismiss
    )
}
