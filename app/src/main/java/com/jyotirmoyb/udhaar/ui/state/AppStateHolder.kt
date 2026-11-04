package com.jyotirmoyb.udhaar.ui.state

import android.content.Context
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.security.EncryptedStorageManager
import com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState
import com.jyotirmoyb.udhaar.ui.ActiveScreen
import com.jyotirmoyb.udhaar.ui.PreselectedPartyDraft
import com.jyotirmoyb.udhaar.ui.components.SuccessAnimationEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared application UI state holder for cross-cutting state:
 * navigation tabs, detail/draft selections, global privacy toggle, undo snackbars, and auth state.
 */
@Singleton
class AppStateHolder @Inject constructor() {

    private val _activeScreen = MutableStateFlow(ActiveScreen.DASHBOARD)
    val activeScreen: StateFlow<ActiveScreen> = _activeScreen.asStateFlow()

    private val _previousScreenBeforeSettings = MutableStateFlow(ActiveScreen.DASHBOARD)
    val previousScreenBeforeSettings: StateFlow<ActiveScreen> = _previousScreenBeforeSettings.asStateFlow()

    private val _summaryCurrency = MutableStateFlow<CurrencyType?>(null)
    val summaryCurrency: StateFlow<CurrencyType?> = _summaryCurrency.asStateFlow()

    private val _selectedPartyIdForSheet = MutableStateFlow<Long?>(null)
    val selectedPartyIdForSheet: StateFlow<Long?> = _selectedPartyIdForSheet.asStateFlow()

    private val _detailTransactionId = MutableStateFlow<Long?>(null)
    val detailTransactionId: StateFlow<Long?> = _detailTransactionId.asStateFlow()

    private val _editingTransaction = MutableStateFlow<UdhaarTransaction?>(null)
    val editingTransaction: StateFlow<UdhaarTransaction?> = _editingTransaction.asStateFlow()

    private val _preselectedPartyDraft = MutableStateFlow<PreselectedPartyDraft?>(null)
    val preselectedPartyDraft: StateFlow<PreselectedPartyDraft?> = _preselectedPartyDraft.asStateFlow()

    private val _isAmountsHidden = MutableStateFlow(false)
    val isAmountsHidden: StateFlow<Boolean> = _isAmountsHidden.asStateFlow()

    private val _undoSnackbarMessage = MutableStateFlow<String?>(null)
    val undoSnackbarMessage: StateFlow<String?> = _undoSnackbarMessage.asStateFlow()

    private val _lastDeletedTransaction = MutableStateFlow<UdhaarTransaction?>(null)
    val lastDeletedTransaction: StateFlow<UdhaarTransaction?> = _lastDeletedTransaction.asStateFlow()

    private val _lastSettledBeforeTransaction = MutableStateFlow<UdhaarTransaction?>(null)
    val lastSettledBeforeTransaction: StateFlow<UdhaarTransaction?> = _lastSettledBeforeTransaction.asStateFlow()

    private val _successAnimationEvent = MutableStateFlow<SuccessAnimationEvent?>(null)
    val successAnimationEvent: StateFlow<SuccessAnimationEvent?> = _successAnimationEvent.asStateFlow()

    private val _hasUnlockedThisSession = MutableStateFlow(false)
    val hasUnlockedThisSession: StateFlow<Boolean> = _hasUnlockedThisSession.asStateFlow()

    private val _manualLockRequested = MutableStateFlow(false)
    val manualLockRequested: StateFlow<Boolean> = _manualLockRequested.asStateFlow()

    private val _dbEncryptionState = MutableStateFlow(DbEncryptionState.ENCRYPTED)
    val dbEncryptionState: StateFlow<DbEncryptionState> = _dbEncryptionState.asStateFlow()

    private val _blockingSqlCipherError = MutableStateFlow<String?>(null)
    val blockingSqlCipherError: StateFlow<String?> = _blockingSqlCipherError.asStateFlow()

