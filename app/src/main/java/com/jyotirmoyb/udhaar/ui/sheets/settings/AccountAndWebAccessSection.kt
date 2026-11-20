package com.jyotirmoyb.udhaar.ui.sheets.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.SyncStatus
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.cloud.GuestWebCloudRepository
import com.jyotirmoyb.udhaar.security.EncryptedTokenStore
import com.jyotirmoyb.udhaar.security.TokenStore
import com.jyotirmoyb.udhaar.sync.GuestActionPollingWorker
import com.jyotirmoyb.udhaar.sync.SyncWorker
import com.jyotirmoyb.udhaar.ui.common.SettingsSectionCard
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SparkAuthSession(
    val uid: String,
    val email: String,
    val displayName: String = "",
    val phoneNumber: String = "",
    val isEmailVerified: Boolean = false,
    val providerId: String = "password",
    val idToken: String = ""
)

interface SparkAuthController {
    fun currentSession(): SparkAuthSession?
    suspend fun signInWithGoogle(context: Context): SparkAuthSession
    suspend fun signUpWithEmailPassword(email: String, password: String, contactPhone: String = ""): SparkAuthSession
    suspend fun signInWithEmailPassword(email: String, password: String): SparkAuthSession
    suspend fun sendEmailVerification()
    suspend fun reloadSession(): SparkAuthSession?
    suspend fun sendPasswordResetEmail(email: String)
    suspend fun reauthenticateWithPassword(email: String, password: String)
    suspend fun reauthenticateWithGoogle(context: Context)
    suspend fun deleteCloudData(uid: String, knownLinkIds: List<String> = emptyList())
    suspend fun signOut()
}

