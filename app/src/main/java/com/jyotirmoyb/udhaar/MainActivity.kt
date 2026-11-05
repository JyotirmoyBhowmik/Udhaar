package com.jyotirmoyb.udhaar

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.jyotirmoyb.udhaar.backup.DriveServiceHelper
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.notification.ChatNotificationHelper
import com.jyotirmoyb.udhaar.notification.ReminderReceiver
import com.jyotirmoyb.udhaar.ui.UdhaarViewModel
import com.jyotirmoyb.udhaar.ui.theme.UdhaarTheme
import com.jyotirmoyb.udhaar.util.DeviceContactHelper
import com.jyotirmoyb.udhaar.util.TelemetryManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
open class MainActivity : FragmentActivity() {

    @Inject
    lateinit var injectedRepository: UdhaarRepository

    private var pendingReminderTransactionId by mutableStateOf<Long?>(null)
    private var pendingDeepLinkUrl by mutableStateOf<String?>(null)
    private var pendingChatThreadKey by mutableStateOf<String?>(null)
    private var pendingChatTransactionId by mutableStateOf<Long?>(null)
    private var pendingChatPartyName by mutableStateOf<String?>(null)
    private var pendingRemoteRecordId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Telemetry starts strictly OFF until user consent is verified
        TelemetryManager.setConsent(applicationContext, enabled = false)
        ReminderReceiver.ensureNotificationChannel(this)
        ChatNotificationHelper.ensureNotificationChannel(this)
        extractIntentPayloads(intent)

        val repo = if (::injectedRepository.isInitialized) {
            injectedRepository
        } else {
            UdhaarRepository(UdhaarDatabase.getInstance(applicationContext).udhaarDao(), applicationContext)
        }

