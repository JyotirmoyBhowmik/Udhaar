package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.UdhaarDatabase
import com.example.data.UdhaarRepository
import com.example.notification.ReminderReceiver
import com.example.ui.ActiveScreen
import com.example.ui.UdhaarViewModel
import com.example.ui.components.AddEditUdhaarScreen
import com.example.ui.components.DashboardScreen
import com.example.ui.components.ProfileSettingsModal
import com.example.ui.components.TransactionDetailModal
import com.example.ui.components.UpiQrCodeModal
import com.example.ui.components.WhatsAppReminderModal
import com.example.ui.theme.UdhaarTheme

class MainActivity : ComponentActivity() {

    private var pendingReminderTransactionId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderReceiver.ensureNotificationChannel(this)
        extractReminderTransactionId(intent)

        val database = UdhaarDatabase.getInstance(applicationContext)
        val repository = UdhaarRepository(database.udhaarDao(), applicationContext)

        setContent {
            UdhaarTheme {
                val viewModel: UdhaarViewModel = viewModel(
                    factory = UdhaarViewModel.Factory(repository, applicationContext)
                )
                val targetTxId = pendingReminderTransactionId
                LaunchedEffect(targetTxId) {
                    if (targetTxId != null && targetTxId > 0L) {
                        viewModel.openWhatsAppReminderModal(targetTxId)
                        pendingReminderTransactionId = null
                    }
                }
                UdhaarApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractReminderTransactionId(intent)
    }

    private fun extractReminderTransactionId(intent: Intent?) {
        val txId = intent?.getLongExtra(ReminderReceiver.EXTRA_TRANSACTION_ID, -1L) ?: -1L
        if (txId > 0L) {
            pendingReminderTransactionId = txId
        }
    }
}

@Composable
fun UdhaarApp(viewModel: UdhaarViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (uiState.activeScreen) {
        ActiveScreen.DASHBOARD -> {
            DashboardScreen(
                uiState = uiState,
                onSelectSummaryCurrency = viewModel::setSummaryCurrency,
                onSelectFilter = viewModel::setFilter,
                onSearchQueryChange = viewModel::setSearchQuery,
                onAddUdhaarClick = viewModel::openAddTransaction,
                onTransactionClick = { summary ->
                    viewModel.openTransactionDetail(summary.transaction.id)
                },
                onWhatsAppClick = { summary ->
                    viewModel.openWhatsAppReminderModal(summary.transaction.id)
                },
                onTransactionQrClick = { summary ->
                    viewModel.openTransactionQrModal(summary)
                },
                onGlobalQrClick = viewModel::openGlobalQrModal,
                onSettingsClick = viewModel::openSettingsModal
            )
        }
        ActiveScreen.ADD_EDIT -> {
            AddEditUdhaarScreen(
                initialTransaction = uiState.editingTransaction,
                defaultCurrency = uiState.summaryCurrency,
                onSave = viewModel::saveUdhaarTransaction,
                onCancel = viewModel::navigateBackToDashboard
            )
        }
    }

    // Detail & Partial Repayment Modal
    val detailTxId = uiState.detailTransactionId
    if (detailTxId != null) {
        val detailSummary = uiState.allCalculatedItems.find { it.transaction.id == detailTxId }
        if (detailSummary != null) {
            val payments = uiState.partialPaymentsByTx[detailTxId].orEmpty()
            TransactionDetailModal(
                summary = detailSummary,
                partialPayments = payments,
                onDismiss = viewModel::closeTransactionDetail,
                onRecordPartialPayment = { amount, note ->
                    viewModel.recordPartialPayment(detailSummary, amount, note)
                },
                onDeletePartialPayment = { payment ->
                    viewModel.deletePartialPayment(detailSummary, payment)
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
                onOpenWhatsApp = {
                    viewModel.openWhatsAppReminderModal(detailSummary.transaction.id)
                }
            )
        }
    }

    // WhatsApp Reminder Preview Modal (with Hide Exact Amount toggle)
    val whatsAppTxId = uiState.whatsAppPreviewTransactionId
    if (whatsAppTxId != null) {
        val reminderSummary = uiState.allCalculatedItems.find { it.transaction.id == whatsAppTxId }
        if (reminderSummary != null) {
            WhatsAppReminderModal(
                summary = reminderSummary,
                userSettings = uiState.userSettings,
                onDismiss = viewModel::closeWhatsAppReminderModal
            )
        }
    }

    // UPI QR Code Generator Modal (Global or Per-Transaction)
    val qrState = uiState.qrModalState
    if (qrState != null) {
        UpiQrCodeModal(
            qrState = qrState,
            userSettings = uiState.userSettings,
            onSaveUpiSettings = viewModel::saveUserSettings,
            onDismiss = viewModel::closeQrModal
        )
    }

    // Profile & UPI Settings Modal
    if (uiState.showSettingsModal) {
        ProfileSettingsModal(
            currentSettings = uiState.userSettings,
            onSave = viewModel::saveUserSettings,
            onDismiss = viewModel::closeSettingsModal
        )
    }
}
