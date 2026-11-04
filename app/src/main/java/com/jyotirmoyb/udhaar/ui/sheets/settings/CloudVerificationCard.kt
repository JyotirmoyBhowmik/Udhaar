package com.jyotirmoyb.udhaar.ui.sheets.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CloudBackupState
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.ui.components.UdhaarAnimatedSwitch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun CloudVerificationCard(
    currentSettings: UserSettings,
    cloudVerificationConsent: Boolean,
    onCloudVerificationConsentChange: (Boolean) -> Unit,
    onSignInWithGoogleForVerification: () -> Unit,
    onRetryCloudBackup: () -> Unit,
    onOpenReceivedVerificationLink: (String) -> Unit,
    onDeleteMyCloudData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var incomingLinkInput by rememberSaveable { mutableStateOf("") }
    var confirmCloudDelete by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.ui_str_two_party_cloud_verification_privac_8c2a13),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.ui_str_udhaar_works_100_offline_by_5362e5),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ui_str_explicit_cloud_verification_consent_255a87),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.ui_str_allow_shared_records_e2ee_notes_2689cf),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            UdhaarAnimatedSwitch(
                checked = cloudVerificationConsent,
                onCheckedChange = onCloudVerificationConsentChange,
                modifier = Modifier.testTag("cloud_verification_consent_switch")
            )
        }

        OutlinedButton(
            onClick = onSignInWithGoogleForVerification,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("google_sign_in_verification_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            val cloudAccountLabel = currentSettings.cloudUserEmail.ifBlank { stringResource(R.string.ui_verified_account_caps) }
            Text(
                text = if (currentSettings.cloudUserId.isNotBlank()) {
                    stringResource(R.string.ui_fmt_signed_in_06c074, cloudAccountLabel)
                } else {
                    stringResource(R.string.ui_str_sign_in_with_google_for_7f6a17)
                },
                style = MaterialTheme.typography.labelMedium
            )
        }

        if (currentSettings.cloudUserId.isNotBlank() ||
            currentSettings.cloudBackupState != CloudBackupState.IDLE ||
            currentSettings.cloudLastBackupTimeMillis > 0L
        ) {
            val backupState = currentSettings.cloudBackupState
            val lastBackupMillis = currentSettings.cloudLastBackupTimeMillis
            val isJustNow = lastBackupMillis > 0L &&
                abs(System.currentTimeMillis() - lastBackupMillis) < 90_000L
            val formattedTimestamp = remember(lastBackupMillis) {
                if (lastBackupMillis > 0L) {
                    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                        .format(Date(lastBackupMillis))
                } else {
                    ""
                }
            }
            val statusText = when {
                backupState == CloudBackupState.BACKING_UP ->
                    stringResource(R.string.cloud_backup_status_backing_up)
                backupState == CloudBackupState.FAILED ->
                    stringResource(R.string.cloud_backup_status_failed_retry)
                lastBackupMillis > 0L && isJustNow ->
                    stringResource(R.string.cloud_backup_status_just_now)
                lastBackupMillis > 0L ->
                    stringResource(
                        R.string.cloud_backup_status_timestamp,
                        formattedTimestamp
                    )
                else ->
                    stringResource(R.string.cloud_backup_status_backing_up)
            }
            val statusColor = when (backupState) {
                CloudBackupState.FAILED -> MaterialTheme.colorScheme.error
                CloudBackupState.BACKING_UP -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.primary
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = statusColor.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cloud_backup_status_row")
                    .then(
                        if (backupState != CloudBackupState.BACKING_UP) {
                            Modifier.clickable { onRetryCloudBackup() }
                        } else {
                            Modifier
                        }
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = when (backupState) {
                            CloudBackupState.FAILED -> Icons.Default.Warning
                            CloudBackupState.BACKING_UP -> Icons.Default.Sync
                            else -> Icons.Default.Check
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.testTag("cloud_backup_status_text")
                        )
                        Text(
                            text = when {
                                backupState == CloudBackupState.FAILED &&
                                    currentSettings.cloudBackupError.isNotBlank() ->
                                    currentSettings.cloudBackupError
                                backupState == CloudBackupState.BACKING_UP ->
                                    stringResource(R.string.cloud_backup_subtitle_syncing)
                                else ->
                                    stringResource(R.string.cloud_backup_subtitle_synced)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Open / Verify a received link directly
        OutlinedTextField(
            value = incomingLinkInput,
            onValueChange = { incomingLinkInput = it },
            label = { Text(stringResource(R.string.ui_str_open_received_verification_or_payme_e37547)) },
            placeholder = { Text(stringResource(R.string.ui_str_paste_https_jyotirmoyb_com_u_6b00ac)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_open_verification_link_input")
        )

        if (incomingLinkInput.isNotBlank()) {
            Button(
                onClick = {
                    onOpenReceivedVerificationLink(incomingLinkInput.trim())
                    incomingLinkInput = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("settings_open_verification_link_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.ui_str_inspect_shared_link_022c24), style = MaterialTheme.typography.labelMedium)
            }
        }

        OutlinedButton(
            onClick = {
                if (confirmCloudDelete) {
                    confirmCloudDelete = false
                    onDeleteMyCloudData()
                } else {
                    confirmCloudDelete = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("delete_my_cloud_data_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (confirmCloudDelete) {
                    stringResource(R.string.ui_str_tap_again_to_permanently_delete_1bbe18)
                } else {
                    stringResource(R.string.ui_str_delete_my_cloud_data_c570cb)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
