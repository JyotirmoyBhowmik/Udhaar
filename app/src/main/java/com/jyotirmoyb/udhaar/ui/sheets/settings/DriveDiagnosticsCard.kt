package com.jyotirmoyb.udhaar.ui.sheets.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.backup.DriveConnectionTestReport
import com.jyotirmoyb.udhaar.ui.icons.UdhaarIcons
import com.jyotirmoyb.udhaar.util.AppSigningInfo

@Composable
fun DriveDiagnosticsCard(
    appSigningInfo: AppSigningInfo?,
    driveConnectionTestReport: DriveConnectionTestReport?,
    isDriveOperationInProgress: Boolean,
    hasConnectedAccountOrBackups: Boolean,
    onRunDriveConnectionTest: () -> Unit,
    onClearDriveConnectionTestReport: () -> Unit,
    onDeleteAllDriveBackups: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredSigningInfo = appSigningInfo
    val uriHandler = LocalUriHandler.current
    var showDeleteAllBackupsDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // User-facing Drive Connection Test
        OutlinedButton(
            onClick = onRunDriveConnectionTest,
            enabled = !isDriveOperationInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("drive_connection_test_button"),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Icon(UdhaarIcons.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.drive_run_connection_test_button), style = MaterialTheme.typography.labelMedium)
        }

        if (driveConnectionTestReport != null) {
            val testBorderColor = if (driveConnectionTestReport.passed) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, testBorderColor, RoundedCornerShape(10.dp))
                    .background(testBorderColor.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .testTag("drive_connection_test_result_card"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (driveConnectionTestReport.passed) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = null,
                        tint = testBorderColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (driveConnectionTestReport.passed) {
                            stringResource(R.string.drive_connection_test_passed_title)
                        } else {
                            stringResource(R.string.drive_connection_test_failed_title)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = testBorderColor
                    )
                }

                val plainFix = driveConnectionTestReport.plainLanguageFix
                val displayText = if (!plainFix.isNullOrBlank()) {
                    plainFix
                } else {
                    driveConnectionTestReport.errorMessage
                }
                if (displayText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, testBorderColor.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().testTag("drive_plain_fix_box")
                    ) {
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onClearDriveConnectionTestReport,
                    modifier = Modifier
                        .align(Alignment.End)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, testBorderColor)
                ) {
                    Text(
                        stringResource(R.string.ui_str_dismiss_d2fd9a),
                        style = MaterialTheme.typography.labelSmall,
                        color = testBorderColor
                    )
                }
            }
        }

        // Privacy Policy link (concise)
        OutlinedButton(
            onClick = {
                uriHandler.openUri("https://udhaar.jyotirmoyb.com/privacy.html")
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("drive_privacy_policy_button"),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(
                text = stringResource(R.string.drive_privacy_policy_button),
                style = MaterialTheme.typography.labelMedium
            )
        }

        // "Delete my Drive backups" button
        if (hasConnectedAccountOrBackups) {
            OutlinedButton(
                onClick = { showDeleteAllBackupsDialog = true },
                enabled = !isDriveOperationInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("drive_delete_all_backups_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Icon(
                    UdhaarIcons.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.drive_delete_backups_button),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteAllBackupsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllBackupsDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.drive_delete_backups_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.drive_delete_backups_dialog_msg),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllBackupsDialog = false
                        onDeleteAllDriveBackups()
                    },
                    modifier = Modifier.testTag("confirm_delete_all_drive_backups_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.drive_delete_backups_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteAllBackupsDialog = false },
                    modifier = Modifier.testTag("cancel_delete_all_drive_backups_button")
                ) {
                    Text(stringResource(R.string.ui_str_cancel_1507c4))
                }
            }
        )
    }
}
