package com.jyotirmoyb.udhaar.ui

import android.app.PendingIntent
import com.jyotirmoyb.udhaar.backup.DriveConnectionTestReport
import com.jyotirmoyb.udhaar.backup.DriveRemoteBackup
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.AuditTrailEntry
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatThreadMetadata
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.DateRangeFilter
import com.jyotirmoyb.udhaar.data.LedgerFilter
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.TransactionSortOption
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.ui.components.SuccessAnimationEvent
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.ParsedLedgerBackup
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper
import kotlin.math.abs

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
    val initialAmountPaise: Long? = null,
    val customUpiId: String? = null,
    val isPayToPartyMode: Boolean = false
) {
    constructor(
        transactionId: Long? = null,
        partyName: String? = null,
        initialAmountInr: Double?,
        customUpiId: String? = null,
        isPayToPartyMode: Boolean = false
    ) : this(
        transactionId = transactionId,
        partyName = partyName,
        initialAmountPaise = initialAmountInr?.let { InterestCalculator.rupeesToPaise(it) },
        customUpiId = customUpiId,
        isPayToPartyMode = isPayToPartyMode
    )

    val initialAmountInr: Double?
        get() = initialAmountPaise?.let { InterestCalculator.paiseToRupees(it) }
}

data class CurrencyLedgerTotals(
    val currency: CurrencyType,
    val totalToReceivePaise: Long = 0L,
    val totalToPayPaise: Long = 0L,
    val activeGivenCount: Int = 0,
    val activeTakenCount: Int = 0
) {
    constructor(
        currency: CurrencyType,
        totalToReceive: Double,
        totalToPay: Double,
        activeGivenCount: Int = 0,
        activeTakenCount: Int = 0
    ) : this(
        currency = currency,
        totalToReceivePaise = InterestCalculator.rupeesToPaise(totalToReceive),
        totalToPayPaise = InterestCalculator.rupeesToPaise(totalToPay),
        activeGivenCount = activeGivenCount,
        activeTakenCount = activeTakenCount
    )

    val netPositionPaise: Long
        get() = totalToReceivePaise - totalToPayPaise

    val totalToReceive: Double
        get() = InterestCalculator.paiseToRupees(totalToReceivePaise)

    val totalToPay: Double
        get() = InterestCalculator.paiseToRupees(totalToPayPaise)

    val netPosition: Double
        get() = InterestCalculator.paiseToRupees(netPositionPaise)
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
    val inrToReceivePaise: Long = 0L,
    val inrToPayPaise: Long = 0L,
    val nprToReceivePaise: Long = 0L,
    val nprToPayPaise: Long = 0L,
    val oldestOverdueDays: Long = 0L,
    val averageRepaymentDays: Double = 0.0,
    val trustScorePercent: Int = 100, // 0 to 100 based on on-time repayments
    val trustBadge: String = "EXCELLENT" // EXCELLENT, GOOD, FAIR, CAUTION, NEW
) {
    constructor(
        party: PartyEntity,
        transactions: List<CalculatedUdhaarSummary>,
        inrToReceive: Double,
        inrToPay: Double,
        nprToReceive: Double,
        nprToPay: Double,
        oldestOverdueDays: Long = 0L,
        averageRepaymentDays: Double = 0.0,
        trustScorePercent: Int = 100,
        trustBadge: String = "EXCELLENT"
    ) : this(
        party = party,
        transactions = transactions,
        inrToReceivePaise = InterestCalculator.rupeesToPaise(inrToReceive),
        inrToPayPaise = InterestCalculator.rupeesToPaise(inrToPay),
        nprToReceivePaise = InterestCalculator.rupeesToPaise(nprToReceive),
        nprToPayPaise = InterestCalculator.rupeesToPaise(nprToPay),
        oldestOverdueDays = oldestOverdueDays,
        averageRepaymentDays = averageRepaymentDays,
        trustScorePercent = trustScorePercent,
        trustBadge = trustBadge
    )

    val inrNetPaise: Long get() = inrToReceivePaise - inrToPayPaise
    val nprNetPaise: Long get() = nprToReceivePaise - nprToPayPaise
    val maxNetBalancePaise: Long get() = maxOf(abs(inrNetPaise), abs(nprNetPaise))
    val activeLoanCount: Int get() = transactions.count { !it.transaction.isSettled }

    val inrToReceive: Double get() = InterestCalculator.paiseToRupees(inrToReceivePaise)
    val inrToPay: Double get() = InterestCalculator.paiseToRupees(inrToPayPaise)
    val nprToReceive: Double get() = InterestCalculator.paiseToRupees(nprToReceivePaise)
    val nprToPay: Double get() = InterestCalculator.paiseToRupees(nprToPayPaise)
    val inrNet: Double get() = InterestCalculator.paiseToRupees(inrNetPaise)
    val nprNet: Double get() = InterestCalculator.paiseToRupees(nprNetPaise)
    val maxNetBalance: Double get() = InterestCalculator.paiseToRupees(maxNetBalancePaise)
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

data class BackupSelfTestReport(
    val passed: Boolean,
    val summaryMessage: String,
    val recordCountVerified: Int,
    val totalPrincipalPaiseVerified: Long,
    val recoveryKeyPreview: String = "",
    val testedAtMillis: Long = System.currentTimeMillis()
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
    val installmentPlansByTx: Map<Long, com.jyotirmoyb.udhaar.data.InstallmentPlan> = emptyMap(),
    val installmentsByPlan: Map<Long, List<com.jyotirmoyb.udhaar.data.Installment>> = emptyMap(),
    val showPlanBuilderTxId: Long? = null,
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
    // --- Stage W3: Guest Web Flow State ---
    val webShareTransactionId: Long? = null,
    val webShareResult: com.jyotirmoyb.udhaar.data.cloud.GuestWebLinkCreateResult? = null,
    val isGeneratingWebLink: Boolean = false,
    val showUpdatesScreen: Boolean = false,
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
    val driveResolutionPendingIntent: PendingIntent? = null,
    val driveConnectionTestReport: DriveConnectionTestReport? = null,
    val backupSelfTestReport: BackupSelfTestReport? = null,
    val pendingRestoreBackup: DriveRemoteBackup? = null,
    val pendingRestoreParsed: ParsedLedgerBackup? = null,
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
    val isInitialLoading: Boolean = false,
    val isAmountsHidden: Boolean = false,
    val showCoachMarks: Boolean = false,
    val isNeedsAttentionDismissed: Boolean = false,
    val undoSnackbarMessage: String? = null,
    val successAnimationEvent: SuccessAnimationEvent? = null,
    val dbEncryptionState: com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState =
        com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.ENCRYPTED,
    val blockingSqlCipherError: String? = null
) {
    val activeSummaryTotals: CurrencyLedgerTotals
        get() = if (summaryCurrency == CurrencyType.INR) inrTotals else nprTotals
}
