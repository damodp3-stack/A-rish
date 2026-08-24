package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.data.local.entity.IdempotencyEntity

@Dao
interface IdempotencyDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecord(record: IdempotencyEntity)

    @Query("SELECT * FROM idempotency_records WHERE idempotency_key = :key")
    suspend fun getRecordByKey(key: String): IdempotencyEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM idempotency_records WHERE idempotency_key = :key)")
    suspend fun hasRecord(key: String): Boolean

    @Query("DELETE FROM idempotency_records WHERE executed_at < :cutoffTimestamp")
    suspend fun pruneOldRecords(cutoffTimestamp: Long): Int
}
