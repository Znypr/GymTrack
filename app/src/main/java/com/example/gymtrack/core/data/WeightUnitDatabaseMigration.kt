package com.example.gymtrack.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object WeightUnitDatabaseMigration {
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE sets ADD COLUMN weightUnit TEXT NOT NULL DEFAULT 'KG'",
            )
        }
    }
}
