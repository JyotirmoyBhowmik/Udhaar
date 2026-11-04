package com.jyotirmoyb.udhaar.ui.sheets.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.backup.DriveRemoteBackup

@Composable
fun DriveRestorePassphraseDialog(
    backup: DriveRemoteBackup,
    onDismiss: () -> Unit,
    onConfirmRestore: (DriveRemoteBackup, passphrase: String) -> Unit
) {
    var restorePassphrase by rememberSaveable { mutableStateOf("") }
    var restorePassphraseVisible by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.ui_str_decrypt_preview_restore_e4b787),
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
                    text = stringResource(R.string.ui_fmt_enter_the_passphrase_used_when_0b3c36, backup.fileName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = restorePassphrase,
                    onValueChange = { restorePassphrase = it },
                    label = { Text(stringResource(R.string.ui_str_backup_passphrase_2a9696)) },
                    placeholder = { Text(stringResource(R.string.ui_str_passphrase_89950c)) },
                    singleLine = true,
                    visualTransformation = if (restorePassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { restorePassphraseVisible = !restorePassphraseVisible }) {
                            Icon(
                                imageVector = if (restorePassphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restore_passphrase_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pass = restorePassphrase.trim()
                    if (pass.isNotEmpty()) {
                        onConfirmRestore(backup, pass)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("confirm_decrypt_and_preview_button")
            ) {
                Text(stringResource(R.string.ui_str_decrypt_preview_10fcf4))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_restore_dialog_button")
            ) {
                Text(stringResource(R.string.ui_str_cancel_1507c4))
            }
        }
    )
}
