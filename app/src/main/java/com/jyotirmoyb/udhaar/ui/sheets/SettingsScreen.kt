package com.jyotirmoyb.udhaar.ui.sheets

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.backup.DriveConnectionTestReport
import com.jyotirmoyb.udhaar.backup.DriveRemoteBackup
import com.jyotirmoyb.udhaar.data.AutoBackupFrequency
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.security.EncryptedStorageManager
import com.jyotirmoyb.udhaar.security.SecureScreenEffect
import com.jyotirmoyb.udhaar.ui.components.AppIconButton
import com.jyotirmoyb.udhaar.ui.components.minTouchTarget
import com.jyotirmoyb.udhaar.ui.components.pressScaleFeedback
import com.jyotirmoyb.udhaar.ui.sheets.settings.AboutSection
import com.jyotirmoyb.udhaar.ui.sheets.settings.BackupSection
import com.jyotirmoyb.udhaar.ui.sheets.settings.CurrencyThemeSection
import com.jyotirmoyb.udhaar.ui.sheets.settings.DriveBackupPassphraseDialog
import com.jyotirmoyb.udhaar.ui.sheets.settings.DriveRestorePassphraseDialog
import com.jyotirmoyb.udhaar.ui.sheets.settings.ProfileUpiSection
import com.jyotirmoyb.udhaar.ui.sheets.settings.RemindersSettingsSection
import com.jyotirmoyb.udhaar.ui.sheets.settings.SecuritySection
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.util.AppSigningInfo

