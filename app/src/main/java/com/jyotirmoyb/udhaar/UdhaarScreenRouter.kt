package com.jyotirmoyb.udhaar

import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.ui.ActiveScreen
import com.jyotirmoyb.udhaar.ui.UdhaarUiState
import com.jyotirmoyb.udhaar.ui.UdhaarViewModel
import com.jyotirmoyb.udhaar.ui.components.AddEditUdhaarScreen
import com.jyotirmoyb.udhaar.ui.components.BiometricSecurityScreen
import com.jyotirmoyb.udhaar.ui.components.DashboardScreen
import com.jyotirmoyb.udhaar.ui.components.LocalAnimatedVisibilityScope
import com.jyotirmoyb.udhaar.ui.components.LocalSharedTransitionScope
import com.jyotirmoyb.udhaar.ui.components.PartyDetailScreen
import com.jyotirmoyb.udhaar.ui.components.ProfileSettingsModal
import com.jyotirmoyb.udhaar.ui.components.SqlCipherBlockingErrorScreen
import com.jyotirmoyb.udhaar.ui.components.SuccessCheckmarkOverlay
import com.jyotirmoyb.udhaar.ui.components.TransactionDetailModal
import com.jyotirmoyb.udhaar.ui.components.rememberReducedMotionEnabled
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.util.LedgerBackupManager
import com.jyotirmoyb.udhaar.util.PdfStatementGenerator
import com.jyotirmoyb.udhaar.util.TelemetryManager
import kotlinx.coroutines.CancellationException

internal sealed class UdhaarNavDestination(val depth: Int, val screenAnalyticsId: String) {
    data object Home : UdhaarNavDestination(0, "dashboard")
    data object PeopleDirectory : UdhaarNavDestination(1, "people_directory")
    data object PartyDetail : UdhaarNavDestination(2, "party_detail")
    data class RecordDetail(val transactionId: Long) : UdhaarNavDestination(3, "record_detail")
    data object AddEdit : UdhaarNavDestination(4, "add_edit_udhaar")
    data object Settings : UdhaarNavDestination(4, "settings")
}

