package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
        if (transactionId == -1L) return

        val partyName = intent.getStringExtra(EXTRA_PARTY_NAME) ?: "Contact"
        val formattedAmount = intent.getStringExtra(EXTRA_FORMATTED_AMOUNT) ?: ""
        val txTypeLabel = intent.getStringExtra(EXTRA_TX_TYPE) ?: "Udhaar"

        showNotification(
            context = context,
            transactionId = transactionId,
            partyName = partyName,
            formattedAmount = formattedAmount,
            txTypeLabel = txTypeLabel
        )
    }

    companion object {
        const val CHANNEL_ID = "udhaar_reminders_channel"
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
        const val EXTRA_PARTY_NAME = "extra_party_name"
        const val EXTRA_FORMATTED_AMOUNT = "extra_formatted_amount"
        const val EXTRA_TX_TYPE = "extra_tx_type"

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Udhaar Settlement Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Scheduled reminders to send WhatsApp settlement follow-ups"
                }
                manager.createNotificationChannel(channel)
            }
        }

        fun showNotification(
            context: Context,
            transactionId: Long,
            partyName: String,
            formattedAmount: String,
            txTypeLabel: String
        ) {
            ensureNotificationChannel(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_TRANSACTION_ID, transactionId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                transactionId.toInt(),
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val contentTitle = "Udhaar Reminder: $partyName"
            val contentText = if (formattedAmount.isNotBlank()) {
                "$txTypeLabel of $formattedAmount is due. Tap to send WhatsApp reminder."
            } else {
                "Scheduled settlement reminder for $partyName. Tap to open WhatsApp share."
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

            manager.notify(transactionId.toInt(), notification)
        }
    }
}
