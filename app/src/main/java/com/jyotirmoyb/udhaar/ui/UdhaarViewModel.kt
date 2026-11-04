package com.jyotirmoyb.udhaar.ui

import android.app.PendingIntent
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.udhaar.backup.DriveConnectionTestReport
import com.jyotirmoyb.udhaar.backup.DriveRemoteBackup
import com.jyotirmoyb.udhaar.backup.DriveResult
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.AutoBackupFrequency
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatMessageType
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.DateRangeFilter
import com.jyotirmoyb.udhaar.data.LedgerFilter
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.ReminderChannel
import com.jyotirmoyb.udhaar.data.TransactionSortOption
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.security.EncryptedStorageManager
import com.jyotirmoyb.udhaar.security.PinVerificationResult
import com.jyotirmoyb.udhaar.ui.state.AppStateHolder
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.ui.viewmodel.BackupUiState
import com.jyotirmoyb.udhaar.ui.viewmodel.BackupViewModel
import com.jyotirmoyb.udhaar.ui.viewmodel.ChatUiState
import com.jyotirmoyb.udhaar.ui.viewmodel.ChatViewModel
import com.jyotirmoyb.udhaar.ui.viewmodel.DashboardUiState
import com.jyotirmoyb.udhaar.ui.viewmodel.DashboardViewModel
import com.jyotirmoyb.udhaar.ui.viewmodel.PeopleUiState
import com.jyotirmoyb.udhaar.ui.viewmodel.PeopleViewModel
import com.jyotirmoyb.udhaar.ui.viewmodel.RecordUiState
import com.jyotirmoyb.udhaar.ui.viewmodel.RecordViewModel
import com.jyotirmoyb.udhaar.ui.viewmodel.SettingsViewModel
import com.jyotirmoyb.udhaar.ui.viewmodel.VerificationUiState
import com.jyotirmoyb.udhaar.ui.viewmodel.VerificationViewModel
import com.jyotirmoyb.udhaar.util.AppSigningInfo
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.DeviceContact
import com.jyotirmoyb.udhaar.util.ParsedLedgerBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

