package com.jyotirmoyb.udhaar.ui.components

import androidx.compose.runtime.Composable
import com.jyotirmoyb.udhaar.backup.DriveConnectionTestReport
import com.jyotirmoyb.udhaar.backup.DriveRemoteBackup
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.AuditTrailEntry
import com.jyotirmoyb.udhaar.data.AutoBackupFrequency
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.security.EncryptedStorageManager
import com.jyotirmoyb.udhaar.ui.common.BreakdownLine as CommonBreakdownLine
import com.jyotirmoyb.udhaar.ui.common.PhoneMaskVisualTransformation as CommonPhoneMaskVisualTransformation
import com.jyotirmoyb.udhaar.ui.common.SettingsSectionCard as CommonSettingsSectionCard
import com.jyotirmoyb.udhaar.ui.common.SqlCipherBlockingErrorScreen as CommonSqlCipherBlockingErrorScreen
import com.jyotirmoyb.udhaar.ui.common.UpiMaskVisualTransformation as CommonUpiMaskVisualTransformation
import com.jyotirmoyb.udhaar.ui.sheets.RecordDetailSheet
import com.jyotirmoyb.udhaar.ui.sheets.SettingsScreen
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.util.AppSigningInfo
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary

val PhoneMaskVisualTransformation = CommonPhoneMaskVisualTransformation
val UpiMaskVisualTransformation = CommonUpiMaskVisualTransformation

@Composable
fun SettingsSectionCard(
    sectionNumber: String,
    title: String,
    subtitle: String,
    testTag: String,
    content: @Composable () -> Unit
) {
    CommonSettingsSectionCard(
        sectionNumber = sectionNumber,
        title = title,
        subtitle = subtitle,
        testTag = testTag,
        content = content
    )
}

@Composable
fun BreakdownLine(
    label: String,
    value: String,
    bold: Boolean = false
) {
    CommonBreakdownLine(
        label = label,
        value = value,
        bold = bold
    )
}

@Composable
fun SqlCipherBlockingErrorScreen(
    errorMessage: String,
    onExportBackup: () -> Unit,
    onTryAgain: () -> Unit
) {
    CommonSqlCipherBlockingErrorScreen(
        errorMessage = errorMessage,
        onExportBackup = onExportBackup,
        onTryAgain = onTryAgain
    )
}

@Composable
fun TransactionDetailModal(
    summary: CalculatedUdhaarSummary,
    partialPayments: List<PartialPayment>,
    auditTrail: List<AuditTrailEntry> = emptyList(),
    linkedParty: PartyEntity? = null,
    attachments: List<Attachment> = emptyList(),
    attachmentsByPayment: Map<Long, List<Attachment>> = emptyMap(),
    onDismiss: () -> Unit,
    onRecordPartialPayment: (amount: Double, paymentMode: PaymentMode, note: String) -> Unit,
    onRecordCounterpartyPayment: (amount: Double, paymentMode: PaymentMode, note: String) -> Unit = { _, _, _ -> },
    onRecordPartialPaymentWithProof: (amount: Double, paymentMode: PaymentMode, note: String, proof: Attachment?) -> Unit = { _, _, _, _ -> },
    onAcknowledgePartialPayment: (PartialPayment) -> Unit = {},
    onDeletePartialPayment: (PartialPayment) -> Unit,
    onAddAttachment: (Attachment) -> Unit = {},
    onDeleteAttachment: (Attachment) -> Unit = {},
    onOpenAttachmentViewer: (Attachment) -> Unit = {},
    onRequestVerification: () -> Unit = {},
    onConfirmOrDisputeRecord: (confirm: Boolean, disputeReason: String) -> Unit = { _, _ -> },
    onToggleSettled: () -> Unit,
    onEdit: () -> Unit,
    onDeleteTransaction: () -> Unit,
    onOpenQr: () -> Unit,
    onPayPartyViaUpi: (partyName: String, partyUpiId: String, amountInr: Double) -> Unit = { _, _, _ -> },
    onShareReceipt: () -> Unit = {},
    onOpenWhatsApp: () -> Unit,
    unreadChatCount: Int = 0,
    onOpenTransactionChat: () -> Unit = {}
) {
    RecordDetailSheet(
        summary = summary,
        partialPayments = partialPayments,
        auditTrail = auditTrail,
        linkedParty = linkedParty,
        attachments = attachments,
        attachmentsByPayment = attachmentsByPayment,
        onDismiss = onDismiss,
        onRecordPartialPayment = onRecordPartialPayment,
        onRecordCounterpartyPayment = onRecordCounterpartyPayment,
        onRecordPartialPaymentWithProof = onRecordPartialPaymentWithProof,
        onAcknowledgePartialPayment = onAcknowledgePartialPayment,
        onDeletePartialPayment = onDeletePartialPayment,
        onAddAttachment = onAddAttachment,
        onDeleteAttachment = onDeleteAttachment,
        onOpenAttachmentViewer = onOpenAttachmentViewer,
        onRequestVerification = onRequestVerification,
        onConfirmOrDisputeRecord = onConfirmOrDisputeRecord,
        onToggleSettled = onToggleSettled,
        onEdit = onEdit,
        onDeleteTransaction = onDeleteTransaction,
        onOpenQr = onOpenQr,
        onPayPartyViaUpi = onPayPartyViaUpi,
        onShareReceipt = onShareReceipt,
        onOpenWhatsApp = onOpenWhatsApp,
        unreadChatCount = unreadChatCount,
        onOpenTransactionChat = onOpenTransactionChat
    )
}

