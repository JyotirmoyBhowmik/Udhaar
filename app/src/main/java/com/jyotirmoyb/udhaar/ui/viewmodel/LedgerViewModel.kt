package com.jyotirmoyb.udhaar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.AuditTrailEntry
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.repository.LedgerRepository
import com.jyotirmoyb.udhaar.data.repository.PaymentRepository
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.UdhaarAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LedgerDomainUiState(
    val parties: List<PartyEntity> = emptyList(),
    val calculatedSummaries: List<CalculatedUdhaarSummary> = emptyList(),
    val paymentsByTransactionId: Map<Long, List<PartialPayment>> = emptyMap(),
    val auditByTransactionId: Map<Long, List<AuditTrailEntry>> = emptyMap(),
    val attachmentsByTransactionId: Map<Long, List<Attachment>> = emptyMap(),
    val selectedCurrency: CurrencyType = CurrencyType.INR,
    val searchQuery: String = "",
    val lastError: UdhaarAppError? = null
)

/**
 * Domain ViewModel managing ledger records, parties, repayments, attachments, and filtering.
 */
class LedgerViewModel(
    private val ledgerRepository: LedgerRepository,
    private val paymentRepository: PaymentRepository? = null
) : ViewModel() {

    private val selectedCurrencyFlow = MutableStateFlow(CurrencyType.INR)
    private val searchQueryFlow = MutableStateFlow("")
    private val lastErrorFlow = MutableStateFlow<UdhaarAppError?>(null)

    val state: StateFlow<LedgerDomainUiState> = combine(
        ledgerRepository.allParties,
        ledgerRepository.allTransactions,
        ledgerRepository.allPartialPayments,
        ledgerRepository.allAuditTrailEntries,
        ledgerRepository.allAttachments
    ) { parties, transactions, payments, audits, attachments ->
        val summaries = transactions.map { InterestCalculator.calculate(it) }
        LedgerDomainUiState(
            parties = parties,
            calculatedSummaries = summaries,
            paymentsByTransactionId = payments.groupBy { it.transactionId },
            auditByTransactionId = audits.groupBy { it.transactionId },
            attachmentsByTransactionId = attachments.groupBy { it.transactionId },
            selectedCurrency = selectedCurrencyFlow.value,
            searchQuery = searchQueryFlow.value,
            lastError = lastErrorFlow.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerDomainUiState()
    )

    fun setSelectedCurrency(currency: CurrencyType) {
        selectedCurrencyFlow.update { currency }
    }

    fun setSearchQuery(query: String) {
        searchQueryFlow.update { query }
    }

    fun clearError() {
        lastErrorFlow.update { null }
    }

    fun recordPartialPayment(
        transaction: UdhaarTransaction,
        amount: Double,
        paymentMode: PaymentMode = PaymentMode.CASH,
        note: String = "",
        totalAccruedAmount: Double = transaction.principalAmount,
        actorName: String = "You",
        onSuccess: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val id = paymentRepository?.recordPartialPayment(
                    transaction = transaction,
                    paymentAmount = amount,
                    paymentMode = paymentMode,
                    note = note,
                    totalAccruedAmount = totalAccruedAmount,
                    actorName = actorName
                ) ?: 0L
                onSuccess(id)
            } catch (t: Throwable) {
                lastErrorFlow.update { UdhaarAppError.fromThrowable(t) }
            }
        }
    }

    class Factory(
        private val ledgerRepository: LedgerRepository,
        private val paymentRepository: PaymentRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LedgerViewModel(ledgerRepository, paymentRepository) as T
        }
    }
}
