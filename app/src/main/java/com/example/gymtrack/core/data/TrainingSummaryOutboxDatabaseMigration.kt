package com.example.gymtrack.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TrainingSummaryOutboxDatabaseMigration {
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS training_summary_outbox (
                    summary_key TEXT NOT NULL,
                    workout_id TEXT NOT NULL,
                    schema_version INTEGER NOT NULL,
                    payload_json TEXT NOT NULL,
                    status TEXT NOT NULL,
                    attempt_count INTEGER NOT NULL,
                    last_error TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    source_updated_at TEXT NOT NULL,
                    PRIMARY KEY(summary_key)
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_training_summary_outbox_workout_id_schema_version ON training_summary_outbox(workout_id, schema_version)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_training_summary_outbox_status ON training_summary_outbox(status)",
            )
        }
    }
}
