package com.jyotirmoyb.udhaar.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jyotirmoyb.udhaar.MainActivity
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.util.InterestCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
        if (transactionId == -1L) return

        val partyName = intent.getStringExtra(EXTRA_PARTY_NAME) ?: "Contact"
        val formattedAmount = intent.getStringExtra(EXTRA_FORMATTED_AMOUNT) ?: ""
        val txTypeLabel = intent.getStringExtra(EXTRA_TX_TYPE) ?: "Udhaar"
        val reminderDaysBefore = intent.getIntExtra(EXTRA_DAYS_BEFORE, 0)

        showNotification(
            context = context,
            transactionId = transactionId,
            partyName = partyName,
            formattedAmount = formattedAmount,
            txTypeLabel = txTypeLabel,
            daysBefore = reminderDaysBefore
        )
    }

    companion object {
        const val CHANNEL_ID = "udhaar_reminders_channel"
        const val OVERDUE_CHANNEL_ID = "udhaar_overdue_digest_channel"
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
        const val EXTRA_PARTY_NAME = "extra_party_name"
        const val EXTRA_FORMATTED_AMOUNT = "extra_formatted_amount"
        const val EXTRA_TX_TYPE = "extra_tx_type"
        const val EXTRA_DAYS_BEFORE = "extra_days_before"

        private const val PREFS_DIGEST = "udhaar_digest_prefs"
        private const val KEY_LAST_DIGEST_DATE = "last_digest_date"

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val reminderChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Udhaar Settlement Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Scheduled reminders to send WhatsApp settlement follow-ups"
                }
                manager.createNotificationChannel(reminderChannel)

                val overdueChannel = NotificationChannel(
                    OVERDUE_CHANNEL_ID,
                    "Overdue Today Digest",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily morning summary of overdue loans requiring follow-up"
                }
                manager.createNotificationChannel(overdueChannel)
            }
        }

        fun showNotification(
            context: Context,
            transactionId: Long,
            partyName: String,
            formattedAmount: String,
            txTypeLabel: String,
            daysBefore: Int = 0
        ) {
            ensureNotificationChannel(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_TRANSACTION_ID, transactionId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                (transactionId * 10 + daysBefore).toInt(),
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val timingPrefix = when (daysBefore) {
                3 -> "Upcoming in 3 days: "
                1 -> "Due tomorrow: "
                else -> "Due today: "
            }

            val contentTitle = "$timingPrefix$partyName"
            val contentText = if (formattedAmount.isNotBlank()) {
                "$txTypeLabel of $formattedAmount. Tap to review and send WhatsApp reminder."
            } else {
                "Settlement reminder for $partyName. Tap to open WhatsApp reminder."
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify((transactionId * 10 + daysBefore).toInt(), notification)
        }

        fun checkAndSendOverdueDigest(
            context: Context,
            overdueCount: Int,
            totalReceiveInr: Double
        ) {
            if (overdueCount <= 0) return
            val prefs = context.getSharedPreferences(PREFS_DIGEST, Context.MODE_PRIVATE)
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val lastSent = prefs.getString(KEY_LAST_DIGEST_DATE, "")
            if (lastSent == todayDate) return // Already sent today

            ensureNotificationChannel(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                99999,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val formattedTotal = InterestCalculator.formatCurrency(totalReceiveInr, CurrencyType.INR)
            val title = "Overdue Today: $overdueCount Payment${if (overdueCount > 1) "s" else ""}"
            val body = "You have $overdueCount overdue record${if (overdueCount > 1) "s" else ""} totaling $formattedTotal to receive. Tap to follow up."

            val notification = NotificationCompat.Builder(context, OVERDUE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify(99999, notification)
            prefs.edit().putString(KEY_LAST_DIGEST_DATE, todayDate).apply()
        }
    }
}
