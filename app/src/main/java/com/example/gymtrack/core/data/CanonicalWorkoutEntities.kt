package com.example.gymtrack.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["legacy_timestamp"], unique = true),
        Index(value = ["started_at"]),
        Index(value = ["category_id"]),
        Index(value = ["status"]),
    ],
)
data class CanonicalWorkoutEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "legacy_timestamp") val legacyTimestamp: Long?,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    val title: String,
    val learnings: String,
    val status: String,
    @ColumnInfo(name = "raw_draft_text") val rawDraftText: String?,
    @ColumnInfo(name = "legacy_migration_status") val legacyMigrationStatus: String?,
    @ColumnInfo(name = "legacy_migration_message") val legacyMigrationMessage: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalWorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CanonicalExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["workout_id"]),
        Index(value = ["exercise_id"]),
        Index(value = ["workout_id", "exercise_id"]),
        Index(value = ["workout_id", "position"], unique = true),
    ],
)
data class CanonicalWorkoutExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val position: Int,
    val mode: String,
    val modifier: String?,
    @ColumnInfo(name = "equipment_brand") val equipmentBrand: String?,
    @ColumnInfo(name = "started_at_offset_seconds") val startedAtOffsetSeconds: Int?,
    @ColumnInfo(name = "started_at") val startedAt: Long?,
    @ColumnInfo(name = "legacy_relative_time_text") val legacyRelativeTimeText: String?,
    @ColumnInfo(name = "legacy_absolute_time_text") val legacyAbsoluteTimeText: String?,
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalWorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["workout_exercise_id"]),
        Index(value = ["workout_exercise_id", "position"], unique = true),
    ],
)
data class CanonicalWorkoutSetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workout_exercise_id") val workoutExerciseId: String,
    val position: Int,
    val repetitions: Int?,
    val weight: Double?,
    @ColumnInfo(name = "weight_unit") val weightUnit: String?,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int?,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double?,
    @ColumnInfo(name = "performed_at_offset_seconds") val performedAtOffsetSeconds: Int?,
    val rpe: Double?,
    val rir: Double?,
)
