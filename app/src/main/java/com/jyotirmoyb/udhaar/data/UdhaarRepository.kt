package com.jyotirmoyb.udhaar.data

import android.content.Context
import com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode
import com.jyotirmoyb.udhaar.backup.DriveServiceHelper
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.data.repository.AttachmentRepository
import com.jyotirmoyb.udhaar.data.repository.BackupRepository
import com.jyotirmoyb.udhaar.data.repository.ChatRepository
import com.jyotirmoyb.udhaar.data.repository.InstallmentRepository
import com.jyotirmoyb.udhaar.data.repository.LedgerRepository
import com.jyotirmoyb.udhaar.data.repository.PartyRepository
import com.jyotirmoyb.udhaar.data.repository.PaymentRepository
import com.jyotirmoyb.udhaar.data.repository.SettingsRepository
import com.jyotirmoyb.udhaar.data.repository.VerificationRepository
import com.jyotirmoyb.udhaar.security.PinSecurityManager
import com.jyotirmoyb.udhaar.security.PinVerificationResult
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.util.ParsedLedgerBackup
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

open class UdhaarRepository(
    val ledgerRepository: LedgerRepository,
    val partyRepository: PartyRepository,
    val paymentRepository: PaymentRepository,
    val installmentRepository: InstallmentRepository,
    val attachmentRepository: AttachmentRepository,
    val verificationRepository: VerificationRepository,
    val chatRepository: ChatRepository,
    val backupRepository: BackupRepository,
    val settingsRepository: SettingsRepository,
    private val dao: UdhaarDao
) {
    constructor(
        dao: UdhaarDao,
        context: Context? = null,
        cloudRepositoryOverride: VerificationCloudRepository? = null,
        driveHelperOverride: DriveServiceHelper? = null
    ) : this(
        dao = dao,
        context = context,
        cloudRepositoryOverride = cloudRepositoryOverride,
        driveHelperOverride = driveHelperOverride,
        sharedSettingsRepository = SettingsRepository(context, cloudRepositoryOverride),
        sharedAttachmentRepository = AttachmentRepository(dao, context),
        sharedInstallmentRepository = InstallmentRepository(dao, SettingsRepository(context, cloudRepositoryOverride), cloudRepositoryOverride)
    )

    private constructor(
        dao: UdhaarDao,
        context: Context?,
        cloudRepositoryOverride: VerificationCloudRepository?,
        driveHelperOverride: DriveServiceHelper?,
        sharedSettingsRepository: SettingsRepository,
        sharedAttachmentRepository: AttachmentRepository,
        sharedInstallmentRepository: InstallmentRepository
    ) : this(
        ledgerRepository = LedgerRepository(
            dao = dao,
            settingsRepository = sharedSettingsRepository,
            cloudRepository = cloudRepositoryOverride,
            context = context
        ),
        partyRepository = PartyRepository(dao),
        paymentRepository = PaymentRepository(
            dao = dao,
            settingsRepository = sharedSettingsRepository,
            cloudRepository = cloudRepositoryOverride,
            installmentRepository = sharedInstallmentRepository
        ),
        installmentRepository = sharedInstallmentRepository,
        attachmentRepository = sharedAttachmentRepository,
        verificationRepository = VerificationRepository(
            dao = dao,
            settingsRepository = sharedSettingsRepository,
            cloudRepository = cloudRepositoryOverride,
            context = context
        ),
        chatRepository = ChatRepository(
            dao = dao,
            settingsRepository = sharedSettingsRepository,
            cloudRepository = cloudRepositoryOverride,
            context = context
        ),
        backupRepository = BackupRepository(
            dao = dao,
            settingsRepository = sharedSettingsRepository,
            attachmentRepository = sharedAttachmentRepository,
            driveServiceHelper = driveHelperOverride ?: (context?.let { DriveServiceHelper(it) } ?: DriveServiceHelper()),
            context = context
        ),
        settingsRepository = sharedSettingsRepository,
        dao = dao
    )

    fun getDao(): UdhaarDao = dao

    val pinSecurityManager: PinSecurityManager?
        get() = settingsRepository.pinSecurityManager

    val allParties: Flow<List<PartyEntity>>
        get() = partyRepository.allParties

    val allTransactions: Flow<List<UdhaarTransaction>>
        get() = ledgerRepository.allTransactions

    val allPartialPayments: Flow<List<PartialPayment>>
        get() = paymentRepository.allPartialPayments

    val allAuditTrailEntries: Flow<List<AuditTrailEntry>>
        get() = ledgerRepository.allAuditTrailEntries

    val allChatThreads: Flow<List<ChatThreadMetadata>>
        get() = chatRepository.allChatThreads

    val allChatMessages: Flow<List<ChatMessageEntity>>
        get() = chatRepository.allChatMessages

    val allAttachments: Flow<List<Attachment>>
        get() = attachmentRepository.allAttachments

    open val userSettings: Flow<UserSettings>
        get() = settingsRepository.userSettings

    // --- Party Operations ---
    suspend fun saveParty(party: PartyEntity): Long = partyRepository.saveParty(party)
    suspend fun saveParties(parties: List<PartyEntity>): List<Long> = partyRepository.saveParties(parties)
    suspend fun togglePartyFavorite(partyId: Long, isFavorite: Boolean) = partyRepository.togglePartyFavorite(partyId, isFavorite)
    suspend fun updatePartyLastInteraction(partyId: Long, millis: Long = System.currentTimeMillis()) = partyRepository.updatePartyLastInteraction(partyId, millis)
    suspend fun updatePartyLanguage(partyId: Long, language: String) = partyRepository.updatePartyLanguage(partyId, language)
    suspend fun updatePartyQuietHoursOverride(partyId: Long, override: Boolean) = partyRepository.updatePartyQuietHoursOverride(partyId, override)
    suspend fun mergeParties(primaryParty: PartyEntity, secondaryParty: PartyEntity): PartyEntity = partyRepository.mergeParties(primaryParty, secondaryParty)
    suspend fun deleteParty(party: PartyEntity) = partyRepository.deleteParty(party)

    // --- Transaction & Reminder Operations ---
    suspend fun snoozeTransaction(recordId: Long, days: Int) = ledgerRepository.snoozeTransaction(recordId, days)
    suspend fun clearTransactionSnooze(recordId: Long) = ledgerRepository.clearTransactionSnooze(recordId)
    suspend fun stopTransactionReminders(recordId: Long) = ledgerRepository.stopTransactionReminders(recordId)
    suspend fun enableTransactionReminders(recordId: Long) = ledgerRepository.enableTransactionReminders(recordId)
    suspend fun recordReminderEvent(recordId: Long, level: Int, channel: ReminderChannel) = ledgerRepository.recordReminderEvent(recordId, level, channel)
    fun getReminderEvents(recordId: Long): Flow<List<ReminderEvent>> = ledgerRepository.getReminderEvents(recordId)

    suspend fun updateReminderSettings(
        quietHoursEnabled: Boolean,
        quietHoursStartHour: Int,
        quietHoursEndHour: Int,
        reminderLevel1DaysBefore: Int,
        reminderLevel2MaxOverdueDays: Int,
        reminderLevel3MaxOverdueDays: Int
    ) = settingsRepository.updateReminderSettings(
        quietHoursEnabled,
        quietHoursStartHour,
        quietHoursEndHour,
        reminderLevel1DaysBefore,
        reminderLevel2MaxOverdueDays,
        reminderLevel3MaxOverdueDays
    )

    fun getPartialPayments(transactionId: Long): Flow<List<PartialPayment>> = paymentRepository.getPartialPayments(transactionId)
    fun getAuditTrail(transactionId: Long): Flow<List<AuditTrailEntry>> = ledgerRepository.getAuditTrail(transactionId)
    suspend fun getTransactionById(id: Long): UdhaarTransaction? = ledgerRepository.getTransactionById(id)
    suspend fun getTransactionByRemoteId(remoteId: String): UdhaarTransaction? = ledgerRepository.getTransactionByRemoteId(remoteId)
    suspend fun saveTransaction(transaction: UdhaarTransaction, myProfileName: String = "You"): Long = ledgerRepository.saveTransaction(transaction, myProfileName)
    suspend fun updateTransaction(transaction: UdhaarTransaction) = ledgerRepository.updateTransaction(transaction)
    suspend fun deleteTransaction(transaction: UdhaarTransaction) = ledgerRepository.deleteTransaction(transaction)

    suspend fun recordPartialPayment(
        transaction: UdhaarTransaction,
        paymentAmount: Double,
        paymentMode: PaymentMode = PaymentMode.CASH,
        note: String,
        totalAccruedAmount: Double,
        actorName: String = "You",
        proofAttachment: Attachment? = null
    ): Long = paymentRepository.recordPartialPayment(
        transaction,
        paymentAmount,
        paymentMode,
        note,
        totalAccruedAmount,
        actorName,
        proofAttachment
    )

    suspend fun acknowledgePartialPayment(
        transaction: UdhaarTransaction,
        payment: PartialPayment,
        actorName: String = "You"
    ) = paymentRepository.acknowledgePartialPayment(transaction, payment, actorName)

    suspend fun deletePartialPayment(
        transaction: UdhaarTransaction,
        payment: PartialPayment,
        totalAccruedAmount: Double
    ) = paymentRepository.deletePartialPayment(transaction, payment, totalAccruedAmount)

    suspend fun toggleFullSettlement(transaction: UdhaarTransaction, totalAccruedAmount: Double) =
        ledgerRepository.toggleFullSettlement(transaction, totalAccruedAmount)

    // --- Two-Party Verification Operations ---
    suspend fun prepareVerificationShare(transaction: UdhaarTransaction): Pair<UdhaarTransaction, String> =
        verificationRepository.prepareVerificationShare(transaction)

    suspend fun processIncomingVerificationResponse(
        payload: VerificationCryptoHelper.SharedVerificationPayload,
        confirm: Boolean,
        disputeReason: String = ""
    ): UdhaarTransaction = verificationRepository.processIncomingVerificationResponse(payload, confirm, disputeReason)

    suspend fun confirmOrDisputeExistingTransaction(
        transaction: UdhaarTransaction,
        confirm: Boolean,
        disputeReason: String = ""
    ) = verificationRepository.confirmOrDisputeExistingTransaction(transaction, confirm, disputeReason)

    suspend fun acceptSharedPaymentProfile(
        profile: VerificationCryptoHelper.SharedPaymentProfilePayload,
        targetPartyId: Long? = null
    ): PartyEntity = verificationRepository.acceptSharedPaymentProfile(profile, targetPartyId)

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
    ) = settingsRepository.updateSettings(
        upiId,
        payeeName,
        defaultCurrency,
        inrEnabled,
        nprEnabled,
        themeMode,
        fontScale,
        biometricLockEnabled,
        securityPin,
        phoneNumber,
        profilePhotoUri,
        cloudVerificationConsent
    )

    suspend fun completeOnboarding(
        securityPin: String? = null,
        upiId: String? = null,
        payeeName: String? = null
    ) = settingsRepository.completeOnboarding(securityPin, upiId, payeeName)

    suspend fun setCloudAccountState(
        consentGranted: Boolean,
        userId: String,
        email: String
    ) = settingsRepository.setCloudAccountState(consentGranted, userId, email)

    suspend fun syncProfileAndRecordsToCloud(userIdOverride: String? = null): Result<Long> =
        verificationRepository.syncProfileAndRecordsToCloud(userIdOverride)

    suspend fun deleteMyCloudData() =
        verificationRepository.deleteMyCloudData()

    // --- Chat Operations ---
    companion object {
        fun transactionThreadKey(transactionId: Long): String =
            ChatRepository.transactionThreadKey(transactionId)

        fun partyGeneralThreadKey(partyName: String): String =
            ChatRepository.partyGeneralThreadKey(partyName)

        fun cloudThreadIdFor(
            scope: ChatThreadScope,
            remoteId: String,
            partyName: String,
            fallbackThreadKey: String
        ): String = ChatRepository.cloudThreadIdFor(scope, remoteId, partyName, fallbackThreadKey)
    }

    suspend fun getOrCreateThreadMetadata(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String
    ): ChatThreadMetadata = chatRepository.getOrCreateThreadMetadata(
        threadKey, scope, transactionId, remoteId, partyId, partyName
    )

    fun buildTransactionThreadKey(transaction: UdhaarTransaction): String =
        chatRepository.buildTransactionThreadKey(transaction)

    suspend fun sendChatMessage(
        threadKey: String,
        scope: ChatThreadScope,
        transaction: UdhaarTransaction? = null,
        transactionId: Long? = null,
        partyId: Long? = null,
        partyName: String,
        messageType: ChatMessageType = ChatMessageType.TEXT,
        textContent: String,
        attachmentUri: String = "",
        attachmentMimeType: String = "",
        proofAttachmentUri: String = "",
        proofMimeType: String = "",
        proofFileName: String = "",
        fromCounterparty: Boolean = false
    ): ChatMessageEntity? = chatRepository.sendChatMessage(
        threadKey, scope, transaction, transactionId, partyId, partyName,
        messageType, textContent, attachmentUri, attachmentMimeType,
        proofAttachmentUri, proofMimeType, proofFileName, fromCounterparty
    )

    suspend fun requestDueDateExtension(
        transaction: UdhaarTransaction,
        proposedDueDateMillis: Long,
        note: String = "",
        reasonText: String = "",
        requestedByCounterparty: Boolean = false
    ): ChatMessageEntity = chatRepository.requestDueDateExtension(
        transaction, proposedDueDateMillis, note, reasonText, requestedByCounterparty
    )

    suspend fun respondToExtensionRequest(
        transaction: UdhaarTransaction? = null,
        message: ChatMessageEntity,
        accept: Boolean,
        responderName: String = "You"
    ): UdhaarTransaction = chatRepository.respondToExtensionRequest(
        transaction, message, accept, responderName
    )

    suspend fun markThreadRead(threadKey: String) = chatRepository.markThreadRead(threadKey)

    suspend fun setTypingIndicator(
        threadKey: String,
        scope: ChatThreadScope = ChatThreadScope.TRANSACTION,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String = "",
        isCounterpartyTyping: Boolean
    ) = chatRepository.setTypingIndicator(
        threadKey, scope, transactionId, remoteId, partyId, partyName, isCounterpartyTyping
    )

    suspend fun toggleThreadMute(
        threadKey: String,
        isMuted: Boolean? = null,
        scope: ChatThreadScope = ChatThreadScope.TRANSACTION,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String = ""
    ): ChatThreadMetadata = chatRepository.toggleThreadMute(
        threadKey, isMuted, scope, transactionId, remoteId, partyId, partyName
    )

    suspend fun toggleThreadBlock(
        threadKey: String,
        isBlocked: Boolean? = null,
        scope: ChatThreadScope = ChatThreadScope.TRANSACTION,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String = ""
    ): ChatThreadMetadata = chatRepository.toggleThreadBlock(
        threadKey, isBlocked, scope, transactionId, remoteId, partyId, partyName
    )

    suspend fun reportAbusiveUserInThread(
        threadKey: String,
        reason: String,
        scope: ChatThreadScope = ChatThreadScope.TRANSACTION,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String = ""
    ): ChatThreadMetadata = chatRepository.reportAbusiveUserInThread(
        threadKey, reason, scope, transactionId, remoteId, partyId, partyName
    )

    // --- PIN & Currency Operations ---
    fun verifyPin(candidatePin: String): PinVerificationResult = settingsRepository.verifyPin(candidatePin)
    fun resetPinFailedAttempts() = settingsRepository.resetPinFailedAttempts()
    fun getPinLockoutRemainingMillis(): Long = settingsRepository.getPinLockoutRemainingMillis()
    fun getPinFailedAttempts(): Int = settingsRepository.getPinFailedAttempts()
    suspend fun setCurrencyEnabled(currency: CurrencyType, enabled: Boolean) = settingsRepository.setCurrencyEnabled(currency, enabled)
    suspend fun setThemeMode(themeMode: AppThemeMode) = settingsRepository.setThemeMode(themeMode)
    suspend fun setFontScale(fontScale: AppFontScale) = settingsRepository.setFontScale(fontScale)

    // --- Backup & Attachments Operations ---
    suspend fun getFullLedgerSnapshot(): Triple<List<PartyEntity>, List<UdhaarTransaction>, List<PartialPayment>> =
        backupRepository.getFullLedgerSnapshot()

    suspend fun importLedgerData(
        parties: List<PartyEntity>,
        transactions: List<UdhaarTransaction>,
        partialPayments: List<PartialPayment>,
        attachments: List<Attachment> = emptyList()
    ): Int = backupRepository.importLedgerData(parties, transactions, partialPayments, attachments)

    fun getAttachmentsForTransaction(transactionId: Long): Flow<List<Attachment>> =
        attachmentRepository.getAttachmentsForTransaction(transactionId)

    fun getAttachmentsForPayment(paymentId: Long): Flow<List<Attachment>> =
        attachmentRepository.getAttachmentsForPayment(paymentId)

    suspend fun insertAttachment(attachment: Attachment): Long = attachmentRepository.insertAttachment(attachment)
    suspend fun deleteAttachment(attachment: Attachment) = attachmentRepository.deleteAttachment(attachment)
    suspend fun deleteAttachmentById(attachmentId: Long) = attachmentRepository.deleteAttachmentById(attachmentId)

    open suspend fun getFullLedgerBackupSnapshot(): FullLedgerSnapshot = backupRepository.getFullLedgerBackupSnapshot()
    open fun getLocalAttachmentsMap(): Map<String, String> = attachmentRepository.getLocalAttachmentsMap()
    open fun getAutoBackupPassphrase(): String? = backupRepository.getAutoBackupPassphrase()
    open suspend fun updateDriveAccount(email: String) = backupRepository.updateDriveAccount(email)
    open suspend fun clearDriveAccount() = backupRepository.clearDriveAccount()
    open suspend fun updateDriveLastBackup(timeMillis: Long, sizeBytes: Long) = backupRepository.updateDriveLastBackup(timeMillis, sizeBytes)
    open suspend fun clearDriveBackupMetadata() = backupRepository.clearDriveBackupMetadata()
    open suspend fun recordDriveBackupSuccess(timeMillis: Long, sizeBytes: Long) = backupRepository.recordDriveBackupSuccess(timeMillis, sizeBytes)

    open suspend fun recordDriveBackupFailure(
        errorCode: DriveBackupErrorCode,
        errorMessage: String = "",
        timeMillis: Long = System.currentTimeMillis()
    ): Int = backupRepository.recordDriveBackupFailure(errorCode, errorMessage, timeMillis)

    open suspend fun setDriveNeedsReconnect(needsReconnect: Boolean, message: String = "") =
        backupRepository.setDriveNeedsReconnect(needsReconnect, message)

    open fun saveAutoBackupPassphrase(passphrase: String) = backupRepository.saveAutoBackupPassphrase(passphrase)

    open suspend fun updateDriveAutoBackupSettings(
        frequency: AutoBackupFrequency,
        wifiOnly: Boolean,
        passphraseToSave: String? = null
    ) = backupRepository.updateDriveAutoBackupSettings(frequency, wifiOnly, passphraseToSave)

    suspend fun restoreFullLedgerBackup(
        backup: ParsedLedgerBackup,
        replaceExisting: Boolean
    ): Int = backupRepository.restoreFullLedgerBackup(backup, replaceExisting)

    // --- Installment Plans ---
    val allInstallmentPlans: Flow<List<com.jyotirmoyb.udhaar.data.InstallmentPlan>> =
        installmentRepository.allInstallmentPlans

    val allInstallments: Flow<List<com.jyotirmoyb.udhaar.data.Installment>> =
        installmentRepository.allInstallments

    fun getInstallmentPlanForTransaction(txId: Long): Flow<com.jyotirmoyb.udhaar.data.InstallmentPlan?> =
        installmentRepository.getInstallmentPlanForTransaction(txId)

    fun getInstallmentsForPlan(planId: Long): Flow<List<com.jyotirmoyb.udhaar.data.Installment>> =
        installmentRepository.getInstallmentsForPlan(planId)

    suspend fun createOrUpdateInstallmentPlan(
        tx: com.jyotirmoyb.udhaar.data.UdhaarTransaction,
        plan: com.jyotirmoyb.udhaar.data.InstallmentPlan,
        draftInstallments: List<com.jyotirmoyb.udhaar.util.InstallmentDraft>,
        myProfileName: String = "You"
    ): Long = installmentRepository.createOrUpdatePlan(tx, plan, draftInstallments, myProfileName)

    suspend fun proposeNewInstallmentSchedule(
        planId: Long,
        txId: Long,
        proposals: List<com.jyotirmoyb.udhaar.util.InstallmentDraft>,
        proposerName: String
    ) = installmentRepository.proposeNewSchedule(planId, txId, proposals, proposerName)

    suspend fun acceptProposedInstallmentSchedule(
        planId: Long,
        tx: com.jyotirmoyb.udhaar.data.UdhaarTransaction,
        actorName: String
    ) = installmentRepository.acceptProposedSchedule(planId, tx, actorName)

    suspend fun declineProposedInstallmentSchedule(
        planId: Long,
        txId: Long,
        actorName: String
    ) = installmentRepository.declineProposedSchedule(planId, txId, actorName)
}
