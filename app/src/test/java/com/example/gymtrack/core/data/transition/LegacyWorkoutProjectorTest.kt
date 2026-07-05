package com.example.gymtrack.core.data.transition

import com.example.gymtrack.core.data.CanonicalCategoryEntity
import com.example.gymtrack.core.data.ExerciseEntity
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteEntity
import com.example.gymtrack.core.util.combineTextAndTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyWorkoutProjectorTest {
    @Test
    fun deterministicKeysAreStableAndNamespaced() {
        assertEquals(CanonicalKeys.workout(123L), CanonicalKeys.workout(123L))
        assertNotEquals(CanonicalKeys.workout(123L), CanonicalKeys.workout(124L))
        assertNotEquals(CanonicalKeys.workout(123L), CanonicalKeys.namedExercise("123"))
        assertEquals(
            CanonicalKeys.namedExercise("  Bench   Press "),
            CanonicalKeys.namedExercise("bench press"),
        )
    }

    @Test
    fun exerciseIdentityDoesNotDependOnLegacyAutoIncrementId() {
        val first = CanonicalExerciseCatalog(
            listOf(
                ExerciseEntity(
                    exerciseId = 7L,
                    name = "Bench press",
                    parentId = null,
                    muscleGroup = "Chest",
                    aliases = "Bench",
                ),
            ),
        )
        val rebuilt = CanonicalExerciseCatalog(
            listOf(
                ExerciseEntity(
                    exerciseId = 91L,
                    name = "Bench press",
                    parentId = null,
                    muscleGroup = "Chest",
                    aliases = "Bench",
                ),
            ),
        )

        assertEquals(first.resolve("Bench").id, rebuilt.resolve("Bench").id)
        assertEquals(CanonicalKeys.namedExercise("Bench press"), first.resolve("Bench").id)
    }

    @Test
    fun projectsOrderedExerciseBlocksAndOnlyExplicitUnits() {
        val timestamp = 1_000_000L
        val plainText = listOf(
            "Bench",
            "      8x 80kg",
            "",
            "Latpull",
            "      10x 70",
        ).joinToString("\n")
        val encoded = combineTextAndTimes(
            text = plainText,
            times = listOf("0'00''", "0'30''", "", "5'00''", "5'30''"),
            flags = listOf(
                ExerciseFlag.UNILATERAL,
                ExerciseFlag.UNILATERAL,
                ExerciseFlag.BILATERAL,
                ExerciseFlag.SUPERSET,
                ExerciseFlag.SUPERSET,
            ),
        )
        val note = NoteEntity(
            timestamp = timestamp,
            title = "Pull and push",
            text = encoded,
            categoryName = "Mixed",
            categoryColor = 123L,
            learnings = "Stable session",
        )
        val category = CanonicalCategoryEntity(
            id = CanonicalKeys.category("Mixed", 123L),
            name = "Mixed",
            colorArgb = 123L,
            position = 0,
            isBuiltIn = false,
            isArchived = false,
        )
        val catalog = CanonicalExerciseCatalog(
            listOf(
                ExerciseEntity(
                    exerciseId = 7L,
                    name = "Bench press",
                    parentId = null,
                    muscleGroup = "Chest",
                    aliases = "Bench",
                ),
            ),
        )

        val projection = LegacyWorkoutProjector().project(note, category, catalog)

        assertEquals("MIGRATED", projection.workout.legacyMigrationStatus)
        assertEquals("PARTIAL", projection.workout.status)
        assertEquals(timestamp + 330_000L, projection.workout.endedAt)
        assertEquals(2, projection.workoutExercises.size)
        assertEquals(listOf(0, 1), projection.workoutExercises.map { it.position })
        assertEquals("UNILATERAL", projection.workoutExercises[0].mode)
        assertEquals("SUPERSET", projection.workoutExercises[1].mode)
        assertEquals(CanonicalKeys.namedExercise("Bench press"), projection.workoutExercises[0].exerciseId)
        assertEquals(2, projection.workoutSets.size)
        assertEquals("KILOGRAM", projection.workoutSets[0].weightUnit)
        assertNull(projection.workoutSets[1].weightUnit)
        assertEquals(30, projection.workoutSets[0].performedAtOffsetSeconds)
        assertEquals(330, projection.workoutSets[1].performedAtOffsetSeconds)
        assertEquals(80.0, projection.workoutSets[0].weight ?: 0.0, 0.0)
        assertEquals(8, projection.workoutSets[0].repetitions)
    }

    @Test
    fun marksOrphanSetRowsForReviewWithoutDroppingRawText() {
        val plainText = "      8x 80kg"
        val encoded = combineTextAndTimes(
            text = plainText,
            times = listOf("0'30''"),
            flags = listOf(ExerciseFlag.BILATERAL),
        )
        val note = NoteEntity(
            timestamp = 2_000_000L,
            title = "Broken",
            text = encoded,
            categoryName = null,
            categoryColor = null,
            learnings = null,
        )

        val projection = LegacyWorkoutProjector().project(
            note = note,
            category = null,
            exercises = CanonicalExerciseCatalog(emptyList()),
        )

        assertEquals("NEEDS_REVIEW", projection.workout.legacyMigrationStatus)
        assertTrue(projection.workout.legacyMigrationMessage.orEmpty().contains("set row without exercise header"))
        assertEquals(encoded, projection.workout.rawDraftText)
        assertTrue(projection.workoutExercises.isEmpty())
        assertTrue(projection.workoutSets.isEmpty())
    }
}
