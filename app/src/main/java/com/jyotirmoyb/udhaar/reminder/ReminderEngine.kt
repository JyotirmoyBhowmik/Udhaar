package com.jyotirmoyb.udhaar.reminder

import android.content.Context
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.util.InterestCalculator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ReminderLevel(val levelNumber: Int, val titleResId: Int) {
    LEVEL_1_GENTLE(1, R.string.reminder_level_1_title),
    LEVEL_2_FRIENDLY(2, R.string.reminder_level_2_title),
    LEVEL_3_FIRM(3, R.string.reminder_level_3_title),
    LEVEL_4_FINAL(4, R.string.reminder_level_4_title);

    companion object {
        fun fromLevelNumber(number: Int): ReminderLevel = when (number) {
            1 -> LEVEL_1_GENTLE
            2 -> LEVEL_2_FRIENDLY
            3 -> LEVEL_3_FIRM
            4 -> LEVEL_4_FINAL
            else -> LEVEL_1_GENTLE
        }
    }
}

data class ReminderPolicy(
    val level1DaysBefore: Int = 3,
    val level2MaxOverdueDays: Int = 7,
    val level3MaxOverdueDays: Int = 30,
    val quietHoursEnabled: Boolean = true,
    val quietHoursStartHour: Int = 21,
    val quietHoursEndHour: Int = 8
)

object ReminderEngine {

    /**
     * Determines whether reminders must be stopped automatically for a record.
     * Stops when:
     * - The record is settled (isSettled == true).
     * - The record is snoozed and the snooze expiration date has not passed yet.
     * - Reminders are explicitly disabled for this record.
     * - The counterparty has pending verification / payment proof waiting for confirmation.
     */
    fun shouldStopReminders(
        transaction: UdhaarTransaction,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (transaction.isSettled) return true
        if (transaction.remindersDisabled) return true

        val snoozedUntil = transaction.snoozedUntilMillis
        if (snoozedUntil != null && snoozedUntil > nowMillis) return true

        // Stop when borrower sent a payment proof waiting for lender's confirmation/acknowledgement
        if (transaction.needsMyConfirmation) return true

        return false
    }

    /**
     * Calculates the number of days overdue.
     * Negative values indicate days until the due date (e.g. -3 means 3 days before due date).
     * 0 means due today.
     * Positive values indicate days overdue.
     */
    fun calculateOverdueDays(
        dueDateMillis: Long?,
        nowMillis: Long = System.currentTimeMillis()
    ): Int? {
        if (dueDateMillis == null || dueDateMillis <= 0L) return null

        val dueCalendar = Calendar.getInstance().apply {
            timeInMillis = dueDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowCalendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMillis = nowCalendar.timeInMillis - dueCalendar.timeInMillis
        return TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
    }

    /**
     * Evaluates the escalation level based on overdue days and the configured policy.
     * - Level 1 (Gentle): 3 days before due date up to and including the due day (days in [-level1DaysBefore..0])
     * - Level 2 (Friendly follow-up): 1 to 7 days overdue (days in [1..level2MaxOverdueDays])
     * - Level 3 (Firm): 8 to 30 days overdue (days in [(level2MaxOverdueDays + 1)..level3MaxOverdueDays])
     * - Level 4 (Final notice): More than 30 days overdue (days > level3MaxOverdueDays)
     */
    fun computeEscalationLevel(
        transaction: UdhaarTransaction,
        policy: ReminderPolicy = ReminderPolicy(),
        nowMillis: Long = System.currentTimeMillis()
    ): ReminderLevel? {
        if (shouldStopReminders(transaction, nowMillis)) return null
        val overdueDays = calculateOverdueDays(transaction.expiryDateMillis, nowMillis) ?: return null

        return when {
            overdueDays < -policy.level1DaysBefore -> null
            overdueDays <= 0 -> ReminderLevel.LEVEL_1_GENTLE
            overdueDays <= policy.level2MaxOverdueDays -> ReminderLevel.LEVEL_2_FRIENDLY
            overdueDays <= policy.level3MaxOverdueDays -> ReminderLevel.LEVEL_3_FIRM
            else -> ReminderLevel.LEVEL_4_FINAL
        }
    }

    /**
     * Checks if current time is within Quiet Hours (e.g. 21:00 to 08:00 crossing midnight).
     * If party overrides quiet hours, this returns false so notifications can proceed.
     */
    fun isInQuietHours(
        nowMillis: Long,
        startHour: Int = 21,
        endHour: Int = 8,
        partyOverride: Boolean = false
    ): Boolean {
        if (partyOverride) return false
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        return if (startHour > endHour) {
            // Crosses midnight, e.g. 21:00 to 08:00
            hour >= startHour || hour < endHour
        } else if (startHour < endHour) {
            // Same day, e.g. 13:00 to 15:00
            hour in startHour until endHour
        } else {
            false
        }
    }

    /**
     * Computes the timestamp when the current quiet hours period ends.
     */
    fun calculateQuietHoursEnd(
        nowMillis: Long,
        endHour: Int = 8
    ): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        if (currentHour >= endHour) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        cal.set(Calendar.HOUR_OF_DAY, endHour)
        return cal.timeInMillis
    }

