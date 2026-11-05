package com.jyotirmoyb.udhaar.ui.sheets.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.backup.DriveConnectionTestReport
import com.jyotirmoyb.udhaar.backup.DriveRemoteBackup
import com.jyotirmoyb.udhaar.data.AutoBackupFrequency
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.ui.common.SettingsSectionCard
import com.jyotirmoyb.udhaar.util.AppSigningInfo

import androidx.compose.ui.res.stringResource
import com.jyotirmoyb.udhaar.R

@Composable
fun BackupSection(
    currentSettings: UserSettings,
    driveRemoteBackups: List<DriveRemoteBackup>,
    isDriveOperationInProgress: Boolean,
    driveOperationStatusMessage: String?,
    driveOperationError: String?,
    hasPendingDriveResolution: Boolean,
    driveConnectionTestReport: DriveConnectionTestReport?,
    appSigningInfo: AppSigningInfo?,
    cloudVerificationConsent: Boolean,
    onCloudVerificationConsentChange: (Boolean) -> Unit,
    onSignInWithGoogleForDrive: () -> Unit,
    onReconnectGoogleDrive: () -> Unit,
    onRunDriveConnectionTest: () -> Unit,
    onClearDriveConnectionTestReport: () -> Unit,
    onSignOutFromDrive: () -> Unit,
    onBackupNowClicked: () -> Unit,
    onRestoreBackupClicked: (DriveRemoteBackup) -> Unit,
    onDeleteDriveBackup: (DriveRemoteBackup) -> Unit,
    onDeleteAllDriveBackups: () -> Unit,
    onUpdateAutoBackupSettings: (AutoBackupFrequency, Boolean, String?) -> Unit,
    onRefreshDriveBackups: () -> Unit,
    onClearDriveOperationError: () -> Unit,
    onExportFullLedgerSaf: () -> Unit,
    onShareFullLedgerFile: () -> Unit,
    onExportLocalUdhaarSafClicked: () -> Unit,
    onImportLedgerSaf: () -> Unit,
    onOpenContactPicker: () -> Unit,
    onSignInWithGoogleForVerification: () -> Unit,
    onRetryCloudBackup: () -> Unit,
    onOpenReceivedVerificationLink: (String) -> Unit,
    onDeleteMyCloudData: () -> Unit,
    onGenerateRecoveryKey: ((onKeyReady: (String) -> Unit) -> Unit)? = null,
    onTestMyBackup: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    SettingsSectionCard(
        sectionNumber = "5",
        title = stringResource(R.string.settings_sec_backup_title),
        subtitle = stringResource(R.string.settings_sec_backup_sub),
        testTag = "section_backup_and_sync"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Google Drive AppData section & earlier backups
            DriveBackupCard(
                currentSettings = currentSettings,
                driveRemoteBackups = driveRemoteBackups,
                isDriveOperationInProgress = isDriveOperationInProgress,
                driveOperationStatusMessage = driveOperationStatusMessage,
                driveOperationError = driveOperationError,
                hasPendingDriveResolution = hasPendingDriveResolution,
                onSignInWithGoogleForDrive = onSignInWithGoogleForDrive,
                onReconnectGoogleDrive = onReconnectGoogleDrive,
                onSignOutFromDrive = onSignOutFromDrive,
                onBackupNowClicked = onBackupNowClicked,
                onRestoreBackupClicked = onRestoreBackupClicked,
                onDeleteDriveBackup = onDeleteDriveBackup,
                onUpdateAutoBackupSettings = onUpdateAutoBackupSettings,
                onRefreshDriveBackups = onRefreshDriveBackups,
                onClearDriveOperationError = onClearDriveOperationError
            )

            // Diagnostics and signing info
            DriveDiagnosticsCard(
                appSigningInfo = appSigningInfo,
                driveConnectionTestReport = driveConnectionTestReport,
                isDriveOperationInProgress = isDriveOperationInProgress,
                hasConnectedAccountOrBackups = currentSettings.driveAccountEmail.isNotBlank() || driveRemoteBackups.isNotEmpty(),
                onRunDriveConnectionTest = onRunDriveConnectionTest,
                onClearDriveConnectionTestReport = onClearDriveConnectionTestReport,
                onDeleteAllDriveBackups = onDeleteAllDriveBackups
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

            // Local Offline JSON Backup & Restore + Recovery Key + Test My Backup
            LocalBackupSection(
                onExportFullLedgerSaf = onExportFullLedgerSaf,
                onShareFullLedgerFile = onShareFullLedgerFile,
                onExportLocalUdhaarSafClicked = onExportLocalUdhaarSafClicked,
                onImportLedgerSaf = onImportLedgerSaf,
                onOpenContactPicker = onOpenContactPicker,
                backupRecoveryKeyId = currentSettings.backupRecoveryKeyId,
                onGenerateRecoveryKey = onGenerateRecoveryKey,
                onTestMyBackup = onTestMyBackup
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

            // Phase 3: Two-Party Cloud Verification, Privacy Consent & Delete My Cloud Data
            CloudVerificationCard(
                currentSettings = currentSettings,
                cloudVerificationConsent = cloudVerificationConsent,
                onCloudVerificationConsentChange = onCloudVerificationConsentChange,
                onSignInWithGoogleForVerification = onSignInWithGoogleForVerification,
                onRetryCloudBackup = onRetryCloudBackup,
                onOpenReceivedVerificationLink = onOpenReceivedVerificationLink,
                onDeleteMyCloudData = onDeleteMyCloudData
            )
        }
    }
}
