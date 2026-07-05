package com.example.gymtrack.core.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "training_summary_outbox",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalWorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["enqueued_at"])],
)
data class TrainingSummaryOutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "workout_id")
    val workoutId: String,
    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "source_updated_at")
    val sourceUpdatedAt: Long,
    @ColumnInfo(name = "enqueued_at")
    val enqueuedAt: Long,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
)

@Dao
interface TrainingSummaryOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TrainingSummaryOutboxEntity)

    @Query("SELECT * FROM training_summary_outbox WHERE workout_id = :workoutId LIMIT 1")
    suspend fun getByWorkoutId(workoutId: String): TrainingSummaryOutboxEntity?

    @Query("SELECT * FROM training_summary_outbox ORDER BY enqueued_at, workout_id LIMIT :limit")
    suspend fun getPending(limit: Int): List<TrainingSummaryOutboxEntity>

    @Query(
        """
        UPDATE training_summary_outbox
        SET attempt_count = attempt_count + 1,
            last_error = :message
        WHERE workout_id = :workoutId
        """,
    )
    suspend fun markFailed(workoutId: String, message: String?)

    @Query("DELETE FROM training_summary_outbox WHERE workout_id = :workoutId")
    suspend fun deleteByWorkoutId(workoutId: String)

    @Query("SELECT COUNT(*) FROM training_summary_outbox")
    suspend fun getCount(): Int
}
