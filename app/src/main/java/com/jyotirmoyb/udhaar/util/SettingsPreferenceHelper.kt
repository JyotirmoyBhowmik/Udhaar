package com.jyotirmoyb.udhaar.util

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode
import com.jyotirmoyb.udhaar.data.AutoBackupFrequency
import com.jyotirmoyb.udhaar.data.CloudBackupState
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.security.PinSecurityManager
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode

object SettingsPreferenceHelper {
    object PrefKeys {
        val UPI_ID = stringPreferencesKey("upi_id")
        val PAYEE_NAME = stringPreferencesKey("payee_name")
        val PHONE_NUMBER = stringPreferencesKey("user_phone_number")
        val PROFILE_PHOTO_URI = stringPreferencesKey("profile_photo_uri")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val INR_ENABLED = booleanPreferencesKey("inr_enabled")
        val NPR_ENABLED = booleanPreferencesKey("npr_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_SCALE = stringPreferencesKey("font_scale")
        val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("app_lock_opt_in_enabled")
        val PIN_CONFIGURED_REVISION = stringPreferencesKey("pin_configured_revision")
        val LEGACY_SECURITY_PIN = stringPreferencesKey("security_pin")
        val CLOUD_CONSENT = booleanPreferencesKey("cloud_verification_consent")
        val CLOUD_USER_ID = stringPreferencesKey("cloud_user_id")
        val CLOUD_USER_EMAIL = stringPreferencesKey("cloud_user_email")
        val CLOUD_LAST_BACKUP_TIME = longPreferencesKey("cloud_last_backup_time")
        val CLOUD_BACKUP_STATE = stringPreferencesKey("cloud_backup_state")
        val CLOUD_BACKUP_ERROR = stringPreferencesKey("cloud_backup_error")
        val DRIVE_USER_EMAIL = stringPreferencesKey("drive_user_email")
        val LEGACY_DRIVE_ACCESS_TOKEN = stringPreferencesKey("drive_access_token")
        val DRIVE_LAST_BACKUP_TIME = longPreferencesKey("drive_last_backup_time")
        val DRIVE_LAST_BACKUP_SIZE = longPreferencesKey("drive_last_backup_size")
        val DRIVE_AUTO_BACKUP_FREQ = stringPreferencesKey("drive_auto_backup_freq")
        val DRIVE_AUTO_BACKUP_WIFI = booleanPreferencesKey("drive_auto_backup_wifi")
        val DRIVE_AUTO_BACKUP_KEY_SAVED = booleanPreferencesKey("drive_auto_backup_key_saved")
        val DRIVE_LAST_ERROR_CODE = stringPreferencesKey("drive_last_error_code")
        val DRIVE_LAST_ERROR_MESSAGE = stringPreferencesKey("drive_last_error_message")
        val DRIVE_LAST_ATTEMPT_TIME = longPreferencesKey("drive_last_attempt_time")
        val DRIVE_CONSECUTIVE_FAILURES = intPreferencesKey("drive_consecutive_failures")
        val DRIVE_NEEDS_RECONNECT = booleanPreferencesKey("drive_needs_reconnect")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_HOURS_START_HOUR = intPreferencesKey("quiet_hours_start_hour")
        val QUIET_HOURS_END_HOUR = intPreferencesKey("quiet_hours_end_hour")
        val REMINDER_L1_DAYS_BEFORE = intPreferencesKey("reminder_l1_days_before")
        val REMINDER_L2_MAX_OVERDUE = intPreferencesKey("reminder_l2_max_overdue")
        val REMINDER_L3_MAX_OVERDUE = intPreferencesKey("reminder_l3_max_overdue")
        val DEFAULT_WEB_LINK_EXPIRY = intPreferencesKey("default_web_link_expiry_days")
        val DEFAULT_WEB_TRUST_LEVEL = stringPreferencesKey("default_web_trust_level")
        val DEFAULT_WEB_SHOW_UPI = booleanPreferencesKey("default_web_show_upi")
        val ANALYTICS_CRASH_CONSENT = booleanPreferencesKey("analytics_crash_consent")
        val BACKUP_RECOVERY_KEY_ID = stringPreferencesKey("backup_recovery_key_id")
        val GROUP_RECORDS_BY_PERSON = booleanPreferencesKey("group_records_by_person")
    }