@Composable
fun ProfileSettingsModal(
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
    onDismiss: () -> Unit
) {
    SettingsScreen(
        currentSettings = currentSettings,
        onSave = onSave,
        onSaveExtendedProfile = onSaveExtendedProfile,
        onShareMyPaymentDetails = onShareMyPaymentDetails,
        onOpenReceivedVerificationLink = onOpenReceivedVerificationLink,
        onSignInWithGoogleForVerification = onSignInWithGoogleForVerification,
        onDeleteMyCloudData = onDeleteMyCloudData,
        onLockAppNow = onLockAppNow,
        onOpenGlobalQrModal = onOpenGlobalQrModal,
        onOpenShareAppModal = onOpenShareAppModal,
        onExportFullLedgerSaf = onExportFullLedgerSaf,
        onShareFullLedgerFile = onShareFullLedgerFile,
        onImportLedgerSaf = onImportLedgerSaf,
        driveRemoteBackups = driveRemoteBackups,
        isDriveOperationInProgress = isDriveOperationInProgress,
        driveOperationStatusMessage = driveOperationStatusMessage,
        driveOperationError = driveOperationError,
        hasPendingDriveResolution = hasPendingDriveResolution,
        driveConnectionTestReport = driveConnectionTestReport,
        appSigningInfo = appSigningInfo,
        onSignInWithGoogleForDrive = onSignInWithGoogleForDrive,
        onReconnectGoogleDrive = onReconnectGoogleDrive,
        onRunDriveConnectionTest = onRunDriveConnectionTest,
        onClearDriveConnectionTestReport = onClearDriveConnectionTestReport,
        onSignOutFromDrive = onSignOutFromDrive,
        onBackupNowToDrive = onBackupNowToDrive,
        onRestoreDriveBackup = onRestoreDriveBackup,
        onDeleteDriveBackup = onDeleteDriveBackup,
        onDeleteAllDriveBackups = onDeleteAllDriveBackups,
        onUpdateAutoBackupSettings = onUpdateAutoBackupSettings,
        onRefreshDriveBackups = onRefreshDriveBackups,
        onClearDriveOperationError = onClearDriveOperationError,
        dbEncryptionState = dbEncryptionState,
        onRetryDatabaseEncryption = onRetryDatabaseEncryption,
        onExportLocalUdhaarSaf = onExportLocalUdhaarSaf,
        onRetryCloudBackup = onRetryCloudBackup,
        onOpenContactPicker = onOpenContactPicker,
        onSaveReminderSettings = onSaveReminderSettings,
        onDismiss = onDismiss
    )
}
