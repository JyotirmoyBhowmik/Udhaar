package com.jyotirmoyb.udhaar.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.LedgerFilter
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.notification.ReminderScheduler
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.LedgerBackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActiveScreen {
    DASHBOARD,
    PEOPLE_DIRECTORY,
    PARTY_DETAIL,
    ADD_EDIT
}

data class QrModalState(
    val transactionId: Long? = null,
    val partyName: String? = null,
    val initialAmountInr: Double? = null,
    val customUpiId: String? = null,
    val isPayToPartyMode: Boolean = false
)

data class CurrencyLedgerTotals(
    val currency: CurrencyType,
    val totalToReceive: Double = 0.0,
    val totalToPay: Double = 0.0,
    val activeGivenCount: Int = 0,
    val activeTakenCount: Int = 0
) {
    val netPosition: Double get() = totalToReceive - totalToPay
}

data class PartyKhataSummary(
    val party: PartyEntity,
    val transactions: List<CalculatedUdhaarSummary>,
    val inrToReceive: Double,
    val inrToPay: Double,
    val nprToReceive: Double,
    val nprToPay: Double
) {
    val inrNet: Double get() = inrToReceive - inrToPay
    val nprNet: Double get() = nprToReceive - nprToPay
    val activeLoanCount: Int get() = transactions.count { !it.transaction.isSettled }
}

data class PreselectedPartyDraft(
    val partyId: Long?,
    val name: String,
    val phoneNumber: String,
    val preferredCurrency: CurrencyType
)

data class UdhaarUiState(
    val isAuthenticated: Boolean = true,
    val activeScreen: ActiveScreen = ActiveScreen.DASHBOARD,
    val summaryCurrency: CurrencyType = CurrencyType.INR,
    val inrTotals: CurrencyLedgerTotals = CurrencyLedgerTotals(CurrencyType.INR),
    val nprTotals: CurrencyLedgerTotals = CurrencyLedgerTotals(CurrencyType.NPR),
    val selectedFilter: LedgerFilter = LedgerFilter.ALL,
    val searchQuery: String = "",
    val peopleSearchQuery: String = "",
    val allCalculatedItems: List<CalculatedUdhaarSummary> = emptyList(),
    val filteredItems: List<CalculatedUdhaarSummary> = emptyList(),
    val allParties: List<PartyEntity> = emptyList(),
    val partyKhataList: List<PartyKhataSummary> = emptyList(),
    val filteredPartyKhataList: List<PartyKhataSummary> = emptyList(),
    val partialPaymentsByTx: Map<Long, List<PartialPayment>> = emptyMap(),
    val userSettings: UserSettings = UserSettings(),
    val editingTransaction: UdhaarTransaction? = null,
    val preselectedPartyDraft: PreselectedPartyDraft? = null,
    val detailTransactionId: Long? = null,
    val whatsAppPreviewTransactionId: Long? = null,
    val qrModalState: QrModalState? = null,
    val showSettingsModal: Boolean = false,
    val showShareAppModal: Boolean = false,
    val selectedPartyIdForSheet: Long? = null,
    val editingParty: PartyEntity? = null,
    val showAddEditPartyModal: Boolean = false,
    val receiptModalTransactionId: Long? = null,
    val receiptModalPartyId: Long? = null
) {
    val activeSummaryTotals: CurrencyLedgerTotals
        get() = if (summaryCurrency == CurrencyType.INR) inrTotals else nprTotals
}

private data class LocalUiFilterState(
    val hasUnlockedThisSession: Boolean = false,
    val manualLockRequested: Boolean = false,
    val activeScreen: ActiveScreen = ActiveScreen.DASHBOARD,
    val summaryCurrency: CurrencyType? = null,
    val selectedFilter: LedgerFilter = LedgerFilter.ALL,
    val searchQuery: String = "",
    val peopleSearchQuery: String = "",
    val editingTransaction: UdhaarTransaction? = null,
    val preselectedPartyDraft: PreselectedPartyDraft? = null,
    val detailTransactionId: Long? = null,
    val whatsAppPreviewTransactionId: Long? = null,
    val qrModalState: QrModalState? = null,
    val showSettingsModal: Boolean = false,
    val showShareAppModal: Boolean = false,
    val selectedPartyIdForSheet: Long? = null,
    val editingParty: PartyEntity? = null,
    val showAddEditPartyModal: Boolean = false,
    val receiptModalTransactionId: Long? = null,
    val receiptModalPartyId: Long? = null
)

