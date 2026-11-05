package com.jyotirmoyb.udhaar.util

import com.jyotirmoyb.udhaar.data.CompoundFrequency
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.InterestRatePeriod
import com.jyotirmoyb.udhaar.data.InterestType
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

data class CalculatedUdhaarSummary(
    val transaction: UdhaarTransaction,
    val daysElapsed: Long,
    val interestAccruedPaise: Long,
    val totalDuePaise: Long,
    val netBalanceDuePaise: Long,
    val isOverdue: Boolean,
    val daysUntilOrSinceExpiry: Long?
) {
    val principalPaise: Long
        get() = transaction.principalAmountPaise

    val amountPaidPaise: Long
        get() = transaction.amountPaidPaise

    val grossTotalPaise: Long
        get() = totalDuePaise

    val principal: Double
        get() = InterestCalculator.paiseToRupees(transaction.principalAmountPaise)

    val accruedInterest: Double
        get() = InterestCalculator.paiseToRupees(interestAccruedPaise)

    val interestAccrued: Double
        get() = InterestCalculator.paiseToRupees(interestAccruedPaise)

    val grossTotal: Double
        get() = InterestCalculator.paiseToRupees(totalDuePaise)

    val totalDue: Double
        get() = InterestCalculator.paiseToRupees(totalDuePaise)

    val amountPaid: Double
        get() = InterestCalculator.paiseToRupees(transaction.amountPaidPaise)

    val netBalanceDue: Double
        get() = InterestCalculator.paiseToRupees(netBalanceDuePaise)

    val elapsedDays: Long
        get() = daysElapsed

    val dueStatusText: String
        get() = when {
            transaction.isSettled -> "Paid back"
            transaction.expiryDateMillis == null -> "No due date"
            isOverdue -> {
                val overdueDays = abs(daysUntilOrSinceExpiry ?: 0L)
                if (overdueDays <= 1L) "Overdue by 1 day" else "Overdue by $overdueDays days"
            }
            else -> {
                val daysLeft = daysUntilOrSinceExpiry ?: 0L
                when {
                    daysLeft == 0L -> "Due today"
                    daysLeft == 1L -> "Due tomorrow"
                    else -> "Due in $daysLeft days"
                }
            }
        }

    val subtitleBreakdown: String
        get() = InterestCalculator.formatSubtitleBreakdownPrivacy(this, isHidden = false)
}

object InterestCalculator {

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    private val HUNDRED = BigDecimal(100)
    private val DAYS_IN_YEAR = BigDecimal("365.25")
    private val DAYS_IN_MONTH = BigDecimal("30")
    private val MC = MathContext.DECIMAL128

