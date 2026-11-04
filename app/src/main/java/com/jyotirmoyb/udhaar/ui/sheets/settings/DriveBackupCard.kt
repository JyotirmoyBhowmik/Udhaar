package com.jyotirmoyb.udhaar.ui.sheets.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode
import com.jyotirmoyb.udhaar.backup.DriveBackupWorker
import com.jyotirmoyb.udhaar.backup.DriveRemoteBackup
import com.jyotirmoyb.udhaar.data.AutoBackupFrequency
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.ui.components.UdhaarAnimatedSwitch
import com.jyotirmoyb.udhaar.util.InterestCalculator

@Composable
fun DriveBackupCard(
    currentSettings: UserSettings,
    driveRemoteBackups: List<DriveRemoteBackup>,
    isDriveOperationInProgress: Boolean,
    driveOperationStatusMessage: String?,
    driveOperationError: String?,
    hasPendingDriveResolution: Boolean,
    onSignInWithGoogleForDrive: () -> Unit,
    onReconnectGoogleDrive: () -> Unit,
    onSignOutFromDrive: () -> Unit,
    onBackupNowClicked: () -> Unit,
    onRestoreBackupClicked: (DriveRemoteBackup) -> Unit,
    onDeleteDriveBackup: (DriveRemoteBackup) -> Unit,
    onUpdateAutoBackupSettings: (AutoBackupFrequency, Boolean, String?) -> Unit,
    onRefreshDriveBackups: () -> Unit,
    onClearDriveOperationError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Operation In Progress Indicator
        if (isDriveOperationInProgress) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .testTag("drive_operation_progress_card"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = driveOperationStatusMessage ?: stringResource(R.string.ui_str_google_drive_operation_in_progress_97811d),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Reconnect Google Drive Prompt Banner
        val showReconnectPrompt = hasPendingDriveResolution ||
            currentSettings.driveNeedsReconnect ||
            currentSettings.driveLastErrorCode == DriveBackupErrorCode.AUTH_EXPIRED
        if (showReconnectPrompt) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .testTag("drive_reconnect_prompt_card"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.drive_reconnect_google_drive_prompt_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = stringResource(R.string.drive_reconnect_google_drive_prompt_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = onReconnectGoogleDrive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("drive_reconnect_prompt_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.drive_reconnect_google_drive_prompt_title),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // Persisted Last Error in Plain Words
        val persistedErrorCode = currentSettings.driveLastErrorCode
        val plainPersistedError = remember(persistedErrorCode, currentSettings.driveLastErrorMessage) {
            if (persistedErrorCode != DriveBackupErrorCode.NONE) {
                currentSettings.driveLastErrorMessage.ifBlank {
                    DriveBackupWorker.plainErrorDescription(persistedErrorCode)
                }
            } else {
                ""
            }
        }
        val effectiveErrorText = driveOperationError ?: plainPersistedError.takeIf { it.isNotBlank() }

        if (effectiveErrorText != null) {
            val errorCodeTag = if (persistedErrorCode != DriveBackupErrorCode.NONE) {
                persistedErrorCode.name
            } else {
                "DRIVE_ERROR"
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .testTag("drive_error_banner"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.ui_str_google_drive_notice_fb05e0),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = stringResource(R.string.drive_last_error_label_fmt, errorCodeTag, effectiveErrorText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("drive_last_error_plain_text")
                )
                if (driveOperationError != null) {
                    OutlinedButton(
                        onClick = onClearDriveOperationError,
                        modifier = Modifier
                            .align(Alignment.End)
                            .height(34.dp)
                            .testTag("dismiss_drive_error_button"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text(
                            stringResource(R.string.ui_str_dismiss_d2fd9a),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Google Drive AppData Section
        Text(
            text = stringResource(R.string.ui_str_google_drive_app_data_cloud_f609d0),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.ui_str_backups_are_client_side_encrypted_450b36),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (currentSettings.driveAccountEmail.isBlank()) {
            Button(
                onClick = onSignInWithGoogleForDrive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("google_drive_sign_in_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.ui_str_sign_in_with_google_drive_f8dca0), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .testTag("drive_account_connected_card"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ui_str_connected_google_account_4aaee2),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentSettings.driveAccountEmail,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("drive_connected_email_text")
                        )
                        val lastBackupStr = if (currentSettings.driveLastBackupMillis > 0) {
                            val dateStr = InterestCalculator.fmtDate(currentSettings.driveLastBackupMillis)
                            val sizeKb = currentSettings.driveLastBackupSizeBytes / 1024
                            "$dateStr (${sizeKb} KB)"
                        } else {
                            "No backup yet"
                        }
                        Text(
                            text = stringResource(R.string.ui_fmt_last_backup_a7f073, lastBackupStr),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("drive_last_backup_text")
                        )
                    }

                    IconButton(
                        onClick = onRefreshDriveBackups,
                        modifier = Modifier.testTag("refresh_drive_backups_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Backups", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onBackupNowClicked,
                        enabled = !isDriveOperationInProgress,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp)
                            .testTag("drive_backup_now_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isDriveOperationInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.drive_backing_up_progress), style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.ui_str_back_up_now_e11395), style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    OutlinedButton(
                        onClick = onReconnectGoogleDrive,
                        enabled = !isDriveOperationInProgress,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("drive_reconnect_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.drive_reconnect_button), style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedButton(
                    onClick = onSignOutFromDrive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("drive_sign_out_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(stringResource(R.string.ui_str_sign_out_c790dd), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (currentSettings.driveAccountEmail.isNotBlank()) {
            // Automatic Backup with WorkManager
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .testTag("drive_auto_backup_config_card"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.ui_str_automatic_backup_workmanager_961df8),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.ui_str_udhaar_schedules_periodic_backgroun_accdee),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AutoBackupFrequency.entries.forEach { freq ->
                        val isSelected = currentSettings.driveAutoBackupFrequency == freq
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onUpdateAutoBackupSettings(
                                        freq,
                                        currentSettings.driveAutoBackupWifiOnly,
                                        null
                                    )
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = freq.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ui_str_wi_fi_only_6ce953),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ui_str_only_upload_auto_backups_over_1776af),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    UdhaarAnimatedSwitch(
                        checked = currentSettings.driveAutoBackupWifiOnly,
                        onCheckedChange = { wifiOnly ->
                            onUpdateAutoBackupSettings(
                                currentSettings.driveAutoBackupFrequency,
                                wifiOnly,
                                null
                            )
                        },
                        modifier = Modifier.testTag("drive_auto_backup_wifi_switch")
                    )
                }

                if (currentSettings.driveAutoBackupFrequency != AutoBackupFrequency.DISABLED && !currentSettings.driveAutoBackupPassphraseSaved) {
                    Text(
                        text = stringResource(R.string.ui_str_auto_backup_is_scheduled_but_40f61e),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // List of Earlier Backups in Drive AppData
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .testTag("drive_earlier_backups_card"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.ui_fmt_earlier_cloud_backups_02efea, driveRemoteBackups.size),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (driveRemoteBackups.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ui_str_no_backups_found_in_google_dbb9f5),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    driveRemoteBackups.forEach { backup ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = backup.fileName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sizeKb = backup.sizeBytes / 1024
                                Text(
                                    text = stringResource(R.string.ui_fmt_kb_bb7db8, sizeKb),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val dateStr = if (backup.createdTimeMillis > 0) {
                                InterestCalculator.fmtDate(backup.createdTimeMillis)
                            } else {
                                "Unknown date"
                            }
                            Text(
                                text = stringResource(R.string.ui_fmt_created_aes_256_gcm_encrypted_b1c8fd, dateStr),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onRestoreBackupClicked(backup) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("restore_backup_${backup.fileId}_button"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.ui_str_restore_6a8135), style = MaterialTheme.typography.labelSmall)
                                }

                                OutlinedButton(
                                    onClick = { onDeleteDriveBackup(backup) },
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .height(38.dp)
                                        .testTag("delete_backup_${backup.fileId}_button"),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        stringResource(R.string.ui_str_delete_d6f563),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
