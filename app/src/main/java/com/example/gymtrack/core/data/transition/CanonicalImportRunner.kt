package com.example.gymtrack.core.data.transition

import androidx.room.withTransaction
import com.example.gymtrack.core.data.CanonicalCategoryEntity
import com.example.gymtrack.core.data.CanonicalWorkoutEntity
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.NoteEntity
import kotlinx.coroutines.flow.first

internal data class CanonicalImportReport(
    val totalNotes: Int,
    val migrated: Int,
    val skipped: Int,
    val needsReview: Int,
)

internal class CanonicalImportRunner(
    private val database: NoteDatabase,
    private val projector: LegacyWorkoutProjector = LegacyWorkoutProjector(),
) {
    suspend fun run(): CanonicalImportReport {
        val notes = database.noteDao().getAll().first()
        val legacyExercises = database.exerciseDao().getAllExercises().first()
        val exerciseCatalog = CanonicalExerciseCatalog(legacyExercises)
        val categories = buildCategories(notes)

        val projections = notes.map { note ->
            val category = categoryFor(note, categories)
            runCatching { projector.project(note, category, exerciseCatalog) }
                .getOrElse { error -> failedProjection(note, category, error) }
        }

        var migrated = 0
        var skipped = 0
        var needsReview = 0

        database.withTransaction {
            categories.values.forEach { category -> upsertCategory(category) }
            upsertExercises(exerciseCatalog)

            projections.forEach { projection ->
                if (projection.workout.legacyMigrationStatus == "NEEDS_REVIEW") {
                    needsReview++
                }

                if (isCurrent(projection)) {
                    skipped++
                    return@forEach
                }

                upsertWorkout(projection.workout)
                database.canonicalWorkoutExerciseDao().deleteForWorkout(projection.workout.id)
                if (projection.workoutExercises.isNotEmpty()) {
                    database.canonicalWorkoutExerciseDao().insertAll(projection.workoutExercises)
                }
                if (projection.workoutSets.isNotEmpty()) {
                    database.canonicalWorkoutSetDao().insertAll(projection.workoutSets)
                }
                migrated++
            }
        }

        return CanonicalImportReport(
            totalNotes = notes.size,
            migrated = migrated,
            skipped = skipped,
            needsReview = needsReview,
        )
    }

    private suspend fun upsertCategory(category: CanonicalCategoryEntity) {
        database.canonicalCategoryDao().insert(category)
    }

    private suspend fun upsertExercises(catalog: CanonicalExerciseCatalog) {
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

    private suspend fun upsertWorkout(workout: CanonicalWorkoutEntity) {
        val inserted = database.canonicalWorkoutDao().insert(workout)
        if (inserted == -1L) database.canonicalWorkoutDao().update(workout)
    }

    private suspend fun isCurrent(projection: CanonicalWorkoutProjection): Boolean {
        val existingWorkout = database.canonicalWorkoutDao()
            .getByLegacyTimestamp(projection.workout.legacyTimestamp ?: return false)
            ?: return false
        if (existingWorkout != projection.workout) return false

        val existingExercises = database.canonicalWorkoutExerciseDao()
            .getForWorkout(projection.workout.id)
        if (existingExercises != projection.workoutExercises) return false

        val exerciseKeys = existingExercises.map { it.id }
        val existingSets = if (exerciseKeys.isEmpty()) {
            emptyList()
        } else {
            database.canonicalWorkoutSetDao().getForWorkoutExercises(exerciseKeys)
        }
        val expectedSets = projection.workoutSets.sortedWith(
            compareBy({ it.workoutExerciseId }, { it.position }),
        )
        return existingSets == expectedSets
    }

    private fun buildCategories(notes: List<NoteEntity>): Map<CategorySignature, CanonicalCategoryEntity> {
        val displayNameBySignature = linkedMapOf<CategorySignature, String>()
        notes.forEach { note ->
            val displayName = note.categoryName?.trim().orEmpty()
            if (displayName.isNotEmpty()) {
                val signature = CategorySignature(
                    normalizedName = CanonicalKeys.normalize(displayName),
                    colorArgb = note.categoryColor ?: 0L,
                )
                displayNameBySignature.putIfAbsent(signature, displayName)
            }
        }

        return displayNameBySignature.entries
            .sortedWith(compareBy({ it.key.normalizedName }, { it.key.colorArgb }))
            .mapIndexed { position, (signature, displayName) ->
                signature to CanonicalCategoryEntity(
                    id = CanonicalKeys.category(displayName, signature.colorArgb),
                    name = displayName,
                    colorArgb = signature.colorArgb,
                    position = position,
                    isBuiltIn = false,
                    isArchived = false,
                )
            }
            .toMap()
    }

    private fun categoryFor(
        note: NoteEntity,
        categories: Map<CategorySignature, CanonicalCategoryEntity>,
    ): CanonicalCategoryEntity? {
        val displayName = note.categoryName?.trim().orEmpty()
        if (displayName.isEmpty()) return null
        return categories[
            CategorySignature(
                normalizedName = CanonicalKeys.normalize(displayName),
                colorArgb = note.categoryColor ?: 0L,
            )
        ]
    }

    private fun failedProjection(
        note: NoteEntity,
        category: CanonicalCategoryEntity?,
        error: Throwable,
    ): CanonicalWorkoutProjection {
        val workout = CanonicalWorkoutEntity(
            id = CanonicalKeys.workout(note.timestamp),
            legacyTimestamp = note.timestamp,
            startedAt = note.timestamp,
            endedAt = null,
            categoryId = category?.id,
            title = note.title,
            learnings = note.learnings.orEmpty(),
            status = "PARTIAL",
            rawDraftText = note.text,
            legacyMigrationStatus = "NEEDS_REVIEW",
            legacyMigrationMessage = "$CANONICAL_IMPORT_VERSION: projection failed (${error::class.simpleName})".take(240),
            createdAt = note.timestamp,
            updatedAt = note.timestamp,
        )
        return CanonicalWorkoutProjection(workout, emptyList(), emptyList())
    }

    private data class CategorySignature(
        val normalizedName: String,
        val colorArgb: Long,
    )
}
