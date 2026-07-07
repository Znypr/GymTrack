package com.example.gymtrack.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.gymtrack.core.data.repository.NoteRepository
import com.example.gymtrack.core.data.repository.toEntity
import com.example.gymtrack.domain.summary.TRAINING_SUMMARY_SCHEMA_VERSION
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingSummaryOutboxTest {
    @Test
    fun explicitCompletionWritesOnePendingTrainingSummary() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val workoutRepository = WorkoutRepository(
                database = database,
                clockMillis = { COMPLETED_AT },
                summaryZoneId = ZoneId.of("UTC"),
            )

            workoutRepository.saveCompletedWorkout(pushWorkout().toEntity(), WeightUnit.LB)

            val entries = database.trainingSummaryOutboxDao().getPending()
            assertEquals(1, entries.size)
            val entry = entries.single()
            assertEquals(TRAINING_SUMMARY_SCHEMA_VERSION, entry.schemaVersion)
            assertEquals("PENDING", entry.state)
            assertEquals(0, entry.attemptCount)
            assertEquals(COMPLETED_AT, entry.createdAt)
            assertEquals(COMPLETED_AT, entry.updatedAt)
            assertTrue(entry.summaryJson.contains("\"status\":\"completed\""))
            assertTrue(entry.summaryJson.contains("Bench Press 100lb x 5"))
            assertTrue(entry.summaryJson.contains("\"source\":\"GymTrack\""))
        } finally {
            database.close()
        }
    }

    @Test
    fun autosaveDoesNotWriteTrainingSummary() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val noteRepository = NoteRepository(database.noteDao())

            noteRepository.saveNote(pushWorkout())

            assertEquals(0, database.trainingSummaryOutboxDao().getCount())
        } finally {
            database.close()
        }
    }

    @Test
    fun editingCompletedWorkoutUpdatesSameSummaryKey() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val workoutRepository = WorkoutRepository(
                database = database,
                clockMillis = { COMPLETED_AT },
                summaryZoneId = ZoneId.of("UTC"),
            )
            workoutRepository.saveCompletedWorkout(pushWorkout().toEntity(), WeightUnit.LB)
            val first = database.trainingSummaryOutboxDao().getPending().single()

            val edited = pushWorkout().copy(text = "Bench Press\n    5x 120")
            val editRepository = WorkoutRepository(
                database = database,
                clockMillis = { COMPLETED_AT + 1_000L },
                summaryZoneId = ZoneId.of("UTC"),
            )
            editRepository.saveCompletedWorkout(edited.toEntity(), WeightUnit.LB)

            val entries = database.trainingSummaryOutboxDao().getPending()
            assertEquals(1, entries.size)
            val updated = entries.single()
            assertEquals(first.workoutId, updated.workoutId)
            assertEquals(first.schemaVersion, updated.schemaVersion)
            assertEquals(first.createdAt, updated.createdAt)
            assertEquals(COMPLETED_AT + 1_000L, updated.updatedAt)
            assertTrue(updated.summaryJson.contains("Bench Press 120lb x 5"))
        } finally {
            database.close()
        }
    }

    private fun inMemoryDatabase(): NoteDatabase {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun pushWorkout() = NoteLine(
        title = "Push Day",
        text = "Bench Press\n    5x 100",
        timestamp = WORKOUT_STARTED_AT,
        categoryName = "Push",
        categoryColor = 0xFFFF3B30,
        learnings = "Strong press.",
    )

    private companion object {
        const val WORKOUT_STARTED_AT = 1_800_000_000_000L
        const val COMPLETED_AT = 1_800_000_900_000L
    }
}
