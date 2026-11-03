package com.jyotirmoyb.udhaar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R

/**
 * Restore Preview Dialog: Previews record counts from parsed decrypted backup
 * and allows the user to choose Merge with existing or Replace all local data.
 */
@Composable
fun RestorePreviewDialog(
    backup: com.jyotirmoyb.udhaar.backup.DriveRemoteBackup,
    parsed: com.jyotirmoyb.udhaar.util.ParsedLedgerBackup,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.ui_str_restore_backup_preview_07b0c7),
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
                    text = stringResource(R.string.ui_fmt_backup_successfully_decrypted_from_1a9713, backup.fileName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BreakdownLine("Schema Version", "v${parsed.schemaVersion}")
                    BreakdownLine("Transactions", "${parsed.transactions.size}")
                    BreakdownLine("Parties", "${parsed.parties.size}")
                    BreakdownLine("Partial Payments", "${parsed.partialPayments.size}")
                    BreakdownLine("Chat Messages", "${parsed.chatMessages.size}")
                    BreakdownLine("Attachments", "${parsed.attachments.size}")
                    val defaultCurr = parsed.settings["defaultCurrency"] as? String
                    if (!defaultCurr.isNullOrBlank()) {
                        BreakdownLine("Currency", defaultCurr)
                    }
                }

                Text(
                    text = stringResource(R.string.ui_str_choose_how_to_apply_this_5b7adc),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.ui_str_merge_adds_all_remote_records_f600c4),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onMerge,
                    modifier = Modifier.testTag("restore_merge_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.ui_str_merge_1e0d71))
                }
                Button(
                    onClick = onReplace,
                    modifier = Modifier.testTag("restore_replace_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.ui_str_replace_all_16af75))
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("restore_cancel_button")
            ) {
                Text(stringResource(R.string.ui_str_cancel_1507c4))
            }
        }
    )
}
