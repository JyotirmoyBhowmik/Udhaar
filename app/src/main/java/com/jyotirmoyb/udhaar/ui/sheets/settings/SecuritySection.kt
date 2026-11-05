package com.jyotirmoyb.udhaar.ui.sheets.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.security.EncryptedStorageManager
import com.jyotirmoyb.udhaar.ui.common.SettingsSectionCard
import com.jyotirmoyb.udhaar.ui.components.AppIconButton
import com.jyotirmoyb.udhaar.ui.components.UdhaarAnimatedSwitch
import com.jyotirmoyb.udhaar.ui.components.minTouchTarget

@Composable
fun SecuritySection(
    currentSettings: UserSettings,
    dbEncryptionState: EncryptedStorageManager.DbEncryptionState,
    biometricLockEnabled: Boolean,
    onBiometricLockToggled: (Boolean) -> Unit,
    securityPin: String,
    onSecurityPinChanged: (String) -> Unit,
    pinValidationError: String?,
    onPinValidationErrorChanged: (String?) -> Unit,
    onRetryDatabaseEncryption: () -> Unit,
    onLockAppNow: () -> Unit,
    analyticsCrashConsent: Boolean = currentSettings.analyticsCrashConsent,
    onAnalyticsCrashConsentChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSecurityPin by rememberSaveable { mutableStateOf(false) }

    SettingsSectionCard(
        sectionNumber = "2",
        title = stringResource(R.string.settings_sec_security_title),
        subtitle = stringResource(R.string.settings_sec_security_sub),
        testTag = "section_security"
    ) {
        val isEncryptionFailed = dbEncryptionState ==
            EncryptedStorageManager.DbEncryptionState.FAILED ||
            dbEncryptionState ==
            EncryptedStorageManager.DbEncryptionState.NONE
        val isEncryptionMigrating = dbEncryptionState ==
            EncryptedStorageManager.DbEncryptionState.MIGRATING

        val statusTitle = when {
            dbEncryptionState == EncryptedStorageManager.DbEncryptionState.ENCRYPTED ->
                stringResource(R.string.db_encryption_status_yes)
            isEncryptionMigrating ->
                stringResource(R.string.db_encryption_status_migrating)
            else ->
                stringResource(R.string.db_encryption_status_failed_retry)
        }
        val statusSubtitle = when {
            dbEncryptionState == EncryptedStorageManager.DbEncryptionState.ENCRYPTED ->
                stringResource(R.string.db_encryption_subtitle_yes)
            isEncryptionMigrating ->
                stringResource(R.string.db_encryption_subtitle_migrating)
            else ->
                stringResource(R.string.db_encryption_subtitle_failed)
        }
        val borderColor = when {
            isEncryptionFailed -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .background(
                    if (isEncryptionFailed) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    RoundedCornerShape(12.dp)
                )
                .clickable(enabled = isEncryptionFailed) {
                    onRetryDatabaseEncryption()
                }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("database_encryption_status_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isEncryptionFailed) Icons.Default.Warning else Icons.Default.Lock,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isEncryptionFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("database_encryption_status_text")
                    )
                    Text(
                        text = statusSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isEncryptionFailed) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.ui_str_biometric_pin_app_lock_1d820f),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.ui_str_require_fingerprint_phone_pin_or_ea5359),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            UdhaarAnimatedSwitch(
                checked = biometricLockEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        val candidate = securityPin.trim()
                        val hasValidNewPin = candidate.length in 4..8 && candidate.all { it.isDigit() }
                        if (!currentSettings.hasPinConfigured && !hasValidNewPin) {
                            onPinValidationErrorChanged("Set a 4 to 8 digit numeric PIN below before turning on App Lock.")
                            Toast.makeText(
                                context,
                                "Enter a 4 to 8 digit PIN below first to enable App Lock.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@UdhaarAnimatedSwitch
                        }
                        onPinValidationErrorChanged(null)
                        onBiometricLockToggled(true)
                    } else {
                        onPinValidationErrorChanged(null)
                        onBiometricLockToggled(false)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .minTouchTarget()
                    .testTag("biometric_lock_switch")
            )
        }

        OutlinedTextField(
            value = securityPin,
            onValueChange = { input ->
                if (input.length <= 8 && input.all { it.isDigit() }) {
                    onSecurityPinChanged(input)
                    onPinValidationErrorChanged(null)
                }
            },
            label = {
                Text(
                    if (currentSettings.hasPinConfigured) {
                        stringResource(R.string.ui_str_change_4_8_digit_security_795f1a)
                    } else {
                        stringResource(R.string.ui_str_set_4_8_digit_security_400df1)
                    }
                )
            },
            placeholder = {
                Text(
                    if (currentSettings.hasPinConfigured) {
                        stringResource(R.string.ui_str_leave_blank_to_keep_current_b44066)
                    } else {
                        stringResource(R.string.ui_str_enter_4_to_8_digits_e796a0)
                    }
                )
            },
            singleLine = true,
            visualTransformation = if (showSecurityPin) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                AppIconButton(
                    onClick = { showSecurityPin = !showSecurityPin },
                    modifier = Modifier.testTag("toggle_settings_pin_visibility")
                ) {
                    Icon(
                        imageVector = if (showSecurityPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showSecurityPin) "Hide PIN" else "Show PIN",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            isError = pinValidationError != null,
            supportingText = {
                val currentPinError = pinValidationError
                if (currentPinError != null) {
                    Text(
                        text = currentPinError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    Text(
                        text = if (currentSettings.hasPinConfigured) {
                            stringResource(R.string.ui_str_pin_is_stored_as_a_a204da)
                        } else {
                            stringResource(R.string.ui_str_no_default_pin_exists_you_7470c3)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_security_pin_input")
        )

        if (currentSettings.hasPinConfigured || (securityPin.trim().length in 4..8)) {
            OutlinedButton(
                onClick = onLockAppNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("lock_app_now_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.ui_str_lock_app_now_5b5570), style = MaterialTheme.typography.labelMedium)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

        // Opt-in Crashlytics & Screen Analytics (Off by default, zero personal data)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("analytics_crash_consent_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.settings_analytics_crash_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_analytics_crash_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            UdhaarAnimatedSwitch(
                checked = analyticsCrashConsent,
                onCheckedChange = { enabled ->
                    com.jyotirmoyb.udhaar.util.TelemetryManager.setConsent(context, enabled)
                    onAnalyticsCrashConsentChanged(enabled)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .minTouchTarget()
                    .testTag("analytics_crash_consent_switch")
            )
        }
    }
}
