package com.example.gymtrack.core.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.gymtrack.core.muscle.MuscleOverride
import com.example.gymtrack.core.muscle.Muscle
import com.example.gymtrack.core.muscle.MuscleRole

/**
 * The user's own muscle mapping of one exercise (#310): one row per muscle. Only user edits are
 * stored; the seed stays in code ([com.example.gymtrack.core.muscle.MuscleMappingSeed]). An
 * exercise with rows uses exactly those rows; deleting them resets it to the seed.
 *
 * Keyed by the legacy `exercises.exerciseId`, which `sets` and the derived `muscle_week` rows use.
 * Editing never rewrites logged sets; history re-renders because the derived rows of the
 * exercise's workouts are rebuilt (ADR 0003).
 */
@Entity(
    tableName = "exercise_muscles",
    primaryKeys = ["exercise_id", "muscle"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["exerciseId"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["exercise_id"])],
)
data class ExerciseMuscleEntity(
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    /** [Muscle.id]. */
    val muscle: String,
    /** [MuscleRole.storageValue]: PRIMARY or SECONDARY. */
    val role: String,
    /** Always [SOURCE_USER]; the column leaves room for imported mappings later. */
    val source: String = SOURCE_USER,
) {
    companion object {
        const val SOURCE_USER = "USER"
    }
}

/** Rows with an unknown muscle id or role (a newer app's backup) are skipped, never guessed. */
fun List<ExerciseMuscleEntity>.toOverrides(): Map<Long, List<MuscleOverride>> = mapNotNull { row ->
    val muscle = Muscle.fromId(row.muscle) ?: return@mapNotNull null
    val role = MuscleRole.fromStorage(row.role) ?: return@mapNotNull null
    row.exerciseId to MuscleOverride(muscle, role)
}.groupBy({ it.first }, { it.second })

@Dao
interface ExerciseMuscleDao {
    @Query("SELECT * FROM exercise_muscles ORDER BY exercise_id, muscle")
    suspend fun getAll(): List<ExerciseMuscleEntity>

    @Query("SELECT * FROM exercise_muscles WHERE exercise_id IN (:exerciseIds) ORDER BY exercise_id, muscle")
    suspend fun getForExercises(exerciseIds: List<Long>): List<ExerciseMuscleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ExerciseMuscleEntity>)

    @Query("DELETE FROM exercise_muscles WHERE exercise_id IN (:exerciseIds)")
    suspend fun deleteForExercises(exerciseIds: List<Long>)

    @Query("DELETE FROM exercise_muscles")
    suspend fun deleteAll()

    /** Replaces the mapping of [exerciseIds] with [rows]; empty [rows] resets them to the seed. */
    @Transaction
    suspend fun replaceForExercises(exerciseIds: List<Long>, rows: List<ExerciseMuscleEntity>) {
        deleteForExercises(exerciseIds)
        if (rows.isNotEmpty()) insertAll(rows)
    }
}
