package com.jyotirmoyb.udhaar.sync

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatMessageType
import com.jyotirmoyb.udhaar.data.ChatThreadMetadata
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.ExtensionRequestStatus
import com.jyotirmoyb.udhaar.data.GuestActionV1
import com.jyotirmoyb.udhaar.data.MessageDeliveryStatus
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.cloud.CloudChatMessage
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.di.FirebaseModule
import com.jyotirmoyb.udhaar.notification.ChatNotificationHelper
import com.jyotirmoyb.udhaar.util.ChatSyncHelper
import com.jyotirmoyb.udhaar.util.SafeLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Attaches real-time Firestore snapshot listeners while the app is in the foreground.
 * Detached completely in onStop.
 * Strictly queries only the lender's ACTIVE links, shared records, and chat threads (limit 50).
 */
object ForegroundGuestActionListener {
    private val activeRegistrations = mutableListOf<ListenerRegistration>()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val processedRecently = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val DEBOUNCE_WINDOW_MS = 1500L
    private const val CHAT_DEBOUNCE_WINDOW_MS = 5000L

    fun start(context: Context) {
        stop()

        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull() ?: return
        if (!user.isEmailVerified && !user.isAnonymous) return
        val ownerUid = user.uid
        if (ownerUid.isBlank()) return

        val firestore = FirebaseModule.getFirestore(context) ?: return

        runCatching {
            register(attachGuestLinksListener(context, firestore, ownerUid))
            register(attachSharedRecordsListener(context, firestore, ownerUid))
            register(attachChatThreadsListener(context, firestore, ownerUid))
        }
    }

    private fun register(reg: ListenerRegistration) {
        synchronized(activeRegistrations) {
            activeRegistrations.add(reg)
        }
    }

