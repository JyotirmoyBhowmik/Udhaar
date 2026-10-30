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
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatMessageType
import com.jyotirmoyb.udhaar.data.ChatThreadMetadata

/**
 * Dispatches rich notifications for incoming Phase 4 in-app communication messages
 * and due-date extension proposals (respecting per-thread mute and block settings).
 */
object ChatNotificationHelper {
    const val CHANNEL_ID = "udhaar_chat_messages_channel"
    const val EXTRA_CHAT_THREAD_KEY = "extra_chat_thread_key"
    const val EXTRA_CHAT_TRANSACTION_ID = "extra_chat_transaction_id"
    const val EXTRA_CHAT_PARTY_NAME = "extra_chat_party_name"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Udhaar In-App Messages & Extensions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Push & real-time notifications for party messages, payment proofs, and due-date extension requests"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showIncomingChatNotification(
        context: Context,
        threadMetadata: ChatThreadMetadata?,
        message: ChatMessageEntity
    ) {
        // Respect thread mute and block controls
        if (threadMetadata?.isMuted == true || threadMetadata?.isBlocked == true) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CHAT_THREAD_KEY, message.threadKey)
            message.transactionId?.let { putExtra(EXTRA_CHAT_TRANSACTION_ID, it) }
            putExtra(EXTRA_CHAT_PARTY_NAME, message.partyName)
        }

        val notificationId = (message.threadKey.hashCode() and 0x7FFFFFFF).coerceAtLeast(10_000)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sender = message.senderName.ifBlank { message.partyName.ifBlank { "Party" } }
        val title = when (message.messageType) {
            ChatMessageType.EXTENSION_REQUEST -> "Due Date Extension Request • $sender"
            ChatMessageType.PAYMENT_PROOF -> "Payment Proof Shared • $sender"
            else -> "New Message from $sender"
        }
        val body = when (message.messageType) {
            ChatMessageType.EXTENSION_REQUEST ->
                message.bodyText.ifBlank { "$sender proposed a new due date. Tap to accept or decline." }
            ChatMessageType.PAYMENT_PROOF ->
                message.bodyText.ifBlank { "$sender attached a payment proof (${message.attachmentMimeType.ifBlank { "Receipt" }})." }
            else -> message.bodyText
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
