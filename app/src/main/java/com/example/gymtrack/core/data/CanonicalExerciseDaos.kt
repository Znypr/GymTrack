package com.example.gymtrack.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CanonicalCategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CanonicalCategoryEntity): Long

    @Update
    suspend fun update(category: CanonicalCategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CanonicalCategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCount(): Int
}

@Dao
interface CanonicalExerciseDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exercise: CanonicalExerciseEntity): Long

    @Update
    suspend fun update(exercise: CanonicalExerciseEntity)

    @Query("SELECT * FROM canonical_exercises WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CanonicalExerciseEntity?

    @Query("SELECT * FROM canonical_exercises WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): CanonicalExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlias(alias: CanonicalExerciseAliasEntity): Long

    @Query("DELETE FROM exercise_aliases WHERE exercise_id = :exerciseId")
    suspend fun deleteAliasesForExercise(exerciseId: String)

    @Query("SELECT COUNT(*) FROM canonical_exercises")
    suspend fun getExerciseCount(): Int

    @Query("SELECT COUNT(*) FROM exercise_aliases")
    suspend fun getAliasCount(): Int
}
