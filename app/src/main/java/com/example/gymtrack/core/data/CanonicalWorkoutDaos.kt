package com.example.gymtrack.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CanonicalWorkoutDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(workout: CanonicalWorkoutEntity): Long

    @Update
    suspend fun update(workout: CanonicalWorkoutEntity)

    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CanonicalWorkoutEntity?

    @Query("SELECT * FROM workouts WHERE legacy_timestamp = :legacyTimestamp LIMIT 1")
    suspend fun getByLegacyTimestamp(legacyTimestamp: Long): CanonicalWorkoutEntity?

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun getCount(): Int
}

@Dao
interface CanonicalWorkoutExerciseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(workoutExercises: List<CanonicalWorkoutExerciseEntity>)

    @Query("DELETE FROM workout_exercises WHERE workout_id = :workoutId")
    suspend fun deleteForWorkout(workoutId: String)

    @Query("SELECT * FROM workout_exercises WHERE workout_id = :workoutId ORDER BY position")
    suspend fun getForWorkout(workoutId: String): List<CanonicalWorkoutExerciseEntity>

    @Query("SELECT COUNT(*) FROM workout_exercises")
    suspend fun getCount(): Int
}

@Dao
interface CanonicalWorkoutSetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(workoutSets: List<CanonicalWorkoutSetEntity>)

    @Query("SELECT * FROM workout_sets WHERE workout_exercise_id IN (:workoutExerciseIds) ORDER BY workout_exercise_id, position")
    suspend fun getForWorkoutExercises(workoutExerciseIds: List<String>): List<CanonicalWorkoutSetEntity>

    @Query("SELECT COUNT(*) FROM workout_sets")
    suspend fun getCount(): Int
}
