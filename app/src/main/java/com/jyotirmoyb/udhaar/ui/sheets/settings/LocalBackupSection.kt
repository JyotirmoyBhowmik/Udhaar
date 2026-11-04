package com.jyotirmoyb.udhaar.ui.sheets.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
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

@Composable
fun LocalBackupSection(
    onExportFullLedgerSaf: () -> Unit,
    onShareFullLedgerFile: () -> Unit,
    onExportLocalUdhaarSafClicked: () -> Unit,
    onImportLedgerSaf: () -> Unit,
    onOpenContactPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.ui_str_local_offline_json_backup_restore_01188e),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.ui_str_export_your_entire_ledger_all_3a1431),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onExportFullLedgerSaf,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("export_ledger_json_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.ui_str_save_backup_9dd257), style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
                onClick = onShareFullLedgerFile,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("share_ledger_json_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.ui_str_share_file_39064b), style = MaterialTheme.typography.labelMedium)
            }
        }

        OutlinedButton(
            onClick = onExportLocalUdhaarSafClicked,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("export_local_udhaar_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.export_local_udhaar_backup), style = MaterialTheme.typography.labelMedium)
        }

        OutlinedButton(
            onClick = onImportLedgerSaf,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("import_ledger_json_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.ui_str_import_restore_json_backup_61b7ef), style = MaterialTheme.typography.labelMedium)
        }

        OutlinedButton(
            onClick = onOpenContactPicker,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("settings_import_contacts_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.ui_str_import_from_contacts_8cdd06), style = MaterialTheme.typography.labelMedium)
        }
    }
}
