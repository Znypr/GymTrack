package com.example.gymtrack.core.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * One exercise in one finished workout, summarised from its WORKING sets (#307). Suggestions read
 * the last few rows per exercise instead of querying `sets` or reparsing notes.
 *
 * Written in the same transaction as the workout's `sets` rows (Finish, history edit, override)
 * and deleted with the workout. `exercise_id` is the legacy `exercises.exerciseId`, which is
 * already the canonical-name identity the parser resolves.
 */
@Entity(
    tableName = "exercise_recent",
    primaryKeys = ["workout_id", "exercise_id"],
    indices = [Index(value = ["exercise_id", "workout_id"])],
)
data class ExerciseRecentEntity(
    /** Workout start timestamp (`notes.timestamp`). Newest first = largest first. */
    @ColumnInfo(name = "workout_id") val workoutId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    @ColumnInfo(name = "working_sets") val workingSets: Int,
    @ColumnInfo(name = "warmup_sets") val warmupSets: Int,
    @ColumnInfo(name = "working_reps") val workingReps: Int,
    /** Heaviest working load as typed, with [topWeightUnit]; 0 for bodyweight/timed only. */
    @ColumnInfo(name = "top_weight") val topWeight: Float,
    @ColumnInfo(name = "top_weight_unit") val topWeightUnit: String,
    /** Reps of the first working set at [topWeight]. */
    @ColumnInfo(name = "top_reps") val topReps: Int,
    /** Sum of load × reps of the working sets, in kilograms. */
    @ColumnInfo(name = "volume_kg") val volumeKg: Float,
    /** Working sets in editor grammar, e.g. `8x 80 · 8x 80 · 6x 80`. */
    @ColumnInfo(name = "sets_summary") val setsSummary: String,
)

/**
 * Effective working sets per muscle per ISO week (#307), tagged with the workout so an edit or a
 * delete rewrites only that workout's rows.
 *
 * Until the muscle map lands, [muscle] is a stub: the exercise's `muscleGroup` when set, else
 * `exercise:<exerciseId>`, and [effectiveSets] is the plain working-set count. The muscle-map work
 * replaces [DerivedIndexWriter.muscleKeysFor] and bumps [DerivedIndexWriter.VERSION].
 */
@Entity(
    tableName = "muscle_week",
    primaryKeys = ["workout_id", "muscle"],
    indices = [Index(value = ["week_key", "muscle"])],
)
data class MuscleWeekEntity(
    @ColumnInfo(name = "workout_id") val workoutId: Long,
    /** ISO week of the workout in the device zone at write time, e.g. `2026-W45`. */
    @ColumnInfo(name = "week_key") val weekKey: String,
    val muscle: String,
    @ColumnInfo(name = "effective_sets") val effectiveSets: Float,
)

/** Version stamp and progress of a derived index (#307). One row per index name. */
@Entity(tableName = "derived_index_state")
data class DerivedIndexStateEntity(
    @androidx.room.PrimaryKey val name: String,
    val version: Int,
    /** Highest workout id already rebuilt; the lazy rebuild continues after it. */
    @ColumnInfo(name = "rebuilt_through") val rebuiltThrough: Long,
    val complete: Boolean,
)

data class ExerciseMuscleRow(val exerciseId: Long, val muscleGroup: String?)

data class MuscleWeekTotal(
    @ColumnInfo(name = "week_key") val weekKey: String,
    val muscle: String,
    @ColumnInfo(name = "effective_sets") val effectiveSets: Float,
)

@Dao
interface DerivedIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(rows: List<ExerciseRecentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMuscleWeek(rows: List<MuscleWeekEntity>)

    @Query("DELETE FROM exercise_recent WHERE workout_id = :workoutId")
    suspend fun deleteRecentForWorkout(workoutId: Long)

    @Query("DELETE FROM muscle_week WHERE workout_id = :workoutId")
    suspend fun deleteMuscleWeekForWorkout(workoutId: Long)

    @Query("DELETE FROM exercise_recent")
    suspend fun deleteAllRecent()

    @Query("DELETE FROM muscle_week")
    suspend fun deleteAllMuscleWeek()

    @Query("DELETE FROM exercise_recent WHERE workout_id NOT IN (SELECT timestamp FROM notes)")
    suspend fun deleteOrphanedRecent()

    @Query("DELETE FROM muscle_week WHERE workout_id NOT IN (SELECT timestamp FROM notes)")
    suspend fun deleteOrphanedMuscleWeek()

    /** The newest [limit] sessions of one exercise. */
    @Query("SELECT * FROM exercise_recent WHERE exercise_id = :exerciseId ORDER BY workout_id DESC LIMIT :limit")
    suspend fun recentForExercise(exerciseId: Long, limit: Int): List<ExerciseRecentEntity>

    /** The newest [perExercise] sessions of each of [exerciseIds], newest first per exercise. */
    @Query(
        """
        SELECT * FROM exercise_recent AS r
        WHERE r.exercise_id IN (:exerciseIds)
          AND (SELECT COUNT(*) FROM exercise_recent AS n
               WHERE n.exercise_id = r.exercise_id AND n.workout_id > r.workout_id) < :perExercise
        ORDER BY r.exercise_id, r.workout_id DESC
        """,
    )
    suspend fun recentForExercises(exerciseIds: List<Long>, perExercise: Int): List<ExerciseRecentEntity>

    @Query("SELECT * FROM exercise_recent WHERE workout_id = :workoutId ORDER BY exercise_id")
    suspend fun recentForWorkout(workoutId: Long): List<ExerciseRecentEntity>

    @Query(
        """
        SELECT week_key, muscle, CAST(SUM(effective_sets) AS REAL) AS effective_sets
        FROM muscle_week WHERE week_key = :weekKey
        GROUP BY week_key, muscle ORDER BY effective_sets DESC, muscle
        """,
    )
    suspend fun muscleWeek(weekKey: String): List<MuscleWeekTotal>

    @Query("SELECT * FROM derived_index_state WHERE name = :name LIMIT 1")
    suspend fun state(name: String): DerivedIndexStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putState(state: DerivedIndexStateEntity)

    @Query("DELETE FROM derived_index_state")
    suspend fun clearStates()

    @Query("SELECT exerciseId, muscleGroup FROM exercises WHERE exerciseId IN (:exerciseIds)")
    suspend fun muscleGroups(exerciseIds: List<Long>): List<ExerciseMuscleRow>

    /** Next [limit] workout ids with set rows after [after], oldest first (lazy rebuild chunk). */
    @Query("SELECT DISTINCT workoutId FROM sets WHERE workoutId > :after ORDER BY workoutId LIMIT :limit")
    suspend fun workoutIdsWithSetsAfter(after: Long, limit: Int): List<Long>
}