class UdhaarViewModel(
    private val repository: UdhaarRepository,
    private val appContext: Context? = null
) : ViewModel() {

    private val localState = MutableStateFlow(LocalUiFilterState())

    val uiState: StateFlow<UdhaarUiState> = combine(
        repository.allParties,
        repository.allTransactions,
        repository.allPartialPayments,
        repository.userSettings,
        localState
    ) { parties, transactions, partialPayments, settings, local ->
        val nowMillis = System.currentTimeMillis()
        val enabledCurrencySet = settings.enabledCurrencies.toSet()

        val calculatedList = transactions.map { tx ->
            InterestCalculator.calculate(tx, nowMillis)
        }

        // Filter visible ledger items to enabled currencies so disabled currencies stay hidden
        val visibleCalculatedList = calculatedList.filter { it.transaction.currency in enabledCurrencySet }

        val activeInr = calculatedList.filter { !it.transaction.isSettled && it.transaction.currency == CurrencyType.INR }
        val inrTotals = CurrencyLedgerTotals(
            currency = CurrencyType.INR,
            totalToReceive = activeInr.filter { it.transaction.transactionType == TransactionType.GIVEN }
                .sumOf { it.netBalanceDue },
            totalToPay = activeInr.filter { it.transaction.transactionType == TransactionType.TAKEN }
                .sumOf { it.netBalanceDue },
            activeGivenCount = activeInr.count { it.transaction.transactionType == TransactionType.GIVEN },
            activeTakenCount = activeInr.count { it.transaction.transactionType == TransactionType.TAKEN }
        )

        val activeNpr = calculatedList.filter { !it.transaction.isSettled && it.transaction.currency == CurrencyType.NPR }
        val nprTotals = CurrencyLedgerTotals(
            currency = CurrencyType.NPR,
            totalToReceive = activeNpr.filter { it.transaction.transactionType == TransactionType.GIVEN }
                .sumOf { it.netBalanceDue },
            totalToPay = activeNpr.filter { it.transaction.transactionType == TransactionType.TAKEN }
                .sumOf { it.netBalanceDue },
            activeGivenCount = activeNpr.count { it.transaction.transactionType == TransactionType.GIVEN },
            activeTakenCount = activeNpr.count { it.transaction.transactionType == TransactionType.TAKEN }
        )

        val query = local.searchQuery.trim().lowercase()
        val filtered = visibleCalculatedList.filter { item ->
            val tx = item.transaction
            val matchesFilter = when (local.selectedFilter) {
                LedgerFilter.ALL -> true
                LedgerFilter.GIVEN -> tx.transactionType == TransactionType.GIVEN && !tx.isSettled
                LedgerFilter.TAKEN -> tx.transactionType == TransactionType.TAKEN && !tx.isSettled
                LedgerFilter.OVERDUE -> item.isOverdue && !tx.isSettled
                LedgerFilter.INR -> tx.currency == CurrencyType.INR
                LedgerFilter.NPR -> tx.currency == CurrencyType.NPR
                LedgerFilter.SETTLED -> tx.isSettled
            }
            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                tx.partyName.lowercase().contains(query) ||
                    tx.phoneNumber.lowercase().contains(query) ||
                    tx.notes.lowercase().contains(query) ||
                    tx.paymentMode.label.lowercase().contains(query)
            }
            matchesFilter && matchesQuery
        }

        // Build unified Party Khata Summaries
        val savedPartiesByName = parties.associateBy { it.name.trim().lowercase() }.toMutableMap()
        val syntheticParties = mutableListOf<PartyEntity>()
        var syntheticIdCounter = -1L
        for (calc in calculatedList) {
            val key = calc.transaction.partyName.trim().lowercase()
            if (key.isNotEmpty() && !savedPartiesByName.containsKey(key)) {
                val synthetic = PartyEntity(
                    partyId = syntheticIdCounter--,
                    name = calc.transaction.partyName.trim(),
                    phoneNumber = calc.transaction.phoneNumber,
                    preferredCurrency = calc.transaction.currency,
                    createdAtMillis = calc.transaction.createdAtMillis
                )
                savedPartiesByName[key] = synthetic
                syntheticParties.add(synthetic)
            }
        }
        val combinedParties = (parties + syntheticParties).sortedBy { it.name.lowercase() }

        val khataList = combinedParties.map { party ->
            val partyTxs = calculatedList.filter { item ->
                ((party.partyId > 0L && item.transaction.partyId == party.partyId) ||
                    item.transaction.partyName.trim().equals(party.name.trim(), ignoreCase = true)) &&
                    item.transaction.currency in enabledCurrencySet
            }
            val activePartyTxs = partyTxs.filter { !it.transaction.isSettled }
            val inrGiven = activePartyTxs.filter {
                it.transaction.currency == CurrencyType.INR && it.transaction.transactionType == TransactionType.GIVEN
            }.sumOf { it.netBalanceDue }
            val inrTaken = activePartyTxs.filter {
                it.transaction.currency == CurrencyType.INR && it.transaction.transactionType == TransactionType.TAKEN
            }.sumOf { it.netBalanceDue }
            val nprGiven = activePartyTxs.filter {
                it.transaction.currency == CurrencyType.NPR && it.transaction.transactionType == TransactionType.GIVEN
            }.sumOf { it.netBalanceDue }
            val nprTaken = activePartyTxs.filter {
                it.transaction.currency == CurrencyType.NPR && it.transaction.transactionType == TransactionType.TAKEN
            }.sumOf { it.netBalanceDue }

            PartyKhataSummary(
                party = party,
                transactions = partyTxs,
                inrToReceive = inrGiven,
                inrToPay = inrTaken,
                nprToReceive = nprGiven,
                nprToPay = nprTaken
            )
        }

        val pQuery = local.peopleSearchQuery.trim().lowercase()
        val filteredKhataList = if (pQuery.isEmpty()) {
            khataList
        } else {
            khataList.filter { khata ->
                val p = khata.party
                p.name.lowercase().contains(pQuery) ||
                    p.phoneNumber.lowercase().contains(pQuery) ||
                    p.upiId.lowercase().contains(pQuery) ||
                    p.email.lowercase().contains(pQuery) ||
                    p.tagOrNotes.lowercase().contains(pQuery)
            }
        }

        val paymentsMap = partialPayments.groupBy { it.transactionId }
        val rawSummaryCurrency = local.summaryCurrency ?: settings.defaultCurrency
        val effectiveSummaryCurrency = if (rawSummaryCurrency in enabledCurrencySet) {
            rawSummaryCurrency
        } else {
            settings.enabledCurrencies.first()
        }
        val requiresAuth = (settings.biometricLockEnabled && !local.hasUnlockedThisSession) || local.manualLockRequested
        val unlocked = !requiresAuth

        UdhaarUiState(
            isAuthenticated = unlocked,
            activeScreen = local.activeScreen,
            summaryCurrency = effectiveSummaryCurrency,
            inrTotals = inrTotals,
            nprTotals = nprTotals,
            selectedFilter = local.selectedFilter,
            searchQuery = local.searchQuery,
            peopleSearchQuery = local.peopleSearchQuery,
            allCalculatedItems = visibleCalculatedList,
            filteredItems = filtered,
            allParties = combinedParties,
            partyKhataList = khataList,
            filteredPartyKhataList = filteredKhataList,
            partialPaymentsByTx = paymentsMap,
            userSettings = settings,
            editingTransaction = local.editingTransaction,
            preselectedPartyDraft = local.preselectedPartyDraft,
            detailTransactionId = local.detailTransactionId,
            whatsAppPreviewTransactionId = local.whatsAppPreviewTransactionId,
            qrModalState = local.qrModalState,
            showSettingsModal = local.showSettingsModal,
            showShareAppModal = local.showShareAppModal,
            selectedPartyIdForSheet = local.selectedPartyIdForSheet,
            editingParty = local.editingParty,
            showAddEditPartyModal = local.showAddEditPartyModal,
            receiptModalTransactionId = local.receiptModalTransactionId,
            receiptModalPartyId = local.receiptModalPartyId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UdhaarUiState()
    )

    fun markAuthenticated() {
        localState.update {
            it.copy(
                hasUnlockedThisSession = true,
                manualLockRequested = false
            )
        }
    }

    fun lockApplicationNow() {
        localState.update {
            it.copy(
                manualLockRequested = true,
                showSettingsModal = false
            )
        }
    }

    fun switchMainTab(screen: ActiveScreen) {
        localState.update { it.copy(activeScreen = screen) }
    }

    fun setSummaryCurrency(currency: CurrencyType) {
        localState.update { it.copy(summaryCurrency = currency) }
    }

    fun toggleCurrencyEnabled(currency: CurrencyType, enabled: Boolean) {
        viewModelScope.launch {
            repository.setCurrencyEnabled(currency, enabled)
        }
    }

    fun cycleOrSelectTheme(themeMode: AppThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(themeMode)
        }
    }

    fun setFilter(filter: LedgerFilter) {
        localState.update { it.copy(selectedFilter = filter) }
    }

    fun setSearchQuery(query: String) {
        localState.update { it.copy(searchQuery = query) }
    }

    fun setPeopleSearchQuery(query: String) {
        localState.update { it.copy(peopleSearchQuery = query) }
    }

    fun openAddTransaction() {
        localState.update {
            it.copy(
                activeScreen = ActiveScreen.ADD_EDIT,
                editingTransaction = null,
                preselectedPartyDraft = null,
                detailTransactionId = null
            )
        }
    }

    fun openAddTransactionForParty(party: PartyEntity) {
        localState.update {
            it.copy(
                activeScreen = ActiveScreen.ADD_EDIT,
                editingTransaction = null,
                preselectedPartyDraft = PreselectedPartyDraft(
                    partyId = party.partyId.takeIf { id -> id > 0L },
                    name = party.name,
                    phoneNumber = party.phoneNumber,
                    preferredCurrency = party.preferredCurrency
                ),
                detailTransactionId = null
            )
        }
    }

    fun openEditTransaction(transaction: UdhaarTransaction) {
        localState.update {
            it.copy(
                activeScreen = ActiveScreen.ADD_EDIT,
                editingTransaction = transaction,
                preselectedPartyDraft = null,
                detailTransactionId = null
            )
        }
    }

    fun navigateBackToDashboard() {
        localState.update {
            val targetScreen = if (it.selectedPartyIdForSheet != null) {
                ActiveScreen.PARTY_DETAIL
            } else {
                ActiveScreen.DASHBOARD
            }
            it.copy(
                activeScreen = targetScreen,
                editingTransaction = null,
                preselectedPartyDraft = null
            )
        }
    }

    // --- Party Directory & Individual Party Ledger Screen Navigation ---
    fun openPartyLedgerScreen(partyId: Long) {
        localState.update {
            it.copy(
                selectedPartyIdForSheet = partyId,
                activeScreen = ActiveScreen.PARTY_DETAIL
            )
        }
    }

    fun closePartyLedgerScreen() {
        localState.update {
            it.copy(
                selectedPartyIdForSheet = null,
                activeScreen = ActiveScreen.PEOPLE_DIRECTORY
            )
        }
    }

    fun openAddPartyModal() {
        localState.update {
            it.copy(
                editingParty = null,
                showAddEditPartyModal = true
            )
        }
    }

    fun openEditPartyModal(party: PartyEntity) {
        localState.update {
            it.copy(
                editingParty = party,
                showAddEditPartyModal = true
            )
        }
    }

    fun closeAddEditPartyModal() {
        localState.update {
            it.copy(
                editingParty = null,
                showAddEditPartyModal = false
            )
        }
    }

    fun saveParty(party: PartyEntity) {
        viewModelScope.launch {
            val savedId = repository.saveParty(party)
            localState.update {
                it.copy(
                    editingParty = null,
                    showAddEditPartyModal = false,
                    selectedPartyIdForSheet = if (it.activeScreen == ActiveScreen.PARTY_DETAIL) savedId else it.selectedPartyIdForSheet
                )
            }
        }
    }

    fun deleteParty(party: PartyEntity) {
        viewModelScope.launch {
            if (party.partyId > 0L) {
                repository.deleteParty(party)
            }
            localState.update {
                it.copy(
                    selectedPartyIdForSheet = null,
                    activeScreen = ActiveScreen.PEOPLE_DIRECTORY,
                    showAddEditPartyModal = false
                )
            }
        }
    }

    fun openPayPartyViaUpiModal(partyName: String, partyUpiId: String, amountInr: Double?) {
        localState.update {
            it.copy(
                qrModalState = QrModalState(
                    transactionId = null,
                    partyName = partyName,
                    initialAmountInr = amountInr?.takeIf { amt -> amt > 0.0 },
                    customUpiId = partyUpiId,
                    isPayToPartyMode = true
                )
            )
        }
    }

    // --- Thermal Statement Receipt Modal ---
    fun openTransactionReceiptModal(transactionId: Long) {
        localState.update {
            it.copy(
                receiptModalTransactionId = transactionId,
                receiptModalPartyId = null
            )
        }
    }

    fun openPartyStatementReceiptModal(partyId: Long) {
        localState.update {
            it.copy(
                receiptModalPartyId = partyId,
                receiptModalTransactionId = null
            )
        }
    }

    fun closeReceiptModal() {
        localState.update {
            it.copy(
                receiptModalTransactionId = null,
                receiptModalPartyId = null
            )
        }
    }

    // --- Transaction Detail & Modals ---
    fun openTransactionDetail(transactionId: Long) {
        localState.update { it.copy(detailTransactionId = transactionId) }
    }

    fun closeTransactionDetail() {
        localState.update { it.copy(detailTransactionId = null) }
    }

    fun openWhatsAppReminderModal(transactionId: Long) {
        localState.update {
            it.copy(
                whatsAppPreviewTransactionId = transactionId,
                detailTransactionId = null
            )
        }
    }

    fun closeWhatsAppReminderModal() {
        localState.update { it.copy(whatsAppPreviewTransactionId = null) }
    }

    fun openGlobalQrModal() {
        localState.update {
            it.copy(
                qrModalState = QrModalState(
                    transactionId = null,
                    partyName = null,
                    initialAmountInr = null,
                    customUpiId = null,
                    isPayToPartyMode = false
                )
            )
        }
    }

    fun openTransactionQrModal(summary: CalculatedUdhaarSummary) {
        localState.update {
            it.copy(
                qrModalState = QrModalState(
                    transactionId = summary.transaction.id,
                    partyName = summary.transaction.partyName,
                    initialAmountInr = summary.netBalanceDue,
                    customUpiId = null,
                    isPayToPartyMode = false
                ),
                detailTransactionId = null
            )
        }
    }

    fun closeQrModal() {
        localState.update { it.copy(qrModalState = null) }
    }

    fun openSettingsModal() {
        localState.update { it.copy(showSettingsModal = true) }
    }

    fun closeSettingsModal() {
        localState.update { it.copy(showSettingsModal = false) }
    }

    fun openShareAppModal() {
        localState.update { it.copy(showShareAppModal = true) }
    }

    fun closeShareAppModal() {
        localState.update { it.copy(showShareAppModal = false) }
    }

    fun saveUdhaarTransaction(transaction: UdhaarTransaction) {
        viewModelScope.launch {
            val savedId = repository.saveTransaction(transaction)
            val savedTx = transaction.copy(id = savedId)
            appContext?.let { ctx ->
                if (savedTx.reminderDateMillis != null && !savedTx.isSettled) {
                    ReminderScheduler.scheduleReminder(ctx, savedTx)
                } else {
                    ReminderScheduler.cancelReminder(ctx, savedId)
                }
            }
            localState.update {
                val returnScreen = if (it.selectedPartyIdForSheet != null) {
                    ActiveScreen.PARTY_DETAIL
                } else {
                    ActiveScreen.DASHBOARD
                }
                it.copy(
                    activeScreen = returnScreen,
                    editingTransaction = null,
                    preselectedPartyDraft = null,
                    summaryCurrency = savedTx.currency
                )
            }
        }
    }

    fun deleteTransaction(transaction: UdhaarTransaction) {
        viewModelScope.launch {
            appContext?.let { ctx ->
                ReminderScheduler.cancelReminder(ctx, transaction.id)
            }
            repository.deleteTransaction(transaction)
            localState.update {
                it.copy(
                    detailTransactionId = null,
                    whatsAppPreviewTransactionId = null
                )
            }
        }
    }

    fun recordPartialPayment(
        summary: CalculatedUdhaarSummary,
        amount: Double,
        paymentMode: PaymentMode = PaymentMode.CASH,
        note: String
    ) {
        viewModelScope.launch {
            repository.recordPartialPayment(
                transaction = summary.transaction,
                paymentAmount = amount,
                paymentMode = paymentMode,
                note = note,
                totalAccruedAmount = summary.grossTotal
            )
        }
    }

    fun deletePartialPayment(
        summary: CalculatedUdhaarSummary,
        payment: PartialPayment
    ) {
        viewModelScope.launch {
            repository.deletePartialPayment(
                transaction = summary.transaction,
                payment = payment,
                totalAccruedAmount = summary.grossTotal
            )
        }
    }

    fun toggleSettled(summary: CalculatedUdhaarSummary) {
        viewModelScope.launch {
            repository.toggleFullSettlement(
                transaction = summary.transaction,
                totalAccruedAmount = summary.grossTotal
            )
            appContext?.let { ctx ->
                if (!summary.transaction.isSettled) {
                    ReminderScheduler.cancelReminder(ctx, summary.transaction.id)
                }
            }
        }
    }

    fun saveUserSettings(
        upiId: String,
        payeeName: String,
        defaultCurrency: CurrencyType,
        inrEnabled: Boolean? = null,
        nprEnabled: Boolean? = null,
        themeMode: AppThemeMode? = null,
        biometricLockEnabled: Boolean? = null,
        securityPin: String? = null
    ) {
        viewModelScope.launch {
            repository.updateSettings(
                upiId = upiId,
                payeeName = payeeName,
                defaultCurrency = defaultCurrency,
                inrEnabled = inrEnabled,
                nprEnabled = nprEnabled,
                themeMode = themeMode,
                biometricLockEnabled = biometricLockEnabled,
                securityPin = securityPin
            )
            localState.update { it.copy(showSettingsModal = false) }
        }
    }

    // --- Backup / Export / Import Operations ---
    fun generateFullLedgerBackupJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val (parties, transactions, payments) = repository.getFullLedgerSnapshot()
            val json = LedgerBackupManager.buildBackupJson(
                parties = parties,
                transactions = transactions,
                partialPayments = payments,
                exportType = "FULL_LEDGER"
            )
            onReady(json)
        }
    }

    fun generateSinglePartyStatementJson(khata: PartyKhataSummary, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val (_, _, allPayments) = repository.getFullLedgerSnapshot()
            val txs = khata.transactions.map { it.transaction }
            val txIds = txs.map { it.id }.toSet()
            val partyPayments = allPayments.filter { it.transactionId in txIds }
            val json = LedgerBackupManager.buildBackupJson(
                parties = listOf(khata.party),
                transactions = txs,
                partialPayments = partyPayments,
                exportType = "SINGLE_PARTY_STATEMENT",
                statementPartyName = khata.party.name
            )
            onReady(json)
        }
    }

    fun importLedgerBackupJson(jsonString: String, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val parsed = LedgerBackupManager.parseBackupJson(jsonString)
                repository.importLedgerData(
                    parties = parsed.parties,
                    transactions = parsed.transactions,
                    partialPayments = parsed.partialPayments
                )
            }
            onResult(result)
        }
    }

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
