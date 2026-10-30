package com.jyotirmoyb.udhaar.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.AuditTrailEntry
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatMessageType
import com.jyotirmoyb.udhaar.data.ChatThreadMetadata
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.LedgerFilter
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.notification.ReminderScheduler
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.util.AttachmentManager
import com.jyotirmoyb.udhaar.backup.BackupPreviewSummary
import com.jyotirmoyb.udhaar.backup.DriveBackupCrypto
import com.jyotirmoyb.udhaar.backup.DriveErrorType
import com.jyotirmoyb.udhaar.backup.DriveRemoteBackup
import com.jyotirmoyb.udhaar.backup.DriveResult
import com.jyotirmoyb.udhaar.backup.DriveServiceHelper
import com.jyotirmoyb.udhaar.data.AutoBackupFrequency
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.data.DateRangeFilter
import com.jyotirmoyb.udhaar.data.TransactionSortOption
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.LedgerBackupManager
import com.jyotirmoyb.udhaar.util.PdfStatementGenerator
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper
import com.jyotirmoyb.udhaar.widget.UdhaarWidgetProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
    ADD_EDIT,
    SETTINGS
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

enum class PartySortOption(val label: String) {
    NAME("Name (A-Z)"),
    HIGHEST_BALANCE("Highest Net Balance"),
    MOST_OVERDUE("Most Overdue"),
    RECENT_INTERACTION("Recently Active")
}

data class PartyKhataSummary(
    val party: PartyEntity,
    val transactions: List<CalculatedUdhaarSummary>,
    val inrToReceive: Double,
    val inrToPay: Double,
    val nprToReceive: Double,
    val nprToPay: Double,
    val oldestOverdueDays: Long = 0L,
    val averageRepaymentDays: Double = 0.0,
    val trustScorePercent: Int = 100, // 0 to 100 based on on-time repayments
    val trustBadge: String = "EXCELLENT" // EXCELLENT, GOOD, FAIR, CAUTION, NEW
) {
    val inrNet: Double get() = inrToReceive - inrToPay
    val nprNet: Double get() = nprToReceive - nprToPay
    val activeLoanCount: Int get() = transactions.count { !it.transaction.isSettled }
    val maxNetBalance: Double get() = maxOf(Math.abs(inrNet), Math.abs(nprNet))
}

data class DuplicatePartyPair(
    val primaryParty: PartyEntity,
    val duplicateParty: PartyEntity,
    val matchReason: String // e.g. "Same Normalized Phone: +91 9876543210" or "Matching Name"
)

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
    val auditTrailByTx: Map<Long, List<AuditTrailEntry>> = emptyMap(),
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
    val receiptModalPartyId: Long? = null,
    // --- Phase 3: Verification & Payment Profile Sharing State ---
    val verificationShareTransactionId: Long? = null,
    val verificationShareLink: String? = null,
    val incomingVerificationPayload: VerificationCryptoHelper.SharedVerificationPayload? = null,
    val incomingPaymentProfilePayload: VerificationCryptoHelper.SharedPaymentProfilePayload? = null,
    val incomingPaymentProfileTargetPartyId: Long? = null,
    val showShareMyPaymentDetailsModal: Boolean = false,
    val shareMyPaymentDetailsTargetParty: PartyEntity? = null,
    val requestPaymentDetailsParty: PartyEntity? = null,
    // --- Phase 4: In-App Communication State ---
    val chatThreadsByKey: Map<String, ChatThreadMetadata> = emptyMap(),
    val chatMessagesByThreadKey: Map<String, List<ChatMessageEntity>> = emptyMap(),
    val unreadCountByTx: Map<Long, Int> = emptyMap(),
    val unreadCountByPartyName: Map<String, Int> = emptyMap(),
    val activeChatThreadKey: String? = null,
    val activeChatScope: ChatThreadScope = ChatThreadScope.TRANSACTION,
    val activeChatTransactionId: Long? = null,
    val activeChatPartyId: Long? = null,
    val activeChatPartyName: String = "",
    // --- Phase 5: Google Drive Backup & Restore State ---
    val driveRemoteBackups: List<DriveRemoteBackup> = emptyList(),
    val isDriveOperationInProgress: Boolean = false,
    val driveOperationStatusMessage: String? = null,
    val driveOperationError: String? = null,
    val pendingRestoreBackup: DriveRemoteBackup? = null,
    val pendingRestoreParsed: com.jyotirmoyb.udhaar.util.ParsedLedgerBackup? = null,
    val showRestorePreviewDialog: Boolean = false,
    val showBackupPassphraseDialog: Boolean = false,
    val showRestorePassphraseDialog: Boolean = false,
    val restoreSelectedBackup: DriveRemoteBackup? = null,
    // --- Phase 6: Documents & Attachments State ---
    val attachmentsByTx: Map<Long, List<Attachment>> = emptyMap(),
    val attachmentsByPayment: Map<Long, List<Attachment>> = emptyMap(),
    val activeViewerAttachment: Attachment? = null,
    val draftAttachments: List<Attachment> = emptyList(),
    // --- Phase 7: Better Contacts State ---
    val partySortOption: PartySortOption = PartySortOption.NAME,
    val duplicatePartyPairs: List<DuplicatePartyPair> = emptyList(),
    val showMergeDuplicatesModal: Boolean = false,
    val showInAppContactPicker: Boolean = false,
    val deviceContacts: List<com.jyotirmoyb.udhaar.util.DeviceContact> = emptyList(),
    val isLoadingContacts: Boolean = false,
    val transactionSortOption: TransactionSortOption = TransactionSortOption.RECENT_FIRST,
    val dateRangeFilter: DateRangeFilter = DateRangeFilter.ALL_TIME,
    val showOnboardingModal: Boolean = false,
    val isAmountsHidden: Boolean = false,
    val showCoachMarks: Boolean = false,
    val isNeedsAttentionDismissed: Boolean = false,
    val undoSnackbarMessage: String? = null
) {
    val activeSummaryTotals: CurrencyLedgerTotals
        get() = if (summaryCurrency == CurrencyType.INR) inrTotals else nprTotals
}

