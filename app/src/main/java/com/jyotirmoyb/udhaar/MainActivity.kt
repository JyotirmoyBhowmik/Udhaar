package com.jyotirmoyb.udhaar

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.jyotirmoyb.udhaar.ui.components.ContactsPermissionRationaleDialog
import com.jyotirmoyb.udhaar.ui.components.InAppContactPickerModal
import com.jyotirmoyb.udhaar.ui.components.LocalAnimatedVisibilityScope
import com.jyotirmoyb.udhaar.ui.components.LocalSharedTransitionScope
import com.jyotirmoyb.udhaar.ui.components.MergeDuplicatesModal
import com.jyotirmoyb.udhaar.ui.components.OnboardingModal
import com.jyotirmoyb.udhaar.ui.components.RestorePreviewDialog
import com.jyotirmoyb.udhaar.ui.components.SuccessCheckmarkOverlay
import com.jyotirmoyb.udhaar.ui.components.rememberReducedMotionEnabled
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.notification.ChatNotificationHelper
import com.jyotirmoyb.udhaar.notification.ReminderReceiver
import com.jyotirmoyb.udhaar.ui.ActiveScreen
import com.jyotirmoyb.udhaar.ui.UdhaarViewModel
import com.jyotirmoyb.udhaar.ui.components.AddEditPartyModal
import com.jyotirmoyb.udhaar.ui.components.AddEditUdhaarScreen
import com.jyotirmoyb.udhaar.ui.components.AttachmentViewerModal
import com.jyotirmoyb.udhaar.ui.components.BiometricSecurityScreen
import com.jyotirmoyb.udhaar.ui.components.ChatThreadBottomSheet
import com.jyotirmoyb.udhaar.ui.components.DashboardScreen
import com.jyotirmoyb.udhaar.ui.components.IncomingPaymentProfileAcceptSheet
import com.jyotirmoyb.udhaar.ui.components.IncomingVerificationReviewSheet
import com.jyotirmoyb.udhaar.ui.components.PartyDetailScreen
import com.jyotirmoyb.udhaar.ui.components.ProfileSettingsModal
import com.jyotirmoyb.udhaar.ui.components.RequestPartyPaymentDetailsSheet
import com.jyotirmoyb.udhaar.ui.components.ShareMyPaymentDetailsSheet
import com.jyotirmoyb.udhaar.ui.components.ShareUdhaarAppModal
import com.jyotirmoyb.udhaar.ui.components.SqlCipherBlockingErrorScreen
import com.jyotirmoyb.udhaar.ui.components.ThermalReceiptModal
import com.jyotirmoyb.udhaar.ui.components.TransactionDetailModal
import com.jyotirmoyb.udhaar.ui.components.UpiQrCodeModal
import com.jyotirmoyb.udhaar.ui.components.VerificationShareSheet
import com.jyotirmoyb.udhaar.ui.components.WhatsAppReminderModal
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.ui.theme.UdhaarTheme
import com.jyotirmoyb.udhaar.util.AttachmentManager
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.LedgerBackupManager
import com.jyotirmoyb.udhaar.util.PdfStatementGenerator
import com.jyotirmoyb.udhaar.util.ThermalReceiptGenerator
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

open class MainActivity : FragmentActivity() {

    private var pendingReminderTransactionId by mutableStateOf<Long?>(null)
    private var pendingDeepLinkUrl by mutableStateOf<String?>(null)
    private var pendingChatThreadKey by mutableStateOf<String?>(null)
    private var pendingChatTransactionId by mutableStateOf<Long?>(null)
    private var pendingChatPartyName by mutableStateOf<String?>(null)
    private var pendingRemoteRecordId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ReminderReceiver.ensureNotificationChannel(this)
        ChatNotificationHelper.ensureNotificationChannel(this)
        extractIntentPayloads(intent)

        val database = UdhaarDatabase.getInstance(applicationContext)
        val repository = UdhaarRepository(database.udhaarDao(), applicationContext)

