package com.jyotirmoyb.udhaar.ui.sheets.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.SyncStatus
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.security.EncryptedTokenStore
import com.jyotirmoyb.udhaar.security.TokenStore
import com.jyotirmoyb.udhaar.sync.GuestActionPollingWorker
import com.jyotirmoyb.udhaar.sync.SyncWorker
import com.jyotirmoyb.udhaar.ui.common.SettingsSectionCard
import kotlinx.coroutines.launch

@Composable
fun AccountAndWebAccessSection(
    dao: UdhaarDao? = null,
    tokenStoreOverride: TokenStore? = null,
    authControllerOverride: SparkAuthController? = null,
    onDeleteMyCloudDataCallback: () -> Unit = {},
    onAccountAndDataDeletedCallback: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val tokenStore = remember(tokenStoreOverride) { tokenStoreOverride ?: EncryptedTokenStore(context) }
    val authController = remember(authControllerOverride) { authControllerOverride ?: FirebaseSparkAuthController() }

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
        mutableStateOf(
            session?.phoneNumber?.ifBlank { tokenStore.getPhoneNumber().orEmpty() } ?: tokenStore.getPhoneNumber().orEmpty()
        )
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

    var showDeleteAccountDialog by rememberSaveable { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var accountDeleteProgress by remember { mutableStateOf("") }
    var accountDeleteError by remember { mutableStateOf<String?>(null) }
    var isAccountDeleteCompleted by rememberSaveable { mutableStateOf(false) }
    var isDeleteAccountReauthNeeded by rememberSaveable { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

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

    fun runAuthAction(defaultError: String, block: suspend () -> Unit) {
        isLoading = true
        errorMessage = null
        statusMessage = null
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                errorMessage = e.message ?: defaultError
            } finally {
                isLoading = false
            }
        }
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
                AccountSignedOutPanel(
                    authMode = authMode,
                    emailInput = emailInput,
                    passwordInput = passwordInput,
                    contactPhoneInput = contactPhoneInput,
                    isLoading = isLoading,
                    onAuthModeChange = { nextMode ->
                        authMode = nextMode
                        errorMessage = null
                        statusMessage = null
                    },
                    onEmailChange = { emailInput = it },
                    onPasswordChange = { passwordInput = it },
                    onContactPhoneChange = { contactPhoneInput = it },
                    onGoogleSignIn = {
                        runAuthAction("Google Sign-In failed") {
                            val googleSession = authController.signInWithGoogle(context)
                            applyAuthenticatedSession(
                                googleSession,
                                statusNote = "Signed in with Google (${googleSession.email})"
                            )
                        }
                    },
                    onSendPasswordReset = {
                        val cleanEmail = emailInput.trim()
                        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                            errorMessage = "Please enter a valid email address."
                            return@AccountSignedOutPanel
                        }
                        runAuthAction("Failed to send password reset email") {
                            authController.sendPasswordResetEmail(cleanEmail)
                            statusMessage = "Password reset link sent to $cleanEmail"
                        }
                    },
                    onSubmitEmailAuth = {
                        val cleanEmail = emailInput.trim()
                        if (!cleanEmail.contains("@")) {
                            errorMessage = "Please enter a valid email address."
                            return@AccountSignedOutPanel
                        }
                        if (authMode == SparkAuthUiMode.SIGNUP && passwordInput.length < 10) {
                            errorMessage = "Password must be at least 10 characters."
                            return@AccountSignedOutPanel
                        }
                        if (passwordInput.isEmpty()) {
                            errorMessage = "Please enter your password."
                            return@AccountSignedOutPanel
                        }
                        runAuthAction("Authentication failed") {
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
                        }
                    }
                )
            } else {
                AccountSignedInPanel(
                    userEmail = userEmail,
                    userPhone = userPhone,
                    isEmailVerified = isEmailVerified,
                    isCloudSyncEnabled = isCloudSyncEnabled,
                    lastSyncTime = lastSyncTime,
                    showReauthPanel = showReauthPanel,
                    reauthPasswordInput = reauthPasswordInput,
                    isReauthenticated = isReauthenticated,
                    confirmDeleteCloudData = confirmDeleteCloudData,
                    isLoading = isLoading,
                    isDeletingAccount = isDeletingAccount,
                    onResendVerification = {
                        runAuthAction("Could not resend verification email") {
                            authController.sendEmailVerification()
                            statusMessage = "Verification email resent to $userEmail"
                        }
                    },
                    onRefreshVerification = {
                        runAuthAction("Failed to refresh verification status") {
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
                        }
                    },
                    onToggleCloudSync = { enabled ->
                        if (!isEmailVerified) {
                            tokenStore.setCloudSyncEnabled(false)
                            isCloudSyncEnabled = false
                            errorMessage = "Cloud sync stays off until your email is verified."
                            return@AccountSignedInPanel
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
                    onSyncNow = {
                        SyncWorker.enqueueImmediate(context)
                        GuestActionPollingWorker.enqueueImmediate(context)
                        lastSyncTime = System.currentTimeMillis()
                        tokenStore.setLastSyncTimestamp(lastSyncTime)
                    },
                    onOpenReauthPanel = {
                        showReauthPanel = true
                        errorMessage = null
                        statusMessage = null
                    },
                    onReauthPasswordChange = { reauthPasswordInput = it },
                    onConfirmPasswordReauth = {
                        runAuthAction("Re-authentication failed") {
                            authController.reauthenticateWithPassword(userEmail, reauthPasswordInput)
                            isReauthenticated = true
                            showReauthPanel = false
                            reauthPasswordInput = ""
                            statusMessage = "Re-authentication successful."
                        }
                    },
                    onReauthWithGoogle = {
                        runAuthAction("Google re-authentication failed") {
                            authController.reauthenticateWithGoogle(context)
                            isReauthenticated = true
                            showReauthPanel = false
                            statusMessage = "Re-authenticated with Google."
                        }
                    },
                    onCancelReauth = {
                        showReauthPanel = false
                        reauthPasswordInput = ""
                    },
                    onDeleteCloudDataClick = {
                        if (!confirmDeleteCloudData) {
                            confirmDeleteCloudData = true
                            errorMessage = null
                        } else {
                            confirmDeleteCloudData = false
                            runAuthAction("Failed to delete cloud data") {
                                val uid = session?.uid?.ifBlank { tokenStore.getUserId().orEmpty() }
                                    ?: tokenStore.getUserId().orEmpty()
                                val allTxs = dao?.getAllTransactionsSnapshot().orEmpty()
                                val knownLinkIds = extractKnownGuestLinkIds(allTxs)
                                SyncWorker.cancelSync(context)
                                GuestActionPollingWorker.cancel(context)
                                tokenStore.setCloudSyncEnabled(false)
                                tokenStore.setLastSyncTimestamp(0L)
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
                            }
                        }
                    },
                    onOpenDeleteAccountDialog = {
                        accountDeleteError = null
                        showDeleteAccountDialog = true
                    },
                    onSignOut = {
                        runAuthAction("Sign-out failed") {
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
                        }
                    }
                )
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

    DeleteAccountAndDataDialog(
        isOpen = showDeleteAccountDialog,
        onDismiss = {
            if (!isDeletingAccount) {
                showDeleteAccountDialog = false
                isDeleteAccountReauthNeeded = false
                accountDeleteError = null
            }
        },
        onConfirmDelete = { deleteDriveBackups ->
            scope.launch {
                isDeletingAccount = true
                accountDeleteError = null
                try {
                    val uid = session?.uid?.ifBlank { tokenStore.getUserId().orEmpty() }
                        ?: tokenStore.getUserId().orEmpty()
                    val allTxs = dao?.getAllTransactionsSnapshot().orEmpty()
                    authController.deleteAccountAndAllData(
                        context = context,
                        uid = uid,
                        knownLinkIds = extractKnownGuestLinkIds(allTxs),
                        deleteDriveBackups = deleteDriveBackups,
                        onProgress = { step -> accountDeleteProgress = step }
                    )
                    tokenStore.clearSession()
                    session = null
                    isSignedIn = false
                    isEmailVerified = false
                    isCloudSyncEnabled = false
                    isAccountDeleteCompleted = true
                    onAccountAndDataDeletedCallback()
                } catch (e: Exception) {
                    if (e is FirebaseAuthRecentLoginRequiredException ||
                        e.cause is FirebaseAuthRecentLoginRequiredException
                    ) {
                        isDeleteAccountReauthNeeded = true
                    } else {
                        accountDeleteError = e.message ?: "Failed to delete account and data"
                    }
                } finally {
                    isDeletingAccount = false
                }
            }
        },
        isDeleting = isDeletingAccount,
        progressMessage = accountDeleteProgress,
        errorMessage = accountDeleteError,
        isReauthRequired = isDeleteAccountReauthNeeded,
        onReauthenticate = {
            isDeleteAccountReauthNeeded = false
            showReauthPanel = true
        },
        isCompleted = isAccountDeleteCompleted,
        onCompletedDismiss = {
            isAccountDeleteCompleted = false
            showDeleteAccountDialog = false
        }
    )
}
