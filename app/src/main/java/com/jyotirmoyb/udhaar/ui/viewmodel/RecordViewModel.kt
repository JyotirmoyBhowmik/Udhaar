package com.jyotirmoyb.udhaar.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.ReminderChannel
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.repository.AttachmentRepository
import com.jyotirmoyb.udhaar.data.repository.LedgerRepository
import com.jyotirmoyb.udhaar.data.repository.PaymentRepository
import com.jyotirmoyb.udhaar.data.repository.SettingsRepository
import com.jyotirmoyb.udhaar.notification.RecurringReminderScheduler
import com.jyotirmoyb.udhaar.notification.ReminderScheduler
import com.jyotirmoyb.udhaar.ui.ActiveScreen
import com.jyotirmoyb.udhaar.ui.components.SuccessAnimationEvent
import com.jyotirmoyb.udhaar.ui.state.AppStateHolder
import com.jyotirmoyb.udhaar.util.AttachmentManager
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.widget.UdhaarWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecordUiState(
    val detailTransactionId: Long? = null,
    val whatsAppPreviewTransactionId: Long? = null,
    val receiptModalTransactionId: Long? = null,
    val activeViewerAttachment: Attachment? = null,
    val draftAttachments: List<Attachment> = emptyList(),
    val partialPaymentsByTx: Map<Long, List<PartialPayment>> = emptyMap(),
    val attachmentsByTx: Map<Long, List<Attachment>> = emptyMap(),
    val attachmentsByPayment: Map<Long, List<Attachment>> = emptyMap()
)

