package com.example.gymtrack.core.data.canonical

import androidx.room.withTransaction
import com.example.gymtrack.core.data.CanonicalExerciseAliasEntity
import com.example.gymtrack.core.data.CanonicalExerciseEntity
import com.example.gymtrack.core.data.CanonicalWorkoutEntity
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.transition.CanonicalKeys
import com.example.gymtrack.domain.model.Exercise
import com.example.gymtrack.domain.model.WorkoutDetails
import com.example.gymtrack.domain.model.WorkoutRecord
import com.example.gymtrack.domain.model.WorkoutStatus
import com.example.gymtrack.domain.repository.CanonicalWorkoutRepository

class RoomCanonicalWorkoutRepository(
    private val database: NoteDatabase,
) : CanonicalWorkoutRepository {
    override suspend fun getById(workoutId: String): WorkoutDetails? = database.withTransaction {
        database.canonicalWorkoutDao().getById(workoutId)?.let { load(it) }
    }

    override suspend fun getByLegacyTimestamp(legacyTimestamp: Long): WorkoutDetails? =
        database.withTransaction {
            database.canonicalWorkoutDao().getByLegacyTimestamp(legacyTimestamp)?.let { load(it) }
        }

    override suspend fun getRecentCompleted(limit: Int): List<WorkoutDetails> {
        if (limit <= 0) return emptyList()
        return database.withTransaction {
            database.canonicalWorkoutDao()
                .getRecentCompleted(
                    completedStatus = WorkoutStatus.COMPLETED.name,
                    limit = limit,
                )
                .map { load(it) }
        }
    }

    override suspend fun save(details: WorkoutDetails) {
        database.withTransaction {
            details.category?.let { category ->
                val entity = category.toEntity()
                val inserted = database.canonicalCategoryDao().insert(entity)
                if (inserted == -1L) database.canonicalCategoryDao().update(entity)
            }

            saveExerciseDefinitions(
                definitions = details.exerciseDefinitions.values.toList(),
                createdAt = details.record.workout.createdAtEpochMillis,
                updatedAt = details.record.workout.updatedAtEpochMillis,
            )

            val workoutEntity = details.record.workout.toEntity()
            val inserted = database.canonicalWorkoutDao().insert(workoutEntity)
            if (inserted == -1L) database.canonicalWorkoutDao().update(workoutEntity)

            database.canonicalWorkoutExerciseDao().deleteForWorkout(workoutEntity.id)

            val workoutExercises = details.record.exercises
                .sortedWith(compareBy({ it.position }, { it.id }))
                .map { it.toEntity() }
            if (workoutExercises.isNotEmpty()) {
                database.canonicalWorkoutExerciseDao().insertAll(workoutExercises)
            }

            val positionByOccurrence = workoutExercises.associate { it.id to it.position }
            val workoutSets = details.record.sets
                .sortedWith(
                    compareBy(
                        { positionByOccurrence[it.workoutExerciseId] ?: Int.MAX_VALUE },
                        { it.position },
                        { it.id },
                    ),
                )
                .map { it.toEntity() }
            if (workoutSets.isNotEmpty()) {
                database.canonicalWorkoutSetDao().insertAll(workoutSets)
            }
        }
    }

    private suspend fun load(workoutEntity: CanonicalWorkoutEntity): WorkoutDetails {
        val workoutExercises = database.canonicalWorkoutExerciseDao()
            .getForWorkout(workoutEntity.id)
        val workoutSets = database.canonicalWorkoutSetDao()
            .getForWorkout(workoutEntity.id)

        val exerciseIds = workoutExercises.map { it.exerciseId }.distinct()
        val exerciseEntities = if (exerciseIds.isEmpty()) {
            emptyList()
        } else {
            database.canonicalExerciseDao().getByIds(exerciseIds)
        }
        val aliases = if (exerciseIds.isEmpty()) {
            emptyList()
        } else {
            database.canonicalExerciseDao().getAliasesForExercises(exerciseIds)
        }

        val definitions = exerciseEntities.associate { entity ->
            entity.id to entity.toDomain(aliases)
        }
        val missingDefinitions = exerciseIds.filterNot(definitions::containsKey)
        if (missingDefinitions.isNotEmpty()) {
            throw CanonicalMappingException(
                "Missing exercise definitions: ${missingDefinitions.joinToString()}",
            )
        }

        val category = workoutEntity.categoryId?.let { categoryId ->
            database.canonicalCategoryDao().getById(categoryId)?.toDomain()
                ?: throw CanonicalMappingException("Missing category definition: $categoryId")
        }

        val record = WorkoutRecord(
            workout = workoutEntity.toDomain(),
            exercises = workoutExercises.map { it.toDomain() },
            sets = workoutSets.map { it.toDomain() },
        )
        return WorkoutDetails(
            record = record,
            exerciseDefinitions = definitions,
            category = category,
        )
    }

    private suspend fun saveExerciseDefinitions(
        definitions: List<Exercise>,
        createdAt: Long,
        updatedAt: Long,
    ) {
        val sorted = definitions.sortedBy { it.id }
        val entities = sorted.map { definition ->
            val existing = database.canonicalExerciseDao().getById(definition.id)
            definition.toEntity(
                createdAt = existing?.createdAt ?: createdAt,
                updatedAt = updatedAt,
            )
        }

        entities.forEach { entity ->
            upsertExercise(entity.copy(parentExerciseId = null))
        }
        entities.forEach { entity -> upsertExercise(entity) }

        sorted.forEach { definition ->
            database.canonicalExerciseDao().deleteAliasesForExercise(definition.id)
            definition.aliases
                .sortedBy(CanonicalKeys::normalize)
                .forEach { alias ->
                    database.canonicalExerciseDao().insertAlias(
                        CanonicalExerciseAliasEntity(
                            id = CanonicalKeys.alias(definition.id, alias),
                            exerciseId = definition.id,
                            normalizedAlias = CanonicalKeys.normalize(alias),
                            originalAlias = alias,
                        ),
                    )
                }
        }
    }

    private suspend fun upsertExercise(entity: CanonicalExerciseEntity) {
        val inserted = database.canonicalExerciseDao().insert(entity)
        if (inserted == -1L) database.canonicalExerciseDao().update(entity)
    }
}