private data class LocalUiFilterState(
    val hasUnlockedThisSession: Boolean = false,
    val manualLockRequested: Boolean = false,
    val activeScreen: ActiveScreen = ActiveScreen.DASHBOARD,
    val previousScreenBeforeSettings: ActiveScreen = ActiveScreen.DASHBOARD,
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
    val receiptModalPartyId: Long? = null,
    val verificationShareTransactionId: Long? = null,
    val verificationShareLink: String? = null,
    val incomingVerificationPayload: VerificationCryptoHelper.SharedVerificationPayload? = null,
    val incomingPaymentProfilePayload: VerificationCryptoHelper.SharedPaymentProfilePayload? = null,
    val incomingPaymentProfileTargetPartyId: Long? = null,
    val showShareMyPaymentDetailsModal: Boolean = false,
    val shareMyPaymentDetailsTargetParty: PartyEntity? = null,
    val requestPaymentDetailsParty: PartyEntity? = null,
    val activeChatThreadKey: String? = null,
    val activeChatScope: ChatThreadScope = ChatThreadScope.TRANSACTION,
    val activeChatTransactionId: Long? = null,
    val activeChatPartyId: Long? = null,
    val activeChatPartyName: String = "",
    val driveRemoteBackups: List<DriveRemoteBackup> = emptyList(),
    val isDriveOperationInProgress: Boolean = false,
    val driveOperationStatusMessage: String? = null,
    val driveOperationError: String? = null,
    val pendingRestoreBackup: DriveRemoteBackup? = null,
    val pendingRestoreParsed: com.jyotirmoyb.udhaar.util.ParsedLedgerBackup? = null,
    val showRestorePreviewDialog: Boolean = false,
    val showBackupPassphraseDialog: Boolean = false,
    val showRestorePassphraseDialog: Boolean = false,
    val restoreSelectedBackup: DriveRemoteBackup? = null,
    val activeViewerAttachment: Attachment? = null,
    val draftAttachments: List<Attachment> = emptyList(),
    val partySortOption: PartySortOption = PartySortOption.NAME,
    val showMergeDuplicatesModal: Boolean = false,
    val showInAppContactPicker: Boolean = false,
    val deviceContacts: List<com.jyotirmoyb.udhaar.util.DeviceContact> = emptyList(),
    val isLoadingContacts: Boolean = false,
    val transactionSortOption: TransactionSortOption = TransactionSortOption.RECENT_FIRST,
    val dateRangeFilter: DateRangeFilter = DateRangeFilter.ALL_TIME,
    val showOnboardingDismissed: Boolean = false,
    val isAmountsHidden: Boolean = false,
    val showCoachMarks: Boolean = false,
    val isNeedsAttentionDismissed: Boolean = false,
    val undoSnackbarMessage: String? = null,
    val lastDeletedTransaction: UdhaarTransaction? = null,
    val lastSettledBeforeTransaction: UdhaarTransaction? = null
)

