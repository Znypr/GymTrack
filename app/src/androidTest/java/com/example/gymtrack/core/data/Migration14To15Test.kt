package com.example.gymtrack.core.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtrack.core.muscle.Muscle
import com.example.gymtrack.core.muscle.MuscleLevel
import com.example.gymtrack.core.muscle.MuscleMappingOrigin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v14 → v15 (#309, #310): `exercise_muscles` added, `muscle_week` recreated with `exercise_id`,
 * the stale stub rows and version stamp dropped so the lazy rebuild writes real muscles.
 */
@RunWith(AndroidJUnit4::class)
class Migration14To15Test {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() = context.deleteMigrationTestDatabase()

    /** The exact v14 tables this migration touches, with a #307 stub row and a complete stamp. */
    @Test
    fun migrationReplacesStubRowsAndAddsTheOverrideTable() {
        context.deleteDatabase(MIGRATION_TEST_DATABASE)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(MIGRATION_TEST_DATABASE)
                .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE exercises (exerciseId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, parentId INTEGER, muscleGroup TEXT, aliases TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE muscle_week (workout_id INTEGER NOT NULL, week_key TEXT NOT NULL, muscle TEXT NOT NULL, effective_sets REAL NOT NULL, PRIMARY KEY(workout_id, muscle))")
                        db.execSQL("CREATE TABLE derived_index_state (name TEXT NOT NULL, version INTEGER NOT NULL, rebuilt_through INTEGER NOT NULL, complete INTEGER NOT NULL, PRIMARY KEY(name))")
                        db.execSQL("INSERT INTO exercises VALUES (1, 'Bench Press', NULL, 'Chest', '')")
                        db.execSQL("INSERT INTO muscle_week VALUES (1000, '1970-W01', 'chest', 4.0)")
                        db.execSQL("INSERT INTO derived_index_state VALUES ('workout_derived', 1, 1000, 1)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.use { db ->
            ExerciseMuscleDatabaseMigration.MIGRATION_14_15.migrate(db)

            db.query("SELECT COUNT(*) FROM muscle_week").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM derived_index_state").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            val columns = db.query("PRAGMA table_info(muscle_week)").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            assertTrue("exercise_id" in columns)

            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("INSERT INTO exercise_muscles VALUES (1, 'chestLower', 'PRIMARY', 'USER')")
            db.execSQL("DELETE FROM exercises WHERE exerciseId = 1")
            db.query("SELECT COUNT(*) FROM exercise_muscles").use { it.moveToFirst(); assertEquals("FK CASCADE", 0, it.getInt(0)) }
        }
        helper.close()
    }

    private fun createOldDatabase() {
        context.createLegacyDatabase(
            version = 8,
            schema = VERSION_8_SCHEMA,
            inserts = listOf(
                "INSERT INTO notes(timestamp, title, text, categoryName, categoryColor, learnings) VALUES " +
                    "(1000, 'Push', 'Bench Press\n    5x 100\n    5x 100\nDead Hang\n    30s', 'Push', 1, '')",
                "INSERT INTO exercises(exerciseId, name, parentId, muscleGroup, aliases) VALUES (1, 'Bench Press', NULL, 'Back', '')",
                "INSERT INTO exercises(exerciseId, name, parentId, muscleGroup, aliases) VALUES (2, 'Dead Hang', NULL, NULL, '')",
                "INSERT INTO sets(setId, workoutId, exerciseId, weight, reps, isUnilateral) VALUES (1, 1000, 1, 100, 5, 0)",
                "INSERT INTO sets(setId, workoutId, exerciseId, weight, reps, isUnilateral) VALUES (2, 1000, 1, 100, 5, 0)",
                "INSERT INTO sets(setId, workoutId, exerciseId, weight, reps, isUnilateral) VALUES (3, 1000, 2, 0, 0, 0)",
            ),
        )
    }

    @Test
    fun fullChainOpensAndTheRebuildWritesRealMuscles() {
        createOldDatabase()
        context.openMigratedDatabase().use { database ->
            assertTrue(database.schemaObjectExists("table", "exercise_muscles"))
            assertTrue(database.schemaObjectExists("index", "index_exercise_muscles_exercise_id"))
            runBlocking {
                assertTrue(database.exerciseMuscleDao().getAll().isEmpty())
                val repository = WorkoutRepository(database)
                val week = DerivedIndexWriter.weekKey(1000)
                val summary = repository.weeklyMuscleSummary(week)

                // The free-text muscleGroup 'Back' is never read: bench counts for the chest.
                assertEquals(2f, summary.muscles.getValue(Muscle.CHEST_LOWER).effectiveSets)
                assertEquals(0f, summary.muscles.getValue(Muscle.LATS).effectiveSets)
                // Dead Hang has no mapping: reported, and untrained regions are not "none".
                assertEquals(listOf("Dead Hang"), summary.unmapped.map { it.name })
                assertEquals(MuscleLevel.UNMAPPED, summary.levels.getValue(Muscle.LATS))
                assertEquals(MuscleLevel.LOW, repository.weeklyMuscleLevels(week).getValue(Muscle.CHEST_LOWER))
            }
        }
    }

    @Test
    fun editingMusclesReRendersHistoryAndResetGoesBackToTheSeed() {
        createOldDatabase()
        context.openMigratedDatabase().use { database ->
            runBlocking {
                val repository = WorkoutRepository(database)
                val week = DerivedIndexWriter.weekKey(1000)
                val setsBefore = database.setDao().getForWorkout(1000)
                repository.ensureDerivedIndexes()

                repository.setExerciseMuscles(listOf(2L), primary = setOf(Muscle.FOREARM_FLEXORS), secondary = setOf(Muscle.LATS))
                val edited = repository.exerciseMuscleMapping(listOf(2L))
                assertTrue(edited.isUserEdit)
                assertEquals(MuscleMappingOrigin.USER, edited.mapping!!.origin)
                val summary = repository.weeklyMuscleSummary(week)
                assertEquals(1f, summary.muscles.getValue(Muscle.FOREARM_FLEXORS).effectiveSets)
                assertEquals(0.5f, summary.muscles.getValue(Muscle.LATS).effectiveSets)
                assertTrue(summary.unmapped.isEmpty())
                // Logged sets are never rewritten by a mapping edit.
                assertEquals(setsBefore, database.setDao().getForWorkout(1000))

                // A rebuild (version bump, restore) keeps the user's mapping.
                database.derivedIndexDao().clearStates()
                assertEquals(1f, repository.weeklyMuscleSummary(week).muscles.getValue(Muscle.FOREARM_FLEXORS).effectiveSets)

                repository.resetExerciseMusclesToSeed(listOf(2L))
                val reset = repository.exerciseMuscleMapping(listOf(2L))
                assertFalse(reset.isUserEdit)
                assertNull(reset.mapping)
                assertEquals(listOf(2L), repository.weeklyMuscleSummary(week).unmapped.map { it.exerciseId })
            }
        }
    }
}
