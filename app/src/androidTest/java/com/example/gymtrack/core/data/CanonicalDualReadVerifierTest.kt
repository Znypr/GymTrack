package com.example.gymtrack.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtrack.core.data.canonical.CanonicalDualReadVerifier
import com.example.gymtrack.core.data.canonical.RoomCanonicalWorkoutRepository
import com.example.gymtrack.core.data.transition.CanonicalImportRunner
import com.example.gymtrack.core.util.combineTextAndTimes
import com.example.gymtrack.domain.repository.VerificationStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalDualReadVerifierTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() = context.deleteMigrationTestDatabase()

    @Test
    fun reportsMatchThenDetectsCanonicalValueChange() {
        context.createLegacyDatabase(8, VERSION_8_SCHEMA, emptyList())

        context.openMigratedDatabase().use { database ->
            val encoded = combineTextAndTimes(
                text = "Bench\n      8x 80kg",
                times = listOf("0'00''", "0'30''"),
                flags = listOf(ExerciseFlag.BILATERAL, ExerciseFlag.BILATERAL),
            )

            runBlocking {
                database.exerciseDao().insert(
                    ExerciseEntity(
                        name = "Bench press",
                        parentId = null,
                        muscleGroup = "Chest",
                        aliases = "Bench",
                    ),
                )
                database.noteDao().insert(
                    NoteEntity(
                        timestamp = 1_000_000L,
                        title = "Push",
                        text = encoded,
                        categoryName = "Push",
                        categoryColor = 55L,
                        learnings = "Controlled reps",
                    ),
                )
                CanonicalImportRunner(database).run()

                val repository = RoomCanonicalWorkoutRepository(database)
                val workout = repository.getByLegacyTimestamp(1_000_000L)!!
                val verifier = CanonicalDualReadVerifier(database, repository)

                val initial = verifier.verify(workout.record.workout.id)
                assertEquals(VerificationStatus.MATCH, initial.status)
                assertTrue(initial.mismatches.isEmpty())

                database.openHelper.writableDatabase.execSQL(
                    "UPDATE workout_sets SET weight = 81.0 WHERE id = ?",
                    arrayOf(workout.record.sets.single().id),
                )

                val changed = verifier.verify(workout.record.workout.id)
                assertEquals(VerificationStatus.MISMATCH, changed.status)
                assertTrue(changed.mismatches.any { it.path.startsWith("sets[") })
            }
        }
    }
}
