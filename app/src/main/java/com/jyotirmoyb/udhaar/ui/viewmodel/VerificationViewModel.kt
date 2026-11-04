package com.jyotirmoyb.udhaar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.cloud.GuestWebLinkCreateResult
import com.jyotirmoyb.udhaar.data.repository.GuestWebRepository
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    val requestPaymentDetailsParty: PartyEntity? = null,
    val webShareTransactionId: Long? = null,
    val webShareResult: GuestWebLinkCreateResult? = null,
    val isGeneratingWebLink: Boolean = false,
    val showUpdatesScreen: Boolean = false
)

@HiltViewModel
class VerificationViewModel @Inject constructor(
    private val verificationRepository: VerificationRepository,
    private val guestWebRepository: GuestWebRepository,
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

    private val _webShareTransactionId = MutableStateFlow<Long?>(null)
    val webShareTransactionId: StateFlow<Long?> = _webShareTransactionId.asStateFlow()

    private val _webShareResult = MutableStateFlow<GuestWebLinkCreateResult?>(null)
    val webShareResult: StateFlow<GuestWebLinkCreateResult?> = _webShareResult.asStateFlow()

    private val _isGeneratingWebLink = MutableStateFlow(false)
    val isGeneratingWebLink: StateFlow<Boolean> = _isGeneratingWebLink.asStateFlow()

    private val _showUpdatesScreen = MutableStateFlow(false)
    val showUpdatesScreen: StateFlow<Boolean> = _showUpdatesScreen.asStateFlow()

    val transactionsWithUpdates: Flow<List<UdhaarTransaction>> =
        guestWebRepository.getTransactionsWithGuestUpdates()

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
        },
        combine(
            _webShareTransactionId,
            _webShareResult,
            _isGeneratingWebLink,
            _showUpdatesScreen
        ) { webTxId, webRes, isGen, showUpd ->
            ThirdHalf(webTxId, webRes, isGen, showUpd)
        }
    ) { first, second, third ->
        VerificationUiState(
            verificationShareTransactionId = first.shareTxId,
            verificationShareLink = first.shareLink,
            incomingVerificationPayload = first.incomingVer,
            incomingPaymentProfilePayload = first.incomingProf,
            incomingPaymentProfileTargetPartyId = first.targetPartyId,
            showShareMyPaymentDetailsModal = second.showShare,
            shareMyPaymentDetailsTargetParty = second.shareTarget,
            requestPaymentDetailsParty = second.reqParty,
            webShareTransactionId = third.webTxId,
            webShareResult = third.webRes,
            isGeneratingWebLink = third.isGen,
            showUpdatesScreen = third.showUpd
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

    private data class ThirdHalf(
        val webTxId: Long?,
        val webRes: GuestWebLinkCreateResult?,
        val isGen: Boolean,
        val showUpd: Boolean
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

    fun openWebShareModal(transactionId: Long) {
        _webShareTransactionId.value = transactionId
        _webShareResult.value = null
    }

    fun closeWebShareModal() {
        _webShareTransactionId.value = null
        _webShareResult.value = null
    }

    fun generateWebLink(
        transactionId: Long,
        expiryDays: Int = 30,
        trustLevel: String = "WHATSAPP_OTP",
        allowNote: Boolean = true,
        allowAttachments: Boolean = false,
        showUpi: Boolean = true,
        allowedActions: List<String> = listOf("view", "confirm", "dispute", "pay_claim", "propose_date", "set_upi", "show_pay_qr")
    ) {
        viewModelScope.launch {
            _isGeneratingWebLink.value = true
            try {
                val result = guestWebRepository.createGuestWebLink(
                    transactionId = transactionId,
                    expiryDays = expiryDays,
                    trustLevelRequired = trustLevel,
                    allowNote = allowNote,
                    allowAttachments = allowAttachments,
                    showUpiQr = showUpi,
                    allowedActions = allowedActions
                )
                _webShareResult.value = result
            } finally {
                _isGeneratingWebLink.value = false
            }
        }
    }

    fun revokeWebLink(transactionId: Long) {
        viewModelScope.launch {
            guestWebRepository.revokeGuestWebLink(transactionId)
            _webShareResult.value = null
        }
    }

    fun openUpdatesScreen() {
        _showUpdatesScreen.value = true
    }

    fun closeUpdatesScreen() {
        _showUpdatesScreen.value = false
    }

    fun confirmPaymentClaim(transactionId: Long) {
        viewModelScope.launch {
            val ok = guestWebRepository.acceptPaymentClaim(transactionId)
            if (ok) {
                appStateHolder.setSuccessAnimation(SuccessAnimationEvent(message = "Payment confirmed"))
            }
        }
    }

    fun rejectPaymentClaim(transactionId: Long, reason: String = "Not received") {
        viewModelScope.launch {
            guestWebRepository.rejectPaymentClaim(transactionId, reason)
        }
    }

    fun acceptDateProposal(transactionId: Long) {
        viewModelScope.launch {
            val ok = guestWebRepository.acceptDateProposal(transactionId)
            if (ok) {
                appStateHolder.setSuccessAnimation(SuccessAnimationEvent(message = "Due date updated"))
            }
        }
    }

    fun declineDateProposal(transactionId: Long, reason: String = "Declined") {
        viewModelScope.launch {
            guestWebRepository.declineDateProposal(transactionId, reason)
        }
    }

    fun suggestCounterDate(transactionId: Long, counterDateMillis: Long) {
        viewModelScope.launch {
            guestWebRepository.declineDateProposal(transactionId, "Counter proposed")
            // Optionally update notes or send suggestion
        }
    }

    fun handleIncomingDeepLinkOrToken(
        urlOrToken: String,
        targetPartyId: Long? = null
    ): Boolean {
        // 1. Check for guest web link: https://udhaar.jyotirmoyb.com/u/r/<recordId>#<secret>
        val guestLinkData = VerificationCryptoHelper.parseGuestWebLink(urlOrToken)
        if (guestLinkData != null) {
            viewModelScope.launch {
                guestWebRepository.claimGuestActions(guestLinkData.recordId, guestLinkData.secret)
            }
            return true
        }

        // 2. Check for payment profile link
        val profilePayload = VerificationCryptoHelper.parsePaymentProfileLink(urlOrToken)
        if (profilePayload != null) {
            _incomingPaymentProfilePayload.value = profilePayload
            _incomingPaymentProfileTargetPartyId.value = targetPartyId
            return true
        }

        // 3. Check for standard verification link
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

    fun revokeAllGuestLinks() {
        viewModelScope.launch {
            val count = guestWebRepository.revokeAllGuestLinks()
            appStateHolder.setSuccessAnimation(SuccessAnimationEvent(message = "Revoked $count active links"))
        }
    }
}
