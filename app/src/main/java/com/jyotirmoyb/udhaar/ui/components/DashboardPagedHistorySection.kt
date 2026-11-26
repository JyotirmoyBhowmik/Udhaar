package com.jyotirmoyb.udhaar.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.TransactionHistoryProjection
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.ui.UdhaarUiState
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary

internal data class DashboardLedgerCardCallbacks(
    val onSelectAdaptiveTx: (Long) -> Unit,
    val onTransactionClick: (CalculatedUdhaarSummary) -> Unit,
    val onOpenTransactionChat: (CalculatedUdhaarSummary) -> Unit,
    val onWhatsAppClick: (CalculatedUdhaarSummary) -> Unit,
    val onTransactionQrClick: (CalculatedUdhaarSummary) -> Unit,
    val onSnoozeTransaction: (recordId: Long, days: Int) -> Unit,
    val onStopTransactionReminders: (recordId: Long) -> Unit,
    val onEnableTransactionReminders: (recordId: Long) -> Unit,
    val onRequestExtension: (CalculatedUdhaarSummary) -> Unit
)

internal fun LazyListScope.dashboardPagedHistoryItems(
    pagedItems: LazyPagingItems<TransactionHistoryProjection>,
    summaryById: Map<Long, CalculatedUdhaarSummary>,
    partyPhotoByName: Map<String, String>,
    uiState: UdhaarUiState,
    reducedMotion: Boolean,
    context: Context,
    callbacks: DashboardLedgerCardCallbacks
) {
    items(
        count = pagedItems.itemCount,
        key = pagedItems.itemKey { proj -> "paged_tx_${proj.id}" }
    ) { index ->
        val projection = pagedItems[index]
        val summary = projection?.let { summaryById[it.id] }
        if (summary != null) {
            DashboardLedgerCardItem(
                summary = summary,
                partyPhotoByName = partyPhotoByName,
                uiState = uiState,
                reducedMotion = reducedMotion,
                context = context,
                callbacks = callbacks
            )
        }
    }
}

@Composable
internal fun LazyItemScope.DashboardLedgerCardItem(
    summary: CalculatedUdhaarSummary,
    partyPhotoByName: Map<String, String>,
    uiState: UdhaarUiState,
    reducedMotion: Boolean,
    context: Context,
    callbacks: DashboardLedgerCardCallbacks
) {
    val partyPhotoUri = partyPhotoByName[summary.transaction.partyName.trim().lowercase()].orEmpty()
    Box(modifier = udhaarAnimateItem(reducedMotion)) {
        SwipeableTransactionCardItem(
            summary = summary,
            partyPhotoUri = partyPhotoUri,
            isAmountsHidden = uiState.isAmountsHidden,
            unreadCount = uiState.unreadCountByTx[summary.transaction.id] ?: 0,
            attachmentsCount = uiState.attachmentsByTx[summary.transaction.id]?.size ?: 0,
            installments = uiState.installmentPlansByTx[summary.transaction.id]
                ?.let { uiState.installmentsByPlan[it.id] }
                .orEmpty(),
            defaultExpanded = false,
            onCardClick = {
                callbacks.onSelectAdaptiveTx(summary.transaction.id)
                callbacks.onTransactionClick(summary)
            },
            onChatClick = { callbacks.onOpenTransactionChat(summary) },
            onWhatsAppClick = { callbacks.onWhatsAppClick(summary) },
            onQrClick = {
                val tx = summary.transaction
                if (tx.currency == CurrencyType.INR &&
                    tx.transactionType == TransactionType.GIVEN &&
                    !tx.isSettled
                ) {
                    callbacks.onTransactionQrClick(summary)
                } else {
                    callbacks.onTransactionClick(summary)
                }
            },
            onSnoozeClick = callbacks.onSnoozeTransaction,
            onStopRemindersClick = callbacks.onStopTransactionReminders,
            onEnableRemindersClick = callbacks.onEnableTransactionReminders,
            onBorrowerAction = { s, actionType ->
                dispatchBorrowerAction(
                    context = context,
                    userSettings = uiState.userSettings,
                    summary = s,
                    actionType = actionType,
                    onRequestExtension = callbacks.onRequestExtension
                )
            }
        )
    }
}
