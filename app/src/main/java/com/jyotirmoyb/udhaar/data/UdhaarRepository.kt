package com.jyotirmoyb.udhaar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jyotirmoyb.udhaar.data.cloud.CloudAuditEvent
import com.jyotirmoyb.udhaar.data.cloud.CloudPartialPayment
import com.jyotirmoyb.udhaar.data.cloud.CloudSharedRecord
import com.jyotirmoyb.udhaar.data.cloud.CloudUserProfile
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
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
    }

    val allParties: Flow<List<PartyEntity>> = dao.getAllParties()
    val allTransactions: Flow<List<UdhaarTransaction>> = dao.getAllTransactions()
    val allPartialPayments: Flow<List<PartialPayment>> = dao.getAllPartialPayments()
    val allAuditTrailEntries: Flow<List<AuditTrailEntry>> = dao.getAllAuditTrailEntries()

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

            val themeStr = prefs[PrefKeys.THEME_MODE] ?: AppThemeMode.MONOCHROME_LIGHT.name
            val themeMode = runCatching { AppThemeMode.valueOf(themeStr) }.getOrDefault(AppThemeMode.MONOCHROME_LIGHT)
            val fontStr = prefs[PrefKeys.FONT_SCALE] ?: AppFontScale.STANDARD.name
            val fontScale = runCatching { AppFontScale.valueOf(fontStr) }.getOrDefault(AppFontScale.STANDARD)

            // Migrate any custom legacy PIN (ignoring the old insecure "0000" default)
            val legacyPin = prefs[PrefKeys.LEGACY_SECURITY_PIN]?.trim().orEmpty()
            if (legacyPin.length in 4..8 && legacyPin != "0000" && pinSecurityManager?.hasPinConfigured() == false) {
                pinSecurityManager.setPin(legacyPin)
            }

            val hasPin = pinSecurityManager?.hasPinConfigured() ?: false
            val lockRequested = prefs[PrefKeys.BIOMETRIC_LOCK_ENABLED] ?: false

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
                cloudUserEmail = prefs[PrefKeys.CLOUD_USER_EMAIL] ?: ""
            )
        } ?: kotlinx.coroutines.flow.flowOf(UserSettings())

    // --- Party Directory Operations ---
    suspend fun saveParty(party: PartyEntity): Long {
        val formattedPhone = InterestCalculator.formatPhoneWithCountryCode(
            party.phoneNumber,
            party.preferredCurrency
        )
        return dao.insertParty(
            party.copy(
                name = party.name.trim(),
                phoneNumber = formattedPhone,
                upiId = party.upiId.trim(),
                email = party.email.trim(),
                tagOrNotes = party.tagOrNotes.trim()
            )
        )
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
        actorName: String = "You"
    ) {
        val cleanPayment = paymentAmount.coerceAtLeast(0.0)
        if (cleanPayment <= 0.0) return

        val settings = userSettings.first()
        val effectiveActor = actorName.trim().ifEmpty { settings.payeeName.ifBlank { "You" } }
        val remotePaymentId = "pay_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val nowMillis = System.currentTimeMillis()
        val isSharedRecord = transaction.verificationStatus != VerificationStatus.LOCAL_ONLY

        dao.insertPartialPayment(
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
        if (current.cloudUserId.isNotBlank()) {
            runCatching {
                cloudRepository?.deleteAllMyCloudData(current.cloudUserId, remoteIds)
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
        partialPayments: List<PartialPayment>
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

        for (payment in partialPayments) {
            val targetTxId = txIdMap[payment.transactionId]
            if (targetTxId != null) {
                dao.insertPartialPayment(
                    payment.copy(
                        id = 0L,
                        transactionId = targetTxId
                    )
                )
            }
        }
        return importedTxCount
    }
}
