package com.jyotirmoyb.udhaar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.repository.VerificationRepository
import com.jyotirmoyb.udhaar.util.UdhaarAppError
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VerificationDomainUiState(
    val activeShareTransactionId: Long? = null,
    val activeShareUrl: String? = null,
    val incomingPayload: VerificationCryptoHelper.SharedVerificationPayload? = null,
    val incomingProfilePayload: VerificationCryptoHelper.SharedPaymentProfilePayload? = null,
    val isProcessing: Boolean = false,
    val lastError: UdhaarAppError? = null
)

/**
 * Domain ViewModel managing two-party verification links (`https://udhaar.jyotirmoyb.com/u/<token>`),
 * payment profile links (`https://udhaar.jyotirmoyb.com/u/p/<token>`), and mutual confirmation.
 */
class VerificationViewModel(
    private val verificationRepository: VerificationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(VerificationDomainUiState())
    val state: StateFlow<VerificationDomainUiState> = _state.asStateFlow()

    fun generateShareLink(transaction: UdhaarTransaction, onReady: (String) -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, lastError = null) }
            verificationRepository.prepareVerificationShare(transaction)
                .onSuccess { (updatedTx, url) ->
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            activeShareTransactionId = updatedTx.id,
                            activeShareUrl = url
                        )
                    }
                    onReady(url)
                }
                .onFailure { err ->
                    _state.update { it.copy(isProcessing = false, lastError = err) }
                }
        }
    }

    fun parseIncomingUrlOrToken(urlOrToken: String): Boolean {
        val profile = VerificationCryptoHelper.parsePaymentProfileLink(urlOrToken)
        if (profile != null) {
            _state.update { it.copy(incomingProfilePayload = profile, lastError = null) }
            return true
        }
        val payload = VerificationCryptoHelper.parseVerificationLink(urlOrToken)
        return if (payload != null) {
            _state.update { it.copy(incomingPayload = payload, lastError = null) }
            true
        } else {
            _state.update { it.copy(lastError = UdhaarAppError.CorruptedFile) }
            false
        }
    }

    fun confirmIncoming(onConfirmed: (Long) -> Unit = {}) {
        val payload = _state.value.incomingPayload ?: return
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, lastError = null) }
            verificationRepository.confirmIncomingVerification(payload)
                .onSuccess { tx ->
                    _state.update { it.copy(isProcessing = false, incomingPayload = null) }
                    onConfirmed(tx.id)
                }
                .onFailure { err ->
                    _state.update { it.copy(isProcessing = false, lastError = err) }
                }
        }
    }

    fun disputeIncoming(disputeReason: String, onDisputed: (Long) -> Unit = {}) {
        val payload = _state.value.incomingPayload ?: return
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, lastError = null) }
            verificationRepository.disputeIncomingVerification(payload, disputeReason)
                .onSuccess { tx ->
                    _state.update { it.copy(isProcessing = false, incomingPayload = null) }
                    onDisputed(tx.id)
                }
                .onFailure { err ->
                    _state.update { it.copy(isProcessing = false, lastError = err) }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(lastError = null) }
    }

    class Factory(
        private val verificationRepository: VerificationRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VerificationViewModel(verificationRepository) as T
        }
    }
}
