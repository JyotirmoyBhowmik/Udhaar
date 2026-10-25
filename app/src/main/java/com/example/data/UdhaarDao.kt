package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UdhaarDao {
    // --- Party Directory Queries ---
    @Query("SELECT * FROM parties ORDER BY name COLLATE NOCASE ASC")
    fun getAllParties(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllPartiesSnapshot(): List<PartyEntity>

    @Query("SELECT * FROM parties WHERE partyId = :partyId LIMIT 1")
    suspend fun getPartyById(partyId: Long): PartyEntity?

    @Query("SELECT * FROM parties WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getPartyByName(name: String): PartyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParty(party: PartyEntity): Long

    @Update
    suspend fun updateParty(party: PartyEntity)

    @Delete
    suspend fun deleteParty(party: PartyEntity)

    // --- Udhaar Transaction Queries ---
    @Query("SELECT * FROM udhaar_transactions ORDER BY isSettled ASC, createdAtMillis DESC")
    fun getAllTransactions(): Flow<List<UdhaarTransaction>>

    @Query("SELECT * FROM udhaar_transactions ORDER BY createdAtMillis DESC")
    suspend fun getAllTransactionsSnapshot(): List<UdhaarTransaction>

    @Query("SELECT * FROM udhaar_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): UdhaarTransaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: UdhaarTransaction): Long

    @Update
    suspend fun updateTransaction(transaction: UdhaarTransaction)

    @Delete
    suspend fun deleteTransaction(transaction: UdhaarTransaction)

    @Query("DELETE FROM udhaar_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    // --- Partial Payment Queries ---
    @Query("SELECT * FROM partial_payments WHERE transactionId = :transactionId ORDER BY paidAtMillis DESC")
    fun getPartialPaymentsForTransaction(transactionId: Long): Flow<List<PartialPayment>>

    @Query("SELECT * FROM partial_payments ORDER BY paidAtMillis DESC")
    fun getAllPartialPayments(): Flow<List<PartialPayment>>

    @Query("SELECT * FROM partial_payments ORDER BY paidAtMillis DESC")
    suspend fun getAllPartialPaymentsSnapshot(): List<PartialPayment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartialPayment(payment: PartialPayment): Long

    @Delete
    suspend fun deletePartialPayment(payment: PartialPayment)
}
