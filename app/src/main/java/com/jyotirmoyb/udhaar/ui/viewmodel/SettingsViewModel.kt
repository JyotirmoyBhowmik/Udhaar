package com.jyotirmoyb.udhaar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.data.repository.SettingsRepository
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.util.UdhaarAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsDomainUiState(
    val userSettings: UserSettings = UserSettings(),
    val isDeletingCloudData: Boolean = false,
    val statusBanner: String? = null,
    val lastError: UdhaarAppError? = null
)

/**
 * Domain ViewModel managing user profile, UPI VPA, theme/font scale, PIN/biometric security,
 * and cloud data deletion (`deleteMyCloudData`).
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val isDeletingFlow = MutableStateFlow(false)
    private val statusBannerFlow = MutableStateFlow<String?>(null)
    private val lastErrorFlow = MutableStateFlow<UdhaarAppError?>(null)

    val state: StateFlow<SettingsDomainUiState> = combine(
        settingsRepository.userSettings,
        isDeletingFlow,
        statusBannerFlow,
        lastErrorFlow
    ) { settings, deleting, banner, error ->
        SettingsDomainUiState(
            userSettings = settings,
            isDeletingCloudData = deleting,
            statusBanner = banner,
            lastError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsDomainUiState()
    )

    fun saveSettings(
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
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            settingsRepository.saveProfileAndSettings(
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
            ).onSuccess {
                statusBannerFlow.update { "Settings saved." }
                onSaved()
            }.onFailure { err ->
                lastErrorFlow.update { err }
            }
        }
    }

    fun deleteMyCloudData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            isDeletingFlow.update { true }
            lastErrorFlow.update { null }
            settingsRepository.deleteMyCloudData()
                .onSuccess {
                    isDeletingFlow.update { false }
                    statusBannerFlow.update { "Cloud verification records, chat threads, and FCM token deleted." }
                    onComplete()
                }
                .onFailure { err ->
                    isDeletingFlow.update { false }
                    lastErrorFlow.update { err }
                }
        }
    }

    class Factory(
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsRepository) as T
        }
    }
}
