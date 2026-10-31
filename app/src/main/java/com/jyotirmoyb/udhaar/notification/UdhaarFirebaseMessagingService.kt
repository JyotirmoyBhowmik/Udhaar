package com.jyotirmoyb.udhaar.notification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.security.EncryptedStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging service that:
 * 1. Persists the device's FCM registration token in [EncryptedStorageManager] and syncs it to
 *    `/user_profiles/{uid}` (`fcmToken`) whenever a user is signed in.
 * 2. Dispatches rich notifications when a push arrives for a new chat message, extension request,
 *    payment proof, or two-party verification request/response even when the app is closed.
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
        val eventType = data["type"] ?: data["eventType"] ?: "CHAT_MESSAGE"
        val threadKey = data["threadKey"] ?: data["threadId"].orEmpty()
        val remoteRecordId = data["remoteId"] ?: data["remoteRecordId"].orEmpty()
        val txId = data["transactionId"]?.toLongOrNull() ?: -1L
        val partyName = data["partyName"] ?: data["senderName"].orEmpty()
        val senderName = data["senderName"] ?: partyName.ifBlank { "Udhaar Contact" }
        val messageType = data["messageType"].orEmpty()

        val fallbackTitle = when (eventType.uppercase()) {
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
            ?: "Tap to open in Udhaar."

        val title = data["title"] ?: remoteMessage.notification?.title ?: fallbackTitle

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
