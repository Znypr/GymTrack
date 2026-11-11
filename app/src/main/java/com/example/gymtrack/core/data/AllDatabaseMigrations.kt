package com.example.gymtrack.core.data

val ALL_DATABASE_MIGRATIONS = arrayOf(
    *DatabaseMigrations.ALL,
    CanonicalDatabaseMigration.MIGRATION_8_9,
    WeightUnitDatabaseMigration.MIGRATION_9_10,
    NoteRowMetadataDatabaseMigration.MIGRATION_10_11,
    TrainingSummaryOutboxDatabaseMigration.MIGRATION_11_12,
    CanonicalSetUnitBackfill.MIGRATION_12_13,
    SetTypeDatabaseMigration.MIGRATION_13_14,
    ExerciseMuscleDatabaseMigration.MIGRATION_14_15,
)
