package com.example.gymtrack.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeightUnitMigrationTest {
    @Test
    fun legacySetsGainExplicitKilogramUnitDuringMigration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "weight-unit-migration-${System.nanoTime()}.db"
        context.deleteDatabase(dbName)
        createVersion8Database(context, dbName)

        val database = Room.databaseBuilder(context, NoteDatabase::class.java, dbName)
            .addMigrations(*ALL_DATABASE_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val sets = database.setDao().getAllForBackup()
            assertEquals(1, sets.size)
            assertEquals(100f, sets.single().weight)
            assertEquals("KG", sets.single().weightUnit)
        } finally {
            database.close()
            context.deleteDatabase(dbName)
        }
    }

    private fun createVersion8Database(context: Context, dbName: String) {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL(
                """
                CREATE TABLE notes (
                    timestamp INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    text TEXT NOT NULL,
                    categoryName TEXT,
                    categoryColor INTEGER,
                    learnings TEXT,
                    PRIMARY KEY(timestamp)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE exercises (
                    exerciseId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    parentId INTEGER,
                    muscleGroup TEXT,
                    aliases TEXT NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE sets (
                    setId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    workoutId INTEGER NOT NULL,
                    exerciseId INTEGER NOT NULL,
                    weight REAL NOT NULL,
                    reps INTEGER NOT NULL,
                    isUnilateral INTEGER NOT NULL,
                    modifier TEXT,
                    brand TEXT,
                    relativeTime TEXT,
                    absoluteTime TEXT,
                    FOREIGN KEY(exerciseId) REFERENCES exercises(exerciseId)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE INDEX index_sets_exerciseId ON sets(exerciseId)")
            database.execSQL("CREATE INDEX index_sets_workoutId ON sets(workoutId)")
            database.execSQL(
                "INSERT INTO notes(timestamp, title, text, categoryName, categoryColor, learnings) VALUES(?, ?, ?, ?, ?, ?)",
                arrayOf(1_800_000_000_000L, "Push", "Bench Press\n    5x 100", null, null, null),
            )
            database.execSQL(
                "INSERT INTO exercises(exerciseId, name, parentId, muscleGroup, aliases) VALUES(?, ?, ?, ?, ?)",
                arrayOf(1L, "Bench Press", null, null, ""),
            )
            database.execSQL(
                """
                INSERT INTO sets(
                    setId,
                    workoutId,
                    exerciseId,
                    weight,
                    reps,
                    isUnilateral,
                    modifier,
                    brand,
                    relativeTime,
                    absoluteTime
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(1L, 1_800_000_000_000L, 1L, 100.0, 5, 0, null, null, null, null),
            )
            database.version = 8
        }
    }
}
