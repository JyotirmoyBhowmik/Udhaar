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
    val isProcessing: Boolean = false,
    val activeShareTransactionId: Long? = null,
    val activeShareUrl: String? = null,
    val incomingPayload: VerificationCryptoHelper.SharedVerificationPayload? = null,
    val incomingProfilePayload: VerificationCryptoHelper.SharedPaymentProfilePayload? = null,
    val lastError: UdhaarAppError? = null
)

/**
 * Domain ViewModel for Two-Party Verification, content hash calculation, and cryptographic token verification.
 */
class VerificationViewModel(
    private val verificationRepository: VerificationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(VerificationDomainUiState())
    val state: StateFlow<VerificationDomainUiState> = _state.asStateFlow()

    fun generateShareLink(transaction: UdhaarTransaction, onReady: (String) -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, lastError = null) }
            try {
                val (updatedTx, url) = verificationRepository.prepareVerificationShare(transaction)
                _state.update {
                    it.copy(
                        isProcessing = false,
                        activeShareTransactionId = updatedTx.id,
                        activeShareUrl = url
                    )
                }
                onReady(url)
            } catch (t: Throwable) {
                _state.update { it.copy(isProcessing = false, lastError = UdhaarAppError.fromThrowable(t)) }
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
            try {
                val tx = verificationRepository.processIncomingVerificationResponse(payload, confirm = true, disputeReason = "")
                _state.update { it.copy(isProcessing = false, incomingPayload = null) }
                onConfirmed(tx.id)
            } catch (t: Throwable) {
                _state.update { it.copy(isProcessing = false, lastError = UdhaarAppError.fromThrowable(t)) }
            }
        }
    }

    fun disputeIncoming(disputeReason: String, onDisputed: (Long) -> Unit = {}) {
        val payload = _state.value.incomingPayload ?: return
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, lastError = null) }
            try {
                val tx = verificationRepository.processIncomingVerificationResponse(payload, confirm = false, disputeReason = disputeReason)
                _state.update { it.copy(isProcessing = false, incomingPayload = null) }
                onDisputed(tx.id)
            } catch (t: Throwable) {
                _state.update { it.copy(isProcessing = false, lastError = UdhaarAppError.fromThrowable(t)) }
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