    fun initSecurityState(context: Context?) {
        if (context != null) {
            _isAmountsHidden.value = EncryptedStorageManager.isAmountsHidden(context)
            _dbEncryptionState.value = EncryptedStorageManager.getDatabaseEncryptionState(context)
        }
        _blockingSqlCipherError.value = EncryptedStorageManager.blockingStartupErrorFlow.value
    }

    fun setDbEncryptionState(state: DbEncryptionState) {
        _dbEncryptionState.value = state
    }

    fun setBlockingSqlCipherError(error: String?) {
        _blockingSqlCipherError.value = error
    }

    fun switchMainTab(screen: ActiveScreen) {
        _activeScreen.value = screen
    }

    fun openPartyLedgerScreen(partyId: Long) {
        _selectedPartyIdForSheet.value = partyId
        _activeScreen.value = ActiveScreen.PARTY_DETAIL
    }

    fun closePartyLedgerScreen() {
        _selectedPartyIdForSheet.value = null
        _activeScreen.value = ActiveScreen.PEOPLE_DIRECTORY
    }

    fun openAddTransaction(partyDraft: PreselectedPartyDraft? = null) {
        _detailTransactionId.value = null
        _editingTransaction.value = null
        _preselectedPartyDraft.value = partyDraft
        _activeScreen.value = ActiveScreen.ADD_EDIT
    }

    fun openAddTransactionForParty(party: PartyEntity) {
        _detailTransactionId.value = null
        _editingTransaction.value = null
        _preselectedPartyDraft.value = PreselectedPartyDraft(
            partyId = party.partyId,
            name = party.name,
            phoneNumber = party.phoneNumber,
            preferredCurrency = party.preferredCurrency
        )
        _activeScreen.value = ActiveScreen.ADD_EDIT
    }

    fun openEditTransaction(transaction: UdhaarTransaction) {
        _detailTransactionId.value = null
        _preselectedPartyDraft.value = null
        _editingTransaction.value = transaction
        _activeScreen.value = ActiveScreen.ADD_EDIT
    }

    fun navigateBackToDashboard() {
        _editingTransaction.value = null
        _preselectedPartyDraft.value = null
        _activeScreen.value = ActiveScreen.DASHBOARD
    }

    fun openTransactionDetail(transactionId: Long) {
        _detailTransactionId.value = transactionId
    }

    fun closeTransactionDetail() {
        _detailTransactionId.value = null
    }

    fun setSummaryCurrency(currency: CurrencyType) {
        _summaryCurrency.value = currency
    }

    fun toggleHideAmounts(context: Context? = null) {
        val next = !_isAmountsHidden.value
        _isAmountsHidden.value = next
        context?.let { EncryptedStorageManager.setAmountsHidden(it, next) }
    }

    fun showUndoSnackbar(
        message: String,
        deletedTx: UdhaarTransaction? = null,
        settledBefore: UdhaarTransaction? = null
    ) {
        _lastDeletedTransaction.value = deletedTx
        _lastSettledBeforeTransaction.value = settledBefore
        _undoSnackbarMessage.value = message
    }

    fun dismissUndoSnackbar() {
        _undoSnackbarMessage.value = null
        _lastDeletedTransaction.value = null
        _lastSettledBeforeTransaction.value = null
    }

    fun setSuccessAnimation(event: SuccessAnimationEvent) {
        _successAnimationEvent.value = event
    }

    fun clearSuccessAnimation() {
        _successAnimationEvent.value = null
    }

    fun markAuthenticated() {
        _hasUnlockedThisSession.value = true
        _manualLockRequested.value = false
    }

    fun lockApplicationNow() {
        _hasUnlockedThisSession.value = false
        _manualLockRequested.value = true
    }

    fun openSettingsScreen() {
        _previousScreenBeforeSettings.value = _activeScreen.value
        _activeScreen.value = ActiveScreen.SETTINGS
    }

    fun closeSettingsScreen() {
        _activeScreen.value = _previousScreenBeforeSettings.value
    }
}
