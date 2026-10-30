package com.jyotirmoyb.udhaar.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.util.InterestCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

object RecurringReminderScheduler {

    /**
     * Schedules reminders for a transaction:
     * - Exactly on due date at 09:00 AM
     * - 1 day before due date at 09:00 AM
     * - 3 days before due date at 09:00 AM
     */
    fun scheduleRemindersForTransaction(
        context: Context,
        transaction: UdhaarTransaction,
        formattedAmount: String
    ) {
        val dueDateMillis = transaction.expiryDateMillis ?: return
        if (transaction.isSettled || dueDateMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Stages: 0 days before (due day), 1 day before, 3 days before
        val stages = listOf(0, 1, 3)

        for (daysBefore in stages) {
            val triggerCalendar = Calendar.getInstance().apply {
                timeInMillis = dueDateMillis
                add(Calendar.DAY_OF_YEAR, -daysBefore)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val triggerMillis = triggerCalendar.timeInMillis
            if (triggerMillis > System.currentTimeMillis()) {
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    putExtra(ReminderReceiver.EXTRA_TRANSACTION_ID, transaction.id)
                    putExtra(ReminderReceiver.EXTRA_PARTY_NAME, transaction.partyName)
                    putExtra(ReminderReceiver.EXTRA_FORMATTED_AMOUNT, formattedAmount)
                    putExtra(
                        ReminderReceiver.EXTRA_TX_TYPE,
                        if (transaction.transactionType == TransactionType.GIVEN) "Loan to collect" else "Loan to repay"
                    )
                    putExtra(ReminderReceiver.EXTRA_DAYS_BEFORE, daysBefore)
                }

                val requestCode = (transaction.id * 10 + daysBefore).toInt()
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerMillis,
                            pendingIntent
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                }
            }
        }
    }

    fun cancelRemindersForTransaction(context: Context, transactionId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val stages = listOf(0, 1, 3)
        for (daysBefore in stages) {
            val intent = Intent(context, ReminderReceiver::class.java)
            val requestCode = (transactionId * 10 + daysBefore).toInt()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    fun rescheduleAllActiveReminders(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = UdhaarDatabase.getInstance(context)
            val dao = db.udhaarDao()
            val transactions = dao.getAllTransactions().first()
            val allPayments = dao.getAllPartialPayments().first().groupBy { it.transactionId }

            for (tx in transactions) {
                val exp = tx.expiryDateMillis
                if (!tx.isSettled && exp != null && exp > System.currentTimeMillis()) {
                    val summary = InterestCalculator.calculate(tx)
                    val formatted = InterestCalculator.formatCurrency(summary.netBalanceDue, tx.currency)
                    scheduleRemindersForTransaction(context, tx, formatted)
                }
            }
        }
    }
}