@HiltViewModel
class RecordViewModel @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val paymentRepository: PaymentRepository,
    private val attachmentRepository: AttachmentRepository,
    private val settingsRepository: SettingsRepository,
    private val appStateHolder: AppStateHolder,
    @ApplicationContext private val appContext: Context? = null
) : ViewModel() {

    private val _whatsAppPreviewTransactionId = MutableStateFlow<Long?>(null)
    val whatsAppPreviewTransactionId: StateFlow<Long?> = _whatsAppPreviewTransactionId.asStateFlow()

    private val _receiptModalTransactionId = MutableStateFlow<Long?>(null)
    val receiptModalTransactionId: StateFlow<Long?> = _receiptModalTransactionId.asStateFlow()

    private val _activeViewerAttachment = MutableStateFlow<Attachment?>(null)
    val activeViewerAttachment: StateFlow<Attachment?> = _activeViewerAttachment.asStateFlow()

    private val _draftAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val draftAttachments: StateFlow<List<Attachment>> = _draftAttachments.asStateFlow()

    val uiState: StateFlow<RecordUiState> = combine(
        ledgerRepository.allPartialPayments,
        attachmentRepository.allAttachments,
        combine(
            appStateHolder.detailTransactionId,
            _whatsAppPreviewTransactionId,
            _receiptModalTransactionId,
            _activeViewerAttachment,
            _draftAttachments
        ) { detailId, waId, receiptId, viewerAtt, drafts ->
            ModalParams(detailId, waId, receiptId, viewerAtt, drafts)
        }
    ) { payments, attachments, params ->
        val paymentsMap = payments.groupBy { it.transactionId }
        val attByTx = attachments.filter { it.transactionId > 0L }.groupBy { it.transactionId }
        val attByPayment = attachments
            .mapNotNull { att -> att.paymentId?.takeIf { it > 0L }?.let { pid -> pid to att } }
            .groupBy({ it.first }, { it.second })

        RecordUiState(
            detailTransactionId = params.detailTransactionId,
            whatsAppPreviewTransactionId = params.whatsAppPreviewTransactionId,
            receiptModalTransactionId = params.receiptModalTransactionId,
            activeViewerAttachment = params.activeViewerAttachment,
            draftAttachments = params.draftAttachments,
            partialPaymentsByTx = paymentsMap,
            attachmentsByTx = attByTx,
            attachmentsByPayment = attByPayment
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordUiState()
    )

    private data class ModalParams(
        val detailTransactionId: Long?,
        val whatsAppPreviewTransactionId: Long?,
        val receiptModalTransactionId: Long?,
        val activeViewerAttachment: Attachment?,
        val draftAttachments: List<Attachment>
    )

    fun openWhatsAppReminderModal(transactionId: Long) {
        _whatsAppPreviewTransactionId.value = transactionId
    }

    fun closeWhatsAppReminderModal() {
        _whatsAppPreviewTransactionId.value = null
    }

    fun openReceiptModal(transactionId: Long) {
        _receiptModalTransactionId.value = transactionId
    }

    fun closeReceiptModal() {
        _receiptModalTransactionId.value = null
    }

    fun openAttachmentViewer(attachment: Attachment) {
        _activeViewerAttachment.value = attachment
    }

    fun closeAttachmentViewer() {
        _activeViewerAttachment.value = null
    }

    fun stageDraftAttachment(attachment: Attachment) {
        _draftAttachments.update { it + attachment }
    }

    fun removeDraftAttachment(attachment: Attachment) {
        _draftAttachments.update { current -> current.filter { it != attachment } }
        appContext?.let { ctx ->
            AttachmentManager.deleteAttachmentFile(ctx, attachment.localPath)
        }
    }

    fun clearDraftAttachments() {
        _draftAttachments.value = emptyList()
    }

    fun addAttachmentToTransaction(transactionId: Long, attachment: Attachment) {
        viewModelScope.launch {
            attachmentRepository.insertAttachment(attachment.copy(id = 0L, transactionId = transactionId))
        }
    }

    fun addAttachmentToPayment(transactionId: Long, paymentId: Long, attachment: Attachment) {
        viewModelScope.launch {
            attachmentRepository.insertAttachment(
                attachment.copy(
                    id = 0L,
                    transactionId = transactionId,
                    paymentId = paymentId
                )
            )
        }
    }

    fun deleteAttachment(attachment: Attachment) {
        viewModelScope.launch {
            attachmentRepository.deleteAttachment(attachment)
            if (_activeViewerAttachment.value?.id == attachment.id) {
                _activeViewerAttachment.value = null
            }
        }
    }

    fun saveTransaction(
        transaction: UdhaarTransaction,
        attachments: List<Attachment> = emptyList(),
        onSuccess: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val isNew = transaction.id == 0L
            val savedId = if (isNew) {
                ledgerRepository.saveTransaction(transaction)
            } else {
                ledgerRepository.updateTransaction(transaction)
                transaction.id
            }
            val savedTx = transaction.copy(id = savedId)

            val staged = _draftAttachments.value
            for (draft in staged) {
                attachmentRepository.insertAttachment(draft.copy(id = 0L, transactionId = savedId))
            }
            _draftAttachments.value = emptyList()

            appContext?.let { ctx ->
                if (savedTx.reminderDateMillis != null && !savedTx.isSettled) {
                    ReminderScheduler.scheduleReminder(ctx, savedTx)
                } else {
                    ReminderScheduler.cancelReminder(ctx, savedId)
                }
                if (savedTx.expiryDateMillis != null && !savedTx.isSettled) {
                    val formatted = InterestCalculator.formatCurrency(savedTx.principalAmount, savedTx.currency)
                    RecurringReminderScheduler.scheduleRemindersForTransaction(ctx, savedTx, formatted)
                } else {
                    RecurringReminderScheduler.cancelRemindersForTransaction(ctx, savedId)
                }
                UdhaarWidgetProvider.updateAllWidgets(ctx)
            }

            appStateHolder.setSummaryCurrency(savedTx.currency)
            appStateHolder.setSuccessAnimation(SuccessAnimationEvent(message = "Record saved"))
            if (appStateHolder.selectedPartyIdForSheet.value != null) {
                appStateHolder.switchMainTab(ActiveScreen.PARTY_DETAIL)
            } else {
                appStateHolder.switchMainTab(ActiveScreen.DASHBOARD)
            }
            onSuccess(savedId)
        }
    }

    fun deleteTransaction(transaction: UdhaarTransaction) {
        viewModelScope.launch {
            val staged = _draftAttachments.value
            appContext?.let { ctx ->
                for (draft in staged) {
                    AttachmentManager.deleteAttachmentFile(ctx, draft.localPath)
                }
                ReminderScheduler.cancelReminder(ctx, transaction.id)
                RecurringReminderScheduler.cancelRemindersForTransaction(ctx, transaction.id)
                UdhaarWidgetProvider.updateAllWidgets(ctx)
            }
            ledgerRepository.deleteTransaction(transaction)
            _draftAttachments.value = emptyList()
            _whatsAppPreviewTransactionId.value = null
            appStateHolder.closeTransactionDetail()
            appStateHolder.showUndoSnackbar(
                message = "Deleted udhaar for ${transaction.partyName}",
                deletedTx = transaction
            )
            if (appStateHolder.activeScreen.value == ActiveScreen.ADD_EDIT) {
                if (appStateHolder.selectedPartyIdForSheet.value != null) {
                    appStateHolder.switchMainTab(ActiveScreen.PARTY_DETAIL)
                } else {
                    appStateHolder.switchMainTab(ActiveScreen.DASHBOARD)
                }
            }
        }
    }

    fun recordPartialPayment(
        summary: CalculatedUdhaarSummary,
        amount: Double,
        paymentMode: PaymentMode = PaymentMode.CASH,
        note: String,
        recordedByCounterparty: Boolean = false,
        proofAttachment: Attachment? = null
    ) {
        viewModelScope.launch {
            val myName = settingsRepository.userSettings.first().payeeName.ifBlank { "You" }
            val actor = if (recordedByCounterparty) {
                summary.transaction.partyName
            } else {
                myName
            }
            paymentRepository.recordPartialPayment(
                transaction = summary.transaction,
                paymentAmount = amount,
                paymentMode = paymentMode,
                note = note,
                totalAccruedAmount = summary.grossTotal,
                actorName = actor,
                proofAttachment = proofAttachment
            )
            appStateHolder.setSuccessAnimation(SuccessAnimationEvent(message = "Payment recorded"))
        }
    }

    fun deletePartialPayment(
        summary: CalculatedUdhaarSummary,
        payment: PartialPayment
    ) {
        viewModelScope.launch {
            paymentRepository.deletePartialPayment(
                transaction = summary.transaction,
                payment = payment,
                totalAccruedAmount = summary.grossTotal
            )
        }
    }

    fun toggleSettled(summary: CalculatedUdhaarSummary) {
        val beforeTx = summary.transaction
        viewModelScope.launch {
            ledgerRepository.toggleFullSettlement(
                transaction = beforeTx,
                totalAccruedAmount = summary.grossTotal
            )
            appContext?.let { ctx ->
                if (!beforeTx.isSettled) {
                    ReminderScheduler.cancelReminder(ctx, beforeTx.id)
                }
            }
            val msg = if (!beforeTx.isSettled) "Settled udhaar for ${beforeTx.partyName}" else "Reopened udhaar for ${beforeTx.partyName}"
            appStateHolder.showUndoSnackbar(
                message = msg,
                settledBefore = beforeTx
            )
            if (!beforeTx.isSettled) {
                appStateHolder.setSuccessAnimation(SuccessAnimationEvent(message = "Payment recorded"))
            }
        }
    }

    fun snoozeTransaction(recordId: Long, days: Int) {
        viewModelScope.launch {
            ledgerRepository.snoozeTransaction(recordId, days)
        }
    }

    fun clearTransactionSnooze(recordId: Long) {
        viewModelScope.launch {
            ledgerRepository.clearTransactionSnooze(recordId)
        }
    }

    fun stopTransactionReminders(recordId: Long) {
        viewModelScope.launch {
            ledgerRepository.stopTransactionReminders(recordId)
        }
    }

    fun enableTransactionReminders(recordId: Long) {
        viewModelScope.launch {
            ledgerRepository.enableTransactionReminders(recordId)
        }
    }

    fun recordReminderSent(recordId: Long, level: Int, channel: ReminderChannel) {
        viewModelScope.launch {
            ledgerRepository.recordReminderEvent(recordId, level, channel)
        }
    }
}