        setContent {
            val viewModel: UdhaarViewModel = viewModel(
                factory = UdhaarViewModel.Factory(repo, applicationContext)
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.userSettings.analyticsCrashConsent) {
                TelemetryManager.setConsent(
                    context = applicationContext,
                    enabled = uiState.userSettings.analyticsCrashConsent
                )
            }

            UdhaarTheme(
                themeMode = uiState.userSettings.themeMode,
                fontScaleMode = uiState.userSettings.fontScale
            ) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) { rootPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(rootPadding)
                            .consumeWindowInsets(rootPadding)
                    ) {
                        val targetTxId = pendingReminderTransactionId
                        LaunchedEffect(targetTxId) {
                            if (targetTxId != null && targetTxId > 0L) {
                                viewModel.openWhatsAppReminderModal(targetTxId)
                                pendingReminderTransactionId = null
                            }
                        }

                        val incomingUrl = pendingDeepLinkUrl
                        LaunchedEffect(incomingUrl) {
                            if (!incomingUrl.isNullOrBlank()) {
                                viewModel.handleIncomingDeepLinkOrToken(incomingUrl)
                                pendingDeepLinkUrl = null
                            }
                        }

                        val incomingChatKey = pendingChatThreadKey
                        val incomingRemoteRecordId = pendingRemoteRecordId
                        LaunchedEffect(incomingChatKey, incomingRemoteRecordId) {
                            if (!incomingChatKey.isNullOrBlank() || !incomingRemoteRecordId.isNullOrBlank()) {
                                viewModel.openChatFromNotification(
                                    threadKey = incomingChatKey.orEmpty(),
                                    transactionId = pendingChatTransactionId,
                                    partyName = pendingChatPartyName.orEmpty(),
                                    remoteRecordId = incomingRemoteRecordId.orEmpty()
                                )
                                pendingChatThreadKey = null
                                pendingChatTransactionId = null
                                pendingChatPartyName = null
                                pendingRemoteRecordId = null
                            }
                        }

                        UdhaarApp(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIntentPayloads(intent)
    }

    private fun extractIntentPayloads(intent: Intent?) {
        val txId = intent?.getLongExtra(ReminderReceiver.EXTRA_TRANSACTION_ID, -1L) ?: -1L
        if (txId > 0L) {
            pendingReminderTransactionId = txId
        }
        val chatKey = intent?.getStringExtra(ChatNotificationHelper.EXTRA_CHAT_THREAD_KEY)
        val remoteRecId = intent?.getStringExtra(ChatNotificationHelper.EXTRA_REMOTE_RECORD_ID)
        if (!chatKey.isNullOrBlank() || !remoteRecId.isNullOrBlank()) {
            pendingChatThreadKey = chatKey
            pendingRemoteRecordId = remoteRecId
            val chatTxId = intent.getLongExtra(ChatNotificationHelper.EXTRA_CHAT_TRANSACTION_ID, -1L)
            pendingChatTransactionId = if (chatTxId > 0L) chatTxId else null
            pendingChatPartyName = intent.getStringExtra(ChatNotificationHelper.EXTRA_CHAT_PARTY_NAME)
        }
        val uri = intent?.data
        if (uri != null) {
            val fullUrl = if (!uri.fragment.isNullOrBlank() && !uri.toString().contains("#")) {
                "${uri.toString()}#${uri.fragment}"
            } else {
                uri.toString()
            }
            if (fullUrl.contains("/u/")) {
                pendingDeepLinkUrl = fullUrl
            }
        }
    }
}

@Composable
fun UdhaarApp(viewModel: UdhaarViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var pendingJsonToSave by remember { mutableStateOf<String?>(null) }
    var showContactsRationaleDialog by remember { mutableStateOf(false) }
    var isPlanProposalMode by remember { mutableStateOf(false) }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.openInAppContactPicker()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.contact_permission_denied_manual),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val requestContactsFlow: () -> Unit = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.openInAppContactPicker()
        } else {
            showContactsRationaleDialog = true
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val jsonContent = pendingJsonToSave
        if (uri != null && jsonContent != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(jsonContent.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Ledger backup exported successfully.", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, "Failed to write JSON backup.", Toast.LENGTH_SHORT).show()
            }
        }
        pendingJsonToSave = null
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val rawContent = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!rawContent.isNullOrBlank()) {
                    if (rawContent.trimStart().startsWith("BEGIN:VCARD", ignoreCase = true)) {
                        val vcfContacts = DeviceContactHelper.parseVCardContacts(
                            vcfContent = rawContent,
                            currency = uiState.summaryCurrency
                        )
                        if (vcfContacts.isNotEmpty()) {
                            viewModel.importSelectedContacts(vcfContacts)
                            Toast.makeText(
                                context,
                                context.getString(R.string.contact_imported_count_fmt, vcfContacts.size),
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.contact_error_read_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        viewModel.importLedgerBackupJson(rawContent) { result ->
                            result.onSuccess { count ->
                                Toast.makeText(
                                    context,
                                    "Imported $count ledger records successfully.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "Invalid Udhaar JSON backup file.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }.onFailure {
                Toast.makeText(context, "Could not read backup file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val signInWithGoogleForVerification: () -> Unit = {
        coroutineScope.launch {
            runCatching {
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
                        com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()
                    }.getOrDefault(false)

                    if (!isFirebaseConfigured) {
                        Toast.makeText(
                            context,
                            "Firebase not configured: verification and chat are off",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    val firebaseCred = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCred).await()
                    val user = authResult.user
                    if (user != null) {
                        viewModel.setCloudAccountState(
                            consentGranted = true,
                            userId = user.uid,
                            email = user.email.orEmpty()
                        )
                        Toast.makeText(
                            context,
                            "Signed in as ${user.email ?: user.uid} for Two-Party Verification",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }.onFailure {
                Toast.makeText(
                    context,
                    "Offline verification links & QR codes remain active on this device.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val activity = context as? Activity
    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK && activity != null) {
            runCatching {
                val authorizationResult = Identity.getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(activityResult.data)
                val accessToken = authorizationResult.accessToken
                if (!accessToken.isNullOrBlank()) {
                    val accountEmail = runCatching { authorizationResult.toGoogleSignInAccount()?.email }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: FirebaseAuth.getInstance().currentUser?.email.orEmpty()
                        .ifBlank { uiState.userSettings.cloudUserEmail }
                    viewModel.setDriveAccountState(accountEmail)
                    Toast.makeText(context, "Connected to Google Drive", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { e ->
                Toast.makeText(context, "Google Drive authorization failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
            }
        } else {
            viewModel.handleDriveNeedsResolution(
                pendingIntent = null,
                message = "Google Drive authorization was canceled. Tap Reconnect to try again."
            )
        }
    }

    val signInOrReconnectGoogleDrive: () -> Unit = {
        val pendingResolution = uiState.driveResolutionPendingIntent
        if (pendingResolution != null) {
            runCatching {
                driveAuthLauncher.launch(
                    IntentSenderRequest.Builder(pendingResolution.intentSender).build()
                )
            }.onFailure { err ->
                Toast.makeText(context, "Could not launch Google Drive consent: ${err.localizedMessage ?: "Error"}", Toast.LENGTH_SHORT).show()
            }
        } else if (activity != null) {
            runCatching {
                val driveScope = Scope(DriveServiceHelper.DRIVE_APPDATA_SCOPE)
                val authRequest = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(driveScope))
                    .build()
                Identity.getAuthorizationClient(activity).authorize(authRequest)
                    .addOnSuccessListener { authorizationResult ->
                        if (authorizationResult.hasResolution()) {
                            val pendingIntent = authorizationResult.pendingIntent
                            if (pendingIntent != null) {
                                driveAuthLauncher.launch(
                                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                )
                            }
                        } else {
                            val accessToken = authorizationResult.accessToken
                            if (!accessToken.isNullOrBlank()) {
                                val accountEmail = runCatching { authorizationResult.toGoogleSignInAccount()?.email }
                                    .getOrNull()
                                    ?.takeIf { it.isNotBlank() }
                                    ?: FirebaseAuth.getInstance().currentUser?.email.orEmpty()
                                    .ifBlank { uiState.userSettings.cloudUserEmail }
                                viewModel.setDriveAccountState(accountEmail)
                                Toast.makeText(context, "Connected to Google Drive", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Google Drive sign in: ${e.localizedMessage ?: "Failed"}", Toast.LENGTH_SHORT).show()
                    }
            }.onFailure { err ->
                Toast.makeText(context, "Drive sign in error: ${err.localizedMessage ?: "Error"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val signOutFromDrive: () -> Unit = {
        viewModel.clearDriveAccountState()
        Toast.makeText(context, "Signed out from Google Drive", Toast.LENGTH_SHORT).show()
    }

    UdhaarScreenRouter(
        viewModel = viewModel,
        uiState = uiState,
        isPlanProposalMode = isPlanProposalMode,
        onPlanProposalModeChange = { isPlanProposalMode = it },
        requestContactsFlow = requestContactsFlow,
        signInWithGoogleForVerification = signInWithGoogleForVerification,
        signInOrReconnectGoogleDrive = signInOrReconnectGoogleDrive,
        signOutFromDrive = signOutFromDrive,
        onLaunchSaveJsonDocument = { fileName, jsonContent ->
            pendingJsonToSave = jsonContent
            createDocumentLauncher.launch(fileName)
        },
        onLaunchOpenDocument = {
            openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
    )

    if (uiState.blockingSqlCipherError == null && uiState.isAuthenticated) {
        UdhaarOverlaysHost(
            viewModel = viewModel,
            uiState = uiState,
            showContactsRationaleDialog = showContactsRationaleDialog,
            onDismissContactsRationaleDialog = { showContactsRationaleDialog = false },
            contactsPermissionLauncher = contactsPermissionLauncher,
            isPlanProposalMode = isPlanProposalMode,
            onPlanProposalModeChange = { isPlanProposalMode = it }
        )
    }
}
