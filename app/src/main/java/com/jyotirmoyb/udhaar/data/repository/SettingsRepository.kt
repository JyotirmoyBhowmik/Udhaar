package com.jyotirmoyb.udhaar.data.repository

import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.security.PinVerificationResult
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.util.SafeLogger
import com.jyotirmoyb.udhaar.util.UdhaarAppError
import com.jyotirmoyb.udhaar.util.UdhaarResult
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository responsible for user preferences, UPI VPA profile, biometric/PIN lock,
 * currency toggles, and cloud account privacy controls (`deleteMyCloudData`).
 */
class SettingsRepository(
    private val delegate: UdhaarRepository
) {
    val userSettings: Flow<UserSettings> = delegate.userSettings

    suspend fun saveProfileAndSettings(
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
        cloudVerificationConsent: Boolean? = null
    ): UdhaarResult<Unit> {
        return try {
            delegate.updateSettings(
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
            UdhaarResult.Success(Unit)
        } catch (t: Throwable) {
            SafeLogger.e("SettingsRepository", "Failed saving user settings", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Could not save settings"), t)
        }
    }

    fun verifyPin(candidatePin: String): PinVerificationResult {
        return delegate.verifyPin(candidatePin)
    }

    suspend fun deleteMyCloudData(): UdhaarResult<Unit> {
        return try {
            delegate.deleteMyCloudData()
            UdhaarResult.Success(Unit)
        } catch (t: Throwable) {
            SafeLogger.e("SettingsRepository", "Failed deleting user cloud data", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Failed to delete cloud data"), t)
        }
    }
}
