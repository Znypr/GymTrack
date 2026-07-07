package com.example.gymtrack.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TrainingSummaryOutboxMigration {
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS training_summary_outbox (
                    workout_id TEXT NOT NULL,
                    schema_version INTEGER NOT NULL,
                    summary_json TEXT NOT NULL,
                    state TEXT NOT NULL,
                    attempt_count INTEGER NOT NULL,
                    last_error TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY(workout_id, schema_version)
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_training_summary_outbox_state ON training_summary_outbox(state)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_training_summary_outbox_updated_at ON training_summary_outbox(updated_at)",
            )
        }
    }
}
