package com.example.gymtrack.domain.recommendation

import com.example.gymtrack.domain.model.Workout
import com.example.gymtrack.domain.model.WorkoutDetails
import com.example.gymtrack.domain.model.WorkoutRecord
import com.example.gymtrack.domain.model.WorkoutStatus
import com.example.gymtrack.domain.repository.CanonicalWorkoutRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class NextWorkoutPredictionProviderTest {
    @Test
    fun loadsConfiguredRecentHistoryLimitAndReturnsSuggestion() = runBlocking {
        val repository = Repository(
            recentCompleted = listOf(
                workoutDetails(label = "Push", day = 1),
                workoutDetails(label = "Pull", day = 2),
                workoutDetails(label = "Push", day = 3),
            ),
        )
        val provider = NextWorkoutPredictionProvider(
            repository = repository,
            historyLimit = 12,
        )

        val suggestion = provider.getSuggestion(nowEpochMillis = dayMillis(4))

        requireNotNull(suggestion)
        assertEquals("Pull", suggestion.workoutLabel)
        assertEquals(listOf(12), repository.recentCompletedRequests)
    }

    @Test
    fun returnsNullWhenRecentHistoryHasNoDefensibleSuggestion() = runBlocking {
        val repository = Repository(recentCompleted = emptyList())
        val provider = NextWorkoutPredictionProvider(repository = repository)

        assertNull(provider.getSuggestion(nowEpochMillis = dayMillis(4)))
        assertEquals(
            listOf(NextWorkoutPredictionProvider.DEFAULT_HISTORY_LIMIT),
            repository.recentCompletedRequests,
        )
    }

    @Test
    fun rejectsNonPositiveHistoryLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            NextWorkoutPredictionProvider(
                repository = Repository(recentCompleted = emptyList()),
                historyLimit = 0,
            )
        }
    }

    private class Repository(
        private val recentCompleted: List<WorkoutDetails>,
    ) : CanonicalWorkoutRepository {
        val recentCompletedRequests = mutableListOf<Int>()

        override suspend fun getById(workoutId: String): WorkoutDetails? = null

        override suspend fun getByLegacyTimestamp(legacyTimestamp: Long): WorkoutDetails? = null

        override suspend fun getRecentCompleted(limit: Int): List<WorkoutDetails> {
            recentCompletedRequests += limit
            return recentCompleted.take(limit)
        }

        override suspend fun save(details: WorkoutDetails) = Unit
    }

    private fun workoutDetails(
        label: String,
        day: Int,
    ): WorkoutDetails {
        val start = dayMillis(day)
        return WorkoutDetails(
            record = WorkoutRecord(
                workout = Workout(
                    id = "workout-$day-$label",
                    startedAtEpochMillis = start,
                    title = label,
                    status = WorkoutStatus.COMPLETED,
                    createdAtEpochMillis = start,
                    updatedAtEpochMillis = start,
                ),
                exercises = emptyList(),
                sets = emptyList(),
            ),
            exerciseDefinitions = emptyMap(),
        )
    }

    private fun dayMillis(day: Int): Long = day * 86_400_000L
}
