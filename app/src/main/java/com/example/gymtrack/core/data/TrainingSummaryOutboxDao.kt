package com.example.gymtrack.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrainingSummaryOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TrainingSummaryOutboxEntity)

    @Query("SELECT * FROM training_summary_outbox WHERE summary_key = :summaryKey LIMIT 1")
    suspend fun get(summaryKey: String): TrainingSummaryOutboxEntity?

    @Query("SELECT * FROM training_summary_outbox WHERE status = 'PENDING' ORDER BY updated_at ASC, summary_key ASC")
    suspend fun getPending(): List<TrainingSummaryOutboxEntity>
}
