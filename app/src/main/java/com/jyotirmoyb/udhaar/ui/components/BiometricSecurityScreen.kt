package com.jyotirmoyb.udhaar.ui.components

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey

@Composable
fun BiometricSecurityScreen(
    expectedPin: String,
    onAuthenticated: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var pinInput by rememberSaveable { mutableStateOf("") }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showManualPinField by rememberSaveable { mutableStateOf(false) }

    val keyguardManager = remember(context) {
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    }
    val isDeviceSecure = remember(keyguardManager) {
        keyguardManager?.isDeviceSecure == true
    }

    // Android Phone PIN / Screen Lock launcher
    val confirmDeviceCredentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onAuthenticated()
        } else {
            statusMessage = "Phone screen lock verification was canceled or failed."
        }
    }

    val biometricAvailability = remember(context) {
        runCatching {
            val manager = BiometricManager.from(context)
            val strongOrCred = runCatching {
                manager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            }.getOrDefault(BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED)

            if (strongOrCred == BiometricManager.BIOMETRIC_SUCCESS) {
                strongOrCred
            } else {
                runCatching {
                    manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                }.getOrDefault(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)
            }
        }.getOrDefault(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)
    }

    val isBiometricReady = biometricAvailability == BiometricManager.BIOMETRIC_SUCCESS

    fun triggerDevicePinPrompt() {
        val intent = keyguardManager?.createConfirmDeviceCredentialIntent(
            "Unlock Udhaar Ledger",
            "Confirm your phone PIN, pattern, or password to view ledger records"
        )
        if (intent != null) {
            confirmDeviceCredentialLauncher.launch(intent)
        } else {
            statusMessage = "Device PIN not enrolled on this phone. Use App PIN below."
            showManualPinField = true
        }
    }

    fun triggerBiometricPrompt() {
        if (activity == null) {
            if (isDeviceSecure) {
                triggerDevicePinPrompt()
            } else {
                showManualPinField = true
            }
            return
        }

        if (!isBiometricReady) {
            if (isDeviceSecure) {
                triggerDevicePinPrompt()
            } else {
                statusMessage = "Biometric or Device PIN not enrolled on this phone. Use App PIN."
                showManualPinField = true
            }
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onAuthenticated()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    statusMessage = "Authentication canceled. Tap below to retry or use Phone PIN."
                } else {
                    statusMessage = errString.toString()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                statusMessage = "Biometric not recognized. Try again or use Phone PIN."
            }
        }

        runCatching {
            val prompt = BiometricPrompt(activity, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Udhaar")
                .setSubtitle("Confirm Fingerprint, Face, or Phone PIN to view records")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
            prompt.authenticate(promptInfo)
        }.onFailure {
            // Fallback to Device Credential directly via KeyguardManager
            if (isDeviceSecure) {
                triggerDevicePinPrompt()
            } else {
                showManualPinField = true
            }
        }
    }

    // Check Biometric / Phone Credential FIRST automatically as soon as screen is displayed
    LaunchedEffect(Unit) {
        triggerBiometricPrompt()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = PureWhite
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .testTag("biometric_security_screen"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .border(2.dp, PureBlack, RoundedCornerShape(4.dp))
                    .background(PureWhite, RoundedCornerShape(4.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(PureBlack, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Biometric Security Lock",
                        tint = PureWhite,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "UDHAAR IS LOCKED",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = PureBlack,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "FINGERPRINT, FACE OR PHONE PIN REQUIRED",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInkGrey,
                        textAlign = TextAlign.Center
                    )
                }

                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                Text(
                    text = "Verify your identity using your phone's Fingerprint, Face Unlock, or Device PIN to access your ledger.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryInkGrey,
                    textAlign = TextAlign.Center
                )

                if (statusMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PureBlack, RoundedCornerShape(4.dp))
                            .background(SubtleSurfaceGrey, RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = statusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = PureBlack,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Primary Action: Check Biometric (Fingerprint/Face) or Phone PIN
                Button(
                    onClick = { triggerBiometricPrompt() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("biometric_authenticate_button"),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureBlack,
                        contentColor = PureWhite
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "UNLOCK WITH BIOMETRIC",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Option for Phone PIN / Pattern (Calls KeyguardManager or Device Credential)
                OutlinedButton(
                    onClick = { triggerDevicePinPrompt() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("use_phone_pin_button"),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.5.dp, PureBlack),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                ) {
                    Icon(
                        imageVector = Icons.Default.Pin,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "USE PHONE PIN / PATTERN",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Clean expandable fallback App PIN field (Masked dots, NO password text shown)
                if (showManualPinField) {
                    HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "APP SECURITY PIN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PureBlack
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { input ->
                                    if (input.length <= 8 && input.all { it.isDigit() }) {
                                        pinInput = input
                                        statusMessage = null
                                        if (input == expectedPin) {
                                            onAuthenticated()
                                        }
                                    }
                                },
                                placeholder = { Text("Enter App PIN") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PureBlack,
                                    unfocusedBorderColor = BorderGrey,
                                    cursorColor = PureBlack,
                                    focusedContainerColor = PureWhite,
                                    unfocusedContainerColor = SubtleSurfaceGrey
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("security_pin_input")
                            )

                            OutlinedButton(
                                onClick = {
                                    if (pinInput == expectedPin || (pinInput.isEmpty() && !isBiometricReady && !isDeviceSecure)) {
                                        onAuthenticated()
                                    } else {
                                        statusMessage = "Incorrect PIN. Please try again."
                                    }
                                },
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("verify_pin_button"),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.5.dp, PureBlack),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = "Unlock",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "UNLOCK",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Or enter App PIN manually",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInkGrey,
                        modifier = Modifier
                            .clickable { showManualPinField = true }
                            .padding(vertical = 4.dp)
                    )
                }

                // If neither biometric nor phone lock is enrolled on device (rare/emulator case)
                if (!isBiometricReady && !isDeviceSecure) {
                    OutlinedButton(
                        onClick = onAuthenticated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("unlock_ledger_fallback_button"),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, BorderGrey),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryInkGrey)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "QUICK ENTER (NO DEVICE SCREEN LOCK CONFIGURED)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