class FirebaseSparkAuthController(
    private val authProvider: () -> FirebaseAuth? = {
        runCatching { FirebaseAuth.getInstance() }.getOrNull()
    },
    private val firestoreProvider: () -> FirebaseFirestore? = {
        runCatching { FirebaseFirestore.getInstance() }.getOrNull()
    }
) : SparkAuthController {

    private fun mapUser(user: FirebaseUser, tokenOverride: String = "", phoneOverride: String = ""): SparkAuthSession {
        val provider = user.providerData.firstOrNull { it.providerId != "firebase" }?.providerId ?: "password"
        val verified = user.isEmailVerified || provider == "google.com"
        return SparkAuthSession(
            uid = user.uid,
            email = user.email.orEmpty(),
            displayName = user.displayName.orEmpty(),
            phoneNumber = phoneOverride.ifBlank { user.phoneNumber.orEmpty() },
            isEmailVerified = verified,
            providerId = provider,
            idToken = tokenOverride.ifBlank { "firebase_session_${user.uid}" }
        )
    }

    override fun currentSession(): SparkAuthSession? {
        val user = authProvider()?.currentUser ?: return null
        if (user.isAnonymous) return null
        return mapUser(user)
    }

    override suspend fun signInWithGoogle(context: Context): SparkAuthSession {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetSignInWithGoogleOption.Builder(
            context.getString(R.string.default_web_client_id)
        ).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = credentialManager.getCredential(
            request = request,
            context = context
        )
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val isFirebaseConfigured = runCatching {
                FirebaseApp.getApps(context).isNotEmpty()
            }.getOrDefault(false)
            if (!isFirebaseConfigured) {
                throw IllegalStateException("Firebase is not configured on this device.")
            }
            val auth = authProvider() ?: throw IllegalStateException("FirebaseAuth unavailable.")
            val firebaseCred = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCred).await()
            val user = authResult.user ?: throw IllegalStateException("Google Sign-In returned no user.")
            val idToken = runCatching { user.getIdToken(false).await().token.orEmpty() }.getOrDefault("")
            return mapUser(user, tokenOverride = idToken)
        }
        throw IllegalStateException("Unsupported credential type returned from Credential Manager.")
    }

    override suspend fun signUpWithEmailPassword(
        email: String,
        password: String,
        contactPhone: String
    ): SparkAuthSession {
        val auth = authProvider() ?: throw IllegalStateException("FirebaseAuth unavailable.")
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
        val user = result.user ?: throw IllegalStateException("Account creation failed.")
        user.sendEmailVerification().await()
        val idToken = runCatching { user.getIdToken(false).await().token.orEmpty() }.getOrDefault("")
        return mapUser(user, tokenOverride = idToken, phoneOverride = contactPhone.trim())
    }

    override suspend fun signInWithEmailPassword(email: String, password: String): SparkAuthSession {
        val auth = authProvider() ?: throw IllegalStateException("FirebaseAuth unavailable.")
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val result = auth.signInWithEmailAndPassword(cleanEmail, password).await()
        val user = result.user ?: throw IllegalStateException("Sign-in failed.")
        val idToken = runCatching { user.getIdToken(false).await().token.orEmpty() }.getOrDefault("")
        return mapUser(user, tokenOverride = idToken)
    }

    override suspend fun sendEmailVerification() {
        val user = authProvider()?.currentUser ?: throw IllegalStateException("Not signed in.")
        user.sendEmailVerification().await()
    }

    override suspend fun reloadSession(): SparkAuthSession? {
        val user = authProvider()?.currentUser ?: return null
        user.reload().await()
        val refreshed = authProvider()?.currentUser ?: return null
        val idToken = runCatching { refreshed.getIdToken(true).await().token.orEmpty() }.getOrDefault("")
        return mapUser(refreshed, tokenOverride = idToken)
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        val auth = authProvider() ?: throw IllegalStateException("FirebaseAuth unavailable.")
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        auth.sendPasswordResetEmail(cleanEmail).await()
    }

    override suspend fun reauthenticateWithPassword(email: String, password: String) {
        val user = authProvider()?.currentUser ?: throw IllegalStateException("Not signed in.")
        val cred = EmailAuthProvider.getCredential(email.trim(), password)
        user.reauthenticate(cred).await()
    }

    override suspend fun reauthenticateWithGoogle(context: Context) {
        val user = authProvider()?.currentUser ?: throw IllegalStateException("Not signed in.")
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetSignInWithGoogleOption.Builder(
            context.getString(R.string.default_web_client_id)
        ).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = credentialManager.getCredential(request = request, context = context)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            user.reauthenticate(firebaseCred).await()
        } else {
            throw IllegalStateException("Google re-authentication failed.")
        }
    }

    override suspend fun deleteCloudData(uid: String, knownLinkIds: List<String>) {
        if (uid.isBlank()) return
        val db = firestoreProvider() ?: return
        val userDoc = db.collection("users").document(uid)

        // Delete mutable subcollections allowed by Spark firestore.rules
        val subcollections = listOf("records", "people", "plans", "attachments")
        for (sub in subcollections) {
            runCatching {
                val snap = userDoc.collection(sub).get().await()
                for (doc in snap.documents) {
                    runCatching { doc.reference.delete().await() }
                }
            }
        }

        // Delete syncSignal and root user document
        runCatching { userDoc.collection("meta").document("syncSignal").delete().await() }
        runCatching { userDoc.delete().await() }

        // Delete known capability guestLinks by direct document ID (since guestLinks list is forbidden)
        for (linkId in knownLinkIds.distinct()) {
            if (linkId.length >= 12) {
                runCatching { db.collection("guestLinks").document(linkId).delete().await() }
            }
        }
    }

    override suspend fun signOut() {
        runCatching { authProvider()?.signOut() }
    }
}

