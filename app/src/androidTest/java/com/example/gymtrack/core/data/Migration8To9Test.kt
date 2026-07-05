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
class Migration8To9Test {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() = context.deleteMigrationTestDatabase()

    @Test
    fun preservesLegacyRowsAndCreatesEmptyCanonicalSchema() {
        context.createLegacyDatabase(
            version = 8,
            schema = VERSION_8_SCHEMA,
            inserts = listOf(
                "INSERT INTO notes(timestamp, title, text, categoryName, categoryColor, learnings) VALUES (1000, 'Pull', 'Row', 'Pull', 123, 'Stable')",
                "INSERT INTO exercises(exerciseId, name, parentId, muscleGroup, aliases) VALUES (1, 'Row', NULL, 'Back', 'Cable Row')",
                "INSERT INTO sets(setId, workoutId, exerciseId, weight, reps, isUnilateral, modifier, brand, relativeTime, absoluteTime) VALUES (1, 1000, 1, 70, 10, 0, 'Cable', 'Gym80', '05:00', '15:05')",
            ),
        )

        context.openMigratedDatabase().use { database ->
            database.openHelper.writableDatabase.query(
                "SELECT title, text, categoryName, categoryColor, learnings FROM notes WHERE timestamp = 1000",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Pull", cursor.getString(0))
                assertEquals("Row", cursor.getString(1))
                assertEquals("Pull", cursor.getString(2))
                assertEquals(123L, cursor.getLong(3))
                assertEquals("Stable", cursor.getString(4))
            }

            database.openHelper.writableDatabase.query(
                "SELECT workoutId, exerciseId, weight, reps, modifier, brand FROM sets WHERE setId = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1000L, cursor.getLong(0))
                assertEquals(1L, cursor.getLong(1))
                assertEquals(70.0, cursor.getDouble(2), 0.0)
                assertEquals(10, cursor.getInt(3))
                assertEquals("Cable", cursor.getString(4))
                assertEquals("Gym80", cursor.getString(5))
            }

            runBlocking {
                assertEquals(0, database.canonicalCategoryDao().getCount())
                assertEquals(0, database.canonicalExerciseDao().getExerciseCount())
                assertEquals(0, database.canonicalExerciseDao().getAliasCount())
                assertEquals(0, database.canonicalWorkoutDao().getCount())
                assertEquals(0, database.canonicalWorkoutExerciseDao().getCount())
                assertEquals(0, database.canonicalWorkoutSetDao().getCount())
            }

            listOf(
                "categories",
                "canonical_exercises",
                "exercise_aliases",
                "workouts",
                "workout_exercises",
                "workout_sets",
            ).forEach { table ->
                assertTrue("Missing table $table", database.schemaObjectExists("table", table))
            }

            listOf(
                "index_workouts_legacy_timestamp",
                "index_workout_exercises_workout_id_position",
                "index_workout_sets_workout_exercise_id_position",
            ).forEach { index ->
                assertTrue("Missing index $index", database.schemaObjectExists("index", index))
            }
        }
    }

    @Test
    fun deletingCanonicalWorkoutCascadesToOccurrencesAndSets() {
        context.createLegacyDatabase(8, VERSION_8_SCHEMA, emptyList())

        context.openMigratedDatabase().use { database ->
            runBlocking {
                database.canonicalCategoryDao().insert(
                    CanonicalCategoryEntity(
                        id = "category-pull",
                        name = "Pull",
                        colorArgb = 123L,
                        position = 0,
                        isBuiltIn = true,
                        isArchived = false,
                    ),
                )
                database.canonicalExerciseDao().insert(
                    CanonicalExerciseEntity(
                        id = "exercise-row",
                        canonicalName = "Row",
                        normalizedName = "row",
                        parentExerciseId = null,
                        muscleGroup = "Back",
                        createdAt = 1000L,
                        updatedAt = 1000L,
                    ),
                )
                database.canonicalWorkoutDao().insert(
                    CanonicalWorkoutEntity(
                        id = "workout-1",
                        legacyTimestamp = null,
                        startedAt = 1000L,
                        endedAt = 2000L,
                        categoryId = "category-pull",
                        title = "Pull",
                        learnings = "",
                        status = "COMPLETED",
                        rawDraftText = null,
                        legacyMigrationStatus = null,
                        legacyMigrationMessage = null,
                        createdAt = 1000L,
                        updatedAt = 2000L,
                    ),
                )
                database.canonicalWorkoutExerciseDao().insertAll(
                    listOf(
                        CanonicalWorkoutExerciseEntity(
                            id = "workout-exercise-1",
                            workoutId = "workout-1",
                            exerciseId = "exercise-row",
                            position = 0,
                            mode = "BILATERAL",
                            modifier = null,
                            equipmentBrand = null,
                            startedAtOffsetSeconds = null,
                            startedAt = null,
                            legacyRelativeTimeText = null,
                            legacyAbsoluteTimeText = null,
                        ),
                    ),
                )
                database.canonicalWorkoutSetDao().insertAll(
                    listOf(
                        CanonicalWorkoutSetEntity(
                            id = "set-1",
                            workoutExerciseId = "workout-exercise-1",
                            position = 0,
                            repetitions = 10,
                            weight = 70.0,
                            weightUnit = "KILOGRAM",
                            durationSeconds = null,
                            distanceMeters = null,
                            performedAtOffsetSeconds = null,
                            rpe = null,
                            rir = null,
                        ),
                    ),
                )
            }

            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM workouts WHERE id = ?",
                arrayOf("workout-1"),
            )

            runBlocking {
                assertEquals(0, database.canonicalWorkoutDao().getCount())
                assertEquals(0, database.canonicalWorkoutExerciseDao().getCount())
                assertEquals(0, database.canonicalWorkoutSetDao().getCount())
                assertEquals(1, database.canonicalExerciseDao().getExerciseCount())
                assertEquals(1, database.canonicalCategoryDao().getCount())
            }
        }
    }
}
