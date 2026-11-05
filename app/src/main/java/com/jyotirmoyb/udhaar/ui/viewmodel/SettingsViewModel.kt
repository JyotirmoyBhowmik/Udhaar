package com.jyotirmoyb.udhaar.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.data.repository.SettingsRepository
import com.jyotirmoyb.udhaar.security.PinVerificationResult
import com.jyotirmoyb.udhaar.ui.ActiveScreen
import com.jyotirmoyb.udhaar.ui.QrModalState
import com.jyotirmoyb.udhaar.ui.state.AppStateHolder
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
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

data class SettingsUiState(
    val userSettings: UserSettings = UserSettings(),
    val qrModalState: QrModalState? = null,
    val showSettingsModal: Boolean = false,
    val showShareAppModal: Boolean = false,
    val showOnboardingModal: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appStateHolder: AppStateHolder,
    @ApplicationContext private val appContext: Context? = null
) : ViewModel() {

    private val _qrModalState = MutableStateFlow<QrModalState?>(null)
    val qrModalState: StateFlow<QrModalState?> = _qrModalState.asStateFlow()

    private val _showSettingsModal = MutableStateFlow(false)
    val showSettingsModal: StateFlow<Boolean> = _showSettingsModal.asStateFlow()

    private val _showShareAppModal = MutableStateFlow(false)
    val showShareAppModal: StateFlow<Boolean> = _showShareAppModal.asStateFlow()

    private val _showOnboardingDismissed = MutableStateFlow(false)
    val showOnboardingDismissed: StateFlow<Boolean> = _showOnboardingDismissed.asStateFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.userSettings,
        _qrModalState,
        _showSettingsModal,
        _showShareAppModal,
        _showOnboardingDismissed
    ) { settings, qr, showSettings, showShare, onboardingDismissed ->
        SettingsUiState(
            userSettings = settings,
            qrModalState = qr,
            showSettingsModal = showSettings,
            showShareAppModal = showShare,
            showOnboardingModal = !settings.hasCompletedOnboarding && !onboardingDismissed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun openSettingsModal() {
        _showSettingsModal.value = true
        appStateHolder.openSettingsScreen()
    }

    fun closeSettingsModal() {
        _showSettingsModal.value = false
        appStateHolder.closeSettingsScreen()
    }

    fun openShareAppModal() {
        _showShareAppModal.value = true
    }

    fun closeShareAppModal() {
        _showShareAppModal.value = false
    }

    fun openGlobalQrModal() {
        _qrModalState.value = QrModalState(
            transactionId = null,
            partyName = null,
            initialAmountInr = null,
            customUpiId = null,
            isPayToPartyMode = false
        )
    }

    fun openTransactionQrModal(summary: CalculatedUdhaarSummary) {
        _qrModalState.value = QrModalState(
            transactionId = summary.transaction.id,
            partyName = summary.transaction.partyName,
            initialAmountInr = summary.netBalanceDue,
            customUpiId = null,
            isPayToPartyMode = false
        )
        appStateHolder.closeTransactionDetail()
    }

    fun openPayToPartyQrModal(partyName: String, upiId: String, amountInr: Double? = null) {
        _qrModalState.value = QrModalState(
            transactionId = null,
            partyName = partyName,
            initialAmountInr = amountInr,
            customUpiId = upiId,
            isPayToPartyMode = true
        )
    }

    fun closeQrModal() {
        _qrModalState.value = null
    }

    fun setSummaryCurrency(currency: CurrencyType) {
        appStateHolder.setSummaryCurrency(currency)
    }

    fun toggleCurrencyEnabled(currency: CurrencyType, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCurrencyEnabled(currency, enabled)
        }
    }

    fun cycleOrSelectTheme(themeMode: AppThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(themeMode)
        }
    }

    fun selectFontScale(fontScale: AppFontScale) {
        viewModelScope.launch {
            settingsRepository.setFontScale(fontScale)
        }
    }

    fun toggleHideAmounts() {
        appStateHolder.toggleHideAmounts(appContext)
    }

    fun saveUserSettings(
        upiId: String,
        payeeName: String,
        defaultCurrency: CurrencyType,
        inrEnabled: Boolean? = null,
        nprEnabled: Boolean? = null,
        themeMode: AppThemeMode? = null,
        fontScale: AppFontScale? = null,
        biometricLockEnabled: Boolean? = null,
        securityPin: String? = null,
        phoneNumber: String? = null,
        profilePhotoUri: String? = null,
        cloudVerificationConsent: Boolean? = null,
        closeModal: Boolean = true
    ) {
        viewModelScope.launch {
            settingsRepository.updateSettings(
                upiId = upiId,
                payeeName = payeeName,
                defaultCurrency = defaultCurrency,
                inrEnabled = inrEnabled,
                nprEnabled = nprEnabled,
                themeMode = themeMode,
                fontScale = fontScale,
                biometricLockEnabled = biometricLockEnabled,
                securityPin = securityPin,
                phoneNumber = phoneNumber,
                profilePhotoUri = profilePhotoUri,
                cloudVerificationConsent = cloudVerificationConsent
            )
            if (closeModal) {
                closeSettingsModal()
            }
        }
    }

    fun completeOnboarding(pin: String, upiId: String, name: String) {
        viewModelScope.launch {
            settingsRepository.completeOnboarding(securityPin = pin, upiId = upiId, payeeName = name)
            _showOnboardingDismissed.value = true
        }
    }

    fun updateReminderSettings(
        quietHoursEnabled: Boolean,
        quietHoursStartHour: Int,
        quietHoursEndHour: Int,
        reminderLevel1DaysBefore: Int,
        reminderLevel2MaxOverdueDays: Int,
        reminderLevel3MaxOverdueDays: Int
    ) {
        viewModelScope.launch {
            settingsRepository.updateReminderSettings(
                quietHoursEnabled = quietHoursEnabled,
                quietHoursStartHour = quietHoursStartHour,
                quietHoursEndHour = quietHoursEndHour,
                reminderLevel1DaysBefore = reminderLevel1DaysBefore,
                reminderLevel2MaxOverdueDays = reminderLevel2MaxOverdueDays,
                reminderLevel3MaxOverdueDays = reminderLevel3MaxOverdueDays
            )
        }
    }

    fun verifyPin(candidatePin: String): PinVerificationResult = settingsRepository.verifyPin(candidatePin)

    fun resetPinFailedAttempts() = settingsRepository.resetPinFailedAttempts()

    fun getPinLockoutRemainingMillis(): Long = settingsRepository.getPinLockoutRemainingMillis()

    fun getPinFailedAttempts(): Int = settingsRepository.getPinFailedAttempts()

    fun markAuthenticated() {
        appStateHolder.markAuthenticated()
    }

    fun lockApplicationNow() {
        appStateHolder.lockApplicationNow()
    }

    fun updateWebLinkDefaults(
        expiryDays: Int,
        trustLevel: String,
        showUpi: Boolean
    ) {
        viewModelScope.launch {
            settingsRepository.updateWebLinkDefaults(expiryDays, trustLevel, showUpi)
        }
    }

    fun setAnalyticsCrashConsent(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAnalyticsCrashConsent(enabled)
        }
    }

    fun generateAndSaveRecoveryKey(onGenerated: (String) -> Unit = {}) {
        viewModelScope.launch {
            val newKey = com.jyotirmoyb.udhaar.util.TelemetryManager.generateBackupRecoveryKey()
            settingsRepository.saveBackupRecoveryKey(newKey)
            onGenerated(newKey)
        }
    }

    fun saveBackupRecoveryKey(recoveryKey: String) {
        viewModelScope.launch {
            settingsRepository.saveBackupRecoveryKey(recoveryKey)
        }
    }
}
