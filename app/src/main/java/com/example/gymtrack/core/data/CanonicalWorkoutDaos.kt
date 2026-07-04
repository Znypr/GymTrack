package com.example.gymtrack.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CanonicalWorkoutDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(workout: CanonicalWorkoutEntity)

    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CanonicalWorkoutEntity?

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun getCount(): Int
}

@Dao
interface CanonicalWorkoutExerciseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(workoutExercise: CanonicalWorkoutExerciseEntity)

    @Query("SELECT COUNT(*) FROM workout_exercises")
    suspend fun getCount(): Int
}

@Dao
interface CanonicalWorkoutSetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(workoutSet: CanonicalWorkoutSetEntity)

    @Query("SELECT COUNT(*) FROM workout_sets")
    suspend fun getCount(): Int
}
