package com.example.gymtrack.domain.recommendation

import com.example.gymtrack.domain.model.WorkoutDetails

class ExerciseOrderSuggestionService {
    fun suggestExerciseOrder(
        workouts: List<WorkoutDetails>,
        workoutLabel: String,
        maxExercises: Int = DEFAULT_MAX_EXERCISES,
    ): ExerciseOrderSuggestion? {
        if (maxExercises <= 0) return null
        val normalizedTarget = workoutLabel.normalizedLabel()
        if (normalizedTarget.isBlank()) return null

        val matchingWorkouts = workouts
            .asSequence()
            .filter { details -> details.historyLabel().normalizedLabel() == normalizedTarget }
            .sortedBy { details -> details.record.workout.startedAtEpochMillis }
            .toList()

        if (matchingWorkouts.size < MIN_MATCHING_WORKOUTS) return null

        val exerciseStats = matchingWorkouts
            .flatMap { details ->
                details.record.exercises
                    .sortedWith(compareBy({ it.position }, { it.id }))
                    .mapNotNull { occurrence ->
                        val name = details.exerciseDefinitions[occurrence.exerciseId]
                            ?.canonicalName
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        ExerciseObservation(
                            name = name,
                            normalizedName = name.normalizedExerciseName(),
                            position = occurrence.position,
                        )
                    }
                    .distinctBy { it.normalizedName }
            }
            .groupBy { it.normalizedName }
            .mapNotNull { (_, observations) ->
                val supportCount = observations.size
                val supportRatio = supportCount.toDouble() / matchingWorkouts.size.toDouble()
                if (supportCount < MIN_SUPPORT_COUNT && supportRatio < MIN_SUPPORT_RATIO) return@mapNotNull null

                val representativeName = observations
                    .groupingBy { it.name }
                    .eachCount()
                    .entries
                    .sortedWith(
                        compareByDescending<Map.Entry<String, Int>> { it.value }
                            .thenBy { it.key },
                    )
                    .first()
                    .key

                SuggestedExercise(
                    name = representativeName,
                    supportCount = supportCount,
                    matchingWorkoutCount = matchingWorkouts.size,
                    medianPosition = observations.map { it.position }.median(),
                )
            }
            .sortedWith(
                compareBy<SuggestedExercise> { it.medianPosition }
                    .thenByDescending { it.supportCount }
                    .thenBy { it.name },
            )
            .take(maxExercises)

        if (exerciseStats.isEmpty()) return null

        return ExerciseOrderSuggestion(
            workoutLabel = workoutLabel,
            exercises = exerciseStats,
            matchingWorkoutCount = matchingWorkouts.size,
        )
    }

    private fun WorkoutDetails.historyLabel(): String = category?.name
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: record.workout.title.trim()

    private fun String.normalizedLabel(): String = trim().lowercase()

    private fun String.normalizedExerciseName(): String = trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")

    private fun List<Int>.median(): Int {
        val sorted = sorted()
        if (sorted.isEmpty()) return 0
        return sorted[sorted.size / 2]
    }

    private data class ExerciseObservation(
        val name: String,
        val normalizedName: String,
        val position: Int,
    )

    private companion object {
        const val DEFAULT_MAX_EXERCISES = 6
        const val MIN_MATCHING_WORKOUTS = 2
        const val MIN_SUPPORT_COUNT = 2
        const val MIN_SUPPORT_RATIO = 0.5
    }
}

data class ExerciseOrderSuggestion(
    val workoutLabel: String,
    val exercises: List<SuggestedExercise>,
    val matchingWorkoutCount: Int,
)

data class SuggestedExercise(
    val name: String,
    val supportCount: Int,
    val matchingWorkoutCount: Int,
    val medianPosition: Int,
)
