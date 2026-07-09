package com.example.gymtrack.domain.summary

import com.example.gymtrack.domain.model.Workout
import com.example.gymtrack.domain.model.WorkoutDetails
import com.example.gymtrack.domain.model.WorkoutRecord
import com.example.gymtrack.domain.model.WorkoutStatus
import com.example.gymtrack.domain.repository.CanonicalWorkoutRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SummaryServiceTest {
    @Test
    fun loadsCanonicalWorkoutAndReturnsNullWhenMissing() = runBlocking {
        val start = Instant.parse("2026-07-05T13:00:00Z").toEpochMilli()
        val details = WorkoutDetails(
            record = WorkoutRecord(
                workout = Workout(
                    id = "workout-1",
                    startedAtEpochMillis = start,
                    title = "Pull",
                    status = WorkoutStatus.PARTIAL,
                    createdAtEpochMillis = start,
                    updatedAtEpochMillis = start,
                ),
                exercises = emptyList(),
                sets = emptyList(),
            ),
            exerciseDefinitions = emptyMap(),
        )
        val repository = Repository(mapOf("workout-1" to details))
        val service = CanonicalTrainingSummaryService(repository)

        assertEquals(
            "workout-1",
            service.getByWorkoutId("workout-1", ZoneId.of("UTC"))?.workoutId,
        )
        assertNull(service.getByWorkoutId("missing", ZoneId.of("UTC")))
        assertEquals(listOf("workout-1", "missing"), repository.requests)
    }

    private class Repository(
        private val workouts: Map<String, WorkoutDetails>,
    ) : CanonicalWorkoutRepository {
        val requests = mutableListOf<String>()

        override suspend fun getById(workoutId: String): WorkoutDetails? {
            requests += workoutId
            return workouts[workoutId]
        }

        override suspend fun getByLegacyTimestamp(legacyTimestamp: Long): WorkoutDetails? = null

        override suspend fun getRecentPredictionHistory(limit: Int): List<WorkoutDetails> = emptyList()

        override suspend fun save(details: WorkoutDetails) = Unit
    }
}
