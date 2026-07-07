package com.example.gymtrack.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeightUnitPersistenceTest {
    @Test
    fun completedWorkoutStoresDefaultAndExplicitWeightUnits() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = WorkoutRepository(database)
            val note = NoteEntity(
                timestamp = 1_800_000_000_000L,
                title = "Mixed Unit Push",
                text = "Bench Press\n    5x 100\n    8x 90 kg",
                categoryName = null,
                categoryColor = null,
                learnings = null,
            )

            repository.saveCompletedWorkout(note, WeightUnit.LB)

            val sets = database.setDao().getAllForBackup()
            assertEquals(2, sets.size)
            assertEquals(100f, sets[0].weight)
            assertEquals("LB", sets[0].weightUnit)
            assertEquals(90f, sets[1].weight)
            assertEquals("KG", sets[1].weightUnit)
        } finally {
            database.close()
        }
    }
}
