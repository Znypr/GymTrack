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
class Migration4To8Test {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun cleanUp() = context.deleteMigrationTestDatabase()

    @Test fun preservesLearningsAndCreatesStructuredTables() {
        context.createLegacyDatabase(
            4,
            listOf(VERSION_4_NOTES_SCHEMA),
            listOf("INSERT INTO notes(timestamp, title, text, learnings) VALUES (103, 'Legs', 'Squat', 'Slow eccentric')"),
        )
        context.openMigratedDatabase().use { database ->
            database.openHelper.writableDatabase.query(
                "SELECT learnings FROM notes WHERE timestamp = 103",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Slow eccentric", cursor.getString(0))
            }
            assertTrue(database.schemaObjectExists("table", "exercises"))
            assertTrue(database.schemaObjectExists("table", "sets"))
            assertTrue(database.schemaObjectExists("index", "index_sets_exerciseId"))
            assertTrue(database.schemaObjectExists("index", "index_sets_workoutId"))
        }
    }
}
