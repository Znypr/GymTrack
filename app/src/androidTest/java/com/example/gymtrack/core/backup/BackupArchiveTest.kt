package com.example.gymtrack.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupArchiveTest {
    @Test
    fun archiveRoundTripPreservesPayload() {
        val payload = BackupFixtures.payload()
        val output = ByteArrayOutputStream()
        val manifest = BackupArchive.write(output, payload, "1.8", 9, 1234L)
        val restored = BackupArchive.read(ByteArrayInputStream(output.toByteArray()))

        assertEquals(payload, restored.payload)
        assertEquals(manifest, restored.manifest)
        assertEquals(9, restored.manifest.counts.totalRecords)
    }

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
}
