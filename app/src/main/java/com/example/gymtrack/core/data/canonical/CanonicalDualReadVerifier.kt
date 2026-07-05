package com.example.gymtrack.core.data.canonical

import com.example.gymtrack.core.data.CanonicalCategoryEntity
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.transition.CanonicalExerciseCatalog
import com.example.gymtrack.core.data.transition.CanonicalKeys
import com.example.gymtrack.core.data.transition.LegacyWorkoutProjector
import com.example.gymtrack.domain.model.WorkoutDetails
import com.example.gymtrack.domain.model.WorkoutRecord
import com.example.gymtrack.domain.repository.CanonicalWorkoutRepository
import com.example.gymtrack.domain.repository.CanonicalWorkoutVerifier
import com.example.gymtrack.domain.repository.VerificationMismatch
import com.example.gymtrack.domain.repository.VerificationStatus
import com.example.gymtrack.domain.repository.WorkoutVerificationReport
import kotlinx.coroutines.flow.first

internal class CanonicalDualReadVerifier(
    private val database: NoteDatabase,
    private val repository: CanonicalWorkoutRepository,
    private val projector: LegacyWorkoutProjector = LegacyWorkoutProjector(),
) : CanonicalWorkoutVerifier {
    override suspend fun verify(workoutId: String): WorkoutVerificationReport {
        val actual = try {
            repository.getById(workoutId)
        } catch (error: RuntimeException) {
            return WorkoutVerificationReport(
                workoutId = workoutId,
                legacyTimestamp = null,
                status = VerificationStatus.INVALID_CANONICAL,
                mismatches = listOf(
                    VerificationMismatch(
                        path = "canonical.mapping",
                        expected = "valid aggregate",
                        actual = error.message,
                    ),
                ),
            )
        } ?: return WorkoutVerificationReport(
            workoutId = workoutId,
            legacyTimestamp = null,
            status = VerificationStatus.INVALID_CANONICAL,
            mismatches = listOf(
                VerificationMismatch("canonical.workout", "present", "missing"),
            ),
        )

        val legacyTimestamp = actual.record.workout.legacyCompatibility?.legacyTimestamp
            ?: return WorkoutVerificationReport(
                workoutId = workoutId,
                legacyTimestamp = null,
                status = VerificationStatus.NOT_APPLICABLE,
            )

        val note = database.noteDao().getById(legacyTimestamp)
            ?: return WorkoutVerificationReport(
                workoutId = workoutId,
                legacyTimestamp = legacyTimestamp,
                status = VerificationStatus.MISSING_LEGACY,
                mismatches = listOf(
                    VerificationMismatch("legacy.note", "present", "missing"),
                ),
            )

        val catalog = CanonicalExerciseCatalog(
            database.exerciseDao().getAllExercises().first(),
        )
        val category = note.categoryName?.trim()?.takeIf(String::isNotEmpty)?.let { name ->
            CanonicalCategoryEntity(
                id = CanonicalKeys.category(name, note.categoryColor ?: 0L),
                name = name,
                colorArgb = note.categoryColor ?: 0L,
                position = actual.category?.position ?: 0,
                isBuiltIn = actual.category?.isBuiltIn ?: false,
                isArchived = actual.category?.isArchived ?: false,
            )
        }
        val expectedProjection = projector.project(note, category, catalog)
        val expected = expectedProjection.toDetails(catalog, category)
        val mismatches = compare(expected, actual)

        return WorkoutVerificationReport(
            workoutId = workoutId,
            legacyTimestamp = legacyTimestamp,
            status = if (mismatches.isEmpty()) {
                VerificationStatus.MATCH
            } else {
                VerificationStatus.MISMATCH
            },
            mismatches = mismatches,
        )
    }

    private fun compare(
        expected: WorkoutDetails,
        actual: WorkoutDetails,
    ): List<VerificationMismatch> = buildList {
        compareValue("workout", expected.record.workout, actual.record.workout)
        compareOrderedList("exercises", expected.record.exercises, actual.record.exercises)
        compareOrderedList("sets", expected.record.sets, actual.record.sets)

        val expectedDefinitions = expected.exerciseDefinitions.toSortedMap()
        val actualDefinitions = actual.exerciseDefinitions.toSortedMap()
        compareValue("exerciseDefinitions.keys", expectedDefinitions.keys, actualDefinitions.keys)
        expectedDefinitions.keys.intersect(actualDefinitions.keys).forEach { id ->
            compareValue(
                path = "exerciseDefinitions[$id]",
                expected = expectedDefinitions[id],
                actual = actualDefinitions[id],
            )
        }
        compareValue("category", expected.category, actual.category)
    }

    private fun <T> MutableList<VerificationMismatch>.compareOrderedList(
        path: String,
        expected: List<T>,
        actual: List<T>,
    ) {
        compareValue("$path.count", expected.size, actual.size)
        val commonSize = minOf(expected.size, actual.size)
        repeat(commonSize) { index ->
            compareValue("$path[$index]", expected[index], actual[index])
        }
    }

    private fun MutableList<VerificationMismatch>.compareValue(
        path: String,
        expected: Any?,
        actual: Any?,
    ) {
        if (expected != actual) {
            add(
                VerificationMismatch(
                    path = path,
                    expected = expected?.toString(),
                    actual = actual?.toString(),
                ),
            )
        }
    }

    private fun com.example.gymtrack.core.data.transition.CanonicalWorkoutProjection.toDetails(
        catalog: CanonicalExerciseCatalog,
        category: CanonicalCategoryEntity?,
    ): WorkoutDetails {
        val aliases = catalog.aliasEntities()
        val referencedIds = workoutExercises.map { it.exerciseId }.toSet()
        val definitions = catalog.exerciseEntities()
            .filter { it.id in referencedIds }
            .associate { entity -> entity.id to entity.toDomain(aliases) }

        return WorkoutDetails(
            record = WorkoutRecord(
                workout = workout.toDomain(),
                exercises = workoutExercises.map { it.toDomain() },
                sets = workoutSets.map { it.toDomain() },
            ),
            exerciseDefinitions = definitions,
            category = category?.toDomain(),
        )
    }
}
