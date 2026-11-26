package com.jyotirmoyb.udhaar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.BuildConfig
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.security.EncryptedTokenStore
import com.jyotirmoyb.udhaar.security.SecureScreenEffect
import com.jyotirmoyb.udhaar.ui.sheets.settings.AccountSignedOutPanel
import com.jyotirmoyb.udhaar.ui.sheets.settings.FirebaseSparkAuthController
import com.jyotirmoyb.udhaar.ui.sheets.settings.SparkAuthController
import com.jyotirmoyb.udhaar.ui.sheets.settings.SparkAuthSession
import com.jyotirmoyb.udhaar.ui.sheets.settings.SparkAuthUiMode
import kotlinx.coroutines.launch

/**
 * Initial Cloud Verification screen shown before accessing ledger screens.
 * Controlled by [BuildConfig.REQUIRE_CLOUD_VERIFICATION] (default false):
 * when [allowLocalSkip] is true, users may continue in local-only encrypted mode.
 */
@Composable
fun CloudVerificationGateScreen(
    isFirebaseConfigured: Boolean,
    isSignedInUnverified: Boolean,
    pendingEmail: String,
    onVerifiedSession: (uid: String, email: String, displayName: String) -> Unit,
    onSignedOut: () -> Unit,
    onSkipLocalOnly: () -> Unit = {},
    allowLocalSkip: Boolean = !BuildConfig.REQUIRE_CLOUD_VERIFICATION,
    authControllerOverride: SparkAuthController? = null
) {
    SecureScreenEffect(enabled = true)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authController = remember(authControllerOverride) { authControllerOverride ?: FirebaseSparkAuthController() }
    val tokenStore = remember(context) { EncryptedTokenStore(context) }

    var authMode by rememberSaveable { mutableStateOf(SparkAuthUiMode.SIGNIN) }
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var contactPhoneInput by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun handleSession(session: SparkAuthSession, note: String? = null) {
        tokenStore.saveSession(
            authToken = session.idToken.ifBlank { "firebase_${session.uid}" },
            userId = session.uid,
            phoneNumber = session.phoneNumber.takeIf { it.isNotBlank() },
            email = session.email
        )
        tokenStore.setCloudSyncEnabled(session.isEmailVerified)
        passwordInput = ""
        errorMessage = null
        statusMessage = note
        onVerifiedSession(session.uid, session.email, session.displayName)
    }

    fun runAction(fallbackError: String, block: suspend () -> Unit) {
        isLoading = true
        errorMessage = null
        statusMessage = null
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                errorMessage = e.message ?: fallbackError
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .testTag("cloud_verification_gate_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(
                        if (allowLocalSkip) R.string.cloud_gate_optional_title else R.string.cloud_gate_title
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(
                        if (allowLocalSkip) R.string.cloud_gate_optional_subtitle else R.string.cloud_gate_subtitle
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            if (allowLocalSkip) {
                                R.string.cloud_gate_optional_consent_note
                            } else {
                                R.string.cloud_gate_verify_consent_note
                            }
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }

                if (!isFirebaseConfigured) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.cloud_gate_firebase_unconfigured_msg),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else if (isSignedInUnverified) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.cloud_gate_signed_in_unverified_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(
                                    R.string.cloud_gate_signed_in_unverified_body,
                                    pendingEmail.ifBlank { context.getString(R.string.account_email_placeholder) }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    runAction(context.getString(R.string.cloud_gate_err_refresh_status)) {
                                        val refreshed = authController.reloadSession()
                                        if (refreshed != null && refreshed.isEmailVerified) {
                                            handleSession(refreshed)
                                        } else {
                                            errorMessage = context.getString(R.string.account_unverified_banner_body)
                                        }
                                    }
                                },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("cloud_gate_check_status_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(stringResource(R.string.cloud_gate_check_status_btn), fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    runAction(context.getString(R.string.cloud_gate_err_resend_email)) {
                                        authController.sendEmailVerification()
                                        statusMessage = context.getString(R.string.account_resend_verification_btn)
                                    }
                                },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("cloud_gate_resend_email_btn")
                            ) {
                                Text(stringResource(R.string.cloud_gate_resend_email_btn))
                            }
                            OutlinedButton(
                                onClick = {
                                    runAction(context.getString(R.string.cloud_gate_err_sign_out)) {
                                        authController.signOut()
                                        tokenStore.clearSession()
                                        onSignedOut()
                                    }
                                },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("cloud_gate_switch_account_btn")
                            ) {
                                Text(stringResource(R.string.cloud_gate_sign_out_switch_btn))
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            AccountSignedOutPanel(
                                authMode = authMode,
                                emailInput = emailInput,
                                passwordInput = passwordInput,
                                contactPhoneInput = contactPhoneInput,
                                isLoading = isLoading,
                                onAuthModeChange = { next ->
                                    authMode = next
                                    errorMessage = null
                                    statusMessage = null
                                },
                                onEmailChange = { emailInput = it },
                                onPasswordChange = { passwordInput = it },
                                onContactPhoneChange = { contactPhoneInput = it },
                                onGoogleSignIn = {
                                    runAction(context.getString(R.string.cloud_gate_err_google_signin)) {
                                        val session = authController.signInWithGoogle(context)
                                        handleSession(session)
                                    }
                                },
                                onSendPasswordReset = {
                                    val clean = emailInput.trim()
                                    if (!clean.contains("@")) {
                                        errorMessage = context.getString(R.string.cloud_gate_err_invalid_email)
                                        return@AccountSignedOutPanel
                                    }
                                    runAction(context.getString(R.string.cloud_gate_err_password_reset)) {
                                        authController.sendPasswordResetEmail(clean)
                                        statusMessage = context.getString(R.string.cloud_gate_status_reset_sent_fmt, clean)
                                    }
                                },
                                onSubmitEmailAuth = {
                                    val clean = emailInput.trim()
                                    if (!clean.contains("@")) {
                                        errorMessage = context.getString(R.string.cloud_gate_err_invalid_email)
                                        return@AccountSignedOutPanel
                                    }
                                    if (authMode == SparkAuthUiMode.SIGNUP && passwordInput.length < 10) {
                                        errorMessage = context.getString(R.string.cloud_gate_err_password_length)
                                        return@AccountSignedOutPanel
                                    }
                                    if (passwordInput.isEmpty()) {
                                        errorMessage = context.getString(R.string.cloud_gate_err_password_empty)
                                        return@AccountSignedOutPanel
                                    }
                                    runAction(context.getString(R.string.cloud_gate_err_auth_failed)) {
                                        val session = if (authMode == SparkAuthUiMode.SIGNUP) {
                                            authController.signUpWithEmailPassword(clean, passwordInput, contactPhoneInput)
                                        } else {
                                            authController.signInWithEmailPassword(clean, passwordInput)
                                        }
                                        handleSession(
                                            session,
                                            note = if (session.isEmailVerified) null
                                            else context.getString(R.string.account_unverified_banner_body)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!statusMessage.isNullOrBlank()) {
                    Text(
                        text = statusMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (allowLocalSkip) {
                    OutlinedButton(
                        onClick = onSkipLocalOnly,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("cloud_gate_skip_local_btn")
                    ) {
                        Text(
                            text = stringResource(R.string.cloud_gate_skip_local_btn),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.cloud_gate_footer_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
