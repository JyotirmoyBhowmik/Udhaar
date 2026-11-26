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
import com.jyotirmoyb.udhaar.data.UserChatSafetyEntity
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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

    val allUserChatSafety: Flow<List<UserChatSafetyEntity>> = dao.getAllUserChatSafety().catch { e ->
        SafeLogger.e("ChatRepository", "Failed reading user chat safety flow", e)
        emit(emptyList())
    }

    companion object {
        fun transactionThreadKey(transactionId: Long): String =
            ChatSyncHelper.transactionThreadKey(transactionId)

        fun partyGeneralThreadKey(partyName: String): String =
            ChatSyncHelper.partyGeneralThreadKey(partyName)

        fun userSafetyKey(counterpartyUid: String = "", partyId: Long? = null, partyName: String = ""): String {
            val cleanUid = counterpartyUid.trim()
            if (cleanUid.isNotBlank()) return "uid_$cleanUid"
            if (partyId != null && partyId > 0L) return "partyid_$partyId"
            return "party_${partyName.trim().lowercase().ifBlank { "unknown" }}"
        }

        fun cloudThreadIdFor(
            scope: ChatThreadScope,
            remoteId: String,
            partyName: String,
            fallbackThreadKey: String,
            myUid: String = "",
            counterpartyUid: String = ""
        ): String = ChatSyncHelper.cloudThreadIdFor(scope, remoteId, partyName, fallbackThreadKey, myUid, counterpartyUid)
    }

    suspend fun resolveCounterpartyUid(
        transaction: UdhaarTransaction? = null,
        partyId: Long? = null,
        partyName: String = ""
    ): String {
        val txUid = transaction?.counterpartyUserId?.trim().orEmpty()
        if (txUid.isNotBlank()) return txUid

        if (transaction != null && transaction.remoteId.isNotBlank()) {
            val fromDbByRemote = dao.getTransactionByRemoteId(transaction.remoteId)?.counterpartyUserId?.trim().orEmpty()
            if (fromDbByRemote.isNotBlank()) return fromDbByRemote
        }

        val party = when {
            partyId != null && partyId > 0L -> dao.getPartyById(partyId)
            transaction?.partyId != null && transaction.partyId > 0L -> dao.getPartyById(transaction.partyId)
            partyName.isNotBlank() -> dao.getPartyByName(partyName.trim())
            transaction != null && transaction.partyName.isNotBlank() -> dao.getPartyByName(transaction.partyName.trim())
            else -> null
        }
        if (party != null && party.linkedUserId.isNotBlank()) {
            return party.linkedUserId.trim()
        }

        val resolvedName = partyName.trim().ifBlank { transaction?.partyName?.trim().orEmpty() }
        val resolvedPartyId = party?.partyId ?: partyId ?: transaction?.partyId
        val fromAnyTx = dao.getAllTransactionsSnapshot().firstOrNull { candidate ->
            candidate.counterpartyUserId.isNotBlank() && (
                (resolvedPartyId != null && resolvedPartyId > 0L && candidate.partyId == resolvedPartyId) ||
                    (resolvedName.isNotBlank() && candidate.partyName.trim().equals(resolvedName, ignoreCase = true))
                )
        }?.counterpartyUserId?.trim().orEmpty()

        if (fromAnyTx.isNotBlank() && party != null && party.linkedUserId.isBlank()) {
            dao.updateParty(party.copy(linkedUserId = fromAnyTx))
        }
        return fromAnyTx
    }

    private suspend fun linkCounterpartyUidLocally(
        transactionId: Long?,
        remoteId: String,
        partyId: Long?,
        partyName: String,
        counterpartyUid: String
    ) {
        val cleanUid = counterpartyUid.trim()
        if (cleanUid.isBlank()) return
        val tx = (transactionId?.let { dao.getTransactionById(it) })
            ?: remoteId.takeIf { it.isNotBlank() }?.let { dao.getTransactionByRemoteId(it) }
        if (tx != null && tx.counterpartyUserId != cleanUid) {
            dao.updateTransaction(tx.copy(counterpartyUserId = cleanUid))
        }
        val targetParty = when {
            partyId != null && partyId > 0L -> dao.getPartyById(partyId)
            tx?.partyId != null && tx.partyId > 0L -> dao.getPartyById(tx.partyId)
            partyName.isNotBlank() -> dao.getPartyByName(partyName.trim())
            else -> null
        }
        if (targetParty != null && targetParty.linkedUserId.isBlank()) {
            dao.updateParty(targetParty.copy(linkedUserId = cleanUid))
        }
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

        val resolvedCounterpartyUid = resolveCounterpartyUid(resolvedTx, partyId, partyName)
        if (isUserBlocked(resolvedCounterpartyUid, partyId ?: resolvedTx?.partyId, partyName)) return null

        val settings = settingsRepository.userSettings.first()
        val myName = settingsRepository.resolveSelfDisplayName(settings)
        val nowMillis = System.currentTimeMillis()
        val remoteMsgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val senderName = if (fromCounterparty) partyName.trim().ifBlank { "Party" } else myName
        val senderUid = if (fromCounterparty) {
            resolvedCounterpartyUid.ifBlank { "party_remote" }
        } else {
            settings.cloudUserId.ifBlank { "local_me" }
        }

        val initialDelivery = if (fromCounterparty) {
            MessageDeliveryStatus.DELIVERED
        } else {
            MessageDeliveryStatus.SENT
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

        val isUserMuted = isUserMuted(resolvedCounterpartyUid, partyId ?: resolvedTx?.partyId, partyName)
        val shouldNotify = fromCounterparty && context != null && !(updatedMeta.isMuted || isUserMuted)
        if (shouldNotify) {
            ChatNotificationHelper.showIncomingChatNotification(
                context = context,
                threadMetadata = updatedMeta,
                message = savedMsg
            )
        }

        val cloudSynced = syncChatThreadAndMessageToCloudIfEnabled(
            meta = updatedMeta,
            message = savedMsg,
            settings = settings,
            counterpartyUserId = resolvedCounterpartyUid
        )

        if (!fromCounterparty && cloudSynced && resolvedCounterpartyUid.isNotBlank()) {
            val deliveredAt = System.currentTimeMillis()
            val deliveredMsg = savedMsg.copy(
                deliveryStatus = MessageDeliveryStatus.DELIVERED,
                deliveredAtMillis = deliveredAt
            )
            dao.updateChatMessage(deliveredMsg)
            return deliveredMsg
        }

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
        responderName: String = ""
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
                val counterpartyUid = resolveCounterpartyUid(null, partyId, updated.partyName)
                val cloudThreadId = cloudThreadIdFor(
                    scope = updated.threadScope,
                    remoteId = updated.remoteThreadId,
                    partyName = updated.partyName,
                    fallbackThreadKey = threadKey,
                    myUid = settings.cloudUserId,
                    counterpartyUid = counterpartyUid
                )
                if (cloudThreadId.startsWith("tx_") || cloudThreadId.startsWith("dm_")) {
                    cloudRepository?.updateTypingStatus(
                        threadId = cloudThreadId,
                        typingUserId = if (isCounterpartyTyping) counterpartyUid.ifBlank { "party" } else settings.cloudUserId,
                        typingUserName = if (isCounterpartyTyping) updated.partyName else settingsRepository.resolveSelfDisplayName(settings),
                        typingUpdatedAtMillis = nowMillis
                    )
                }
            }
        }
    }

    suspend fun isUserBlocked(
        counterpartyUid: String = "",
        partyId: Long? = null,
        partyName: String = ""
    ): Boolean {
        val byUid = counterpartyUid.trim().takeIf { it.isNotBlank() }?.let { dao.getUserChatSafetyByUid(it) }
        if (byUid?.isBlocked == true) return true
        val key = userSafetyKey(counterpartyUid, partyId, partyName)
        val byKey = dao.getUserChatSafety(key)
        if (byKey?.isBlocked == true) return true
        if (partyId != null && partyId > 0L) {
            val byPartyId = dao.getUserChatSafety("partyid_$partyId")
            if (byPartyId?.isBlocked == true) return true
        }
        if (partyName.isNotBlank()) {
            val byName = dao.getUserChatSafety("party_${partyName.trim().lowercase()}")
            if (byName?.isBlocked == true) return true
        }
        return false
    }

    suspend fun isUserMuted(
        counterpartyUid: String = "",
        partyId: Long? = null,
        partyName: String = ""
    ): Boolean {
        val byUid = counterpartyUid.trim().takeIf { it.isNotBlank() }?.let { dao.getUserChatSafetyByUid(it) }
        if (byUid?.isMuted == true) return true
        val key = userSafetyKey(counterpartyUid, partyId, partyName)
        val byKey = dao.getUserChatSafety(key)
        if (byKey?.isMuted == true) return true
        if (partyName.isNotBlank()) {
            val byName = dao.getUserChatSafety("party_${partyName.trim().lowercase()}")
            if (byName?.isMuted == true) return true
        }
        return false
    }

    private suspend fun getOrCreateUserSafety(
        counterpartyUid: String = "",
        partyId: Long? = null,
        partyName: String = ""
    ): UserChatSafetyEntity {
        val resolvedUid = counterpartyUid.trim().ifBlank { resolveCounterpartyUid(null, partyId, partyName) }
        val existingByUid = resolvedUid.takeIf { it.isNotBlank() }?.let { dao.getUserChatSafetyByUid(it) }
        val key = existingByUid?.userKey ?: userSafetyKey(resolvedUid, partyId, partyName)
        val existing = existingByUid ?: dao.getUserChatSafety(key)
        if (existing != null) {
            return existing.copy(
                counterpartyUid = existing.counterpartyUid.ifBlank { resolvedUid },
                partyId = existing.partyId ?: partyId,
                partyName = existing.partyName.ifBlank { partyName.trim() }
            )
        }
        return UserChatSafetyEntity(
            userKey = key,
            counterpartyUid = resolvedUid,
            partyId = partyId,
            partyName = partyName.trim()
        )
    }

    suspend fun toggleUserMute(
        counterpartyUid: String = "",
        partyId: Long? = null,
        partyName: String = "",
        isMuted: Boolean? = null
    ): UserChatSafetyEntity {
        val existing = getOrCreateUserSafety(counterpartyUid, partyId, partyName)
        val updated = existing.copy(
            isMuted = isMuted ?: !existing.isMuted,
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.upsertUserChatSafety(updated)
        ChatSyncHelper.syncUserSafetyToCloud(updated, settingsRepository, cloudRepository)
        return updated
    }

    suspend fun toggleUserBlock(
        counterpartyUid: String = "",
        partyId: Long? = null,
        partyName: String = "",
        isBlocked: Boolean? = null
    ): UserChatSafetyEntity {
        val existing = getOrCreateUserSafety(counterpartyUid, partyId, partyName)
        val nextBlocked = isBlocked ?: !existing.isBlocked
        val updated = existing.copy(
            isBlocked = nextBlocked,
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.upsertUserChatSafety(updated)
        ChatSyncHelper.syncUserSafetyToCloud(updated, settingsRepository, cloudRepository)
        return updated
    }

    suspend fun reportUser(
        counterpartyUid: String = "",
        partyId: Long? = null,
        partyName: String = "",
        reason: String,
        alsoBlock: Boolean = true
    ): UserChatSafetyEntity {
        val existing = getOrCreateUserSafety(counterpartyUid, partyId, partyName)
        val updated = existing.copy(
            isReported = true,
            isBlocked = if (alsoBlock) true else existing.isBlocked,
            reportReason = reason.trim().ifBlank { "Abusive or spam behavior" },
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.upsertUserChatSafety(updated)
        ChatSyncHelper.syncUserSafetyToCloud(updated, settingsRepository, cloudRepository)
        return updated
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
        val settings = settingsRepository.userSettings.first()
        val meta = getOrCreateThreadMetadata(threadKey, scope, transactionId, remoteId, partyId, partyName)
        val nextBlocked = isBlocked ?: !meta.isBlocked
        val updated = meta.copy(
            isBlocked = nextBlocked,
            blockedByUid = if (nextBlocked) settings.cloudUserId.ifBlank { "local_me" } else "",
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.upsertChatThreadMetadata(updated)
        syncModerationFlagsToCloud(updated)
        return updated
    }

    suspend fun reportThread(
        threadKey: String,
        reason: String,
        scope: ChatThreadScope = ChatThreadScope.TRANSACTION,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String = "",
        alsoBlockThread: Boolean = true
    ): ChatThreadMetadata {
        val settings = settingsRepository.userSettings.first()
        val meta = getOrCreateThreadMetadata(threadKey, scope, transactionId, remoteId, partyId, partyName)
        val nextBlocked = if (alsoBlockThread) true else meta.isBlocked
        val updated = meta.copy(
            isReported = true,
            isBlocked = nextBlocked,
            blockedByUid = if (nextBlocked) settings.cloudUserId.ifBlank { "local_me" } else meta.blockedByUid,
            reportReason = reason.trim().ifBlank { "Abusive or spam behavior" },
            updatedAtMillis = System.currentTimeMillis()
        )
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
        val updatedThread = reportThread(
            threadKey = threadKey,
            reason = reason,
            scope = scope,
            transactionId = transactionId,
            remoteId = remoteId,
            partyId = partyId,
            partyName = partyName,
            alsoBlockThread = true
        )
        reportUser(
            counterpartyUid = "",
            partyId = partyId,
            partyName = partyName,
            reason = reason,
            alsoBlock = true
        )
        return updatedThread
    }

    private suspend fun syncModerationFlagsToCloud(meta: ChatThreadMetadata) {
        val counterpartyUid = resolveCounterpartyUid(null, meta.partyId, meta.partyName)
        val settings = settingsRepository.userSettings.first()
        val effectiveMeta = if ((meta.threadScope == ChatThreadScope.PARTY || meta.threadScope == ChatThreadScope.PARTY_GENERAL) && counterpartyUid.isNotBlank()) {
            val dmId = cloudThreadIdFor(meta.threadScope, meta.remoteThreadId, meta.partyName, meta.threadKey, settings.cloudUserId, counterpartyUid)
            meta.copy(remoteThreadId = dmId)
        } else meta
        ChatSyncHelper.syncModerationFlagsToCloud(effectiveMeta, settingsRepository, cloudRepository, counterpartyUid)
    }

    private suspend fun syncChatThreadAndMessageToCloudIfEnabled(
        meta: ChatThreadMetadata,
        message: ChatMessageEntity,
        settings: UserSettings,
        counterpartyUserId: String
    ): Boolean {
        return ChatSyncHelper.syncChatThreadAndMessageToCloudIfEnabled(
            meta = meta,
            message = message,
            settings = settings,
            counterpartyUserId = counterpartyUserId,
            cloudRepository = cloudRepository
        )
    }

    /**
     * Observes real-time Firestore chat thread & messages for the active thread and syncs incoming
     * messages, read/delivery receipts, and counterparty UID links into local Room storage.
     */
    fun observeAndSyncCloudThread(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long?,
        remoteId: String,
        partyId: Long?,
        partyName: String
    ): Flow<Unit> = flow {
        val cloud = cloudRepository
        if (cloud == null || !cloud.isAvailable) return@flow

        val settings = settingsRepository.userSettings.first()
        val myUid = settings.cloudUserId.trim()
        if (!settings.cloudVerificationConsent || myUid.isBlank()) return@flow

        val tx = (transactionId?.let { dao.getTransactionById(it) })
            ?: remoteId.takeIf { it.isNotBlank() }?.let { dao.getTransactionByRemoteId(it) }
        val resolvedRemoteId = tx?.remoteId?.ifBlank { remoteId } ?: remoteId
        val safeRemoteId = resolvedRemoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")

        var counterpartyUid = resolveCounterpartyUid(tx, partyId, partyName)
        if (safeRemoteId.isNotBlank() && counterpartyUid.isBlank()) {
            val shared = runCatching { cloud.getSharedRecord(safeRemoteId) }.getOrNull()
            if (shared != null) {
                val otherUid = when {
                    shared.counterpartyUid.isNotBlank() && shared.counterpartyUid != myUid -> shared.counterpartyUid
                    shared.creatorUid.isNotBlank() && shared.creatorUid != myUid -> shared.creatorUid
                    else -> ""
                }
                if (otherUid.isNotBlank()) {
                    counterpartyUid = otherUid
                    linkCounterpartyUidLocally(tx?.id ?: transactionId, safeRemoteId, partyId, partyName, otherUid)
                }
            }
        }

        val cloudThreadId = cloudThreadIdFor(
            scope = scope,
            remoteId = safeRemoteId,
            partyName = partyName,
            fallbackThreadKey = threadKey,
            myUid = myUid,
            counterpartyUid = counterpartyUid
        )
        if (!cloudThreadId.startsWith("tx_") && !cloudThreadId.startsWith("dm_")) {
            return@flow
        }

        coroutineScope {
            if (safeRemoteId.isNotBlank()) {
                launch {
                    cloud.observeSharedRecord(safeRemoteId)
                        .catch { e -> SafeLogger.w("ChatRepository", "observeSharedRecord error", e) }
                        .collect { shared ->
                            if (shared != null) {
                                val otherUid = when {
                                    shared.counterpartyUid.isNotBlank() && shared.counterpartyUid != myUid -> shared.counterpartyUid
                                    shared.creatorUid.isNotBlank() && shared.creatorUid != myUid -> shared.creatorUid
                                    else -> ""
                                }
                                if (otherUid.isNotBlank()) {
                                    linkCounterpartyUidLocally(tx?.id ?: transactionId, safeRemoteId, partyId, partyName, otherUid)
                                }
                            }
                        }
                }
            }

            launch {
                cloud.observeChatThread(cloudThreadId)
                    .catch { e -> SafeLogger.w("ChatRepository", "observeChatThread error", e) }
                    .collect { cloudThread ->
                        if (cloudThread != null) {
                            val otherUid = when {
                                cloudThread.counterpartyUid.isNotBlank() && cloudThread.counterpartyUid != myUid -> cloudThread.counterpartyUid
                                cloudThread.creatorUid.isNotBlank() && cloudThread.creatorUid != myUid -> cloudThread.creatorUid
                                else -> cloudThread.participantUids.firstOrNull { it.isNotBlank() && it != myUid }.orEmpty()
                            }
                            if (otherUid.isNotBlank()) {
                                linkCounterpartyUidLocally(tx?.id ?: transactionId, safeRemoteId, partyId, partyName, otherUid)
                            }
                            val existingMeta = getOrCreateThreadMetadata(
                                threadKey = threadKey,
                                scope = scope,
                                transactionId = tx?.id ?: transactionId,
                                remoteId = safeRemoteId.ifBlank { cloudThreadId },
                                partyId = partyId,
                                partyName = partyName
                            )
                            val typingActive = cloudThread.typingUserId.isNotBlank() &&
                                cloudThread.typingUserId != myUid &&
                                (System.currentTimeMillis() - cloudThread.typingUpdatedAtMillis) < 15_000L
                            val remoteBlocked = cloudThread.blockedByUids.isNotEmpty()
                            dao.upsertChatThreadMetadata(
                                existingMeta.copy(
                                    remoteThreadId = existingMeta.remoteThreadId.ifBlank { safeRemoteId.ifBlank { cloudThreadId } },
                                    counterpartyTyping = typingActive,
                                    typingUpdatedAtMillis = cloudThread.typingUpdatedAtMillis.coerceAtLeast(existingMeta.typingUpdatedAtMillis),
                                    isBlocked = existingMeta.isBlocked || remoteBlocked,
                                    updatedAtMillis = System.currentTimeMillis()
                                )
                            )
                        }
                    }
            }

            launch {
                cloud.observeChatMessages(cloudThreadId, myUid)
                    .catch { e -> SafeLogger.w("ChatRepository", "observeChatMessages error", e) }
                    .collect { cloudMsgs ->
                        val nowMillis = System.currentTimeMillis()
                        for (cMsg in cloudMsgs) {
                            val isFromMe = cMsg.senderUid == myUid
                            val parsedType = runCatching { ChatMessageType.valueOf(cMsg.messageType) }
                                .getOrDefault(ChatMessageType.TEXT)
                            val parsedExtStatus = runCatching { ExtensionRequestStatus.valueOf(cMsg.extensionStatus) }
                                .getOrDefault(ExtensionRequestStatus.NONE)
                            val parsedDelivery = runCatching { MessageDeliveryStatus.valueOf(cMsg.deliveryStatus) }
                                .getOrDefault(MessageDeliveryStatus.DELIVERED)

                            if (!isFromMe && cMsg.senderUid.isNotBlank()) {
                                linkCounterpartyUidLocally(tx?.id ?: transactionId, safeRemoteId, partyId, partyName, cMsg.senderUid)
                            }

                            val existingLocal = dao.getChatMessageByRemoteId(cMsg.messageId)
                            if (existingLocal == null) {
                                val resolvedDeliveredAt = cMsg.deliveredAtMillis.takeIf { it > 0L } ?: nowMillis
                                val resolvedReadAt = if (!isFromMe) {
                                    nowMillis
                                } else {
                                    cMsg.readAtMillis.takeIf { it > 0L }
                                }
                                val entity = ChatMessageEntity(
                                    remoteMessageId = cMsg.messageId,
                                    threadKey = threadKey,
                                    threadScope = scope,
                                    transactionId = tx?.id ?: transactionId,
                                    partyId = partyId ?: tx?.partyId,
                                    partyName = partyName.ifBlank { cMsg.senderName },
                                    senderUserId = cMsg.senderUid,
                                    senderName = cMsg.senderName.ifBlank { partyName.ifBlank { "Party" } },
                                    isFromMe = isFromMe,
                                    messageType = parsedType,
                                    bodyText = cMsg.textContent,
                                    attachmentUri = cMsg.attachmentRef,
                                    attachmentMimeType = cMsg.attachmentMimeType,
                                    attachmentName = "",
                                    proposedDueDateMillis = cMsg.proposedDueDateMillis.takeIf { it > 0L },
                                    extensionStatus = parsedExtStatus,
                                    deliveryStatus = if (!isFromMe) MessageDeliveryStatus.READ else parsedDelivery,
                                    isUnreadForMe = false,
                                    sentAtMillis = cMsg.sentAtMillis.takeIf { it > 0L } ?: nowMillis,
                                    deliveredAtMillis = resolvedDeliveredAt,
                                    readAtMillis = resolvedReadAt
                                )
                                dao.insertChatMessage(entity)

                                if (!isFromMe) {
                                    runCatching {
                                        cloud.updateChatMessageDeliveryOrExtension(
                                            threadId = cloudThreadId,
                                            messageId = cMsg.messageId,
                                            deliveryStatus = MessageDeliveryStatus.READ.name,
                                            deliveredAtMillis = resolvedDeliveredAt,
                                            readAtMillis = nowMillis,
                                            extensionStatus = parsedExtStatus.name
                                        )
                                    }
                                }
                            } else {
                                val needsDeliveryUpdate = parsedDelivery.ordinal > existingLocal.deliveryStatus.ordinal
                                val needsExtensionUpdate = parsedExtStatus != existingLocal.extensionStatus
                                val shouldMarkReadRemotely = !isFromMe && existingLocal.deliveryStatus != MessageDeliveryStatus.READ
                                if (needsDeliveryUpdate || needsExtensionUpdate || shouldMarkReadRemotely) {
                                    val nextDelivery = if (!isFromMe) {
                                        MessageDeliveryStatus.READ
                                    } else if (needsDeliveryUpdate) {
                                        parsedDelivery
                                    } else {
                                        existingLocal.deliveryStatus
                                    }
                                    val nextDeliveredAt = cMsg.deliveredAtMillis.takeIf { it > 0L }
                                        ?: existingLocal.deliveredAtMillis ?: nowMillis
                                    val nextReadAt = if (!isFromMe) {
                                        nowMillis
                                    } else {
                                        cMsg.readAtMillis.takeIf { it > 0L } ?: existingLocal.readAtMillis
                                    }
                                    dao.updateChatMessage(
                                        existingLocal.copy(
                                            deliveryStatus = nextDelivery,
                                            extensionStatus = if (needsExtensionUpdate) parsedExtStatus else existingLocal.extensionStatus,
                                            deliveredAtMillis = nextDeliveredAt,
                                            readAtMillis = nextReadAt,
                                            isUnreadForMe = false
                                        )
                                    )
                                    if (shouldMarkReadRemotely) {
                                        runCatching {
                                            cloud.updateChatMessageDeliveryOrExtension(
                                                threadId = cloudThreadId,
                                                messageId = cMsg.messageId,
                                                deliveryStatus = MessageDeliveryStatus.READ.name,
                                                deliveredAtMillis = nextDeliveredAt,
                                                readAtMillis = nowMillis,
                                                extensionStatus = parsedExtStatus.name
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }
        emit(Unit)
    }
}
