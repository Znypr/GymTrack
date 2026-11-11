package com.example.gymtrack.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v14 → v15 (#309, #310): the user's muscle overrides and the real `muscle_week` rows.
 *
 * - `exercise_muscles` starts empty: every exercise uses the seed until the user edits it.
 * - `muscle_week` is derived data, so it is dropped and recreated with `exercise_id` (per-muscle
 *   exercise breakdown for the UI). [DerivedIndexWriter.VERSION] moved to 2, so the first reader
 *   rebuilds it lazily from the stored `sets`; logged sets are not touched.
 */
object ExerciseMuscleDatabaseMigration {
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS exercise_muscles (
                    exercise_id INTEGER NOT NULL,
                    muscle TEXT NOT NULL,
                    role TEXT NOT NULL,
                    source TEXT NOT NULL,
                    PRIMARY KEY(exercise_id, muscle),
                    FOREIGN KEY(exercise_id) REFERENCES exercises(exerciseId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_muscles_exercise_id ON exercise_muscles (exercise_id)")

            database.execSQL("DROP TABLE IF EXISTS muscle_week")
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS muscle_week (
                    workout_id INTEGER NOT NULL,
                    week_key TEXT NOT NULL,
                    muscle TEXT NOT NULL,
                    exercise_id INTEGER NOT NULL,
                    effective_sets REAL NOT NULL,
                    PRIMARY KEY(workout_id, muscle, exercise_id)
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_muscle_week_week_key_muscle ON muscle_week (week_key, muscle)")
            // The stub rows are gone; drop the stamp too so the rebuild also runs on a same-version DB.
            database.execSQL("DELETE FROM derived_index_state")
        }
    }
}
