package com.jyotirmoyb.udhaar.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode
import com.jyotirmoyb.udhaar.data.AutoBackupFrequency
import com.jyotirmoyb.udhaar.data.CloudBackupState
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.data.cloud.CloudUserProfile
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.security.EncryptedStorageManager
import com.jyotirmoyb.udhaar.security.PinSecurityManager
import com.jyotirmoyb.udhaar.security.PinVerificationResult
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.util.SafeLogger
import com.jyotirmoyb.udhaar.util.SettingsPreferenceHelper.PrefKeys
import com.jyotirmoyb.udhaar.util.SettingsPreferenceHelper.mapPreferencesToUserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private val Context.udhaarDataStore: DataStore<Preferences> by preferencesDataStore(name = "udhaar_settings")

open class SettingsRepository(
    @ApplicationContext private val context: Context? = null,
    private val cloudRepository: VerificationCloudRepository? = null
) {
    val pinSecurityManager: PinSecurityManager? = context?.let { PinSecurityManager(it) }
    private val inMemorySettingsFallback = MutableStateFlow(UserSettings())

    val userSettings: Flow<UserSettings> = context?.udhaarDataStore?.data
        ?.catch { e ->
            SafeLogger.w("SettingsRepository", "Failed reading DataStore preferences", e)
            emit(emptyPreferences())
        }
        ?.map { prefs ->
            mapPreferencesToUserSettings(prefs, pinSecurityManager)
        } ?: inMemorySettingsFallback

    suspend fun updateSettings(
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
    ) {
        if (!securityPin.isNullOrBlank()) {
            pinSecurityManager?.setPin(securityPin.trim())
        }
        val hasPinNow = pinSecurityManager?.hasPinConfigured() ?: false

        context?.udhaarDataStore?.edit { prefs ->
            prefs.remove(PrefKeys.LEGACY_SECURITY_PIN)
            prefs[PrefKeys.UPI_ID] = upiId.trim()
            prefs[PrefKeys.PAYEE_NAME] = payeeName.trim()
            if (phoneNumber != null) {
                prefs[PrefKeys.PHONE_NUMBER] = phoneNumber.trim()
            }
            if (profilePhotoUri != null) {
                prefs[PrefKeys.PROFILE_PHOTO_URI] = profilePhotoUri.trim()
            }
            if (cloudVerificationConsent != null) {
                prefs[PrefKeys.CLOUD_CONSENT] = cloudVerificationConsent
            }
            val nextInr = inrEnabled ?: (prefs[PrefKeys.INR_ENABLED] ?: true)
            val nextNpr = nprEnabled ?: (prefs[PrefKeys.NPR_ENABLED] ?: false)
            val finalInr = if (!nextInr && !nextNpr) true else nextInr
            prefs[PrefKeys.INR_ENABLED] = finalInr
            prefs[PrefKeys.NPR_ENABLED] = nextNpr

            val effectiveDefault = when {
                defaultCurrency == CurrencyType.NPR && !nextNpr -> CurrencyType.INR
                defaultCurrency == CurrencyType.INR && !finalInr && nextNpr -> CurrencyType.NPR
                else -> defaultCurrency
            }
            prefs[PrefKeys.DEFAULT_CURRENCY] = effectiveDefault.name

            if (themeMode != null) {
                prefs[PrefKeys.THEME_MODE] = themeMode.name
            }
            if (fontScale != null) {
                prefs[PrefKeys.FONT_SCALE] = fontScale.name
            }
            if (biometricLockEnabled != null) {
                prefs[PrefKeys.BIOMETRIC_LOCK_ENABLED] = biometricLockEnabled && hasPinNow
            }
            if (!securityPin.isNullOrBlank()) {
                prefs[PrefKeys.PIN_CONFIGURED_REVISION] = System.currentTimeMillis().toString()
            }
        }

        context?.let { ctx ->
            runCatching {
                EncryptedStorageManager.getEncryptedPreferences(ctx)
                    .edit()
                    .putString("upi_id", upiId.trim())
                    .putString("payee_name", payeeName.trim())
                    .apply {
                        if (phoneNumber != null) putString("user_phone_number", phoneNumber.trim())
                    }
                    .apply()
            }
        }

        val currentSettings = userSettings.first()
        if (currentSettings.cloudVerificationConsent && currentSettings.cloudUserId.isNotBlank()) {
            runCatching {
                val fcmToken = resolveCurrentFcmToken()
                cloudRepository?.upsertUserProfile(
                    CloudUserProfile(
                        userId = currentSettings.cloudUserId,
                        displayName = currentSettings.payeeName.ifBlank { "Udhaar User" },
                        phoneNumber = currentSettings.phoneNumber,
                        upiId = currentSettings.upiId,
                        photoUri = currentSettings.profilePhotoUri,
                        fcmToken = fcmToken
                    )
                )
            }
        }
    }

    suspend fun resolveCurrentFcmToken(): String {
        val ctx = context ?: return ""
        val cached = runCatching {
            EncryptedStorageManager.getEncryptedPreferences(ctx)
                .getString(
                    com.jyotirmoyb.udhaar.notification.UdhaarFirebaseMessagingService.KEY_FCM_TOKEN,
                    ""
                )
                .orEmpty()
        }.getOrDefault("")
        if (cached.isNotBlank()) return cached
        return runCatching {
            val fresh = com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            fresh.await().orEmpty().also { token ->
                if (token.isNotBlank()) {
                    EncryptedStorageManager.getEncryptedPreferences(ctx)
                        .edit()
                        .putString(
                            com.jyotirmoyb.udhaar.notification.UdhaarFirebaseMessagingService.KEY_FCM_TOKEN,
                            token
                        )
                        .apply()
                }
            }
        }.getOrDefault("")
    }

    suspend fun updateReminderSettings(
        quietHoursEnabled: Boolean,
        quietHoursStartHour: Int,
        quietHoursEndHour: Int,
        reminderLevel1DaysBefore: Int,
        reminderLevel2MaxOverdueDays: Int,
        reminderLevel3MaxOverdueDays: Int
    ) {
        context?.udhaarDataStore?.edit { prefs ->
            prefs[PrefKeys.QUIET_HOURS_ENABLED] = quietHoursEnabled
            prefs[PrefKeys.QUIET_HOURS_START_HOUR] = quietHoursStartHour
            prefs[PrefKeys.QUIET_HOURS_END_HOUR] = quietHoursEndHour
            prefs[PrefKeys.REMINDER_L1_DAYS_BEFORE] = reminderLevel1DaysBefore
            prefs[PrefKeys.REMINDER_L2_MAX_OVERDUE] = reminderLevel2MaxOverdueDays
            prefs[PrefKeys.REMINDER_L3_MAX_OVERDUE] = reminderLevel3MaxOverdueDays
        }
    }

    suspend fun completeOnboarding(
        securityPin: String? = null,
        upiId: String? = null,
        payeeName: String? = null
    ) {
        if (!securityPin.isNullOrBlank()) {
            pinSecurityManager?.setPin(securityPin.trim())
        }
        val hasPinNow = pinSecurityManager?.hasPinConfigured() ?: false
        context?.udhaarDataStore?.edit { prefs ->
            prefs[PrefKeys.HAS_COMPLETED_ONBOARDING] = true
            if (!upiId.isNullOrBlank()) {
                prefs[PrefKeys.UPI_ID] = upiId.trim()
            }
            if (!payeeName.isNullOrBlank()) {
                prefs[PrefKeys.PAYEE_NAME] = payeeName.trim()
            }
            if (hasPinNow) {
                prefs[PrefKeys.BIOMETRIC_LOCK_ENABLED] = true
                prefs[PrefKeys.PIN_CONFIGURED_REVISION] = System.currentTimeMillis().toString()
            }
        }
    }

    suspend fun setCloudAccountState(
        consentGranted: Boolean,
        userId: String,
        email: String
    ) {
        val cleanUid = userId.trim()
        context?.udhaarDataStore?.edit { prefs ->
            prefs[PrefKeys.CLOUD_CONSENT] = consentGranted
            prefs[PrefKeys.CLOUD_USER_ID] = cleanUid
            prefs[PrefKeys.CLOUD_USER_EMAIL] = email.trim()
            if (consentGranted && cleanUid.isNotBlank()) {
                prefs[PrefKeys.CLOUD_BACKUP_STATE] = CloudBackupState.BACKING_UP.name
                prefs[PrefKeys.CLOUD_BACKUP_ERROR] = ""
            } else if (!consentGranted || cleanUid.isBlank()) {
                prefs[PrefKeys.CLOUD_BACKUP_STATE] = CloudBackupState.IDLE.name
                prefs[PrefKeys.CLOUD_BACKUP_ERROR] = ""
            }
        }
    }

    fun verifyPin(candidatePin: String): PinVerificationResult {
        val manager = pinSecurityManager
            ?: return PinVerificationResult(
                isSuccess = true,
                failedAttempts = 0,
                lockoutRemainingMillis = 0L
            )
        return manager.verifyPin(candidatePin)
    }

    fun resetPinFailedAttempts() {
        pinSecurityManager?.resetFailedAttempts()
    }

    fun getPinLockoutRemainingMillis(): Long =
        pinSecurityManager?.getRemainingLockoutMillis() ?: 0L

    fun getPinFailedAttempts(): Int =
        pinSecurityManager?.getFailedAttempts() ?: 0

    suspend fun setCurrencyEnabled(currency: CurrencyType, enabled: Boolean) {
        context?.udhaarDataStore?.edit { prefs ->
            val currentInr = prefs[PrefKeys.INR_ENABLED] ?: true
            val currentNpr = prefs[PrefKeys.NPR_ENABLED] ?: false
            var nextInr = if (currency == CurrencyType.INR) enabled else currentInr
            var nextNpr = if (currency == CurrencyType.NPR) enabled else currentNpr
            if (!nextInr && !nextNpr) {
                if (currency == CurrencyType.INR) nextNpr = true else nextInr = true
            }
            prefs[PrefKeys.INR_ENABLED] = nextInr
            prefs[PrefKeys.NPR_ENABLED] = nextNpr
        }
    }

    suspend fun setThemeMode(themeMode: AppThemeMode) {
        context?.udhaarDataStore?.edit { prefs ->
            prefs[PrefKeys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun setFontScale(fontScale: AppFontScale) {
        context?.udhaarDataStore?.edit { prefs ->
            prefs[PrefKeys.FONT_SCALE] = fontScale.name
        }
    }

    suspend fun updateDriveAccount(email: String) {
        inMemorySettingsFallback.value = inMemorySettingsFallback.value.copy(
            driveUserEmail = email.trim(),
            driveNeedsReconnect = false,
            driveLastErrorCode = DriveBackupErrorCode.NONE,
            driveLastErrorMessage = ""
        )
        context?.udhaarDataStore?.edit { prefs ->
            prefs[PrefKeys.DRIVE_USER_EMAIL] = email.trim()
            prefs.remove(PrefKeys.LEGACY_DRIVE_ACCESS_TOKEN)
            prefs[PrefKeys.DRIVE_NEEDS_RECONNECT] = false
            if (prefs[PrefKeys.DRIVE_LAST_ERROR_CODE] == DriveBackupErrorCode.NOT_CONNECTED.name ||
                prefs[PrefKeys.DRIVE_LAST_ERROR_CODE] == DriveBackupErrorCode.AUTH_EXPIRED.name
            ) {
                prefs[PrefKeys.DRIVE_LAST_ERROR_CODE] = DriveBackupErrorCode.NONE.name
                prefs.remove(PrefKeys.DRIVE_LAST_ERROR_MESSAGE)
            }
        }
    }

    suspend fun clearDriveAccount() {
        pinSecurityManager?.clearAutoBackupPassphrase()
        inMemorySettingsFallback.value = inMemorySettingsFallback.value.copy(
            driveUserEmail = "",
            driveAutoBackupPassphraseSaved = false,
            driveLastErrorCode = DriveBackupErrorCode.NONE,
            driveLastErrorMessage = "",
            driveConsecutiveFailures = 0,
            driveNeedsReconnect = false
        )
        context?.udhaarDataStore?.edit { prefs ->
            prefs.remove(PrefKeys.DRIVE_USER_EMAIL)
            prefs.remove(PrefKeys.LEGACY_DRIVE_ACCESS_TOKEN)
            prefs.remove(PrefKeys.DRIVE_AUTO_BACKUP_KEY_SAVED)
            prefs.remove(PrefKeys.DRIVE_LAST_ERROR_CODE)
            prefs.remove(PrefKeys.DRIVE_LAST_ERROR_MESSAGE)
            prefs.remove(PrefKeys.DRIVE_CONSECUTIVE_FAILURES)
            prefs.remove(PrefKeys.DRIVE_NEEDS_RECONNECT)
        }
    }

    suspend fun updateDriveLastBackup(timeMillis: Long, sizeBytes: Long) {
        recordDriveBackupSuccess(timeMillis, sizeBytes)
    }

    suspend fun clearDriveBackupMetadata() {
        inMemorySettingsFallback.value = inMemorySettingsFallback.value.copy(
            driveLastBackupTimeMillis = 0L,
            driveLastBackupSizeBytes = 0L,
            driveLastErrorCode = DriveBackupErrorCode.NONE,
            driveLastErrorMessage = "",
            driveConsecutiveFailures = 0
        )
        context?.udhaarDataStore?.edit { prefs ->
            prefs.remove(PrefKeys.DRIVE_LAST_BACKUP_TIME)
            prefs.remove(PrefKeys.DRIVE_LAST_BACKUP_SIZE)
            prefs.remove(PrefKeys.DRIVE_LAST_ERROR_CODE)
            prefs.remove(PrefKeys.DRIVE_LAST_ERROR_MESSAGE)
            prefs.remove(PrefKeys.DRIVE_CONSECUTIVE_FAILURES)
        }
    }

    suspend fun recordDriveBackupSuccess(timeMillis: Long, sizeBytes: Long) {
        inMemorySettingsFallback.value = inMemorySettingsFallback.value.copy(
            driveLastBackupTimeMillis = timeMillis,
            driveLastBackupSizeBytes = sizeBytes,
            driveLastAttemptTimeMillis = timeMillis,
            driveLastErrorCode = DriveBackupErrorCode.NONE,
            driveLastErrorMessage = "",
            driveConsecutiveFailures = 0,
            driveNeedsReconnect = false
        )
        context?.udhaarDataStore?.edit { prefs ->
            prefs[PrefKeys.DRIVE_LAST_BACKUP_TIME] = timeMillis
            prefs[PrefKeys.DRIVE_LAST_BACKUP_SIZE] = sizeBytes
            prefs[PrefKeys.DRIVE_LAST_ATTEMPT_TIME] = timeMillis
            prefs[PrefKeys.DRIVE_LAST_ERROR_CODE] = DriveBackupErrorCode.NONE.name
            prefs[PrefKeys.DRIVE_LAST_ERROR_MESSAGE] = ""
            prefs[PrefKeys.DRIVE_CONSECUTIVE_FAILURES] = 0
            prefs[PrefKeys.DRIVE_NEEDS_RECONNECT] = false
        }
    }

    suspend fun recordDriveBackupFailure(
        errorCode: DriveBackupErrorCode,
        errorMessage: String = "",
        timeMillis: Long = System.currentTimeMillis()
    ): Int {
        val nextFailures = inMemorySettingsFallback.value.driveConsecutiveFailures + 1
        inMemorySettingsFallback.value = inMemorySettingsFallback.value.copy(
            driveLastAttemptTimeMillis = timeMillis,
            driveLastErrorCode = errorCode,
            driveLastErrorMessage = errorMessage,
            driveConsecutiveFailures = nextFailures,
            driveNeedsReconnect = errorCode == DriveBackupErrorCode.AUTH_EXPIRED
        )
        var persistedCount = nextFailures
        context?.udhaarDataStore?.edit { prefs ->
            val currentCount = prefs[PrefKeys.DRIVE_CONSECUTIVE_FAILURES] ?: 0
            persistedCount = currentCount + 1
            prefs[PrefKeys.DRIVE_LAST_ATTEMPT_TIME] = timeMillis
            prefs[PrefKeys.DRIVE_LAST_ERROR_CODE] = errorCode.name
            prefs[PrefKeys.DRIVE_LAST_ERROR_MESSAGE] = errorMessage
            prefs[PrefKeys.DRIVE_CONSECUTIVE_FAILURES] = persistedCount
            prefs[PrefKeys.DRIVE_NEEDS_RECONNECT] = (errorCode == DriveBackupErrorCode.AUTH_EXPIRED)
        }
        return persistedCount
    }

    suspend fun setDriveNeedsReconnect(needsReconnect: Boolean, message: String = "") {
        inMemorySettingsFallback.value = inMemorySettingsFallback.value.copy(
            driveNeedsReconnect = needsReconnect,
            driveLastErrorCode = if (needsReconnect) DriveBackupErrorCode.AUTH_EXPIRED else inMemorySettingsFallback.value.driveLastErrorCode,
            driveLastErrorMessage = if (needsReconnect && message.isNotBlank()) message else inMemorySettingsFallback.value.driveLastErrorMessage
        )
        context?.udhaarDataStore?.edit { prefs ->
            prefs[PrefKeys.DRIVE_NEEDS_RECONNECT] = needsReconnect
            if (needsReconnect) {
                prefs[PrefKeys.DRIVE_LAST_ERROR_CODE] = DriveBackupErrorCode.AUTH_EXPIRED.name
                if (message.isNotBlank()) {
                    prefs[PrefKeys.DRIVE_LAST_ERROR_MESSAGE] = message
                }
            }
        }
    }

    suspend fun updateDriveAutoBackupSettings(
        frequency: AutoBackupFrequency,
        wifiOnly: Boolean,
        passphraseToSave: String? = null
    ) {
        if (!passphraseToSave.isNullOrBlank()) {
            pinSecurityManager?.saveAutoBackupPassphrase(passphraseToSave)
        }
        val isKeySaved = pinSecurityManager?.getAutoBackupPassphrase() != null
        inMemorySettingsFallback.value = inMemorySettingsFallback.value.copy(
            driveAutoBackupFrequency = frequency,
            driveAutoBackupWifiOnly = wifiOnly,
            driveAutoBackupPassphraseSaved = isKeySaved
        )
        context?.udhaarDataStore?.edit { prefs ->
            prefs[PrefKeys.DRIVE_AUTO_BACKUP_FREQ] = frequency.name
            prefs[PrefKeys.DRIVE_AUTO_BACKUP_WIFI] = wifiOnly
            prefs[PrefKeys.DRIVE_AUTO_BACKUP_KEY_SAVED] = isKeySaved
        }
    }
}
