package com.jyotirmoyb.udhaar.notification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.reminder.ReminderEngine
import com.jyotirmoyb.udhaar.security.EncryptedStorageManager
import com.jyotirmoyb.udhaar.util.SafeLogger
import com.jyotirmoyb.udhaar.util.SettingsPreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging service that:
 * 1. Persists the device's FCM registration token in [EncryptedStorageManager] and syncs it to
 *    `/user_profiles/{uid}` (`fcmToken`) whenever a user is signed in.
 * 2. Dispatches rich notifications when a push arrives for chat messages, extension requests,
 *    payment proofs, two-party verifications, and Stage W3 Guest Web Flow actions.
 * 3. Enforces quiet hours rules to prevent annoying the user at night.
 */
class UdhaarFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.isBlank()) return
        EncryptedStorageManager.getEncryptedPreferences(applicationContext)
            .edit()
            .putString(KEY_FCM_TOKEN, token.trim())
            .apply()

        val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull().orEmpty()
        if (uid.isNotBlank()) {
            serviceScope.launch {
                runCatching {
                    VerificationCloudRepository(applicationContext).updateUserFcmToken(uid, token.trim())
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val eventType = (data["type"] ?: data["eventType"] ?: "CHAT_MESSAGE").uppercase()
        val threadKey = data["threadKey"] ?: data["threadId"].orEmpty()
        val remoteRecordId = data["recordId"] ?: data["remoteId"] ?: data["remoteRecordId"].orEmpty()
        val txId = data["transactionId"]?.toLongOrNull() ?: -1L
        val partyName = data["partyName"] ?: data["senderName"].orEmpty()
        val senderName = data["senderName"] ?: partyName.ifBlank { "Guest Counterparty" }
        val messageType = data["messageType"].orEmpty()

        val nowMillis = System.currentTimeMillis()
        val settings = runCatching {
            val prefs = EncryptedStorageManager.getEncryptedPreferences(applicationContext)
            // Default quiet hours 21:00 to 08:00
            val qhEnabled = prefs.getBoolean("quiet_hours_enabled", true)
            val qhStart = prefs.getInt("quiet_hours_start_hour", 21)
            val qhEnd = prefs.getInt("quiet_hours_end_hour", 8)
            Triple(qhEnabled, qhStart, qhEnd)
        }.getOrDefault(Triple(true, 21, 8))

        val inQuietHours = settings.first && ReminderEngine.isInQuietHours(
            nowMillis = nowMillis,
            startHour = settings.second,
            endHour = settings.third
        )

        val fallbackTitle = when (eventType) {
            "GUEST_WEB_CONFIRMED" -> "Record Confirmed • $senderName"
            "GUEST_WEB_DISPUTED" -> "Record Disputed • $senderName"
            "GUEST_WEB_PAYMENT_CLAIM" -> "Payment Claim • $senderName"
            "GUEST_WEB_DATE_PROPOSAL" -> "Return Date Proposed • $senderName"
            "GUEST_WEB_SET_UPI" -> "UPI Details Updated • $senderName"
            "VERIFICATION_REQUEST" -> "Verification Requested • $senderName"
            "VERIFICATION_CONFIRMED" -> "Record Confirmed • $senderName"
            "VERIFICATION_DISPUTED" -> "Record Disputed • $senderName"
            else -> when (messageType.uppercase()) {
                "EXTENSION_REQUEST" -> "Due Date Extension Request • $senderName"
                "PAYMENT_PROOF" -> "Payment Proof Shared • $senderName"
                else -> "New Message from $senderName"
            }
        }

        val fallbackBody = data["body"]
            ?: data["textContent"]
            ?: remoteMessage.notification?.body
            ?: when (eventType) {
                "GUEST_WEB_CONFIRMED" -> "$senderName verified the record terms via web link."
                "GUEST_WEB_DISPUTED" -> "$senderName disputed the record terms. Tap to inspect."
                "GUEST_WEB_PAYMENT_CLAIM" -> "$senderName submitted a payment claim. Tap to confirm or reject."
                "GUEST_WEB_DATE_PROPOSAL" -> "$senderName proposed a return date. Tap to accept or decline."
                "GUEST_WEB_SET_UPI" -> "$senderName added their repayment UPI ID."
                else -> "Tap to open in Udhaar."
            }

        val title = data["title"] ?: remoteMessage.notification?.title ?: fallbackTitle

        // If in quiet hours, notifications are suppressed or delayed
        if (inQuietHours) {
            SafeLogger.i("FCMService", "Notification held during quiet hours ($title)")
            return
        }

        ChatNotificationHelper.showFcmPushNotification(
            context = applicationContext,
            title = title,
            body = fallbackBody,
            threadKey = threadKey,
            transactionId = txId.takeIf { it > 0L },
            remoteRecordId = remoteRecordId,
            partyName = partyName.ifBlank { senderName }
        )
    }

    companion object {
        const val KEY_FCM_TOKEN = "fcm_registration_token"
    }
}
