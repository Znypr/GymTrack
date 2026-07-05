package com.example.gymtrack.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TrainingSummaryOutboxMigration {
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS training_summary_outbox (
                    workout_id TEXT NOT NULL,
                    schema_version INTEGER NOT NULL,
                    payload_json TEXT NOT NULL,
                    source_updated_at INTEGER NOT NULL,
                    enqueued_at INTEGER NOT NULL,
                    attempt_count INTEGER NOT NULL,
                    last_error TEXT,
                    PRIMARY KEY(workout_id),
                    FOREIGN KEY(workout_id) REFERENCES workouts(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_training_summary_outbox_enqueued_at ON training_summary_outbox(enqueued_at)",
            )
        }
    }
}