    private fun attachGuestLinksListener(
        context: Context,
        firestore: FirebaseFirestore,
        ownerUid: String
    ): ListenerRegistration {
        return firestore.collection("guestLinks")
            .whereEqualTo("ownerUid", ownerUid)
            .whereEqualTo("status", "ACTIVE")
            .limit(50)
            .addSnapshotListener { linksSnapshot, err ->
                if (err != null || linksSnapshot == null) {
                    logFirestoreListenerError("links listener", err)
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                processedRecently.entries.removeIf { now - it.value > 60_000L }

                for (linkDoc in linksSnapshot.documents) {
                    if (linkDoc.getString("status") != "ACTIVE") continue
                    register(attachActionsListener(context, ownerUid, linkDoc))
                }
            }
    }

    private fun attachActionsListener(
        context: Context,
        ownerUid: String,
        linkDoc: DocumentSnapshot
    ): ListenerRegistration {
        return linkDoc.reference.collection("actions")
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { actionsSnapshot, actErr ->
                if (actErr != null || actionsSnapshot == null) {
                    logFirestoreListenerError("Actions listener", actErr)
                    return@addSnapshotListener
                }
                for (doc in actionsSnapshot.documents) {
                    handlePendingActionDoc(context, ownerUid, doc)
                }
            }
    }

    private fun handlePendingActionDoc(
        context: Context,
        ownerUid: String,
        doc: DocumentSnapshot
    ) {
        val action = GuestActionV1.fromDocument(doc)
        if (action == null) {
            scope.launch {
                runCatching {
                    doc.reference.update(
                        mapOf(
                            "status" to "REJECTED",
                            "rejectReason" to "invalid_payload",
                            "reviewedAtMs" to System.currentTimeMillis()
                        )
                    ).await()
                }
            }
            return
        }

        val currentTime = System.currentTimeMillis()
        val lastProcessed = processedRecently[action.compositeKey] ?: 0L
        if (currentTime - lastProcessed < DEBOUNCE_WINDOW_MS) return
        processedRecently[action.compositeKey] = currentTime

        scope.launch {
            GuestActionPollingWorker.processSingleAction(
                context = context,
                action = action,
                ownerUid = ownerUid
            )
        }
    }

    private fun attachSharedRecordsListener(
        context: Context,
        firestore: FirebaseFirestore,
        ownerUid: String
    ): ListenerRegistration {
        return firestore.collection("shared_records")
            .whereArrayContains("participantUids", ownerUid)
            .limit(50)
            .addSnapshotListener { sharedSnapshot, sharedErr ->
                if (sharedErr != null || sharedSnapshot == null) {
                    logFirestoreListenerError("shared_records listener", sharedErr)
                    return@addSnapshotListener
                }
                for (sharedDoc in sharedSnapshot.documents) {
                    scope.launch {
                        runCatching {
                            GuestActionPollingWorker.syncSharedRecordSnapshotToLocal(
                                context = context,
                                ownerUid = ownerUid,
                                doc = sharedDoc
                            )
                        }
                    }
                }
            }
    }

    private fun attachChatThreadsListener(
        context: Context,
        firestore: FirebaseFirestore,
        ownerUid: String
    ): ListenerRegistration {
        return firestore.collection("chat_threads")
            .whereArrayContains("participantUids", ownerUid)
            .limit(50)
            .addSnapshotListener { threadsSnapshot, threadsErr ->
                if (threadsErr != null || threadsSnapshot == null) {
                    logFirestoreListenerError("chat_threads listener", threadsErr)
                    return@addSnapshotListener
                }
                for (threadDoc in threadsSnapshot.documents) {
                    dispatchCloudThreadSync(context, firestore, ownerUid, threadDoc)
                }
            }
    }

    private fun dispatchCloudThreadSync(
        context: Context,
        firestore: FirebaseFirestore,
        ownerUid: String,
        threadDoc: DocumentSnapshot
    ) {
        val lastMsgAt = threadDoc.getLong("lastMessageAtMillis") ?: 0L
        if (lastMsgAt <= 0L) return

        val cloudThreadId = threadDoc.id
        val debounceKey = "chat_${cloudThreadId}_$lastMsgAt"
        val nowMs = System.currentTimeMillis()
        if (nowMs - (processedRecently[debounceKey] ?: 0L) < CHAT_DEBOUNCE_WINDOW_MS) return
        processedRecently[debounceKey] = nowMs

        val creatorUid = threadDoc.getString("creatorUid").orEmpty().trim()
        val counterpartyUid = threadDoc.getString("counterpartyUid").orEmpty().trim()
        val otherUid = when {
            counterpartyUid.isNotBlank() && counterpartyUid != ownerUid -> counterpartyUid
            creatorUid.isNotBlank() && creatorUid != ownerUid -> creatorUid
            else -> ""
        }
        scope.launch {
            syncIncomingMessagesForCloudThread(
                context = context,
                firestore = firestore,
                ownerUid = ownerUid,
                cloudThreadId = cloudThreadId,
                remoteRecordId = threadDoc.getString("remoteRecordId").orEmpty().trim(),
                threadScopeStr = threadDoc.getString("threadScope").orEmpty().trim(),
                otherUid = otherUid
            )
        }
    }

    private fun logFirestoreListenerError(label: String, err: Exception?) {
        if (err == null) return
        if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
            SafeLogger.w("ForegroundGuestAction", "$label quota reached")
        } else {
            SafeLogger.w("ForegroundGuestAction", "$label error: ${err.message}")
        }
    }

    private data class ResolvedThreadTarget(
        val threadKey: String,
        val scopeEnum: ChatThreadScope,
        val transactionId: Long?,
        val partyId: Long?,
        val partyName: String
    )

