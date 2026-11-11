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
 * Effective working sets per muscle, exercise and ISO week (#307, #309), tagged with the workout
 * so an edit or a delete rewrites only that workout's rows.
 *
 * [muscle] is a [com.example.gymtrack.core.muscle.Muscle.id], or
 * [com.example.gymtrack.core.muscle.WeeklyMuscleSummary.UNMAPPED_KEY] for the working sets of an
 * exercise with no mapping (then [effectiveSets] is the plain set count). Primary muscles get 1.0
 * per working set, secondary 0.5 ([com.example.gymtrack.core.muscle.MuscleLoadCalculator]).
 */
@Entity(
    tableName = "muscle_week",
    primaryKeys = ["workout_id", "muscle", "exercise_id"],
    indices = [Index(value = ["week_key", "muscle"])],
)
data class MuscleWeekEntity(
    @ColumnInfo(name = "workout_id") val workoutId: Long,
    /** ISO week of the workout in the device zone at write time, e.g. `2026-W45`. */
    @ColumnInfo(name = "week_key") val weekKey: String,
    val muscle: String,
    /** Legacy `exercises.exerciseId` the sets came from. */
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
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

/** Name and parent of one exercise, for the muscle resolver. */
data class ExerciseMuscleRefRow(val exerciseId: Long, val name: String, val parentId: Long?)

/** Per muscle and exercise sums of one week, with the exercise name. */
data class MuscleWeekExerciseRow(
    val muscle: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    val name: String?,
    @ColumnInfo(name = "effective_sets") val effectiveSets: Float,
)

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

    /**
     * Every exercise's name and parent for the muscle resolver (#309). The free-text
     * `exercises.muscleGroup` is deliberately not read: muscle stats come from the seed and the
     * user's `exercise_muscles` overrides only.
     */
    @Query("SELECT exerciseId, name, parentId FROM exercises")
    suspend fun exerciseMuscleRefs(): List<ExerciseMuscleRefRow>

    @Query(
        """
        SELECT m.muscle AS muscle, m.exercise_id AS exercise_id, e.name AS name,
               CAST(SUM(m.effective_sets) AS REAL) AS effective_sets
        FROM muscle_week AS m LEFT JOIN exercises AS e ON e.exerciseId = m.exercise_id
        WHERE m.week_key = :weekKey
        GROUP BY m.muscle, m.exercise_id ORDER BY m.muscle, effective_sets DESC
        """,
    )
    suspend fun muscleWeekByExercise(weekKey: String): List<MuscleWeekExerciseRow>

    /** Workouts with set rows of any of [exerciseIds] (re-derive after a mapping edit). */
    @Query("SELECT DISTINCT workoutId FROM sets WHERE exerciseId IN (:exerciseIds) ORDER BY workoutId")
    suspend fun workoutIdsForExercises(exerciseIds: List<Long>): List<Long>

    /** Next [limit] workout ids with set rows after [after], oldest first (lazy rebuild chunk). */
    @Query("SELECT DISTINCT workoutId FROM sets WHERE workoutId > :after ORDER BY workoutId LIMIT :limit")
    suspend fun workoutIdsWithSetsAfter(after: Long, limit: Int): List<Long>
}
