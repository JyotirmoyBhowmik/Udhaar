package com.jyotirmoyb.udhaar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.DateRangeFilter
import com.jyotirmoyb.udhaar.data.LedgerFilter
import com.jyotirmoyb.udhaar.data.TransactionSortOption
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.data.repository.LedgerRepository
import com.jyotirmoyb.udhaar.data.repository.SettingsRepository
import com.jyotirmoyb.udhaar.ui.CurrencyLedgerTotals
import com.jyotirmoyb.udhaar.ui.state.AppStateHolder
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DashboardUiState(
    val inrTotals: CurrencyLedgerTotals = CurrencyLedgerTotals(CurrencyType.INR),
    val nprTotals: CurrencyLedgerTotals = CurrencyLedgerTotals(CurrencyType.NPR),
    val selectedFilter: LedgerFilter = LedgerFilter.ALL,
    val searchQuery: String = "",
    val allCalculatedItems: List<CalculatedUdhaarSummary> = emptyList(),
    val filteredItems: List<CalculatedUdhaarSummary> = emptyList(),
    val transactionSortOption: TransactionSortOption = TransactionSortOption.RECENT_FIRST,
    val dateRangeFilter: DateRangeFilter = DateRangeFilter.ALL_TIME,
    val showCoachMarks: Boolean = false,
    val isNeedsAttentionDismissed: Boolean = false,
    val isInitialLoading: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val settingsRepository: SettingsRepository,
    private val appStateHolder: AppStateHolder
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(LedgerFilter.ALL)
    val selectedFilter: StateFlow<LedgerFilter> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _transactionSortOption = MutableStateFlow(TransactionSortOption.RECENT_FIRST)
    val transactionSortOption: StateFlow<TransactionSortOption> = _transactionSortOption.asStateFlow()

    private val _dateRangeFilter = MutableStateFlow(DateRangeFilter.ALL_TIME)
    val dateRangeFilter: StateFlow<DateRangeFilter> = _dateRangeFilter.asStateFlow()

    private val _showCoachMarks = MutableStateFlow(false)
    val showCoachMarks: StateFlow<Boolean> = _showCoachMarks.asStateFlow()

    private val _isNeedsAttentionDismissed = MutableStateFlow(false)
    val isNeedsAttentionDismissed: StateFlow<Boolean> = _isNeedsAttentionDismissed.asStateFlow()

    val uiState: StateFlow<DashboardUiState> = combine(
        ledgerRepository.allTransactions,
        ledgerRepository.allPartialPayments,
        ledgerRepository.allAttachments,
        settingsRepository.userSettings,
        combine(
            _selectedFilter,
            _searchQuery,
            _transactionSortOption,
            _dateRangeFilter,
            _isNeedsAttentionDismissed
        ) { filter, query, sortOpt, range, needsAttn ->
            FilterParams(filter, query, sortOpt, range, needsAttn)
        }
    ) { transactions, payments, attachments, settings, params ->
        val nowMillis = System.currentTimeMillis()
        val enabledCurrencySet = settings.enabledCurrencies.toSet()

        val calculatedList = transactions.map { tx ->
            InterestCalculator.calculate(tx, nowMillis)
        }
        val visibleCalculatedList = calculatedList.filter { it.transaction.currency in enabledCurrencySet }

        val activeInr = calculatedList.filter { !it.transaction.isSettled && it.transaction.currency == CurrencyType.INR }
        val inrTotals = CurrencyLedgerTotals(
            currency = CurrencyType.INR,
            totalToReceivePaise = activeInr.filter { it.transaction.transactionType == TransactionType.GIVEN }.sumOf { it.netBalanceDuePaise },
            totalToPayPaise = activeInr.filter { it.transaction.transactionType == TransactionType.TAKEN }.sumOf { it.netBalanceDuePaise },
            activeGivenCount = activeInr.count { it.transaction.transactionType == TransactionType.GIVEN },
            activeTakenCount = activeInr.count { it.transaction.transactionType == TransactionType.TAKEN }
        )

        val activeNpr = calculatedList.filter { !it.transaction.isSettled && it.transaction.currency == CurrencyType.NPR }
        val nprTotals = CurrencyLedgerTotals(
            currency = CurrencyType.NPR,
            totalToReceivePaise = activeNpr.filter { it.transaction.transactionType == TransactionType.GIVEN }.sumOf { it.netBalanceDuePaise },
            totalToPayPaise = activeNpr.filter { it.transaction.transactionType == TransactionType.TAKEN }.sumOf { it.netBalanceDuePaise },
            activeGivenCount = activeNpr.count { it.transaction.transactionType == TransactionType.GIVEN },
            activeTakenCount = activeNpr.count { it.transaction.transactionType == TransactionType.TAKEN }
        )

        val paymentsMap = payments.groupBy { it.transactionId }
        val attachmentsByTx = attachments.filter { it.transactionId > 0L }.groupBy { it.transactionId }
        val attachmentsByPayment = attachments
            .mapNotNull { att -> att.paymentId?.takeIf { it > 0L }?.let { pid -> pid to att } }
            .groupBy({ it.first }, { it.second })

        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = cal.timeInMillis
        val query = params.query.trim().lowercase()

        val filtered = visibleCalculatedList.filter { item ->
            val tx = item.transaction
            val txPayments = paymentsMap[tx.id].orEmpty()
            val txAttachments = attachmentsByTx[tx.id].orEmpty()
            val needsConfirm = tx.needsMyConfirmation ||
                tx.verificationStatus == VerificationStatus.EDITED_AFTER_CONFIRM ||
                tx.verificationStatus == VerificationStatus.PENDING ||
                txPayments.any { it.needsMyAcknowledgement || !it.isAcknowledged }

            val matchesFilter = when (params.filter) {
                LedgerFilter.ALL -> true
                LedgerFilter.GIVEN -> tx.transactionType == TransactionType.GIVEN && !tx.isSettled
                LedgerFilter.TAKEN -> tx.transactionType == TransactionType.TAKEN && !tx.isSettled
                LedgerFilter.NEEDS_CONFIRMATION -> needsConfirm && !tx.isSettled
                LedgerFilter.OVERDUE -> item.isOverdue && !tx.isSettled
                LedgerFilter.INR -> tx.currency == CurrencyType.INR
                LedgerFilter.NPR -> tx.currency == CurrencyType.NPR
                LedgerFilter.SETTLED -> tx.isSettled
            }

            val matchesDateRange = when (params.dateRangeFilter) {
                DateRangeFilter.ALL_TIME -> true
                DateRangeFilter.THIS_MONTH -> tx.createdAtMillis >= startOfMonth
                DateRangeFilter.LAST_30_DAYS -> tx.createdAtMillis >= (nowMillis - 30L * 24 * 3600 * 1000)
                DateRangeFilter.LAST_90_DAYS -> tx.createdAtMillis >= (nowMillis - 90L * 24 * 3600 * 1000)
            }

            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                tx.partyName.lowercase().contains(query) ||
                    tx.phoneNumber.lowercase().contains(query) ||
                    tx.notes.lowercase().contains(query) ||
                    tx.paymentMode.label.lowercase().contains(query) ||
                    tx.verificationStatus.label.lowercase().contains(query) ||
                    txAttachments.any { it.fileName.lowercase().contains(query) } ||
                    txPayments.any { p ->
                        p.note.lowercase().contains(query) ||
                            attachmentsByPayment[p.id].orEmpty().any { it.fileName.lowercase().contains(query) }
                    }
            }
            matchesFilter && matchesDateRange && matchesQuery
        }

        val sortedFiltered = when (params.sortOption) {
            TransactionSortOption.RECENT_FIRST -> filtered.sortedByDescending { it.transaction.createdAtMillis }
            TransactionSortOption.DUE_DATE_ASC -> filtered.sortedWith(
                compareBy<CalculatedUdhaarSummary> { it.transaction.expiryDateMillis ?: Long.MAX_VALUE }
                    .thenByDescending { it.transaction.createdAtMillis }
            )
            TransactionSortOption.AMOUNT_DESC -> filtered.sortedByDescending { it.netBalanceDuePaise }
            TransactionSortOption.PARTY_NAME -> filtered.sortedBy { it.transaction.partyName.lowercase() }
        }

        DashboardUiState(
            inrTotals = inrTotals,
            nprTotals = nprTotals,
            selectedFilter = params.filter,
            searchQuery = params.query,
            allCalculatedItems = visibleCalculatedList,
            filteredItems = sortedFiltered,
            transactionSortOption = params.sortOption,
            dateRangeFilter = params.dateRangeFilter,
            showCoachMarks = _showCoachMarks.value,
            isNeedsAttentionDismissed = params.isNeedsAttentionDismissed,
            isInitialLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(isInitialLoading = true)
    )

    private data class FilterParams(
        val filter: LedgerFilter,
        val query: String,
        val sortOption: TransactionSortOption,
        val dateRangeFilter: DateRangeFilter,
        val isNeedsAttentionDismissed: Boolean
    )

    fun setFilter(filter: LedgerFilter) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTransactionSortOption(option: TransactionSortOption) {
        _transactionSortOption.value = option
    }

    fun setDateRangeFilter(filter: DateRangeFilter) {
        _dateRangeFilter.value = filter
    }

    fun dismissCoachMarks() {
        _showCoachMarks.value = false
    }

    fun dismissNeedsAttentionStrip() {
        _isNeedsAttentionDismissed.value = true
    }

    fun performUndoAction() {
        val deletedTx = appStateHolder.lastDeletedTransaction.value
        val settledBefore = appStateHolder.lastSettledBeforeTransaction.value

        viewModelScope.launch {
            if (deletedTx != null) {
                ledgerRepository.saveTransaction(deletedTx)
                appStateHolder.dismissUndoSnackbar()
            } else if (settledBefore != null) {
                ledgerRepository.updateTransaction(settledBefore)
                appStateHolder.dismissUndoSnackbar()
            }
        }
    }
}
