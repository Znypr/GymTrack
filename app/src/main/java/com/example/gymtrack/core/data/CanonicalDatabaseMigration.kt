package com.example.gymtrack.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object CanonicalDatabaseMigration {
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS categories (
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    color_argb INTEGER NOT NULL,
                    position INTEGER NOT NULL,
                    is_built_in INTEGER NOT NULL,
                    is_archived INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_categories_position ON categories(position)",
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS canonical_exercises (
                    id TEXT NOT NULL,
                    canonical_name TEXT NOT NULL,
                    normalized_name TEXT NOT NULL,
                    parent_exercise_id TEXT,
                    muscle_group TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY(id),
                    FOREIGN KEY(parent_exercise_id) REFERENCES canonical_exercises(id)
                        ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_canonical_exercises_normalized_name ON canonical_exercises(normalized_name)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_canonical_exercises_parent_exercise_id ON canonical_exercises(parent_exercise_id)",
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS workouts (
                    id TEXT NOT NULL,
                    legacy_timestamp INTEGER,
                    started_at INTEGER NOT NULL,
                    ended_at INTEGER,
                    category_id TEXT,
                    title TEXT NOT NULL,
                    learnings TEXT NOT NULL,
                    status TEXT NOT NULL,
                    raw_draft_text TEXT,
                    legacy_migration_status TEXT,
                    legacy_migration_message TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY(id),
                    FOREIGN KEY(category_id) REFERENCES categories(id)
                        ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_workouts_legacy_timestamp ON workouts(legacy_timestamp)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_workouts_started_at ON workouts(started_at)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_workouts_category_id ON workouts(category_id)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_workouts_status ON workouts(status)",
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS exercise_aliases (
                    id TEXT NOT NULL,
                    exercise_id TEXT NOT NULL,
                    normalized_alias TEXT NOT NULL,
                    original_alias TEXT NOT NULL,
                    PRIMARY KEY(id),
                    FOREIGN KEY(exercise_id) REFERENCES canonical_exercises(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_exercise_aliases_exercise_id ON exercise_aliases(exercise_id)",
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_exercise_aliases_exercise_id_normalized_alias ON exercise_aliases(exercise_id, normalized_alias)",
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS workout_exercises (
                    id TEXT NOT NULL,
                    workout_id TEXT NOT NULL,
                    exercise_id TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    mode TEXT NOT NULL,
                    modifier TEXT,
                    equipment_brand TEXT,
                    started_at_offset_seconds INTEGER,
                    started_at INTEGER,
                    legacy_relative_time_text TEXT,
                    legacy_absolute_time_text TEXT,
                    PRIMARY KEY(id),
                    FOREIGN KEY(workout_id) REFERENCES workouts(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(exercise_id) REFERENCES canonical_exercises(id)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_workout_exercises_workout_id ON workout_exercises(workout_id)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_workout_exercises_exercise_id ON workout_exercises(exercise_id)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_workout_exercises_workout_id_exercise_id ON workout_exercises(workout_id, exercise_id)",
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_workout_exercises_workout_id_position ON workout_exercises(workout_id, position)",
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS workout_sets (
                    id TEXT NOT NULL,
                    workout_exercise_id TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    repetitions INTEGER,
                    weight REAL,
                    weight_unit TEXT,
                    duration_seconds INTEGER,
                    distance_meters REAL,
                    performed_at_offset_seconds INTEGER,
                    rpe REAL,
                    rir REAL,
                    PRIMARY KEY(id),
                    FOREIGN KEY(workout_exercise_id) REFERENCES workout_exercises(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_workout_sets_workout_exercise_id ON workout_sets(workout_exercise_id)",
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_workout_sets_workout_exercise_id_position ON workout_sets(workout_exercise_id, position)",
            )
        }
    }
}
