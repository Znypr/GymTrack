package com.example.gymtrack.domain.recommendation

import com.example.gymtrack.domain.repository.CanonicalWorkoutRepository

class NextWorkoutPredictionProvider(
    private val repository: CanonicalWorkoutRepository,
    private val predictionService: NextWorkoutPredictionService = NextWorkoutPredictionService(),
    private val exerciseOrderService: ExerciseOrderSuggestionService = ExerciseOrderSuggestionService(),
    private val historyLimit: Int = DEFAULT_HISTORY_LIMIT,
) {
    init {
        require(historyLimit > 0) { "History limit must be positive" }
    }

    suspend fun getSuggestion(nowEpochMillis: Long): NextWorkoutSuggestion? {
        val history = repository.getRecentPredictionHistory(historyLimit)
        return predictionService.predictNextWorkout(
            workouts = history,
            nowEpochMillis = nowEpochMillis,
        )
    }

    suspend fun getExerciseOrderSuggestion(workoutLabel: String): ExerciseOrderSuggestion? {
        val history = repository.getRecentPredictionHistory(historyLimit)
        return exerciseOrderService.suggestExerciseOrder(
            workouts = history,
            workoutLabel = workoutLabel,
        )
    }

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 24
    }
}
