package com.example.gymtrack.core.data.transition

import androidx.room.withTransaction
import com.example.gymtrack.core.data.CanonicalCategoryEntity
import com.example.gymtrack.core.data.CanonicalWorkoutEntity
import com.example.gymtrack.core.data.CanonicalWorkoutExerciseEntity
import com.example.gymtrack.core.data.CanonicalWorkoutSetEntity
import com.example.gymtrack.core.data.ExerciseEntity
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.NoteEntity
import kotlinx.coroutines.flow.first

/** The reads and writes [CanonicalImportRunner] needs; a seam so the import rule is JVM-testable. */
internal interface CanonicalImportStore {
    suspend fun notes(): List<NoteEntity>
    suspend fun legacyExercises(): List<ExerciseEntity>
    suspend fun legacyBackedWorkouts(): List<CanonicalWorkoutEntity>
    suspend fun workoutByLegacyTimestamp(legacyTimestamp: Long): CanonicalWorkoutEntity?
    suspend fun workoutExercises(workoutId: String): List<CanonicalWorkoutExerciseEntity>

    /** Sets of the given occurrences, ordered by workout exercise id, then position. */
    suspend fun workoutSets(workoutExerciseIds: List<String>): List<CanonicalWorkoutSetEntity>

    suspend fun <T> inTransaction(block: suspend () -> T): T
    suspend fun upsertCategory(category: CanonicalCategoryEntity)
    suspend fun upsertExercises(catalog: CanonicalExerciseCatalog)

    /** Updates only the workout row; exercise and set rows are untouched. */
    suspend fun updateWorkout(workout: CanonicalWorkoutEntity)

    /** Writes the workout row and replaces all of its exercise and set rows. */
    suspend fun replaceWorkout(
        workout: CanonicalWorkoutEntity,
        workoutExercises: List<CanonicalWorkoutExerciseEntity>,
        workoutSets: List<CanonicalWorkoutSetEntity>,
    )
}

internal class RoomCanonicalImportStore(
    private val database: NoteDatabase,
) : CanonicalImportStore {
    override suspend fun notes(): List<NoteEntity> = database.noteDao().getAll().first()

    override suspend fun legacyExercises(): List<ExerciseEntity> =
        database.exerciseDao().getAllExercises().first()

    override suspend fun legacyBackedWorkouts(): List<CanonicalWorkoutEntity> =
        database.canonicalWorkoutDao().getLegacyBacked()

    override suspend fun workoutByLegacyTimestamp(legacyTimestamp: Long): CanonicalWorkoutEntity? =
        database.canonicalWorkoutDao().getByLegacyTimestamp(legacyTimestamp)

    override suspend fun workoutExercises(workoutId: String): List<CanonicalWorkoutExerciseEntity> =
        database.canonicalWorkoutExerciseDao().getForWorkout(workoutId)

    override suspend fun workoutSets(workoutExerciseIds: List<String>): List<CanonicalWorkoutSetEntity> =
        if (workoutExerciseIds.isEmpty()) {
            emptyList()
        } else {
            database.canonicalWorkoutSetDao().getForWorkoutExercises(workoutExerciseIds)
        }

    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        database.withTransaction { block() }

    override suspend fun upsertCategory(category: CanonicalCategoryEntity) {
        database.canonicalCategoryDao().insert(category)
    }

    override suspend fun upsertExercises(catalog: CanonicalExerciseCatalog) {
        val dao = database.canonicalExerciseDao()
        val exercises = catalog.exerciseEntities()

        exercises.forEach { incoming ->
            val existing = dao.getById(incoming.id)
            if (existing == null) {
                dao.insert(incoming.copy(parentExerciseId = null))
            } else {
                val merged = existing.copy(
                    canonicalName = existing.canonicalName.ifBlank { incoming.canonicalName },
                    normalizedName = existing.normalizedName.ifBlank { incoming.normalizedName },
                    muscleGroup = existing.muscleGroup ?: incoming.muscleGroup,
                )
                if (merged != existing) dao.update(merged)
            }
        }

        exercises.forEach { incoming ->
            val existing = dao.getById(incoming.id) ?: return@forEach
            val merged = existing.copy(
                parentExerciseId = existing.parentExerciseId ?: incoming.parentExerciseId,
                muscleGroup = existing.muscleGroup ?: incoming.muscleGroup,
            )
            if (merged != existing) dao.update(merged)
        }

        catalog.aliasEntities().forEach { alias ->
            dao.insertAlias(alias)
        }
    }

    override suspend fun updateWorkout(workout: CanonicalWorkoutEntity) {
        database.canonicalWorkoutDao().update(workout)
    }

    override suspend fun replaceWorkout(
        workout: CanonicalWorkoutEntity,
        workoutExercises: List<CanonicalWorkoutExerciseEntity>,
        workoutSets: List<CanonicalWorkoutSetEntity>,
    ) {
        val inserted = database.canonicalWorkoutDao().insert(workout)
        if (inserted == -1L) database.canonicalWorkoutDao().update(workout)
        database.canonicalWorkoutExerciseDao().deleteForWorkout(workout.id)
        if (workoutExercises.isNotEmpty()) {
            database.canonicalWorkoutExerciseDao().insertAll(workoutExercises)
        }
        if (workoutSets.isNotEmpty()) {
            database.canonicalWorkoutSetDao().insertAll(workoutSets)
        }
    }
}