    private suspend fun resolveCloudThreadTarget(
        dao: UdhaarDao,
        remoteRecordId: String,
        threadScopeStr: String,
        otherUid: String
    ): ResolvedThreadTarget? {
        val matchedTx = if (remoteRecordId.isNotBlank()) {
            dao.getTransactionByRemoteId(remoteRecordId)
                ?: dao.getAllTransactionsSnapshot().firstOrNull {
                    it.remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_") == remoteRecordId
                }
        } else {
            null
        }
        val scopeEnum = if (threadScopeStr == "PARTY" || matchedTx == null) {
            ChatThreadScope.PARTY_GENERAL
        } else {
            ChatThreadScope.TRANSACTION
        }
        val resolvedParty = when {
            matchedTx?.partyId != null && matchedTx.partyId > 0L -> dao.getPartyById(matchedTx.partyId)
            otherUid.isNotBlank() -> dao.getAllPartiesSnapshot().firstOrNull { it.linkedUserId == otherUid }
            else -> null
        }
        linkRemoteUidLocally(dao, matchedTx, resolvedParty, otherUid)
        val partyName = matchedTx?.partyName?.ifBlank { resolvedParty?.name.orEmpty() }
            ?: resolvedParty?.name.orEmpty()
        val threadKey = when {
            scopeEnum == ChatThreadScope.TRANSACTION && matchedTx != null -> ChatSyncHelper.transactionThreadKey(matchedTx.id)
            partyName.isNotBlank() -> ChatSyncHelper.partyGeneralThreadKey(partyName)
            else -> return null
        }
        return ResolvedThreadTarget(
            threadKey = threadKey,
            scopeEnum = scopeEnum,
            transactionId = matchedTx?.id,
            partyId = matchedTx?.partyId ?: resolvedParty?.partyId,
            partyName = partyName
        )
    }

    private suspend fun linkRemoteUidLocally(
        dao: UdhaarDao,
        matchedTx: com.jyotirmoyb.udhaar.data.UdhaarTransaction?,
        resolvedParty: com.jyotirmoyb.udhaar.data.PartyEntity?,
        otherUid: String
    ) {
        if (otherUid.isBlank()) return
        if (matchedTx != null && matchedTx.counterpartyUserId.isBlank()) {
            dao.updateTransaction(matchedTx.copy(counterpartyUserId = otherUid))
        }
        if (resolvedParty != null && resolvedParty.linkedUserId.isBlank()) {
            dao.updateParty(resolvedParty.copy(linkedUserId = otherUid))
        }
    }

    private suspend fun syncIncomingMessagesForCloudThread(
        context: Context,
        firestore: FirebaseFirestore,
        ownerUid: String,
        cloudThreadId: String,
        remoteRecordId: String,
        threadScopeStr: String,
        otherUid: String
    ) {
        val dao = runCatching { UdhaarDatabase.getInstance(context).udhaarDao() }.getOrNull() ?: return
        val target = resolveCloudThreadTarget(dao, remoteRecordId, threadScopeStr, otherUid) ?: return
        val cloudRepo = VerificationCloudRepository(firestore)
        val cloudMsgs = runCatching { cloudRepo.listChatMessages(cloudThreadId, ownerUid) }.getOrDefault(emptyList())
        if (cloudMsgs.isEmpty()) return

        val fallbackName = cloudMsgs.firstOrNull { it.senderUid != ownerUid }?.senderName.orEmpty().ifBlank { "Party" }
        val existingMeta = dao.getChatThreadMetadata(target.threadKey)
        val meta = existingMeta?.copy(
            remoteThreadId = existingMeta.remoteThreadId.ifBlank { remoteRecordId.ifBlank { cloudThreadId } },
            updatedAtMillis = System.currentTimeMillis()
        ) ?: ChatThreadMetadata(
            threadKey = target.threadKey,
            remoteThreadId = remoteRecordId.ifBlank { cloudThreadId },
            threadScope = target.scopeEnum,
            transactionId = target.transactionId,
            partyId = target.partyId,
            partyName = target.partyName.ifBlank { fallbackName }
        )
        dao.upsertChatThreadMetadata(meta)

        val nowMs = System.currentTimeMillis()
        for (cMsg in cloudMsgs) {
            upsertSyncedCloudMessage(
                context = context,
                dao = dao,
                cloudRepo = cloudRepo,
                ownerUid = ownerUid,
                cloudThreadId = cloudThreadId,
                target = target,
                meta = meta,
                cMsg = cMsg,
                nowMs = nowMs
            )
        }
    }

