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
    @Query("SELECT * FROM udhaar_transactions ORDER BY isSettled ASC, createdAtMillis DESC")
    fun getAllTransactions(): Flow<List<UdhaarTransaction>>

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

    @Query("SELECT * FROM partial_payments WHERE transactionId = :transactionId ORDER BY paidAtMillis DESC")
    fun getPartialPaymentsForTransaction(transactionId: Long): Flow<List<PartialPayment>>

    @Query("SELECT * FROM partial_payments ORDER BY paidAtMillis DESC")
    fun getAllPartialPayments(): Flow<List<PartialPayment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartialPayment(payment: PartialPayment): Long

    @Delete
    suspend fun deletePartialPayment(payment: PartialPayment)
}
