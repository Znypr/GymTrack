package com.example.gymtrack.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnsupportedMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun cleanUp() = context.deleteMigrationTestDatabase()

    @Test fun versionFiveFailsWithoutDeletingRows() {
        context.createLegacyDatabase(
            5,
            listOf(VERSION_4_NOTES_SCHEMA),
            listOf("INSERT INTO notes(timestamp, title, text) VALUES (999, 'Keep me', 'Sentinel')"),
        )
        val database = Room.databaseBuilder(context, NoteDatabase::class.java, MIGRATION_TEST_DATABASE)
            .addMigrations(*DatabaseMigrations.ALL)
            .allowMainThreadQueries()
            .build()

        assertThrows(IllegalStateException::class.java) { database.openHelper.writableDatabase }
        database.close()

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(MIGRATION_TEST_DATABASE).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { raw ->
            raw.rawQuery("SELECT title, text FROM notes WHERE timestamp = 999", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Keep me", cursor.getString(0))
                assertEquals("Sentinel", cursor.getString(1))
            }
        }
    }
}
