package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType(val label: String, val badgeLabel: String) {
    GIVEN("Given (Lent)", "GIVEN • TO RECEIVE"),
    TAKEN("Taken (Borrowed)", "TAKEN • TO PAY")
}

enum class CurrencyType(val code: String, val symbol: String, val displayName: String, val defaultCountryCode: String) {
    INR("INR", "₹", "INR (₹)", "+91"),
    NPR("NPR", "रू", "NPR (रू)", "+977")
}

enum class PaymentMode(val label: String) {
    CASH("Cash"),
    ONLINE("Online"),
    UPI("UPI"),
    PARTY_ACCOUNT("Party Account")
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

enum class LedgerFilter(val label: String) {
    ALL("All"),
    GIVEN("Given"),
    TAKEN("Taken"),
    OVERDUE("Overdue/Expired"),
    INR("INR (₹)"),
    NPR("NPR (रू)"),
    SETTLED("Settled")
}

@Entity(tableName = "udhaar_transactions")
data class UdhaarTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
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
    val createdAtMillis: Long = System.currentTimeMillis()
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
    val paidAtMillis: Long = System.currentTimeMillis(),
    val note: String = ""
)

data class UserSettings(
    val upiId: String = "",
    val payeeName: String = "",
    val defaultCurrency: CurrencyType = CurrencyType.INR
)
