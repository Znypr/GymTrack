package com.example.gymtrack.core.data.transition

import com.example.gymtrack.core.data.CanonicalCategoryEntity
import com.example.gymtrack.core.data.CanonicalWorkoutEntity
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.NoteEntity

internal data class CanonicalImportReport(
    val totalNotes: Int,
    /** Workouts whose exercise and set rows were (re)written. */
    val migrated: Int,
    /** Notes that needed no write at all. */
    val skipped: Int,
    val needsReview: Int,
    /** Workouts where only the workout row changed (for example a source stamp on a pre-stamp row). */
    val restamped: Int = 0,
)

/**
 * Projects legacy notes into the canonical tables at startup.
 *
 * Idempotence rule: a note whose source hash matches the stamp on its canonical row is skipped
 * before projection (see [CanonicalImportSource]). Rows that do need work are merged with
 * [CanonicalImportSource.merge], so the import never lowers a row written by the completion path
 * from `COMPLETED` to `PARTIAL` and never moves `updatedAt` back.
 */
internal class CanonicalImportRunner(
    private val store: CanonicalImportStore,
    private val projector: LegacyWorkoutProjector = LegacyWorkoutProjector(),
) {
    constructor(
        database: NoteDatabase,
        projector: LegacyWorkoutProjector = LegacyWorkoutProjector(),
    ) : this(RoomCanonicalImportStore(database), projector)

    suspend fun run(): CanonicalImportReport {
        val notes = store.notes()
        val existingByTimestamp = store.legacyBackedWorkouts()
            .mapNotNull { workout -> workout.legacyTimestamp?.let { it to workout } }
            .toMap()

        val (unchanged, pending) = notes.partition { note ->
            CanonicalImportSource.isCurrent(existingByTimestamp[note.timestamp], note)
        }
        val unchangedNeedingReview = unchanged.count { note ->
            existingByTimestamp[note.timestamp]?.legacyMigrationStatus == NEEDS_REVIEW
        }
        if (pending.isEmpty()) {
            return CanonicalImportReport(
                totalNotes = notes.size,
                migrated = 0,
                skipped = notes.size,
                needsReview = unchangedNeedingReview,
            )
        }

        val exerciseCatalog = CanonicalExerciseCatalog(store.legacyExercises())
        val categories = buildCategories(notes)
        val projections = pending.map { note ->
            val category = categoryFor(note, categories)
            runCatching { projector.project(note, category, exerciseCatalog) }
                .getOrElse { error -> failedProjection(note, category, error) }
        }

        var migrated = 0
        var restamped = 0
        var skipped = unchanged.size
        var needsReview = unchangedNeedingReview

        store.inTransaction {
            categories.values.forEach { category -> store.upsertCategory(category) }
            store.upsertExercises(exerciseCatalog)

            projections.forEach { projection ->
                if (projection.workout.legacyMigrationStatus == NEEDS_REVIEW) needsReview++

                // Re-read inside the transaction: the completion path may have written this row
                // after the snapshot above (cold start racing a Finish).
                val existing = projection.workout.legacyTimestamp?.let { store.workoutByLegacyTimestamp(it) }
                val sourceHash = CanonicalImportSource.storedHash(projection.workout.legacyMigrationMessage)
                val workout = CanonicalImportSource.merge(existing, projection.workout)
                when {
                    existing != null &&
                        CanonicalImportSource.storedHash(existing.legacyMigrationMessage) == sourceHash -> skipped++
                    existing == null || !childrenMatch(projection) -> {
                        store.replaceWorkout(workout, projection.workoutExercises, projection.workoutSets)
                        migrated++
                    }
                    workout != existing -> {
                        store.updateWorkout(workout)
                        restamped++
                    }
                    else -> skipped++
                }
            }
        }

        return CanonicalImportReport(
            totalNotes = notes.size,
            migrated = migrated,
            skipped = skipped,
            needsReview = needsReview,
            restamped = restamped,
        )
    }

    private suspend fun childrenMatch(projection: CanonicalWorkoutProjection): Boolean {
        val existingExercises = store.workoutExercises(projection.workout.id)
        if (existingExercises != projection.workoutExercises) return false

        val existingSets = store.workoutSets(existingExercises.map { it.id })
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
            legacyMigrationStatus = NEEDS_REVIEW,
            legacyMigrationMessage = "${CanonicalImportSource.stamp(note)}: projection failed (${error::class.simpleName})".take(240),
            createdAt = note.timestamp,
            updatedAt = note.timestamp,
        )
        return CanonicalWorkoutProjection(workout, emptyList(), emptyList())
    }

    private data class CategorySignature(
        val normalizedName: String,
        val colorArgb: Long,
    )

    private companion object {
        const val NEEDS_REVIEW = "NEEDS_REVIEW"
    }
}
