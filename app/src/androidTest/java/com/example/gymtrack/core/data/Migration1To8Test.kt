package com.example.gymtrack.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To8Test {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun cleanUp() = context.deleteMigrationTestDatabase()

    @Test fun preservesLegacyNotes() {
        context.createLegacyDatabase(
            1,
            listOf(VERSION_1_NOTES_SCHEMA),
            listOf("INSERT INTO notes(timestamp, text) VALUES (100, 'first')"),
        )
        context.openMigratedDatabase().use { database ->
            database.openHelper.writableDatabase.query(
                "SELECT timestamp, title, text FROM notes",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(100L, cursor.getLong(0))
                assertEquals("", cursor.getString(1))
                assertEquals("first", cursor.getString(2))
            }
        }
    }
}
