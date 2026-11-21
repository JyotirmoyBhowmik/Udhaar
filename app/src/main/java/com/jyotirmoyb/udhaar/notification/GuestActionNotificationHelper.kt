package com.jyotirmoyb.udhaar.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jyotirmoyb.udhaar.MainActivity
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.GuestActionV1
import com.jyotirmoyb.udhaar.util.InterestCalculator

object GuestActionNotificationHelper {
    const val CHANNEL_ID = "udhaar_guest_actions_channel"
    const val EXTRA_SHOW_UPDATES = "extra_show_updates"
    const val EXTRA_TRANSACTION_ID = "extra_transaction_id"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Guest Actions & Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for borrower confirmations, disputes, payment claims, and date proposals"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showPendingActionNotification(
        context: Context,
        action: GuestActionV1,
        partyName: String,
        currency: CurrencyType = CurrencyType.INR,
        transactionId: Long? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val title = when (action) {
            is GuestActionV1.ClaimPayment -> "Payment Claim • $partyName"
            is GuestActionV1.Confirm -> "Record Confirmed • $partyName"
            is GuestActionV1.Dispute -> "Record Disputed • $partyName"
            is GuestActionV1.ProposeDate -> "Due Date Proposal • $partyName"
        }

        val body = when (action) {
            is GuestActionV1.ClaimPayment ->
                "$partyName says they paid ${InterestCalculator.formatPaise(action.amountPaise, currency)} via ${action.paymentMode.label}."
            is GuestActionV1.Confirm ->
                "$partyName confirmed the record terms."
            is GuestActionV1.Dispute ->
                "$partyName disputed: ${action.reason.ifBlank { "Terms disputed" }}"
            is GuestActionV1.ProposeDate ->
                "$partyName proposed new return date: ${InterestCalculator.formatDate(action.proposedDateMs)}"
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SHOW_UPDATES, true)
            if (transactionId != null && transactionId > 0L) {
                putExtra(EXTRA_TRANSACTION_ID, transactionId)
            }
        }

        val notificationId = action.compositeKey.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(notificationId, notification)
    }
}
