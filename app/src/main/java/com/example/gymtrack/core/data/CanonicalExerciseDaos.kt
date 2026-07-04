package com.example.gymtrack.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CanonicalCategoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CanonicalCategoryEntity)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCount(): Int
}

@Dao
interface CanonicalExerciseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exercise: CanonicalExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAlias(alias: CanonicalExerciseAliasEntity)

    @Query("SELECT COUNT(*) FROM canonical_exercises")
    suspend fun getExerciseCount(): Int

    @Query("SELECT COUNT(*) FROM exercise_aliases")
    suspend fun getAliasCount(): Int
}
