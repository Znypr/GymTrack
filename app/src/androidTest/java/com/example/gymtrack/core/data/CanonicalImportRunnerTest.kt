package com.example.gymtrack.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtrack.core.data.transition.CanonicalImportRunner
import com.example.gymtrack.core.data.transition.CanonicalKeys
import com.example.gymtrack.core.util.combineTextAndTimes
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalImportRunnerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() = context.deleteMigrationTestDatabase()

    @Test
    fun importsLegacyWorkoutOnceAndSkipsExactSecondRun() {
        context.createLegacyDatabase(8, VERSION_8_SCHEMA, emptyList())

        context.openMigratedDatabase().use { database ->
            val encoded = combineTextAndTimes(
                text = "Bench\n      8x 80kg",
                times = listOf("0'00''", "0'30''"),
                flags = listOf(ExerciseFlag.UNILATERAL, ExerciseFlag.UNILATERAL),
            )

            runBlocking {
                val legacyExerciseId = database.exerciseDao().insert(
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
                database.setDao().insertSets(
                    listOf(
                        SetEntity(
                            workoutId = 1_000_000L,
                            exerciseId = legacyExerciseId,
                            weight = 80f,
                            reps = 8,
                            isUnilateral = true,
                        ),
                    ),
                )

                val first = CanonicalImportRunner(database).run()
                assertEquals(1, first.totalNotes)
                assertEquals(1, first.migrated)
                assertEquals(0, first.skipped)
                assertEquals(0, first.needsReview)

                val workout = database.canonicalWorkoutDao().getByLegacyTimestamp(1_000_000L)
                assertNotNull(workout)
                assertEquals(CanonicalKeys.workout(1_000_000L), workout?.id)
                assertEquals(encoded, workout?.rawDraftText)
                assertEquals("MIGRATED", workout?.legacyMigrationStatus)

                val occurrences = database.canonicalWorkoutExerciseDao()
                    .getForWorkout(workout!!.id)
                assertEquals(1, occurrences.size)
                assertEquals("UNILATERAL", occurrences.single().mode)
                assertEquals(CanonicalKeys.legacyExercise(legacyExerciseId), occurrences.single().exerciseId)

                val sets = database.canonicalWorkoutSetDao()
                    .getForWorkoutExercises(occurrences.map { it.id })
                assertEquals(1, sets.size)
                assertEquals(8, sets.single().repetitions)
                assertEquals(80.0, sets.single().weight ?: 0.0, 0.0)
                assertEquals("KILOGRAM", sets.single().weightUnit)

                val second = CanonicalImportRunner(database).run()
                assertEquals(0, second.migrated)
                assertEquals(1, second.skipped)
                assertEquals(1, database.canonicalWorkoutDao().getCount())
                assertEquals(1, database.canonicalWorkoutExerciseDao().getCount())
                assertEquals(1, database.canonicalWorkoutSetDao().getCount())
                assertTrue(database.noteDao().getById(1_000_000L)?.text == encoded)
                assertEquals(1, database.setDao().getCount())
            }
        }
    }
}
