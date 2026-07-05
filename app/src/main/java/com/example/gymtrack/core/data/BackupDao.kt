package com.example.gymtrack.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BackupDao {
    @Query("SELECT * FROM categories ORDER BY position, id")
    suspend fun getCanonicalCategories(): List<CanonicalCategoryEntity>

    @Query("SELECT * FROM canonical_exercises ORDER BY created_at, id")
    suspend fun getCanonicalExercises(): List<CanonicalExerciseEntity>

    @Query("SELECT * FROM exercise_aliases ORDER BY exercise_id, normalized_alias, id")
    suspend fun getCanonicalExerciseAliases(): List<CanonicalExerciseAliasEntity>

    @Query("SELECT * FROM workouts ORDER BY started_at, id")
    suspend fun getCanonicalWorkouts(): List<CanonicalWorkoutEntity>

    @Query("SELECT * FROM workout_exercises ORDER BY workout_id, position, id")
    suspend fun getCanonicalWorkoutExercises(): List<CanonicalWorkoutExerciseEntity>

    @Query("SELECT * FROM workout_sets ORDER BY workout_exercise_id, position, id")
    suspend fun getCanonicalWorkoutSets(): List<CanonicalWorkoutSetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanonicalCategories(values: List<CanonicalCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanonicalExercises(values: List<CanonicalExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanonicalExerciseAliases(values: List<CanonicalExerciseAliasEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanonicalWorkouts(values: List<CanonicalWorkoutEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanonicalWorkoutExercises(values: List<CanonicalWorkoutExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanonicalWorkoutSets(values: List<CanonicalWorkoutSetEntity>)

    @Query("DELETE FROM workout_sets")
    suspend fun deleteCanonicalWorkoutSets()

    @Query("DELETE FROM workout_exercises")
    suspend fun deleteCanonicalWorkoutExercises()

    @Query("DELETE FROM workouts")
    suspend fun deleteCanonicalWorkouts()

    @Query("DELETE FROM exercise_aliases")
    suspend fun deleteCanonicalExerciseAliases()

    @Query("DELETE FROM canonical_exercises")
    suspend fun deleteCanonicalExercises()

    @Query("DELETE FROM categories")
    suspend fun deleteCanonicalCategories()
}
