package com.example.util

import com.example.data.CompoundFrequency
import com.example.data.CurrencyType
import com.example.data.InterestRatePeriod
import com.example.data.InterestType
import com.example.data.UdhaarTransaction
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.pow

data class CalculatedUdhaarSummary(
    val transaction: UdhaarTransaction,
    val principal: Double,
    val accruedInterest: Double,
    val grossTotal: Double,
    val amountPaid: Double,
    val netBalanceDue: Double,
    val elapsedDays: Long,
    val isOverdue: Boolean,
    val subtitleBreakdown: String,
    val dueStatusText: String
)

object InterestCalculator {
    private val wholeNumberFormat = DecimalFormat("#,##0")
    private val decimalFormat = DecimalFormat("#,##0.00")
    private const val MILLIS_PER_DAY = 86_400_000L
    private const val DAYS_PER_MONTH = 30.4375
    private const val DAYS_PER_YEAR = 365.25

    fun calculate(
        transaction: UdhaarTransaction,
        asOfMillis: Long = System.currentTimeMillis()
    ): CalculatedUdhaarSummary {
        val principal = max(0.0, transaction.principalAmount)
        // If settled, calculate interest up to settledAtMillis; otherwise up to expiryDateMillis or asOfMillis (whichever is applicable)
        // Per spec: Dynamically calculate accrued interest from Start Date up to Current Date (or Expiry Date)
        val effectiveEndMillis = when {
            transaction.isSettled && transaction.settledAtMillis != null -> transaction.settledAtMillis
            transaction.expiryDateMillis != null && asOfMillis > transaction.expiryDateMillis ->
                max(transaction.expiryDateMillis, asOfMillis)
            else -> asOfMillis
        }

        val elapsedMillis = max(0L, effectiveEndMillis - transaction.startDateMillis)
        val elapsedDays = elapsedMillis / MILLIS_PER_DAY

        val accruedInterest = if (!transaction.isInterestEnabled || transaction.interestRate <= 0.0 || elapsedDays <= 0L) {
            0.0
        } else {
            computeInterestAmount(
                principal = principal,
                rate = transaction.interestRate,
                ratePeriod = transaction.interestRatePeriod,
                interestType = transaction.interestType,
                compoundFrequency = transaction.compoundFrequency,
                elapsedDays = elapsedDays
            )
        }

        val grossTotal = principal + accruedInterest
        val paid = max(0.0, transaction.amountPaid)
        val netBalanceDue = if (transaction.isSettled) {
            0.0
        } else {
            max(0.0, grossTotal - paid)
        }

        val isOverdue = !transaction.isSettled &&
            transaction.expiryDateMillis != null &&
            asOfMillis > (transaction.expiryDateMillis + MILLIS_PER_DAY - 1)

        val currency = transaction.currency
        val subtitleParts = mutableListOf<String>()
        subtitleParts.add("Principal: ${formatCurrency(principal, currency)}")

        if (transaction.isInterestEnabled && transaction.interestRate > 0.0) {
            val typeShort = if (transaction.interestType == InterestType.SIMPLE) {
                "Simple"
            } else {
                "Compound ${transaction.compoundFrequency.label}"
            }
            val rateFormatted = formatRate(transaction.interestRate)
            subtitleParts.add(
                "Interest: ${formatCurrency(accruedInterest, currency)} ($rateFormatted${transaction.interestRatePeriod.shortSuffix} • $typeShort • ${elapsedDays}d)"
            )
        } else {
            subtitleParts.add("Interest: ${formatCurrency(0.0, currency)} (0% • Interest Free)")
        }

        if (paid > 0.0) {
            subtitleParts.add("Paid: ${formatCurrency(paid, currency)}")
        }

        val dueStatusText = when {
            transaction.isSettled -> "SETTLED"
            isOverdue -> "OVERDUE • ${formatDate(transaction.expiryDateMillis!!)}"
            transaction.expiryDateMillis != null -> "DUE ${formatDate(transaction.expiryDateMillis)}"
            else -> "NO EXPIRY"
        }

        return CalculatedUdhaarSummary(
            transaction = transaction,
            principal = principal,
            accruedInterest = accruedInterest,
            grossTotal = grossTotal,
            amountPaid = paid,
            netBalanceDue = netBalanceDue,
            elapsedDays = elapsedDays,
            isOverdue = isOverdue,
            subtitleBreakdown = subtitleParts.joinToString("  |  "),
            dueStatusText = dueStatusText
        )
    }