    fun rupeesToPaise(amount: Double): Long {
        if (amount.isNaN() || amount.isInfinite()) return 0L
        return BigDecimal.valueOf(amount)
            .multiply(HUNDRED, MC)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    fun rupeesStringToPaise(text: String): Long? {
        val cleaned = text.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        return try {
            val bd = BigDecimal(cleaned)
            if (bd < BigDecimal.ZERO) null
            else bd.multiply(HUNDRED, MC).setScale(0, RoundingMode.HALF_UP).toLong()
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun paiseToRupees(paise: Long): Double {
        return BigDecimal.valueOf(paise)
            .divide(HUNDRED, 2, RoundingMode.HALF_UP)
            .toDouble()
    }

    fun calculate(
        transaction: UdhaarTransaction,
        asOfMillis: Long = System.currentTimeMillis()
    ): CalculatedUdhaarSummary {
        val effectiveEndMillis = if (transaction.isSettled && transaction.settledAtMillis != null) {
            minOf(asOfMillis, transaction.settledAtMillis)
        } else {
            asOfMillis
        }

        val elapsedMillis = max(0L, effectiveEndMillis - transaction.startDateMillis)
        val daysElapsed = elapsedMillis / MILLIS_PER_DAY

        val principalPaise = max(0L, transaction.principalAmountPaise)
        val paidPaise = max(0L, transaction.amountPaidPaise)

        val effectiveInterestDays = if (transaction.isInterestEnabled && transaction.interestRate > 0.0 && daysElapsed == 0L) {
            30L
        } else {
            daysElapsed
        }

        val interestAccruedPaise = if (
            !transaction.isInterestEnabled ||
            transaction.interestRate <= 0.0 ||
            principalPaise <= 0L
        ) {
            0L
        } else {
            computeInterestPaise(
                principalPaise = principalPaise,
                rate = transaction.interestRate,
                ratePeriod = transaction.interestRatePeriod,
                interestType = transaction.interestType,
                compoundFrequency = transaction.compoundFrequency,
                elapsedDays = effectiveInterestDays
            )
        }

        val totalDuePaise = principalPaise + interestAccruedPaise
        val netBalanceDuePaise = if (transaction.isSettled) {
            0L
        } else {
            max(0L, totalDuePaise - paidPaise)
        }

        val expiryMillis = transaction.expiryDateMillis
        val daysUntilOrSinceExpiry: Long? = expiryMillis?.let { exp ->
            val diffMillis = exp - asOfMillis
            diffMillis / MILLIS_PER_DAY
        }

        val isOverdue = !transaction.isSettled &&
            expiryMillis != null &&
            asOfMillis > expiryMillis &&
            netBalanceDuePaise > 0L

        return CalculatedUdhaarSummary(
            transaction = transaction,
            daysElapsed = daysElapsed,
            interestAccruedPaise = interestAccruedPaise,
            totalDuePaise = totalDuePaise,
            netBalanceDuePaise = netBalanceDuePaise,
            isOverdue = isOverdue,
            daysUntilOrSinceExpiry = daysUntilOrSinceExpiry
        )
    }

    fun computeInterestPaise(
        principalPaise: Long,
        rate: Double,
        ratePeriod: InterestRatePeriod,
        interestType: InterestType,
        compoundFrequency: CompoundFrequency,
        elapsedDays: Long
    ): Long {
        if (principalPaise <= 0L || rate <= 0.0 || elapsedDays <= 0L) return 0L
        val principalBd = BigDecimal.valueOf(principalPaise)
        val rateDecimal = BigDecimal.valueOf(rate).divide(HUNDRED, MC)

        return when (interestType) {
            InterestType.SIMPLE -> {
                val periods = when (ratePeriod) {
                    InterestRatePeriod.PER_MONTH -> BigDecimal.valueOf(elapsedDays).divide(DAYS_IN_MONTH, MC)
                    InterestRatePeriod.PER_ANNUM -> BigDecimal.valueOf(elapsedDays).divide(DAYS_IN_YEAR, MC)
                }
                principalBd.multiply(rateDecimal, MC)
                    .multiply(periods, MC)
                    .setScale(0, RoundingMode.HALF_UP)
                    .toLong()
                    .coerceAtLeast(0L)
            }
            InterestType.COMPOUND -> {
                val annualRate = when (ratePeriod) {
                    InterestRatePeriod.PER_MONTH -> rateDecimal.multiply(BigDecimal(12), MC).toDouble()
                    InterestRatePeriod.PER_ANNUM -> rateDecimal.toDouble()
                }
                val n = when (compoundFrequency) {
                    CompoundFrequency.MONTHLY -> 12.0
                    CompoundFrequency.QUARTERLY -> 4.0
                    CompoundFrequency.YEARLY -> 1.0
                }
                val years = elapsedDays.toDouble() / 365.25
                val factor = (1.0 + (annualRate / n)).pow(n * years) - 1.0
                if (factor <= 0.0 || factor.isNaN() || factor.isInfinite()) {
                    0L
                } else {
                    principalBd.multiply(BigDecimal.valueOf(factor), MC)
                        .setScale(0, RoundingMode.HALF_UP)
                        .toLong()
                        .coerceAtLeast(0L)
                }
            }
        }
    }

    fun computeInterestAmount(
        principal: Double,
        rate: Double,
        ratePeriod: InterestRatePeriod,
        interestType: InterestType,
        compoundFrequency: CompoundFrequency,
        elapsedDays: Long
    ): Double {
        val principalPaise = rupeesToPaise(principal)
        val interestPaise = computeInterestPaise(
            principalPaise = principalPaise,
            rate = rate,
            ratePeriod = ratePeriod,
            interestType = interestType,
            compoundFrequency = compoundFrequency,
            elapsedDays = elapsedDays
        )
        return paiseToRupees(interestPaise)
    }

    fun formatSubtitleBreakdownPrivacy(
        summary: CalculatedUdhaarSummary,
        isHidden: Boolean = false
    ): String {
        val tx = summary.transaction
        val currency = tx.currency
        val rolePrefix = if (tx.transactionType == TransactionType.GIVEN) "Amount lent" else "Amount borrowed"
        val principalFormatted = formatCurrencyPrivacyPaise(summary.principalPaise, currency, isHidden)
        val parts = mutableListOf("$rolePrefix: $principalFormatted")
        if (tx.isInterestEnabled && tx.interestRate > 0.0) {
            val interestFormatted = formatCurrencyPrivacyPaise(summary.interestAccruedPaise, currency, isHidden)
            parts.add("Interest: $interestFormatted")
        }
        if (summary.amountPaidPaise > 0L) {
            val paidFormatted = formatCurrencyPrivacyPaise(summary.amountPaidPaise, currency, isHidden)
            parts.add("Paid: $paidFormatted")
        }
        return parts.joinToString("  |  ")
    }

    fun formatPaise(paise: Long, currency: CurrencyType = CurrencyType.INR): String {
        val isNegative = paise < 0L
        val absPaise = abs(paise)
        val rupees = absPaise / 100L
        val remainderPaise = absPaise % 100L
        val formattedRupees = formatSouthAsianInteger(rupees)
        val numberPart = if (remainderPaise == 0L) {
            formattedRupees
        } else {
            "$formattedRupees.${remainderPaise.toString().padStart(2, '0')}"
        }
        val signPrefix = if (isNegative) "-" else ""
        return "$signPrefix${currency.symbol} $numberPart"
    }

    fun formatCurrencyPaise(paise: Long, currency: CurrencyType = CurrencyType.INR): String {
        return formatPaise(paise, currency)
    }

    fun formatCurrency(amount: Double, currency: CurrencyType = CurrencyType.INR): String {
        return formatPaise(rupeesToPaise(amount), currency)
    }

    fun formatCurrencyPrivacyPaise(
        paise: Long,
        currency: CurrencyType = CurrencyType.INR,
        isHidden: Boolean = false
    ): String {
        return if (isHidden) {
            "${currency.symbol} •••••"
        } else {
            formatPaise(paise, currency)
        }
    }

    fun formatCurrencyPaisePrivacy(
        paise: Long,
        currency: CurrencyType = CurrencyType.INR,
        isHidden: Boolean = false
    ): String = formatCurrencyPrivacyPaise(paise, currency, isHidden)

    fun formatCurrencyPrivacy(
        amount: Double,
        currency: CurrencyType = CurrencyType.INR,
        isHidden: Boolean = false
    ): String {
        return formatCurrencyPrivacyPaise(rupeesToPaise(amount), currency, isHidden)
    }

    fun formatAmountPaise(paise: Long): String {
        val absPaise = abs(paise)
        val rupees = absPaise / 100L
        val remainderPaise = absPaise % 100L
        val formattedRupees = formatSouthAsianInteger(rupees)
        val prefix = if (paise < 0L) "-" else ""
        return if (remainderPaise == 0L) {
            "$prefix$formattedRupees"
        } else {
            "$prefix$formattedRupees.${remainderPaise.toString().padStart(2, '0')}"
        }
    }

    fun formatAmount(amount: Double): String {
        return formatAmountPaise(rupeesToPaise(amount))
    }

    fun formatPlainAmountPaise(paise: Long): String {
        val absPaise = abs(paise)
        val rupees = absPaise / 100L
        val remainderPaise = absPaise % 100L
        return if (remainderPaise == 0L) {
            rupees.toString()
        } else {
            "$rupees.${remainderPaise.toString().padStart(2, '0')}"
        }
    }

    fun formatPlainAmount(amount: Double): String {
        return formatPlainAmountPaise(rupeesToPaise(amount))
    }

    fun formatRate(rate: Double): String {
        val df = DecimalFormat("0.##")
        return "${df.format(rate)}%"
    }

    private fun formatSouthAsianInteger(value: Long): String {
        val s = value.toString()
        if (s.length <= 3) return s
        val lastThree = s.substring(s.length - 3)
        val remaining = s.substring(0, s.length - 3)
        val groups = mutableListOf<String>()
        var i = remaining.length
        while (i > 0) {
            val start = max(0, i - 2)
            groups.add(0, remaining.substring(start, i))
            i = start
        }
        return groups.joinToString(",") + "," + lastThree
    }

    fun formatDate(millis: Long): String {
        if (millis <= 0L) return "Open-ended"
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        return sdf.format(Date(millis))
    }

    fun formatDateTime(millis: Long): String {
        if (millis <= 0L) return "Open-ended"
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
        return sdf.format(Date(millis))
    }

    fun fmtDate(millis: Long): String {
        if (millis <= 0L) return "Open-ended"
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return if (hour == 0 && minute == 0) {
            formatDate(millis)
        } else {
            formatDateTime(millis)
        }
    }

    fun normalizeToE164(rawPhone: String, defaultCurrency: CurrencyType = CurrencyType.INR): String {
        val trimmed = rawPhone.trim()
        if (trimmed.isEmpty()) return ""
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        if (hasPlus) {
            return "+$digits"
        }
        val strippedLeadingZero = if (digits.length == 11 && digits.startsWith("0")) {
            digits.substring(1)
        } else {
            digits
        }
        return when {
            strippedLeadingZero.length == 10 -> {
                val prefix = if (defaultCurrency == CurrencyType.NPR) "+977" else "+91"
                "$prefix$strippedLeadingZero"
            }
            strippedLeadingZero.startsWith("91") && strippedLeadingZero.length == 12 -> "+$strippedLeadingZero"
            strippedLeadingZero.startsWith("977") && strippedLeadingZero.length == 13 -> "+$strippedLeadingZero"
            else -> "+$strippedLeadingZero"
        }
    }

    fun arePhonesEquivalent(
        phoneA: String,
        phoneB: String,
        defaultCurrency: CurrencyType = CurrencyType.INR
    ): Boolean {
        val normA = normalizeToE164(phoneA, defaultCurrency)
        val normB = normalizeToE164(phoneB, defaultCurrency)
        return normA.isNotEmpty() && normA == normB
    }

    fun formatPhoneWithCountryCode(rawPhone: String, currency: CurrencyType = CurrencyType.INR): String {
        val e164 = normalizeToE164(rawPhone, currency)
        if (e164.isEmpty()) return ""
        return when {
            e164.startsWith("+91") && e164.length == 13 -> "+91 ${e164.substring(3)}"
            e164.startsWith("+977") && e164.length == 14 -> "+977 ${e164.substring(4)}"
            else -> e164
        }
    }

    fun sanitizePhoneForWhatsApp(rawPhone: String, currency: CurrencyType = CurrencyType.INR): String {
        return normalizeToE164(rawPhone, currency).removePrefix("+")
    }

    fun maskPhoneNumber(rawPhone: String): String {
        val digits = rawPhone.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        val coreTen = when {
            digits.length > 10 -> digits.takeLast(10)
            else -> digits
        }
        if (coreTen.length < 4) return "***"
        return "${coreTen.take(2)}*** ***${coreTen.takeLast(2)}"
    }

    fun maskUpiId(rawUpi: String): String {
        val trimmed = rawUpi.trim()
        if (trimmed.isEmpty()) return ""
        val atIndex = trimmed.indexOf('@')
        if (atIndex <= 0) {
            if (trimmed.length < 4) return "***"
            return "${trimmed.take(2)}*** ***${trimmed.takeLast(2)}"
        }
        val handle = trimmed.substring(0, atIndex)
        val domain = trimmed.substring(atIndex)
        val maskedHandle = if (handle.length >= 4) {
            "${handle.take(2)}*** ***${handle.takeLast(2)}"
        } else {
            "${handle.take(1)}***"
        }
        return "$maskedHandle$domain"
    }

    fun buildWhatsAppReminderMessage(
        summary: CalculatedUdhaarSummary,
        hideExactAmount: Boolean = false,
        upiId: String = "",
        senderName: String = ""
    ): String {
        val tx = summary.transaction
        val greeting = "Hello ${tx.partyName},"
        val amountLine = if (hideExactAmount) {
            "This is a friendly reminder regarding our pending Udhaar record."
        } else {
            val formattedNet = formatPaise(summary.netBalanceDuePaise, tx.currency)
            if (tx.transactionType == TransactionType.GIVEN) {
                "This is a friendly reminder that $formattedNet is pending on our Udhaar record."
            } else {
                "Sharing an update on our Udhaar record (balance: $formattedNet)."
            }
        }
        val upiLine = if (upiId.isNotBlank() && tx.transactionType == TransactionType.GIVEN) {
            "\nYou can pay via UPI ID: ${upiId.trim()}"
        } else {
            ""
        }
        val signOff = if (senderName.isNotBlank()) "\n— $senderName" else "\n— Sent via Udhaar"
        return "$greeting\n$amountLine$upiLine$signOff"
    }
}
