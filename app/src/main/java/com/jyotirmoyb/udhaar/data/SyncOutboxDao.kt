package com.jyotirmoyb.udhaar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entry: SyncOutboxEntity): Long

    @Query("SELECT * FROM sync_outbox WHERE (status = 'PENDING' OR status = 'FAILED') AND nextRetryAtMs <= :now ORDER BY id ASC LIMIT :limit")
    suspend fun getPendingMutations(now: Long = System.currentTimeMillis(), limit: Int = 100): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_outbox WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Update
    suspend fun update(entry: SyncOutboxEntity)

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status = 'PENDING' OR status = 'FAILED'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun getTotalCount(): Int

    @Query("SELECT * FROM sync_outbox ORDER BY id ASC")
    suspend fun getAllSnapshot(): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox")
    suspend fun clearAll()
}