    fun computeInterestAmount(
        principal: Double,
        rate: Double,
        ratePeriod: InterestRatePeriod,
        interestType: InterestType,
        compoundFrequency: CompoundFrequency,
        elapsedDays: Long
    ): Double {
        if (principal <= 0.0 || rate <= 0.0 || elapsedDays <= 0L) return 0.0

        val annualRateDecimal = when (ratePeriod) {
            InterestRatePeriod.PER_MONTH -> (rate * 12.0) / 100.0
            InterestRatePeriod.PER_ANNUM -> rate / 100.0
        }
        val years = elapsedDays.toDouble() / DAYS_PER_YEAR

        return when (interestType) {
            InterestType.SIMPLE -> {
                val months = elapsedDays.toDouble() / DAYS_PER_MONTH
                when (ratePeriod) {
                    InterestRatePeriod.PER_MONTH -> principal * (rate / 100.0) * months
                    InterestRatePeriod.PER_ANNUM -> principal * annualRateDecimal * years
                }
            }
            InterestType.COMPOUND -> {
                val n = compoundFrequency.periodsPerYear.toDouble()
                val totalAmount = principal * (1.0 + (annualRateDecimal / n)).pow(n * years)
                max(0.0, totalAmount - principal)
            }
        }
    }

    fun formatCurrency(amount: Double, currency: CurrencyType): String {
        val clean = if (kotlin.math.abs(amount) < 0.005) 0.0 else amount
        val hasDecimals = kotlin.math.abs(clean - kotlin.math.round(clean)) >= 0.01
        val formattedNumber = if (hasDecimals) {
            decimalFormat.format(clean)
        } else {
            wholeNumberFormat.format(clean)
        }
        return "${currency.symbol}$formattedNumber"
    }

    fun formatPlainAmount(amount: Double): String {
        val clean = max(0.0, amount)
        return String.format(Locale.US, "%.2f", clean)
    }

    fun formatRate(rate: Double): String {
        val hasDecimals = kotlin.math.abs(rate - kotlin.math.round(rate)) >= 0.01
        return if (hasDecimals) {
            String.format(Locale.US, "%.2f", rate)
        } else {
            String.format(Locale.US, "%.0f", rate)
        }
    }

    fun formatDate(millis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
        return sdf.format(Date(millis))
    }

    fun formatDateTime(millis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
        return sdf.format(Date(millis))
    }

    fun buildWhatsAppReminderMessage(
        summary: CalculatedUdhaarSummary,
        hideExactAmount: Boolean,
        upiId: String,
        includeUpiId: Boolean = true
    ): String {
        val tx = summary.transaction
        val partyName = tx.partyName.ifBlank { "Friend" }
        val expiryText = tx.expiryDateMillis?.let { formatDate(it) } ?: "the earliest convenience"
        val currency = tx.currency

        if (hideExactAmount) {
            return "Hello $partyName, hope you are doing well. This is a gentle reminder regarding our pending account settlement scheduled for $expiryText. Please check at your convenience. Thank you!"
        }

        val totalFormatted = formatCurrency(summary.netBalanceDue, currency)
        val principalFormatted = formatCurrency(summary.principal, currency)
        val interestFormatted = formatCurrency(summary.accruedInterest, currency)
        val paidPart = if (summary.amountPaid > 0.0) {
            ", Paid: ${formatCurrency(summary.amountPaid, currency)}"
        } else {
            ""
        }

        val baseMessage = "Hello $partyName, hope you are doing well. This is a gentle reminder from Udhaar regarding the ${tx.paymentMode.label} record of $totalFormatted (Principal: $principalFormatted, Interest: $interestFormatted$paidPart) due on $expiryText. Kindly let me know once settled. Thank you!"

        return if (includeUpiId && currency == CurrencyType.INR && upiId.isNotBlank() && tx.transactionType.name == "GIVEN") {
            "$baseMessage\n\nUPI ID for settlement: ${upiId.trim()}"
        } else {
            baseMessage
        }
    }

    fun sanitizePhoneForWhatsApp(rawPhone: String, currency: CurrencyType): String {
        val digitsOnly = rawPhone.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return ""
        return when {
            rawPhone.trim().startsWith("+") -> digitsOnly
            digitsOnly.length == 10 && currency == CurrencyType.INR -> "91$digitsOnly"
            digitsOnly.length == 10 && currency == CurrencyType.NPR -> "977$digitsOnly"
            else -> digitsOnly
        }
    }

    /**
     * Auto-sanitizes 10-digit phone numbers with +91 for INR or +977 for NPR.
     */
    fun formatPhoneWithCountryCode(rawPhone: String, currency: CurrencyType): String {
        val trimmed = rawPhone.trim()
        if (trimmed.isEmpty()) return ""
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return trimmed
        return when {
            trimmed.startsWith("+") -> "+$digitsOnly"
            digitsOnly.length == 10 && currency == CurrencyType.INR -> "+91 $digitsOnly"
            digitsOnly.length == 10 && currency == CurrencyType.NPR -> "+977 $digitsOnly"
            digitsOnly.length == 12 && digitsOnly.startsWith("91") -> "+91 ${digitsOnly.substring(2)}"
            digitsOnly.length == 13 && digitsOnly.startsWith("977") -> "+977 ${digitsOnly.substring(3)}"
            else -> trimmed
        }
    }
}
