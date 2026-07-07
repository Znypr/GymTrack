package com.example.gymtrack.core.data

val ALL_DATABASE_MIGRATIONS = DatabaseMigrations.ALL +
    CanonicalDatabaseMigration.MIGRATION_8_9 +
    WeightUnitDatabaseMigration.MIGRATION_9_10