    fun mapPreferencesToUserSettings(
        prefs: Preferences,
        pinSecurityManager: PinSecurityManager?
    ): UserSettings {
        val inrEnabled = prefs[PrefKeys.INR_ENABLED] ?: true
        val nprEnabled = prefs[PrefKeys.NPR_ENABLED] ?: false
        val safeInr = if (!inrEnabled && !nprEnabled) true else inrEnabled

        val currencyStr = prefs[PrefKeys.DEFAULT_CURRENCY] ?: CurrencyType.INR.name
        val rawCurrency = runCatching { CurrencyType.valueOf(currencyStr) }.getOrDefault(CurrencyType.INR)
        val effectiveDefaultCurrency = when {
            rawCurrency == CurrencyType.NPR && !nprEnabled -> CurrencyType.INR
            rawCurrency == CurrencyType.INR && !safeInr && nprEnabled -> CurrencyType.NPR
            else -> rawCurrency
        }

        val themeStr = prefs[PrefKeys.THEME_MODE] ?: AppThemeMode.EMERALD_FINANCE.name
        val themeMode = runCatching { AppThemeMode.valueOf(themeStr) }.getOrDefault(AppThemeMode.EMERALD_FINANCE)
        val fontStr = prefs[PrefKeys.FONT_SCALE] ?: AppFontScale.STANDARD.name
        val fontScale = runCatching { AppFontScale.valueOf(fontStr) }.getOrDefault(AppFontScale.STANDARD)

        val legacyPin = prefs[PrefKeys.LEGACY_SECURITY_PIN]?.trim().orEmpty()
        if (legacyPin.length in 4..8 && legacyPin != "0000" && pinSecurityManager?.hasPinConfigured() == false) {
            pinSecurityManager.setPin(legacyPin)
        }

        val hasPin = pinSecurityManager?.hasPinConfigured() ?: false
        val lockRequested = prefs[PrefKeys.BIOMETRIC_LOCK_ENABLED] ?: false

        val driveAutoFreqStr = prefs[PrefKeys.DRIVE_AUTO_BACKUP_FREQ] ?: AutoBackupFrequency.OFF.name
        val driveAutoFreq = runCatching { AutoBackupFrequency.valueOf(driveAutoFreqStr) }.getOrDefault(AutoBackupFrequency.OFF)

        val driveLastErrorStr = prefs[PrefKeys.DRIVE_LAST_ERROR_CODE] ?: DriveBackupErrorCode.NONE.name
        val driveLastErrorCode = runCatching { DriveBackupErrorCode.valueOf(driveLastErrorStr) }
            .getOrDefault(DriveBackupErrorCode.NONE)

        val cloudLastBackupMillis = prefs[PrefKeys.CLOUD_LAST_BACKUP_TIME] ?: 0L
        val cloudStateStr = prefs[PrefKeys.CLOUD_BACKUP_STATE].orEmpty()
        val rawCloudState = runCatching { CloudBackupState.valueOf(cloudStateStr) }.getOrDefault(
            if (cloudLastBackupMillis > 0L) CloudBackupState.BACKED_UP else CloudBackupState.IDLE
        )
        val effectiveCloudState = if (rawCloudState == CloudBackupState.IDLE && cloudLastBackupMillis > 0L) {
            CloudBackupState.BACKED_UP
        } else {
            rawCloudState
        }

        return UserSettings(
            upiId = prefs[PrefKeys.UPI_ID] ?: "",
            payeeName = prefs[PrefKeys.PAYEE_NAME] ?: "",
            phoneNumber = prefs[PrefKeys.PHONE_NUMBER] ?: "",
            profilePhotoUri = prefs[PrefKeys.PROFILE_PHOTO_URI] ?: "",
            defaultCurrency = effectiveDefaultCurrency,
            inrEnabled = safeInr,
            nprEnabled = nprEnabled,
            themeMode = themeMode,
            fontScale = fontScale,
            biometricLockEnabled = lockRequested && hasPin,
            hasPinConfigured = hasPin,
            cloudVerificationConsent = prefs[PrefKeys.CLOUD_CONSENT] ?: false,
            cloudUserId = prefs[PrefKeys.CLOUD_USER_ID] ?: "",
            cloudUserEmail = prefs[PrefKeys.CLOUD_USER_EMAIL] ?: "",
            cloudLastBackupTimeMillis = cloudLastBackupMillis,
            cloudBackupState = effectiveCloudState,
            cloudBackupError = prefs[PrefKeys.CLOUD_BACKUP_ERROR] ?: "",
            driveUserEmail = prefs[PrefKeys.DRIVE_USER_EMAIL] ?: "",
            driveLastBackupTimeMillis = prefs[PrefKeys.DRIVE_LAST_BACKUP_TIME] ?: 0L,
            driveLastBackupSizeBytes = prefs[PrefKeys.DRIVE_LAST_BACKUP_SIZE] ?: 0L,
            driveAutoBackupFrequency = driveAutoFreq,
            driveAutoBackupWifiOnly = prefs[PrefKeys.DRIVE_AUTO_BACKUP_WIFI] ?: true,
            driveAutoBackupPassphraseSaved = prefs[PrefKeys.DRIVE_AUTO_BACKUP_KEY_SAVED] ?: false,
            driveLastErrorCode = driveLastErrorCode,
            driveLastErrorMessage = prefs[PrefKeys.DRIVE_LAST_ERROR_MESSAGE] ?: "",
            driveLastAttemptTimeMillis = prefs[PrefKeys.DRIVE_LAST_ATTEMPT_TIME] ?: 0L,
            driveConsecutiveFailures = prefs[PrefKeys.DRIVE_CONSECUTIVE_FAILURES] ?: 0,
            driveNeedsReconnect = prefs[PrefKeys.DRIVE_NEEDS_RECONNECT] ?: false,
            hasCompletedOnboarding = prefs[PrefKeys.HAS_COMPLETED_ONBOARDING] ?: false,
            quietHoursEnabled = prefs[PrefKeys.QUIET_HOURS_ENABLED] ?: true,
            quietHoursStartHour = prefs[PrefKeys.QUIET_HOURS_START_HOUR] ?: 21,
            quietHoursEndHour = prefs[PrefKeys.QUIET_HOURS_END_HOUR] ?: 8,
            reminderLevel1DaysBefore = prefs[PrefKeys.REMINDER_L1_DAYS_BEFORE] ?: 3,
            reminderLevel2MaxOverdueDays = prefs[PrefKeys.REMINDER_L2_MAX_OVERDUE] ?: 7,
            reminderLevel3MaxOverdueDays = prefs[PrefKeys.REMINDER_L3_MAX_OVERDUE] ?: 30,
            defaultWebLinkExpiryDays = prefs[PrefKeys.DEFAULT_WEB_LINK_EXPIRY] ?: 30,
            defaultWebTrustLevel = prefs[PrefKeys.DEFAULT_WEB_TRUST_LEVEL] ?: "WHATSAPP_OTP",
            defaultWebShowUpi = prefs[PrefKeys.DEFAULT_WEB_SHOW_UPI] ?: true,
            analyticsCrashConsent = prefs[PrefKeys.ANALYTICS_CRASH_CONSENT] ?: false,
            backupRecoveryKeyId = prefs[PrefKeys.BACKUP_RECOVERY_KEY_ID] ?: "",
            groupRecordsByPerson = prefs[PrefKeys.GROUP_RECORDS_BY_PERSON] ?: false
        )
    }
}
