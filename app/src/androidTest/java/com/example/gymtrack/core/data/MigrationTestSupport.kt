package com.example.gymtrack.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room

internal const val MIGRATION_TEST_DATABASE = "gymtrack-migration-test.db"

internal fun Context.createLegacyDatabase(
    version: Int,
    schema: List<String>,
    inserts: List<String>,
) {
    deleteDatabase(MIGRATION_TEST_DATABASE)
    val databaseFile = getDatabasePath(MIGRATION_TEST_DATABASE)
    databaseFile.parentFile?.mkdirs()

    SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
        schema.forEach(database::execSQL)
        inserts.forEach(database::execSQL)
        database.version = version
    }
}

internal fun Context.openMigratedDatabase(): NoteDatabase {
    return Room.databaseBuilder(
        this,
        NoteDatabase::class.java,
        MIGRATION_TEST_DATABASE,
    )
        .addMigrations(*DatabaseMigrations.ALL)
        .allowMainThreadQueries()
        .build()
        .also { it.openHelper.writableDatabase }
}

internal fun Context.deleteMigrationTestDatabase() {
    deleteDatabase(MIGRATION_TEST_DATABASE)
}

internal fun NoteDatabase.schemaObjectExists(type: String, name: String): Boolean {
    return openHelper.writableDatabase.query(
        "SELECT COUNT(*) FROM sqlite_master WHERE type = ? AND name = ?",
        arrayOf(type, name),
    ).use { cursor ->
        cursor.moveToFirst() && cursor.getInt(0) == 1
    }
}

internal const val VERSION_4_NOTES_SCHEMA = """
    CREATE TABLE notes (
        timestamp INTEGER NOT NULL,
        title TEXT NOT NULL,
        text TEXT NOT NULL,
        categoryName TEXT,
        categoryColor INTEGER,
        learnings TEXT,
        PRIMARY KEY(timestamp)
    )
"""
