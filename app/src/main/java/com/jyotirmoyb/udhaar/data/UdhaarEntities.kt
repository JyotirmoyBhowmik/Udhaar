package com.jyotirmoyb.udhaar.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode

enum class TransactionType(val label: String, val badgeLabel: String) {
    GIVEN("Given (Lent)", "GIVEN • TO RECEIVE"),
    TAKEN("Taken (Borrowed)", "TAKEN • TO PAY")
}

enum class CurrencyType(val code: String, val symbol: String, val displayName: String, val defaultCountryCode: String) {
    INR("INR", "₹", "INR (₹)", "+91"),
    NPR("NPR", "रू", "NPR (रू)", "+977")
}

enum class PaymentMode(val label: String, val shortBadge: String) {
    CASH("Cash", "CASH"),
    ONLINE("Online", "ONLINE"),
    UPI("UPI", "UPI"),
    PARTY_ACCOUNT("Party Account", "PARTY ACC")
}

enum class InterestType(val label: String) {
    SIMPLE("Simple Interest"),
    COMPOUND("Compound Interest")
}

enum class InterestRatePeriod(val label: String, val shortSuffix: String) {
    PER_MONTH("% per month", "% / mo"),
    PER_ANNUM("% per annum", "% / yr")
}

enum class CompoundFrequency(val label: String, val periodsPerYear: Int) {
    MONTHLY("Monthly", 12),
    QUARTERLY("Quarterly", 4),
    YEARLY("Yearly", 1)
}

enum class VerificationStatus(val label: String, val shortBadge: String) {
    LOCAL_ONLY("Local Only", "LOCAL"),
    PENDING("Pending Verification", "PENDING"),
    CONFIRMED("Verified by Both", "VERIFIED ✓"),
    DISPUTED("Disputed", "DISPUTED !"),
    EDITED_AFTER_CONFIRM("Edited • Re-confirm", "RE-CONFIRM")
}

enum class AuditEventType(val label: String) {
    CREATED("Record Created"),
    SHARED("Shared for Verification"),
    VIEWED("Viewed by Counterparty"),
    CONFIRMED("Confirmed by Counterparty"),
    DISPUTED("Disputed by Counterparty"),
    EDITED("Terms Edited"),
    PAYMENT_RECORDED("Partial Payment Recorded"),
    PAYMENT_ACKNOWLEDGED("Payment Acknowledged"),
    SETTLED("Marked Settled")
}

enum class LedgerFilter(val label: String) {
    ALL("All"),
    GIVEN("Given"),
    TAKEN("Taken"),
    NEEDS_CONFIRMATION("Needs my confirmation"),
    OVERDUE("Overdue/Expired"),
    INR("INR (₹)"),
    NPR("NPR (रू)"),
    SETTLED("Settled")
}

@Entity(tableName = "parties")
data class PartyEntity(
    @PrimaryKey(autoGenerate = true)
    val partyId: Long = 0L,
    val name: String,
    val phoneNumber: String = "",
    val upiId: String = "",
    val email: String = "",
    val tagOrNotes: String = "",
    val preferredCurrency: CurrencyType = CurrencyType.INR,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "udhaar_transactions")
data class UdhaarTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val partyId: Long? = null,
    val transactionType: TransactionType = TransactionType.GIVEN,
    val partyName: String,
    val phoneNumber: String = "",
    val currency: CurrencyType = CurrencyType.INR,
    val principalAmount: Double,
    val amountPaid: Double = 0.0,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val isInterestEnabled: Boolean = false,
    val interestType: InterestType = InterestType.SIMPLE,
    val interestRate: Double = 0.0,
    val interestRatePeriod: InterestRatePeriod = InterestRatePeriod.PER_MONTH,
    val compoundFrequency: CompoundFrequency = CompoundFrequency.MONTHLY,
    val startDateMillis: Long = System.currentTimeMillis(),
    val expiryDateMillis: Long? = null,
    val reminderDateMillis: Long? = null,
    val notes: String = "",
    val isSettled: Boolean = false,
    val settledAtMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    // --- Phase 3: Two-Party Verification Fields ---
    val remoteId: String = "",
    val verificationStatus: VerificationStatus = VerificationStatus.LOCAL_ONLY,
    val verifiedAtMillis: Long? = null,
    val counterpartyUserId: String = "",
    val contentHash: String = "",
    val needsMyConfirmation: Boolean = false,
    val shareToken: String = "",
    val disputeReason: String = "",
    val lenderName: String = "",
    val borrowerName: String = ""
)

@Entity(
    tableName = "partial_payments",
    foreignKeys = [
        ForeignKey(
            entity = UdhaarTransaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["transactionId"])]
)
data class PartialPayment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val transactionId: Long,
    val amount: Double,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val paidAtMillis: Long = System.currentTimeMillis(),
    val note: String = "",
    // --- Phase 3: Two-Party Partial Payment Acknowledgement ---
    val remotePaymentId: String = "",
    val recordedByUserId: String = "",
    val recordedByName: String = "",
    val isAcknowledged: Boolean = false,
    val acknowledgedAtMillis: Long? = null,
    val needsMyAcknowledgement: Boolean = false
)

@Entity(
    tableName = "audit_trail_entries",
    foreignKeys = [
        ForeignKey(
            entity = UdhaarTransaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["transactionId"])]
)
data class AuditTrailEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val transactionId: Long,
    val remoteEventId: String = "",
    val eventType: AuditEventType = AuditEventType.CREATED,
    val actorUserId: String = "",
    val actorName: String = "You",
    val summaryText: String = "",
    val contentHashSnapshot: String = "",
    val isAcknowledged: Boolean = false,
    val eventTimeMillis: Long = System.currentTimeMillis()
)

data class UserSettings(
    val upiId: String = "",
    val payeeName: String = "",
    val phoneNumber: String = "",
    val profilePhotoUri: String = "",
    val defaultCurrency: CurrencyType = CurrencyType.INR,
    val inrEnabled: Boolean = true,
    val nprEnabled: Boolean = false, // Disabled by default per user specification
    val themeMode: AppThemeMode = AppThemeMode.MONOCHROME_LIGHT,
    val fontScale: AppFontScale = AppFontScale.STANDARD,
    val biometricLockEnabled: Boolean = false,
    val hasPinConfigured: Boolean = false,
    val cloudVerificationConsent: Boolean = false,
    val cloudUserId: String = "",
    val cloudUserEmail: String = ""
) {
    val enabledCurrencies: List<CurrencyType>
        get() = buildList {
            if (inrEnabled) add(CurrencyType.INR)
            if (nprEnabled) add(CurrencyType.NPR)
            if (isEmpty()) add(CurrencyType.INR)
        }
}