        setContent {
            val viewModel: UdhaarViewModel = viewModel(
                factory = UdhaarViewModel.Factory(repository, applicationContext)
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            val chatTxId = intent?.getLongExtra(ChatNotificationHelper.EXTRA_CHAT_TRANSACTION_ID, -1L) ?: -1L
            pendingChatTransactionId = if (chatTxId > 0L) chatTxId else null
            pendingChatPartyName = intent?.getStringExtra(ChatNotificationHelper.EXTRA_CHAT_PARTY_NAME)
        }
        val dataUrl = intent?.data?.toString()
        if (!dataUrl.isNullOrBlank() && dataUrl.contains("/u/")) {
            pendingDeepLinkUrl = dataUrl
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

    // Storage Access Framework: CreateDocument for Exporting JSON Backup / Single-Party Statement
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

    // Storage Access Framework: OpenDocument for Importing JSON Backup or vCard (.vcf) Contacts
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val rawContent = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!rawContent.isNullOrBlank()) {
                    if (rawContent.trimStart().startsWith("BEGIN:VCARD", ignoreCase = true)) {
                        val vcfContacts = com.jyotirmoyb.udhaar.util.DeviceContactHelper.parseVCardContacts(
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
                val driveScope = Scope(com.jyotirmoyb.udhaar.backup.DriveServiceHelper.DRIVE_APPDATA_SCOPE)
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

    // Blocking error screen if SQLCipher could not start on a fresh install
    val blockingSqlCipherError = uiState.blockingSqlCipherError
    if (blockingSqlCipherError != null) {
        SqlCipherBlockingErrorScreen(
            errorMessage = blockingSqlCipherError,
            onExportBackup = {
                viewModel.generateFullLedgerBackupJson { json ->
                    pendingJsonToSave = json
                    createDocumentLauncher.launch("udhaar_emergency_backup.json")
                }
            },
            onTryAgain = {
                viewModel.retryDatabaseEncryption { ok ->
                    if (ok) {
                        Toast.makeText(context, "Encrypted storage initialized.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "SQLCipher initialization failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        return
    }

    // Enhancement A: Biometric / PIN App Lock Gate when opening the application
    if (!uiState.isAuthenticated) {
        BiometricSecurityScreen(
            hasPinConfigured = uiState.userSettings.hasPinConfigured,
            initialLockoutMillis = viewModel.getPinLockoutRemainingMillis(),
            initialFailedAttempts = viewModel.getPinFailedAttempts(),
            onVerifyPin = viewModel::verifyPin,
            onAuthenticated = viewModel::markAuthenticated
        )
        return
    }

    val reducedMotion = rememberReducedMotionEnabled()
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }

    val canNavigateBack = uiState.detailTransactionId != null ||
        uiState.activeScreen != ActiveScreen.DASHBOARD

    PredictiveBackHandler(enabled = canNavigateBack) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                predictiveBackProgress = if (reducedMotion) 0f else backEvent.progress
            }
            predictiveBackProgress = 0f
            when {
                uiState.detailTransactionId != null -> viewModel.closeTransactionDetail()
                uiState.activeScreen == ActiveScreen.ADD_EDIT -> viewModel.navigateBackToDashboard()
                uiState.activeScreen == ActiveScreen.SETTINGS -> viewModel.closeSettingsModal()
                uiState.activeScreen == ActiveScreen.PARTY_DETAIL -> viewModel.closePartyLedgerScreen()
                uiState.activeScreen == ActiveScreen.PEOPLE_DIRECTORY -> viewModel.switchMainTab(ActiveScreen.DASHBOARD)
            }
        } catch (_: CancellationException) {
            predictiveBackProgress = 0f
        }
    }

    val currentNavDestination = remember(uiState.activeScreen, uiState.detailTransactionId) {
        val detailId = uiState.detailTransactionId
        when {
            detailId != null -> UdhaarNavDestination.RecordDetail(detailId)
            uiState.activeScreen == ActiveScreen.PARTY_DETAIL -> UdhaarNavDestination.PartyDetail
            uiState.activeScreen == ActiveScreen.ADD_EDIT -> UdhaarNavDestination.AddEdit
            uiState.activeScreen == ActiveScreen.SETTINGS -> UdhaarNavDestination.Settings
            uiState.activeScreen == ActiveScreen.PEOPLE_DIRECTORY -> UdhaarNavDestination.PeopleDirectory
            else -> UdhaarNavDestination.Home
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        @OptIn(ExperimentalSharedTransitionApi::class)
        SharedTransitionLayout(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = predictiveBackProgress.coerceIn(0f, 1f)
                    val scale = 1f - (p * 0.04f)
                    scaleX = scale
                    scaleY = scale
                    translationX = p * 24f
                }
        ) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                AnimatedContent(
                    targetState = currentNavDestination,
                    transitionSpec = {
                        if (reducedMotion) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            val isForward = targetState.depth >= initialState.depth
                            val slideEnter = slideInHorizontally(
                                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                                initialOffsetX = { fullWidth -> if (isForward) fullWidth / 5 else -fullWidth / 5 }
                            ) + fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))

                            val slideExit = slideOutHorizontally(
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> if (isForward) -fullWidth / 6 else fullWidth / 6 }
                            ) + fadeOut(animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing))

                            slideEnter togetherWith slideExit
                        }
                    },
                    label = "udhaar_screen_transition",
                    modifier = Modifier.fillMaxSize()
                ) { navDestination ->
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        when (navDestination) {
                            is UdhaarNavDestination.Home,
                            is UdhaarNavDestination.PeopleDirectory -> {
                                DashboardScreen(
                                    uiState = uiState,
                                    onSwitchTab = viewModel::switchMainTab,
                                    onSelectSummaryCurrency = viewModel::setSummaryCurrency,
                                    onToggleCurrencyEnabled = { currency, enabled ->
                                        viewModel.toggleCurrencyEnabled(currency, enabled)
                                        Toast.makeText(
                                            context,
                                            "${currency.displayName} ${if (enabled) "enabled" else "disabled"}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onCycleTheme = {
                                        val themes = AppThemeMode.entries
                                        val nextIndex = (themes.indexOf(uiState.userSettings.themeMode) + 1) % themes.size
                                        val nextTheme = themes[nextIndex]
                                        viewModel.cycleOrSelectTheme(nextTheme)
                                        Toast.makeText(context, "Theme: ${nextTheme.label}", Toast.LENGTH_SHORT).show()
                                    },
                                    onSelectFilter = viewModel::setFilter,
                                    onSearchQueryChange = viewModel::setSearchQuery,
                                    onPeopleSearchQueryChange = viewModel::setPeopleSearchQuery,
                                    onAddUdhaarClick = viewModel::openAddTransaction,
                                    onAddPartyClick = viewModel::openAddPartyModal,
                                    onPartyClick = { khata ->
                                        viewModel.openPartyLedgerScreen(khata.party.partyId)
                                    },
                                    onQuickAddLoanForParty = viewModel::openAddTransactionForParty,
                                    onTransactionClick = { summary ->
                                        viewModel.openTransactionDetail(summary.transaction.id)
                                    },
                                    onWhatsAppClick = { summary ->
                                        viewModel.openWhatsAppReminderModal(summary.transaction.id)
                                    },
                                    onTransactionQrClick = { summary ->
                                        viewModel.openTransactionQrModal(summary)
                                    },
                                    onOpenTransactionChat = { summary ->
                                        viewModel.openTransactionChat(summary.transaction.id)
                                    },
                                    onOpenPartyGeneralChat = { party ->
                                        viewModel.openPartyGeneralChat(party)
                                    },
                                    onGlobalQrClick = viewModel::openGlobalQrModal,
                                    onShareMyPaymentDetailsClick = {
                                        viewModel.openShareMyPaymentDetailsModal(null)
                                    },
                                    onShareAppClick = viewModel::openShareAppModal,
                                    onLockAppClick = viewModel::lockApplicationNow,
                                    onSettingsClick = viewModel::openSettingsModal,
                                    onPartySortOptionChange = viewModel::setPartySortOption,
                                    onOpenMergeDuplicates = viewModel::openMergeDuplicatesModal,
                                    onOpenContactPicker = requestContactsFlow,
                                    onToggleFavoriteParty = viewModel::togglePartyFavorite,
                                    onTransactionSortOptionChange = viewModel::setTransactionSortOption,
                                    onDateRangeFilterChange = viewModel::setDateRangeFilter,
                                    onExportFullLedgerPdf = {
                                        viewModel.generateFullLedgerPdf { file ->
                                            PdfStatementGenerator.sharePdfFile(context, file, "Udhaar Full Ledger Statement")
                                        }
                                    },
                                    onToggleHideAmounts = viewModel::toggleHideAmounts,
                                    onDismissNeedsAttention = viewModel::dismissNeedsAttentionStrip,
                                    onDismissCoachMarks = viewModel::dismissCoachMarks,
                                    onUndoAction = viewModel::performUndoAction,
                                    onDismissUndo = viewModel::dismissUndoSnackbar
                                )
                            }
                            is UdhaarNavDestination.PartyDetail -> {
                                val selectedPartyId = uiState.selectedPartyIdForSheet
                                val selectedKhata = uiState.partyKhataList.find { it.party.partyId == selectedPartyId }
                                if (selectedKhata != null) {
                                    PartyDetailScreen(
                                        khata = selectedKhata,
                                        enabledCurrencies = uiState.userSettings.enabledCurrencies,
                                        onBack = viewModel::closePartyLedgerScreen,
                                        onEditParty = {
                                            viewModel.openEditPartyModal(selectedKhata.party)
                                        },
                                        onDeleteParty = {
                                            viewModel.deleteParty(selectedKhata.party)
                                        },
                                        onAddLoanForParty = {
                                            viewModel.openAddTransactionForParty(selectedKhata.party)
                                        },
                                        onTransactionClick = { summary ->
                                            viewModel.openTransactionDetail(summary.transaction.id)
                                        },
                                        onWhatsAppClick = { summary ->
                                            viewModel.openWhatsAppReminderModal(summary.transaction.id)
                                        },
                                        onOpenTransactionChat = { summary ->
                                            viewModel.openTransactionChat(summary.transaction.id)
                                        },
                                        onOpenPartyGeneralChat = {
                                            viewModel.openPartyGeneralChat(selectedKhata.party)
                                        },
                                        unreadPartyChatCount = uiState.unreadCountByPartyName[selectedKhata.party.name.trim().lowercase()] ?: 0,
                                        unreadCountByTx = uiState.unreadCountByTx,
                                        onPayPartyViaUpi = { amountOwed ->
                                            viewModel.openPayPartyViaUpiModal(
                                                partyName = selectedKhata.party.name,
                                                partyUpiId = selectedKhata.party.upiId,
                                                amountInr = amountOwed
                                            )
                                        },
                                        onShareMyPaymentDetails = {
                                            viewModel.openShareMyPaymentDetailsModal(selectedKhata.party)
                                        },
                                        onRequestPartyPaymentDetails = {
                                            viewModel.openRequestPartyPaymentDetailsModal(selectedKhata.party)
                                        },
                                        onToggleFavorite = viewModel::togglePartyFavorite,
                                        onRequestVerification = {
                                            val firstTx = selectedKhata.transactions.firstOrNull()?.transaction
                                            if (firstTx != null) {
                                                viewModel.requestVerificationForTransaction(firstTx)
                                            } else {
                                                Toast.makeText(context, "No transactions to verify for this party yet.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onShareThermalReceipt = {
                                            viewModel.openPartyStatementReceiptModal(selectedKhata.party.partyId)
                                        },
                                        onExportPartyStatement = {
                                            viewModel.generateSinglePartyStatementJson(selectedKhata) { json ->
                                                pendingJsonToSave = json
                                                val safeName = selectedKhata.party.name.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase()
                                                createDocumentLauncher.launch("udhaar_statement_${safeName}.json")
                                            }
                                        },
                                        onSharePartyStatement = {
                                            viewModel.generateSinglePartyStatementJson(selectedKhata) { json ->
                                                val safeName = selectedKhata.party.name.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase()
                                                LedgerBackupManager.shareBackupFile(
                                                    context = context,
                                                    jsonContent = json,
                                                    fileNamePrefix = "udhaar_statement_${safeName}"
                                                )
                                            }
                                        },
                                        onExportPartyPdfStatement = {
                                            viewModel.generatePartyStatementPdf(selectedKhata) { file ->
                                                PdfStatementGenerator.sharePdfFile(
                                                    context = context,
                                                    file = file,
                                                    subject = "Udhaar Statement - ${selectedKhata.party.name}"
                                                )
                                            }
                                        }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        viewModel.closePartyLedgerScreen()
                                    }
                                }
                            }
                            is UdhaarNavDestination.RecordDetail -> {
                                val detailTxId = navDestination.transactionId
                                val detailSummary = uiState.allCalculatedItems.find { it.transaction.id == detailTxId }
                                if (detailSummary != null) {
                                    val payments = uiState.partialPaymentsByTx[detailTxId].orEmpty()
                                    val auditEntries = uiState.auditTrailByTx[detailTxId].orEmpty()
                                    val linkedParty = uiState.allParties.find { p ->
                                        (detailSummary.transaction.partyId != null && p.partyId == detailSummary.transaction.partyId) ||
                                            p.name.trim().equals(detailSummary.transaction.partyName.trim(), ignoreCase = true)
                                    }
                                    val txAttachments = uiState.attachmentsByTx[detailTxId].orEmpty()
                                    TransactionDetailModal(
                                        summary = detailSummary,
                                        partialPayments = payments,
                                        auditTrail = auditEntries,
                                        linkedParty = linkedParty,
                                        attachments = txAttachments,
                                        attachmentsByPayment = uiState.attachmentsByPayment,
                                        onDismiss = viewModel::closeTransactionDetail,
                                        onRecordPartialPayment = { amount, paymentMode, note ->
                                            viewModel.recordPartialPayment(detailSummary, amount, paymentMode, note, recordedByCounterparty = false)
                                        },
                                        onRecordPartialPaymentWithProof = { amount, paymentMode, note, proofAttachment ->
                                            viewModel.recordPartialPayment(
                                                summary = detailSummary,
                                                amount = amount,
                                                paymentMode = paymentMode,
                                                note = note,
                                                recordedByCounterparty = false,
                                                proofAttachment = proofAttachment
                                            )
                                        },
                                        onRecordCounterpartyPayment = { amount, paymentMode, note ->
                                            viewModel.recordPartialPayment(detailSummary, amount, paymentMode, note, recordedByCounterparty = true)
                                        },
                                        onAcknowledgePartialPayment = { payment ->
                                            viewModel.acknowledgePartialPayment(detailSummary.transaction, payment)
                                        },
                                        onDeletePartialPayment = { payment ->
                                            viewModel.deletePartialPayment(detailSummary, payment)
                                        },
                                        onAddAttachment = { att ->
                                            viewModel.addAttachmentToTransaction(detailTxId, att)
                                        },
                                        onDeleteAttachment = viewModel::deleteAttachment,
                                        onOpenAttachmentViewer = viewModel::openAttachmentViewer,
                                        onRequestVerification = {
                                            viewModel.requestVerificationForTransaction(detailSummary.transaction)
                                        },
                                        onConfirmOrDisputeRecord = { confirm, reason ->
                                            viewModel.confirmOrDisputeExistingRecord(detailSummary.transaction, confirm, reason)
                                        },
                                        onToggleSettled = {
                                            viewModel.toggleSettled(detailSummary)
                                        },
                                        onEdit = {
                                            viewModel.openEditTransaction(detailSummary.transaction)
                                        },
                                        onDeleteTransaction = {
                                            viewModel.deleteTransaction(detailSummary.transaction)
                                        },
                                        onOpenQr = {
                                            viewModel.openTransactionQrModal(detailSummary)
                                        },
                                        onPayPartyViaUpi = { partyName, partyUpiId, amountInr ->
                                            viewModel.openPayPartyViaUpiModal(partyName, partyUpiId, amountInr)
                                        },
                                        onShareReceipt = {
                                            viewModel.openTransactionReceiptModal(detailSummary.transaction.id)
                                        },
                                        onOpenWhatsApp = {
                                            viewModel.openWhatsAppReminderModal(detailSummary.transaction.id)
                                        },
                                        unreadChatCount = uiState.unreadCountByTx[detailSummary.transaction.id] ?: 0,
                                        onOpenTransactionChat = {
                                            viewModel.closeTransactionDetail()
                                            viewModel.openTransactionChat(detailSummary.transaction.id)
                                        }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        viewModel.closeTransactionDetail()
                                    }
                                }
                            }
                            is UdhaarNavDestination.AddEdit -> {
                                AddEditUdhaarScreen(
                                    initialTransaction = uiState.editingTransaction,
                                    preselectedPartyDraft = uiState.preselectedPartyDraft,
                                    savedParties = uiState.allParties,
                                    enabledCurrencies = uiState.userSettings.enabledCurrencies,
                                    defaultCurrency = uiState.summaryCurrency,
                                    draftAttachments = uiState.draftAttachments,
                                    existingAttachments = uiState.attachmentsByTx[uiState.editingTransaction?.id ?: -1L].orEmpty(),
                                    onEnableCurrency = { curr ->
                                        viewModel.toggleCurrencyEnabled(curr, true)
                                    },
                                    onAddDraftAttachment = { att ->
                                        val editingId = uiState.editingTransaction?.id
                                        if (editingId != null && editingId > 0L) {
                                            viewModel.addAttachmentToTransaction(editingId, att)
                                        } else {
                                            viewModel.stageDraftAttachment(att)
                                        }
                                    },
                                    onRemoveDraftAttachment = viewModel::removeDraftAttachment,
                                    onDeleteExistingAttachment = viewModel::deleteAttachment,
                                    onOpenAttachmentViewer = viewModel::openAttachmentViewer,
                                    onOpenInAppContactPicker = requestContactsFlow,
                                    onSave = viewModel::saveUdhaarTransaction,
                                    onDeleteTransaction = viewModel::deleteTransaction,
                                    onCancel = viewModel::navigateBackToDashboard
                                )
                            }
                            is UdhaarNavDestination.Settings -> {
                                ProfileSettingsModal(
                                    currentSettings = uiState.userSettings,
                                    onSave = { upiId, payeeName, currency, inrEnabled, nprEnabled, themeMode, fontScale, bioEnabled, pin, closeModal ->
                                        viewModel.saveUserSettings(
                                            upiId = upiId,
                                            payeeName = payeeName,
                                            defaultCurrency = currency,
                                            inrEnabled = inrEnabled,
                                            nprEnabled = nprEnabled,
                                            themeMode = themeMode,
                                            fontScale = fontScale,
                                            biometricLockEnabled = bioEnabled,
                                            securityPin = pin,
                                            closeModal = closeModal
                                        )
                                    },
                                    onSaveExtendedProfile = { phone, photoUri, cloudConsent ->
                                        viewModel.saveUserSettings(
                                            upiId = uiState.userSettings.upiId,
                                            payeeName = uiState.userSettings.payeeName,
                                            defaultCurrency = uiState.userSettings.defaultCurrency,
                                            phoneNumber = phone,
                                            profilePhotoUri = photoUri,
                                            cloudVerificationConsent = cloudConsent,
                                            closeModal = false
                                        )
                                    },
                                    onShareMyPaymentDetails = {
                                        viewModel.openShareMyPaymentDetailsModal(null)
                                    },
                                    onOpenReceivedVerificationLink = { link ->
                                        val handled = viewModel.handleIncomingDeepLinkOrToken(link)
                                        if (!handled) {
                                            Toast.makeText(context, "Invalid verification or payment link.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onSignInWithGoogleForVerification = signInWithGoogleForVerification,
                                    onRetryCloudBackup = {
                                        viewModel.retryCloudBackup()
                                    },
                                    onDeleteMyCloudData = {
                                        viewModel.deleteMyCloudData {
                                            Toast.makeText(
                                                context,
                                                "Cloud verification data cleared. Local ledger preserved.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    onLockAppNow = viewModel::lockApplicationNow,
                                    onOpenGlobalQrModal = viewModel::openGlobalQrModal,
                                    onOpenShareAppModal = {
                                        viewModel.openShareAppModal()
                                    },
                                    onExportFullLedgerSaf = {
                                        viewModel.generateFullLedgerBackupJson { json ->
                                            pendingJsonToSave = json
                                            createDocumentLauncher.launch("udhaar_full_ledger_backup.json")
                                        }
                                    },
                                    onShareFullLedgerFile = {
                                        viewModel.generateFullLedgerBackupJson { json ->
                                            LedgerBackupManager.shareBackupFile(
                                                context = context,
                                                jsonContent = json,
                                                fileNamePrefix = "udhaar_full_ledger_backup"
                                            )
                                        }
                                    },
                                    onImportLedgerSaf = {
                                        openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                    },
                                    driveRemoteBackups = uiState.driveRemoteBackups,
                                    isDriveOperationInProgress = uiState.isDriveOperationInProgress,
                                    driveOperationStatusMessage = uiState.driveOperationStatusMessage,
                                    driveOperationError = uiState.driveOperationError,
                                    hasPendingDriveResolution = uiState.driveResolutionPendingIntent != null,
                                    driveConnectionTestReport = uiState.driveConnectionTestReport,
                                    onSignInWithGoogleForDrive = signInOrReconnectGoogleDrive,
                                    onReconnectGoogleDrive = signInOrReconnectGoogleDrive,
                                    onRunDriveConnectionTest = viewModel::runDriveConnectionTest,
                                    onClearDriveConnectionTestReport = viewModel::clearDriveConnectionTestReport,
                                    onSignOutFromDrive = signOutFromDrive,
                                    onBackupNowToDrive = { pass, saveAuto ->
                                        viewModel.uploadDriveBackup(pass, saveAuto)
                                    },
                                    onRestoreDriveBackup = { backup, pass ->
                                        viewModel.downloadAndDecryptDriveBackup(backup, pass)
                                    },
                                    onDeleteDriveBackup = { backup ->
                                        viewModel.deleteDriveBackup(backup)
                                    },
                                    onUpdateAutoBackupSettings = { freq, wifiOnly, pass ->
                                        viewModel.updateDriveAutoBackupSettings(freq, wifiOnly, pass)
                                    },
                                    onRefreshDriveBackups = {
                                        viewModel.refreshDriveBackups()
                                    },
                                    onClearDriveOperationError = {
                                        viewModel.clearDriveOperationError()
                                    },
                                    dbEncryptionState = uiState.dbEncryptionState,
                                    onRetryDatabaseEncryption = {
                                        viewModel.retryDatabaseEncryption { ok ->
                                            if (ok) {
                                                Toast.makeText(context, "Database encrypted successfully.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Database encryption failed. Tap to retry.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onExportLocalUdhaarSaf = { passphrase ->
                                        viewModel.generateLocalEncryptedUdhaarBackup(passphrase) { result ->
                                            result.onSuccess { encryptedBytes ->
                                                pendingJsonToSave = String(encryptedBytes, Charsets.UTF_8)
                                                val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                                                    .format(java.util.Date())
                                                createDocumentLauncher.launch("udhaar_backup_${dateStr}.udhaar")
                                            }.onFailure {
                                                Toast.makeText(context, "Failed to encrypt .udhaar backup.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onOpenContactPicker = requestContactsFlow,
                                    onDismiss = viewModel::closeSettingsModal
                                )
                            }
                        }
                    }
                }
            }
        }

        // Non-blocking drawn checkmark success animation overlay (record saved, payment recorded, record verified)
        SuccessCheckmarkOverlay(
            event = uiState.successAnimationEvent,
            onFinished = viewModel::clearSuccessAnimation
        )
    }

    // Google Drive Restore Preview & Confirmation Dialog
    val restoreBackup = uiState.pendingRestoreBackup
    val restoreParsed = uiState.pendingRestoreParsed
    if (uiState.showRestorePreviewDialog && restoreParsed != null && restoreBackup != null) {
        RestorePreviewDialog(
            backup = restoreBackup,
            parsed = restoreParsed,
            onMerge = {
                viewModel.executeDriveRestore(replaceExisting = false)
            },
            onReplace = {
                viewModel.executeDriveRestore(replaceExisting = true)
            },
            onDismiss = viewModel::closeRestorePreviewDialog
        )
    }

    // Add / Edit Party Contact Profile Modal
    if (uiState.showAddEditPartyModal) {
        AddEditPartyModal(
            initialParty = uiState.editingParty,
            enabledCurrencies = uiState.userSettings.enabledCurrencies,
            defaultCurrency = uiState.summaryCurrency,
            onSave = viewModel::saveParty,
            onDismiss = viewModel::closeAddEditPartyModal
        )
    }

    // Phase 4: In-App Communication Chat Thread Bottom Sheet (Per-Transaction or Per-Party)
    val activeThreadKey = uiState.activeChatThreadKey
    if (!activeThreadKey.isNullOrBlank()) {
        val activeTxId = uiState.activeChatTransactionId
        val activeTxSummary = if (activeTxId != null) {
            uiState.allCalculatedItems.find { it.transaction.id == activeTxId }
        } else {
            null
        }
        val activeParty = uiState.allParties.find { p ->
            (uiState.activeChatPartyId != null && p.partyId == uiState.activeChatPartyId) ||
                p.name.trim().equals(uiState.activeChatPartyName.trim(), ignoreCase = true)
        }
        val threadMetadata = uiState.chatThreadsByKey[activeThreadKey]
        val messages = uiState.chatMessagesByThreadKey[activeThreadKey].orEmpty()
        val auditEvents = if (activeTxId != null) {
            uiState.auditTrailByTx[activeTxId].orEmpty()
        } else {
            emptyList()
        }

        val resolvedPartyName = uiState.activeChatPartyName.ifBlank {
            activeTxSummary?.transaction?.partyName ?: activeParty?.name ?: "Party"
        }
        val resolvedPartyId = uiState.activeChatPartyId ?: activeTxSummary?.transaction?.partyId ?: activeParty?.partyId

        ChatThreadBottomSheet(
            threadKey = activeThreadKey,
            scope = uiState.activeChatScope,
            partyName = resolvedPartyName,
            partyId = resolvedPartyId,
            partyPhone = activeTxSummary?.transaction?.phoneNumber?.ifBlank { activeParty?.phoneNumber.orEmpty() }
                ?: activeParty?.phoneNumber.orEmpty(),
            transactionSummary = activeTxSummary,
            threadMetadata = threadMetadata,
            messages = messages,
            auditEntries = auditEvents,
            myProfileName = uiState.userSettings.payeeName.ifBlank { "You" },
            myUpiId = uiState.userSettings.upiId,
            onDismiss = viewModel::closeChatSheet,
            onSendMessage = { messageType, textContent, attachmentUri, attachmentMimeType, fromCounterparty ->
                viewModel.sendChatMessage(
                    threadKey = activeThreadKey,
                    scope = uiState.activeChatScope,
                    transaction = activeTxSummary?.transaction,
                    partyId = resolvedPartyId,
                    partyName = resolvedPartyName,
                    messageType = messageType,
                    textContent = textContent,
                    attachmentUri = attachmentUri,
                    attachmentMimeType = attachmentMimeType,
                    fromCounterparty = fromCounterparty
                )
            },
            onRequestExtension = { proposedMillis, note, requestedByCounterparty ->
                if (activeTxSummary != null) {
                    viewModel.requestDueDateExtension(
                        transaction = activeTxSummary.transaction,
                        proposedDueDateMillis = proposedMillis,
                        note = note,
                        requestedByCounterparty = requestedByCounterparty
                    )
                    Toast.makeText(
                        context,
                        "Due-date extension proposed in chat.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onRespondToExtension = { message, accept ->
                if (activeTxSummary != null) {
                    viewModel.respondToExtensionRequest(activeTxSummary.transaction, message, accept)
                    Toast.makeText(
                        context,
                        if (accept) "Extension accepted! Due date updated." else "Extension request declined.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onSetTypingIndicator = { isTyping ->
                viewModel.setTypingIndicator(
                    threadKey = activeThreadKey,
                    scope = uiState.activeChatScope,
                    transactionId = activeTxSummary?.transaction?.id,
                    remoteId = activeTxSummary?.transaction?.remoteId.orEmpty(),
                    partyId = resolvedPartyId,
                    partyName = resolvedPartyName,
                    isCounterpartyTyping = isTyping
                )
            },
            onToggleMute = {
                viewModel.toggleThreadMute(
                    threadKey = activeThreadKey,
                    scope = uiState.activeChatScope,
                    transactionId = activeTxSummary?.transaction?.id,
                    remoteId = activeTxSummary?.transaction?.remoteId.orEmpty(),
                    partyId = resolvedPartyId,
                    partyName = resolvedPartyName
                )
            },
            onToggleBlock = {
                viewModel.toggleThreadBlock(
                    threadKey = activeThreadKey,
                    scope = uiState.activeChatScope,
                    transactionId = activeTxSummary?.transaction?.id,
                    remoteId = activeTxSummary?.transaction?.remoteId.orEmpty(),
                    partyId = resolvedPartyId,
                    partyName = resolvedPartyName
                )
            },
            onReportAbusiveUser = { reason ->
                viewModel.reportAbusiveUserInThread(
                    threadKey = activeThreadKey,
                    scope = uiState.activeChatScope,
                    transactionId = activeTxSummary?.transaction?.id,
                    remoteId = activeTxSummary?.transaction?.remoteId.orEmpty(),
                    partyId = resolvedPartyId,
                    partyName = resolvedPartyName,
                    reason = reason
                )
                Toast.makeText(
                    context,
                    "Abusive user reported and blocked.",
                    Toast.LENGTH_LONG
                ).show()
            },
            onOpenWhatsAppFallback = { txId ->
                viewModel.closeChatSheet()
                viewModel.openWhatsAppReminderModal(txId)
            }
        )
    }

    // Phase 3: Verification Share Sheet (QR & Link for https://udhaar.jyotirmoyb.com/u/<token>)
    val verifyShareTxId = uiState.verificationShareTransactionId
    val verifyShareLink = uiState.verificationShareLink
    if (verifyShareTxId != null && !verifyShareLink.isNullOrBlank()) {
        val txSummary = uiState.allCalculatedItems.find { it.transaction.id == verifyShareTxId }
        if (txSummary != null) {
            val verifyAttachments = uiState.attachmentsByTx[verifyShareTxId].orEmpty()
            VerificationShareSheet(
                transaction = txSummary.transaction,
                shareLink = verifyShareLink,
                settings = uiState.userSettings,
                attachments = verifyAttachments,
                onDismiss = viewModel::closeVerificationShareModal,
                onOpenVerificationLink = { link ->
                    viewModel.closeVerificationShareModal()
                    val handled = viewModel.handleIncomingDeepLinkOrToken(link)
                    if (!handled) {
                        Toast.makeText(context, "Invalid verification link.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    // Phase 3: Incoming Verification Review Sheet (Confirm / Save Mirrored Record or Dispute)
    val incomingVerification = uiState.incomingVerificationPayload
    if (incomingVerification != null) {
        IncomingVerificationReviewSheet(
            payload = incomingVerification,
            onDismiss = viewModel::dismissIncomingVerification,
            onConfirmRecord = {
                viewModel.confirmOrDisputeIncomingVerification(confirm = true)
                Toast.makeText(
                    context,
                    "Confirmed! Mirrored record saved to your Khata.",
                    Toast.LENGTH_LONG
                ).show()
            },
            onDisputeRecord = { reason ->
                viewModel.confirmOrDisputeIncomingVerification(confirm = false, disputeReason = reason)
                Toast.makeText(
                    context,
                    "Dispute recorded on entry.",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    // Phase 3: Share My Payment Details Sheet
    if (uiState.showShareMyPaymentDetailsModal) {
        ShareMyPaymentDetailsSheet(
            settings = uiState.userSettings,
            targetParty = uiState.shareMyPaymentDetailsTargetParty,
            onDismiss = viewModel::closeShareMyPaymentDetailsModal
        )
    }

    // Phase 3: Request Party Payment Details Sheet
    val requestParty = uiState.requestPaymentDetailsParty
    if (requestParty != null) {
        RequestPartyPaymentDetailsSheet(
            party = requestParty,
            settings = uiState.userSettings,
            onDismiss = viewModel::closeRequestPartyPaymentDetailsModal,
            onOpenReceivedProfileLink = { link ->
                val handled = viewModel.handleIncomingDeepLinkOrToken(
                    urlOrToken = link,
                    targetPartyId = requestParty.partyId
                )
                if (!handled) {
                    Toast.makeText(context, "Invalid payment profile link.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Phase 3: Incoming Payment Profile Accept Sheet (Auto-saves UPI ID to Party after user accepts)
    val incomingProfile = uiState.incomingPaymentProfilePayload
    if (incomingProfile != null) {
        IncomingPaymentProfileAcceptSheet(
            payload = incomingProfile,
            onDismiss = viewModel::dismissIncomingPaymentProfile,
            onAccept = {
                viewModel.acceptIncomingPaymentProfile()
                Toast.makeText(
                    context,
                    "Saved ${incomingProfile.displayName}'s UPI ID (${InterestCalculator.maskUpiId(incomingProfile.upiId)})",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    // Enhancement B: Thermal Statement Receipt Preview & Share Modal (Single Transaction)
    val receiptTxId = uiState.receiptModalTransactionId
    if (receiptTxId != null) {
        val receiptSummary = uiState.allCalculatedItems.find { it.transaction.id == receiptTxId }
        if (receiptSummary != null) {
            val payments = uiState.partialPaymentsByTx[receiptTxId].orEmpty()
            val auditTrail = uiState.auditTrailByTx[receiptTxId].orEmpty()
            val linkedParty = uiState.allParties.find { p ->
                (receiptSummary.transaction.partyId != null && p.partyId == receiptSummary.transaction.partyId) ||
                    p.name.trim().equals(receiptSummary.transaction.partyName.trim(), ignoreCase = true)
            }
            val receiptStrings = remember(context) {
                ThermalReceiptGenerator.ThermalReceiptStrings.fromContext(context)
            }
            val bitmap = remember(
                receiptSummary,
                payments,
                auditTrail,
                uiState.userSettings,
                linkedParty,
                uiState.isAmountsHidden,
                receiptStrings
            ) {
                ThermalReceiptGenerator.createTransactionReceiptBitmap(
                    summary = receiptSummary,
                    partialPayments = payments,
                    userSettings = uiState.userSettings,
                    partyUpiId = linkedParty?.upiId,
                    auditTrail = auditTrail,
                    hideAmounts = uiState.isAmountsHidden,
                    strings = receiptStrings
                )
            }
            val safeParty = receiptSummary.transaction.partyName.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase()
            val formattedNet = InterestCalculator.formatCurrencyPrivacy(
                receiptSummary.netBalanceDue,
                receiptSummary.transaction.currency,
                uiState.isAmountsHidden
            )
            ThermalReceiptModal(
                title = stringResource(R.string.receipt_modal_tx_title),
                subtitle = "${receiptSummary.transaction.partyName.uppercase()} • $formattedNet",
                receiptBitmap = bitmap,
                fileNamePrefix = "udhaar_receipt_${safeParty}_${receiptSummary.transaction.id}",
                shareCaption = "Udhaar Statement Receipt for ${receiptSummary.transaction.partyName} — Net Balance: $formattedNet",
                onDismiss = viewModel::closeReceiptModal
            )
        }
    }

    // Enhancement B: Thermal Statement Receipt Preview & Share Modal (Party Khata Statement)
    val receiptPartyId = uiState.receiptModalPartyId
    if (receiptPartyId != null) {
        val khata = uiState.partyKhataList.find { it.party.partyId == receiptPartyId }
        if (khata != null) {
            val receiptStrings = remember(context) {
                ThermalReceiptGenerator.ThermalReceiptStrings.fromContext(context)
            }
            val bitmap = remember(khata, uiState.userSettings, uiState.isAmountsHidden, receiptStrings) {
                ThermalReceiptGenerator.createPartyStatementReceiptBitmap(
                    khata = khata,
                    enabledCurrencies = uiState.userSettings.enabledCurrencies,
                    userSettings = uiState.userSettings,
                    hideAmounts = uiState.isAmountsHidden,
                    strings = receiptStrings
                )
            }
            val safeParty = khata.party.name.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase()
            ThermalReceiptModal(
                title = stringResource(R.string.receipt_modal_party_title),
                subtitle = "${khata.party.name.uppercase()} • ${khata.transactions.size} RECORDS",
                receiptBitmap = bitmap,
                fileNamePrefix = "udhaar_khata_statement_${safeParty}",
                shareCaption = "Udhaar Party Khata Statement for ${khata.party.name}",
                onDismiss = viewModel::closeReceiptModal
            )
        }
    }

    // WhatsApp Reminder Preview Modal (with Hide Exact Amount toggle)
    val whatsAppTxId = uiState.whatsAppPreviewTransactionId
    if (whatsAppTxId != null) {
        val reminderSummary = uiState.allCalculatedItems.find { it.transaction.id == whatsAppTxId }
        if (reminderSummary != null) {
            val linkedParty = uiState.allParties.find { p ->
                (reminderSummary.transaction.partyId != null && p.partyId == reminderSummary.transaction.partyId) ||
                    p.name.trim().equals(reminderSummary.transaction.partyName.trim(), ignoreCase = true)
            }
            WhatsAppReminderModal(
                summary = reminderSummary,
                userSettings = uiState.userSettings,
                fallbackPhoneNumber = linkedParty?.phoneNumber.orEmpty(),
                onDismiss = viewModel::closeWhatsAppReminderModal
            )
        }
    }

    // UPI QR Code Generator Modal (Global, Per-Transaction, or Pay-To-Party Mode)
    val qrState = uiState.qrModalState
    if (qrState != null) {
        UpiQrCodeModal(
            qrState = qrState,
            userSettings = uiState.userSettings,
            onSaveUpiSettings = { upiId, payeeName, currency ->
                viewModel.saveUserSettings(upiId, payeeName, currency, closeModal = false)
            },
            onDismiss = viewModel::closeQrModal
        )
    }

    // Share Udhaar App Modal (Play Store QR + WhatsApp Invite)
    if (uiState.showShareAppModal) {
        ShareUdhaarAppModal(
            onDismiss = viewModel::closeShareAppModal
        )
    }

    // Phase 6: Full-Screen Pinch-Zoom Attachment Viewer Modal
    val viewerAttachment = uiState.activeViewerAttachment
    if (viewerAttachment != null) {
        AttachmentViewerModal(
            attachment = viewerAttachment,
            onDismiss = viewModel::closeAttachmentViewer,
            onShare = {
                AttachmentManager.shareAttachment(context, viewerAttachment.localPath, viewerAttachment.mimeType)
            },
            onDelete = {
                viewModel.deleteAttachment(viewerAttachment)
            }
        )
    }

    // Phase 7: Contacts Permission Rationale Dialog
    if (showContactsRationaleDialog) {
        ContactsPermissionRationaleDialog(
            onRequestPermission = {
                showContactsRationaleDialog = false
                runCatching {
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }.onFailure {
                    Toast.makeText(
                        context,
                        context.getString(R.string.contact_error_no_app),
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDismiss = {
                showContactsRationaleDialog = false
            }
        )
    }

    // Phase 7: In-App Contact Picker Bottom Sheet Modal
    if (uiState.showInAppContactPicker) {
        InAppContactPickerModal(
            contacts = uiState.deviceContacts,
            isLoading = uiState.isLoadingContacts,
            onImportContacts = { selected ->
                if (selected.isEmpty()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.contact_error_read_failed),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    viewModel.importSelectedContacts(selected)
                    Toast.makeText(
                        context,
                        context.getString(R.string.contact_imported_count_fmt, selected.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = viewModel::closeInAppContactPicker
        )
    }

    // Phase 7: Merge Duplicate Parties Modal
    if (uiState.showMergeDuplicatesModal) {
        MergeDuplicatesModal(
            duplicatePairs = uiState.duplicatePartyPairs,
            onMergePair = { pair ->
                viewModel.mergeDuplicatePair(pair)
                Toast.makeText(
                    context,
                    "Merged ${pair.duplicateParty.name} into ${pair.primaryParty.name}.",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = viewModel::closeMergeDuplicatesModal
        )
    }

    // Phase 8: Onboarding Flow for First-Time Users
    if (uiState.showOnboardingModal) {
        OnboardingModal(
            onFinish = { pin, upiId, payeeName ->
                viewModel.completeOnboarding(pin, upiId, payeeName)
                Toast.makeText(context, "Welcome to Udhaar!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

private sealed class UdhaarNavDestination(val depth: Int) {
    data object Home : UdhaarNavDestination(0)
    data object PeopleDirectory : UdhaarNavDestination(1)
    data object PartyDetail : UdhaarNavDestination(2)
    data class RecordDetail(val transactionId: Long) : UdhaarNavDestination(3)
    data object AddEdit : UdhaarNavDestination(4)
    data object Settings : UdhaarNavDestination(4)
}