class UdhaarViewModel(
    private val repository: UdhaarRepository,
    private val appContext: Context? = null
) : ViewModel() {

    private val localState = MutableStateFlow(LocalUiFilterState())

    private val ledgerCoreFlow = combine(
        repository.allParties,
        repository.allTransactions,
        repository.allPartialPayments,
        repository.allAuditTrailEntries,
        repository.allAttachments
    ) { parties, transactions, partialPayments, auditEntries, attachments ->
        LedgerCoreBundle(parties, transactions, partialPayments, auditEntries, attachments)
    }

    private val chatBundleFlow = combine(
        repository.allChatThreads,
        repository.allChatMessages
    ) { threads, messages ->
        ChatBundle(threads, messages)
    }

    private data class LedgerCoreBundle(
        val parties: List<PartyEntity>,
        val transactions: List<UdhaarTransaction>,
        val partialPayments: List<PartialPayment>,
        val auditEntries: List<AuditTrailEntry>,
        val attachments: List<Attachment>
    )

    private data class ChatBundle(
        val threads: List<ChatThreadMetadata>,
        val messages: List<ChatMessageEntity>
    )

    val uiState: StateFlow<UdhaarUiState> = combine(
        ledgerCoreFlow,
        chatBundleFlow,
        repository.userSettings,
        localState
    ) { core, chat, settings, local ->
        val parties = core.parties
        val transactions = core.transactions
        val partialPayments = core.partialPayments
        val auditEntries = core.auditEntries
        val chatThreads = chat.threads
        val chatMessages = chat.messages

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

        val paymentsMap = partialPayments.groupBy { it.transactionId }
        val auditMap = auditEntries.groupBy { it.transactionId }
        val attachmentsByTx = core.attachments.filter { it.transactionId > 0L }.groupBy { it.transactionId }
        val attachmentsByPayment = core.attachments.filter { it.paymentId != null && it.paymentId > 0L }.groupBy { it.paymentId!! }

        val threadsByKey = chatThreads.associateBy { it.threadKey }
        val messagesByThreadKey = chatMessages.groupBy { it.threadKey }

        val unreadByTx = mutableMapOf<Long, Int>()
        val unreadByPartyName = mutableMapOf<String, Int>()
        for (msg in chatMessages) {
            if (msg.isUnreadForMe) {
                val txId = msg.transactionId
                if (txId != null && txId > 0L) {
                    unreadByTx[txId] = (unreadByTx[txId] ?: 0) + 1
                }
                val partyKey = msg.partyName.trim().lowercase()
                if (partyKey.isNotEmpty()) {
                    unreadByPartyName[partyKey] = (unreadByPartyName[partyKey] ?: 0) + 1
                }
            }
        }

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = cal.timeInMillis

        val query = local.searchQuery.trim().lowercase()
        val filtered = visibleCalculatedList.filter { item ->
            val tx = item.transaction
            val txPayments = paymentsMap[tx.id].orEmpty()
            val txAttachments = attachmentsByTx[tx.id].orEmpty()
            val needsConfirm = tx.needsMyConfirmation ||
                tx.verificationStatus == VerificationStatus.EDITED_AFTER_CONFIRM ||
                tx.verificationStatus == VerificationStatus.PENDING ||
                txPayments.any { it.needsMyAcknowledgement || !it.isAcknowledged }

            val matchesFilter = when (local.selectedFilter) {
                LedgerFilter.ALL -> true
                LedgerFilter.GIVEN -> tx.transactionType == TransactionType.GIVEN && !tx.isSettled
                LedgerFilter.TAKEN -> tx.transactionType == TransactionType.TAKEN && !tx.isSettled
                LedgerFilter.NEEDS_CONFIRMATION -> needsConfirm && !tx.isSettled
                LedgerFilter.OVERDUE -> item.isOverdue && !tx.isSettled
                LedgerFilter.INR -> tx.currency == CurrencyType.INR
                LedgerFilter.NPR -> tx.currency == CurrencyType.NPR
                LedgerFilter.SETTLED -> tx.isSettled
            }

            val matchesDateRange = when (local.dateRangeFilter) {
                DateRangeFilter.ALL_TIME -> true
                DateRangeFilter.THIS_MONTH -> tx.createdAtMillis >= startOfMonth
                DateRangeFilter.LAST_30_DAYS -> tx.createdAtMillis >= (now - 30L * 24 * 3600 * 1000)
                DateRangeFilter.LAST_90_DAYS -> tx.createdAtMillis >= (now - 90L * 24 * 3600 * 1000)
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

        val sortedFiltered = when (local.transactionSortOption) {
            TransactionSortOption.RECENT_FIRST -> filtered.sortedByDescending { it.transaction.createdAtMillis }
            TransactionSortOption.DUE_DATE_ASC -> filtered.sortedWith(
                compareBy<CalculatedUdhaarSummary> { it.transaction.expiryDateMillis ?: Long.MAX_VALUE }
                    .thenByDescending { it.transaction.createdAtMillis }
            )
            TransactionSortOption.AMOUNT_DESC -> filtered.sortedByDescending { it.netBalanceDue }
            TransactionSortOption.PARTY_NAME -> filtered.sortedBy { it.transaction.partyName.lowercase() }
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

            val overdueTxs = activePartyTxs.filter { it.isOverdue }
            val oldestOverdueDays = if (overdueTxs.isNotEmpty()) {
                val oldestExpiry = overdueTxs.mapNotNull { it.transaction.expiryDateMillis }.minOrNull()
                if (oldestExpiry != null && nowMillis > oldestExpiry) {
                    (nowMillis - oldestExpiry) / 86_400_000L
                } else 0L
            } else 0L

            // Calculate average repayment time for settled transactions
            val settledTxs = partyTxs.filter { it.transaction.isSettled }
            val averageRepaymentDays = if (settledTxs.isNotEmpty()) {
                val totalDays = settledTxs.sumOf { s ->
                    val end = s.transaction.settledAtMillis ?: nowMillis
                    maxOf(1L, (end - s.transaction.startDateMillis) / 86_400_000L)
                }
                totalDays.toDouble() / settledTxs.size
            } else 0.0

            // Trust score calculation (0 - 100%)
            // On-time settled transactions increase trust; overdue active ones decrease it
            val totalCompleted = settledTxs.size
            val onTimeSettled = settledTxs.count { s ->
                val expiry = s.transaction.expiryDateMillis
                val settledAt = s.transaction.settledAtMillis ?: nowMillis
                expiry == null || settledAt <= (expiry + 86_400_000L)
            }
            val overdueCount = overdueTxs.size

            val (trustScore, trustBadge) = when {
                partyTxs.isEmpty() -> Pair(100, "NEW")
                overdueCount == 0 && totalCompleted > 0 -> Pair(100, "EXCELLENT")
                overdueCount == 0 && totalCompleted == 0 -> Pair(95, "GOOD")
                overdueCount > 0 && totalCompleted > overdueCount -> {
                    val score = maxOf(40, (100 - (overdueCount * 25)))
                    Pair(score, "FAIR")
                }
                overdueCount > 0 -> {
                    val score = maxOf(10, (60 - (overdueCount * 20)))
                    Pair(score, "CAUTION")
                }
                else -> Pair(80, "GOOD")
            }

            PartyKhataSummary(
                party = party,
                transactions = partyTxs,
                inrToReceive = inrGiven,
                inrToPay = inrTaken,
                nprToReceive = nprGiven,
                nprToPay = nprTaken,
                oldestOverdueDays = oldestOverdueDays,
                averageRepaymentDays = averageRepaymentDays,
                trustScorePercent = trustScore,
                trustBadge = trustBadge
            )
        }

        // Detect duplicates among parties (by normalized phone or identical clean name)
        val duplicatePairs = mutableListOf<DuplicatePartyPair>()
        val phonePartyMap = mutableMapOf<String, PartyEntity>()
        val namePartyMap = mutableMapOf<String, PartyEntity>()

        for (p in parties) {
            val normPhone = InterestCalculator.normalizeToE164(p.phoneNumber, p.preferredCurrency)
            if (normPhone.isNotBlank()) {
                val existing = phonePartyMap[normPhone]
                if (existing != null && existing.partyId != p.partyId) {
                    duplicatePairs.add(
                        DuplicatePartyPair(
                            primaryParty = existing,
                            duplicateParty = p,
                            matchReason = "Same Phone Number ($normPhone)"
                        )
                    )
                } else {
                    phonePartyMap[normPhone] = p
                }
            }
            val cleanName = p.name.trim().lowercase()
            if (cleanName.isNotBlank()) {
                val existing = namePartyMap[cleanName]
                if (existing != null && existing.partyId != p.partyId && !duplicatePairs.any { it.duplicateParty.partyId == p.partyId && it.primaryParty.partyId == existing.partyId }) {
                    duplicatePairs.add(
                        DuplicatePartyPair(
                            primaryParty = existing,
                            duplicateParty = p,
                            matchReason = "Identical Name (${p.name})"
                        )
                    )
                } else {
                    namePartyMap[cleanName] = p
                }
            }
        }

        // Apply Sorting to khataList
        val sortedKhataList = when (local.partySortOption) {
            PartySortOption.NAME -> khataList.sortedWith(
                compareByDescending<PartyKhataSummary> { it.party.isFavorite }
                    .thenBy { it.party.name.lowercase() }
            )
            PartySortOption.HIGHEST_BALANCE -> khataList.sortedWith(
                compareByDescending<PartyKhataSummary> { it.party.isFavorite }
                    .thenByDescending { it.maxNetBalance }
                    .thenBy { it.party.name.lowercase() }
            )
            PartySortOption.MOST_OVERDUE -> khataList.sortedWith(
                compareByDescending<PartyKhataSummary> { it.party.isFavorite }
                    .thenByDescending { it.oldestOverdueDays }
                    .thenByDescending { it.transactions.count { t -> t.isOverdue } }
                    .thenBy { it.party.name.lowercase() }
            )
            PartySortOption.RECENT_INTERACTION -> khataList.sortedWith(
                compareByDescending<PartyKhataSummary> { it.party.isFavorite }
                    .thenByDescending { maxOf(it.party.lastInteractionMillis, it.party.createdAtMillis) }
                    .thenBy { it.party.name.lowercase() }
            )
        }

        val pQuery = local.peopleSearchQuery.trim().lowercase()
        val filteredKhataList = if (pQuery.isEmpty()) {
            sortedKhataList
        } else {
            sortedKhataList.filter { khata ->
                val p = khata.party
                p.name.lowercase().contains(pQuery) ||
                    p.phoneNumber.lowercase().contains(pQuery) ||
                    p.alternatePhoneNumber.lowercase().contains(pQuery) ||
                    p.upiId.lowercase().contains(pQuery) ||
                    p.email.lowercase().contains(pQuery) ||
                    p.tagOrNotes.lowercase().contains(pQuery) ||
                    p.notes.lowercase().contains(pQuery)
            }
        }

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
            filteredItems = sortedFiltered,
            allParties = combinedParties,
            partyKhataList = khataList,
            filteredPartyKhataList = filteredKhataList,
            partialPaymentsByTx = paymentsMap,
            auditTrailByTx = auditMap,
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
            receiptModalPartyId = local.receiptModalPartyId,
            verificationShareTransactionId = local.verificationShareTransactionId,
            verificationShareLink = local.verificationShareLink,
            incomingVerificationPayload = local.incomingVerificationPayload,
            incomingPaymentProfilePayload = local.incomingPaymentProfilePayload,
            incomingPaymentProfileTargetPartyId = local.incomingPaymentProfileTargetPartyId,
            showShareMyPaymentDetailsModal = local.showShareMyPaymentDetailsModal,
            shareMyPaymentDetailsTargetParty = local.shareMyPaymentDetailsTargetParty,
            requestPaymentDetailsParty = local.requestPaymentDetailsParty,
            chatThreadsByKey = threadsByKey,
            chatMessagesByThreadKey = messagesByThreadKey,
            unreadCountByTx = unreadByTx,
            unreadCountByPartyName = unreadByPartyName,
            activeChatThreadKey = local.activeChatThreadKey,
            activeChatScope = local.activeChatScope,
            activeChatTransactionId = local.activeChatTransactionId,
            activeChatPartyId = local.activeChatPartyId,
            activeChatPartyName = local.activeChatPartyName,
            driveRemoteBackups = local.driveRemoteBackups,
            isDriveOperationInProgress = local.isDriveOperationInProgress,
            driveOperationStatusMessage = local.driveOperationStatusMessage,
            driveOperationError = local.driveOperationError,
            pendingRestoreBackup = local.pendingRestoreBackup,
            pendingRestoreParsed = local.pendingRestoreParsed,
            showRestorePreviewDialog = local.showRestorePreviewDialog,
            showBackupPassphraseDialog = local.showBackupPassphraseDialog,
            showRestorePassphraseDialog = local.showRestorePassphraseDialog,
            restoreSelectedBackup = local.restoreSelectedBackup,
            attachmentsByTx = attachmentsByTx,
            attachmentsByPayment = attachmentsByPayment,
            activeViewerAttachment = local.activeViewerAttachment,
            draftAttachments = local.draftAttachments,
            partySortOption = local.partySortOption,
            duplicatePartyPairs = duplicatePairs,
            showMergeDuplicatesModal = local.showMergeDuplicatesModal,
            showInAppContactPicker = local.showInAppContactPicker,
            deviceContacts = local.deviceContacts,
            isLoadingContacts = local.isLoadingContacts,
            transactionSortOption = local.transactionSortOption,
            dateRangeFilter = local.dateRangeFilter,
            showOnboardingModal = !settings.hasCompletedOnboarding && !local.showOnboardingDismissed,
            isAmountsHidden = local.isAmountsHidden,
            showCoachMarks = local.showCoachMarks,
            isNeedsAttentionDismissed = local.isNeedsAttentionDismissed,
            undoSnackbarMessage = local.undoSnackbarMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UdhaarUiState()
    )

    fun markAuthenticated() {
        repository.resetPinFailedAttempts()
        localState.update {
            it.copy(
                hasUnlockedThisSession = true,
                manualLockRequested = false
            )
        }
    }

    fun verifyPin(candidatePin: String): com.jyotirmoyb.udhaar.security.PinVerificationResult {
        val result = repository.verifyPin(candidatePin)
        if (result.isSuccess) {
            markAuthenticated()
        }
        return result
    }

    fun getPinLockoutRemainingMillis(): Long = repository.getPinLockoutRemainingMillis()

    fun getPinFailedAttempts(): Int = repository.getPinFailedAttempts()

    fun lockApplicationNow() {
        localState.update {
            val returnScreen = if (it.activeScreen == ActiveScreen.SETTINGS) {
                it.previousScreenBeforeSettings
            } else {
                it.activeScreen
            }
            it.copy(
                manualLockRequested = true,
                showSettingsModal = false,
                activeScreen = returnScreen
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

    fun selectFontScale(fontScale: AppFontScale) {
        viewModelScope.launch {
            repository.setFontScale(fontScale)
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

    fun setTransactionSortOption(option: TransactionSortOption) {
        localState.update { it.copy(transactionSortOption = option) }
    }

    fun setDateRangeFilter(filter: DateRangeFilter) {
        localState.update { it.copy(dateRangeFilter = filter) }
    }

    fun completeOnboarding(pin: String, upiId: String, name: String) {
        viewModelScope.launch {
            repository.completeOnboarding(
                securityPin = pin.ifBlank { null },
                upiId = upiId.ifBlank { null },
                payeeName = name.ifBlank { null }
            )
            localState.update {
                it.copy(
                    showOnboardingDismissed = true,
                    showCoachMarks = true
                )
            }
        }
    }

    fun dismissCoachMarks() {
        localState.update { it.copy(showCoachMarks = false) }
    }

    fun toggleHideAmounts() {
        localState.update { it.copy(isAmountsHidden = !it.isAmountsHidden) }
    }

    fun dismissNeedsAttentionStrip() {
        localState.update { it.copy(isNeedsAttentionDismissed = true) }
    }

    fun dismissUndoSnackbar() {
        localState.update {
            it.copy(
                undoSnackbarMessage = null,
                lastDeletedTransaction = null,
                lastSettledBeforeTransaction = null
            )
        }
    }

    fun performUndoAction() {
        val current = localState.value
        val deletedTx = current.lastDeletedTransaction
        val settledBeforeTx = current.lastSettledBeforeTransaction
        localState.update {
            it.copy(
                undoSnackbarMessage = null,
                lastDeletedTransaction = null,
                lastSettledBeforeTransaction = null
            )
        }
        viewModelScope.launch {
            val myName = uiState.value.userSettings.payeeName.ifBlank { "You" }
            if (deletedTx != null) {
                repository.saveTransaction(deletedTx.copy(id = 0L), myProfileName = myName)
            } else if (settledBeforeTx != null) {
                repository.saveTransaction(settledBeforeTx, myProfileName = myName)
            }
        }
    }

    fun generatePartyStatementPdf(khata: PartyKhataSummary, onReady: (File) -> Unit) {
        if (appContext == null) return
        viewModelScope.launch {
            val file = PdfStatementGenerator.generatePartyStatementPdf(
                context = appContext,
                khata = khata,
                userSettings = uiState.value.userSettings,
                enabledCurrencies = uiState.value.userSettings.enabledCurrencies
            )
            onReady(file)
        }
    }

    fun generateFullLedgerPdf(onReady: (File) -> Unit) {
        if (appContext == null) return
        viewModelScope.launch {
            val file = PdfStatementGenerator.generateFullLedgerPdf(
                context = appContext,
                parties = uiState.value.allParties,
                khataList = uiState.value.partyKhataList,
                userSettings = uiState.value.userSettings,
                enabledCurrencies = uiState.value.userSettings.enabledCurrencies
            )
            onReady(file)
        }
    }

    fun openAddTransaction() {
        localState.update {
            it.copy(
                activeScreen = ActiveScreen.ADD_EDIT,
                editingTransaction = null,
                preselectedPartyDraft = null,
                detailTransactionId = null,
                draftAttachments = emptyList()
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
                detailTransactionId = null,
                draftAttachments = emptyList()
            )
        }
    }

    fun openEditTransaction(transaction: UdhaarTransaction) {
        localState.update {
            it.copy(
                activeScreen = ActiveScreen.ADD_EDIT,
                editingTransaction = transaction,
                preselectedPartyDraft = null,
                detailTransactionId = null,
                draftAttachments = emptyList()
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

    fun setPartySortOption(option: PartySortOption) {
        localState.update { it.copy(partySortOption = option) }
    }

    fun togglePartyFavorite(party: PartyEntity) {
        viewModelScope.launch {
            if (party.partyId > 0L) {
                repository.togglePartyFavorite(party.partyId, !party.isFavorite)
            } else {
                // If it's a synthetic party, persist it as a real party entity with favorite = true
                repository.saveParty(party.copy(partyId = 0L, isFavorite = true))
            }
        }
    }

    fun openMergeDuplicatesModal() {
        localState.update { it.copy(showMergeDuplicatesModal = true) }
    }

    fun closeMergeDuplicatesModal() {
        localState.update { it.copy(showMergeDuplicatesModal = false) }
    }

    fun mergeDuplicatePair(pair: DuplicatePartyPair) {
        viewModelScope.launch {
            repository.mergeParties(pair.primaryParty, pair.duplicateParty)
        }
    }

    fun openInAppContactPicker() {
        localState.update { it.copy(showInAppContactPicker = true) }
        loadDeviceContacts()
    }

    fun closeInAppContactPicker() {
        localState.update { it.copy(showInAppContactPicker = false) }
    }

    fun loadDeviceContacts() {
        if (appContext == null) return
        localState.update { it.copy(isLoadingContacts = true) }
        viewModelScope.launch {
            val contacts = com.jyotirmoyb.udhaar.util.DeviceContactHelper.fetchDeviceContacts(
                context = appContext,
                currency = localState.value.summaryCurrency ?: CurrencyType.INR,
                existingParties = uiState.value.allParties
            )
            localState.update {
                it.copy(
                    deviceContacts = contacts,
                    isLoadingContacts = false
                )
            }
        }
    }

    fun importSelectedContacts(selectedContacts: List<com.jyotirmoyb.udhaar.util.DeviceContact>) {
        viewModelScope.launch {
            val newParties = selectedContacts.map { c ->
                PartyEntity(
                    partyId = 0L,
                    name = c.name,
                    phoneNumber = c.normalizedPhoneNumber.ifBlank { c.rawPhoneNumber },
                    photoUri = c.photoUri,
                    email = c.email,
                    preferredCurrency = localState.value.summaryCurrency ?: CurrencyType.INR,
                    createdAtMillis = System.currentTimeMillis(),
                    lastInteractionMillis = System.currentTimeMillis()
                )
            }
            repository.saveParties(newParties)
            closeInAppContactPicker()
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
        localState.update {
            val prev = if (it.activeScreen != ActiveScreen.SETTINGS) it.activeScreen else it.previousScreenBeforeSettings
            it.copy(
                previousScreenBeforeSettings = prev,
                activeScreen = ActiveScreen.SETTINGS,
                showSettingsModal = true
            )
        }
    }

    fun closeSettingsModal() {
        localState.update {
            val nextScreen = if (it.activeScreen == ActiveScreen.SETTINGS) {
                it.previousScreenBeforeSettings
            } else {
                it.activeScreen
            }
            it.copy(
                activeScreen = nextScreen,
                showSettingsModal = false
            )
        }
    }

    fun openShareAppModal() {
        localState.update { it.copy(showShareAppModal = true) }
    }

    fun closeShareAppModal() {
        localState.update { it.copy(showShareAppModal = false) }
    }

    // --- Phase 3: Two-Party Verification & Payment Profile Sharing Actions ---
    fun requestVerificationForTransaction(transaction: UdhaarTransaction) {
        viewModelScope.launch {
            val (updatedTx, link) = repository.prepareVerificationShare(transaction)
            localState.update {
                it.copy(
                    verificationShareTransactionId = updatedTx.id,
                    verificationShareLink = link
                )
            }
        }
    }

    fun closeVerificationShareModal() {
        localState.update {
            it.copy(
                verificationShareTransactionId = null,
                verificationShareLink = null
            )
        }
    }

    /**
     * Handles an incoming Android App Link (`https://jyotirmoyb.com/u/<token>` or `.../u/p/<token>`)
     * or a pasted/scanned verification or payment profile token.
     */
    fun handleIncomingDeepLinkOrToken(
        urlOrToken: String,
        targetPartyId: Long? = null
    ): Boolean {
        val profilePayload = VerificationCryptoHelper.parsePaymentProfileLink(urlOrToken)
        if (profilePayload != null) {
            localState.update {
                it.copy(
                    incomingPaymentProfilePayload = profilePayload,
                    incomingPaymentProfileTargetPartyId = targetPartyId
                )
            }
            return true
        }

        val verificationPayload = VerificationCryptoHelper.parseVerificationLink(urlOrToken)
        if (verificationPayload != null) {
            localState.update {
                it.copy(
                    incomingVerificationPayload = verificationPayload
                )
            }
            return true
        }
        return false
    }

    fun confirmOrDisputeIncomingVerification(
        confirm: Boolean,
        disputeReason: String = ""
    ) {
        val payload = localState.value.incomingVerificationPayload ?: return
        viewModelScope.launch {
            val savedTx = repository.processIncomingVerificationResponse(
                payload = payload,
                confirm = confirm,
                disputeReason = disputeReason
            )
            localState.update {
                it.copy(
                    incomingVerificationPayload = null,
                    detailTransactionId = savedTx.id,
                    summaryCurrency = savedTx.currency
                )
            }
        }
    }

    fun dismissIncomingVerification() {
        localState.update { it.copy(incomingVerificationPayload = null) }
    }

    fun confirmOrDisputeExistingRecord(
        transaction: UdhaarTransaction,
        confirm: Boolean,
        disputeReason: String = ""
    ) {
        viewModelScope.launch {
            repository.confirmOrDisputeExistingTransaction(
                transaction = transaction,
                confirm = confirm,
                disputeReason = disputeReason
            )
        }
    }

    fun acknowledgePartialPayment(
        transaction: UdhaarTransaction,
        payment: PartialPayment
    ) {
        viewModelScope.launch {
            repository.acknowledgePartialPayment(
                transaction = transaction,
                payment = payment,
                actorName = uiState.value.userSettings.payeeName.ifBlank { "You" }
            )
        }
    }

    fun openShareMyPaymentDetailsModal(targetParty: PartyEntity? = null) {
        localState.update {
            it.copy(
                showShareMyPaymentDetailsModal = true,
                shareMyPaymentDetailsTargetParty = targetParty
            )
        }
    }

    fun closeShareMyPaymentDetailsModal() {
        localState.update {
            it.copy(
                showShareMyPaymentDetailsModal = false,
                shareMyPaymentDetailsTargetParty = null
            )
        }
    }

    fun openRequestPartyPaymentDetailsModal(party: PartyEntity) {
        localState.update {
            it.copy(requestPaymentDetailsParty = party)
        }
    }

    fun closeRequestPartyPaymentDetailsModal() {
        localState.update {
            it.copy(requestPaymentDetailsParty = null)
        }
    }

    fun acceptIncomingPaymentProfile() {
        val payload = localState.value.incomingPaymentProfilePayload ?: return
        val targetPartyId = localState.value.incomingPaymentProfileTargetPartyId
        viewModelScope.launch {
            val updatedParty = repository.acceptSharedPaymentProfile(payload, targetPartyId)
            localState.update {
                it.copy(
                    incomingPaymentProfilePayload = null,
                    incomingPaymentProfileTargetPartyId = null,
                    requestPaymentDetailsParty = null,
                    selectedPartyIdForSheet = if (it.activeScreen == ActiveScreen.PARTY_DETAIL) {
                        updatedParty.partyId
                    } else {
                        it.selectedPartyIdForSheet
                    }
                )
            }
        }
    }

    fun dismissIncomingPaymentProfile() {
        localState.update {
            it.copy(
                incomingPaymentProfilePayload = null,
                incomingPaymentProfileTargetPartyId = null
            )
        }
    }

    fun setCloudAccountState(
        consentGranted: Boolean,
        userId: String,
        email: String
    ) {
        viewModelScope.launch {
            repository.setCloudAccountState(
                consentGranted = consentGranted,
                userId = userId,
                email = email
            )
        }
    }

    fun deleteMyCloudData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteMyCloudData()
            onComplete()
        }
    }

    fun saveUdhaarTransaction(transaction: UdhaarTransaction) {
        viewModelScope.launch {
            val myName = uiState.value.userSettings.payeeName.ifBlank { "You" }
            val savedId = repository.saveTransaction(transaction, myProfileName = myName)
            val savedTx = transaction.copy(id = savedId)

            val staged = localState.value.draftAttachments
            for (draft in staged) {
                repository.insertAttachment(draft.copy(id = 0L, transactionId = savedId))
            }

            appContext?.let { ctx ->
                if (savedTx.reminderDateMillis != null && !savedTx.isSettled) {
                    ReminderScheduler.scheduleReminder(ctx, savedTx)
                } else {
                    ReminderScheduler.cancelReminder(ctx, savedId)
                }
                if (savedTx.expiryDateMillis != null && !savedTx.isSettled) {
                    val formatted = InterestCalculator.formatCurrency(savedTx.principalAmount, savedTx.currency)
                    com.jyotirmoyb.udhaar.notification.RecurringReminderScheduler.scheduleRemindersForTransaction(ctx, savedTx, formatted)
                } else {
                    com.jyotirmoyb.udhaar.notification.RecurringReminderScheduler.cancelRemindersForTransaction(ctx, savedId)
                }
                UdhaarWidgetProvider.updateAllWidgets(ctx)
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
                    summaryCurrency = savedTx.currency,
                    draftAttachments = emptyList()
                )
            }
        }
    }

    fun deleteTransaction(transaction: UdhaarTransaction) {
        viewModelScope.launch {
            appContext?.let { ctx ->
                ReminderScheduler.cancelReminder(ctx, transaction.id)
                com.jyotirmoyb.udhaar.notification.RecurringReminderScheduler.cancelRemindersForTransaction(ctx, transaction.id)
                UdhaarWidgetProvider.updateAllWidgets(ctx)
            }
            repository.deleteTransaction(transaction)
            localState.update {
                it.copy(
                    detailTransactionId = null,
                    whatsAppPreviewTransactionId = null,
                    lastDeletedTransaction = transaction,
                    lastSettledBeforeTransaction = null,
                    undoSnackbarMessage = "Deleted udhaar for ${transaction.partyName}"
                )
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
            val myName = uiState.value.userSettings.payeeName.ifBlank { "You" }
            val actor = if (recordedByCounterparty) {
                summary.transaction.partyName
            } else {
                myName
            }
            repository.recordPartialPayment(
                transaction = summary.transaction,
                paymentAmount = amount,
                paymentMode = paymentMode,
                note = note,
                totalAccruedAmount = summary.grossTotal,
                actorName = actor,
                proofAttachment = proofAttachment
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

    // --- Phase 6: Documents & Attachments Management ---
    fun openAttachmentViewer(attachment: Attachment) {
        localState.update { it.copy(activeViewerAttachment = attachment) }
    }

    fun closeAttachmentViewer() {
        localState.update { it.copy(activeViewerAttachment = null) }
    }

    fun stageDraftAttachment(attachment: Attachment) {
        localState.update { it.copy(draftAttachments = it.draftAttachments + attachment) }
    }

    fun removeDraftAttachment(attachment: Attachment) {
        localState.update { it.copy(draftAttachments = it.draftAttachments.filter { a -> a != attachment }) }
        appContext?.let { ctx ->
            AttachmentManager.deleteAttachmentFile(ctx, attachment.localPath)
        }
    }

    fun clearDraftAttachments() {
        localState.update { it.copy(draftAttachments = emptyList()) }
    }

    fun addAttachmentToTransaction(transactionId: Long, attachment: Attachment) {
        viewModelScope.launch {
            repository.insertAttachment(attachment.copy(id = 0L, transactionId = transactionId))
        }
    }

    fun addAttachmentToPayment(transactionId: Long, paymentId: Long, attachment: Attachment) {
        viewModelScope.launch {
            repository.insertAttachment(
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
            repository.deleteAttachment(attachment)
            if (localState.value.activeViewerAttachment?.id == attachment.id) {
                localState.update { it.copy(activeViewerAttachment = null) }
            }
        }
    }

    fun toggleSettled(summary: CalculatedUdhaarSummary) {
        val beforeTx = summary.transaction
        viewModelScope.launch {
            repository.toggleFullSettlement(
                transaction = beforeTx,
                totalAccruedAmount = summary.grossTotal
            )
            appContext?.let { ctx ->
                if (!beforeTx.isSettled) {
                    ReminderScheduler.cancelReminder(ctx, beforeTx.id)
                }
            }
            localState.update {
                it.copy(
                    lastSettledBeforeTransaction = beforeTx,
                    lastDeletedTransaction = null,
                    undoSnackbarMessage = if (!beforeTx.isSettled) {
                        "Settled udhaar for ${beforeTx.partyName}"
                    } else {
                        "Reopened udhaar for ${beforeTx.partyName}"
                    }
                )
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
        fontScale: AppFontScale? = null,
        biometricLockEnabled: Boolean? = null,
        securityPin: String? = null,
        phoneNumber: String? = null,
        profilePhotoUri: String? = null,
        cloudVerificationConsent: Boolean? = null,
        closeModal: Boolean = true
    ) {
        viewModelScope.launch {
            repository.updateSettings(
                upiId = upiId,
                payeeName = payeeName,
                defaultCurrency = defaultCurrency,
                inrEnabled = inrEnabled,
                nprEnabled = nprEnabled,
                themeMode = themeMode,
                fontScale = fontScale,
                biometricLockEnabled = biometricLockEnabled,
                securityPin = securityPin,
                phoneNumber = phoneNumber,
                profilePhotoUri = profilePhotoUri,
                cloudVerificationConsent = cloudVerificationConsent
            )
            if (closeModal) {
                localState.update {
                    val nextScreen = if (it.activeScreen == ActiveScreen.SETTINGS) {
                        it.previousScreenBeforeSettings
                    } else {
                        it.activeScreen
                    }
                    it.copy(
                        activeScreen = nextScreen,
                        showSettingsModal = false
                    )
                }
            }
        }
    }

    // --- Phase 4: In-App Communication Actions ---
    fun openTransactionChat(transactionId: Long) {
        val tx = uiState.value.allCalculatedItems.firstOrNull { it.transaction.id == transactionId }?.transaction
        val threadKey = UdhaarRepository.transactionThreadKey(transactionId)
        val partyName = tx?.partyName.orEmpty()
        viewModelScope.launch {
            repository.getOrCreateThreadMetadata(
                threadKey = threadKey,
                scope = ChatThreadScope.TRANSACTION,
                transactionId = transactionId,
                remoteId = tx?.remoteId.orEmpty(),
                partyId = tx?.partyId,
                partyName = partyName
            )
            repository.markThreadRead(threadKey)
        }
        localState.update {
            it.copy(
                activeChatThreadKey = threadKey,
                activeChatScope = ChatThreadScope.TRANSACTION,
                activeChatTransactionId = transactionId,
                activeChatPartyId = tx?.partyId,
                activeChatPartyName = partyName
            )
        }
    }

    fun openPartyGeneralChat(party: PartyEntity) {
        val threadKey = UdhaarRepository.partyGeneralThreadKey(party.name)
        viewModelScope.launch {
            repository.getOrCreateThreadMetadata(
                threadKey = threadKey,
                scope = ChatThreadScope.PARTY_GENERAL,
                transactionId = null,
                remoteId = "",
                partyId = party.partyId.takeIf { it > 0L },
                partyName = party.name
            )
            repository.markThreadRead(threadKey)
        }
        localState.update {
            it.copy(
                activeChatThreadKey = threadKey,
                activeChatScope = ChatThreadScope.PARTY_GENERAL,
                activeChatTransactionId = null,
                activeChatPartyId = party.partyId,
                activeChatPartyName = party.name
            )
        }
    }

    fun openChatFromNotification(
        threadKey: String,
        transactionId: Long?,
        partyName: String
    ) {
        val isTxThread = transactionId != null && transactionId > 0L
        val scope = if (isTxThread) ChatThreadScope.TRANSACTION else ChatThreadScope.PARTY_GENERAL
        viewModelScope.launch {
            repository.markThreadRead(threadKey)
        }
        localState.update {
            it.copy(
                activeChatThreadKey = threadKey,
                activeChatScope = scope,
                activeChatTransactionId = transactionId?.takeIf { id -> id > 0L },
                activeChatPartyName = partyName
            )
        }
    }

    fun closeChatSheet() {
        val currentKey = localState.value.activeChatThreadKey
        if (!currentKey.isNullOrBlank()) {
            viewModelScope.launch {
                repository.markThreadRead(currentKey)
            }
        }
        localState.update {
            it.copy(
                activeChatThreadKey = null,
                activeChatTransactionId = null,
                activeChatPartyId = null,
                activeChatPartyName = ""
            )
        }
    }

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
    ) {
        viewModelScope.launch {
            repository.sendChatMessage(
                threadKey = threadKey,
                scope = scope,
                transaction = transaction,
                partyId = partyId,
                partyName = partyName,
                messageType = messageType,
                textContent = textContent,
                attachmentUri = attachmentUri,
                attachmentMimeType = attachmentMimeType,
                fromCounterparty = fromCounterparty
            )
            // If the user is actively viewing this thread and sent/received a message, mark read if outgoing
            if (!fromCounterparty && localState.value.activeChatThreadKey == threadKey) {
                repository.markThreadRead(threadKey)
            }
        }
    }

    fun requestDueDateExtension(
        transaction: UdhaarTransaction,
        proposedDueDateMillis: Long,
        note: String = "",
        requestedByCounterparty: Boolean = false
    ) {
        viewModelScope.launch {
            repository.requestDueDateExtension(
                transaction = transaction,
                proposedDueDateMillis = proposedDueDateMillis,
                note = note,
                requestedByCounterparty = requestedByCounterparty
            )
        }
    }

    fun respondToExtensionRequest(
        transaction: UdhaarTransaction,
        message: ChatMessageEntity,
        accept: Boolean
    ) {
        viewModelScope.launch {
            val updatedTx = repository.respondToExtensionRequest(
                transaction = transaction,
                message = message,
                accept = accept
            )
            if (accept && !updatedTx.isSettled && updatedTx.reminderDateMillis != null) {
                appContext?.let { ctx ->
                    ReminderScheduler.scheduleReminder(ctx, updatedTx)
                }
            }
        }
    }

    fun markThreadRead(threadKey: String) {
        viewModelScope.launch {
            repository.markThreadRead(threadKey)
        }
    }

    fun setTypingIndicator(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String,
        isCounterpartyTyping: Boolean
    ) {
        viewModelScope.launch {
            repository.setTypingIndicator(
                threadKey = threadKey,
                scope = scope,
                transactionId = transactionId,
                remoteId = remoteId,
                partyId = partyId,
                partyName = partyName,
                isCounterpartyTyping = isCounterpartyTyping
            )
        }
    }

    fun toggleThreadMute(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String
    ) {
        viewModelScope.launch {
            repository.toggleThreadMute(
                threadKey = threadKey,
                scope = scope,
                transactionId = transactionId,
                remoteId = remoteId,
                partyId = partyId,
                partyName = partyName
            )
        }
    }

    fun toggleThreadBlock(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String
    ) {
        viewModelScope.launch {
            repository.toggleThreadBlock(
                threadKey = threadKey,
                scope = scope,
                transactionId = transactionId,
                remoteId = remoteId,
                partyId = partyId,
                partyName = partyName
            )
        }
    }

    fun reportAbusiveUserInThread(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String,
        reason: String
    ) {
        viewModelScope.launch {
            repository.reportAbusiveUserInThread(
                threadKey = threadKey,
                scope = scope,
                transactionId = transactionId,
                remoteId = remoteId,
                partyId = partyId,
                partyName = partyName,
                reason = reason
            )
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

    // --- Phase 5: Google Drive Backup & Restore Operations ---
    private val driveHelper: DriveServiceHelper = DriveServiceHelper()

    fun setDriveAccountState(email: String, accessToken: String) {
        viewModelScope.launch {
            repository.updateDriveAccount(email, accessToken)
            refreshDriveBackups(accessToken)
        }
    }

    fun clearDriveAccountState() {
        viewModelScope.launch {
            repository.clearDriveAccount()
            localState.update {
                it.copy(
                    driveRemoteBackups = emptyList(),
                    driveOperationStatusMessage = null,
                    driveOperationError = null
                )
            }
        }
    }

    fun openBackupPassphraseDialog() {
        localState.update { it.copy(showBackupPassphraseDialog = true, driveOperationError = null) }
    }

    fun closeBackupPassphraseDialog() {
        localState.update { it.copy(showBackupPassphraseDialog = false) }
    }

    fun openRestorePassphraseDialog(backup: DriveRemoteBackup) {
        localState.update {
            it.copy(
                restoreSelectedBackup = backup,
                showRestorePassphraseDialog = true,
                driveOperationError = null
            )
        }
    }

    fun closeRestorePassphraseDialog() {
        localState.update { it.copy(showRestorePassphraseDialog = false, restoreSelectedBackup = null) }
    }

    fun closeRestorePreviewDialog() {
        localState.update {
            it.copy(
                showRestorePreviewDialog = false,
                pendingRestoreBackup = null,
                pendingRestoreParsed = null
            )
        }
    }

    fun clearDriveOperationState() {
        localState.update {
            it.copy(
                driveOperationStatusMessage = null,
                driveOperationError = null
            )
        }
    }

    fun refreshDriveBackups(tokenOverride: String? = null) {
        viewModelScope.launch {
            val token = tokenOverride ?: uiState.value.userSettings.driveAccessToken
            if (token.isBlank()) return@launch

            localState.update {
                it.copy(
                    isDriveOperationInProgress = true,
                    driveOperationStatusMessage = "Checking Google Drive backups...",
                    driveOperationError = null
                )
            }
            when (val res = driveHelper.listBackups(token)) {
                is DriveResult.Success -> {
                    localState.update {
                        it.copy(
                            driveRemoteBackups = res.data,
                            isDriveOperationInProgress = false,
                            driveOperationStatusMessage = null
                        )
                    }
                }
                is DriveResult.Error -> {
                    localState.update {
                        it.copy(
                            isDriveOperationInProgress = false,
                            driveOperationStatusMessage = null,
                            driveOperationError = res.message
                        )
                    }
                }
            }
        }
    }

    fun uploadDriveBackup(
        passphrase: String,
        savePassphraseForAutoBackup: Boolean = false,
        onComplete: ((Result<DriveRemoteBackup>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val token = uiState.value.userSettings.driveAccessToken
            if (token.isBlank()) {
                val err = "Google Drive is not signed in."
                localState.update { it.copy(driveOperationError = err) }
                onComplete?.invoke(Result.failure(IllegalStateException(err)))
                return@launch
            }

            localState.update {
                it.copy(
                    isDriveOperationInProgress = true,
                    driveOperationStatusMessage = "Encrypting ledger on device with AES-256...",
                    driveOperationError = null,
                    showBackupPassphraseDialog = false
                )
            }

            try {
                val snapshot = repository.getFullLedgerBackupSnapshot()
                val settings = uiState.value.userSettings
                val attachments = repository.getLocalAttachmentsMap()

                val settingsMap = mapOf(
                    "upiId" to settings.upiId,
                    "payeeName" to settings.payeeName,
                    "phoneNumber" to settings.phoneNumber,
                    "profilePhotoUri" to settings.profilePhotoUri,
                    "defaultCurrency" to settings.defaultCurrency.name,
                    "inrEnabled" to settings.inrEnabled,
                    "nprEnabled" to settings.nprEnabled,
                    "themeMode" to settings.themeMode.name,
                    "fontScale" to settings.fontScale.name
                )

                val plainJson = LedgerBackupManager.buildBackupJson(
                    parties = snapshot.parties,
                    transactions = snapshot.transactions,
                    partialPayments = snapshot.partialPayments,
                    chatThreads = snapshot.chatThreads,
                    chatMessages = snapshot.chatMessages,
                    settings = settingsMap,
                    attachments = attachments
                )

                val preview = BackupPreviewSummary(
                    partiesCount = snapshot.parties.size,
                    transactionsCount = snapshot.transactions.size,
                    paymentsCount = snapshot.partialPayments.size,
                    attachmentsCount = attachments.size,
                    appVersion = "7.0",
                    createdAtMillis = System.currentTimeMillis()
                )

                val encryptedBytes = DriveBackupCrypto.encrypt(
                    plainBytes = plainJson.toByteArray(Charsets.UTF_8),
                    passphrase = passphrase,
                    preview = preview
                )

                localState.update {
                    it.copy(driveOperationStatusMessage = "Uploading encrypted backup to hidden AppData folder...")
                }

                val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "udhaar_backup_${dateStr}.udhaar"

                when (val uploadRes = driveHelper.uploadBackup(token, fileName, encryptedBytes, preview)) {
                    is DriveResult.Success -> {
                        repository.updateDriveLastBackup(System.currentTimeMillis(), uploadRes.data.sizeBytes)
                        if (savePassphraseForAutoBackup) {
                            repository.saveAutoBackupPassphrase(passphrase)
                        }
                        localState.update {
                            it.copy(
                                isDriveOperationInProgress = false,
                                driveOperationStatusMessage = "Backup completed successfully! (${uploadRes.data.sizeBytes / 1024} KB)"
                            )
                        }
                        refreshDriveBackups(token)
                        onComplete?.invoke(Result.success(uploadRes.data))
                    }
                    is DriveResult.Error -> {
                        localState.update {
                            it.copy(
                                isDriveOperationInProgress = false,
                                driveOperationStatusMessage = null,
                                driveOperationError = uploadRes.message
                            )
                        }
                        onComplete?.invoke(Result.failure(IOException(uploadRes.message)))
                    }
                }
            } catch (e: Exception) {
                localState.update {
                    it.copy(
                        isDriveOperationInProgress = false,
                        driveOperationStatusMessage = null,
                        driveOperationError = e.localizedMessage ?: "Backup encryption failed."
                    )
                }
                onComplete?.invoke(Result.failure(e))
            }
        }
    }

    fun downloadAndDecryptDriveBackup(
        backup: DriveRemoteBackup,
        passphrase: String,
        onPreviewReady: ((Result<com.jyotirmoyb.udhaar.util.ParsedLedgerBackup>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val token = uiState.value.userSettings.driveAccessToken
            if (token.isBlank()) {
                val err = "Google Drive is not signed in."
                localState.update { it.copy(driveOperationError = err) }
                onPreviewReady?.invoke(Result.failure(IllegalStateException(err)))
                return@launch
            }

            localState.update {
                it.copy(
                    isDriveOperationInProgress = true,
                    driveOperationStatusMessage = "Downloading encrypted backup from Google Drive...",
                    driveOperationError = null,
                    showRestorePassphraseDialog = false
                )
            }

            when (val dlRes = driveHelper.downloadBackup(token, backup.fileId)) {
                is DriveResult.Success -> {
                    localState.update {
                        it.copy(driveOperationStatusMessage = "Decrypting with AES-256 on device...")
                    }
                    try {
                        val decryptedBytes = DriveBackupCrypto.decrypt(dlRes.data, passphrase)
                        val plainJson = String(decryptedBytes, Charsets.UTF_8)
                        val parsed = LedgerBackupManager.parseBackupJson(plainJson)

                        localState.update {
                            it.copy(
                                isDriveOperationInProgress = false,
                                driveOperationStatusMessage = null,
                                pendingRestoreBackup = backup,
                                pendingRestoreParsed = parsed,
                                showRestorePreviewDialog = true
                            )
                        }
                        onPreviewReady?.invoke(Result.success(parsed))
                    } catch (e: Exception) {
                        localState.update {
                            it.copy(
                                isDriveOperationInProgress = false,
                                driveOperationStatusMessage = null,
                                driveOperationError = e.localizedMessage ?: "Decryption failed. Incorrect passphrase."
                            )
                        }
                        onPreviewReady?.invoke(Result.failure(e))
                    }
                }
                is DriveResult.Error -> {
                    localState.update {
                        it.copy(
                            isDriveOperationInProgress = false,
                            driveOperationStatusMessage = null,
                            driveOperationError = dlRes.message
                        )
                    }
                    onPreviewReady?.invoke(Result.failure(IOException(dlRes.message)))
                }
            }
        }
    }

    fun executeDriveRestore(
        replaceExisting: Boolean,
        onComplete: ((Result<Int>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val parsed = localState.value.pendingRestoreParsed
            if (parsed == null) {
                val err = "No pending backup to restore."
                localState.update { it.copy(driveOperationError = err) }
                onComplete?.invoke(Result.failure(IllegalStateException(err)))
                return@launch
            }

            localState.update {
                it.copy(
                    isDriveOperationInProgress = true,
                    driveOperationStatusMessage = if (replaceExisting) "Replacing ledger with backup..." else "Merging backup into ledger...",
                    showRestorePreviewDialog = false
                )
            }

            try {
                val restoredCount = repository.restoreFullLedgerBackup(parsed, replaceExisting)
                localState.update {
                    it.copy(
                        isDriveOperationInProgress = false,
                        driveOperationStatusMessage = "Restored successfully: $restoredCount transactions, ${parsed.parties.size} parties.",
                        pendingRestoreBackup = null,
                        pendingRestoreParsed = null
                    )
                }
                onComplete?.invoke(Result.success(restoredCount))
            } catch (e: Exception) {
                localState.update {
                    it.copy(
                        isDriveOperationInProgress = false,
                        driveOperationStatusMessage = null,
                        driveOperationError = "Failed to restore backup: ${e.localizedMessage}"
                    )
                }
                onComplete?.invoke(Result.failure(e))
            }
        }
    }

    fun deleteDriveBackup(backup: DriveRemoteBackup) {
        viewModelScope.launch {
            val token = uiState.value.userSettings.driveAccessToken
            if (token.isBlank()) return@launch
            driveHelper.deleteBackup(token, backup.fileId)
            refreshDriveBackups(token)
        }
    }

    fun updateDriveAutoBackupSettings(
        frequency: AutoBackupFrequency,
        wifiOnly: Boolean,
        passphraseToSave: String? = null
    ) {
        viewModelScope.launch {
            repository.updateDriveAutoBackupSettings(frequency, wifiOnly, passphraseToSave)
        }
    }

    fun clearDriveOperationError() {
        localState.update { it.copy(driveOperationError = null) }
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