    /**
     * Checks whether a reminder can be sent today (enforces maximum one reminder per record per day).
     */
    fun canSendReminderToday(
        transaction: UdhaarTransaction,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val lastSent = transaction.lastReminderSentMillis ?: return true
        val calLast = Calendar.getInstance().apply { timeInMillis = lastSent }
        val calNow = Calendar.getInstance().apply { timeInMillis = nowMillis }

        return calLast.get(Calendar.YEAR) != calNow.get(Calendar.YEAR) ||
            calLast.get(Calendar.DAY_OF_YEAR) != calNow.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Calculates snooze timestamp given a number of days (1, 3, or 7 days).
     */
    fun calculateSnoozeDate(days: Int, nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, days)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Formats the snoozed timestamp as e.g. "10 Nov" or localized date.
     */
    fun formatSnoozedUntil(snoozedUntilMillis: Long): String {
        return SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(snoozedUntilMillis))
    }

    fun getTemplateResId(level: ReminderLevel, hideExactAmount: Boolean = false): Int = when (level) {
        ReminderLevel.LEVEL_1_GENTLE -> if (hideExactAmount) R.string.reminder_draft_level_1_hidden else R.string.reminder_draft_level_1
        ReminderLevel.LEVEL_2_FRIENDLY -> if (hideExactAmount) R.string.reminder_draft_level_2_hidden else R.string.reminder_draft_level_2
        ReminderLevel.LEVEL_3_FIRM -> if (hideExactAmount) R.string.reminder_draft_level_3_hidden else R.string.reminder_draft_level_3
        ReminderLevel.LEVEL_4_FINAL -> if (hideExactAmount) R.string.reminder_draft_level_4_hidden else R.string.reminder_draft_level_4
    }

    fun getLocalizedTemplate(
        context: Context,
        level: ReminderLevel,
        languageCode: String,
        hideExactAmount: Boolean = false
    ): String {
        val resId = getTemplateResId(level, hideExactAmount)
        if (languageCode.isBlank()) {
            return context.getString(resId)
        }
        val targetLocale = when (languageCode.lowercase()) {
            "hi" -> Locale("hi")
            "bn" -> Locale("bn")
            "as" -> Locale("as")
            "ne" -> Locale("ne")
            else -> Locale("en")
        }
        val conf = android.content.res.Configuration(context.resources.configuration)
        conf.setLocale(targetLocale)
        val localizedCtx = context.createConfigurationContext(conf)
        return localizedCtx.getString(resId)
    }

    /**
     * Replaces all placeholders:
     * {name}, {my_name}, {amount}, {remaining}, {paid_so_far}, {due_date}, {days_overdue}, {upi_id}, {new_date}
     */
    fun renderDraftMessage(
        template: String,
        name: String,
        myName: String,
        amount: String,
        remaining: String,
        paidSoFar: String,
        dueDate: String,
        daysOverdue: Int,
        upiId: String,
        newDate: String = ""
    ): String {
        return template
            .replace("{name}", name)
            .replace("{my_name}", myName)
            .replace("{amount}", amount)
            .replace("{remaining}", remaining)
            .replace("{paid_so_far}", paidSoFar)
            .replace("{due_date}", dueDate)
            .replace("{days_overdue}", daysOverdue.coerceAtLeast(0).toString())
            .replace("{upi_id}", upiId)
            .replace("{new_date}", newDate)
    }

    /**
     * Determines the escalation level for a specific installment.
     * Stops automatically if the installment is already paid.
     */
    fun computeInstallmentEscalationLevel(
        installment: com.jyotirmoyb.udhaar.data.Installment,
        policy: ReminderPolicy = ReminderPolicy(),
        nowMillis: Long = System.currentTimeMillis()
    ): ReminderLevel? {
        if (installment.amountPaid >= installment.amountDue || installment.status == com.jyotirmoyb.udhaar.data.InstallmentStatus.PAID) {
            return null
        }
        val overdueDays = calculateOverdueDays(installment.dueDate, nowMillis) ?: return null

        return when {
            overdueDays < -policy.level1DaysBefore -> null
            overdueDays <= 0 -> ReminderLevel.LEVEL_1_GENTLE
            overdueDays <= policy.level2MaxOverdueDays -> ReminderLevel.LEVEL_2_FRIENDLY
            overdueDays <= policy.level3MaxOverdueDays -> ReminderLevel.LEVEL_3_FIRM
            else -> ReminderLevel.LEVEL_4_FINAL
        }
    }

    fun getLocalizedInstallmentTemplate(
        context: Context,
        languageCode: String
    ): String {
        val resId = R.string.reminder_installment_draft
        if (languageCode.isBlank()) {
            return context.getString(resId)
        }
        val targetLocale = when (languageCode.lowercase()) {
            "hi" -> Locale("hi")
            "bn" -> Locale("bn")
            "as" -> Locale("as")
            "ne" -> Locale("ne")
            else -> Locale("en")
        }
        val conf = android.content.res.Configuration(context.resources.configuration)
        conf.setLocale(targetLocale)
        val localizedCtx = context.createConfigurationContext(conf)
        return localizedCtx.getString(resId)
    }

    /**
     * Replaces placeholders for an installment reminder:
     * {name}, {n}, {total}, {amount}, {due_date}, {remaining}
     */
    fun renderInstallmentDraftMessage(
        template: String,
        name: String,
        sequence: Int,
        totalCount: Int,
        installmentAmount: String,
        dueDate: String,
        totalRemaining: String
    ): String {
        return template
            .replace("{name}", name)
            .replace("{n}", sequence.toString())
            .replace("{total}", totalCount.toString())
            .replace("{amount}", installmentAmount)
            .replace("{due_date}", dueDate)
            .replace("{remaining}", totalRemaining)
    }
}
