package com.jyotirmoyb.udhaar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jyotirmoyb.udhaar.util.ParsedLedgerBackup
import com.jyotirmoyb.udhaar.data.cloud.CloudAuditEvent
import com.jyotirmoyb.udhaar.data.cloud.CloudChatMessage
import com.jyotirmoyb.udhaar.data.cloud.CloudChatThread
import com.jyotirmoyb.udhaar.data.cloud.CloudPartialPayment
import com.jyotirmoyb.udhaar.data.cloud.CloudSharedRecord
import com.jyotirmoyb.udhaar.data.cloud.CloudUserProfile
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.notification.ChatNotificationHelper
import com.jyotirmoyb.udhaar.security.PinSecurityManager
import com.jyotirmoyb.udhaar.security.PinVerificationResult
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "udhaar_settings")

class UdhaarRepository(
    private val dao: UdhaarDao,
    private val context: Context? = null,
    private val cloudRepositoryOverride: VerificationCloudRepository? = null
) {
    val pinSecurityManager: PinSecurityManager? = context?.let { PinSecurityManager(it) }

    private val cloudRepository: VerificationCloudRepository? by lazy {
        cloudRepositoryOverride ?: context?.let {
            runCatching { VerificationCloudRepository(it) }.getOrNull()
        }
    }

    private object PrefKeys {
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
        val DRIVE_USER_EMAIL = stringPreferencesKey("drive_user_email")
        val DRIVE_ACCESS_TOKEN = stringPreferencesKey("drive_access_token")
        val DRIVE_LAST_BACKUP_TIME = longPreferencesKey("drive_last_backup_time")
        val DRIVE_LAST_BACKUP_SIZE = longPreferencesKey("drive_last_backup_size")
        val DRIVE_AUTO_BACKUP_FREQ = stringPreferencesKey("drive_auto_backup_freq")
        val DRIVE_AUTO_BACKUP_WIFI = booleanPreferencesKey("drive_auto_backup_wifi")
        val DRIVE_AUTO_BACKUP_KEY_SAVED = booleanPreferencesKey("drive_auto_backup_key_saved")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
    }

    val allParties: Flow<List<PartyEntity>> = dao.getAllParties()
    val allTransactions: Flow<List<UdhaarTransaction>> = dao.getAllTransactions()
    val allPartialPayments: Flow<List<PartialPayment>> = dao.getAllPartialPayments()
    val allAuditTrailEntries: Flow<List<AuditTrailEntry>> = dao.getAllAuditTrailEntries()
    val allChatThreads: Flow<List<ChatThreadMetadata>> = dao.getAllChatThreads()
    val allChatMessages: Flow<List<ChatMessageEntity>> = dao.getAllChatMessages()
    val allAttachments: Flow<List<Attachment>> = dao.getAllAttachmentsFlow()

    val userSettings: Flow<UserSettings> = context?.dataStore?.data
        ?.catch { emit(emptyPreferences()) }
        ?.map { prefs ->
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

            // Migrate any custom legacy PIN (ignoring the old insecure "0000" default)
            val legacyPin = prefs[PrefKeys.LEGACY_SECURITY_PIN]?.trim().orEmpty()
            if (legacyPin.length in 4..8 && legacyPin != "0000" && pinSecurityManager?.hasPinConfigured() == false) {
                pinSecurityManager.setPin(legacyPin)
            }

            val hasPin = pinSecurityManager?.hasPinConfigured() ?: false
            val lockRequested = prefs[PrefKeys.BIOMETRIC_LOCK_ENABLED] ?: false

            val driveAutoFreqStr = prefs[PrefKeys.DRIVE_AUTO_BACKUP_FREQ] ?: AutoBackupFrequency.OFF.name
            val driveAutoFreq = runCatching { AutoBackupFrequency.valueOf(driveAutoFreqStr) }.getOrDefault(AutoBackupFrequency.OFF)

            UserSettings(
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
                driveUserEmail = prefs[PrefKeys.DRIVE_USER_EMAIL] ?: "",
                driveAccessToken = prefs[PrefKeys.DRIVE_ACCESS_TOKEN] ?: "",
                driveLastBackupTimeMillis = prefs[PrefKeys.DRIVE_LAST_BACKUP_TIME] ?: 0L,
                driveLastBackupSizeBytes = prefs[PrefKeys.DRIVE_LAST_BACKUP_SIZE] ?: 0L,
                driveAutoBackupFrequency = driveAutoFreq,
                driveAutoBackupWifiOnly = prefs[PrefKeys.DRIVE_AUTO_BACKUP_WIFI] ?: true,
                driveAutoBackupPassphraseSaved = prefs[PrefKeys.DRIVE_AUTO_BACKUP_KEY_SAVED] ?: false,
                hasCompletedOnboarding = prefs[PrefKeys.HAS_COMPLETED_ONBOARDING] ?: false
            )
        } ?: kotlinx.coroutines.flow.flowOf(UserSettings())

    // --- Party Directory Operations ---
    suspend fun saveParty(party: PartyEntity): Long {
        val formattedPhone = InterestCalculator.formatPhoneWithCountryCode(
            party.phoneNumber,
            party.preferredCurrency
        )
        val formattedAltPhone = if (party.alternatePhoneNumber.isNotBlank()) {
            InterestCalculator.formatPhoneWithCountryCode(
                party.alternatePhoneNumber,
                party.preferredCurrency
            )
        } else {
            ""
        }
        return dao.insertParty(
            party.copy(
                name = party.name.trim(),
                phoneNumber = formattedPhone,
                alternatePhoneNumber = formattedAltPhone,
                upiId = party.upiId.trim(),
                email = party.email.trim(),
                tagOrNotes = party.tagOrNotes.trim(),
                notes = party.notes.trim(),
                photoUri = party.photoUri.trim(),
                linkedUserId = party.linkedUserId.trim(),
                isFavorite = party.isFavorite,
                lastInteractionMillis = if (party.lastInteractionMillis > 0L) party.lastInteractionMillis else System.currentTimeMillis()
            )
        )
    }

    suspend fun saveParties(parties: List<PartyEntity>): List<Long> {
        val mapped = parties.map { p ->
            val formattedPhone = InterestCalculator.formatPhoneWithCountryCode(p.phoneNumber, p.preferredCurrency)
            val formattedAltPhone = if (p.alternatePhoneNumber.isNotBlank()) {
                InterestCalculator.formatPhoneWithCountryCode(p.alternatePhoneNumber, p.preferredCurrency)
            } else ""
            p.copy(
                name = p.name.trim(),
                phoneNumber = formattedPhone,
                alternatePhoneNumber = formattedAltPhone,
                upiId = p.upiId.trim(),
                email = p.email.trim(),
                tagOrNotes = p.tagOrNotes.trim(),
                notes = p.notes.trim(),
                photoUri = p.photoUri.trim(),
                linkedUserId = p.linkedUserId.trim()
            )
        }
        return dao.insertParties(mapped)
    }

    suspend fun togglePartyFavorite(partyId: Long, isFavorite: Boolean) {
        dao.setPartyFavorite(partyId, isFavorite)
    }

    suspend fun updatePartyLastInteraction(partyId: Long, millis: Long = System.currentTimeMillis()) {
        dao.updatePartyLastInteraction(partyId, millis)
    }

    /**
     * Merges a source party into a primary (target) party.
     * Re-links all transactions, chat threads, and chat messages to targetParty.
     * Keeps target party's non-empty fields or populates empty ones from source party.
     * Deletes sourceParty from the database.
     */
    suspend fun mergeParties(primaryParty: PartyEntity, secondaryParty: PartyEntity): PartyEntity {
        val merged = primaryParty.copy(
            phoneNumber = primaryParty.phoneNumber.ifBlank { secondaryParty.phoneNumber },
            alternatePhoneNumber = primaryParty.alternatePhoneNumber.ifBlank {
                if (secondaryParty.phoneNumber != primaryParty.phoneNumber) secondaryParty.phoneNumber
                else secondaryParty.alternatePhoneNumber
            },
            upiId = primaryParty.upiId.ifBlank { secondaryParty.upiId },
            email = primaryParty.email.ifBlank { secondaryParty.email },
            tagOrNotes = primaryParty.tagOrNotes.ifBlank { secondaryParty.tagOrNotes },
            notes = if (primaryParty.notes.isNotBlank() && secondaryParty.notes.isNotBlank() && primaryParty.notes != secondaryParty.notes) {
                "${primaryParty.notes}\n[Merged]: ${secondaryParty.notes}"
            } else {
                primaryParty.notes.ifBlank { secondaryParty.notes }
            },
            photoUri = primaryParty.photoUri.ifBlank { secondaryParty.photoUri },
            linkedUserId = primaryParty.linkedUserId.ifBlank { secondaryParty.linkedUserId },
            isFavorite = primaryParty.isFavorite || secondaryParty.isFavorite,
            lastInteractionMillis = maxOf(primaryParty.lastInteractionMillis, secondaryParty.lastInteractionMillis)
        )
        dao.insertParty(merged)

        // Relink records from secondary party to primary party
        if (secondaryParty.partyId > 0L && primaryParty.partyId > 0L) {
            dao.relinkTransactionsForPartyId(secondaryParty.partyId, primaryParty.partyId, primaryParty.name)
            dao.relinkChatMessagesForPartyId(secondaryParty.partyId, primaryParty.partyId, primaryParty.name)
            dao.relinkChatThreadsForPartyId(secondaryParty.partyId, primaryParty.partyId, primaryParty.name)
            dao.deletePartyById(secondaryParty.partyId)
        }
        return merged
    }

    suspend fun deleteParty(party: PartyEntity) {
        dao.deleteParty(party)
    }

    // --- Transaction & Audit Operations ---
    fun getPartialPayments(transactionId: Long): Flow<List<PartialPayment>> =
        dao.getPartialPaymentsForTransaction(transactionId)

    fun getAuditTrail(transactionId: Long): Flow<List<AuditTrailEntry>> =
        dao.getAuditTrailForTransaction(transactionId)

    suspend fun getTransactionById(id: Long): UdhaarTransaction? =
        dao.getTransactionById(id)

    suspend fun getTransactionByRemoteId(remoteId: String): UdhaarTransaction? =
        dao.getTransactionByRemoteId(remoteId)

    suspend fun saveTransaction(
        transaction: UdhaarTransaction,
        myProfileName: String = "You"
    ): Long {
        val formattedPhone = InterestCalculator.formatPhoneWithCountryCode(
            transaction.phoneNumber,
            transaction.currency
        )
        val linkedPartyId = if (transaction.partyId != null && transaction.partyId > 0L) {
            transaction.partyId
        } else {
            val existingParty = dao.getPartyByName(transaction.partyName.trim())
            if (existingParty != null) {
                if (existingParty.phoneNumber.isBlank() && formattedPhone.isNotBlank()) {
                    dao.updateParty(existingParty.copy(phoneNumber = formattedPhone))
                }
                existingParty.partyId
            } else {
                dao.insertParty(
                    PartyEntity(
                        name = transaction.partyName.trim(),
                        phoneNumber = formattedPhone,
                        preferredCurrency = transaction.currency
                    )
                )
            }
        }

        val existingTx = if (transaction.id > 0L) dao.getTransactionById(transaction.id) else null
        val effectiveRemoteId = transaction.remoteId.ifBlank {
            existingTx?.remoteId?.ifBlank { UUID.randomUUID().toString() } ?: UUID.randomUUID().toString()
        }
        val effectiveShareToken = transaction.shareToken.ifBlank {
            existingTx?.shareToken?.ifBlank { UUID.randomUUID().toString().replace("-", "") }
                ?: UUID.randomUUID().toString().replace("-", "")
        }

        val (lenderName, borrowerName) = VerificationCryptoHelper.resolveLenderAndBorrowerNames(
            type = transaction.transactionType,
            partyName = transaction.partyName,
            myProfileName = myProfileName,
            explicitLenderName = transaction.lenderName.ifBlank { existingTx?.lenderName.orEmpty() },
            explicitBorrowerName = transaction.borrowerName.ifBlank { existingTx?.borrowerName.orEmpty() }
        )

        val computedHash = VerificationCryptoHelper.computeContentHash(
            lenderName = lenderName,
            borrowerName = borrowerName,
            principalAmount = transaction.principalAmount,
            currencyCode = transaction.currency.code,
            startDateMillis = transaction.startDateMillis,
            expiryDateMillis = transaction.expiryDateMillis,
            isInterestEnabled = transaction.isInterestEnabled,
            interestType = transaction.interestType,
            interestRate = transaction.interestRate,
            interestRatePeriod = transaction.interestRatePeriod,
            compoundFrequency = transaction.compoundFrequency
        )

        val termsChanged = existingTx != null && existingTx.contentHash.isNotBlank() &&
            existingTx.contentHash != computedHash

        val nextVerificationStatus = when {
            existingTx == null -> transaction.verificationStatus
            termsChanged && (
                existingTx.verificationStatus == VerificationStatus.CONFIRMED ||
                    existingTx.verificationStatus == VerificationStatus.DISPUTED
                ) -> VerificationStatus.EDITED_AFTER_CONFIRM
            else -> transaction.verificationStatus
        }

        val preparedTx = transaction.copy(
            partyId = linkedPartyId,
            partyName = transaction.partyName.trim(),
            phoneNumber = formattedPhone,
            remoteId = effectiveRemoteId,
            shareToken = effectiveShareToken,
            lenderName = lenderName,
            borrowerName = borrowerName,
            contentHash = computedHash,
            verificationStatus = nextVerificationStatus,
            needsMyConfirmation = if (termsChanged) false else transaction.needsMyConfirmation
        )

        val savedId = dao.insertTransaction(preparedTx)
        val actorDisplay = myProfileName.trim().ifEmpty { "You" }

        if (existingTx == null) {
            appendLocalAuditEvent(
                transactionId = savedId,
                eventType = AuditEventType.CREATED,
                actorName = actorDisplay,
                summaryText = "$actorDisplay created ${preparedTx.transactionType.label} record of ${preparedTx.currency.symbol}${InterestCalculator.formatAmount(preparedTx.principalAmount)} with ${preparedTx.partyName}",
                contentHashSnapshot = computedHash
            )
        } else if (termsChanged) {
            appendLocalAuditEvent(
                transactionId = savedId,
                eventType = AuditEventType.EDITED,
                actorName = actorDisplay,
                summaryText = "$actorDisplay edited record terms (new hash ${computedHash.take(8)}…). Re-confirmation required.",
                contentHashSnapshot = computedHash
            )
            syncEditedTermsToCloudIfEnabled(preparedTx.copy(id = savedId), actorDisplay)
        }

        return savedId
    }

    suspend fun updateTransaction(transaction: UdhaarTransaction) =
        dao.updateTransaction(transaction)

    suspend fun deleteTransaction(transaction: UdhaarTransaction) =
        dao.deleteTransaction(transaction)

    suspend fun recordPartialPayment(
        transaction: UdhaarTransaction,
        paymentAmount: Double,
        paymentMode: PaymentMode = PaymentMode.CASH,
        note: String,
        totalAccruedAmount: Double,
        actorName: String = "You",
        proofAttachment: Attachment? = null
    ): Long {
        val cleanPayment = paymentAmount.coerceAtLeast(0.0)
        if (cleanPayment <= 0.0) return -1L

        val settings = userSettings.first()
        val effectiveActor = actorName.trim().ifEmpty { settings.payeeName.ifBlank { "You" } }
        val remotePaymentId = "pay_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val nowMillis = System.currentTimeMillis()
        val isSharedRecord = transaction.verificationStatus != VerificationStatus.LOCAL_ONLY

        val insertedPaymentId = dao.insertPartialPayment(
            PartialPayment(
                transactionId = transaction.id,
                amount = cleanPayment,
                paymentMode = paymentMode,
                paidAtMillis = nowMillis,
                note = note.trim(),
                remotePaymentId = remotePaymentId,
                recordedByUserId = settings.cloudUserId,
                recordedByName = effectiveActor,
                isAcknowledged = !isSharedRecord,
                acknowledgedAtMillis = if (!isSharedRecord) nowMillis else null,
                needsMyAcknowledgement = false
            )
        )

        if (proofAttachment != null) {
            dao.insertAttachment(
                proofAttachment.copy(
                    id = 0L,
                    transactionId = transaction.id,
                    paymentId = insertedPaymentId
                )
            )
        }

        val updatedPaid = transaction.amountPaid + cleanPayment
        val nowSettled = updatedPaid >= (totalAccruedAmount - 0.01)
        val updatedTx = transaction.copy(
            amountPaid = updatedPaid,
            isSettled = nowSettled,
            settledAtMillis = if (nowSettled) nowMillis else transaction.settledAtMillis
        )
        dao.updateTransaction(updatedTx)

        val formattedAmt = "${transaction.currency.symbol}${InterestCalculator.formatAmount(cleanPayment)}"
        appendLocalAuditEvent(
            transactionId = transaction.id,
            eventType = AuditEventType.PAYMENT_RECORDED,
            actorUserId = settings.cloudUserId,
            actorName = effectiveActor,
            summaryText = "$effectiveActor recorded partial payment of $formattedAmt via ${paymentMode.label}" +
                if (isSharedRecord) " (awaiting counterparty acknowledgement)" else "",
            contentHashSnapshot = transaction.contentHash,
            isAcknowledged = !isSharedRecord
        )

        if (nowSettled) {
            appendLocalAuditEvent(
                transactionId = transaction.id,
                eventType = AuditEventType.SETTLED,
                actorUserId = settings.cloudUserId,
                actorName = effectiveActor,
                summaryText = "Record settled in full ($formattedAmt final payment)",
                contentHashSnapshot = transaction.contentHash,
                isAcknowledged = true
            )
        }

        if (settings.cloudVerificationConsent && settings.cloudUserId.isNotBlank() && transaction.remoteId.isNotBlank()) {
            runCatching {
                val participants = listOfNotNull(
                    settings.cloudUserId.takeIf { it.isNotBlank() },
                    transaction.counterpartyUserId.takeIf { it.isNotBlank() }
                ).distinct()
                val encNote = VerificationCryptoHelper.encryptE2eeField(
                    note.trim(),
                    transaction.remoteId,
                    transaction.shareToken
                )
                cloudRepository?.createPartialPayment(
                    CloudPartialPayment(
                        paymentId = remotePaymentId,
                        remoteId = transaction.remoteId,
                        recordedByUid = settings.cloudUserId,
                        recordedByName = effectiveActor,
                        participantUids = participants,
                        amount = cleanPayment,
                        paymentMode = paymentMode.name,
                        paidAtMillis = nowMillis,
                        encryptedNote = encNote,
                        isAcknowledged = false
                    )
                )
                cloudRepository?.updateSharedRecordTermsOrStatus(
                    remoteId = transaction.remoteId,
                    updates = mapOf(
                        "amountPaid" to updatedPaid,
                        "isSettled" to nowSettled
                    )
                )
            }
        }
        return insertedPaymentId
    }

    /**
     * Acknowledges a partial payment ("Bapan marked ₹5,000 received — Confirm?").
     */
    suspend fun acknowledgePartialPayment(
        transaction: UdhaarTransaction,
        payment: PartialPayment,
        actorName: String = "You"
    ) {
        val settings = userSettings.first()
        val effectiveActor = actorName.trim().ifEmpty { settings.payeeName.ifBlank { "You" } }
        val nowMillis = System.currentTimeMillis()

        val updatedPayment = payment.copy(
            isAcknowledged = true,
            acknowledgedAtMillis = nowMillis,
            needsMyAcknowledgement = false
        )
        dao.updatePartialPayment(updatedPayment)

        val formattedAmt = "${transaction.currency.symbol}${InterestCalculator.formatAmount(payment.amount)}"
        appendLocalAuditEvent(
            transactionId = transaction.id,
            eventType = AuditEventType.PAYMENT_ACKNOWLEDGED,
            actorUserId = settings.cloudUserId,
            actorName = effectiveActor,
            summaryText = "$effectiveActor confirmed partial payment of $formattedAmt recorded by ${payment.recordedByName.ifBlank { transaction.partyName }}",
            contentHashSnapshot = transaction.contentHash,
            isAcknowledged = true
        )

        if (settings.cloudVerificationConsent &&
            settings.cloudUserId.isNotBlank() &&
            transaction.remoteId.isNotBlank() &&
            payment.remotePaymentId.isNotBlank()
        ) {
            runCatching {
                cloudRepository?.acknowledgePartialPayment(
                    remoteId = transaction.remoteId,
                    paymentId = payment.remotePaymentId,
                    acknowledgedByUid = settings.cloudUserId,
                    acknowledgedAtMillis = nowMillis
                )
            }
        }
    }

    suspend fun deletePartialPayment(
        transaction: UdhaarTransaction,
        payment: PartialPayment,
        totalAccruedAmount: Double
    ) {
        dao.deletePartialPayment(payment)
        val updatedPaid = (transaction.amountPaid - payment.amount).coerceAtLeast(0.0)
        val nowSettled = updatedPaid >= (totalAccruedAmount - 0.01) && totalAccruedAmount > 0.0
        dao.updateTransaction(
            transaction.copy(
                amountPaid = updatedPaid,
                isSettled = nowSettled,
                settledAtMillis = if (nowSettled) transaction.settledAtMillis else null
            )
        )
    }

    suspend fun toggleFullSettlement(transaction: UdhaarTransaction, totalAccruedAmount: Double) {
        val settings = userSettings.first()
        val actorName = settings.payeeName.ifBlank { "You" }
        if (transaction.isSettled) {
            dao.updateTransaction(
                transaction.copy(
                    isSettled = false,
                    settledAtMillis = null
                )
            )
            appendLocalAuditEvent(
                transactionId = transaction.id,
                eventType = AuditEventType.EDITED,
                actorUserId = settings.cloudUserId,
                actorName = actorName,
                summaryText = "$actorName reopened settled record",
                contentHashSnapshot = transaction.contentHash
            )
        } else {
            val now = System.currentTimeMillis()
            dao.updateTransaction(
                transaction.copy(
                    isSettled = true,
                    settledAtMillis = now
                )
            )
            appendLocalAuditEvent(
                transactionId = transaction.id,
                eventType = AuditEventType.SETTLED,
                actorUserId = settings.cloudUserId,
                actorName = actorName,
                summaryText = "$actorName marked record settled",
                contentHashSnapshot = transaction.contentHash,
                isAcknowledged = true
            )
        }
    }

    /**
     * Prepares a transaction for Two-Party Verification ("Request verification"):
     * - Ensures remoteId, shareToken, lenderName, borrowerName, and SHA-256 contentHash are populated.
     * - Transitions LOCAL_ONLY -> PENDING.
     * - Logs SHARED event in the immutable audit trail.
     * - If Cloud Verification consent is granted and signed in, publishes the record with E2EE notes to Firestore.
     * - Returns the shareable App Link URL `https://jyotirmoyb.com/u/<token>`.
     */
    suspend fun prepareVerificationShare(transaction: UdhaarTransaction): Pair<UdhaarTransaction, String> {
        val settings = userSettings.first()
        val myName = settings.payeeName.ifBlank { "Udhaar User" }
        val remoteId = transaction.remoteId.ifBlank { UUID.randomUUID().toString() }
        val shareToken = transaction.shareToken.ifBlank { UUID.randomUUID().toString().replace("-", "") }
        val (lender, borrower) = VerificationCryptoHelper.resolveLenderAndBorrowerNames(
            type = transaction.transactionType,
            partyName = transaction.partyName,
            myProfileName = myName,
            explicitLenderName = transaction.lenderName,
            explicitBorrowerName = transaction.borrowerName
        )
        val hash = VerificationCryptoHelper.computeContentHash(
            lenderName = lender,
            borrowerName = borrower,
            principalAmount = transaction.principalAmount,
            currencyCode = transaction.currency.code,
            startDateMillis = transaction.startDateMillis,
            expiryDateMillis = transaction.expiryDateMillis,
            isInterestEnabled = transaction.isInterestEnabled,
            interestType = transaction.interestType,
            interestRate = transaction.interestRate,
            interestRatePeriod = transaction.interestRatePeriod,
            compoundFrequency = transaction.compoundFrequency
        )

        val nextStatus = when (transaction.verificationStatus) {
            VerificationStatus.LOCAL_ONLY -> VerificationStatus.PENDING
            else -> transaction.verificationStatus
        }

        val updatedTx = transaction.copy(
            remoteId = remoteId,
            shareToken = shareToken,
            lenderName = lender,
            borrowerName = borrower,
            contentHash = hash,
            verificationStatus = nextStatus
        )
        dao.updateTransaction(updatedTx)

        val existingAudit = dao.getAuditTrailForTransactionSnapshot(transaction.id)
        if (existingAudit.none { it.eventType == AuditEventType.SHARED && it.contentHashSnapshot == hash }) {
            appendLocalAuditEvent(
                transactionId = transaction.id,
                eventType = AuditEventType.SHARED,
                actorUserId = settings.cloudUserId,
                actorName = myName,
                summaryText = "$myName requested verification from ${transaction.partyName} (SHA-256: ${hash.take(10)}…)",
                contentHashSnapshot = hash
            )
        }

        val encryptedNote = VerificationCryptoHelper.encryptE2eeField(
            plainText = updatedTx.notes,
            remoteId = remoteId,
            shareToken = shareToken
        )

        if (settings.cloudVerificationConsent && settings.cloudUserId.isNotBlank()) {
            runCatching {
                val safeRemoteId = remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                val participants = listOfNotNull(
                    settings.cloudUserId.takeIf { it.isNotBlank() },
                    updatedTx.counterpartyUserId.takeIf { it.isNotBlank() }
                ).distinct()
                val cloudRecord = CloudSharedRecord(
                    remoteId = safeRemoteId,
                    creatorUid = settings.cloudUserId,
                    counterpartyUid = updatedTx.counterpartyUserId,
                    participantUids = participants,
                    lenderName = lender,
                    borrowerName = borrower,
                    creatorRole = updatedTx.transactionType.name,
                    currency = updatedTx.currency.code,
                    principalAmount = updatedTx.principalAmount,
                    amountPaid = updatedTx.amountPaid,
                    paymentMode = updatedTx.paymentMode.name,
                    isInterestEnabled = updatedTx.isInterestEnabled,
                    interestType = updatedTx.interestType.name,
                    interestRate = updatedTx.interestRate,
                    interestRatePeriod = updatedTx.interestRatePeriod.name,
                    compoundFrequency = updatedTx.compoundFrequency.name,
                    startDateMillis = updatedTx.startDateMillis,
                    expiryDateMillis = updatedTx.expiryDateMillis ?: -1L,
                    contentHash = hash,
                    verificationStatus = nextStatus.name,
                    verifiedAtMillis = updatedTx.verifiedAtMillis ?: -1L,
                    disputeReason = updatedTx.disputeReason,
                    encryptedNotes = encryptedNote,
                    attachmentRefs = emptyList(),
                    isSettled = updatedTx.isSettled
                )
                cloudRepository?.createSharedRecord(cloudRecord)
                cloudRepository?.appendAuditEvent(
                    CloudAuditEvent(
                        eventId = "evt_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                        remoteId = safeRemoteId,
                        eventType = AuditEventType.SHARED.name,
                        actorUid = settings.cloudUserId,
                        actorName = myName,
                        participantUids = participants,
                        summaryText = "$myName shared record for two-party verification",
                        contentHashSnapshot = hash,
                        acknowledged = false,
                        eventTimeMillis = System.currentTimeMillis()
                    )
                )
            }
        }

        val payload = VerificationCryptoHelper.SharedVerificationPayload(
            remoteId = remoteId,
            shareToken = shareToken,
            creatorUserId = settings.cloudUserId,
            creatorRole = updatedTx.transactionType,
            lenderName = lender,
            borrowerName = borrower,
            creatorPhone = settings.phoneNumber,
            counterpartyPhone = updatedTx.phoneNumber,
            principalAmount = updatedTx.principalAmount,
            currencyCode = updatedTx.currency.code,
            startDateMillis = updatedTx.startDateMillis,
            expiryDateMillis = updatedTx.expiryDateMillis,
            isInterestEnabled = updatedTx.isInterestEnabled,
            interestType = updatedTx.interestType,
            interestRate = updatedTx.interestRate,
            interestRatePeriod = updatedTx.interestRatePeriod,
            compoundFrequency = updatedTx.compoundFrequency,
            contentHash = hash,
            encryptedNote = encryptedNote
        )

        val link = VerificationCryptoHelper.buildVerificationLink(payload)
        return updatedTx to link
    }

    /**
     * Imports or updates a mirrored record when the counterparty opens a verification link
     * and confirms or disputes it.
     * - Flips the role (`GIVEN` <-> `TAKEN`) when imported by the counterparty so the borrower gets
     *   a `TAKEN` record and the lender gets a `GIVEN` record, while both sides see the exact same
     *   64-char SHA-256 `contentHash`.
     */
    suspend fun processIncomingVerificationResponse(
        payload: VerificationCryptoHelper.SharedVerificationPayload,
        confirm: Boolean,
        disputeReason: String = ""
    ): UdhaarTransaction {
        val settings = userSettings.first()
        val myName = settings.payeeName.ifBlank {
            if (payload.creatorRole == TransactionType.GIVEN) payload.borrowerName else payload.lenderName
        }
        val nowMillis = System.currentTimeMillis()
        val existing = dao.getTransactionByRemoteId(payload.remoteId)
        val currency = runCatching { CurrencyType.valueOf(payload.currencyCode) }.getOrDefault(CurrencyType.INR)
        val decryptedNote = VerificationCryptoHelper.decryptE2eeField(
            cipherText = payload.encryptedNote,
            remoteId = payload.remoteId,
            shareToken = payload.shareToken
        )

        val canonicalHash = VerificationCryptoHelper.computeContentHash(
            lenderName = payload.lenderName,
            borrowerName = payload.borrowerName,
            principalAmount = payload.principalAmount,
            currencyCode = currency.code,
            startDateMillis = payload.startDateMillis,
            expiryDateMillis = payload.expiryDateMillis,
            isInterestEnabled = payload.isInterestEnabled,
            interestType = payload.interestType,
            interestRate = payload.interestRate,
            interestRatePeriod = payload.interestRatePeriod,
            compoundFrequency = payload.compoundFrequency
        )

        val newStatus = if (confirm) VerificationStatus.CONFIRMED else VerificationStatus.DISPUTED

        val resultingTx: UdhaarTransaction = if (existing != null) {
            val updated = existing.copy(
                verificationStatus = newStatus,
                verifiedAtMillis = if (confirm) nowMillis else existing.verifiedAtMillis,
                needsMyConfirmation = false,
                disputeReason = if (confirm) "" else disputeReason.trim(),
                contentHash = canonicalHash,
                lenderName = payload.lenderName,
                borrowerName = payload.borrowerName
            )
            dao.updateTransaction(updated)
            updated
        } else {
            // Mirrored record for the counterparty: GIVEN becomes TAKEN, TAKEN becomes GIVEN
            val mirroredType = if (payload.creatorRole == TransactionType.GIVEN) {
                TransactionType.TAKEN
            } else {
                TransactionType.GIVEN
            }
            val counterpartyName = if (mirroredType == TransactionType.TAKEN) {
                payload.lenderName
            } else {
                payload.borrowerName
            }
            val counterpartyPhone = payload.creatorPhone

            val linkedParty = dao.getPartyByName(counterpartyName)
            val partyId = linkedParty?.partyId ?: dao.insertParty(
                PartyEntity(
                    name = counterpartyName,
                    phoneNumber = counterpartyPhone,
                    preferredCurrency = currency
                )
            )

            val mirroredTx = UdhaarTransaction(
                partyId = partyId,
                transactionType = mirroredType,
                partyName = counterpartyName,
                phoneNumber = counterpartyPhone,
                currency = currency,
                principalAmount = payload.principalAmount,
                amountPaid = 0.0,
                paymentMode = PaymentMode.UPI,
                isInterestEnabled = payload.isInterestEnabled,
                interestType = payload.interestType,
                interestRate = payload.interestRate,
                interestRatePeriod = payload.interestRatePeriod,
                compoundFrequency = payload.compoundFrequency,
                startDateMillis = payload.startDateMillis,
                expiryDateMillis = payload.expiryDateMillis,
                notes = decryptedNote,
                remoteId = payload.remoteId,
                verificationStatus = newStatus,
                verifiedAtMillis = if (confirm) nowMillis else null,
                counterpartyUserId = payload.creatorUserId,
                contentHash = canonicalHash,
                needsMyConfirmation = false,
                shareToken = payload.shareToken,
                disputeReason = if (confirm) "" else disputeReason.trim(),
                lenderName = payload.lenderName,
                borrowerName = payload.borrowerName
            )
            val newId = dao.insertTransaction(mirroredTx)
            appendLocalAuditEvent(
                transactionId = newId,
                eventType = AuditEventType.VIEWED,
                actorUserId = settings.cloudUserId,
                actorName = myName,
                summaryText = "$myName opened shared verification link from $counterpartyName",
                contentHashSnapshot = canonicalHash
            )
            mirroredTx.copy(id = newId)
        }

        appendLocalAuditEvent(
            transactionId = resultingTx.id,
            eventType = if (confirm) AuditEventType.CONFIRMED else AuditEventType.DISPUTED,
            actorUserId = settings.cloudUserId,
            actorName = myName,
            summaryText = if (confirm) {
                "$myName confirmed mirrored record (SHA-256: ${canonicalHash.take(10)}…)"
            } else {
                "$myName disputed record: ${disputeReason.ifBlank { "Terms disputed" }}"
            },
            contentHashSnapshot = canonicalHash,
            isAcknowledged = confirm
        )

        if (settings.cloudVerificationConsent &&
            settings.cloudUserId.isNotBlank() &&
            payload.remoteId.isNotBlank() &&
            payload.creatorUserId.isNotBlank() &&
            payload.creatorUserId != settings.cloudUserId
        ) {
            runCatching {
                val safeRemoteId = payload.remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                cloudRepository?.claimAndRespondSharedRecord(
                    remoteId = safeRemoteId,
                    creatorUid = payload.creatorUserId,
                    counterpartyUid = settings.cloudUserId,
                    verificationStatus = newStatus.name,
                    verifiedAtMillis = if (confirm) nowMillis else -1L,
                    disputeReason = if (confirm) "" else disputeReason.trim()
                )
                cloudRepository?.appendAuditEvent(
                    CloudAuditEvent(
                        eventId = "evt_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                        remoteId = safeRemoteId,
                        eventType = if (confirm) AuditEventType.CONFIRMED.name else AuditEventType.DISPUTED.name,
                        actorUid = settings.cloudUserId,
                        actorName = myName,
                        participantUids = listOf(payload.creatorUserId, settings.cloudUserId).distinct(),
                        summaryText = if (confirm) "$myName confirmed the record" else "$myName disputed: $disputeReason",
                        contentHashSnapshot = canonicalHash,
                        acknowledged = confirm,
                        eventTimeMillis = nowMillis
                    )
                )
            }
        }

        return resultingTx
    }

    /**
     * Confirms or disputes an existing local record that is flagged `needsMyConfirmation` or `PENDING` / `EDITED_AFTER_CONFIRM`.
     */
    suspend fun confirmOrDisputeExistingTransaction(
        transaction: UdhaarTransaction,
        confirm: Boolean,
        disputeReason: String = ""
    ) {
        val settings = userSettings.first()
        val myName = settings.payeeName.ifBlank { "You" }
        val nowMillis = System.currentTimeMillis()
        val nextStatus = if (confirm) VerificationStatus.CONFIRMED else VerificationStatus.DISPUTED
        val updated = transaction.copy(
            verificationStatus = nextStatus,
            verifiedAtMillis = if (confirm) nowMillis else transaction.verifiedAtMillis,
            needsMyConfirmation = false,
            disputeReason = if (confirm) "" else disputeReason.trim()
        )
        dao.updateTransaction(updated)

        appendLocalAuditEvent(
            transactionId = transaction.id,
            eventType = if (confirm) AuditEventType.CONFIRMED else AuditEventType.DISPUTED,
            actorUserId = settings.cloudUserId,
            actorName = myName,
            summaryText = if (confirm) {
                "$myName confirmed record (SHA-256: ${transaction.contentHash.take(10)}…)"
            } else {
                "$myName disputed record: ${disputeReason.ifBlank { "Terms disputed" }}"
            },
            contentHashSnapshot = transaction.contentHash,
            isAcknowledged = confirm
        )

        if (settings.cloudVerificationConsent && settings.cloudUserId.isNotBlank() && transaction.remoteId.isNotBlank()) {
            runCatching {
                val safeRemoteId = transaction.remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                cloudRepository?.updateSharedRecordTermsOrStatus(
                    remoteId = safeRemoteId,
                    updates = mapOf(
                        "verificationStatus" to nextStatus.name,
                        "verifiedAtMillis" to (if (confirm) nowMillis else -1L),
                        "disputeReason" to (if (confirm) "" else disputeReason.trim())
                    )
                )
            }
        }
    }

    /**
     * Accepts a shared Payment Profile from a counterparty and automatically saves their UPI ID & phone
     * to the matching or newly created PartyEntity.
     */
    suspend fun acceptSharedPaymentProfile(
        profile: VerificationCryptoHelper.SharedPaymentProfilePayload,
        targetPartyId: Long? = null
    ): PartyEntity {
        val existingParty = when {
            targetPartyId != null && targetPartyId > 0L -> dao.getPartyById(targetPartyId)
            profile.displayName.isNotBlank() -> dao.getPartyByName(profile.displayName.trim())
            else -> null
        }

        return if (existingParty != null) {
            val updated = existingParty.copy(
                upiId = profile.upiId.trim().ifBlank { existingParty.upiId },
                phoneNumber = profile.phoneNumber.trim().ifBlank { existingParty.phoneNumber }
            )
            dao.updateParty(updated)
            updated
        } else {
            val newParty = PartyEntity(
                name = profile.displayName.trim().ifBlank { "Shared Contact" },
                phoneNumber = profile.phoneNumber.trim(),
                upiId = profile.upiId.trim(),
                preferredCurrency = CurrencyType.INR
            )
            val newId = dao.insertParty(newParty)
            newParty.copy(partyId = newId)
        }
    }

    private suspend fun appendLocalAuditEvent(
        transactionId: Long,
        eventType: AuditEventType,
        actorUserId: String = "",
        actorName: String,
        summaryText: String,
        contentHashSnapshot: String,
        isAcknowledged: Boolean = false
    ) {
        dao.insertAuditTrailEntry(
            AuditTrailEntry(
                transactionId = transactionId,
                remoteEventId = "evt_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                eventType = eventType,
                actorUserId = actorUserId,
                actorName = actorName,
                summaryText = summaryText,
                contentHashSnapshot = contentHashSnapshot.ifBlank { "0".repeat(64) },
                isAcknowledged = isAcknowledged,
                eventTimeMillis = System.currentTimeMillis()
            )
        )
    }

    private suspend fun syncEditedTermsToCloudIfEnabled(
        tx: UdhaarTransaction,
        actorName: String
    ) {
        val settings = userSettings.first()
        if (!settings.cloudVerificationConsent || settings.cloudUserId.isBlank() || tx.remoteId.isBlank()) return
        runCatching {
            val safeRemoteId = tx.remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            cloudRepository?.updateSharedRecordTermsOrStatus(
                remoteId = safeRemoteId,
                updates = mapOf(
                    "principalAmount" to tx.principalAmount,
                    "currency" to tx.currency.code,
                    "isInterestEnabled" to tx.isInterestEnabled,
                    "interestType" to tx.interestType.name,
                    "interestRate" to tx.interestRate,
                    "interestRatePeriod" to tx.interestRatePeriod.name,
                    "compoundFrequency" to tx.compoundFrequency.name,
                    "startDateMillis" to tx.startDateMillis,
                    "expiryDateMillis" to (tx.expiryDateMillis ?: -1L),
                    "contentHash" to tx.contentHash,
                    "verificationStatus" to VerificationStatus.EDITED_AFTER_CONFIRM.name
                )
            )
            val participants = listOfNotNull(
                settings.cloudUserId.takeIf { it.isNotBlank() },
                tx.counterpartyUserId.takeIf { it.isNotBlank() }
            ).distinct()
            cloudRepository?.appendAuditEvent(
                CloudAuditEvent(
                    eventId = "evt_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    remoteId = safeRemoteId,
                    eventType = AuditEventType.EDITED.name,
                    actorUid = settings.cloudUserId,
                    actorName = actorName,
                    participantUids = participants,
                    summaryText = "$actorName edited terms; re-confirmation required",
                    contentHashSnapshot = tx.contentHash,
                    acknowledged = false,
                    eventTimeMillis = System.currentTimeMillis()
                )
            )
        }
    }

    // --- Settings & Privacy Consent Operations ---
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

        context?.dataStore?.edit { prefs ->
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

        val currentSettings = userSettings.first()
        if (currentSettings.cloudVerificationConsent && currentSettings.cloudUserId.isNotBlank()) {
            runCatching {
                cloudRepository?.upsertUserProfile(
                    CloudUserProfile(
                        userId = currentSettings.cloudUserId,
                        displayName = currentSettings.payeeName.ifBlank { "Udhaar User" },
                        phoneNumber = currentSettings.phoneNumber,
                        upiId = currentSettings.upiId,
                        photoUri = currentSettings.profilePhotoUri
                    )
                )
            }
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
        context?.dataStore?.edit { prefs ->
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
        context?.dataStore?.edit { prefs ->
            prefs[PrefKeys.CLOUD_CONSENT] = consentGranted
            prefs[PrefKeys.CLOUD_USER_ID] = userId.trim()
            prefs[PrefKeys.CLOUD_USER_EMAIL] = email.trim()
        }
        if (consentGranted && userId.isNotBlank()) {
            val current = userSettings.first()
            runCatching {
                cloudRepository?.upsertUserProfile(
                    CloudUserProfile(
                        userId = userId.trim(),
                        displayName = current.payeeName.ifBlank { "Udhaar User" },
                        phoneNumber = current.phoneNumber,
                        upiId = current.upiId,
                        photoUri = current.profilePhotoUri
                    )
                )
            }
        }
    }

    /**
     * Deletes all Cloud Firestore documents created by the current user, revokes cloud consent,
     * and resets local verification statuses back to `LOCAL_ONLY` while preserving all local offline data.
     */
    suspend fun deleteMyCloudData() {
        val current = userSettings.first()
        val allTxs = dao.getAllTransactionsSnapshot()
        val remoteIds = allTxs.mapNotNull { tx ->
            tx.remoteId.takeIf { it.isNotBlank() }?.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        }
        val allThreads = dao.getAllChatThreadsSnapshot()
        val createdThreadIds = allThreads.mapNotNull { t ->
            t.remoteId.takeIf { it.isNotBlank() }?.let { "tx_${it.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}" }
                ?: if (t.scope == ChatThreadScope.PARTY_GENERAL && t.partyName.isNotBlank()) {
                    "party_${t.partyName.trim().lowercase().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}"
                } else null
        }.distinct()
        if (current.cloudUserId.isNotBlank()) {
            runCatching {
                cloudRepository?.deleteAllMyCloudData(
                    userId = current.cloudUserId,
                    createdRemoteIds = remoteIds,
                    createdThreadIds = createdThreadIds
                )
            }
        }
        for (tx in allTxs) {
            if (tx.verificationStatus != VerificationStatus.LOCAL_ONLY) {
                dao.updateTransaction(
                    tx.copy(
                        verificationStatus = VerificationStatus.LOCAL_ONLY,
                        needsMyConfirmation = false
                    )
                )
            }
        }
        context?.dataStore?.edit { prefs ->
            prefs[PrefKeys.CLOUD_CONSENT] = false
            prefs[PrefKeys.CLOUD_USER_ID] = ""
            prefs[PrefKeys.CLOUD_USER_EMAIL] = ""
        }
    }

    // --- Phase 4: In-App Communication (Chat Threads, Quick Replies, Payment Proofs, Extensions, Moderation) ---
    companion object {
        fun transactionThreadKey(transactionId: Long): String = "tx_$transactionId"

        fun partyGeneralThreadKey(partyName: String): String =
            "party_${partyName.trim().lowercase()}"

        fun cloudThreadIdFor(
            scope: ChatThreadScope,
            remoteId: String,
            partyName: String,
            fallbackThreadKey: String
        ): String {
            return if (scope == ChatThreadScope.TRANSACTION && remoteId.isNotBlank()) {
                "tx_${remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}"
            } else if (scope == ChatThreadScope.PARTY_GENERAL && partyName.isNotBlank()) {
                "party_${partyName.trim().lowercase().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}"
            } else {
                fallbackThreadKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            }
        }
    }

    suspend fun getOrCreateThreadMetadata(
        threadKey: String,
        scope: ChatThreadScope,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String
    ): ChatThreadMetadata {
        val existing = dao.getChatThreadMetadata(threadKey)
        if (existing != null) return existing
        val created = ChatThreadMetadata(
            threadKey = threadKey,
            remoteThreadId = remoteId,
            threadScope = scope,
            transactionId = transactionId,
            partyId = partyId,
            partyName = partyName.trim()
        )
        dao.upsertChatThreadMetadata(created)
        return created
    }

    fun buildTransactionThreadKey(transaction: UdhaarTransaction): String =
        transactionThreadKey(transaction.id)

    /**
     * Sends a chat message (`TEXT`, `QUICK_REPLY`, `PAYMENT_PROOF`) in either a transaction thread
     * or a party's general thread.
     * - Blocks sending if the thread is marked `isBlocked == true`.
     * - When `fromCounterparty == false` (outgoing), updates delivery status (`SENT` -> `DELIVERED` when synced or verified).
     * - When `fromCounterparty == true` (incoming message or simulated counterparty reply), marks `isUnreadForMe = true`
     *   and triggers a system notification via [ChatNotificationHelper] unless muted or blocked.
     */
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
    ): ChatMessageEntity? {
        val resolvedTx = transaction ?: transactionId?.let { dao.getTransactionById(it) }
        val finalAttachmentUri = attachmentUri.ifBlank { proofAttachmentUri }.trim()
        val finalMimeType = attachmentMimeType.ifBlank { proofMimeType }.trim()
        val cleanText = textContent.trim()
        if (cleanText.isEmpty() && finalAttachmentUri.isBlank()) return null

        val meta = getOrCreateThreadMetadata(
            threadKey = threadKey,
            scope = scope,
            transactionId = resolvedTx?.id ?: transactionId,
            remoteId = resolvedTx?.remoteId.orEmpty(),
            partyId = partyId ?: resolvedTx?.partyId,
            partyName = partyName
        )
        if (meta.isBlocked) return null

        val settings = userSettings.first()
        val myName = settings.payeeName.ifBlank { "You" }
        val nowMillis = System.currentTimeMillis()
        val remoteMsgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        val senderName = if (fromCounterparty) partyName.trim().ifBlank { "Party" } else myName
        val senderUid = if (fromCounterparty) {
            resolvedTx?.counterpartyUserId?.ifBlank { "party_remote" } ?: "party_remote"
        } else {
            settings.cloudUserId.ifBlank { "local_me" }
        }

        val initialDelivery = when {
            fromCounterparty -> MessageDeliveryStatus.DELIVERED
            settings.cloudVerificationConsent && settings.cloudUserId.isNotBlank() -> MessageDeliveryStatus.DELIVERED
            resolvedTx != null && resolvedTx.verificationStatus == VerificationStatus.CONFIRMED -> MessageDeliveryStatus.DELIVERED
            else -> MessageDeliveryStatus.SENT
        }

        val msgEntity = ChatMessageEntity(
            remoteMessageId = remoteMsgId,
            threadKey = threadKey,
            threadScope = scope,
            transactionId = resolvedTx?.id ?: transactionId,
            partyId = partyId ?: resolvedTx?.partyId,
            partyName = partyName.trim(),
            senderUserId = senderUid,
            senderName = senderName,
            isFromMe = !fromCounterparty,
            messageType = messageType,
            bodyText = cleanText.ifEmpty {
                if (finalMimeType.contains("pdf", ignoreCase = true)) "Attached Payment Proof (PDF)"
                else "Attached Payment Proof (Image)"
            },
            attachmentUri = finalAttachmentUri,
            attachmentMimeType = finalMimeType,
            attachmentName = proofFileName,
            proposedDueDateMillis = null,
            extensionStatus = ExtensionRequestStatus.NONE,
            deliveryStatus = initialDelivery,
            isUnreadForMe = fromCounterparty,
            sentAtMillis = nowMillis,
            deliveredAtMillis = if (initialDelivery != MessageDeliveryStatus.SENT) nowMillis else null,
            readAtMillis = null
        )

        val insertedId = dao.insertChatMessage(msgEntity)
        val savedMsg = msgEntity.copy(id = insertedId)

        val updatedMeta = meta.copy(
            counterpartyTyping = false,
            typingUpdatedAtMillis = nowMillis,
            updatedAtMillis = nowMillis
        )
        dao.upsertChatThreadMetadata(updatedMeta)

        if (fromCounterparty && context != null) {
            ChatNotificationHelper.showIncomingChatNotification(
                context = context,
                threadMetadata = updatedMeta,
                message = savedMsg
            )
        }

        syncChatThreadAndMessageToCloudIfEnabled(
            meta = updatedMeta,
            message = savedMsg,
            settings = settings,
            counterpartyUserId = resolvedTx?.counterpartyUserId.orEmpty()
        )

        return savedMsg
    }

    /**
     * One-tap "Request extension":
     * - Proposes a new due date (`proposedDueDateMillis`) on a transaction thread.
     * - Inserts an `EXTENSION_REQUEST` message with `ExtensionRequestStatus.PENDING`.
     * - Logs `AuditEventType.EXTENSION_REQUESTED` in the immutable audit trail.
     * - Syncs to Firestore and dispatches a notification if requested by the counterparty.
     */
    suspend fun requestDueDateExtension(
        transaction: UdhaarTransaction,
        proposedDueDateMillis: Long,
        note: String = "",
        reasonText: String = "",
        requestedByCounterparty: Boolean = false
    ): ChatMessageEntity {
        val effectiveNote = note.ifBlank { reasonText }
        val threadKey = transactionThreadKey(transaction.id)
        val meta = getOrCreateThreadMetadata(
            threadKey = threadKey,
            scope = ChatThreadScope.TRANSACTION,
            transactionId = transaction.id,
            remoteId = transaction.remoteId,
            partyId = transaction.partyId,
            partyName = transaction.partyName
        )

        val settings = userSettings.first()
        val myName = settings.payeeName.ifBlank { "You" }
        val nowMillis = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(proposedDueDateMillis))

        val senderName = if (requestedByCounterparty) transaction.partyName else myName
        val senderUid = if (requestedByCounterparty) {
            transaction.counterpartyUserId.ifBlank { "party_remote" }
        } else {
            settings.cloudUserId.ifBlank { "local_me" }
        }

        val messageBody = buildString {
            append("$senderName requested a due-date extension to $dateStr")
            if (effectiveNote.isNotBlank()) {
                append(" — \"${effectiveNote.trim()}\"")
            }
        }

        val remoteMsgId = "ext_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val extMessage = ChatMessageEntity(
            remoteMessageId = remoteMsgId,
            threadKey = threadKey,
            threadScope = ChatThreadScope.TRANSACTION,
            transactionId = transaction.id,
            partyId = transaction.partyId,
            partyName = transaction.partyName,
            senderUserId = senderUid,
            senderName = senderName,
            isFromMe = !requestedByCounterparty,
            messageType = ChatMessageType.EXTENSION_REQUEST,
            bodyText = messageBody,
            proposedDueDateMillis = proposedDueDateMillis,
            extensionStatus = ExtensionRequestStatus.PENDING,
            deliveryStatus = MessageDeliveryStatus.DELIVERED,
            isUnreadForMe = requestedByCounterparty,
            sentAtMillis = nowMillis,
            deliveredAtMillis = nowMillis
        )

        val insertedId = dao.insertChatMessage(extMessage)
        val savedMsg = extMessage.copy(id = insertedId)

        val updatedMeta = meta.copy(
            updatedAtMillis = nowMillis
        )
        dao.upsertChatThreadMetadata(updatedMeta)

        appendLocalAuditEvent(
            transactionId = transaction.id,
            eventType = AuditEventType.EXTENSION_REQUESTED,
            actorUserId = senderUid,
            actorName = senderName,
            summaryText = "$senderName proposed extending due date to $dateStr",
            contentHashSnapshot = transaction.contentHash,
            isAcknowledged = false
        )

        if (requestedByCounterparty && context != null) {
            ChatNotificationHelper.showIncomingChatNotification(
                context = context,
                threadMetadata = updatedMeta,
                message = savedMsg
            )
        }

        syncChatThreadAndMessageToCloudIfEnabled(
            meta = updatedMeta,
            message = savedMsg,
            settings = settings,
            counterpartyUserId = transaction.counterpartyUserId
        )

        return savedMsg
    }

    /**
     * Lender (or counterparty) accepts or declines a pending due-date extension request:
     * - Updates `message.extensionStatus` to `ACCEPTED` or `DECLINED` and marks it `READ`.
     * - On `accept == true`, updates `transaction.expiryDateMillis` (and `reminderDateMillis` if set)
     *   to `proposedDueDateMillis`, recomputes the canonical SHA-256 `contentHash`, updates both
     *   local Room and Cloud Firestore records, and logs `AuditEventType.EXTENSION_ACCEPTED`.
     * - On `accept == false`, logs `AuditEventType.EXTENSION_DECLINED`.
     */
    suspend fun respondToExtensionRequest(
        transaction: UdhaarTransaction? = null,
        message: ChatMessageEntity,
        accept: Boolean
    ): UdhaarTransaction {
        val resolvedTx = transaction
            ?: message.transactionId?.let { dao.getTransactionById(it) }
            ?: throw IllegalStateException("Transaction not found for extension request")
        val settings = userSettings.first()
        val myName = settings.payeeName.ifBlank { "You" }
        val nowMillis = System.currentTimeMillis()
        val proposedMillis = message.proposedDueDateMillis ?: resolvedTx.expiryDateMillis ?: nowMillis
        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(proposedMillis))
        val newExtStatus = if (accept) ExtensionRequestStatus.ACCEPTED else ExtensionRequestStatus.DECLINED

        val updatedMessage = message.copy(
            extensionStatus = newExtStatus,
            deliveryStatus = MessageDeliveryStatus.READ,
            readAtMillis = nowMillis,
            isUnreadForMe = false
        )
        dao.updateChatMessage(updatedMessage)

        val updatedTx = if (accept) {
            val (lender, borrower) = VerificationCryptoHelper.resolveLenderAndBorrowerNames(
                type = resolvedTx.transactionType,
                partyName = resolvedTx.partyName,
                myProfileName = myName,
                explicitLenderName = resolvedTx.lenderName,
                explicitBorrowerName = resolvedTx.borrowerName
            )
            val newHash = VerificationCryptoHelper.computeContentHash(
                lenderName = lender,
                borrowerName = borrower,
                principalAmount = resolvedTx.principalAmount,
                currencyCode = resolvedTx.currency.code,
                startDateMillis = resolvedTx.startDateMillis,
                expiryDateMillis = proposedMillis,
                isInterestEnabled = resolvedTx.isInterestEnabled,
                interestType = resolvedTx.interestType,
                interestRate = resolvedTx.interestRate,
                interestRatePeriod = resolvedTx.interestRatePeriod,
                compoundFrequency = resolvedTx.compoundFrequency
            )
            val txWithNewDue = resolvedTx.copy(
                expiryDateMillis = proposedMillis,
                reminderDateMillis = if (resolvedTx.reminderDateMillis != null) proposedMillis else null,
                contentHash = newHash,
                lenderName = lender,
                borrowerName = borrower
            )
            dao.updateTransaction(txWithNewDue)
            txWithNewDue
        } else {
            resolvedTx
        }

        val auditType = if (accept) AuditEventType.EXTENSION_ACCEPTED else AuditEventType.EXTENSION_DECLINED
        val auditSummary = if (accept) {
            "$myName accepted due-date extension to $dateStr (new SHA-256: ${updatedTx.contentHash.take(10)}…)"
        } else {
            "$myName declined due-date extension request for $dateStr"
        }

        appendLocalAuditEvent(
            transactionId = resolvedTx.id,
            eventType = auditType,
            actorUserId = settings.cloudUserId,
            actorName = myName,
            summaryText = auditSummary,
            contentHashSnapshot = updatedTx.contentHash,
            isAcknowledged = accept
        )

        if (settings.cloudVerificationConsent && settings.cloudUserId.isNotBlank()) {
            runCatching {
                val cloudThreadId = cloudThreadIdFor(
                    scope = message.threadScope,
                    remoteId = resolvedTx.remoteId,
                    partyName = resolvedTx.partyName,
                    fallbackThreadKey = message.threadKey
                )
                if (message.remoteMessageId.isNotBlank()) {
                    cloudRepository?.updateChatMessageDeliveryOrExtension(
                        threadId = cloudThreadId,
                        messageId = message.remoteMessageId,
                        deliveryStatus = MessageDeliveryStatus.READ.name,
                        deliveredAtMillis = message.deliveredAtMillis ?: nowMillis,
                        readAtMillis = nowMillis,
                        extensionStatus = newExtStatus.name
                    )
                }
                if (resolvedTx.remoteId.isNotBlank()) {
                    val safeRemoteId = resolvedTx.remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                    if (accept) {
                        cloudRepository?.updateSharedRecordTermsOrStatus(
                            remoteId = safeRemoteId,
                            updates = mapOf(
                                "expiryDateMillis" to proposedMillis,
                                "contentHash" to updatedTx.contentHash
                            )
                        )
                    }
                    val participants = listOfNotNull(
                        settings.cloudUserId.takeIf { it.isNotBlank() },
                        resolvedTx.counterpartyUserId.takeIf { it.isNotBlank() }
                    ).distinct()
                    cloudRepository?.appendAuditEvent(
                        CloudAuditEvent(
                            eventId = "evt_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                            remoteId = safeRemoteId,
                            eventType = auditType.name,
                            actorUid = settings.cloudUserId,
                            actorName = myName,
                            participantUids = participants,
                            summaryText = auditSummary,
                            contentHashSnapshot = updatedTx.contentHash,
                            acknowledged = accept,
                            eventTimeMillis = nowMillis
                        )
                    )
                }
            }
        }

        return updatedTx
    }

    /**
     * Marks all messages in [threadKey] as read (`isUnreadForMe = false`, `deliveryStatus = READ`)
     * so unread badges clear and read ticks (`✓✓ Read`) update immediately.
     */
    suspend fun markThreadRead(threadKey: String) {
        val nowMillis = System.currentTimeMillis()
        dao.markThreadMessagesRead(threadKey, nowMillis)
    }

    /**
     * Updates the typing indicator state for a thread (both local and Firestore when cloud-enabled).
     */
    suspend fun setTypingIndicator(
        threadKey: String,
        scope: ChatThreadScope = ChatThreadScope.TRANSACTION,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String = "",
        isCounterpartyTyping: Boolean
    ) {
        val nowMillis = System.currentTimeMillis()
        val meta = getOrCreateThreadMetadata(
            threadKey = threadKey,
            scope = scope,
            transactionId = transactionId,
            remoteId = remoteId,
            partyId = partyId,
            partyName = partyName
        )
        val updated = meta.copy(
            counterpartyTyping = isCounterpartyTyping,
            typingUpdatedAtMillis = nowMillis,
            updatedAtMillis = nowMillis
        )
        dao.upsertChatThreadMetadata(updated)

        val settings = userSettings.first()
        if (settings.cloudVerificationConsent && settings.cloudUserId.isNotBlank()) {
            runCatching {
                val cloudThreadId = cloudThreadIdFor(updated.threadScope, updated.remoteThreadId, updated.partyName, threadKey)
                cloudRepository?.updateTypingStatus(
                    threadId = cloudThreadId,
                    typingUserId = if (isCounterpartyTyping) "party" else settings.cloudUserId,
                    typingUserName = if (isCounterpartyTyping) updated.partyName else settings.payeeName.ifBlank { "You" },
                    typingUpdatedAtMillis = nowMillis
                )
            }
        }
    }

    suspend fun toggleThreadMute(
        threadKey: String,
        isMuted: Boolean? = null,
        scope: ChatThreadScope = ChatThreadScope.TRANSACTION,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String = ""
    ): ChatThreadMetadata {
        val meta = getOrCreateThreadMetadata(threadKey, scope, transactionId, remoteId, partyId, partyName)
        val updated = meta.copy(isMuted = isMuted ?: !meta.isMuted, updatedAtMillis = System.currentTimeMillis())
        dao.upsertChatThreadMetadata(updated)
        syncModerationFlagsToCloud(updated)
        return updated
    }

    suspend fun toggleThreadBlock(
        threadKey: String,
        isBlocked: Boolean? = null,
        scope: ChatThreadScope = ChatThreadScope.TRANSACTION,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String = ""
    ): ChatThreadMetadata {
        val meta = getOrCreateThreadMetadata(threadKey, scope, transactionId, remoteId, partyId, partyName)
        val updated = meta.copy(isBlocked = isBlocked ?: !meta.isBlocked, updatedAtMillis = System.currentTimeMillis())
        dao.upsertChatThreadMetadata(updated)
        syncModerationFlagsToCloud(updated)
        return updated
    }

    suspend fun reportAbusiveUserInThread(
        threadKey: String,
        reason: String,
        scope: ChatThreadScope = ChatThreadScope.TRANSACTION,
        transactionId: Long? = null,
        remoteId: String = "",
        partyId: Long? = null,
        partyName: String = ""
    ): ChatThreadMetadata {
        val meta = getOrCreateThreadMetadata(threadKey, scope, transactionId, remoteId, partyId, partyName)
        val updated = meta.copy(
            isReported = true,
            isBlocked = true,
            reportReason = reason.trim().ifBlank { "Abusive or spam behavior" },
            updatedAtMillis = System.currentTimeMillis()
        )
        dao.upsertChatThreadMetadata(updated)
        syncModerationFlagsToCloud(updated)
        return updated
    }

    private suspend fun syncModerationFlagsToCloud(meta: ChatThreadMetadata) {
        val settings = userSettings.first()
        if (!settings.cloudVerificationConsent || settings.cloudUserId.isBlank()) return
        runCatching {
            val uid = settings.cloudUserId
            val cloudThreadId = cloudThreadIdFor(meta.threadScope, meta.remoteThreadId, meta.partyName, meta.threadKey)
            cloudRepository?.updateThreadMuteBlockReport(
                threadId = cloudThreadId,
                mutedByUids = if (meta.isMuted) listOf(uid) else emptyList(),
                blockedByUids = if (meta.isBlocked) listOf(uid) else emptyList(),
                reportedByUids = if (meta.isReported) listOf(uid) else emptyList(),
                reportReason = meta.reportReason
            )
        }
    }

    private suspend fun syncChatThreadAndMessageToCloudIfEnabled(
        meta: ChatThreadMetadata,
        message: ChatMessageEntity,
        settings: UserSettings,
        counterpartyUserId: String
    ) {
        if (!settings.cloudVerificationConsent || settings.cloudUserId.isBlank()) return
        runCatching {
            val uid = settings.cloudUserId
            val cloudThreadId = cloudThreadIdFor(meta.threadScope, meta.remoteThreadId, meta.partyName, meta.threadKey)
            val participants = listOfNotNull(
                uid.takeIf { it.isNotBlank() },
                counterpartyUserId.takeIf { it.isNotBlank() }
            ).distinct()
            val safeRemoteId = meta.remoteThreadId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")

            cloudRepository?.upsertChatThread(
                CloudChatThread(
                    threadId = cloudThreadId,
                    scope = meta.threadScope.name,
                    remoteId = safeRemoteId,
                    partyName = meta.partyName,
                    creatorUid = uid,
                    counterpartyUid = counterpartyUserId,
                    participantUids = participants,
                    mutedByUids = if (meta.isMuted) listOf(uid) else emptyList(),
                    blockedByUids = if (meta.isBlocked) listOf(uid) else emptyList(),
                    reportedByUids = if (meta.isReported) listOf(uid) else emptyList(),
                    reportReason = meta.reportReason,
                    lastMessagePreview = message.bodyText.take(120),
                    lastMessageAtMillis = message.sentAtMillis
                )
            )

            cloudRepository?.sendChatMessage(
                CloudChatMessage(
                    messageId = message.remoteMessageId.ifBlank { "msg_${message.id}" },
                    threadId = cloudThreadId,
                    remoteId = safeRemoteId,
                    senderUid = uid,
                    senderName = message.senderName,
                    participantUids = participants,
                    messageType = message.messageType.name,
                    textContent = message.bodyText,
                    attachmentRef = message.attachmentUri,
                    attachmentMimeType = message.attachmentMimeType,
                    proposedDueDateMillis = message.proposedDueDateMillis ?: -1L,
                    extensionStatus = message.extensionStatus.name,
                    deliveryStatus = message.deliveryStatus.name,
                    sentAtMillis = message.sentAtMillis,
                    deliveredAtMillis = message.deliveredAtMillis ?: -1L,
                    readAtMillis = message.readAtMillis ?: -1L
                )
            )
        }
    }

    fun verifyPin(candidatePin: String): PinVerificationResult {
        val manager = pinSecurityManager
            ?: return PinVerificationResult(
                isSuccess = false,
                failedAttempts = 0,
                lockoutRemainingMillis = 0L,
                errorMessage = "PIN security manager unavailable."
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
        context?.dataStore?.edit { prefs ->
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
        context?.dataStore?.edit { prefs ->
            prefs[PrefKeys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun setFontScale(fontScale: AppFontScale) {
        context?.dataStore?.edit { prefs ->
            prefs[PrefKeys.FONT_SCALE] = fontScale.name
        }
    }

    // --- Snapshot & Import Helpers for JSON Ledger Backup ---
    suspend fun getFullLedgerSnapshot(): Triple<List<PartyEntity>, List<UdhaarTransaction>, List<PartialPayment>> {
        val parties = dao.getAllPartiesSnapshot()
        val transactions = dao.getAllTransactionsSnapshot()
        val payments = dao.getAllPartialPaymentsSnapshot()
        return Triple(parties, transactions, payments)
    }

    suspend fun importLedgerData(
        parties: List<PartyEntity>,
        transactions: List<UdhaarTransaction>,
        partialPayments: List<PartialPayment>,
        attachments: List<Attachment> = emptyList()
    ): Int {
        val partyIdMap = mutableMapOf<Long, Long>()
        for (party in parties) {
            val existing = dao.getPartyByName(party.name)
            val newId = if (existing != null) {
                dao.updateParty(
                    existing.copy(
                        phoneNumber = party.phoneNumber.ifBlank { existing.phoneNumber },
                        upiId = party.upiId.ifBlank { existing.upiId },
                        email = party.email.ifBlank { existing.email },
                        tagOrNotes = party.tagOrNotes.ifBlank { existing.tagOrNotes }
                    )
                )
                existing.partyId
            } else {
                dao.insertParty(party.copy(partyId = 0L))
            }
            if (party.partyId > 0L) {
                partyIdMap[party.partyId] = newId
            }
        }

        val txIdMap = mutableMapOf<Long, Long>()
        var importedTxCount = 0
        for (tx in transactions) {
            val mappedPartyId = tx.partyId?.let { partyIdMap[it] }
                ?: dao.getPartyByName(tx.partyName)?.partyId
            val computedHash = tx.contentHash.ifBlank {
                VerificationCryptoHelper.computeTransactionContentHash(tx)
            }
            val insertedTxId = dao.insertTransaction(
                tx.copy(
                    id = 0L,
                    partyId = mappedPartyId,
                    remoteId = tx.remoteId.ifBlank { UUID.randomUUID().toString() },
                    contentHash = computedHash
                )
            )
            if (tx.id > 0L) {
                txIdMap[tx.id] = insertedTxId
            }
            importedTxCount++
        }

        val paymentIdMap = mutableMapOf<Long, Long>()
        for (payment in partialPayments) {
            val targetTxId = txIdMap[payment.transactionId]
            if (targetTxId != null) {
                val insertedPaymentId = dao.insertPartialPayment(
                    payment.copy(
                        id = 0L,
                        transactionId = targetTxId
                    )
                )
                if (payment.id > 0L) {
                    paymentIdMap[payment.id] = insertedPaymentId
                }
            }
        }

        for (att in attachments) {
            val targetTxId = txIdMap[att.transactionId]
            if (targetTxId != null) {
                val targetPaymentId = att.paymentId?.let { paymentIdMap[it] }
                dao.insertAttachment(
                    att.copy(
                        id = 0L,
                        transactionId = targetTxId,
                        paymentId = targetPaymentId
                    )
                )
            }
        }

        return importedTxCount
    }

    fun getAttachmentsForTransaction(transactionId: Long): Flow<List<Attachment>> =
        dao.getAttachmentsForTransaction(transactionId)

    fun getAttachmentsForPayment(paymentId: Long): Flow<List<Attachment>> =
        dao.getAttachmentsForPayment(paymentId)

    suspend fun insertAttachment(attachment: Attachment): Long =
        dao.insertAttachment(attachment)

    suspend fun deleteAttachment(attachment: Attachment) {
        dao.deleteAttachment(attachment)
        context?.let { ctx ->
            com.jyotirmoyb.udhaar.util.AttachmentManager.deleteAttachmentFile(ctx, attachment.localPath)
        }
    }

    suspend fun deleteAttachmentById(attachmentId: Long) {
        val all = dao.getAllAttachmentsSnapshot()
        val match = all.firstOrNull { it.id == attachmentId }
        if (match != null) {
            deleteAttachment(match)
        } else {
            dao.deleteAttachmentById(attachmentId)
        }
    }

    data class FullLedgerSnapshot(
        val parties: List<PartyEntity>,
        val transactions: List<UdhaarTransaction>,
        val partialPayments: List<PartialPayment>,
        val chatThreads: List<ChatThreadMetadata>,
        val chatMessages: List<ChatMessageEntity>,
        val attachments: List<Attachment> = emptyList()
    )

    suspend fun getFullLedgerBackupSnapshot(): FullLedgerSnapshot {
        val parties = dao.getAllPartiesSnapshot()
        val transactions = dao.getAllTransactionsSnapshot()
        val payments = dao.getAllPartialPaymentsSnapshot()
        val threads = dao.getAllChatThreadsSnapshot()
        val messages = dao.getAllChatMessagesSnapshot()
        val attachments = dao.getAllAttachmentsSnapshot()
        return FullLedgerSnapshot(parties, transactions, payments, threads, messages, attachments)
    }

    fun getLocalAttachmentsMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val filesDir = context?.filesDir ?: return map
        val attachDir = java.io.File(filesDir, "attachments")
        if (attachDir.exists() && attachDir.isDirectory) {
            attachDir.listFiles()?.forEach { file ->
                if (file.isFile && file.length() in 1..(5 * 1024 * 1024)) {
                    val b64 = runCatching {
                        android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
                    }.getOrNull()
                    if (b64 != null) {
                        map[file.name] = b64
                    }
                }
            }
        }
        return map
    }

    fun getAutoBackupPassphrase(): String? = pinSecurityManager?.getAutoBackupPassphrase()

    suspend fun updateDriveAccount(email: String, accessToken: String) {
        context?.dataStore?.edit { prefs ->
            prefs[PrefKeys.DRIVE_USER_EMAIL] = email.trim()
            prefs[PrefKeys.DRIVE_ACCESS_TOKEN] = accessToken.trim()
        }
    }

    suspend fun clearDriveAccount() {
        pinSecurityManager?.clearAutoBackupPassphrase()
        context?.dataStore?.edit { prefs ->
            prefs.remove(PrefKeys.DRIVE_USER_EMAIL)
            prefs.remove(PrefKeys.DRIVE_ACCESS_TOKEN)
            prefs.remove(PrefKeys.DRIVE_AUTO_BACKUP_KEY_SAVED)
        }
    }

    suspend fun updateDriveLastBackup(timeMillis: Long, sizeBytes: Long) {
        context?.dataStore?.edit { prefs ->
            prefs[PrefKeys.DRIVE_LAST_BACKUP_TIME] = timeMillis
            prefs[PrefKeys.DRIVE_LAST_BACKUP_SIZE] = sizeBytes
        }
    }

    fun saveAutoBackupPassphrase(passphrase: String) {
        pinSecurityManager?.saveAutoBackupPassphrase(passphrase)
    }

    suspend fun updateDriveAutoBackupSettings(
        frequency: AutoBackupFrequency,
        wifiOnly: Boolean,
        passphraseToSave: String? = null
    ) {
        if (!passphraseToSave.isNullOrBlank()) {
            pinSecurityManager?.saveAutoBackupPassphrase(passphraseToSave)
        } else if (frequency == AutoBackupFrequency.OFF) {
            pinSecurityManager?.clearAutoBackupPassphrase()
        }
        context?.dataStore?.edit { prefs ->
            prefs[PrefKeys.DRIVE_AUTO_BACKUP_FREQ] = frequency.name
            prefs[PrefKeys.DRIVE_AUTO_BACKUP_WIFI] = wifiOnly
            prefs[PrefKeys.DRIVE_AUTO_BACKUP_KEY_SAVED] = pinSecurityManager?.getAutoBackupPassphrase() != null
        }
        context?.let { ctx ->
            com.jyotirmoyb.udhaar.backup.DriveBackupScheduler.scheduleOrCancel(ctx, frequency, wifiOnly)
        }
    }

    suspend fun restoreFullLedgerBackup(
        backup: ParsedLedgerBackup,
        replaceExisting: Boolean
    ): Int {
        if (replaceExisting) {
            dao.clearAllLedgerData()
        }

        // 1. Restore non-PIN settings if present
        if (backup.settings.isNotEmpty()) {
            val s = backup.settings
            val upi = s["upiId"] as? String
            val payee = s["payeeName"] as? String
            val phone = s["phoneNumber"] as? String
            val currStr = s["defaultCurrency"] as? String
            val curr = currStr?.let { runCatching { CurrencyType.valueOf(it) }.getOrNull() } ?: CurrencyType.INR
            val inr = s["inrEnabled"] as? Boolean
            val npr = s["nprEnabled"] as? Boolean
            val themeStr = s["themeMode"] as? String
            val theme = themeStr?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
            val fontStr = s["fontScale"] as? String
            val font = fontStr?.let { runCatching { AppFontScale.valueOf(it) }.getOrNull() }

            updateSettings(
                upiId = upi.orEmpty(),
                payeeName = payee.orEmpty(),
                defaultCurrency = curr,
                inrEnabled = inr,
                nprEnabled = npr,
                themeMode = theme,
                fontScale = font,
                phoneNumber = phone
            )
        }

        // 2. Extract attachments to filesDir/attachments
        context?.filesDir?.let { fDir ->
            val attachDir = java.io.File(fDir, "attachments").apply { mkdirs() }
            for ((name, b64) in backup.attachments) {
                runCatching {
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                    java.io.File(attachDir, name).writeBytes(bytes)
                }
            }
        }

        // 3. Import parties, transactions, partial payments using ID mapping
        val importedTxCount = importLedgerData(
            parties = backup.parties,
            transactions = backup.transactions,
            partialPayments = backup.partialPayments,
            attachments = backup.attachmentRecords
        )

        // 4. Import chat threads and messages
        for (th in backup.chatThreads) {
            dao.upsertChatThreadMetadata(th)
        }
        for (msg in backup.chatMessages) {
            dao.insertChatMessage(msg.copy(id = 0L))
        }

        return importedTxCount
    }
}
