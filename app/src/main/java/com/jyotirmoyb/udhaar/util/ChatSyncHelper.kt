package com.jyotirmoyb.udhaar.util

import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatThreadMetadata
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.data.cloud.CloudChatMessage
import com.jyotirmoyb.udhaar.data.cloud.CloudChatThread
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first

object ChatSyncHelper {
    fun transactionThreadKey(transactionId: Long): String = "tx_$transactionId"

    fun partyGeneralThreadKey(partyName: String): String =
        "party_${partyName.trim().lowercase()}"

    fun cloudThreadIdFor(
        scope: ChatThreadScope,
        remoteId: String,
        partyName: String,
        fallbackThreadKey: String
    ): String {
        return if (scope == ChatThreadScope.TRANSACTION && remoteId.isNotBlank()) {
            "tx_${remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}"
        } else if (scope == ChatThreadScope.PARTY_GENERAL && partyName.isNotBlank()) {
            "party_${partyName.trim().lowercase().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}"
        } else {
            fallbackThreadKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        }
    }

    suspend fun syncModerationFlagsToCloud(
        meta: ChatThreadMetadata,
        settingsRepository: SettingsRepository,
        cloudRepository: VerificationCloudRepository?
    ) {
        val settings = settingsRepository.userSettings.first()
        if (!settings.cloudVerificationConsent || settings.cloudUserId.isBlank()) return
        runCatching {
            val uid = settings.cloudUserId
            val cloudThreadId = cloudThreadIdFor(meta.threadScope, meta.remoteThreadId, meta.partyName, meta.threadKey)
            cloudRepository?.updateThreadMuteBlockReport(
                threadId = cloudThreadId,
                mutedByUids = if (meta.isMuted) listOf(uid) else emptyList(),
                blockedByUids = if (meta.isBlocked) listOf(uid) else emptyList(),
                reportedByUids = if (meta.isReported) listOf(uid) else emptyList(),
                reportReason = meta.reportReason
            )
        }
    }

    suspend fun syncChatThreadAndMessageToCloudIfEnabled(
        meta: ChatThreadMetadata,
        message: ChatMessageEntity,
        settings: UserSettings,
        counterpartyUserId: String,
        cloudRepository: VerificationCloudRepository?
    ) {
        if (!settings.cloudVerificationConsent || settings.cloudUserId.isBlank()) return
        runCatching {
            val uid = settings.cloudUserId
            val cloudThreadId = cloudThreadIdFor(meta.threadScope, meta.remoteThreadId, meta.partyName, meta.threadKey)
            val participants = listOfNotNull(
                uid.takeIf { it.isNotBlank() },
                counterpartyUserId.takeIf { it.isNotBlank() }
            ).distinct()
            val safeRemoteId = meta.remoteThreadId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")

            cloudRepository?.upsertChatThread(
                CloudChatThread(
                    threadId = cloudThreadId,
                    scope = meta.threadScope.name,
                    remoteId = safeRemoteId,
                    partyName = meta.partyName,
                    creatorUid = uid,
                    counterpartyUid = counterpartyUserId,
                    participantUids = participants,
                    mutedByUids = if (meta.isMuted) listOf(uid) else emptyList(),
                    blockedByUids = if (meta.isBlocked) listOf(uid) else emptyList(),
                    reportedByUids = if (meta.isReported) listOf(uid) else emptyList(),
                    reportReason = meta.reportReason,
                    lastMessagePreview = message.bodyText.take(120),
                    lastMessageAtMillis = message.sentAtMillis
                )
            )

            cloudRepository?.sendChatMessage(
                CloudChatMessage(
                    messageId = message.remoteMessageId.ifBlank { "msg_${message.id}" },
                    threadId = cloudThreadId,
                    remoteId = safeRemoteId,
                    senderUid = uid,
                    senderName = message.senderName,
                    participantUids = participants,
                    messageType = message.messageType.name,
                    textContent = message.bodyText,
                    attachmentRef = message.attachmentUri,
                    attachmentMimeType = message.attachmentMimeType,
                    proposedDueDateMillis = message.proposedDueDateMillis ?: -1L,
                    extensionStatus = message.extensionStatus.name,
                    deliveryStatus = message.deliveryStatus.name,
                    sentAtMillis = message.sentAtMillis
                )
            )
        }
    }
}