@Composable
internal fun UdhaarScreenRouter(
    viewModel: UdhaarViewModel,
    uiState: UdhaarUiState,
    isPlanProposalMode: Boolean,
    onPlanProposalModeChange: (Boolean) -> Unit,
    requestContactsFlow: () -> Unit,
    signInWithGoogleForVerification: () -> Unit,
    signInOrReconnectGoogleDrive: () -> Unit,
    signOutFromDrive: () -> Unit,
    onLaunchSaveJsonDocument: (fileName: String, jsonContent: String) -> Unit,
    onLaunchOpenDocument: () -> Unit
) {
    val context = LocalContext.current

    val blockingSqlCipherError = uiState.blockingSqlCipherError
    if (blockingSqlCipherError != null) {
        SqlCipherBlockingErrorScreen(
            errorMessage = blockingSqlCipherError,
            onExportBackup = {
                viewModel.generateFullLedgerBackupJson { json ->
                    onLaunchSaveJsonDocument("udhaar_emergency_backup.json", json)
                }
            },
            onTryAgain = {
                viewModel.retryDatabaseEncryption { ok ->
                    val msg = if (ok) "Encrypted storage initialized." else "SQLCipher initialization failed."
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
        return
    }

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
            uiState.activeScreen == ActiveScreen.ADD_EDIT -> UdhaarNavDestination.AddEdit
            uiState.activeScreen == ActiveScreen.SETTINGS -> UdhaarNavDestination.Settings
            detailId != null -> UdhaarNavDestination.RecordDetail(detailId)
            uiState.activeScreen == ActiveScreen.PARTY_DETAIL -> UdhaarNavDestination.PartyDetail
            uiState.activeScreen == ActiveScreen.PEOPLE_DIRECTORY -> UdhaarNavDestination.PeopleDirectory
            else -> UdhaarNavDestination.Home
        }
    }

    LaunchedEffect(currentNavDestination.screenAnalyticsId, uiState.userSettings.analyticsCrashConsent) {
        if (uiState.userSettings.analyticsCrashConsent) {
            TelemetryManager.logScreenView(currentNavDestination.screenAnalyticsId, context)
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
                                    onPartyClick = { khata -> viewModel.openPartyLedgerScreen(khata.party.partyId) },
                                    onQuickAddLoanForParty = viewModel::openAddTransactionForParty,
                                    onTransactionClick = { summary -> viewModel.openTransactionDetail(summary.transaction.id) },
                                    onWhatsAppClick = { summary -> viewModel.openWhatsAppReminderModal(summary.transaction.id) },
                                    onTransactionQrClick = viewModel::openTransactionQrModal,
                                    onOpenTransactionChat = { summary -> viewModel.openTransactionChat(summary.transaction.id) },
                                    onOpenPartyGeneralChat = viewModel::openPartyGeneralChat,
                                    onGlobalQrClick = viewModel::openGlobalQrModal,
                                    onShareMyPaymentDetailsClick = { viewModel.openShareMyPaymentDetailsModal(null) },
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
                                            PdfStatementGenerator.sharePdfFile(context, file, context.getString(R.string.pdf_full_ledger_title))
                                        }
                                    },
                                    onToggleHideAmounts = viewModel::toggleHideAmounts,
                                    onDismissNeedsAttention = viewModel::dismissNeedsAttentionStrip,
                                    onDismissCoachMarks = viewModel::dismissCoachMarks,
                                    onUndoAction = viewModel::performUndoAction,
                                    onDismissUndo = viewModel::dismissUndoSnackbar,
                                    onOpenUpdatesScreen = viewModel::openUpdatesScreen,
                                    onCreateGroupSplitTransactions = viewModel::saveGroupSplitTransactions
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
                                        onEditParty = { viewModel.openEditPartyModal(selectedKhata.party) },
                                        onDeleteParty = { viewModel.deleteParty(selectedKhata.party) },
                                        onAddLoanForParty = { viewModel.openAddTransactionForParty(selectedKhata.party) },
                                        onTransactionClick = { summary -> viewModel.openTransactionDetail(summary.transaction.id) },
                                        onWhatsAppClick = { summary -> viewModel.openWhatsAppReminderModal(summary.transaction.id) },
                                        onOpenTransactionChat = { summary -> viewModel.openTransactionChat(summary.transaction.id) },
                                        onOpenPartyGeneralChat = { viewModel.openPartyGeneralChat(selectedKhata.party) },
                                        unreadPartyChatCount = uiState.unreadCountByPartyName[selectedKhata.party.name.trim().lowercase()] ?: 0,
                                        unreadCountByTx = uiState.unreadCountByTx,
                                        onPayPartyViaUpi = { amountOwed ->
                                            viewModel.openPayPartyViaUpiModal(
                                                partyName = selectedKhata.party.name,
                                                partyUpiId = selectedKhata.party.upiId,
                                                amountInr = amountOwed
                                            )
                                        },
                                        onShareMyPaymentDetails = { viewModel.openShareMyPaymentDetailsModal(selectedKhata.party) },
                                        onRequestPartyPaymentDetails = { viewModel.openRequestPartyPaymentDetailsModal(selectedKhata.party) },
                                        onToggleFavorite = viewModel::togglePartyFavorite,
                                        onRequestVerification = {
                                            val firstTx = selectedKhata.transactions.firstOrNull()?.transaction
                                            if (firstTx != null) {
                                                viewModel.requestVerificationForTransaction(firstTx)
                                            } else {
                                                Toast.makeText(context, "No transactions to verify for this party yet.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onShareThermalReceipt = { viewModel.openPartyStatementReceiptModal(selectedKhata.party.partyId) },
                                        onExportPartyStatement = {
                                            viewModel.generateSinglePartyStatementJson(selectedKhata) { json ->
                                                val safeName = selectedKhata.party.name.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase()
                                                onLaunchSaveJsonDocument("udhaar_statement_${safeName}.json", json)
                                            }
                                        },
                                        onSharePartyStatement = {
                                            viewModel.generateSinglePartyStatementJson(selectedKhata) { json ->
                                                val safeName = selectedKhata.party.name.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase()
                                                LedgerBackupManager.shareBackupFile(context, json, "udhaar_statement_${safeName}")
                                            }
                                        },
                                        onExportPartyPdfStatement = {
                                            viewModel.generatePartyStatementPdf(selectedKhata) { file ->
                                                PdfStatementGenerator.sharePdfFile(
                                                    context = context,
                                                    file = file,
                                                    subject = "${context.getString(R.string.pdf_party_statement_title)} — ${selectedKhata.party.name}"
                                                )
                                            }
                                        }
                                    )
                                } else {
                                    LaunchedEffect(Unit) { viewModel.closePartyLedgerScreen() }
                                }
                            }
                            is UdhaarNavDestination.RecordDetail -> {
                                val detailTxId = navDestination.transactionId
                                val detailSummary = uiState.allCalculatedItems.find { it.transaction.id == detailTxId }
                                if (detailSummary != null) {
                                    val linkedParty = uiState.allParties.find { p ->
                                        (detailSummary.transaction.partyId != null && p.partyId == detailSummary.transaction.partyId) ||
                                            p.name.trim().equals(detailSummary.transaction.partyName.trim(), ignoreCase = true)
                                    }
                                    TransactionDetailModal(
                                        summary = detailSummary,
                                        partialPayments = uiState.partialPaymentsByTx[detailTxId].orEmpty(),
                                        auditTrail = uiState.auditTrailByTx[detailTxId].orEmpty(),
                                        linkedParty = linkedParty,
                                        attachments = uiState.attachmentsByTx[detailTxId].orEmpty(),
                                        attachmentsByPayment = uiState.attachmentsByPayment,
                                        installmentPlan = uiState.installmentPlansByTx[detailTxId],
                                        installments = uiState.installmentPlansByTx[detailTxId]?.let { uiState.installmentsByPlan[it.id] }.orEmpty(),
                                        lenderUpiId = if (detailSummary.transaction.transactionType == TransactionType.GIVEN) {
                                            uiState.userSettings.upiId
                                        } else {
                                            linkedParty?.upiId.orEmpty()
                                        },
                                        onOpenPlanBuilder = {
                                            onPlanProposalModeChange(false)
                                            viewModel.openInstallmentPlanBuilder(detailTxId)
                                        },
                                        onRequestNewDates = {
                                            onPlanProposalModeChange(true)
                                            viewModel.openInstallmentPlanBuilder(detailTxId)
                                        },
                                        onConfirmInstallmentUpiPayment = { inst, amt ->
                                            val totalCount = uiState.installmentPlansByTx[detailTxId]?.installmentCount ?: 1
                                            viewModel.recordPartialPayment(
                                                summary = detailSummary,
                                                amount = amt,
                                                paymentMode = PaymentMode.UPI,
                                                note = context.getString(R.string.upi_reference_note_fmt, inst.sequence, totalCount),
                                                recordedByCounterparty = false
                                            )
                                        },
                                        onMoveInstallmentPayment = { fromSeq, toSeq ->
                                            uiState.installmentPlansByTx[detailTxId]?.let { plan ->
                                                viewModel.moveInstallmentPayment(plan.id, fromSeq, toSeq)
                                            }
                                        },
                                        onAcceptProposedSchedule = {
                                            uiState.installmentPlansByTx[detailTxId]?.let { plan ->
                                                viewModel.acceptProposedInstallmentSchedule(plan.id, detailSummary.transaction)
                                            }
                                        },
                                        onDeclineProposedSchedule = {
                                            uiState.installmentPlansByTx[detailTxId]?.let { plan ->
                                                viewModel.declineProposedInstallmentSchedule(plan.id, detailTxId)
                                            }
                                        },
                                        onDismiss = viewModel::closeTransactionDetail,
                                        onRecordPartialPayment = { amount, paymentMode, note ->
                                            viewModel.recordPartialPayment(detailSummary, amount, paymentMode, note, recordedByCounterparty = false)
                                        },
                                        onRecordPartialPaymentWithProof = { amount, paymentMode, note, proofAttachment ->
                                            viewModel.recordPartialPayment(detailSummary, amount, paymentMode, note, false, proofAttachment)
                                        },
                                        onRecordCounterpartyPayment = { amount, paymentMode, note ->
                                            viewModel.recordPartialPayment(detailSummary, amount, paymentMode, note, recordedByCounterparty = true)
                                        },
                                        onAcknowledgePartialPayment = { payment -> viewModel.acknowledgePartialPayment(detailSummary.transaction, payment) },
                                        onDeletePartialPayment = { payment -> viewModel.deletePartialPayment(detailSummary, payment) },
                                        onAddAttachment = { att -> viewModel.addAttachmentToTransaction(detailTxId, att) },
                                        onDeleteAttachment = viewModel::deleteAttachment,
                                        onOpenAttachmentViewer = viewModel::openAttachmentViewer,
                                        onRequestVerification = { viewModel.requestVerificationForTransaction(detailSummary.transaction) },
                                        onConfirmOrDisputeRecord = { confirm, reason ->
                                            viewModel.confirmOrDisputeExistingRecord(detailSummary.transaction, confirm, reason)
                                        },
                                        onOpenWebShare = { viewModel.openWebShareModal(detailTxId) },
                                        onRevokeWebLink = { viewModel.revokeWebLink(detailTxId) },
                                        onConfirmPaymentClaim = { viewModel.confirmPaymentClaim(detailTxId) },
                                        onRejectPaymentClaim = { viewModel.rejectPaymentClaim(detailTxId) },
                                        onAcceptDateProposal = { viewModel.acceptDateProposal(detailTxId) },
                                        onDeclineDateProposal = { viewModel.declineDateProposal(detailTxId) },
                                        onToggleSettled = { viewModel.toggleSettled(detailSummary) },
                                        onEdit = { viewModel.openEditTransaction(detailSummary.transaction) },
                                        onDeleteTransaction = { viewModel.deleteTransaction(detailSummary.transaction) },
                                        onOpenQr = { viewModel.openTransactionQrModal(detailSummary) },
                                        onPayPartyViaUpi = viewModel::openPayPartyViaUpiModal,
                                        onShareReceipt = { viewModel.openTransactionReceiptModal(detailSummary.transaction.id) },
                                        onOpenWhatsApp = { viewModel.openWhatsAppReminderModal(detailSummary.transaction.id) },
                                        unreadChatCount = uiState.unreadCountByTx[detailSummary.transaction.id] ?: 0,
                                        onOpenTransactionChat = {
                                            viewModel.closeTransactionDetail()
                                            viewModel.openTransactionChat(detailSummary.transaction.id)
                                        }
                                    )
                                } else {
                                    LaunchedEffect(Unit) { viewModel.closeTransactionDetail() }
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
                                    onEnableCurrency = { curr -> viewModel.toggleCurrencyEnabled(curr, true) },
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
                                            upiId, payeeName, currency, inrEnabled, nprEnabled,
                                            themeMode, fontScale, bioEnabled, pin, closeModal = closeModal
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
                                    onShareMyPaymentDetails = { viewModel.openShareMyPaymentDetailsModal(null) },
                                    onOpenReceivedVerificationLink = { link ->
                                        if (!viewModel.handleIncomingDeepLinkOrToken(link)) {
                                            Toast.makeText(context, "Invalid verification or payment link.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onSignInWithGoogleForVerification = signInWithGoogleForVerification,
                                    onRetryCloudBackup = viewModel::retryCloudBackup,
                                    onDeleteMyCloudData = {
                                        viewModel.deleteMyCloudData {
                                            Toast.makeText(context, "Cloud verification data cleared. Local ledger preserved.", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    onLockAppNow = viewModel::lockApplicationNow,
                                    onOpenGlobalQrModal = viewModel::openGlobalQrModal,
                                    onOpenShareAppModal = viewModel::openShareAppModal,
                                    onExportFullLedgerSaf = {
                                        viewModel.generateFullLedgerBackupJson { json ->
                                            onLaunchSaveJsonDocument("udhaar_full_ledger_backup.json", json)
                                        }
                                    },
                                    onShareFullLedgerFile = {
                                        viewModel.generateFullLedgerBackupJson { json ->
                                            LedgerBackupManager.shareBackupFile(context, json, "udhaar_full_ledger_backup")
                                        }
                                    },
                                    onImportLedgerSaf = onLaunchOpenDocument,
                                    driveRemoteBackups = uiState.driveRemoteBackups,
                                    isDriveOperationInProgress = uiState.isDriveOperationInProgress,
                                    driveOperationStatusMessage = uiState.driveOperationStatusMessage,
                                    driveOperationError = uiState.driveOperationError,
                                    hasPendingDriveResolution = uiState.driveResolutionPendingIntent != null,
                                    driveConnectionTestReport = uiState.driveConnectionTestReport,
                                    appSigningInfo = viewModel.getAppSigningInfo(),
                                    onSignInWithGoogleForDrive = signInOrReconnectGoogleDrive,
                                    onReconnectGoogleDrive = signInOrReconnectGoogleDrive,
                                    onRunDriveConnectionTest = viewModel::runDriveConnectionTest,
                                    onClearDriveConnectionTestReport = viewModel::clearDriveConnectionTestReport,
                                    onSignOutFromDrive = signOutFromDrive,
                                    onBackupNowToDrive = viewModel::uploadDriveBackup,
                                    onRestoreDriveBackup = viewModel::downloadAndDecryptDriveBackup,
                                    onDeleteDriveBackup = viewModel::deleteDriveBackup,
                                    onDeleteAllDriveBackups = viewModel::deleteAllDriveBackups,
                                    onUpdateAutoBackupSettings = viewModel::updateDriveAutoBackupSettings,
                                    onRefreshDriveBackups = viewModel::refreshDriveBackups,
                                    onClearDriveOperationError = viewModel::clearDriveOperationError,
                                    dbEncryptionState = uiState.dbEncryptionState,
                                    onRetryDatabaseEncryption = {
                                        viewModel.retryDatabaseEncryption { ok ->
                                            val msg = if (ok) "Database encrypted successfully." else "Database encryption failed. Tap to retry."
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onExportLocalUdhaarSaf = { passphrase ->
                                        viewModel.generateLocalEncryptedUdhaarBackup(passphrase) { result ->
                                            result.onSuccess { encryptedBytes ->
                                                val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                                                onLaunchSaveJsonDocument("udhaar_backup_${dateStr}.udhaar", String(encryptedBytes, Charsets.UTF_8))
                                            }.onFailure {
                                                Toast.makeText(context, "Failed to encrypt .udhaar backup.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onOpenContactPicker = requestContactsFlow,
                                    onSaveReminderSettings = viewModel::updateReminderSettings,
                                    onAnalyticsCrashConsentChanged = viewModel::setAnalyticsCrashConsent,
                                    onGenerateRecoveryKey = viewModel::generateAndSaveBackupRecoveryKey,
                                    onSaveRecoveryKey = viewModel::saveBackupRecoveryKey,
                                    onTestMyBackup = viewModel::testMyBackup,
                                    onDismiss = viewModel::closeSettingsModal
                                )
                            }
                        }
                    }
                }
            }
        }

        SuccessCheckmarkOverlay(
            event = uiState.successAnimationEvent,
            onFinished = viewModel::clearSuccessAnimation
        )
    }
}
