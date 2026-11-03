package com.jyotirmoyb.udhaar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatThreadMetadata
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.repository.ChatRepository
import com.jyotirmoyb.udhaar.util.UdhaarAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatDomainUiState(
    val threadsByKey: Map<String, ChatThreadMetadata> = emptyMap(),
    val messagesByThreadKey: Map<String, List<ChatMessageEntity>> = emptyMap(),
    val activeThreadKey: String? = null,
    val lastError: UdhaarAppError? = null
)

/**
 * Domain ViewModel managing per-party and per-record chat threads, messages, and unread states.
 */
class ChatViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val activeThreadKeyFlow = MutableStateFlow<String?>(null)
    private val lastErrorFlow = MutableStateFlow<UdhaarAppError?>(null)

    val state: StateFlow<ChatDomainUiState> = combine(
        chatRepository.allChatThreads,
        chatRepository.allChatMessages,
        activeThreadKeyFlow,
        lastErrorFlow
    ) { threads, messages, activeKey, lastErr ->
        ChatDomainUiState(
            threadsByKey = threads.associateBy { it.threadKey },
            messagesByThreadKey = messages.groupBy { it.threadKey },
            activeThreadKey = activeKey,
            lastError = lastErr
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatDomainUiState()
    )

    fun openThread(threadKey: String) {
        activeThreadKeyFlow.update { threadKey }
        viewModelScope.launch {
            chatRepository.markThreadMessagesRead(threadKey)
        }
    }

    fun closeThread() {
        activeThreadKeyFlow.update { null }
    }

    fun sendMessage(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long?,
        partyId: Long? = null,
        partyName: String,
        text: String
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            chatRepository.sendMessage(
                threadKey = threadKey,
                scope = scope,
                transactionId = transactionId,
                partyId = partyId,
                partyName = partyName,
                textContent = trimmed
            ).onFailure { err ->
                lastErrorFlow.update { err }
            }
        }
    }

    class Factory(
        private val chatRepository: ChatRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(chatRepository) as T
        }
    }
}
