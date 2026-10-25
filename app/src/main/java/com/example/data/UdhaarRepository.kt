package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "udhaar_settings")

class UdhaarRepository(
    private val dao: UdhaarDao,
    private val context: Context? = null
) {
    private object PrefKeys {
        val UPI_ID = stringPreferencesKey("upi_id")
        val PAYEE_NAME = stringPreferencesKey("payee_name")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
    }

    val allTransactions: Flow<List<UdhaarTransaction>> = dao.getAllTransactions()
    val allPartialPayments: Flow<List<PartialPayment>> = dao.getAllPartialPayments()

    val userSettings: Flow<UserSettings> = context?.dataStore?.data?.map { prefs ->
        val currencyStr = prefs[PrefKeys.DEFAULT_CURRENCY] ?: CurrencyType.INR.name
        val currency = runCatching { CurrencyType.valueOf(currencyStr) }.getOrDefault(CurrencyType.INR)
        UserSettings(
            upiId = prefs[PrefKeys.UPI_ID] ?: "",
            payeeName = prefs[PrefKeys.PAYEE_NAME] ?: "",
            defaultCurrency = currency
        )
    } ?: kotlinx.coroutines.flow.flowOf(UserSettings())

    fun getPartialPayments(transactionId: Long): Flow<List<PartialPayment>> =
        dao.getPartialPaymentsForTransaction(transactionId)

    suspend fun getTransactionById(id: Long): UdhaarTransaction? =
        dao.getTransactionById(id)

    suspend fun saveTransaction(transaction: UdhaarTransaction): Long =
        dao.insertTransaction(transaction)

    suspend fun updateTransaction(transaction: UdhaarTransaction) =
        dao.updateTransaction(transaction)

    suspend fun deleteTransaction(transaction: UdhaarTransaction) =
        dao.deleteTransaction(transaction)

    suspend fun recordPartialPayment(
        transaction: UdhaarTransaction,
        paymentAmount: Double,
        note: String,
        totalAccruedAmount: Double
    ) {
        val cleanPayment = paymentAmount.coerceAtLeast(0.0)
        if (cleanPayment <= 0.0) return

        dao.insertPartialPayment(
            PartialPayment(
                transactionId = transaction.id,
                amount = cleanPayment,
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

    suspend fun updateSettings(upiId: String, payeeName: String, defaultCurrency: CurrencyType) {
        context?.dataStore?.edit { prefs ->
            prefs[PrefKeys.UPI_ID] = upiId.trim()
            prefs[PrefKeys.PAYEE_NAME] = payeeName.trim()
            prefs[PrefKeys.DEFAULT_CURRENCY] = defaultCurrency.name
        }
    }
}