@Composable
fun SettingsScreen(
    currentSettings: UserSettings,
    onSave: (
        upiId: String,
        payeeName: String,
        defaultCurrency: CurrencyType,
        inrEnabled: Boolean,
        nprEnabled: Boolean,
        themeMode: AppThemeMode,
        fontScale: AppFontScale,
        biometricLockEnabled: Boolean,
        securityPin: String,
        closeModal: Boolean
    ) -> Unit,
    onSaveExtendedProfile: (phoneNumber: String, profilePhotoUri: String, cloudVerificationConsent: Boolean) -> Unit = { _, _, _ -> },
    onShareMyPaymentDetails: () -> Unit = {},
    onOpenReceivedVerificationLink: (String) -> Unit = {},
    onSignInWithGoogleForVerification: () -> Unit = {},
    onDeleteMyCloudData: () -> Unit = {},
    onLockAppNow: () -> Unit = {},
    onOpenGlobalQrModal: () -> Unit = {},
    onOpenShareAppModal: () -> Unit = {},
    onExportFullLedgerSaf: () -> Unit,
    onShareFullLedgerFile: () -> Unit,
    onImportLedgerSaf: () -> Unit,
    driveRemoteBackups: List<DriveRemoteBackup> = emptyList(),
    isDriveOperationInProgress: Boolean = false,
    driveOperationStatusMessage: String? = null,
    driveOperationError: String? = null,
    hasPendingDriveResolution: Boolean = false,
    driveConnectionTestReport: DriveConnectionTestReport? = null,
    appSigningInfo: AppSigningInfo? = null,
    onSignInWithGoogleForDrive: () -> Unit = {},
    onReconnectGoogleDrive: () -> Unit = onSignInWithGoogleForDrive,
    onRunDriveConnectionTest: () -> Unit = {},
    onClearDriveConnectionTestReport: () -> Unit = {},
    onSignOutFromDrive: () -> Unit = {},
    onBackupNowToDrive: (passphrase: String, savePassphraseForAuto: Boolean) -> Unit = { _, _ -> },
    onRestoreDriveBackup: (DriveRemoteBackup, passphrase: String) -> Unit = { _, _ -> },
    onDeleteDriveBackup: (DriveRemoteBackup) -> Unit = {},
    onDeleteAllDriveBackups: () -> Unit = {},
    onUpdateAutoBackupSettings: (AutoBackupFrequency, Boolean, String?) -> Unit = { _, _, _ -> },
    onRefreshDriveBackups: () -> Unit = {},
    onClearDriveOperationError: () -> Unit = {},
    dbEncryptionState: EncryptedStorageManager.DbEncryptionState =
        EncryptedStorageManager.DbEncryptionState.ENCRYPTED,
    onRetryDatabaseEncryption: () -> Unit = {},
    onExportLocalUdhaarSaf: (passphrase: String) -> Unit = {},
    onRetryCloudBackup: () -> Unit = {},
    onOpenContactPicker: () -> Unit = {},
    onSaveReminderSettings: ((Boolean, Int, Int, Int, Int, Int) -> Unit)? = null,
    onSaveWebLinkDefaults: ((defaultExpiryDays: Int, defaultTrustLevel: String, defaultShowUpi: Boolean) -> Unit)? = null,
    onRevokeAllWebLinks: (() -> Unit)? = null,
    onAnalyticsCrashConsentChanged: (Boolean) -> Unit = {},
    onGroupRecordsByPersonChange: (Boolean) -> Unit = {},
    onGenerateRecoveryKey: ((onKeyReady: (String) -> Unit) -> Unit)? = null,
    onTestMyBackup: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    SecureScreenEffect(enabled = false)

    BackHandler {
        onDismiss()
    }

    val context = LocalContext.current
    var payeeName by rememberSaveable(currentSettings.payeeName) { mutableStateOf(currentSettings.payeeName) }
    var upiId by rememberSaveable(currentSettings.upiId) { mutableStateOf(currentSettings.upiId) }
    var phoneNumber by rememberSaveable(currentSettings.phoneNumber) { mutableStateOf(currentSettings.phoneNumber) }
    var profilePhotoUri by rememberSaveable(currentSettings.profilePhotoUri) { mutableStateOf(currentSettings.profilePhotoUri) }
    var cloudVerificationConsent by rememberSaveable(currentSettings.cloudVerificationConsent) {
        mutableStateOf(currentSettings.cloudVerificationConsent)
    }

    var inrEnabled by rememberSaveable(currentSettings.inrEnabled) { mutableStateOf(currentSettings.inrEnabled) }
    var nprEnabled by rememberSaveable(currentSettings.nprEnabled) { mutableStateOf(currentSettings.nprEnabled) }
    var defaultCurrency by rememberSaveable(currentSettings.defaultCurrency) { mutableStateOf(currentSettings.defaultCurrency) }
    var themeMode by rememberSaveable(currentSettings.themeMode) { mutableStateOf(currentSettings.themeMode) }
    var fontScale by rememberSaveable(currentSettings.fontScale) { mutableStateOf(currentSettings.fontScale) }
    var biometricLockEnabled by rememberSaveable(currentSettings.biometricLockEnabled) {
        mutableStateOf(currentSettings.biometricLockEnabled)
    }
    var analyticsCrashConsent by rememberSaveable(currentSettings.analyticsCrashConsent) {
        mutableStateOf(currentSettings.analyticsCrashConsent)
    }
    var groupRecordsByPerson by rememberSaveable(currentSettings.groupRecordsByPerson) {
        mutableStateOf(currentSettings.groupRecordsByPerson)
    }
    var securityPin by rememberSaveable { mutableStateOf("") }
    var pinValidationError by rememberSaveable { mutableStateOf<String?>(null) }

    var showBackupDialog by rememberSaveable { mutableStateOf(false) }
    var isLocalUdhaarExportMode by rememberSaveable { mutableStateOf(false) }
    var showRestoreDialog by rememberSaveable { mutableStateOf(false) }
    var selectedRestoreBackup by remember { mutableStateOf<DriveRemoteBackup?>(null) }

    var quietHoursEnabled by rememberSaveable(currentSettings.quietHoursEnabled) { mutableStateOf(currentSettings.quietHoursEnabled) }
    var quietHoursStartHour by rememberSaveable(currentSettings.quietHoursStartHour) { mutableStateOf(currentSettings.quietHoursStartHour) }
    var quietHoursEndHour by rememberSaveable(currentSettings.quietHoursEndHour) { mutableStateOf(currentSettings.quietHoursEndHour) }
    var reminderL1Days by rememberSaveable(currentSettings.reminderLevel1DaysBefore) { mutableStateOf(currentSettings.reminderLevel1DaysBefore) }
    var reminderL2Days by rememberSaveable(currentSettings.reminderLevel2MaxOverdueDays) { mutableStateOf(currentSettings.reminderLevel2MaxOverdueDays) }
    var reminderL3Days by rememberSaveable(currentSettings.reminderLevel3MaxOverdueDays) { mutableStateOf(currentSettings.reminderLevel3MaxOverdueDays) }

    fun persistReminderSettings() {
        onSaveReminderSettings?.invoke(
            quietHoursEnabled,
            quietHoursStartHour,
            quietHoursEndHour,
            reminderL1Days,
            reminderL2Days,
            reminderL3Days
        )
    }

    val validateAndSave: (Boolean) -> Unit = { closeAfterSave ->
        val trimmedPin = securityPin.trim()
        val hasValidNewPin = trimmedPin.length in 4..8 && trimmedPin.all { it.isDigit() }
        if (biometricLockEnabled && !currentSettings.hasPinConfigured && !hasValidNewPin) {
            pinValidationError = "Please set a 4 to 8 digit numeric PIN before enabling App Lock."
            Toast.makeText(
                context,
                "Please set a 4 to 8 digit PIN to enable App Lock.",
                Toast.LENGTH_SHORT
            ).show()
        } else if (trimmedPin.isNotEmpty() && !hasValidNewPin) {
            pinValidationError = "Security PIN must be 4 to 8 numeric digits."
            Toast.makeText(
                context,
                "Security PIN must be 4 to 8 numeric digits.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            pinValidationError = null
            onSaveExtendedProfile(phoneNumber.trim(), profilePhotoUri, cloudVerificationConsent)
            onSave(
                upiId.trim(),
                payeeName.trim(),
                defaultCurrency,
                inrEnabled,
                nprEnabled,
                themeMode,
                fontScale,
                biometricLockEnabled,
                trimmedPin,
                closeAfterSave
            )
            if (closeAfterSave) {
                Toast.makeText(context, context.getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val persistImmediately = {
        onSaveExtendedProfile(phoneNumber.trim(), profilePhotoUri, cloudVerificationConsent)
        onSave(
            upiId.trim(),
            payeeName.trim(),
            defaultCurrency,
            inrEnabled,
            nprEnabled,
            themeMode,
            fontScale,
            biometricLockEnabled,
            securityPin.trim(),
            false
        )
    }

    var selectedCategory by rememberSaveable { mutableStateOf("ALL") }
    val categories = listOf(
        "ALL" to R.string.settings_cat_all,
        "PROFILE" to R.string.settings_cat_profile,
        "SECURITY" to R.string.settings_cat_security,
        "DISPLAY" to R.string.settings_cat_display,
        "BACKUP" to R.string.settings_cat_backup,
        "ABOUT" to R.string.settings_cat_about
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            AppIconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("close_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.ui_str_settings_preferences_1f2ee4),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = stringResource(R.string.ui_str_profile_security_currency_backup_ab_1c71e9),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Horizontal Category Filter Strip
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("settings_category_filter_row"),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories.size) { idx ->
                            val (catKey, labelRes) = categories[idx]
                            val isSelected = selectedCategory == catKey
                            androidx.compose.material3.FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = catKey },
                                label = {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                modifier = Modifier
                                    .minTouchTarget()
                                    .testTag("settings_cat_chip_${catKey.lowercase()}")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .pressScaleFeedback()
                                .testTag("cancel_settings_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(stringResource(R.string.ui_str_cancel_1507c4), style = MaterialTheme.typography.labelLarge)
                        }
                        Button(
                            onClick = { validateAndSave(true) },
                            modifier = Modifier
                                .weight(1.6f)
                                .heightIn(min = 48.dp)
                                .pressScaleFeedback()
                                .testTag("save_settings_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ui_str_save_settings_d0f818), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .padding(innerPadding)
                    .imePadding()
                    .testTag("settings_lazy_column"),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. PROFILE AND UPI
                if (selectedCategory == "ALL" || selectedCategory == "PROFILE") {
                    item(key = "section_profile_and_upi") {
                        ProfileUpiSection(
                            payeeName = payeeName,
                            onPayeeNameChange = { payeeName = it },
                            upiId = upiId,
                            onUpiIdChange = { upiId = it },
                            phoneNumber = phoneNumber,
                            onPhoneNumberChange = { phoneNumber = it },
                            profilePhotoUri = profilePhotoUri,
                            onProfilePhotoChanged = { uri ->
                                profilePhotoUri = uri
                                onSaveExtendedProfile(phoneNumber.trim(), profilePhotoUri, cloudVerificationConsent)
                            },
                            onShareMyPaymentDetails = {
                                persistImmediately()
                                onShareMyPaymentDetails()
                            },
                            onOpenGlobalQrModal = {
                                persistImmediately()
                                onOpenGlobalQrModal()
                            }
                        )
                    }
                }

                // 2. SECURITY
                if (selectedCategory == "ALL" || selectedCategory == "SECURITY") {
                    item(key = "section_security") {
                        SecuritySection(
                            currentSettings = currentSettings,
                            dbEncryptionState = dbEncryptionState,
                            biometricLockEnabled = biometricLockEnabled,
                            onBiometricLockToggled = { enabled ->
                                biometricLockEnabled = enabled
                                persistImmediately()
                            },
                            securityPin = securityPin,
                            onSecurityPinChanged = { securityPin = it },
                            pinValidationError = pinValidationError,
                            onPinValidationErrorChanged = { pinValidationError = it },
                            onRetryDatabaseEncryption = onRetryDatabaseEncryption,
                            onLockAppNow = {
                                persistImmediately()
                                onLockAppNow()
                            },
                            analyticsCrashConsent = analyticsCrashConsent,
                            onAnalyticsCrashConsentChanged = { enabled ->
                                analyticsCrashConsent = enabled
                                onAnalyticsCrashConsentChanged(enabled)
                            }
                        )
                    }
                }

                // 3. CURRENCY AND THEME
                if (selectedCategory == "ALL" || selectedCategory == "DISPLAY") {
                    item(key = "section_currency_and_theme") {
                        CurrencyThemeSection(
                            inrEnabled = inrEnabled,
                            onInrEnabledChange = { checked ->
                                if (!checked && !nprEnabled) {
                                    nprEnabled = true
                                    defaultCurrency = CurrencyType.NPR
                                }
                                inrEnabled = checked
                                if (!inrEnabled && defaultCurrency == CurrencyType.INR) {
                                    defaultCurrency = CurrencyType.NPR
                                }
                                persistImmediately()
                            },
                            nprEnabled = nprEnabled,
                            onNprEnabledChange = { checked ->
                                if (!checked && !inrEnabled) {
                                    inrEnabled = true
                                    defaultCurrency = CurrencyType.INR
                                }
                                nprEnabled = checked
                                if (!nprEnabled && defaultCurrency == CurrencyType.NPR) {
                                    defaultCurrency = CurrencyType.INR
                                }
                                persistImmediately()
                            },
                            defaultCurrency = defaultCurrency,
                            onDefaultCurrencyChange = { curr ->
                                defaultCurrency = curr
                                persistImmediately()
                            },
                            fontScale = fontScale,
                            onFontScaleChange = { scale ->
                                fontScale = scale
                                persistImmediately()
                            },
                            themeMode = themeMode,
                            onThemeModeChange = { mode ->
                                themeMode = mode
                                persistImmediately()
                            },
                            groupRecordsByPerson = groupRecordsByPerson,
                            onGroupRecordsByPersonChange = { checked ->
                                groupRecordsByPerson = checked
                                onGroupRecordsByPersonChange(checked)
                            }
                        )
                    }

                    // 4. REMINDERS AND QUIET HOURS
                    item(key = "section_reminders_and_quiet_hours") {
                        RemindersSettingsSection(
                            quietHoursEnabled = quietHoursEnabled,
                            onQuietHoursEnabledChange = { checked ->
                                quietHoursEnabled = checked
                                persistReminderSettings()
                            },
                            quietHoursStartHour = quietHoursStartHour,
                            onQuietHoursStartHourChange = { hour ->
                                quietHoursStartHour = hour
                                persistReminderSettings()
                            },
                            quietHoursEndHour = quietHoursEndHour,
                            onQuietHoursEndHourChange = { hour ->
                                quietHoursEndHour = hour
                                persistReminderSettings()
                            },
                            reminderL1Days = reminderL1Days,
                            onReminderL1DaysChange = { days ->
                                reminderL1Days = days
                                persistReminderSettings()
                            },
                            reminderL2Days = reminderL2Days,
                            onReminderL2DaysChange = { days ->
                                reminderL2Days = days
                                persistReminderSettings()
                            },
                            reminderL3Days = reminderL3Days,
                            onReminderL3DaysChange = { days ->
                                reminderL3Days = days
                                persistReminderSettings()
                            }
                        )
                    }
                }

                // 5. BACKUP AND SYNC + 6. WEB VERIFICATION LINKS
                if (selectedCategory == "ALL" || selectedCategory == "BACKUP") {
                    item(key = "section_backup_and_sync") {
                        BackupSection(
                            currentSettings = currentSettings,
                            driveRemoteBackups = driveRemoteBackups,
                            isDriveOperationInProgress = isDriveOperationInProgress,
                            driveOperationStatusMessage = driveOperationStatusMessage,
                            driveOperationError = driveOperationError,
                            hasPendingDriveResolution = hasPendingDriveResolution,
                            driveConnectionTestReport = driveConnectionTestReport,
                            appSigningInfo = appSigningInfo,
                            cloudVerificationConsent = cloudVerificationConsent,
                            onCloudVerificationConsentChange = { granted ->
                                cloudVerificationConsent = granted
                                persistImmediately()
                            },
                            onSignInWithGoogleForDrive = onSignInWithGoogleForDrive,
                            onReconnectGoogleDrive = onReconnectGoogleDrive,
                            onRunDriveConnectionTest = onRunDriveConnectionTest,
                            onClearDriveConnectionTestReport = onClearDriveConnectionTestReport,
                            onSignOutFromDrive = onSignOutFromDrive,
                            onBackupNowClicked = {
                                isLocalUdhaarExportMode = false
                                showBackupDialog = true
                            },
                            onRestoreBackupClicked = { backup ->
                                selectedRestoreBackup = backup
                                showRestoreDialog = true
                            },
                            onDeleteDriveBackup = onDeleteDriveBackup,
                            onDeleteAllDriveBackups = onDeleteAllDriveBackups,
                            onUpdateAutoBackupSettings = onUpdateAutoBackupSettings,
                            onRefreshDriveBackups = onRefreshDriveBackups,
                            onClearDriveOperationError = onClearDriveOperationError,
                            onExportFullLedgerSaf = onExportFullLedgerSaf,
                            onShareFullLedgerFile = onShareFullLedgerFile,
                            onExportLocalUdhaarSafClicked = {
                                isLocalUdhaarExportMode = true
                                showBackupDialog = true
                            },
                            onImportLedgerSaf = onImportLedgerSaf,
                            onOpenContactPicker = onOpenContactPicker,
                            onSignInWithGoogleForVerification = {
                                persistImmediately()
                                onSignInWithGoogleForVerification()
                            },
                            onRetryCloudBackup = onRetryCloudBackup,
                            onOpenReceivedVerificationLink = onOpenReceivedVerificationLink,
                            onDeleteMyCloudData = onDeleteMyCloudData,
                            onGenerateRecoveryKey = onGenerateRecoveryKey,
                            onTestMyBackup = onTestMyBackup
                        )
                    }

                    item(key = "section_web_links") {
                        com.jyotirmoyb.udhaar.ui.sheets.settings.WebLinkSettingsSection(
                            settings = currentSettings,
                            onSaveDefaults = { expiry, trust, upi ->
                                onSaveWebLinkDefaults?.invoke(expiry, trust, upi)
                            },
                            onRevokeAllLinks = {
                                onRevokeAllWebLinks?.invoke()
                            }
                        )
                    }
                }

                // 7. ABOUT
                if (selectedCategory == "ALL" || selectedCategory == "ABOUT") {
                    item(key = "section_about") {
                        AboutSection(
                            onOpenShareAppModal = onOpenShareAppModal
                        )
                    }
                }
            }
        }
    }

    if (showBackupDialog) {
        DriveBackupPassphraseDialog(
            isLocalUdhaarExportMode = isLocalUdhaarExportMode,
            initialSavePassphraseForAuto = currentSettings.driveAutoBackupPassphraseSaved,
            onDismiss = { showBackupDialog = false },
            onConfirmDriveBackup = onBackupNowToDrive,
            onConfirmLocalExport = onExportLocalUdhaarSaf
        )
    }

    selectedRestoreBackup?.let { backup ->
        if (showRestoreDialog) {
            DriveRestorePassphraseDialog(
                backup = backup,
                onDismiss = {
                    showRestoreDialog = false
                    selectedRestoreBackup = null
                },
                onConfirmRestore = onRestoreDriveBackup
            )
        }
    }
}
