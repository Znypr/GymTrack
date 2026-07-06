package com.example.gymtrack.core.backup

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
}
