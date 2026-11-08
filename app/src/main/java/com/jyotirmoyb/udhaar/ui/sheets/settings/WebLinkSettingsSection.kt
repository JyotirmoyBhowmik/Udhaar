package com.jyotirmoyb.udhaar.ui.sheets.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.jyotirmoyb.udhaar.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.ui.common.SettingsSectionCard

@Composable
fun WebLinkSettingsSection(
    settings: UserSettings,
    onSaveDefaults: (expiryDays: Int, trustLevel: String, showUpi: Boolean) -> Unit,
    onRevokeAllLinks: () -> Unit
) {
    var expiryDays by rememberSaveable { mutableIntStateOf(settings.defaultWebLinkExpiryDays) }
    var trustLevel by rememberSaveable { mutableStateOf(settings.defaultWebTrustLevel) }
    var showUpi by rememberSaveable { mutableStateOf(settings.defaultWebShowUpi) }
    var showRevokeConfirmDialog by rememberSaveable { mutableStateOf(false) }

    SettingsSectionCard(
        sectionNumber = "6",
        title = stringResource(R.string.web_share_sheet_title),
        subtitle = stringResource(R.string.settings_web_link_upi_subtitle),
        testTag = "settings_web_links_section"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Expiry Days
            Text(
                text = stringResource(R.string.settings_default_link_expiry),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { days ->
                    FilterChip(
                        selected = expiryDays == days,
                        onClick = {
                            expiryDays = days
                            onSaveDefaults(days, trustLevel, showUpi)
                        },
                        label = { Text(stringResource(R.string.days_count, days)) },
                        modifier = Modifier.testTag("settings_expiry_chip_$days")
                    )
                }
            }

            // Trust Level Default
            Text(
                text = stringResource(R.string.settings_default_verification_method),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = trustLevel == "WHATSAPP_OTP",
                    onClick = {
                        trustLevel = "WHATSAPP_OTP"
                        onSaveDefaults(expiryDays, "WHATSAPP_OTP", showUpi)
                    },
                    label = { Text(stringResource(R.string.trust_level_whatsapp_otp)) },
                    leadingIcon = {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("settings_trust_whatsapp")
                )
                FilterChip(
                    selected = trustLevel == "LINK_ONLY",
                    onClick = {
                        trustLevel = "LINK_ONLY"
                        onSaveDefaults(expiryDays, "LINK_ONLY", showUpi)
                    },
                    label = { Text(stringResource(R.string.trust_level_link_only)) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("settings_trust_link_only")
                )
            }

            // Show UPI toggle
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_web_link_upi_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_web_link_upi_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = showUpi,
                        onCheckedChange = {
                            showUpi = it
                            onSaveDefaults(expiryDays, trustLevel, it)
                        },
                        modifier = Modifier.testTag("settings_switch_web_show_upi")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Revoke All Links Button
            OutlinedButton(
                onClick = { showRevokeConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("btn_settings_revoke_all_links"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.btn_revoke_all_web_links))
            }
        }
    }

    if (showRevokeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeConfirmDialog = false },
            title = { Text(stringResource(R.string.dialog_revoke_all_web_links_title)) },
            text = { Text(stringResource(R.string.dialog_revoke_all_web_links_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevokeConfirmDialog = false
                        onRevokeAllLinks()
                    }
                ) {
                    Text(stringResource(R.string.btn_revoke_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeConfirmDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}
