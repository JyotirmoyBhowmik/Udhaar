package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CompoundFrequency
import com.example.data.CurrencyType
import com.example.data.InterestRatePeriod
import com.example.data.InterestType
import com.example.data.LedgerFilter
import com.example.data.PartialPayment
import com.example.data.PaymentMode
import com.example.data.TransactionType
import com.example.data.UdhaarRepository
import com.example.data.UdhaarTransaction
import com.example.data.UserSettings
import com.example.notification.ReminderScheduler
import com.example.util.CalculatedUdhaarSummary
import com.example.util.InterestCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActiveScreen {
    DASHBOARD,
    ADD_EDIT
}

data class QrModalState(
    val transactionId: Long? = null,
    val partyName: String? = null,
    val initialAmountInr: Double? = null
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

data class UdhaarUiState(
    val activeScreen: ActiveScreen = ActiveScreen.DASHBOARD,
    val summaryCurrency: CurrencyType = CurrencyType.INR,
    val inrTotals: CurrencyLedgerTotals = CurrencyLedgerTotals(CurrencyType.INR),
    val nprTotals: CurrencyLedgerTotals = CurrencyLedgerTotals(CurrencyType.NPR),
    val selectedFilter: LedgerFilter = LedgerFilter.ALL,
    val searchQuery: String = "",
    val allCalculatedItems: List<CalculatedUdhaarSummary> = emptyList(),
    val filteredItems: List<CalculatedUdhaarSummary> = emptyList(),
    val partialPaymentsByTx: Map<Long, List<PartialPayment>> = emptyMap(),
    val userSettings: UserSettings = UserSettings(),
    val editingTransaction: UdhaarTransaction? = null,
    val detailTransactionId: Long? = null,
    val whatsAppPreviewTransactionId: Long? = null,
    val qrModalState: QrModalState? = null,
    val showSettingsModal: Boolean = false
) {
    val activeSummaryTotals: CurrencyLedgerTotals
        get() = if (summaryCurrency == CurrencyType.INR) inrTotals else nprTotals
}

private data class LocalUiFilterState(
    val activeScreen: ActiveScreen = ActiveScreen.DASHBOARD,
    val summaryCurrency: CurrencyType? = null,
    val selectedFilter: LedgerFilter = LedgerFilter.ALL,
    val searchQuery: String = "",
    val editingTransaction: UdhaarTransaction? = null,
    val detailTransactionId: Long? = null,
    val whatsAppPreviewTransactionId: Long? = null,
    val qrModalState: QrModalState? = null,
    val showSettingsModal: Boolean = false
)

class UdhaarViewModel(
    private val repository: UdhaarRepository,
    private val appContext: Context? = null
) : ViewModel() {

    private val localState = MutableStateFlow(LocalUiFilterState())

    val uiState: StateFlow<UdhaarUiState> = combine(
        repository.allTransactions,
        repository.allPartialPayments,
        repository.userSettings,
        localState
    ) { transactions, partialPayments, settings, local ->
        val nowMillis = System.currentTimeMillis()
        val calculatedList = transactions.map { tx ->
            InterestCalculator.calculate(tx, nowMillis)
        }

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
        val filtered = calculatedList.filter { item ->
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

        val paymentsMap = partialPayments.groupBy { it.transactionId }
        val effectiveSummaryCurrency = local.summaryCurrency ?: settings.defaultCurrency

        UdhaarUiState(
            activeScreen = local.activeScreen,
            summaryCurrency = effectiveSummaryCurrency,
            inrTotals = inrTotals,
            nprTotals = nprTotals,
            selectedFilter = local.selectedFilter,
            searchQuery = local.searchQuery,
            allCalculatedItems = calculatedList,
            filteredItems = filtered,
            partialPaymentsByTx = paymentsMap,
            userSettings = settings,
            editingTransaction = local.editingTransaction,
            detailTransactionId = local.detailTransactionId,
            whatsAppPreviewTransactionId = local.whatsAppPreviewTransactionId,
            qrModalState = local.qrModalState,
            showSettingsModal = local.showSettingsModal
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UdhaarUiState()
    )

    fun setSummaryCurrency(currency: CurrencyType) {
        localState.update { it.copy(summaryCurrency = currency) }
    }

    fun setFilter(filter: LedgerFilter) {
        localState.update { it.copy(selectedFilter = filter) }
    }

    fun setSearchQuery(query: String) {
        localState.update { it.copy(searchQuery = query) }
    }

    fun openAddTransaction() {
        localState.update {
            it.copy(
                activeScreen = ActiveScreen.ADD_EDIT,
                editingTransaction = null,
                detailTransactionId = null
            )
        }
    }

    fun openEditTransaction(transaction: UdhaarTransaction) {
        localState.update {
            it.copy(
                activeScreen = ActiveScreen.ADD_EDIT,
                editingTransaction = transaction,
                detailTransactionId = null
            )
        }
    }

    fun navigateBackToDashboard() {
        localState.update {
            it.copy(
                activeScreen = ActiveScreen.DASHBOARD,
                editingTransaction = null
            )
        }
    }

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
                    initialAmountInr = null
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
                    initialAmountInr = summary.netBalanceDue
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
                it.copy(
                    activeScreen = ActiveScreen.DASHBOARD,
                    editingTransaction = null,
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
        note: String
    ) {
        viewModelScope.launch {
            repository.recordPartialPayment(
                transaction = summary.transaction,
                paymentAmount = amount,
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

    fun saveUserSettings(upiId: String, payeeName: String, defaultCurrency: CurrencyType) {
        viewModelScope.launch {
            repository.updateSettings(upiId, payeeName, defaultCurrency)
            localState.update { it.copy(showSettingsModal = false) }
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
