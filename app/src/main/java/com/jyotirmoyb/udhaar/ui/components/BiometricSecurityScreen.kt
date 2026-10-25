package com.jyotirmoyb.udhaar.ui.components

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

    val biometricAvailability = remember(context) {
        val manager = BiometricManager.from(context)
        manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
    }

    val isBiometricReady = biometricAvailability == BiometricManager.BIOMETRIC_SUCCESS

    fun triggerBiometricPrompt() {
        if (activity == null) {
            statusMessage = "Biometric prompt unavailable in current host context. Use Security PIN below."
            return
        }
        if (!isBiometricReady) {
            statusMessage = when (biometricAvailability) {
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                    "No Biometric or Device PIN enrolled on this device. Enter PIN (default: $expectedPin) or tap Unlock below."
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                    "No biometric sensor detected on this device. Use Security PIN or tap Unlock below."
                else ->
                    "Biometric sensor currently unavailable. Use Security PIN below."
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
                statusMessage = errString.toString()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                statusMessage = "Biometric not recognized. Try again or enter Security PIN."
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Udhaar Ledger")
            .setSubtitle("Authenticate with Biometric or Device Credential to view financial records")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        runCatching {
            prompt.authenticate(promptInfo)
        }.onFailure {
            statusMessage = "Use your Security PIN below to unlock."
        }
    }

    LaunchedEffect(isBiometricReady) {
        if (isBiometricReady) {
            triggerBiometricPrompt()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = PureWhite
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .testTag("biometric_security_screen"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .border(2.dp, PureBlack, RoundedCornerShape(4.dp))
                    .background(PureWhite, RoundedCornerShape(4.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(PureBlack, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Biometric Security Lock",
                        tint = PureWhite,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "UDHAAR PRIVACY GUARD",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PureBlack,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "BIOMETRIC / DEVICE PIN PROTECTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInkGrey,
                        textAlign = TextAlign.Center
                    )
                }

                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                Text(
                    text = "Verify your identity using Fingerprint, Face Unlock, Device Credential, or your Security PIN to access your ledger balances.",
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

                Button(
                    onClick = { triggerBiometricPrompt() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
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
                        text = "BIOMETRIC / DEVICE PIN UNLOCK",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "OR UNLOCK WITH APP PIN (DEFAULT: $expectedPin)",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInkGrey
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
                            placeholder = { Text("Enter PIN ($expectedPin)") },
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
                                if (pinInput == expectedPin || (pinInput.isEmpty() && !isBiometricReady)) {
                                    onAuthenticated()
                                } else {
                                    statusMessage = "Incorrect PIN. Default PIN is $expectedPin."
                                }
                            },
                            modifier = Modifier
                                .height(56.dp)
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

                if (!isBiometricReady) {
                    OutlinedButton(
                        onClick = onAuthenticated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
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
                            text = "QUICK ENTER (NO HARDWARE BIOMETRICS ENROLLED)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
