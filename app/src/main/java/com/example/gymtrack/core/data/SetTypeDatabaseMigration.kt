package com.example.gymtrack.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v13 → v14 (#306, #307): set type, its source and rest on `sets`, plus the derived tables.
 *
 * Existing rows become WORKING / INFERRED (nothing is hidden from stats by the migration). The
 * derived tables start empty with no version stamp, so the first reader runs the lazy, chunked
 * rebuild ([WorkoutRepository.ensureDerivedIndexes]), which also infers warm-ups for old rows.
 * Nothing is rebuilt at startup (#126).
 */
object SetTypeDatabaseMigration {
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE sets ADD COLUMN setType TEXT NOT NULL DEFAULT 'WORKING'")
            database.execSQL("ALTER TABLE sets ADD COLUMN setTypeSource TEXT NOT NULL DEFAULT 'INFERRED'")
            database.execSQL("ALTER TABLE sets ADD COLUMN restSeconds INTEGER")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS exercise_recent (
                    workout_id INTEGER NOT NULL,
                    exercise_id INTEGER NOT NULL,
                    working_sets INTEGER NOT NULL,
                    warmup_sets INTEGER NOT NULL,
                    working_reps INTEGER NOT NULL,
                    top_weight REAL NOT NULL,
                    top_weight_unit TEXT NOT NULL,
                    top_reps INTEGER NOT NULL,
                    volume_kg REAL NOT NULL,
                    sets_summary TEXT NOT NULL,
                    PRIMARY KEY(workout_id, exercise_id)
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_exercise_recent_exercise_id_workout_id " +
                    "ON exercise_recent (exercise_id, workout_id)",
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS muscle_week (
                    workout_id INTEGER NOT NULL,
                    week_key TEXT NOT NULL,
                    muscle TEXT NOT NULL,
                    effective_sets REAL NOT NULL,
                    PRIMARY KEY(workout_id, muscle)
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_muscle_week_week_key_muscle ON muscle_week (week_key, muscle)",
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS derived_index_state (
                    name TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    rebuilt_through INTEGER NOT NULL,
                    complete INTEGER NOT NULL,
                    PRIMARY KEY(name)
                )
                """.trimIndent(),
            )
        }
    }
}
