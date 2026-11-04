package com.jyotirmoyb.udhaar.ui.sheets.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R

@Composable
fun DriveBackupPassphraseDialog(
    isLocalUdhaarExportMode: Boolean,
    initialSavePassphraseForAuto: Boolean,
    onDismiss: () -> Unit,
    onConfirmDriveBackup: (passphrase: String, saveForAuto: Boolean) -> Unit,
    onConfirmLocalExport: (passphrase: String) -> Unit
) {
    var backupPassphrase by rememberSaveable { mutableStateOf("") }
    var confirmBackupPassphrase by rememberSaveable { mutableStateOf("") }
    var savePassphraseForAuto by rememberSaveable { mutableStateOf(initialSavePassphraseForAuto) }
    var backupPassphraseError by rememberSaveable { mutableStateOf<String?>(null) }
    var backupPassphraseVisible by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.ui_str_encrypt_back_up_to_google_a82102),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.ui_str_set_a_passphrase_to_encrypt_4bdc01),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = backupPassphrase,
                    onValueChange = {
                        backupPassphrase = it
                        backupPassphraseError = null
                    },
                    label = { Text(stringResource(R.string.ui_str_backup_passphrase_2a9696)) },
                    placeholder = { Text(stringResource(R.string.ui_str_enter_strong_passphrase_2db9a2)) },
                    singleLine = true,
                    visualTransformation = if (backupPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { backupPassphraseVisible = !backupPassphraseVisible }) {
                            Icon(
                                imageVector = if (backupPassphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_passphrase_input")
                )

                OutlinedTextField(
                    value = confirmBackupPassphrase,
                    onValueChange = {
                        confirmBackupPassphrase = it
                        backupPassphraseError = null
                    },
                    label = { Text(stringResource(R.string.ui_str_confirm_passphrase_81f868)) },
                    placeholder = { Text(stringResource(R.string.ui_str_re_enter_passphrase_87fa77)) },
                    singleLine = true,
                    visualTransformation = if (backupPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_confirm_passphrase_input")
                )

                val currentBackupPassError = backupPassphraseError
                if (currentBackupPassError != null) {
                    Text(
                        text = currentBackupPassError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (!isLocalUdhaarExportMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = savePassphraseForAuto,
                            onCheckedChange = { savePassphraseForAuto = it },
                            modifier = Modifier.testTag("save_passphrase_for_auto_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.ui_str_save_securely_in_phone_keystore_216a55),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pass = backupPassphrase.trim()
                    val confirm = confirmBackupPassphrase.trim()
                    if (pass.length < 4) {
                        backupPassphraseError = "Passphrase must be at least 4 characters."
                        return@Button
                    }
                    if (pass != confirm) {
                        backupPassphraseError = "Passphrases do not match."
                        return@Button
                    }
                    if (isLocalUdhaarExportMode) {
                        onConfirmLocalExport(pass)
                    } else {
                        onConfirmDriveBackup(pass, savePassphraseForAuto)
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_encrypt_and_backup_button")
            ) {
                Text(
                    if (isLocalUdhaarExportMode) {
                        stringResource(R.string.export_local_udhaar_backup)
                    } else {
                        stringResource(R.string.ui_str_encrypt_upload_623890)
                    }
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_backup_dialog_button")
            ) {
                Text(stringResource(R.string.ui_str_cancel_1507c4))
            }
        }
    )
}
