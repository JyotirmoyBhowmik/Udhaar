package com.jyotirmoyb.udhaar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jyotirmoyb.udhaar.security.PinSecurityManager
import com.jyotirmoyb.udhaar.security.PinVerificationResult
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.util.InterestCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "udhaar_settings")

class UdhaarRepository(
    private val dao: UdhaarDao,
    private val context: Context? = null
) {
    val pinSecurityManager: PinSecurityManager? = context?.let { PinSecurityManager(it) }

    private object PrefKeys {
        val UPI_ID = stringPreferencesKey("upi_id")
        val PAYEE_NAME = stringPreferencesKey("payee_name")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val INR_ENABLED = booleanPreferencesKey("inr_enabled")
        val NPR_ENABLED = booleanPreferencesKey("npr_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_SCALE = stringPreferencesKey("font_scale")
        val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("app_lock_opt_in_enabled")
        val PIN_CONFIGURED_REVISION = stringPreferencesKey("pin_configured_revision")
        val LEGACY_SECURITY_PIN = stringPreferencesKey("security_pin")
    }

    val allParties: Flow<List<PartyEntity>> = dao.getAllParties()
    val allTransactions: Flow<List<UdhaarTransaction>> = dao.getAllTransactions()
    val allPartialPayments: Flow<List<PartialPayment>> = dao.getAllPartialPayments()

    val userSettings: Flow<UserSettings> = context?.dataStore?.data
        ?.catch { emit(emptyPreferences()) }
        ?.map { prefs ->
        val inrEnabled = prefs[PrefKeys.INR_ENABLED] ?: true
        val nprEnabled = prefs[PrefKeys.NPR_ENABLED] ?: false // Disabled by default
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
            defaultCurrency = effectiveDefaultCurrency,
            inrEnabled = safeInr,
            nprEnabled = nprEnabled,
            themeMode = themeMode,
            fontScale = fontScale,
            biometricLockEnabled = lockRequested && hasPin,
            hasPinConfigured = hasPin
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

    // --- Transaction Operations ---
    fun getPartialPayments(transactionId: Long): Flow<List<PartialPayment>> =
        dao.getPartialPaymentsForTransaction(transactionId)

    suspend fun getTransactionById(id: Long): UdhaarTransaction? =
        dao.getTransactionById(id)

    suspend fun saveTransaction(transaction: UdhaarTransaction): Long {
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

        return dao.insertTransaction(
            transaction.copy(
                partyId = linkedPartyId,
                partyName = transaction.partyName.trim(),
                phoneNumber = formattedPhone
            )
        )
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
        totalAccruedAmount: Double
    ) {
        val cleanPayment = paymentAmount.coerceAtLeast(0.0)
        if (cleanPayment <= 0.0) return

        dao.insertPartialPayment(
            PartialPayment(
                transactionId = transaction.id,
                amount = cleanPayment,
                paymentMode = paymentMode,
                paidAtMillis = System.currentTimeMillis(),
                note = note.trim()
            )
        )
        val updatedPaid = transaction.amountPaid + cleanPayment
        val nowSettled = updatedPaid >= (totalAccruedAmount - 0.01)
        dao.updateTransaction(
            transaction.copy(
                amountPaid = updatedPaid,
                isSettled = nowSettled,
                settledAtMillis = if (nowSettled) System.currentTimeMillis() else transaction.settledAtMillis
            )
        )
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
        if (transaction.isSettled) {
            dao.updateTransaction(
                transaction.copy(
                    isSettled = false,
                    settledAtMillis = null
                )
            )
        } else {
            dao.updateTransaction(
                transaction.copy(
                    isSettled = true,
                    settledAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun updateSettings(
        upiId: String,
        payeeName: String,
        defaultCurrency: CurrencyType,
        inrEnabled: Boolean? = null,
        nprEnabled: Boolean? = null,
        themeMode: AppThemeMode? = null,
        fontScale: AppFontScale? = null,
        biometricLockEnabled: Boolean? = null,
        securityPin: String? = null
    ) {
        if (!securityPin.isNullOrBlank()) {
            pinSecurityManager?.setPin(securityPin.trim())
        }
        val hasPinNow = pinSecurityManager?.hasPinConfigured() ?: false

        context?.dataStore?.edit { prefs ->
            prefs.remove(PrefKeys.LEGACY_SECURITY_PIN)
            prefs[PrefKeys.UPI_ID] = upiId.trim()
            prefs[PrefKeys.PAYEE_NAME] = payeeName.trim()
            val nextInr = inrEnabled ?: (prefs[PrefKeys.INR_ENABLED] ?: true)
            val nextNpr = nprEnabled ?: (prefs[PrefKeys.NPR_ENABLED] ?: false)
            // Ensure at least one currency stays enabled
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
            val insertedTxId = dao.insertTransaction(
                tx.copy(
                    id = 0L,
                    partyId = mappedPartyId
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
