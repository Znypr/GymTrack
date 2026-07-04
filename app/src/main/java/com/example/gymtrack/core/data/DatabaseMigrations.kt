package com.example.gymtrack.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Contains only migrations backed by verified historical schemas.
 * Unsupported versions must fail without deleting or replacing user data.
 */
object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS notes_new (
                    timestamp INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    text TEXT NOT NULL,
                    PRIMARY KEY(timestamp)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO notes_new(timestamp, title, text)
                SELECT timestamp, '', text FROM notes
                """.trimIndent(),
            )
            database.execSQL("DROP TABLE notes")
            database.execSQL("ALTER TABLE notes_new RENAME TO notes")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE notes ADD COLUMN categoryName TEXT")
            database.execSQL("ALTER TABLE notes ADD COLUMN categoryColor INTEGER")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE notes ADD COLUMN learnings TEXT")
        }
    }

    val MIGRATION_4_8 = object : Migration(4, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS exercises (
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
                CREATE TABLE IF NOT EXISTS sets (
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
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_sets_exerciseId ON sets(exerciseId)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_sets_workoutId ON sets(workoutId)",
            )
        }
    }

    val ALL = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_8,
    )
}
