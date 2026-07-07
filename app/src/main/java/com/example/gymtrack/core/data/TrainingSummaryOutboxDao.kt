package com.example.gymtrack.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.gymtrack.domain.summary.TRAINING_SUMMARY_OUTBOX_STATUS_PENDING

@Dao
interface TrainingSummaryOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TrainingSummaryOutboxEntity)

    @Query("SELECT * FROM training_summary_outbox WHERE summary_key = :summaryKey LIMIT 1")
    suspend fun get(summaryKey: String): TrainingSummaryOutboxEntity?

    @Query("SELECT * FROM training_summary_outbox WHERE status = :status ORDER BY updated_at ASC, summary_key ASC")
    suspend fun getByStatus(status: String = TRAINING_SUMMARY_OUTBOX_STATUS_PENDING): List<TrainingSummaryOutboxEntity>
}
