package com.jyotirmoyb.udhaar.data.repository

import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatMessageType
import com.jyotirmoyb.udhaar.data.ChatThreadMetadata
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.util.SafeLogger
import com.jyotirmoyb.udhaar.util.UdhaarAppError
import com.jyotirmoyb.udhaar.util.UdhaarResult
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository responsible for in-app chat threads, messages, due-date extension requests,
 * and thread moderation controls (mute, block, report).
 */
class ChatRepository(
    private val delegate: UdhaarRepository
) {
    val allChatThreads: Flow<List<ChatThreadMetadata>> = delegate.allChatThreads
    val allChatMessages: Flow<List<ChatMessageEntity>> = delegate.allChatMessages

    suspend fun sendMessage(
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
        proofFileName: String = "",
        fromCounterparty: Boolean = false
    ): UdhaarResult<ChatMessageEntity?> {
        return try {
            val saved = delegate.sendChatMessage(
                threadKey = threadKey,
                scope = scope,
                transaction = transaction,
                transactionId = transactionId,
                partyId = partyId,
                partyName = partyName,
                messageType = messageType,
                textContent = textContent,
                attachmentUri = attachmentUri,
                attachmentMimeType = attachmentMimeType,
                proofFileName = proofFileName,
                fromCounterparty = fromCounterparty
            )
            UdhaarResult.Success(saved)
        } catch (t: Throwable) {
            SafeLogger.e("ChatRepository", "Failed sending chat message", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Failed to send message"), t)
        }
    }

    suspend fun markThreadMessagesRead(threadKey: String): UdhaarResult<Unit> {
        return try {
            delegate.markThreadRead(threadKey)
            UdhaarResult.Success(Unit)
        } catch (t: Throwable) {
            SafeLogger.w("ChatRepository", "Failed marking thread read", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Could not mark messages read"), t)
        }
    }

    suspend fun requestDueDateExtension(
        transaction: UdhaarTransaction,
        proposedDueDateMillis: Long,
        note: String = ""
    ): UdhaarResult<ChatMessageEntity> {
        return try {
            val msg = delegate.requestDueDateExtension(
                transaction = transaction,
                proposedDueDateMillis = proposedDueDateMillis,
                note = note
            )
            UdhaarResult.Success(msg)
        } catch (t: Throwable) {
            SafeLogger.e("ChatRepository", "Failed requesting due date extension", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Extension request failed"), t)
        }
    }

    suspend fun respondToExtensionRequest(
        transaction: UdhaarTransaction? = null,
        message: ChatMessageEntity,
        accept: Boolean
    ): UdhaarResult<UdhaarTransaction> {
        return try {
            val updated = delegate.respondToExtensionRequest(
                transaction = transaction,
                message = message,
                accept = accept
            )
            UdhaarResult.Success(updated)
        } catch (t: Throwable) {
            SafeLogger.e("ChatRepository", "Failed responding to extension request", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Could not update extension status"), t)
        }
    }

    suspend fun toggleThreadMute(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long?,
        remoteId: String,
        partyName: String
    ): UdhaarResult<ChatThreadMetadata> {
        return try {
            val updated = delegate.toggleThreadMute(
                threadKey = threadKey,
                scope = scope,
                transactionId = transactionId,
                remoteId = remoteId,
                partyName = partyName
            )
            UdhaarResult.Success(updated)
        } catch (t: Throwable) {
            SafeLogger.e("ChatRepository", "Failed toggling thread mute", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Could not update mute state"), t)
        }
    }

    suspend fun toggleThreadBlock(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long?,
        remoteId: String,
        partyName: String
    ): UdhaarResult<ChatThreadMetadata> {
        return try {
            val updated = delegate.toggleThreadBlock(
                threadKey = threadKey,
                scope = scope,
                transactionId = transactionId,
                remoteId = remoteId,
                partyName = partyName
            )
            UdhaarResult.Success(updated)
        } catch (t: Throwable) {
            SafeLogger.e("ChatRepository", "Failed toggling thread block", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Could not update block state"), t)
        }
    }

    suspend fun reportAbusiveUser(
        threadKey: String,
        reason: String,
        scope: ChatThreadScope,
        transactionId: Long?,
        remoteId: String,
        partyName: String
    ): UdhaarResult<ChatThreadMetadata> {
        return try {
            val updated = delegate.reportAbusiveUserInThread(
                threadKey = threadKey,
                reason = reason,
                scope = scope,
                transactionId = transactionId,
                remoteId = remoteId,
                partyName = partyName
            )
            UdhaarResult.Success(updated)
        } catch (t: Throwable) {
            SafeLogger.e("ChatRepository", "Failed reporting abusive user", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Could not submit abuse report"), t)
        }
    }
}
