package com.jyotirmoyb.udhaar.ui.sheets.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.BuildConfig
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.backup.DriveConnectionTestReport
import com.jyotirmoyb.udhaar.ui.common.SigningCredentialRow
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
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    var showTechnicalDetails by rememberSaveable { mutableStateOf(false) }
    var showDeleteAllBackupsDialog by rememberSaveable { mutableStateOf(false) }

    val appPkg = appSigningInfo?.packageName ?: BuildConfig.APPLICATION_ID
    val sha1Val = appSigningInfo?.sha1Fingerprint ?: ""
    val sha256Val = appSigningInfo?.sha256Fingerprint ?: ""

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // App Signing Credentials (Package name, SHA-1, SHA-256 with Copy buttons)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(12.dp)
                .testTag("app_signing_credentials_card"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.drive_signing_credentials_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(R.string.drive_signing_credentials_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SigningCredentialRow(
                label = stringResource(R.string.drive_package_name_label),
                value = appPkg,
                testTag = "package_name_row",
                copyTag = "copy_package_name_button",
                onCopy = {
                    clipboardManager.setText(AnnotatedString(appPkg))
                    Toast.makeText(
                        context,
                        context.getString(R.string.drive_copied_toast_fmt, context.getString(R.string.drive_package_name_label)),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            if (sha1Val.isNotBlank()) {
                SigningCredentialRow(
                    label = stringResource(R.string.drive_sha1_label),
                    value = sha1Val,
                    testTag = "sha1_credential_row",
                    copyTag = "copy_sha1_button",
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(sha1Val))
                        Toast.makeText(
                            context,
                            context.getString(R.string.drive_copied_toast_fmt, "SHA-1"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            if (sha256Val.isNotBlank()) {
                SigningCredentialRow(
                    label = stringResource(R.string.drive_sha256_label),
                    value = sha256Val,
                    testTag = "sha256_credential_row",
                    copyTag = "copy_sha256_button",
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(sha256Val))
                        Toast.makeText(
                            context,
                            context.getString(R.string.drive_copied_toast_fmt, "SHA-256"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }

        // "Run connection test" Button & Diagnostic Result Card
        OutlinedButton(
            onClick = onRunDriveConnectionTest,
            enabled = !isDriveOperationInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("drive_connection_test_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
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
                    .border(1.dp, testBorderColor, RoundedCornerShape(12.dp))
                    .background(testBorderColor.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
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
                        modifier = Modifier.size(18.dp)
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
                if (!driveConnectionTestReport.failedStep.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.drive_connection_test_step_fmt, driveConnectionTestReport.failedStep),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val plainFix = driveConnectionTestReport.plainLanguageFix
                if (!plainFix.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, testBorderColor.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().testTag("drive_plain_fix_box")
                    ) {
                        Text(
                            text = plainFix,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    Text(
                        text = driveConnectionTestReport.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val techDetails = driveConnectionTestReport.technicalDetails
                    ?: listOfNotNull(
                        driveConnectionTestReport.errorCode.takeIf { it.isNotBlank() }?.let { "Code: $it" },
                        driveConnectionTestReport.errorMessage.takeIf { it.isNotBlank() }
                    ).joinToString("\n")

                if (techDetails.isNotBlank()) {
                    TextButton(
                        onClick = { showTechnicalDetails = !showTechnicalDetails },
                        modifier = Modifier.testTag("toggle_technical_details_button")
                    ) {
                        Text(
                            text = if (showTechnicalDetails) {
                                stringResource(R.string.drive_hide_technical_details)
                            } else {
                                stringResource(R.string.drive_show_technical_details)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (showTechnicalDetails) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth().testTag("drive_technical_details_panel")
                        ) {
                            Text(
                                text = techDetails,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onClearDriveConnectionTestReport,
                    modifier = Modifier
                        .align(Alignment.End)
                        .height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, testBorderColor)
                ) {
                    Text(stringResource(R.string.ui_str_dismiss_d2fd9a), style = MaterialTheme.typography.labelSmall, color = testBorderColor)
                }
            }
        }

        // Help text for common Google Drive setup causes
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(12.dp)
                .testTag("drive_common_causes_help_card"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.drive_common_causes_title),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.drive_common_causes_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Privacy Policy & Data Safety link
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.drive_privacy_policy_button),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.drive_privacy_policy_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        uriHandler.openUri("https://udhaar.jyotirmoyb.com/privacy.html")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("drive_privacy_policy_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.drive_privacy_policy_button),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // "Delete my Drive backups" button
        if (hasConnectedAccountOrBackups) {
            OutlinedButton(
                onClick = { showDeleteAllBackupsDialog = true },
                enabled = !isDriveOperationInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("drive_delete_all_backups_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
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
