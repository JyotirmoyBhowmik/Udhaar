package com.jyotirmoyb.udhaar.ui.sheets.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.SyncStatus
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.security.EncryptedTokenStore
import com.jyotirmoyb.udhaar.security.TokenStore
import com.jyotirmoyb.udhaar.sync.ApiClient
import com.jyotirmoyb.udhaar.sync.DeviceDto
import com.jyotirmoyb.udhaar.sync.SyncWorker
import com.jyotirmoyb.udhaar.ui.common.SettingsSectionCard
import com.jyotirmoyb.udhaar.ui.components.UdhaarAnimatedSwitch
import com.jyotirmoyb.udhaar.ui.components.minTouchTarget
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PHONE_PLACEHOLDER = "+919876543210"
private const val EMAIL_PLACEHOLDER = "user@example.com"

@Composable
fun AccountAndWebAccessSection(
    dao: UdhaarDao,
    tokenStore: TokenStore = EncryptedTokenStore(LocalContext.current),
    apiClient: ApiClient? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isSignedIn by remember { mutableStateOf(tokenStore.isSignedIn()) }
    var isSyncEnabled by remember { mutableStateOf(tokenStore.isCloudSyncEnabled()) }
    var lastSyncTimestamp by remember { mutableStateOf(tokenStore.getLastSyncTimestamp()) }

    var isSignUpMode by rememberSaveable { mutableStateOf(false) }
    var phoneInput by rememberSaveable { mutableStateOf("") }
    var otpInput by rememberSaveable { mutableStateOf("") }
    var emailInput by rememberSaveable { mutableStateOf("") }
    var emailOtpInput by rememberSaveable { mutableStateOf("") }

    var flowId by rememberSaveable { mutableStateOf<String?>(null) }
    var signUpStep by rememberSaveable { mutableIntStateOf(1) } // 1: phone, 2: phone OTP, 3: email, 4: email OTP
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    var showUploadPreviewDialog by rememberSaveable { mutableStateOf(false) }
    var showSignOutConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var localRecordCount by rememberSaveable { mutableIntStateOf(0) }
    var localPaymentCount by rememberSaveable { mutableIntStateOf(0) }

    var devicesList by remember { mutableStateOf<List<DeviceDto>>(emptyList()) }
    var conflictedTransactions by remember { mutableStateOf<List<UdhaarTransaction>>(emptyList()) }

    // Load initial counts & conflicts
    LaunchedEffect(isSignedIn) {
        if (isSignedIn) {
            val txs = dao.getAllTransactionsSnapshot()
            conflictedTransactions = txs.filter { it.syncStatus == SyncStatus.CONFLICT }
            apiClient?.let { client ->
                runCatching {
                    val resp = client.getDevices()
                    devicesList = resp.devices
                }
            }
        }
    }

    SettingsSectionCard(
        sectionNumber = "06",
        title = stringResource(R.string.account_and_web_access_title),
        subtitle = stringResource(R.string.account_and_web_access_subtitle),
        testTag = "section_account_and_web_access"
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isSignedIn) {
                // -------------------------------------------------------------
                // SIGN IN / SIGN UP FLOW
                // -------------------------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(if (isSignUpMode) R.string.account_sign_up_heading else R.string.account_sign_in_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = {
                            isSignUpMode = !isSignUpMode
                            flowId = null
                            signUpStep = 1
                            errorMessage = null
                        },
                        modifier = Modifier.minTouchTarget()
                    ) {
                        Text(
                            text = stringResource(if (isSignUpMode) R.string.account_switch_to_signin else R.string.account_switch_to_signup),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (!isSignUpMode) {
                    // Sign In Mode: Phone -> WhatsApp Code
                    if (flowId == null) {
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text(stringResource(R.string.account_phone_label)) },
                            placeholder = { Text(text = PHONE_PLACEHOLDER) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signin_phone_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                if (phoneInput.isBlank()) return@Button
                                isLoading = true
                                errorMessage = null
                                coroutineScope.launch {
                                    try {
                                        val resp = apiClient?.startSignin(phoneInput.trim())
                                        flowId = resp?.flowId ?: "flow_${System.currentTimeMillis()}"
                                        Toast.makeText(context, "Verification code sent via WhatsApp", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Failed to send code"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading && phoneInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("signin_send_otp_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.account_send_otp_btn))
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = otpInput,
                            onValueChange = { if (it.length <= 6) otpInput = it },
                            label = { Text(stringResource(R.string.account_otp_label)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signin_otp_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                if (otpInput.length < 6) return@Button
                                isLoading = true
                                errorMessage = null
                                coroutineScope.launch {
                                    try {
                                        apiClient?.verifySignin(flowId!!, otpInput.trim())
                                        isSignedIn = true
                                        isSyncEnabled = tokenStore.isCloudSyncEnabled()
                                        // Load local records for upload preview
                                        val txs = dao.getAllTransactionsSnapshot()
                                        localRecordCount = txs.size
                                        localPaymentCount = 0 // Count snapshot
                                        showUploadPreviewDialog = true
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Invalid code"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading && otpInput.length == 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("signin_verify_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.account_verify_and_continue))
                            }
                        }
                    }
                } else {
                    // Sign Up Mode: Phone -> Phone OTP -> Email -> Email OTP
                    when (signUpStep) {
                        1 -> {
                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                label = { Text(stringResource(R.string.account_phone_label)) },
                                placeholder = { Text(text = PHONE_PLACEHOLDER) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_phone_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (phoneInput.isBlank()) return@Button
                                    isLoading = true
                                    errorMessage = null
                                    coroutineScope.launch {
                                        try {
                                            val resp = apiClient?.startSignup(phoneInput.trim())
                                            flowId = resp?.flowId ?: "flow_${System.currentTimeMillis()}"
                                            signUpStep = 2
                                        } catch (e: Exception) {
                                            errorMessage = e.message ?: "Failed to send code"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                enabled = !isLoading && phoneInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("signup_send_phone_otp_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.account_send_otp_btn))
                                }
                            }
                        }
                        2 -> {
                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = { if (it.length <= 6) otpInput = it },
                                label = { Text(stringResource(R.string.account_otp_label)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_phone_otp_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (otpInput.length < 6) return@Button
                                    isLoading = true
                                    errorMessage = null
                                    coroutineScope.launch {
                                        try {
                                            apiClient?.verifySignupPhone(flowId!!, otpInput.trim())
                                            signUpStep = 3
                                        } catch (e: Exception) {
                                            errorMessage = e.message ?: "Invalid phone code"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                enabled = !isLoading && otpInput.length == 6,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("signup_verify_phone_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.account_verify_and_continue))
                                }
                            }
                        }
                        3 -> {
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text(stringResource(R.string.account_email_label)) },
                                placeholder = { Text(text = EMAIL_PLACEHOLDER) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_email_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (emailInput.isBlank()) return@Button
                                    isLoading = true
                                    errorMessage = null
                                    coroutineScope.launch {
                                        try {
                                            apiClient?.sendSignupEmail(flowId!!, emailInput.trim())
                                            signUpStep = 4
                                        } catch (e: Exception) {
                                            errorMessage = e.message ?: "Failed to send email code"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                enabled = !isLoading && emailInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("signup_send_email_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.account_send_email_code))
                                }
                            }
                        }
                        4 -> {
                            OutlinedTextField(
                                value = emailOtpInput,
                                onValueChange = { if (it.length <= 6) emailOtpInput = it },
                                label = { Text(stringResource(R.string.account_otp_label)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_email_otp_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (emailOtpInput.length < 6) return@Button
                                    isLoading = true
                                    errorMessage = null
                                    coroutineScope.launch {
                                        try {
                                            apiClient?.verifySignupEmail(flowId!!, emailOtpInput.trim())
                                            isSignedIn = true
                                            isSyncEnabled = tokenStore.isCloudSyncEnabled()
                                            val txs = dao.getAllTransactionsSnapshot()
                                            localRecordCount = txs.size
                                            localPaymentCount = 0
                                            showUploadPreviewDialog = true
                                        } catch (e: Exception) {
                                            errorMessage = e.message ?: "Invalid email code"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                enabled = !isLoading && emailOtpInput.length == 6,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("signup_verify_email_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.account_verify_and_finish))
                                }
                            }
                        }
                    }
                }
            } else {
                // -------------------------------------------------------------
                // SIGNED IN: ACCOUNT & SYNC CONTROLS
                // -------------------------------------------------------------
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "User ID: ${tokenStore.getUserId() ?: ""}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!tokenStore.getPhoneNumber().isNullOrBlank()) {
                            Text(
                                text = "Phone: ${tokenStore.getPhoneNumber()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!tokenStore.getEmail().isNullOrBlank()) {
                            Text(
                                text = "Email: ${tokenStore.getEmail()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Device ID: ${tokenStore.getDeviceId()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // Cloud Sync Toggle Row (Feature flag: off by default)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = if (isSyncEnabled) Icons.Default.Sync else Icons.Default.Refresh,
                                contentDescription = null,
                                tint = if (isSyncEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.account_sync_status_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = stringResource(if (isSyncEnabled) R.string.account_sync_status_on else R.string.account_sync_status_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isSyncEnabled && lastSyncTimestamp > 0L) {
                            val formatted = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(lastSyncTimestamp))
                            Text(
                                text = stringResource(R.string.account_last_synced_fmt, formatted),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    UdhaarAnimatedSwitch(
                        checked = isSyncEnabled,
                        onCheckedChange = { checked ->
                            isSyncEnabled = checked
                            tokenStore.setCloudSyncEnabled(checked)
                            if (checked) {
                                SyncWorker.enqueuePeriodic(context)
                                SyncWorker.enqueueImmediate(context)
                            } else {
                                SyncWorker.cancelSync(context)
                            }
                        },
                        modifier = Modifier.testTag("cloud_sync_toggle")
                    )
                }

                if (isSyncEnabled) {
                    OutlinedButton(
                        onClick = {
                            SyncWorker.enqueueImmediate(context)
                            Toast.makeText(context, "Sync triggered", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .testTag("sync_now_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.account_sync_now), style = MaterialTheme.typography.labelLarge)
                    }
                }

                // -------------------------------------------------------------
                // CONFLICTS: "NEEDS YOUR ATTENTION" LIST
                // -------------------------------------------------------------
                if (conflictedTransactions.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = stringResource(R.string.account_conflicts_heading),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        conflictedTransactions.forEach { conflictTx ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "${conflictTx.partyName} (₹${conflictTx.principalAmountPaise / 100})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (conflictTx.disputeReason.isNotBlank()) {
                                        Text(
                                            text = conflictTx.disputeReason,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    dao.updateTransaction(conflictTx.copy(syncStatus = SyncStatus.LOCAL_ONLY, disputeReason = ""))
                                                    conflictedTransactions = dao.getAllTransactionsSnapshot().filter { it.syncStatus == SyncStatus.CONFLICT }
                                                }
                                            },
                                            modifier = Modifier.weight(1f).minTouchTarget()
                                        ) {
                                            Text(stringResource(R.string.account_conflict_keep_phone), style = MaterialTheme.typography.labelSmall)
                                        }
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    dao.updateTransaction(conflictTx.copy(syncStatus = SyncStatus.SYNCED, disputeReason = ""))
                                                    conflictedTransactions = dao.getAllTransactionsSnapshot().filter { it.syncStatus == SyncStatus.CONFLICT }
                                                }
                                            },
                                            modifier = Modifier.weight(1f).minTouchTarget()
                                        ) {
                                            Text(stringResource(R.string.account_conflict_accept_cloud), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // -------------------------------------------------------------
                // CONNECTED DEVICES LIST
                // -------------------------------------------------------------
                if (devicesList.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                text = stringResource(R.string.account_devices_heading),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        devicesList.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (device.current) "${device.userAgent} (${stringResource(R.string.account_this_device)})" else device.userAgent.ifBlank { device.deviceId },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (device.current) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = device.deviceId,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!device.current) {
                                    TextButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                apiClient?.revokeDevice(device.deviceId)
                                                devicesList = devicesList.filterNot { it.deviceId == device.deviceId }
                                            }
                                        },
                                        modifier = Modifier.minTouchTarget()
                                    ) {
                                        Text(stringResource(R.string.account_revoke_device), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                // Sign Out Button
                OutlinedButton(
                    onClick = { showSignOutConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .testTag("account_sign_out_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.account_sign_out_btn), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // DIALOGS: Upload Preview & Sign Out Confirmation
    // -------------------------------------------------------------------------
    if (showUploadPreviewDialog) {
        AlertDialog(
            onDismissRequest = { showUploadPreviewDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.account_upload_preview_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.account_upload_preview_body, localRecordCount, localPaymentCount),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSyncEnabled = true
                        tokenStore.setCloudSyncEnabled(true)
                        SyncWorker.enqueuePeriodic(context)
                        SyncWorker.enqueueImmediate(context)
                        showUploadPreviewDialog = false
                        Toast.makeText(context, "Cloud sync enabled", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("enable_cloud_sync_dialog_confirm")
                ) {
                    Text(stringResource(R.string.account_enable_sync_btn))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        isSyncEnabled = false
                        tokenStore.setCloudSyncEnabled(false)
                        showUploadPreviewDialog = false
                    },
                    modifier = Modifier.testTag("keep_offline_dialog_dismiss")
                ) {
                    Text(stringResource(R.string.account_keep_offline_btn))
                }
            }
        )
    }

    if (showSignOutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.account_sign_out_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.account_sign_out_confirm_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            apiClient?.signOut()
                            tokenStore.clearSession()
                            SyncWorker.cancelSync(context)
                            isSignedIn = false
                            isSyncEnabled = false
                            showSignOutConfirmDialog = false
                            Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("sign_out_dialog_confirm")
                ) {
                    Text(stringResource(R.string.account_sign_out_btn))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSignOutConfirmDialog = false },
                    modifier = Modifier.testTag("sign_out_dialog_dismiss")
                ) {
                    Text(stringResource(R.string.ui_str_cancel_1507c4))
                }
            }
        )
    }
}
