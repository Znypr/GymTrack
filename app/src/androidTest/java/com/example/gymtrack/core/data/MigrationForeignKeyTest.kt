package com.example.gymtrack.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationForeignKeyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun cleanUp() = context.deleteMigrationTestDatabase()

    @Test fun deletingExerciseCascadesToSets() {
        context.createLegacyDatabase(4, listOf(VERSION_4_NOTES_SCHEMA), emptyList())
        context.openMigratedDatabase().use { database ->
            val exerciseId = runBlocking {
                database.exerciseDao().insert(ExerciseEntity(name = "Squat", muscleGroup = "Legs", aliases = ""))
            }
            runBlocking {
                database.setDao().insertSets(
                    listOf(SetEntity(workoutId = 1, exerciseId = exerciseId, weight = 140f, reps = 5, isUnilateral = false)),
                )
            }
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM exercises WHERE exerciseId = ?",
                arrayOf(exerciseId),
            )
            database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM sets").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }
}
