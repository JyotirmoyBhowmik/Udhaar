package com.jyotirmoyb.udhaar.ui.sheets.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.ui.icons.UdhaarIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountSignedInPanel(
    userEmail: String,
    userPhone: String,
    isEmailVerified: Boolean,
    isCloudSyncEnabled: Boolean,
    lastSyncTime: Long,
    showReauthPanel: Boolean,
    reauthPasswordInput: String,
    isReauthenticated: Boolean,
    confirmDeleteCloudData: Boolean,
    isLoading: Boolean,
    isDeletingAccount: Boolean,
    onResendVerification: () -> Unit,
    onRefreshVerification: () -> Unit,
    onToggleCloudSync: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onOpenReauthPanel: () -> Unit,
    onReauthPasswordChange: (String) -> Unit,
    onConfirmPasswordReauth: () -> Unit,
    onReauthWithGoogle: () -> Unit,
    onCancelReauth: () -> Unit,
    onDeleteCloudDataClick: () -> Unit,
    onOpenDeleteAccountDialog: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AccountIdentityCard(
            userEmail = userEmail,
            userPhone = userPhone,
            isEmailVerified = isEmailVerified
        )

        if (!isEmailVerified) {
            UnverifiedEmailBanner(
                isLoading = isLoading,
                onResendVerification = onResendVerification,
                onRefreshVerification = onRefreshVerification
            )
        }

        CloudSyncStatusCard(
            isEmailVerified = isEmailVerified,
            isCloudSyncEnabled = isCloudSyncEnabled,
            lastSyncTime = lastSyncTime,
            isLoading = isLoading,
            onToggleCloudSync = onToggleCloudSync
        )

        if (isCloudSyncEnabled && isEmailVerified) {
            OutlinedButton(
                onClick = onSyncNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("btn_sync_now"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.account_sync_now))
            }
        }

        AccountReauthSection(
            showReauthPanel = showReauthPanel,
            reauthPasswordInput = reauthPasswordInput,
            isReauthenticated = isReauthenticated,
            isLoading = isLoading,
            onOpenReauthPanel = onOpenReauthPanel,
            onReauthPasswordChange = onReauthPasswordChange,
            onConfirmPasswordReauth = onConfirmPasswordReauth,
            onReauthWithGoogle = onReauthWithGoogle,
            onCancelReauth = onCancelReauth
        )

        Text(
            text = stringResource(R.string.account_delete_cloud_data_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = onDeleteCloudDataClick,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(if (confirmDeleteCloudData) "btn_confirm_delete_cloud_data" else "btn_delete_cloud_data"),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(
                imageVector = UdhaarIcons.DeleteOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (confirmDeleteCloudData) {
                    stringResource(R.string.account_delete_cloud_data_confirm_btn)
                } else {
                    stringResource(R.string.account_delete_cloud_data_btn)
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.account_delete_all_data_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onOpenDeleteAccountDialog,
            enabled = !isLoading && !isDeletingAccount,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("btn_delete_account_and_data"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Icon(
                imageVector = UdhaarIcons.DeleteOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onError
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.account_delete_all_data_btn),
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        OutlinedButton(
            onClick = onSignOut,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("btn_account_signout"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text(stringResource(R.string.account_sign_out_btn))
        }
    }
}

@Composable
private fun AccountIdentityCard(
    userEmail: String,
    userPhone: String,
    isEmailVerified: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isEmailVerified) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isEmailVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (isEmailVerified) {
                        stringResource(R.string.ui_verified_account_caps)
                    } else {
                        stringResource(R.string.account_unverified_badge)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("email_verification_badge")
                )
            }
            if (userEmail.isNotBlank()) {
                Text(
                    text = stringResource(R.string.account_email_fmt, userEmail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (userPhone.isNotBlank()) {
                Text(
                    text = stringResource(R.string.account_phone_fmt, userPhone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnverifiedEmailBanner(
    isLoading: Boolean,
    onResendVerification: () -> Unit,
    onRefreshVerification: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("unverified_email_banner")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.account_unverified_banner_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = stringResource(R.string.account_unverified_banner_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onResendVerification,
                    enabled = !isLoading,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_resend_verification_email")
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.account_resend_verification_btn))
                }

                Button(
                    onClick = onRefreshVerification,
                    enabled = !isLoading,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_refresh_verification_status")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.account_refresh_verification_btn))
                }
            }
        }
    }
}

@Composable
private fun CloudSyncStatusCard(
    isEmailVerified: Boolean,
    isCloudSyncEnabled: Boolean,
    lastSyncTime: Long,
    isLoading: Boolean,
    onToggleCloudSync: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.account_sync_status_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = if (!isEmailVerified) {
                        stringResource(R.string.account_verify_first_hint)
                    } else {
                        stringResource(R.string.account_sync_status_on)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (lastSyncTime > 0L && isEmailVerified) {
                    val fmt = remember(lastSyncTime) {
                        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(lastSyncTime))
                    }
                    Text(
                        text = stringResource(R.string.account_last_synced_fmt, fmt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Switch(
                checked = isCloudSyncEnabled && isEmailVerified,
                enabled = isEmailVerified && !isLoading,
                onCheckedChange = onToggleCloudSync,
                modifier = Modifier.testTag("switch_cloud_sync")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountReauthSection(
    showReauthPanel: Boolean,
    reauthPasswordInput: String,
    isReauthenticated: Boolean,
    isLoading: Boolean,
    onOpenReauthPanel: () -> Unit,
    onReauthPasswordChange: (String) -> Unit,
    onConfirmPasswordReauth: () -> Unit,
    onReauthWithGoogle: () -> Unit,
    onCancelReauth: () -> Unit
) {
    if (!showReauthPanel) {
        OutlinedButton(
            onClick = onOpenReauthPanel,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("btn_open_reauth"),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (isReauthenticated) {
                    stringResource(R.string.account_reauthenticated_done)
                } else {
                    stringResource(R.string.account_reauthenticate_btn)
                }
            )
        }
    } else {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reauth_panel")
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.account_confirm_identity_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = reauthPasswordInput,
                    onValueChange = onReauthPasswordChange,
                    label = { Text(stringResource(R.string.account_current_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reauth_password_input")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onConfirmPasswordReauth,
                        enabled = !isLoading && reauthPasswordInput.isNotEmpty(),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_confirm_reauth")
                    ) {
                        Text(stringResource(R.string.account_verify_password_btn))
                    }
                    OutlinedButton(
                        onClick = onReauthWithGoogle,
                        enabled = !isLoading,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_reauth_google")
                    ) {
                        Text(stringResource(R.string.account_reauth_google_btn))
                    }
                    TextButton(
                        onClick = onCancelReauth,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_cancel_reauth")
                    ) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            }
        }
    }
}
