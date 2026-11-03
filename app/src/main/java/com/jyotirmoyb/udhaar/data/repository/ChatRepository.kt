package com.jyotirmoyb.udhaar.data.repository

import android.content.Context
import com.jyotirmoyb.udhaar.data.AuditEventType
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatMessageType
import com.jyotirmoyb.udhaar.data.ChatThreadMetadata
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.ExtensionRequestStatus
import com.jyotirmoyb.udhaar.data.MessageDeliveryStatus
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.data.cloud.CloudAuditEvent
import com.jyotirmoyb.udhaar.data.cloud.CloudChatMessage
import com.jyotirmoyb.udhaar.data.cloud.CloudChatThread
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.notification.ChatNotificationHelper
import com.jyotirmoyb.udhaar.util.AuditTrailHelper
import com.jyotirmoyb.udhaar.util.ChatExtensionHelper
import com.jyotirmoyb.udhaar.util.ChatSyncHelper
import com.jyotirmoyb.udhaar.util.SafeLogger
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

open class ChatRepository(
    private val dao: UdhaarDao,
    private val settingsRepository: SettingsRepository,
    private val cloudRepository: VerificationCloudRepository? = null,
    @ApplicationContext private val context: Context? = null
) {
    val allChatThreads: Flow<List<ChatThreadMetadata>> = dao.getAllChatThreads().catch { e ->
        SafeLogger.e("ChatRepository", "Failed reading chat threads flow", e)
        emit(emptyList())
    }

    val allChatMessages: Flow<List<ChatMessageEntity>> = dao.getAllChatMessages().catch { e ->
        SafeLogger.e("ChatRepository", "Failed reading chat messages flow", e)
        emit(emptyList())
    }

    companion object {
        fun transactionThreadKey(transactionId: Long): String =
            ChatSyncHelper.transactionThreadKey(transactionId)

        fun partyGeneralThreadKey(partyName: String): String =
            ChatSyncHelper.partyGeneralThreadKey(partyName)

        fun cloudThreadIdFor(
            scope: ChatThreadScope,
            remoteId: String,
            partyName: String,
            fallbackThreadKey: String
        ): String = ChatSyncHelper.cloudThreadIdFor(scope, remoteId, partyName, fallbackThreadKey)
    }

    suspend fun getOrCreateThreadMetadata(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String
    ): ChatThreadMetadata {
        val existing = dao.getChatThreadMetadata(threadKey)
        if (existing != null) return existing
        val created = ChatThreadMetadata(
            threadKey = threadKey,
            remoteThreadId = remoteId,
            threadScope = scope,
            transactionId = transactionId,
            partyId = partyId,
            partyName = partyName.trim()
        )
        dao.upsertChatThreadMetadata(created)
        return created
    }

    fun buildTransactionThreadKey(transaction: UdhaarTransaction): String =
        transactionThreadKey(transaction.id)

    suspend fun sendChatMessage(
        threadKey: String,
        scope: ChatThreadScope,
        transaction: UdhaarTransaction? = null,
        transactionId: Long? = null,
        partyId: Long? = null,
        partyName: String,
        messageType: ChatMessageType = ChatMessageType.TEXT,
        textContent: String,
        attachmentUri: String = "",
        attachmentMimeType: String = "",
        proofAttachmentUri: String = "",
        proofMimeType: String = "",
        proofFileName: String = "",
        fromCounterparty: Boolean = false
    ): ChatMessageEntity? {
        val resolvedTx = transaction ?: transactionId?.let { dao.getTransactionById(it) }
        val finalAttachmentUri = attachmentUri.ifBlank { proofAttachmentUri }.trim()
        val finalMimeType = attachmentMimeType.ifBlank { proofMimeType }.trim()
        val cleanText = textContent.trim()
        if (cleanText.isEmpty() && finalAttachmentUri.isBlank()) return null

        val meta = getOrCreateThreadMetadata(
            threadKey = threadKey,
            scope = scope,
            transactionId = resolvedTx?.id ?: transactionId,
            remoteId = resolvedTx?.remoteId.orEmpty(),
            partyId = partyId ?: resolvedTx?.partyId,
            partyName = partyName
        )
        if (meta.isBlocked) return null

        val settings = settingsRepository.userSettings.first()
        val myName = settings.payeeName.ifBlank { "You" }
        val nowMillis = System.currentTimeMillis()
        val remoteMsgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        val senderName = if (fromCounterparty) partyName.trim().ifBlank { "Party" } else myName
        val senderUid = if (fromCounterparty) {
            resolvedTx?.counterpartyUserId?.ifBlank { "party_remote" } ?: "party_remote"
        } else {
            settings.cloudUserId.ifBlank { "local_me" }
        }

        val initialDelivery = when {
            fromCounterparty -> MessageDeliveryStatus.DELIVERED
            settings.cloudVerificationConsent && settings.cloudUserId.isNotBlank() -> MessageDeliveryStatus.DELIVERED
            resolvedTx != null && resolvedTx.verificationStatus == VerificationStatus.CONFIRMED -> MessageDeliveryStatus.DELIVERED
            else -> MessageDeliveryStatus.SENT
        }

        val msgEntity = ChatMessageEntity(
            remoteMessageId = remoteMsgId,
            threadKey = threadKey,
            threadScope = scope,
            transactionId = resolvedTx?.id ?: transactionId,
            partyId = partyId ?: resolvedTx?.partyId,
            partyName = partyName.trim(),
            senderUserId = senderUid,
            senderName = senderName,
            isFromMe = !fromCounterparty,
            messageType = messageType,
            bodyText = cleanText.ifEmpty {
                if (finalMimeType.contains("pdf", ignoreCase = true)) "Attached Payment Proof (PDF)"
                else "Attached Payment Proof (Image)"
            },
            attachmentUri = finalAttachmentUri,
            attachmentMimeType = finalMimeType,
            attachmentName = proofFileName,
            proposedDueDateMillis = null,
            extensionStatus = ExtensionRequestStatus.NONE,
            deliveryStatus = initialDelivery,
            isUnreadForMe = fromCounterparty,
            sentAtMillis = nowMillis,
            deliveredAtMillis = if (initialDelivery != MessageDeliveryStatus.SENT) nowMillis else null,
            readAtMillis = null
        )

        val insertedId = dao.insertChatMessage(msgEntity)
        val savedMsg = msgEntity.copy(id = insertedId)

        val updatedMeta = meta.copy(
            counterpartyTyping = false,
            typingUpdatedAtMillis = nowMillis,
            updatedAtMillis = nowMillis
        )
        dao.upsertChatThreadMetadata(updatedMeta)

        if (fromCounterparty && context != null) {
            ChatNotificationHelper.showIncomingChatNotification(
                context = context,
                threadMetadata = updatedMeta,
                message = savedMsg
            )
        }

        syncChatThreadAndMessageToCloudIfEnabled(
            meta = updatedMeta,
            message = savedMsg,
            settings = settings,
            counterpartyUserId = resolvedTx?.counterpartyUserId.orEmpty()
        )

        return savedMsg
    }

    suspend fun requestDueDateExtension(
        transaction: UdhaarTransaction,
        proposedDueDateMillis: Long,
        note: String = "",
        reasonText: String = "",
        requestedByCounterparty: Boolean = false
    ): ChatMessageEntity = ChatExtensionHelper.requestDueDateExtension(
        dao = dao,
        settingsRepository = settingsRepository,
        cloudRepository = cloudRepository,
        context = context,
        chatRepository = this,
        transaction = transaction,
        proposedDueDateMillis = proposedDueDateMillis,
        note = note,
        reasonText = reasonText,
        requestedByCounterparty = requestedByCounterparty
    )

    suspend fun respondToExtensionRequest(
        transaction: UdhaarTransaction? = null,
        message: ChatMessageEntity,
        accept: Boolean,
        responderName: String = "You"
    ): UdhaarTransaction = ChatExtensionHelper.respondToExtensionRequest(
        dao = dao,
        settingsRepository = settingsRepository,
        cloudRepository = cloudRepository,
        chatRepository = this,
        transaction = transaction,
        message = message,
        accept = accept,
        responderName = responderName
    )

    suspend fun markThreadRead(threadKey: String) {
        val nowMillis = System.currentTimeMillis()
        dao.markThreadMessagesRead(threadKey, nowMillis)
    }

    suspend fun setTypingIndicator(
        threadKey: String,
        scope: ChatThreadScope = ChatThreadScope.TRANSACTION,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String = "",
        isCounterpartyTyping: Boolean
    ) {
        val nowMillis = System.currentTimeMillis()
        val meta = getOrCreateThreadMetadata(
            threadKey = threadKey,
            scope = scope,
            transactionId = transactionId,
            remoteId = remoteId,
            partyId = partyId,
            partyName = partyName
        )
        val updated = meta.copy(
            counterpartyTyping = isCounterpartyTyping,
            typingUpdatedAtMillis = nowMillis,
            updatedAtMillis = nowMillis
        )
        dao.upsertChatThreadMetadata(updated)

        val settings = settingsRepository.userSettings.first()
        if (settings.cloudVerificationConsent && settings.cloudUserId.isNotBlank()) {
            runCatching {
                val cloudThreadId = cloudThreadIdFor(updated.threadScope, updated.remoteThreadId, updated.partyName, threadKey)
                cloudRepository?.updateTypingStatus(
                    threadId = cloudThreadId,
                    typingUserId = if (isCounterpartyTyping) "party" else settings.cloudUserId,
                    typingUserName = if (isCounterpartyTyping) updated.partyName else settings.payeeName.ifBlank { "You" },
                    typingUpdatedAtMillis = nowMillis
                )
            }
        }
    }

    suspend fun toggleThreadMute(
        threadKey: String,
        isMuted: Boolean? = null,
        scope: ChatThreadScope = ChatThreadScope.TRANSACTION,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String = ""
    ): ChatThreadMetadata {
        val meta = getOrCreateThreadMetadata(threadKey, scope, transactionId, remoteId, partyId, partyName)
        val updated = meta.copy(isMuted = isMuted ?: !meta.isMuted, updatedAtMillis = System.currentTimeMillis())
        dao.upsertChatThreadMetadata(updated)
        syncModerationFlagsToCloud(updated)
        return updated
    }

    suspend fun toggleThreadBlock(
        threadKey: String,
        isBlocked: Boolean? = null,
        scope: ChatThreadScope = ChatThreadScope.TRANSACTION,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String = ""
    ): ChatThreadMetadata {
        val meta = getOrCreateThreadMetadata(threadKey, scope, transactionId, remoteId, partyId, partyName)
        val updated = meta.copy(isBlocked = isBlocked ?: !meta.isBlocked, updatedAtMillis = System.currentTimeMillis())
        dao.upsertChatThreadMetadata(updated)
        syncModerationFlagsToCloud(updated)
        return updated
    }

    suspend fun reportAbusiveUserInThread(
        threadKey: String,
        reason: String,
        scope: ChatThreadScope = ChatThreadScope.TRANSACTION,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String = ""
    ): ChatThreadMetadata {
        val meta = getOrCreateThreadMetadata(threadKey, scope, transactionId, remoteId, partyId, partyName)
        val updated = meta.copy(
            isReported = true,
            isBlocked = true,
            reportReason = reason.trim().ifBlank { "Abusive or spam behavior" },
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.upsertChatThreadMetadata(updated)
        syncModerationFlagsToCloud(updated)
        return updated
    }

    private suspend fun syncModerationFlagsToCloud(meta: ChatThreadMetadata) {
        ChatSyncHelper.syncModerationFlagsToCloud(meta, settingsRepository, cloudRepository)
    }

    private suspend fun syncChatThreadAndMessageToCloudIfEnabled(
        meta: ChatThreadMetadata,
        message: ChatMessageEntity,
        settings: UserSettings,
        counterpartyUserId: String
    ) {
        ChatSyncHelper.syncChatThreadAndMessageToCloudIfEnabled(
            meta = meta,
            message = message,
            settings = settings,
            counterpartyUserId = counterpartyUserId,
            cloudRepository = cloudRepository
        )
    }
}