class UdhaarViewModel(
    private val repository: UdhaarRepository,
    private val appContext: Context?,
    val appStateHolder: AppStateHolder,
    val dashboardViewModel: DashboardViewModel,
    val recordViewModel: RecordViewModel,
    val peopleViewModel: PeopleViewModel,
    val verificationViewModel: VerificationViewModel,
    val chatViewModel: ChatViewModel,
    val backupViewModel: BackupViewModel,
    val settingsViewModel: SettingsViewModel
) : ViewModel() {

    constructor(
        repository: UdhaarRepository,
        appContext: Context? = null
    ) : this(
        repository = repository,
        appContext = appContext,
        stateHolder = AppStateHolder().apply { initSecurityState(appContext) }
    )

    private constructor(
        repository: UdhaarRepository,
        appContext: Context?,
        stateHolder: AppStateHolder
    ) : this(
        repository = repository,
        appContext = appContext,
        appStateHolder = stateHolder,
        dashboardViewModel = DashboardViewModel(repository.ledgerRepository, repository.settingsRepository, stateHolder),
        recordViewModel = RecordViewModel(
            repository.ledgerRepository,
            repository.paymentRepository,
            repository.attachmentRepository,
            repository.settingsRepository,
            stateHolder,
            appContext
        ),
        peopleViewModel = PeopleViewModel(
            repository.partyRepository,
            repository.ledgerRepository,
            repository.settingsRepository,
            stateHolder,
            appContext
        ),
        verificationViewModel = VerificationViewModel(
            repository.verificationRepository,
            repository.paymentRepository,
            repository.settingsRepository,
            stateHolder
        ),
        chatViewModel = ChatViewModel(repository.chatRepository, repository.ledgerRepository, stateHolder, appContext),
        backupViewModel = BackupViewModel(repository.backupRepository, stateHolder, appContext),
        settingsViewModel = SettingsViewModel(repository.settingsRepository, stateHolder, appContext)
    )

    init {
        appStateHolder.initSecurityState(appContext)
        viewModelScope.launch {
            EncryptedStorageManager.encryptionStateFlow.collect { encState ->
                appStateHolder.setDbEncryptionState(encState)
            }
        }
        viewModelScope.launch {
            EncryptedStorageManager.blockingStartupErrorFlow.collect { err ->
                appStateHolder.setBlockingSqlCipherError(err)
            }
        }
    }

    private data class SharedNavBundle(
        val activeScreen: ActiveScreen,
        val summaryCurrency: CurrencyType?,
        val selectedPartyIdForSheet: Long?,
        val editingTransaction: UdhaarTransaction?,
        val preselectedPartyDraft: PreselectedPartyDraft?,
        val isAmountsHidden: Boolean,
        val undoSnackbarMessage: String?,
        val successAnimationEvent: com.jyotirmoyb.udhaar.ui.components.SuccessAnimationEvent?,
        val hasUnlockedThisSession: Boolean,
        val manualLockRequested: Boolean,
        val dbEncryptionState: EncryptedStorageManager.DbEncryptionState,
        val blockingSqlCipherError: String?
    )

    private val sharedNavFlow = combine(
        appStateHolder.activeScreen,
        appStateHolder.summaryCurrency,
        appStateHolder.selectedPartyIdForSheet,
        appStateHolder.editingTransaction,
        appStateHolder.preselectedPartyDraft
    ) { activeScreen, summaryCurrency, partySheetId, editTx, draft ->
        NavFirstHalf(activeScreen, summaryCurrency, partySheetId, editTx, draft)
    }

    private val sharedNavSecondFlow = combine(
        appStateHolder.isAmountsHidden,
        appStateHolder.undoSnackbarMessage,
        appStateHolder.successAnimationEvent,
        appStateHolder.hasUnlockedThisSession,
        combine(
            appStateHolder.manualLockRequested,
            appStateHolder.dbEncryptionState,
            appStateHolder.blockingSqlCipherError
        ) { lockReq, encState, sqlErr ->
            Triple(lockReq, encState, sqlErr)
        }
    ) { amountsHidden, undoMsg, animEvent, unlocked, triple ->
        NavSecondHalf(amountsHidden, undoMsg, animEvent, unlocked, triple.first, triple.second, triple.third)
    }

    private data class NavFirstHalf(
        val activeScreen: ActiveScreen,
        val summaryCurrency: CurrencyType?,
        val selectedPartyIdForSheet: Long?,
        val editingTransaction: UdhaarTransaction?,
        val preselectedPartyDraft: PreselectedPartyDraft?
    )

    private data class NavSecondHalf(
        val isAmountsHidden: Boolean,
        val undoSnackbarMessage: String?,
        val successAnimationEvent: com.jyotirmoyb.udhaar.ui.components.SuccessAnimationEvent?,
        val hasUnlockedThisSession: Boolean,
        val manualLockRequested: Boolean,
        val dbEncryptionState: EncryptedStorageManager.DbEncryptionState,
        val blockingSqlCipherError: String?
    )

    private data class CoreDomainBundle(
        val dState: DashboardUiState,
        val rState: RecordUiState,
        val pState: PeopleUiState
    )

    private data class CommsDomainBundle(
        val vState: VerificationUiState,
        val cState: ChatUiState,
        val bState: BackupUiState
    )

    private val coreDomainFlow = combine(
        dashboardViewModel.uiState,
        recordViewModel.uiState,
        peopleViewModel.uiState
    ) { d, r, p -> CoreDomainBundle(d, r, p) }

    private val commsDomainFlow = combine(
        verificationViewModel.uiState,
        chatViewModel.uiState,
        backupViewModel.uiState
    ) { v, c, b -> CommsDomainBundle(v, c, b) }

    val uiState: StateFlow<UdhaarUiState> = combine(
        coreDomainFlow,
        commsDomainFlow,
        settingsViewModel.uiState,
        sharedNavFlow,
        sharedNavSecondFlow
    ) { core, comms, sState, nav1, nav2 ->
        val dState = core.dState
        val rState = core.rState
        val pState = core.pState
        val vState = comms.vState
        val cState = comms.cState
        val bState = comms.bState
        val enabledCurrencySet = sState.userSettings.enabledCurrencies.toSet()
        val rawSummaryCurrency = nav1.summaryCurrency ?: sState.userSettings.defaultCurrency
        val effectiveSummaryCurrency = if (rawSummaryCurrency in enabledCurrencySet) {
            rawSummaryCurrency
        } else {
            sState.userSettings.enabledCurrencies.firstOrNull() ?: CurrencyType.INR
        }

        val requiresAuth = (sState.userSettings.biometricLockEnabled && !nav2.hasUnlockedThisSession) || nav2.manualLockRequested
        val unlocked = !requiresAuth

        UdhaarUiState(
            isAuthenticated = unlocked,
            activeScreen = nav1.activeScreen,
            summaryCurrency = effectiveSummaryCurrency,
            inrTotals = dState.inrTotals,
            nprTotals = dState.nprTotals,
            selectedFilter = dState.selectedFilter,
            searchQuery = dState.searchQuery,
            peopleSearchQuery = pState.peopleSearchQuery,
            allCalculatedItems = dState.allCalculatedItems,
            filteredItems = dState.filteredItems,
            allParties = pState.allParties,
            partyKhataList = pState.partyKhataList,
            filteredPartyKhataList = pState.filteredPartyKhataList,
            partialPaymentsByTx = rState.partialPaymentsByTx,
            auditTrailByTx = emptyMap(),
            userSettings = sState.userSettings,
            editingTransaction = nav1.editingTransaction,
            preselectedPartyDraft = nav1.preselectedPartyDraft,
            detailTransactionId = rState.detailTransactionId,
            whatsAppPreviewTransactionId = rState.whatsAppPreviewTransactionId,
            qrModalState = sState.qrModalState,
            showSettingsModal = sState.showSettingsModal,
            showShareAppModal = sState.showShareAppModal,
            selectedPartyIdForSheet = nav1.selectedPartyIdForSheet,
            editingParty = pState.editingParty,
            showAddEditPartyModal = pState.showAddEditPartyModal,
            receiptModalTransactionId = rState.receiptModalTransactionId,
            receiptModalPartyId = pState.receiptModalPartyId,
            verificationShareTransactionId = vState.verificationShareTransactionId,
            verificationShareLink = vState.verificationShareLink,
            incomingVerificationPayload = vState.incomingVerificationPayload,
            incomingPaymentProfilePayload = vState.incomingPaymentProfilePayload,
            incomingPaymentProfileTargetPartyId = vState.incomingPaymentProfileTargetPartyId,
            showShareMyPaymentDetailsModal = vState.showShareMyPaymentDetailsModal,
            shareMyPaymentDetailsTargetParty = vState.shareMyPaymentDetailsTargetParty,
            requestPaymentDetailsParty = vState.requestPaymentDetailsParty,
            chatThreadsByKey = cState.chatThreadsByKey,
            chatMessagesByThreadKey = cState.chatMessagesByThreadKey,
            unreadCountByTx = cState.unreadCountByTx,
            unreadCountByPartyName = cState.unreadCountByPartyName,
            activeChatThreadKey = cState.activeChatThreadKey,
            activeChatScope = cState.activeChatScope,
            activeChatTransactionId = cState.activeChatTransactionId,
            activeChatPartyId = cState.activeChatPartyId,
            activeChatPartyName = cState.activeChatPartyName,
            driveRemoteBackups = bState.driveRemoteBackups,
            isDriveOperationInProgress = bState.isDriveOperationInProgress,
            driveOperationStatusMessage = bState.driveOperationStatusMessage,
            driveOperationError = bState.driveOperationError,
            driveResolutionPendingIntent = bState.driveResolutionPendingIntent,
            driveConnectionTestReport = bState.driveConnectionTestReport,
            pendingRestoreBackup = bState.pendingRestoreBackup,
            pendingRestoreParsed = bState.pendingRestoreParsed,
            showRestorePreviewDialog = bState.showRestorePreviewDialog,
            showBackupPassphraseDialog = bState.showBackupPassphraseDialog,
            showRestorePassphraseDialog = bState.showRestorePassphraseDialog,
            restoreSelectedBackup = bState.restoreSelectedBackup,
            attachmentsByTx = rState.attachmentsByTx,
            attachmentsByPayment = rState.attachmentsByPayment,
            activeViewerAttachment = rState.activeViewerAttachment,
            draftAttachments = rState.draftAttachments,
            partySortOption = pState.partySortOption,
            duplicatePartyPairs = pState.duplicatePartyPairs,
            showMergeDuplicatesModal = pState.showMergeDuplicatesModal,
            showInAppContactPicker = pState.showInAppContactPicker,
            deviceContacts = pState.deviceContacts,
            isLoadingContacts = pState.isLoadingContacts,
            transactionSortOption = dState.transactionSortOption,
            dateRangeFilter = dState.dateRangeFilter,
            showOnboardingModal = sState.showOnboardingModal,
            isInitialLoading = dState.isInitialLoading,
            isAmountsHidden = nav2.isAmountsHidden,
            showCoachMarks = dState.showCoachMarks,
            isNeedsAttentionDismissed = dState.isNeedsAttentionDismissed,
            undoSnackbarMessage = nav2.undoSnackbarMessage,
            successAnimationEvent = nav2.successAnimationEvent,
            dbEncryptionState = nav2.dbEncryptionState,
            blockingSqlCipherError = nav2.blockingSqlCipherError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UdhaarUiState(isInitialLoading = true)
    )

    fun retryDatabaseEncryption(onComplete: ((Boolean) -> Unit)? = null) {
        val ctx = appContext
        if (ctx == null) {
            onComplete?.invoke(false)
            return
        }
        viewModelScope.launch {
            val ok = com.jyotirmoyb.udhaar.data.UdhaarDatabase.retryDatabaseEncryption(ctx)
            val newState = EncryptedStorageManager.getDatabaseEncryptionState(ctx)
            val err = EncryptedStorageManager.blockingStartupErrorFlow.value
            appStateHolder.setDbEncryptionState(newState)
            appStateHolder.setBlockingSqlCipherError(err)
            onComplete?.invoke(ok)
        }
    }

    fun markAuthenticated() = settingsViewModel.markAuthenticated()
    fun verifyPin(candidatePin: String): PinVerificationResult = settingsViewModel.verifyPin(candidatePin)
    fun getPinLockoutRemainingMillis(): Long = settingsViewModel.getPinLockoutRemainingMillis()
    fun getPinFailedAttempts(): Int = settingsViewModel.getPinFailedAttempts()
    fun lockApplicationNow() = settingsViewModel.lockApplicationNow()

    fun switchMainTab(screen: ActiveScreen) = appStateHolder.switchMainTab(screen)
    fun setSummaryCurrency(currency: CurrencyType) = settingsViewModel.setSummaryCurrency(currency)
    fun toggleCurrencyEnabled(currency: CurrencyType, enabled: Boolean) = settingsViewModel.toggleCurrencyEnabled(currency, enabled)
    fun cycleOrSelectTheme(themeMode: AppThemeMode) = settingsViewModel.cycleOrSelectTheme(themeMode)
    fun selectFontScale(fontScale: AppFontScale) = settingsViewModel.selectFontScale(fontScale)
    fun setFilter(filter: LedgerFilter) = dashboardViewModel.setFilter(filter)
    fun setSearchQuery(query: String) = dashboardViewModel.setSearchQuery(query)
    fun setPeopleSearchQuery(query: String) = peopleViewModel.setPeopleSearchQuery(query)
    fun setTransactionSortOption(option: TransactionSortOption) = dashboardViewModel.setTransactionSortOption(option)
    fun setDateRangeFilter(filter: DateRangeFilter) = dashboardViewModel.setDateRangeFilter(filter)
    fun completeOnboarding(pin: String, upiId: String, name: String) = settingsViewModel.completeOnboarding(pin, upiId, name)
    fun dismissCoachMarks() = dashboardViewModel.dismissCoachMarks()
    fun toggleHideAmounts() = settingsViewModel.toggleHideAmounts()
    fun dismissNeedsAttentionStrip() = dashboardViewModel.dismissNeedsAttentionStrip()
    fun dismissUndoSnackbar() = appStateHolder.dismissUndoSnackbar()
    fun clearSuccessAnimation() = appStateHolder.clearSuccessAnimation()
    fun performUndoAction() = dashboardViewModel.performUndoAction()

    fun generatePartyStatementPdf(khata: PartyKhataSummary, onReady: (File) -> Unit) = peopleViewModel.generatePartyStatementPdf(khata, onReady)
    fun generateFullLedgerPdf(onReady: (File) -> Unit) = peopleViewModel.generateFullLedgerPdf(onReady)

    fun openAddTransaction() = appStateHolder.openAddTransaction()
    fun openAddTransactionForParty(party: PartyEntity) = appStateHolder.openAddTransactionForParty(party)
    fun openEditTransaction(transaction: UdhaarTransaction) = appStateHolder.openEditTransaction(transaction)
    fun navigateBackToDashboard() = appStateHolder.navigateBackToDashboard()

    fun openPartyLedgerScreen(partyId: Long) = appStateHolder.openPartyLedgerScreen(partyId)
    fun closePartyLedgerScreen() = appStateHolder.closePartyLedgerScreen()

    fun openAddPartyModal() = peopleViewModel.openAddPartyModal()
    fun openEditPartyModal(party: PartyEntity) = peopleViewModel.openEditPartyModal(party)
    fun closeAddEditPartyModal() = peopleViewModel.closeAddEditPartyModal()

    fun setPartySortOption(option: PartySortOption) = peopleViewModel.setPartySortOption(option)
    fun togglePartyFavorite(party: PartyEntity) = peopleViewModel.togglePartyFavorite(party)
    fun openMergeDuplicatesModal() = peopleViewModel.openMergeDuplicatesModal()
    fun closeMergeDuplicatesModal() = peopleViewModel.closeMergeDuplicatesModal()
    fun mergeDuplicatePair(pair: DuplicatePartyPair) = peopleViewModel.mergeDuplicatePair(pair)

    fun openInAppContactPicker() = peopleViewModel.openInAppContactPicker()
    fun closeInAppContactPicker() = peopleViewModel.closeInAppContactPicker()
    fun loadDeviceContacts() = peopleViewModel.loadDeviceContacts()
    fun importSelectedContacts(selectedContacts: List<DeviceContact>) = peopleViewModel.importSelectedContacts(selectedContacts)

    fun openReceiptModal(transactionId: Long) = recordViewModel.openReceiptModal(transactionId)
    fun openTransactionReceiptModal(transactionId: Long) = openReceiptModal(transactionId)
    fun openReceiptModalForParty(partyId: Long) = peopleViewModel.openReceiptModalForParty(partyId)
    fun openPartyStatementReceiptModal(partyId: Long) = openReceiptModalForParty(partyId)
    fun closeReceiptModal() {
        recordViewModel.closeReceiptModal()
        peopleViewModel.closeReceiptModal()
    }

    fun openTransactionDetail(transactionId: Long) = appStateHolder.openTransactionDetail(transactionId)
    fun closeTransactionDetail() = appStateHolder.closeTransactionDetail()

    fun openWhatsAppReminderModal(transactionId: Long) = recordViewModel.openWhatsAppReminderModal(transactionId)
    fun closeWhatsAppReminderModal() = recordViewModel.closeWhatsAppReminderModal()

    fun recordReminderSent(recordId: Long, level: Int, channel: ReminderChannel) =
        recordViewModel.recordReminderSent(recordId, level, channel)

    fun snoozeTransaction(recordId: Long, days: Int) = recordViewModel.snoozeTransaction(recordId, days)
    fun clearTransactionSnooze(recordId: Long) = recordViewModel.clearTransactionSnooze(recordId)
    fun stopTransactionReminders(recordId: Long) = recordViewModel.stopTransactionReminders(recordId)
    fun enableTransactionReminders(recordId: Long) = recordViewModel.enableTransactionReminders(recordId)

    fun updatePartyQuietHoursOverride(partyId: Long, override: Boolean) {
        viewModelScope.launch {
            repository.updatePartyQuietHoursOverride(partyId, override)
        }
    }

    fun updateReminderSettings(
        quietHoursEnabled: Boolean,
        quietHoursStartHour: Int,
        quietHoursEndHour: Int,
        reminderLevel1DaysBefore: Int,
        reminderLevel2MaxOverdueDays: Int,
        reminderLevel3MaxOverdueDays: Int
    ) = settingsViewModel.updateReminderSettings(
        quietHoursEnabled, quietHoursStartHour, quietHoursEndHour,
        reminderLevel1DaysBefore, reminderLevel2MaxOverdueDays, reminderLevel3MaxOverdueDays
    )

    fun openGlobalQrModal() = settingsViewModel.openGlobalQrModal()
    fun openTransactionQrModal(summary: CalculatedUdhaarSummary) = settingsViewModel.openTransactionQrModal(summary)
    fun openPayToPartyQrModal(partyName: String, upiId: String, amountInr: Double? = null) =
        settingsViewModel.openPayToPartyQrModal(partyName, upiId, amountInr)
    fun openPayPartyViaUpiModal(partyName: String, partyUpiId: String, amountInr: Double? = null) =
        settingsViewModel.openPayToPartyQrModal(partyName, partyUpiId, amountInr)
    fun closeQrModal() = settingsViewModel.closeQrModal()

    fun openSettingsModal() = settingsViewModel.openSettingsModal()
    fun closeSettingsModal() = settingsViewModel.closeSettingsModal()
    fun openShareAppModal() = settingsViewModel.openShareAppModal()
    fun closeShareAppModal() = settingsViewModel.closeShareAppModal()

    fun requestVerificationForTransaction(transaction: UdhaarTransaction) =
        verificationViewModel.requestVerificationForTransaction(transaction)
    fun closeVerificationShareModal() = verificationViewModel.closeVerificationShareModal()

    fun handleIncomingDeepLinkOrToken(urlOrToken: String, targetPartyId: Long? = null): Boolean =
        verificationViewModel.handleIncomingDeepLinkOrToken(urlOrToken, targetPartyId)

    fun confirmOrDisputeIncomingVerification(confirm: Boolean, disputeReason: String = "") =
        verificationViewModel.confirmOrDisputeIncomingVerification(confirm, disputeReason)

    fun dismissIncomingVerification() = verificationViewModel.dismissIncomingVerification()

    fun confirmOrDisputeExistingRecord(transaction: UdhaarTransaction, confirm: Boolean, disputeReason: String = "") =
        verificationViewModel.confirmOrDisputeExistingRecord(transaction, confirm, disputeReason)

    fun acknowledgePartialPayment(transaction: UdhaarTransaction, payment: PartialPayment) {
        verificationViewModel.acknowledgePartialPayment(
            transaction,
            payment,
            uiState.value.userSettings.payeeName.ifBlank { "You" }
        )
    }

    fun openShareMyPaymentDetailsModal(targetParty: PartyEntity? = null) =
        verificationViewModel.openShareMyPaymentDetailsModal(targetParty)
    fun closeShareMyPaymentDetailsModal() = verificationViewModel.closeShareMyPaymentDetailsModal()
    fun openRequestPartyPaymentDetailsModal(party: PartyEntity) =
        verificationViewModel.openRequestPartyPaymentDetailsModal(party)
    fun closeRequestPartyPaymentDetailsModal() = verificationViewModel.closeRequestPartyPaymentDetailsModal()
    fun acceptIncomingPaymentProfile() = verificationViewModel.acceptIncomingPaymentProfile()
    fun dismissIncomingPaymentProfile() = verificationViewModel.dismissIncomingPaymentProfile()

    fun setCloudAccountState(consentGranted: Boolean, userId: String, email: String) =
        verificationViewModel.setCloudAccountState(consentGranted, userId, email)

    fun saveParty(party: PartyEntity) = peopleViewModel.saveParty(party)
    fun deleteParty(party: PartyEntity) = peopleViewModel.deleteParty(party)

    fun saveTransaction(transaction: UdhaarTransaction, attachments: List<Attachment> = emptyList()) =
        recordViewModel.saveTransaction(transaction, attachments)
    fun saveUdhaarTransaction(transaction: UdhaarTransaction) = saveTransaction(transaction)

    fun retryCloudBackup(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.syncProfileAndRecordsToCloud()
            onComplete?.invoke(res.isSuccess)
        }
    }

    fun deleteMyCloudData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteMyCloudData()
            onComplete()
        }
    }

    fun deleteTransaction(transaction: UdhaarTransaction) = recordViewModel.deleteTransaction(transaction)

    fun recordPartialPayment(
        summary: CalculatedUdhaarSummary,
        amount: Double,
        paymentMode: PaymentMode = PaymentMode.CASH,
        note: String,
        recordedByCounterparty: Boolean = false,
        proofAttachment: Attachment? = null
    ) = recordViewModel.recordPartialPayment(summary, amount, paymentMode, note, recordedByCounterparty, proofAttachment)

    fun deletePartialPayment(summary: CalculatedUdhaarSummary, payment: PartialPayment) =
        recordViewModel.deletePartialPayment(summary, payment)

    fun openAttachmentViewer(attachment: Attachment) = recordViewModel.openAttachmentViewer(attachment)
    fun closeAttachmentViewer() = recordViewModel.closeAttachmentViewer()
    fun stageDraftAttachment(attachment: Attachment) = recordViewModel.stageDraftAttachment(attachment)
    fun removeDraftAttachment(attachment: Attachment) = recordViewModel.removeDraftAttachment(attachment)
    fun clearDraftAttachments() = recordViewModel.clearDraftAttachments()
    fun addAttachmentToTransaction(transactionId: Long, attachment: Attachment) =
        recordViewModel.addAttachmentToTransaction(transactionId, attachment)
    fun addAttachmentToPayment(transactionId: Long, paymentId: Long, attachment: Attachment) =
        recordViewModel.addAttachmentToPayment(transactionId, paymentId, attachment)
    fun deleteAttachment(attachment: Attachment) = recordViewModel.deleteAttachment(attachment)

    fun toggleSettled(summary: CalculatedUdhaarSummary) = recordViewModel.toggleSettled(summary)

    fun saveUserSettings(
        upiId: String,
        payeeName: String,
        defaultCurrency: CurrencyType,
        inrEnabled: Boolean? = null,
        nprEnabled: Boolean? = null,
        themeMode: AppThemeMode? = null,
        fontScale: AppFontScale? = null,
        biometricLockEnabled: Boolean? = null,
        securityPin: String? = null,
        phoneNumber: String? = null,
        profilePhotoUri: String? = null,
        cloudVerificationConsent: Boolean? = null,
        closeModal: Boolean = true
    ) = settingsViewModel.saveUserSettings(
        upiId, payeeName, defaultCurrency, inrEnabled, nprEnabled, themeMode,
        fontScale, biometricLockEnabled, securityPin, phoneNumber, profilePhotoUri,
        cloudVerificationConsent, closeModal
    )

    fun openTransactionChat(transactionId: Long) {
        val tx = uiState.value.allCalculatedItems.firstOrNull { it.transaction.id == transactionId }?.transaction
        chatViewModel.openTransactionChat(
            transactionId = transactionId,
            partyName = tx?.partyName.orEmpty(),
            partyId = tx?.partyId,
            remoteId = tx?.remoteId.orEmpty()
        )
    }

    fun openPartyGeneralChat(party: PartyEntity) = chatViewModel.openPartyGeneralChat(party)

    fun openChatFromNotification(
        threadKey: String,
        transactionId: Long?,
        partyName: String,
        remoteRecordId: String = ""
    ) = chatViewModel.openChatFromNotification(threadKey, transactionId, partyName, remoteRecordId)

    fun closeChatSheet() = chatViewModel.closeChatSheet()

    fun sendChatMessage(
        threadKey: String,
        scope: ChatThreadScope,
        transaction: UdhaarTransaction? = null,
        partyId: Long? = null,
        partyName: String,
        messageType: ChatMessageType = ChatMessageType.TEXT,
        textContent: String,
        attachmentUri: String = "",
        attachmentMimeType: String = "",
        fromCounterparty: Boolean = false
    ) = chatViewModel.sendChatMessage(
        threadKey, scope, transaction, partyId, partyName, messageType,
        textContent, attachmentUri, attachmentMimeType, fromCounterparty
    )

    fun requestDueDateExtension(
        transaction: UdhaarTransaction,
        proposedDueDateMillis: Long,
        note: String = "",
        requestedByCounterparty: Boolean = false
    ) = chatViewModel.requestDueDateExtension(transaction, proposedDueDateMillis, note, requestedByCounterparty)

    fun respondToExtensionRequest(
        transaction: UdhaarTransaction,
        message: ChatMessageEntity,
        accept: Boolean
    ) = chatViewModel.respondToExtensionRequest(transaction, message, accept)

    fun markThreadRead(threadKey: String) = chatViewModel.markThreadRead(threadKey)

    fun setTypingIndicator(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String,
        isCounterpartyTyping: Boolean
    ) = chatViewModel.setTypingIndicator(threadKey, scope, transactionId, remoteId, partyId, partyName, isCounterpartyTyping)

    fun toggleThreadMute(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String
    ) = chatViewModel.toggleThreadMute(threadKey, scope, transactionId, remoteId, partyId, partyName)

    fun toggleThreadBlock(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String
    ) = chatViewModel.toggleThreadBlock(threadKey, scope, transactionId, remoteId, partyId, partyName)

    fun reportAbusiveUserInThread(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String,
        reason: String
    ) = chatViewModel.reportAbusiveUserInThread(threadKey, scope, transactionId, remoteId, partyId, partyName, reason)

    fun generateFullLedgerBackupJson(onReady: (String) -> Unit) =
        backupViewModel.generateFullLedgerBackupJson(onReady)

    fun generateLocalEncryptedUdhaarBackup(passphrase: String, onReady: (Result<ByteArray>) -> Unit) =
        backupViewModel.generateLocalEncryptedUdhaarBackup(passphrase, onReady)

    fun generateSinglePartyStatementJson(khata: PartyKhataSummary, onReady: (String) -> Unit) =
        backupViewModel.generateSinglePartyStatementJson(khata, onReady)

    fun importLedgerBackupJson(rawJson: String, onComplete: (Result<Int>) -> Unit) =
        backupViewModel.importLedgerBackupJson(rawJson, onComplete)

    fun setDriveAccountState(initialEmail: String = "") = backupViewModel.setDriveAccountState(initialEmail)
    fun handleDriveNeedsResolution(pendingIntent: PendingIntent?, message: String) =
        backupViewModel.handleDriveNeedsResolution(pendingIntent, message)
    fun clearDriveAccountState() = backupViewModel.clearDriveAccountState()

    fun openBackupPassphraseDialog() = backupViewModel.openBackupPassphraseDialog()
    fun closeBackupPassphraseDialog() = backupViewModel.closeBackupPassphraseDialog()
    fun openRestorePassphraseDialog(backup: DriveRemoteBackup) = backupViewModel.openRestorePassphraseDialog(backup)
    fun closeRestorePassphraseDialog() = backupViewModel.closeRestorePassphraseDialog()
    fun closeRestorePreviewDialog() = backupViewModel.closeRestorePreviewDialog()

    fun clearDriveOperationState() = backupViewModel.clearDriveOperationState()
    fun clearDriveOperationError() = backupViewModel.clearDriveOperationError()
    fun refreshDriveBackups() = backupViewModel.refreshDriveBackups()
    fun runDriveConnectionTest() = backupViewModel.runDriveConnectionTest()
    fun clearDriveConnectionTestReport() = backupViewModel.clearDriveConnectionTestReport()
    fun getAppSigningInfo(): AppSigningInfo = backupViewModel.getAppSigningInfo()

    fun deleteAllDriveBackups(onComplete: ((Result<Int>) -> Unit)? = null) =
        backupViewModel.deleteAllDriveBackups(onComplete)

    fun uploadDriveBackup(
        passphrase: String,
        savePassphraseForAutoBackup: Boolean = false,
        onComplete: ((Result<DriveRemoteBackup>) -> Unit)? = null
    ) = backupViewModel.uploadDriveBackup(passphrase, savePassphraseForAutoBackup, onComplete)

    fun downloadAndDecryptDriveBackup(
        backup: DriveRemoteBackup,
        passphrase: String,
        onPreviewReady: ((Result<ParsedLedgerBackup>) -> Unit)? = null
    ) = backupViewModel.downloadAndDecryptDriveBackup(backup, passphrase, onPreviewReady)

    fun executeDriveRestore(replaceExisting: Boolean, onComplete: ((Result<Int>) -> Unit)? = null) =
        backupViewModel.executeDriveRestore(replaceExisting, onComplete)

    fun deleteDriveBackup(backup: DriveRemoteBackup) = backupViewModel.deleteDriveBackup(backup)

    fun updateDriveAutoBackupSettings(
        frequency: AutoBackupFrequency,
        wifiOnly: Boolean,
        passphraseToSave: String? = null
    ) = backupViewModel.updateDriveAutoBackupSettings(frequency, wifiOnly, passphraseToSave)

    class Factory(
        private val repository: UdhaarRepository,
        private val appContext: Context? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UdhaarViewModel(repository, appContext) as T
        }
    }
}
