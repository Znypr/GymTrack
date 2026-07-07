package com.example.gymtrack.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "training_summary_outbox",
    indices = [
        Index(value = ["workout_id", "schema_version"], unique = true),
        Index(value = ["status"]),
    ],
)
data class TrainingSummaryOutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "summary_key") val summaryKey: String,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    val status: String,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "source_updated_at") val sourceUpdatedAt: String,
)
