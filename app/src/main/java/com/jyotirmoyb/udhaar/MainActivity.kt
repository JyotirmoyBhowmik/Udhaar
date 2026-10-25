package com.jyotirmoyb.udhaar

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.notification.ReminderReceiver
import com.jyotirmoyb.udhaar.ui.ActiveScreen
import com.jyotirmoyb.udhaar.ui.UdhaarViewModel
import com.jyotirmoyb.udhaar.ui.components.AddEditPartyModal
import com.jyotirmoyb.udhaar.ui.components.AddEditUdhaarScreen
import com.jyotirmoyb.udhaar.ui.components.BiometricSecurityScreen
import com.jyotirmoyb.udhaar.ui.components.DashboardScreen
import com.jyotirmoyb.udhaar.ui.components.PartyDetailScreen
import com.jyotirmoyb.udhaar.ui.components.ProfileSettingsModal
import com.jyotirmoyb.udhaar.ui.components.ShareUdhaarAppModal
import com.jyotirmoyb.udhaar.ui.components.ThermalReceiptModal
import com.jyotirmoyb.udhaar.ui.components.TransactionDetailModal
import com.jyotirmoyb.udhaar.ui.components.UpiQrCodeModal
import com.jyotirmoyb.udhaar.ui.components.WhatsAppReminderModal
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.ui.theme.UdhaarTheme
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.LedgerBackupManager
import com.jyotirmoyb.udhaar.util.ThermalReceiptGenerator

open class MainActivity : FragmentActivity() {

    private var pendingReminderTransactionId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderReceiver.ensureNotificationChannel(this)
        extractReminderTransactionId(intent)

        val database = UdhaarDatabase.getInstance(applicationContext)
        val repository = UdhaarRepository(database.udhaarDao(), applicationContext)

