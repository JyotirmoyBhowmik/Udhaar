package com.jyotirmoyb.udhaar.ui.sheets.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R

/**
 * Enterprise Production-Grade Account and Data Deletion Dialog (Google Play User Data Policy).
 * Supports:
 * 1. Explicit multi-step confirmation with checkbox for Google Drive backups.
 * 2. Real-time batched deletion progress state.
 * 3. Security re-authentication notification if auth token is stale.
 * 4. Final confirmation acknowledgement.
 */
@Composable
fun DeleteAccountAndDataDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onConfirmDelete: (deleteDriveBackups: Boolean) -> Unit,
    isDeleting: Boolean,
    progressMessage: String,
    errorMessage: String? = null,
    isReauthRequired: Boolean = false,
    onReauthenticate: () -> Unit = {},
    isCompleted: Boolean = false,
    onCompletedDismiss: () -> Unit = {}
) {
    if (!isOpen) return

    var deleteDriveBackups by rememberSaveable { mutableStateOf(true) }

    when {
        isCompleted -> {
            AlertDialog(
                onDismissRequest = onCompletedDismiss,
                modifier = Modifier.testTag("dialog_account_deleted_final"),
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.account_delete_success_title),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.account_delete_success_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.account_delete_success_msg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onCompletedDismiss,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_account_deleted_done")
                    ) {
                        Text(stringResource(R.string.delete_account_done_dismiss))
                    }
                }
            )
        }

        isDeleting -> {
            AlertDialog(
                onDismissRequest = {}, // Non-cancelable during critical multi-layer deletion
                modifier = Modifier.testTag("dialog_account_deleting_progress"),
                icon = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.error,
                        strokeWidth = 3.dp
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.account_delete_all_data_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = progressMessage.ifBlank { stringResource(R.string.delete_account_progress_firestore) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("text_account_delete_progress")
                        )
                    }
                },
                confirmButton = {}
            )
        }

        isReauthRequired -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                modifier = Modifier.testTag("dialog_account_delete_reauth"),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.account_delete_reauth_title),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.account_delete_reauth_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.account_delete_reauth_msg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onReauthenticate,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_confirm_reauth_before_delete")
                    ) {
                        Text(stringResource(R.string.account_sign_in_heading))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.account_delete_cancel_btn))
                    }
                }
            )
        }

        else -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                modifier = Modifier.testTag("dialog_account_delete_confirmation"),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.account_delete_all_data_title),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.account_delete_all_data_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.account_delete_all_data_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = deleteDriveBackups,
                                onCheckedChange = { deleteDriveBackups = it },
                                modifier = Modifier.testTag("checkbox_delete_drive_backup")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.account_delete_drive_backup_checkbox),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("error_account_delete_dialog")
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onConfirmDelete(deleteDriveBackups) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_confirm_delete_account_and_data")
                    ) {
                        Text(
                            text = stringResource(R.string.account_delete_confirm_btn),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_cancel_delete_account")
                    ) {
                        Text(stringResource(R.string.account_delete_cancel_btn))
                    }
                }
            )
        }
    }
}