private enum class SparkAuthUiMode {
    SIGNUP,
    SIGNIN,
    PASSWORD_RESET
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountAndWebAccessSection(
    dao: UdhaarDao? = null,
    tokenStoreOverride: TokenStore? = null,
    authControllerOverride: SparkAuthController? = null,
    onDeleteMyCloudDataCallback: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val tokenStore = remember(tokenStoreOverride) {
        tokenStoreOverride ?: EncryptedTokenStore(context)
    }
    val authController = remember(authControllerOverride) {
        authControllerOverride ?: FirebaseSparkAuthController()
    }

    var session by remember { mutableStateOf(authController.currentSession()) }
    var isSignedIn by remember { mutableStateOf(session != null || tokenStore.isSignedIn()) }
    var isEmailVerified by remember { mutableStateOf(session?.isEmailVerified ?: false) }
    var isCloudSyncEnabled by remember {
        mutableStateOf((session?.isEmailVerified == true) && tokenStore.isCloudSyncEnabled())
    }
    var userEmail by remember {
        mutableStateOf(session?.email?.ifBlank { tokenStore.getEmail().orEmpty() } ?: tokenStore.getEmail().orEmpty())
    }
    var userPhone by remember {
        mutableStateOf(session?.phoneNumber?.ifBlank { tokenStore.getPhoneNumber().orEmpty() } ?: tokenStore.getPhoneNumber().orEmpty())
    }
    var lastSyncTime by remember { mutableLongStateOf(tokenStore.getLastSyncTimestamp()) }

    var authMode by rememberSaveable { mutableStateOf(SparkAuthUiMode.SIGNIN) }
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var contactPhoneInput by rememberSaveable { mutableStateOf("") }

    var showReauthPanel by rememberSaveable { mutableStateOf(false) }
    var reauthPasswordInput by rememberSaveable { mutableStateOf("") }
    var isReauthenticated by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteCloudData by rememberSaveable { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Enforce Spark invariant: Cloud sync stays strictly OFF until email is verified
    LaunchedEffect(session, isEmailVerified) {
        if (!isEmailVerified && tokenStore.isCloudSyncEnabled()) {
            tokenStore.setCloudSyncEnabled(false)
            isCloudSyncEnabled = false
            SyncWorker.cancelSync(context)
        }
    }

    fun applyAuthenticatedSession(authSession: SparkAuthSession, statusNote: String? = null) {
        session = authSession
        isSignedIn = true
        isEmailVerified = authSession.isEmailVerified
        userEmail = authSession.email
        if (authSession.phoneNumber.isNotBlank()) {
            userPhone = authSession.phoneNumber
        }
        tokenStore.saveSession(
            authToken = authSession.idToken.ifBlank { "firebase_${authSession.uid}" },
            userId = authSession.uid,
            phoneNumber = userPhone.ifBlank { null },
            email = authSession.email
        )
        if (!authSession.isEmailVerified) {
            tokenStore.setCloudSyncEnabled(false)
            isCloudSyncEnabled = false
            SyncWorker.cancelSync(context)
        } else {
            isCloudSyncEnabled = tokenStore.isCloudSyncEnabled()
        }
        passwordInput = ""
        errorMessage = null
        statusMessage = statusNote
    }

    SettingsSectionCard(
        sectionNumber = "06",
        title = stringResource(R.string.account_and_web_access_title),
        subtitle = stringResource(R.string.account_and_web_access_subtitle),
        testTag = "settings_account_web_section"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isSignedIn) {
                // =============================================================
                // SIGNED-OUT STATE: Google (Credential Manager) + Email/Password
                // =============================================================
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when (authMode) {
                            SparkAuthUiMode.SIGNUP -> stringResource(R.string.account_sign_up_heading)
                            SparkAuthUiMode.SIGNIN -> stringResource(R.string.account_sign_in_heading)
                            SparkAuthUiMode.PASSWORD_RESET -> stringResource(R.string.account_reset_password_heading)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    if (authMode != SparkAuthUiMode.PASSWORD_RESET) {
                        TextButton(
                            onClick = {
                                authMode = if (authMode == SparkAuthUiMode.SIGNUP) {
                                    SparkAuthUiMode.SIGNIN
                                } else {
                                    SparkAuthUiMode.SIGNUP
                                }
                                errorMessage = null
                                statusMessage = null
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_toggle_auth_mode")
                        ) {
                            Text(
                                text = if (authMode == SparkAuthUiMode.SIGNUP) {
                                    stringResource(R.string.account_switch_to_signin)
                                } else {
                                    stringResource(R.string.account_switch_to_signup)
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                // Google Sign-In via Jetpack Credential Manager
                OutlinedButton(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        statusMessage = null
                        scope.launch {
                            try {
                                val googleSession = authController.signInWithGoogle(context)
                                applyAuthenticatedSession(
                                    googleSession,
                                    statusNote = "Signed in with Google (${googleSession.email})"
                                )
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Google Sign-In failed"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_google_credential_signin"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.account_continue_with_google))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                when (authMode) {
                    SparkAuthUiMode.PASSWORD_RESET -> {
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text(stringResource(R.string.account_email_label)) },
                            placeholder = { Text(stringResource(R.string.account_email_placeholder)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_email_input")
                        )

                        Button(
                            onClick = {
                                val cleanEmail = emailInput.trim()
                                if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                                    errorMessage = "Please enter a valid email address."
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null
                                statusMessage = null
                                scope.launch {
                                    try {
                                        authController.sendPasswordResetEmail(cleanEmail)
                                        statusMessage = "Password reset link sent to $cleanEmail"
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Failed to send password reset email"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading && emailInput.contains("@"),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("btn_send_password_reset"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.account_send_password_reset_btn))
                        }

                        TextButton(
                            onClick = {
                                authMode = SparkAuthUiMode.SIGNIN
                                errorMessage = null
                            },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .heightIn(min = 48.dp)
                                .testTag("btn_back_to_signin")
                        ) {
                            Text(stringResource(R.string.account_back_to_signin_btn))
                        }
                    }

                    SparkAuthUiMode.SIGNUP,
                    SparkAuthUiMode.SIGNIN -> {
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text(stringResource(R.string.account_email_label)) },
                            placeholder = { Text(stringResource(R.string.account_email_placeholder)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("account_email_input")
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = {
                                Text(
                                    if (authMode == SparkAuthUiMode.SIGNUP) {
                                        stringResource(R.string.account_password_signup_label)
                                    } else {
                                        stringResource(R.string.account_password_signin_label)
                                    }
                                )
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("account_password_input")
                        )

                        if (authMode == SparkAuthUiMode.SIGNUP) {
                            OutlinedTextField(
                                value = contactPhoneInput,
                                onValueChange = { contactPhoneInput = it },
                                label = { Text(stringResource(R.string.account_contact_phone_optional_label)) },
                                placeholder = { Text(stringResource(R.string.account_contact_phone_placeholder)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("account_phone_input")
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        authMode = SparkAuthUiMode.PASSWORD_RESET
                                        errorMessage = null
                                        statusMessage = null
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_forgot_password")
                                ) {
                                    Text(stringResource(R.string.account_forgot_password_btn), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val cleanEmail = emailInput.trim()
                                if (!cleanEmail.contains("@")) {
                                    errorMessage = "Please enter a valid email address."
                                    return@Button
                                }
                                if (authMode == SparkAuthUiMode.SIGNUP && passwordInput.length < 10) {
                                    errorMessage = "Password must be at least 10 characters."
                                    return@Button
                                }
                                if (passwordInput.isEmpty()) {
                                    errorMessage = "Please enter your password."
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null
                                statusMessage = null
                                scope.launch {
                                    try {
                                        if (authMode == SparkAuthUiMode.SIGNUP) {
                                            val newSession = authController.signUpWithEmailPassword(
                                                email = cleanEmail,
                                                password = passwordInput,
                                                contactPhone = contactPhoneInput
                                            )
                                            applyAuthenticatedSession(
                                                newSession,
                                                statusNote = "Verification email sent to ${newSession.email}. Verify your email to enable cloud sync."
                                            )
                                        } else {
                                            val existingSession = authController.signInWithEmailPassword(
                                                email = cleanEmail,
                                                password = passwordInput
                                            )
                                            applyAuthenticatedSession(
                                                existingSession,
                                                statusNote = if (existingSession.isEmailVerified) {
                                                    "Signed in as ${existingSession.email}"
                                                } else {
                                                    "Signed in. Please verify your email before enabling cloud sync."
                                                }
                                            )
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Authentication failed"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading && emailInput.contains("@") && passwordInput.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("btn_submit_email_auth"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                if (authMode == SparkAuthUiMode.SIGNUP) {
                                    stringResource(R.string.account_create_and_verify_btn)
                                } else {
                                    stringResource(R.string.account_signin_email_btn)
                                }
                            )
                        }
                    }
                }
            } else {
                // =============================================================
                // SIGNED-IN STATE: Verification Gate, Cloud Sync, Re-Auth, Delete Cloud Data
                // =============================================================
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

                // Email Verification Gate Banner when unverified
                if (!isEmailVerified) {
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
                                    onClick = {
                                        isLoading = true
                                        errorMessage = null
                                        scope.launch {
                                            try {
                                                authController.sendEmailVerification()
                                                statusMessage = "Verification email resent to $userEmail"
                                            } catch (e: Exception) {
                                                errorMessage = e.message ?: "Could not resend verification email"
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    },
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
                                    onClick = {
                                        isLoading = true
                                        errorMessage = null
                                        scope.launch {
                                            try {
                                                val refreshed = authController.reloadSession()
                                                if (refreshed != null) {
                                                    applyAuthenticatedSession(
                                                        refreshed,
                                                        statusNote = if (refreshed.isEmailVerified) {
                                                            "Email verified! You can now enable Cloud Sync."
                                                        } else {
                                                            "Email is still not verified yet."
                                                        }
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = e.message ?: "Failed to refresh verification status"
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    },
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

                // Cloud Sync Toggle (Strictly disabled until isEmailVerified == true)
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
                            onCheckedChange = { enabled ->
                                if (!isEmailVerified) {
                                    tokenStore.setCloudSyncEnabled(false)
                                    isCloudSyncEnabled = false
                                    errorMessage = "Cloud sync stays off until your email is verified."
                                    return@Switch
                                }
                                isCloudSyncEnabled = enabled
                                tokenStore.setCloudSyncEnabled(enabled)
                                if (enabled) {
                                    SyncWorker.enqueuePeriodic(context)
                                    SyncWorker.enqueueImmediate(context)
                                    GuestActionPollingWorker.enqueuePeriodic(context)
                                    GuestActionPollingWorker.enqueueImmediate(context)
                                } else {
                                    SyncWorker.cancelSync(context)
                                    GuestActionPollingWorker.cancel(context)
                                }
                            },
                            modifier = Modifier.testTag("switch_cloud_sync")
                        )
                    }
                }

                if (isCloudSyncEnabled && isEmailVerified) {
                    OutlinedButton(
                        onClick = {
                            SyncWorker.enqueueImmediate(context)
                            GuestActionPollingWorker.enqueueImmediate(context)
                            lastSyncTime = System.currentTimeMillis()
                            tokenStore.setLastSyncTimestamp(lastSyncTime)
                        },
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

                // Re-authentication section for sensitive account operations
                if (!showReauthPanel) {
                    OutlinedButton(
                        onClick = {
                            showReauthPanel = true
                            errorMessage = null
                            statusMessage = null
                        },
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
                                onValueChange = { reauthPasswordInput = it },
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
                                    onClick = {
                                        isLoading = true
                                        errorMessage = null
                                        scope.launch {
                                            try {
                                                authController.reauthenticateWithPassword(userEmail, reauthPasswordInput)
                                                isReauthenticated = true
                                                showReauthPanel = false
                                                reauthPasswordInput = ""
                                                statusMessage = "Re-authentication successful."
                                            } catch (e: Exception) {
                                                errorMessage = e.message ?: "Re-authentication failed"
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    },
                                    enabled = !isLoading && reauthPasswordInput.isNotEmpty(),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_confirm_reauth")
                                ) {
                                    Text(stringResource(R.string.account_verify_password_btn))
                                }
                                OutlinedButton(
                                    onClick = {
                                        isLoading = true
                                        errorMessage = null
                                        scope.launch {
                                            try {
                                                authController.reauthenticateWithGoogle(context)
                                                isReauthenticated = true
                                                showReauthPanel = false
                                                statusMessage = "Re-authenticated with Google."
                                            } catch (e: Exception) {
                                                errorMessage = e.message ?: "Google re-authentication failed"
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    },
                                    enabled = !isLoading,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_reauth_google")
                                ) {
                                    Text(stringResource(R.string.account_reauth_google_btn))
                                }
                                TextButton(
                                    onClick = {
                                        showReauthPanel = false
                                        reauthPasswordInput = ""
                                    },
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

                // Delete My Cloud Data (Spark Mode native Firestore cleanup)
                Text(
                    text = stringResource(R.string.account_delete_cloud_data_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        if (!confirmDeleteCloudData) {
                            confirmDeleteCloudData = true
                            errorMessage = null
                        } else {
                            confirmDeleteCloudData = false
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val uid = session?.uid?.ifBlank { tokenStore.getUserId().orEmpty() }
                                        ?: tokenStore.getUserId().orEmpty()
                                    val allTxs = dao?.getAllTransactionsSnapshot().orEmpty()
                                    val knownLinkIds = allTxs.mapNotNull { tx ->
                                        val token = tx.shareToken.trim()
                                        when {
                                            token.length == 64 && token.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } ->
                                                token.lowercase(Locale.ROOT)
                                            token.length == 32 && token.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } ->
                                                GuestWebCloudRepository.sha256Hex(token.lowercase(Locale.ROOT))
                                            else -> null
                                        }
                                    }
                                    SyncWorker.cancelSync(context)
                                    GuestActionPollingWorker.cancel(context)
                                    tokenStore.setCloudSyncEnabled(false)
                                    isCloudSyncEnabled = false
                                    authController.deleteCloudData(uid, knownLinkIds)
                                    for (tx in allTxs) {
                                        if (tx.syncStatus != SyncStatus.LOCAL_ONLY || tx.webLinkStatus == "ACTIVE") {
                                            dao?.updateTransaction(
                                                tx.copy(
                                                    syncStatus = SyncStatus.LOCAL_ONLY,
                                                    webLinkStatus = if (tx.webLinkStatus == "ACTIVE") "REVOKED" else tx.webLinkStatus
                                                )
                                            )
                                        }
                                    }
                                    onDeleteMyCloudDataCallback()
                                    statusMessage = context.getString(R.string.account_delete_cloud_data_done)
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Failed to delete cloud data"
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
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
                        imageVector = Icons.Default.DeleteOutline,
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Sign Out Button (100% Spark Firebase Auth + local state cleanup, zero /api/v1 calls)
                OutlinedButton(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            try {
                                SyncWorker.cancelSync(context)
                                GuestActionPollingWorker.cancel(context)
                                authController.signOut()
                                tokenStore.clearSession()
                                val allTxs = dao?.getAllTransactionsSnapshot().orEmpty()
                                for (tx in allTxs) {
                                    if (tx.syncStatus != SyncStatus.LOCAL_ONLY) {
                                        dao?.updateTransaction(tx.copy(syncStatus = SyncStatus.LOCAL_ONLY))
                                    }
                                }
                                session = null
                                isSignedIn = false
                                isEmailVerified = false
                                isCloudSyncEnabled = false
                                isReauthenticated = false
                                confirmDeleteCloudData = false
                                userEmail = ""
                                userPhone = ""
                                statusMessage = null
                                errorMessage = null
                            } finally {
                                isLoading = false
                            }
                        }
                    },
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

            if (!statusMessage.isNullOrBlank()) {
                Text(
                    text = statusMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("account_status_message")
                )
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("account_error_message")
                )
            }
        }
    }
}