    private suspend fun upsertSyncedCloudMessage(
        context: Context,
        dao: UdhaarDao,
        cloudRepo: VerificationCloudRepository,
        ownerUid: String,
        cloudThreadId: String,
        target: ResolvedThreadTarget,
        meta: ChatThreadMetadata,
        cMsg: CloudChatMessage,
        nowMs: Long
    ) {
        val isFromMe = cMsg.senderUid == ownerUid
        val existingLocal = dao.getChatMessageByRemoteId(cMsg.messageId)
        val parsedType = runCatching { ChatMessageType.valueOf(cMsg.messageType) }.getOrDefault(ChatMessageType.TEXT)
        val parsedExt = runCatching { ExtensionRequestStatus.valueOf(cMsg.extensionStatus) }.getOrDefault(ExtensionRequestStatus.NONE)
        val parsedDelivery = runCatching { MessageDeliveryStatus.valueOf(cMsg.deliveryStatus) }.getOrDefault(MessageDeliveryStatus.DELIVERED)

        if (existingLocal != null) {
            if (isFromMe && parsedDelivery.ordinal > existingLocal.deliveryStatus.ordinal) {
                dao.updateChatMessage(
                    existingLocal.copy(
                        deliveryStatus = parsedDelivery,
                        deliveredAtMillis = cMsg.deliveredAtMillis.takeIf { it > 0L } ?: existingLocal.deliveredAtMillis,
                        readAtMillis = cMsg.readAtMillis.takeIf { it > 0L } ?: existingLocal.readAtMillis,
                        extensionStatus = parsedExt
                    )
                )
            }
            return
        }

        val entity = ChatMessageEntity(
            remoteMessageId = cMsg.messageId,
            threadKey = target.threadKey,
            threadScope = target.scopeEnum,
            transactionId = target.transactionId,
            partyId = target.partyId,
            partyName = meta.partyName,
            senderUserId = cMsg.senderUid,
            senderName = cMsg.senderName.ifBlank { meta.partyName },
            isFromMe = isFromMe,
            messageType = parsedType,
            bodyText = cMsg.textContent,
            attachmentUri = cMsg.attachmentRef,
            attachmentMimeType = cMsg.attachmentMimeType,
            attachmentName = "",
            proposedDueDateMillis = cMsg.proposedDueDateMillis.takeIf { it > 0L },
            extensionStatus = parsedExt,
            deliveryStatus = if (!isFromMe) MessageDeliveryStatus.DELIVERED else parsedDelivery,
            isUnreadForMe = !isFromMe,
            sentAtMillis = cMsg.sentAtMillis.takeIf { it > 0L } ?: nowMs,
            deliveredAtMillis = cMsg.deliveredAtMillis.takeIf { it > 0L } ?: nowMs,
            readAtMillis = cMsg.readAtMillis.takeIf { it > 0L }
        )
        val insertedId = dao.insertChatMessage(entity)
        if (!isFromMe) {
            runCatching {
                cloudRepo.updateChatMessageDeliveryOrExtension(
                    threadId = cloudThreadId,
                    messageId = cMsg.messageId,
                    deliveryStatus = MessageDeliveryStatus.DELIVERED.name,
                    deliveredAtMillis = nowMs,
                    readAtMillis = cMsg.readAtMillis.coerceAtLeast(-1L),
                    extensionStatus = parsedExt.name
                )
            }
            if (!meta.isMuted) {
                ChatNotificationHelper.showIncomingChatNotification(
                    context = context,
                    threadMetadata = meta,
                    message = entity.copy(id = insertedId)
                )
            }
        }
    }

    fun stop() {
        synchronized(activeRegistrations) {
            for (reg in activeRegistrations) {
                runCatching { reg.remove() }
            }
            activeRegistrations.clear()
        }
    }
}
