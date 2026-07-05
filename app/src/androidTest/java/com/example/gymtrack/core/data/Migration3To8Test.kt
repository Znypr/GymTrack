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
class Migration3To8Test {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun cleanUp() = context.deleteMigrationTestDatabase()

    @Test fun preservesCategory() {
        context.createLegacyDatabase(
            3,
            listOf(VERSION_3_NOTES_SCHEMA),
            listOf("INSERT INTO notes(timestamp, title, text, categoryName, categoryColor) VALUES (102, 'Pull', 'Rows', 'Pull', 4278255360)"),
        )
        context.openMigratedDatabase().use { database ->
            database.openHelper.writableDatabase.query(
                "SELECT categoryName, categoryColor FROM notes WHERE timestamp = 102",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Pull", cursor.getString(0))
                assertEquals(4_278_255_360L, cursor.getLong(1))
            }
        }
    }
}
