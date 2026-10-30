package com.jyotirmoyb.udhaar.util

import com.jyotirmoyb.udhaar.data.CompoundFrequency
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.InterestRatePeriod
import com.jyotirmoyb.udhaar.data.InterestType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
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
            transaction.expiryDateMillis != null -> "DUE: ${formatDate(transaction.expiryDateMillis)}"
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

    fun formatAmount(amount: Double): String {
        val clean = if (kotlin.math.abs(amount) < 0.005) 0.0 else amount
        val hasDecimals = kotlin.math.abs(clean - kotlin.math.round(clean)) >= 0.01
        return if (hasDecimals) {
            decimalFormat.format(clean)
        } else {
            wholeNumberFormat.format(clean)
        }
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
        includeUpiId: Boolean = true,
        verificationLink: String? = null
    ): String {
        val tx = summary.transaction
        val partyName = tx.partyName.ifBlank { "Friend" }
        val expiryText = tx.expiryDateMillis?.let { formatDate(it) } ?: "the earliest convenience"
        val currency = tx.currency

        val effectiveLink = verificationLink?.takeIf { it.isNotBlank() } ?: run {
            val (lender, borrower) = VerificationCryptoHelper.resolveLenderAndBorrowerNames(
                type = tx.transactionType,
                partyName = tx.partyName,
                myProfileName = "Udhaar User",
                explicitLenderName = tx.lenderName,
                explicitBorrowerName = tx.borrowerName
            )
            val hash = tx.contentHash.ifBlank {
                VerificationCryptoHelper.computeTransactionContentHash(tx)
            }
            val payload = VerificationCryptoHelper.SharedVerificationPayload(
                remoteId = tx.remoteId.ifBlank { "tx_${tx.id}" },
                shareToken = tx.shareToken,
                creatorUserId = "",
                creatorRole = tx.transactionType,
                lenderName = lender,
                borrowerName = borrower,
                creatorPhone = "",
                counterpartyPhone = tx.phoneNumber,
                principalAmount = tx.principalAmount,
                currencyCode = tx.currency.code,
                startDateMillis = tx.startDateMillis,
                expiryDateMillis = tx.expiryDateMillis,
                isInterestEnabled = tx.isInterestEnabled,
                interestType = tx.interestType,
                interestRate = tx.interestRate,
                interestRatePeriod = tx.interestRatePeriod,
                compoundFrequency = tx.compoundFrequency,
                contentHash = hash
            )
            VerificationCryptoHelper.buildVerificationLink(payload)
        }

        if (hideExactAmount) {
            return "Hello $partyName, hope you are doing well. This is a gentle reminder regarding our pending account settlement scheduled for $expiryText. Please check or confirm the entry here: $effectiveLink"
        }

        val totalFormatted = formatCurrency(summary.netBalanceDue, currency)
        val principalFormatted = formatCurrency(summary.principal, currency)
        val interestFormatted = formatCurrency(summary.accruedInterest, currency)
        val paidPart = if (summary.amountPaid > 0.0) {
            ", Paid: ${formatCurrency(summary.amountPaid, currency)}"
        } else {
            ""
        }

        val baseMessage = "Hello $partyName, hope you are doing well. This is a gentle reminder from Udhaar regarding the ${tx.paymentMode.label} record of $totalFormatted (Principal: $principalFormatted, Interest: $interestFormatted$paidPart) due on $expiryText."

        val withUpi = if (includeUpiId && currency == CurrencyType.INR && upiId.isNotBlank() && tx.transactionType.name == "GIVEN") {
            "$baseMessage\n\nUPI ID for settlement: ${upiId.trim()}"
        } else {
            baseMessage
        }

        return "$withUpi\n\nVerify or confirm this Udhaar record (Hash ${tx.contentHash.take(8).ifBlank { "verified" }}): $effectiveLink"
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
     * Normalizes a phone number to standard E.164 string format (+[countryCode][nationalNumber]).
     * Strips whitespace, dashes, leading zero, handles +91 / +977 or un-prefixed 10-digit numbers.
     * E.g. "98765 43210", "+91 98765-43210" and "09876543210" with INR all normalize to "+919876543210".
     */
    fun normalizeToE164(rawPhone: String, currency: CurrencyType = CurrencyType.INR): String {
        val trimmed = rawPhone.trim()
        if (trimmed.isEmpty()) return ""
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return ""

        val defaultPrefix = if (currency == CurrencyType.NPR) "977" else "91"

        return when {
            trimmed.startsWith("+") -> "+$digitsOnly"
            // Handle leading 0 (common domestic trunk prefix like 09876543210)
            digitsOnly.length == 11 && digitsOnly.startsWith("0") -> "+$defaultPrefix${digitsOnly.substring(1)}"
            // 10-digit national number
            digitsOnly.length == 10 -> "+$defaultPrefix$digitsOnly"
            // Already includes 91 (12 digits) or 977 (13 digits) without +
            digitsOnly.length == 12 && digitsOnly.startsWith("91") -> "+$digitsOnly"
            digitsOnly.length == 13 && digitsOnly.startsWith("977") -> "+$digitsOnly"
            else -> "+$digitsOnly"
        }
    }

    /**
     * Checks if two raw phone numbers represent the same person by comparing normalized E.164.
     */
    fun arePhonesEquivalent(phoneA: String, phoneB: String, currency: CurrencyType = CurrencyType.INR): Boolean {
        val normA = normalizeToE164(phoneA, currency)
        val normB = normalizeToE164(phoneB, currency)
        if (normA.isEmpty() || normB.isEmpty()) return false
        return normA == normB
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

    /**
     * Masks a phone number for privacy so full digits are never exposed on screen.
     * Formats 10-digit subscriber numbers as "98*** ***10" (first 2 digits + "*** ***" + last 2 digits).
     */
    fun maskPhoneNumber(rawPhone: String): String {
        val trimmed = rawPhone.trim()
        if (trimmed.isEmpty()) return ""
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return trimmed

        val subscriberDigits = if (digitsOnly.length >= 10) {
            digitsOnly.takeLast(10)
        } else {
            digitsOnly
        }
        return when {
            subscriberDigits.length >= 4 -> {
                val firstTwo = subscriberDigits.take(2)
                val lastTwo = subscriberDigits.takeLast(2)
                "$firstTwo*** ***$lastTwo"
            }
            subscriberDigits.length >= 2 -> "${subscriberDigits.first()}*** ***${subscriberDigits.last()}"
            else -> "***"
        }
    }

    /**
     * Masks a UPI ID (VPA) in the same style as phone numbers (e.g. "98*** ***10@ybl" or "me*** ***nt@okaxis").
     */
    fun maskUpiId(rawUpiId: String): String {
        val trimmed = rawUpiId.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.contains("@")) {
            val userPart = trimmed.substringBefore("@")
            val handlePart = trimmed.substringAfter("@")
            val maskedUser = when {
                userPart.length >= 4 -> "${userPart.take(2)}*** ***${userPart.takeLast(2)}"
                userPart.length >= 2 -> "${userPart.first()}*** ***${userPart.last()}"
                userPart.isNotEmpty() -> "${userPart.first()}*** ***"
                else -> "*** ***"
            }
            return if (handlePart.isNotEmpty()) "$maskedUser@$handlePart" else maskedUser
        }
        return when {
            trimmed.length >= 4 -> "${trimmed.take(2)}*** ***${trimmed.takeLast(2)}"
            trimmed.length >= 2 -> "${trimmed.first()}*** ***${trimmed.last()}"
            else -> "*** ***"
        }
    }
}