        setContent {
            val viewModel: UdhaarViewModel = viewModel(
                factory = UdhaarViewModel.Factory(repository, applicationContext)
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            UdhaarTheme(
                themeMode = uiState.userSettings.themeMode,
                fontScaleMode = uiState.userSettings.fontScale
            ) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) { rootPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(rootPadding)
                            .consumeWindowInsets(rootPadding)
                    ) {
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
    val context = LocalContext.current

    var pendingJsonToSave by remember { mutableStateOf<String?>(null) }

    // Storage Access Framework: CreateDocument for Exporting JSON Backup / Single-Party Statement
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val jsonContent = pendingJsonToSave
        if (uri != null && jsonContent != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(jsonContent.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Ledger backup exported successfully.", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, "Failed to write JSON backup.", Toast.LENGTH_SHORT).show()
            }
        }
        pendingJsonToSave = null
    }

    // Storage Access Framework: OpenDocument for Importing JSON Backup
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!jsonString.isNullOrBlank()) {
                    viewModel.importLedgerBackupJson(jsonString) { result ->
                        result.onSuccess { count ->
                            Toast.makeText(
                                context,
                                "Imported $count ledger records successfully.",
                                Toast.LENGTH_LONG
                            ).show()
                        }.onFailure {
                            Toast.makeText(
                                context,
                                "Invalid Udhaar JSON backup file.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }.onFailure {
                Toast.makeText(context, "Could not read backup file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Enhancement A: Biometric / PIN App Lock Gate when opening the application
    if (!uiState.isAuthenticated) {
        BiometricSecurityScreen(
            expectedPin = uiState.userSettings.securityPin,
            onAuthenticated = viewModel::markAuthenticated
        )
        return
    }

    when (uiState.activeScreen) {
        ActiveScreen.DASHBOARD, ActiveScreen.PEOPLE_DIRECTORY -> {
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
                onPartyClick = { khata ->
                    viewModel.openPartyLedgerScreen(khata.party.partyId)
                },
                onQuickAddLoanForParty = viewModel::openAddTransactionForParty,
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
                onShareAppClick = viewModel::openShareAppModal,
                onLockAppClick = viewModel::lockApplicationNow,
                onSettingsClick = viewModel::openSettingsModal
            )
        }
        ActiveScreen.PARTY_DETAIL -> {
            val selectedPartyId = uiState.selectedPartyIdForSheet
            val selectedKhata = uiState.partyKhataList.find { it.party.partyId == selectedPartyId }
            if (selectedKhata != null) {
                PartyDetailScreen(
                    khata = selectedKhata,
                    enabledCurrencies = uiState.userSettings.enabledCurrencies,
                    onBack = viewModel::closePartyLedgerScreen,
                    onEditParty = {
                        viewModel.openEditPartyModal(selectedKhata.party)
                    },
                    onDeleteParty = {
                        viewModel.deleteParty(selectedKhata.party)
                    },
                    onAddLoanForParty = {
                        viewModel.openAddTransactionForParty(selectedKhata.party)
                    },
                    onTransactionClick = { summary ->
                        viewModel.openTransactionDetail(summary.transaction.id)
                    },
                    onWhatsAppClick = { summary ->
                        viewModel.openWhatsAppReminderModal(summary.transaction.id)
                    },
                    onPayPartyViaUpi = { amountOwed ->
                        viewModel.openPayPartyViaUpiModal(
                            partyName = selectedKhata.party.name,
                            partyUpiId = selectedKhata.party.upiId,
                            amountInr = amountOwed
                        )
                    },
                    onShareThermalReceipt = {
                        viewModel.openPartyStatementReceiptModal(selectedKhata.party.partyId)
                    },
                    onExportPartyStatement = {
                        viewModel.generateSinglePartyStatementJson(selectedKhata) { json ->
                            pendingJsonToSave = json
                            val safeName = selectedKhata.party.name.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase()
                            createDocumentLauncher.launch("udhaar_statement_${safeName}.json")
                        }
                    },
                    onSharePartyStatement = {
                        viewModel.generateSinglePartyStatementJson(selectedKhata) { json ->
                            val safeName = selectedKhata.party.name.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase()
                            LedgerBackupManager.shareBackupFile(
                                context = context,
                                jsonContent = json,
                                fileNamePrefix = "udhaar_statement_${safeName}"
                            )
                        }
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    viewModel.closePartyLedgerScreen()
                }
            }
        }
        ActiveScreen.ADD_EDIT -> {
            AddEditUdhaarScreen(
                initialTransaction = uiState.editingTransaction,
                preselectedPartyDraft = uiState.preselectedPartyDraft,
                savedParties = uiState.allParties,
                enabledCurrencies = uiState.userSettings.enabledCurrencies,
                defaultCurrency = uiState.summaryCurrency,
                onEnableCurrency = { curr ->
                    viewModel.toggleCurrencyEnabled(curr, true)
                },
                onSave = viewModel::saveUdhaarTransaction,
                onCancel = viewModel::navigateBackToDashboard
            )
        }
    }

    // Add / Edit Party Contact Profile Modal
    if (uiState.showAddEditPartyModal) {
        AddEditPartyModal(
            initialParty = uiState.editingParty,
            enabledCurrencies = uiState.userSettings.enabledCurrencies,
            defaultCurrency = uiState.summaryCurrency,
            onSave = viewModel::saveParty,
            onDismiss = viewModel::closeAddEditPartyModal
        )
    }

    // Detail & Partial Repayment Modal
    val detailTxId = uiState.detailTransactionId
    if (detailTxId != null) {
        val detailSummary = uiState.allCalculatedItems.find { it.transaction.id == detailTxId }
        if (detailSummary != null) {
            val payments = uiState.partialPaymentsByTx[detailTxId].orEmpty()
            val linkedParty = uiState.allParties.find { p ->
                (detailSummary.transaction.partyId != null && p.partyId == detailSummary.transaction.partyId) ||
                    p.name.trim().equals(detailSummary.transaction.partyName.trim(), ignoreCase = true)
            }
            TransactionDetailModal(
                summary = detailSummary,
                partialPayments = payments,
                linkedParty = linkedParty,
                onDismiss = viewModel::closeTransactionDetail,
                onRecordPartialPayment = { amount, paymentMode, note ->
                    viewModel.recordPartialPayment(detailSummary, amount, paymentMode, note)
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
                onPayPartyViaUpi = { partyName, partyUpiId, amountInr ->
                    viewModel.openPayPartyViaUpiModal(partyName, partyUpiId, amountInr)
                },
                onShareReceipt = {
                    viewModel.openTransactionReceiptModal(detailSummary.transaction.id)
                },
                onOpenWhatsApp = {
                    viewModel.openWhatsAppReminderModal(detailSummary.transaction.id)
                }
            )
        }
    }

    // Enhancement B: Thermal Statement Receipt Preview & Share Modal (Single Transaction)
    val receiptTxId = uiState.receiptModalTransactionId
    if (receiptTxId != null) {
        val receiptSummary = uiState.allCalculatedItems.find { it.transaction.id == receiptTxId }
        if (receiptSummary != null) {
            val payments = uiState.partialPaymentsByTx[receiptTxId].orEmpty()
            val linkedParty = uiState.allParties.find { p ->
                (receiptSummary.transaction.partyId != null && p.partyId == receiptSummary.transaction.partyId) ||
                    p.name.trim().equals(receiptSummary.transaction.partyName.trim(), ignoreCase = true)
            }
            val bitmap = remember(receiptSummary, payments, uiState.userSettings, linkedParty) {
                ThermalReceiptGenerator.createTransactionReceiptBitmap(
                    summary = receiptSummary,
                    partialPayments = payments,
                    userSettings = uiState.userSettings,
                    partyUpiId = linkedParty?.upiId
                )
            }
            val safeParty = receiptSummary.transaction.partyName.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase()
            val formattedNet = InterestCalculator.formatCurrency(
                receiptSummary.netBalanceDue,
                receiptSummary.transaction.currency
            )
            ThermalReceiptModal(
                title = "THERMAL STATEMENT RECEIPT",
                subtitle = "${receiptSummary.transaction.partyName.uppercase()} • $formattedNet",
                receiptBitmap = bitmap,
                fileNamePrefix = "udhaar_receipt_${safeParty}_${receiptSummary.transaction.id}",
                shareCaption = "Udhaar Statement Receipt for ${receiptSummary.transaction.partyName} — Net Balance: $formattedNet",
                onDismiss = viewModel::closeReceiptModal
            )
        }
    }

    // Enhancement B: Thermal Statement Receipt Preview & Share Modal (Party Khata Statement)
    val receiptPartyId = uiState.receiptModalPartyId
    if (receiptPartyId != null) {
        val khata = uiState.partyKhataList.find { it.party.partyId == receiptPartyId }
        if (khata != null) {
            val bitmap = remember(khata, uiState.userSettings) {
                ThermalReceiptGenerator.createPartyStatementReceiptBitmap(
                    khata = khata,
                    enabledCurrencies = uiState.userSettings.enabledCurrencies,
                    userSettings = uiState.userSettings
                )
            }
            val safeParty = khata.party.name.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase()
            ThermalReceiptModal(
                title = "PARTY KHATA STATEMENT RECEIPT",
                subtitle = "${khata.party.name.uppercase()} • ${khata.transactions.size} RECORDS",
                receiptBitmap = bitmap,
                fileNamePrefix = "udhaar_khata_statement_${safeParty}",
                shareCaption = "Udhaar Party Khata Statement for ${khata.party.name}",
                onDismiss = viewModel::closeReceiptModal
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

    // UPI QR Code Generator Modal (Global, Per-Transaction, or Pay-To-Party Mode)
    val qrState = uiState.qrModalState
    if (qrState != null) {
        UpiQrCodeModal(
            qrState = qrState,
            userSettings = uiState.userSettings,
            onSaveUpiSettings = { upiId, payeeName, currency ->
                viewModel.saveUserSettings(upiId, payeeName, currency)
            },
            onDismiss = viewModel::closeQrModal
        )
    }

    // Share Udhaar App Modal (Play Store QR + WhatsApp Invite)
    if (uiState.showShareAppModal) {
        ShareUdhaarAppModal(
            onDismiss = viewModel::closeShareAppModal
        )
    }

    // Profile, Currency Toggles, Theme, Backup & Admin Support Modal
    if (uiState.showSettingsModal) {
        ProfileSettingsModal(
            currentSettings = uiState.userSettings,
            onSave = { upiId, payeeName, currency, inrEnabled, nprEnabled, themeMode, fontScale, bioEnabled, pin, closeModal ->
                viewModel.saveUserSettings(
                    upiId = upiId,
                    payeeName = payeeName,
                    defaultCurrency = currency,
                    inrEnabled = inrEnabled,
                    nprEnabled = nprEnabled,
                    themeMode = themeMode,
                    fontScale = fontScale,
                    biometricLockEnabled = bioEnabled,
                    securityPin = pin,
                    closeModal = closeModal
                )
            },
            onLockAppNow = viewModel::lockApplicationNow,
            onOpenShareAppModal = {
                viewModel.closeSettingsModal()
                viewModel.openShareAppModal()
            },
            onExportFullLedgerSaf = {
                viewModel.generateFullLedgerBackupJson { json ->
                    pendingJsonToSave = json
                    createDocumentLauncher.launch("udhaar_full_ledger_backup.json")
                }
            },
            onShareFullLedgerFile = {
                viewModel.generateFullLedgerBackupJson { json ->
                    LedgerBackupManager.shareBackupFile(
                        context = context,
                        jsonContent = json,
                        fileNamePrefix = "udhaar_full_ledger_backup"
                    )
                }
            },
            onImportLedgerSaf = {
                openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onDismiss = viewModel::closeSettingsModal
        )
    }
}
