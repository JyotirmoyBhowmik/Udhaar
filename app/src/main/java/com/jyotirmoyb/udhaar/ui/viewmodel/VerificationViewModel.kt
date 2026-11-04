package com.jyotirmoyb.udhaar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.repository.PaymentRepository
import com.jyotirmoyb.udhaar.data.repository.SettingsRepository
import com.jyotirmoyb.udhaar.data.repository.VerificationRepository
import com.jyotirmoyb.udhaar.ui.ActiveScreen
import com.jyotirmoyb.udhaar.ui.components.SuccessAnimationEvent
import com.jyotirmoyb.udhaar.ui.state.AppStateHolder
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.SharedPaymentProfilePayload
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.SharedVerificationPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VerificationUiState(
    val verificationShareTransactionId: Long? = null,
    val verificationShareLink: String? = null,
    val incomingVerificationPayload: SharedVerificationPayload? = null,
    val incomingPaymentProfilePayload: SharedPaymentProfilePayload? = null,
    val incomingPaymentProfileTargetPartyId: Long? = null,
    val showShareMyPaymentDetailsModal: Boolean = false,
    val shareMyPaymentDetailsTargetParty: PartyEntity? = null,
    val requestPaymentDetailsParty: PartyEntity? = null
)

@HiltViewModel
class VerificationViewModel @Inject constructor(
    private val verificationRepository: VerificationRepository,
    private val paymentRepository: PaymentRepository,
    private val settingsRepository: SettingsRepository,
    private val appStateHolder: AppStateHolder
) : ViewModel() {

    private val _verificationShareTransactionId = MutableStateFlow<Long?>(null)
    val verificationShareTransactionId: StateFlow<Long?> = _verificationShareTransactionId.asStateFlow()

    private val _verificationShareLink = MutableStateFlow<String?>(null)
    val verificationShareLink: StateFlow<String?> = _verificationShareLink.asStateFlow()

    private val _incomingVerificationPayload = MutableStateFlow<SharedVerificationPayload?>(null)
    val incomingVerificationPayload: StateFlow<SharedVerificationPayload?> = _incomingVerificationPayload.asStateFlow()

    private val _incomingPaymentProfilePayload = MutableStateFlow<SharedPaymentProfilePayload?>(null)
    val incomingPaymentProfilePayload: StateFlow<SharedPaymentProfilePayload?> = _incomingPaymentProfilePayload.asStateFlow()

    private val _incomingPaymentProfileTargetPartyId = MutableStateFlow<Long?>(null)
    val incomingPaymentProfileTargetPartyId: StateFlow<Long?> = _incomingPaymentProfileTargetPartyId.asStateFlow()

    private val _showShareMyPaymentDetailsModal = MutableStateFlow(false)
    val showShareMyPaymentDetailsModal: StateFlow<Boolean> = _showShareMyPaymentDetailsModal.asStateFlow()

    private val _shareMyPaymentDetailsTargetParty = MutableStateFlow<PartyEntity?>(null)
    val shareMyPaymentDetailsTargetParty: StateFlow<PartyEntity?> = _shareMyPaymentDetailsTargetParty.asStateFlow()

    private val _requestPaymentDetailsParty = MutableStateFlow<PartyEntity?>(null)
    val requestPaymentDetailsParty: StateFlow<PartyEntity?> = _requestPaymentDetailsParty.asStateFlow()

    val uiState: StateFlow<VerificationUiState> = combine(
        combine(
            _verificationShareTransactionId,
            _verificationShareLink,
            _incomingVerificationPayload,
            _incomingPaymentProfilePayload,
            _incomingPaymentProfileTargetPartyId
        ) { shareTxId, shareLink, incomingVer, incomingProf, targetPartyId ->
            FirstHalf(shareTxId, shareLink, incomingVer, incomingProf, targetPartyId)
        },
        combine(
            _showShareMyPaymentDetailsModal,
            _shareMyPaymentDetailsTargetParty,
            _requestPaymentDetailsParty
        ) { showShare, shareTarget, reqParty ->
            SecondHalf(showShare, shareTarget, reqParty)
        }
    ) { first, second ->
        VerificationUiState(
            verificationShareTransactionId = first.shareTxId,
            verificationShareLink = first.shareLink,
            incomingVerificationPayload = first.incomingVer,
            incomingPaymentProfilePayload = first.incomingProf,
            incomingPaymentProfileTargetPartyId = first.targetPartyId,
            showShareMyPaymentDetailsModal = second.showShare,
            shareMyPaymentDetailsTargetParty = second.shareTarget,
            requestPaymentDetailsParty = second.reqParty
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VerificationUiState()
    )

    private data class FirstHalf(
        val shareTxId: Long?,
        val shareLink: String?,
        val incomingVer: SharedVerificationPayload?,
        val incomingProf: SharedPaymentProfilePayload?,
        val targetPartyId: Long?
    )

    private data class SecondHalf(
        val showShare: Boolean,
        val shareTarget: PartyEntity?,
        val reqParty: PartyEntity?
    )

    fun requestVerificationForTransaction(transaction: UdhaarTransaction) {
        viewModelScope.launch {
            val (updatedTx, link) = verificationRepository.prepareVerificationShare(transaction)
            _verificationShareTransactionId.value = updatedTx.id
            _verificationShareLink.value = link
        }
    }

    fun closeVerificationShareModal() {
        _verificationShareTransactionId.value = null
        _verificationShareLink.value = null
    }

    fun handleIncomingDeepLinkOrToken(
        urlOrToken: String,
        targetPartyId: Long? = null
    ): Boolean {
        val profilePayload = VerificationCryptoHelper.parsePaymentProfileLink(urlOrToken)
        if (profilePayload != null) {
            _incomingPaymentProfilePayload.value = profilePayload
            _incomingPaymentProfileTargetPartyId.value = targetPartyId
            return true
        }

        val verificationPayload = VerificationCryptoHelper.parseVerificationLink(urlOrToken)
        if (verificationPayload != null) {
            _incomingVerificationPayload.value = verificationPayload
            return true
        }
        return false
    }

    fun confirmOrDisputeIncomingVerification(
        confirm: Boolean,
        disputeReason: String = ""
    ) {
        val payload = _incomingVerificationPayload.value ?: return
        viewModelScope.launch {
            val savedTx = verificationRepository.processIncomingVerificationResponse(
                payload = payload,
                confirm = confirm,
                disputeReason = disputeReason
            )
            _incomingVerificationPayload.value = null
            appStateHolder.openTransactionDetail(savedTx.id)
            appStateHolder.setSummaryCurrency(savedTx.currency)
            if (confirm) {
                appStateHolder.setSuccessAnimation(SuccessAnimationEvent(message = "Record verified"))
            }
        }
    }

    fun dismissIncomingVerification() {
        _incomingVerificationPayload.value = null
    }

    fun confirmOrDisputeExistingRecord(
        transaction: UdhaarTransaction,
        confirm: Boolean,
        disputeReason: String = ""
    ) {
        viewModelScope.launch {
            verificationRepository.confirmOrDisputeExistingTransaction(
                transaction = transaction,
                confirm = confirm,
                disputeReason = disputeReason
            )
            if (confirm) {
                appStateHolder.setSuccessAnimation(SuccessAnimationEvent(message = "Record verified"))
            }
        }
    }

    fun acknowledgePartialPayment(
        transaction: UdhaarTransaction,
        payment: PartialPayment,
        actorName: String = "You"
    ) {
        viewModelScope.launch {
            paymentRepository.acknowledgePartialPayment(
                transaction = transaction,
                payment = payment,
                actorName = actorName
            )
            appStateHolder.setSuccessAnimation(SuccessAnimationEvent(message = "Payment verified"))
        }
    }

    fun openShareMyPaymentDetailsModal(targetParty: PartyEntity? = null) {
        _shareMyPaymentDetailsTargetParty.value = targetParty
        _showShareMyPaymentDetailsModal.value = true
    }

    fun closeShareMyPaymentDetailsModal() {
        _shareMyPaymentDetailsTargetParty.value = null
        _showShareMyPaymentDetailsModal.value = false
    }

    fun openRequestPartyPaymentDetailsModal(party: PartyEntity) {
        _requestPaymentDetailsParty.value = party
    }

    fun closeRequestPartyPaymentDetailsModal() {
        _requestPaymentDetailsParty.value = null
    }

    fun acceptIncomingPaymentProfile() {
        val payload = _incomingPaymentProfilePayload.value ?: return
        val targetPartyId = _incomingPaymentProfileTargetPartyId.value
        viewModelScope.launch {
            val updatedParty = verificationRepository.acceptSharedPaymentProfile(payload, targetPartyId)
            _incomingPaymentProfilePayload.value = null
            _incomingPaymentProfileTargetPartyId.value = null
            _requestPaymentDetailsParty.value = null
            if (appStateHolder.activeScreen.value == ActiveScreen.PARTY_DETAIL) {
                appStateHolder.openPartyLedgerScreen(updatedParty.partyId)
            }
        }
    }

    fun dismissIncomingPaymentProfile() {
        _incomingPaymentProfilePayload.value = null
        _incomingPaymentProfileTargetPartyId.value = null
    }

    fun setCloudAccountState(
        consentGranted: Boolean,
        userId: String,
        email: String
    ) {
        viewModelScope.launch {
            settingsRepository.setCloudAccountState(consentGranted, userId, email)
        }
    }
}
