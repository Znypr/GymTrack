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
class Migration2To8Test {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun cleanUp() = context.deleteMigrationTestDatabase()

    @Test fun preservesTitle() {
        context.createLegacyDatabase(
            2,
            listOf(VERSION_2_NOTES_SCHEMA),
            listOf("INSERT INTO notes(timestamp, title, text) VALUES (101, 'Push day', 'Bench')"),
        )
        context.openMigratedDatabase().use { database ->
            database.openHelper.writableDatabase.query(
                "SELECT title, text FROM notes WHERE timestamp = 101",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Push day", cursor.getString(0))
                assertEquals("Bench", cursor.getString(1))
            }
        }
    }
}
