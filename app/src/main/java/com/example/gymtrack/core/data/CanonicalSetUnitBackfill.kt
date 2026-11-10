package com.example.gymtrack.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Fills `workout_sets.weight_unit` for weighted canonical sets that were stored without a unit
 * (keypad sets before #326). Rule, documented in docs/WEIGHT_UNITS.md:
 *
 * - The legacy `sets` rows of the same workout (`sets.workoutId = workouts.legacy_timestamp`) were
 *   written at Finish with the default unit in force then, so they record the unit actually used.
 * - When all those rows share ONE unit, every NULL-unit weighted canonical set of that workout
 *   gets it (`LB` -> `POUND`, `KG` -> `KILOGRAM`).
 * - When the workout has no legacy rows, or they mix units, the set stays NULL, which reads as
 *   `UNKNOWN`: ambiguous data stays visible instead of being guessed.
 *
 * Deterministic and idempotent: it only touches rows whose unit is NULL and never rebuilds or
 * reparses anything (#126). It runs once, as the 12 -> 13 migration.
 */
object CanonicalSetUnitBackfill {
    private const val LEGACY_UNIT = "(CASE WHEN UPPER(s.weightUnit) IN ('LB', 'LBS') THEN 'POUND' ELSE 'KILOGRAM' END)"
    private const val LEGACY_ROWS_OF_THIS_SET = """
        FROM sets AS s
        JOIN workouts AS w ON s.workoutId = w.legacy_timestamp
        JOIN workout_exercises AS we ON we.workout_id = w.id
        WHERE we.id = workout_sets.workout_exercise_id
    """

    const val SQL = """
        UPDATE workout_sets
        SET weight_unit = (SELECT MIN($LEGACY_UNIT) $LEGACY_ROWS_OF_THIS_SET)
        WHERE weight_unit IS NULL
          AND weight IS NOT NULL
          AND weight > 0
          AND (SELECT COUNT(DISTINCT $LEGACY_UNIT) $LEGACY_ROWS_OF_THIS_SET) = 1
    """

    fun run(database: SupportSQLiteDatabase) {
        database.execSQL(SQL)
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) = run(database)
    }
}
