package com.example.gymtrack.core.backup

import com.example.gymtrack.core.data.ExerciseMuscleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupArchiveTest {
    @Test
    fun missingRelationshipIsRejected() {
        val payload = BackupFixtures.payload()
        val invalid = payload.copy(
            canonicalWorkoutSets = listOf(
                payload.canonicalWorkoutSets.single().copy(workoutExerciseId = "missing"),
            ),
        )
        assertThrows(InvalidBackupException::class.java) {
            BackupValidator.check(invalid)
        }
    }

    @Test
    fun duplicateStableIdsAreRejected() {
        val payload = BackupFixtures.payload()
        val duplicate = payload.canonicalWorkouts.single().copy()
        val invalid = payload.copy(canonicalWorkouts = listOf(duplicate, duplicate))

        assertThrows(InvalidBackupException::class.java) {
            BackupValidator.check(invalid)
        }
    }

    @Test
    fun muscleEditsAreOptionalAndCounted() {
        val payload = BackupFixtures.payload()
        assertEquals(0, BackupCounts.from(payload).exerciseMuscles)
        BackupValidator.check(payload)

        val withEdits = payload.copy(exerciseMuscles = listOf(ExerciseMuscleEntity(1L, "chestLower", "PRIMARY")))
        BackupValidator.check(withEdits)
        val counts = BackupCounts.from(withEdits)
        assertEquals(1, counts.exerciseMuscles)
        assertEquals(BackupCounts.from(payload).totalRecords + 1, counts.totalRecords)
    }

    @Test
    fun muscleEditOfAMissingExerciseIsRejected() {
        val invalid = BackupFixtures.payload().copy(exerciseMuscles = listOf(ExerciseMuscleEntity(99L, "chestLower", "PRIMARY")))
        assertThrows(InvalidBackupException::class.java) { BackupValidator.check(invalid) }
    }

    @Test
    fun duplicateMuscleEditsAreRejected() {
        val row = ExerciseMuscleEntity(1L, "chestLower", "PRIMARY")
        val invalid = BackupFixtures.payload().copy(exerciseMuscles = listOf(row, row.copy(role = "SECONDARY")))
        assertThrows(InvalidBackupException::class.java) { BackupValidator.check(invalid) }
    }
}
