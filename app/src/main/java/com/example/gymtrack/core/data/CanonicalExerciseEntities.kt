package com.example.gymtrack.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [Index(value = ["position"])],
)
data class CanonicalCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Long,
    val position: Int,
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
)

@Entity(
    tableName = "canonical_exercises",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_exercise_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["normalized_name"]),
        Index(value = ["parent_exercise_id"]),
    ],
)
data class CanonicalExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "canonical_name") val canonicalName: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "parent_exercise_id") val parentExerciseId: String?,
    @ColumnInfo(name = "muscle_group") val muscleGroup: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "exercise_aliases",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["exercise_id"]),
        Index(value = ["exercise_id", "normalized_alias"], unique = true),
    ],
)
data class CanonicalExerciseAliasEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "normalized_alias") val normalizedAlias: String,
    @ColumnInfo(name = "original_alias") val originalAlias: String,
)
