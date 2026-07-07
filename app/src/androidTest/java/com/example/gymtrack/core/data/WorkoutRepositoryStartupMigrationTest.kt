package com.example.gymtrack.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryStartupMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() = context.deleteMigrationTestDatabase()

    @Test
    fun compatibilityMigrationDoesNotReparseWhenDerivedStatsAlreadyExist() = runBlocking {
        context.createLegacyDatabase(8, VERSION_8_SCHEMA, emptyList())

        context.openMigratedDatabase().use { database ->
            val repository = WorkoutRepository(database)
            val note = NoteEntity(
                timestamp = 1_000L,
                title = "Pull",
                text = "Row\n 80kg x 8",
                categoryName = "Pull",
                categoryColor = 1L,
                learnings = "",
                rowMetadata = "",
            )
            val exerciseId = database.exerciseDao().insert(
                ExerciseEntity(
                    name = "Existing row",
                    muscleGroup = null,
                    aliases = "",
                ),
            )
            database.noteDao().insert(note)
            database.setDao().insertSets(
                listOf(
                    SetEntity(
                        workoutId = note.timestamp,
                        exerciseId = exerciseId,
                        weight = 80f,
                        reps = 8,
                        isUnilateral = false,
                    ),
                ),
            )

            repository.checkAndMigrate()

            assertEquals(1, database.setDao().getCount())
        }
    }
}
