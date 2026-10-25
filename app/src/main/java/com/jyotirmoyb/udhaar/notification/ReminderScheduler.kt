package com.jyotirmoyb.udhaar.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.util.InterestCalculator

object ReminderScheduler {

    fun scheduleReminder(context: Context, transaction: UdhaarTransaction) {
        val reminderMillis = transaction.reminderDateMillis ?: return
        if (transaction.isSettled) {
            cancelReminder(context, transaction.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val summary = InterestCalculator.calculate(transaction)
        val formattedAmount = InterestCalculator.formatCurrency(
            summary.netBalanceDue,
            transaction.currency
        )

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TRANSACTION_ID, transaction.id)
            putExtra(ReminderReceiver.EXTRA_PARTY_NAME, transaction.partyName)
            putExtra(ReminderReceiver.EXTRA_FORMATTED_AMOUNT, formattedAmount)
            putExtra(ReminderReceiver.EXTRA_TX_TYPE, transaction.transactionType.label)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            transaction.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = reminderMillis.coerceAtLeast(System.currentTimeMillis() + 5_000L)
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context, transactionId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            transactionId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            alarmManager.cancel(pendingIntent)
        }
    }
}
