package com.jyotirmoyb.udhaar.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatMessageType
import com.jyotirmoyb.udhaar.data.ChatThreadMetadata
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.repository.ChatRepository
import com.jyotirmoyb.udhaar.data.repository.LedgerRepository
import com.jyotirmoyb.udhaar.notification.ReminderScheduler
import com.jyotirmoyb.udhaar.ui.state.AppStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val chatThreadsByKey: Map<String, ChatThreadMetadata> = emptyMap(),
    val chatMessagesByThreadKey: Map<String, List<ChatMessageEntity>> = emptyMap(),
    val unreadCountByTx: Map<Long, Int> = emptyMap(),
    val unreadCountByPartyName: Map<String, Int> = emptyMap(),
    val activeChatThreadKey: String? = null,
    val activeChatScope: ChatThreadScope = ChatThreadScope.TRANSACTION,
    val activeChatTransactionId: Long? = null,
    val activeChatPartyId: Long? = null,
    val activeChatPartyName: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val ledgerRepository: LedgerRepository,
    private val appStateHolder: AppStateHolder,
    @ApplicationContext private val appContext: Context? = null
) : ViewModel() {

    private val _activeChatThreadKey = MutableStateFlow<String?>(null)
    val activeChatThreadKey: StateFlow<String?> = _activeChatThreadKey.asStateFlow()

    private val _activeChatScope = MutableStateFlow(ChatThreadScope.TRANSACTION)
    val activeChatScope: StateFlow<ChatThreadScope> = _activeChatScope.asStateFlow()

    private val _activeChatTransactionId = MutableStateFlow<Long?>(null)
    val activeChatTransactionId: StateFlow<Long?> = _activeChatTransactionId.asStateFlow()

    private val _activeChatPartyId = MutableStateFlow<Long?>(null)
    val activeChatPartyId: StateFlow<Long?> = _activeChatPartyId.asStateFlow()

    private val _activeChatPartyName = MutableStateFlow("")
    val activeChatPartyName: StateFlow<String> = _activeChatPartyName.asStateFlow()

    val uiState: StateFlow<ChatUiState> = combine(
        chatRepository.allChatThreads,
        chatRepository.allChatMessages,
        combine(
            _activeChatThreadKey,
            _activeChatScope,
            _activeChatTransactionId,
            _activeChatPartyId,
            _activeChatPartyName
        ) { threadKey, scope, txId, partyId, partyName ->
            ActiveChatParams(threadKey, scope, txId, partyId, partyName)
        }
    ) { threads, messages, activeParams ->
        val threadsByKey = threads.associateBy { it.threadKey }
        val messagesByThreadKey = messages.groupBy { it.threadKey }

        val unreadByTx = mutableMapOf<Long, Int>()
        val unreadByPartyName = mutableMapOf<String, Int>()
        for (msg in messages) {
            if (msg.isUnreadForMe) {
                val txId = msg.transactionId
                if (txId != null && txId > 0L) {
                    unreadByTx[txId] = (unreadByTx[txId] ?: 0) + 1
                }
                val partyKey = msg.partyName.trim().lowercase()
                if (partyKey.isNotEmpty()) {
                    unreadByPartyName[partyKey] = (unreadByPartyName[partyKey] ?: 0) + 1
                }
            }
        }

        ChatUiState(
            chatThreadsByKey = threadsByKey,
            chatMessagesByThreadKey = messagesByThreadKey,
            unreadCountByTx = unreadByTx,
            unreadCountByPartyName = unreadByPartyName,
            activeChatThreadKey = activeParams.threadKey,
            activeChatScope = activeParams.scope,
            activeChatTransactionId = activeParams.txId,
            activeChatPartyId = activeParams.partyId,
            activeChatPartyName = activeParams.partyName
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState()
    )

    private data class ActiveChatParams(
        val threadKey: String?,
        val scope: ChatThreadScope,
        val txId: Long?,
        val partyId: Long?,
        val partyName: String
    )

    fun openTransactionChat(transactionId: Long, partyName: String = "", partyId: Long? = null, remoteId: String = "") {
        val threadKey = UdhaarRepository.transactionThreadKey(transactionId)
        viewModelScope.launch {
            chatRepository.getOrCreateThreadMetadata(
                threadKey = threadKey,
                scope = ChatThreadScope.TRANSACTION,
                transactionId = transactionId,
                remoteId = remoteId,
                partyId = partyId,
                partyName = partyName
            )
            chatRepository.markThreadRead(threadKey)
        }
        _activeChatThreadKey.value = threadKey
        _activeChatScope.value = ChatThreadScope.TRANSACTION
        _activeChatTransactionId.value = transactionId
        _activeChatPartyId.value = partyId
        _activeChatPartyName.value = partyName
    }

    fun openPartyGeneralChat(party: PartyEntity) {
        val threadKey = UdhaarRepository.partyGeneralThreadKey(party.name)
        viewModelScope.launch {
            chatRepository.getOrCreateThreadMetadata(
                threadKey = threadKey,
                scope = ChatThreadScope.PARTY_GENERAL,
                transactionId = null,
                remoteId = "",
                partyId = party.partyId.takeIf { it > 0L },
                partyName = party.name
            )
            chatRepository.markThreadRead(threadKey)
        }
        _activeChatThreadKey.value = threadKey
        _activeChatScope.value = ChatThreadScope.PARTY_GENERAL
        _activeChatTransactionId.value = null
        _activeChatPartyId.value = party.partyId
        _activeChatPartyName.value = party.name
    }

    fun openChatFromNotification(
        threadKey: String,
        transactionId: Long?,
        partyName: String,
        remoteRecordId: String = ""
    ) {
        val resolvedTxId = transactionId?.takeIf { it > 0L }
        val resolvedPartyName = partyName

        if (threadKey.isBlank() && resolvedTxId != null) {
            appStateHolder.openTransactionDetail(resolvedTxId)
            return
        }

        val effectiveThreadKey = threadKey.ifBlank {
            if (resolvedTxId != null && resolvedTxId > 0L) "tx_$resolvedTxId" else "party_${resolvedPartyName.lowercase()}"
        }
        val isTxThread = resolvedTxId != null && resolvedTxId > 0L
        val scope = if (isTxThread) ChatThreadScope.TRANSACTION else ChatThreadScope.PARTY_GENERAL

        viewModelScope.launch {
            chatRepository.markThreadRead(effectiveThreadKey)
        }
        _activeChatThreadKey.value = effectiveThreadKey
        _activeChatScope.value = scope
        _activeChatTransactionId.value = resolvedTxId
        _activeChatPartyName.value = resolvedPartyName
    }

    fun closeChatSheet() {
        val currentKey = _activeChatThreadKey.value
        if (!currentKey.isNullOrBlank()) {
            viewModelScope.launch {
                chatRepository.markThreadRead(currentKey)
            }
        }
        _activeChatThreadKey.value = null
        _activeChatTransactionId.value = null
        _activeChatPartyId.value = null
        _activeChatPartyName.value = ""
    }

    fun sendChatMessage(
        threadKey: String,
        scope: ChatThreadScope,
        transaction: UdhaarTransaction? = null,
        partyId: Long? = null,
        partyName: String,
        messageType: ChatMessageType = ChatMessageType.TEXT,
        textContent: String,
        attachmentUri: String = "",
        attachmentMimeType: String = "",
        fromCounterparty: Boolean = false
    ) {
        viewModelScope.launch {
            chatRepository.sendChatMessage(
                threadKey = threadKey,
                scope = scope,
                transaction = transaction,
                partyId = partyId,
                partyName = partyName,
                messageType = messageType,
                textContent = textContent,
                attachmentUri = attachmentUri,
                attachmentMimeType = attachmentMimeType,
                fromCounterparty = fromCounterparty
            )
            if (!fromCounterparty && _activeChatThreadKey.value == threadKey) {
                chatRepository.markThreadRead(threadKey)
            }
        }
    }

    fun requestDueDateExtension(
        transaction: UdhaarTransaction,
        proposedDueDateMillis: Long,
        note: String = "",
        requestedByCounterparty: Boolean = false
    ) {
        viewModelScope.launch {
            chatRepository.requestDueDateExtension(
                transaction = transaction,
                proposedDueDateMillis = proposedDueDateMillis,
                note = note,
                requestedByCounterparty = requestedByCounterparty
            )
        }
    }

    fun respondToExtensionRequest(
        transaction: UdhaarTransaction,
        message: ChatMessageEntity,
        accept: Boolean
    ) {
        viewModelScope.launch {
            val updatedTx = chatRepository.respondToExtensionRequest(
                transaction = transaction,
                message = message,
                accept = accept
            )
            if (accept && !updatedTx.isSettled && updatedTx.reminderDateMillis != null) {
                appContext?.let { ctx ->
                    ReminderScheduler.scheduleReminder(ctx, updatedTx)
                }
            }
        }
    }

    fun markThreadRead(threadKey: String) {
        viewModelScope.launch {
            chatRepository.markThreadRead(threadKey)
        }
    }

    fun setTypingIndicator(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String,
        isCounterpartyTyping: Boolean
    ) {
        viewModelScope.launch {
            chatRepository.setTypingIndicator(
                threadKey = threadKey,
                scope = scope,
                transactionId = transactionId,
                remoteId = remoteId,
                partyId = partyId,
                partyName = partyName,
                isCounterpartyTyping = isCounterpartyTyping
            )
        }
    }

    fun toggleThreadMute(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String
    ) {
        viewModelScope.launch {
            chatRepository.toggleThreadMute(
                threadKey = threadKey,
                scope = scope,
                transactionId = transactionId,
                remoteId = remoteId,
                partyId = partyId,
                partyName = partyName
            )
        }
    }

    fun toggleThreadBlock(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String
    ) {
        viewModelScope.launch {
            chatRepository.toggleThreadBlock(
                threadKey = threadKey,
                scope = scope,
                transactionId = transactionId,
                remoteId = remoteId,
                partyId = partyId,
                partyName = partyName
            )
        }
    }

    fun reportAbusiveUserInThread(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String,
        reason: String
    ) {
        viewModelScope.launch {
            chatRepository.reportAbusiveUserInThread(
                threadKey = threadKey,
                scope = scope,
                transactionId = transactionId,
                remoteId = remoteId,
                partyId = partyId,
                partyName = partyName,
                reason = reason
            )
        }
    }
}
