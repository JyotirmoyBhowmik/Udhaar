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
        fallbackThreadKey: String,
        myUid: String = "",
        counterpartyUid: String = ""
    ): String {
        return if (scope == ChatThreadScope.TRANSACTION && remoteId.isNotBlank()) {
            "tx_${remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}"
        } else if (scope == ChatThreadScope.PARTY_GENERAL || scope == ChatThreadScope.PARTY) {
            val cleanMy = myUid.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val cleanOther = counterpartyUid.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            if (cleanMy.isNotBlank() && cleanOther.isNotBlank()) {
                val pair = listOf(cleanMy, cleanOther).sorted()
                "dm_${pair[0]}_${pair[1]}"
            } else if (partyName.isNotBlank()) {
                "party_${partyName.trim().lowercase().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}"
            } else {
                fallbackThreadKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            }
        } else {
            fallbackThreadKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        }
    }

    suspend fun syncModerationFlagsToCloud(
        meta: ChatThreadMetadata,
        settingsRepository: SettingsRepository,
        cloudRepository: VerificationCloudRepository?,
        counterpartyUid: String = ""
    ) {
        val settings = settingsRepository.userSettings.first()
        if (!settings.cloudVerificationConsent || settings.cloudUserId.isBlank()) return
        runCatching {
            val uid = settings.cloudUserId
            val cloudThreadId = cloudThreadIdFor(
                scope = meta.threadScope,
                remoteId = meta.remoteThreadId,
                partyName = meta.partyName,
                fallbackThreadKey = meta.threadKey,
                myUid = uid,
                counterpartyUid = counterpartyUid
            )
            cloudRepository?.updateThreadMuteBlockReport(
                threadId = cloudThreadId,
                mutedByUids = if (meta.isMuted) listOf(uid) else emptyList(),
                blockedByUids = if (meta.isBlocked) listOf(uid) else emptyList(),
                reportedByUids = if (meta.isReported) listOf(uid) else emptyList(),
                reportReason = meta.reportReason
            )
        }
    }

    suspend fun syncUserSafetyToCloud(
        safety: com.jyotirmoyb.udhaar.data.UserChatSafetyEntity,
        settingsRepository: SettingsRepository,
        cloudRepository: VerificationCloudRepository?
    ) {
        val settings = settingsRepository.userSettings.first()
        val myUid = settings.cloudUserId.trim()
        val targetUid = safety.counterpartyUid.trim()
        if (!settings.cloudVerificationConsent || myUid.isBlank() || targetUid.isBlank()) return
        runCatching {
            if (!safety.isMuted && !safety.isBlocked && !safety.isReported) {
                cloudRepository?.deleteUserSafetyControl(myUid, targetUid)
            } else {
                cloudRepository?.upsertUserSafetyControl(
                    ownerUid = myUid,
                    targetUid = targetUid,
                    isMuted = safety.isMuted,
                    isBlocked = safety.isBlocked,
                    isReported = safety.isReported,
                    reportReason = safety.reportReason
                )
            }
        }
    }

    suspend fun syncChatThreadAndMessageToCloudIfEnabled(
        meta: ChatThreadMetadata,
        message: ChatMessageEntity,
        settings: UserSettings,
        counterpartyUserId: String,
        cloudRepository: VerificationCloudRepository?,
        fallbackRemoteId: String = ""
    ): Boolean {
        if (!settings.cloudVerificationConsent || settings.cloudUserId.isBlank() || cloudRepository == null) {
            return false
        }
        val uid = settings.cloudUserId.trim()
        val effectiveRemoteId = meta.remoteThreadId.ifBlank { fallbackRemoteId }.trim()
        val safeRemoteId = effectiveRemoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val cloudThreadId = cloudThreadIdFor(
            scope = meta.threadScope,
            remoteId = effectiveRemoteId,
            partyName = meta.partyName,
            fallbackThreadKey = meta.threadKey,
            myUid = uid,
            counterpartyUid = counterpartyUserId
        )
        val hasDeterministicCloudThread = (cloudThreadId.startsWith("tx_") && safeRemoteId.isNotBlank()) ||
            cloudThreadId.startsWith("dm_")
        if (!hasDeterministicCloudThread) return false

        return runCatching {
            val cleanCounterpartyUid = counterpartyUserId.trim().takeIf { it.isNotBlank() && it != uid }.orEmpty()
            val participants = listOfNotNull(
                uid.takeIf { it.isNotBlank() },
                cleanCounterpartyUid.takeIf { it.isNotBlank() }
            ).distinct()

            cloudRepository.upsertChatThread(
                CloudChatThread(
                    threadId = cloudThreadId,
                    scope = meta.threadScope.name,
                    remoteId = safeRemoteId,
                    partyName = meta.partyName,
                    creatorUid = uid,
                    counterpartyUid = cleanCounterpartyUid,
                    participantUids = participants,
                    mutedByUids = if (meta.isMuted) listOf(uid) else emptyList(),
                    blockedByUids = if (meta.isBlocked) listOf(uid) else emptyList(),
                    reportedByUids = if (meta.isReported) listOf(uid) else emptyList(),
                    reportReason = meta.reportReason,
                    lastMessagePreview = message.bodyText.take(120),
                    lastMessageAtMillis = message.sentAtMillis
                )
            )

            cloudRepository.sendChatMessage(
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
            true
        }.onFailure { err ->
            SafeLogger.w("ChatSyncHelper", "Failed syncing chat message to cloud thread=$cloudThreadId", err)
        }.getOrDefault(false)
    }
}
