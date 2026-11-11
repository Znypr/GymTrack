package com.example.gymtrack.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v13 → v14 (#306, #307). Old databases arrive through the verified chain from v8, so this also
 * covers 13 → 14 on real old rows.
 */
@RunWith(AndroidJUnit4::class)
class Migration13To14Test {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() = context.deleteMigrationTestDatabase()

    private fun createOldDatabaseWithARamp() {
        context.createLegacyDatabase(
            version = 8,
            schema = VERSION_8_SCHEMA,
            inserts = listOf(
                "INSERT INTO notes(timestamp, title, text, categoryName, categoryColor, learnings) VALUES " +
                    "(1000, 'Push', 'Bench Press\n    10x 20\n    5x 60\n    5x 100\n    5x 100', 'Push', 1, '')",
                "INSERT INTO exercises(exerciseId, name, parentId, muscleGroup, aliases) VALUES (1, 'Bench Press', NULL, 'Chest', '')",
                "INSERT INTO sets(setId, workoutId, exerciseId, weight, reps, isUnilateral) VALUES (1, 1000, 1, 20, 10, 0)",
                "INSERT INTO sets(setId, workoutId, exerciseId, weight, reps, isUnilateral) VALUES (2, 1000, 1, 60, 5, 0)",
                "INSERT INTO sets(setId, workoutId, exerciseId, weight, reps, isUnilateral) VALUES (3, 1000, 1, 100, 5, 0)",
                "INSERT INTO sets(setId, workoutId, exerciseId, weight, reps, isUnilateral) VALUES (4, 1000, 1, 100, 5, 0)",
            ),
        )
    }

    @Test
    fun oldRowsBecomeWorkingInferredAndDerivedTablesStartEmpty() {
        createOldDatabaseWithARamp()
        context.openMigratedDatabase().use { database ->
            runBlocking {
                val rows = database.setDao().getForWorkout(1000)
                assertEquals(4, rows.size)
                assertTrue(rows.all { it.setType == "WORKING" && it.setTypeSource == "INFERRED" && it.restSeconds == null })
                assertEquals(listOf(20f, 60f, 100f, 100f), rows.map { it.weight })
                assertEquals(listOf("KG", "KG", "KG", "KG"), rows.map { it.weightUnit })
                assertTrue(database.derivedIndexDao().recentForWorkout(1000).isEmpty())
                assertNull(database.derivedIndexDao().state(DerivedIndexWriter.INDEX_NAME))
            }
            listOf("exercise_recent", "muscle_week", "derived_index_state").forEach { table ->
                assertTrue("Missing table $table", database.schemaObjectExists("table", table))
            }
            listOf(
                "index_exercise_recent_exercise_id_workout_id",
                "index_muscle_week_week_key_muscle",
            ).forEach { index ->
                assertTrue("Missing index $index", database.schemaObjectExists("index", index))
            }
        }
    }

    @Test
    fun lazyRebuildInfersWarmUpsAndFillsDerivedRowsOnce() {
        createOldDatabaseWithARamp()
        context.openMigratedDatabase().use { database ->
            runBlocking {
                val repository = WorkoutRepository(database)
                repository.ensureDerivedIndexes(chunkSize = 1)

                val rows = database.setDao().getForWorkout(1000)
                assertEquals(listOf("WARMUP", "WARMUP", "WORKING", "WORKING"), rows.map { it.setType })
                assertEquals(listOf("KG", "KG", "KG", "KG"), rows.map { it.weightUnit })

                val recent = database.derivedIndexDao().recentForWorkout(1000).single()
                assertEquals(2, recent.workingSets)
                assertEquals(2, recent.warmupSets)
                assertEquals("5x 100 · 5x 100", recent.setsSummary)

                val state = database.derivedIndexDao().state(DerivedIndexWriter.INDEX_NAME)!!
                assertEquals(DerivedIndexWriter.VERSION, state.version)
                assertTrue(state.complete)

                // Progress queries count working sets only.
                val counts = database.openHelper.writableDatabase
                    .query("SELECT COUNT(*) FROM sets WHERE setType = 'WORKING'")
                    .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
                assertEquals(2, counts)
            }
        }
    }

    @Test
    fun overrideRewritesDerivedRowsAndDeleteRemovesThem() {
        createOldDatabaseWithARamp()
        context.openMigratedDatabase().use { database ->
            runBlocking {
                val repository = WorkoutRepository(database)
                repository.ensureDerivedIndexes()

                // The user says the 60 × 5 was a working set.
                assertTrue(repository.setSetTypeOverride(1000, setOrdinal = 1, type = SetType.WORKING))
                val rows = database.setDao().getForWorkout(1000)
                assertEquals("WORKING", rows[1].setType)
                assertEquals("USER", rows[1].setTypeSource)
                assertEquals(3, database.derivedIndexDao().recentForWorkout(1000).single().workingSets)
                assertEquals(false, repository.setSetTypeOverride(1000, setOrdinal = 9, type = SetType.WARMUP))

                // A rebuild (e.g. after a version bump) keeps the user's choice.
                database.derivedIndexDao().clearStates()
                repository.ensureDerivedIndexes()
                assertEquals("USER", database.setDao().getForWorkout(1000)[1].setTypeSource)

                val sessions = repository.recentSessions(listOf(1L))
                assertEquals(1, sessions.getValue(1L).size)
                assertEquals(3f, repository.muscleWeek(DerivedIndexWriter.weekKey(1000)).single().effectiveSets)

                repository.deleteWorkout(1000)
                assertTrue(database.derivedIndexDao().recentForWorkout(1000).isEmpty())
                assertTrue(repository.muscleWeek(DerivedIndexWriter.weekKey(1000)).isEmpty())
            }
        }
    }
}
