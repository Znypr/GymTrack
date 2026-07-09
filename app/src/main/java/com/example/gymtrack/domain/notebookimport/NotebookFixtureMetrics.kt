package com.example.gymtrack.domain.notebookimport

/**
 * Lightweight fixture metrics for representative notebook import samples.
 *
 * These metrics are intentionally simple and deterministic so sample coverage can start before a
 * full OCR implementation exists.
 */
data class NotebookFixtureExpectation(
    val workoutCount: Int,
    val exerciseCount: Int,
    val setCount: Int,
    val unresolvedFieldCountMaximum: Int,
) {
    init {
        require(workoutCount >= 0) { "Expected workout count must not be negative" }
        require(exerciseCount >= 0) { "Expected exercise count must not be negative" }
        require(setCount >= 0) { "Expected set count must not be negative" }
        require(unresolvedFieldCountMaximum >= 0) {
            "Expected unresolved field maximum must not be negative"
        }
    }
}

data class NotebookFixtureMetricReport(
    val actualWorkoutCount: Int,
    val actualExerciseCount: Int,
    val actualSetCount: Int,
    val unresolvedFieldCount: Int,
    val expected: NotebookFixtureExpectation,
) {
    init {
        require(actualWorkoutCount >= 0) { "Actual workout count must not be negative" }
        require(actualExerciseCount >= 0) { "Actual exercise count must not be negative" }
        require(actualSetCount >= 0) { "Actual set count must not be negative" }
        require(unresolvedFieldCount >= 0) { "Actual unresolved count must not be negative" }
    }

    val workoutCountMatches: Boolean
        get() = actualWorkoutCount == expected.workoutCount

    val exerciseCountMatches: Boolean
        get() = actualExerciseCount == expected.exerciseCount

    val setCountMatches: Boolean
        get() = actualSetCount == expected.setCount

    val unresolvedFieldsWithinLimit: Boolean
        get() = unresolvedFieldCount <= expected.unresolvedFieldCountMaximum

    val passes: Boolean
        get() = workoutCountMatches &&
            exerciseCountMatches &&
            setCountMatches &&
            unresolvedFieldsWithinLimit
}

object NotebookFixtureMetrics {

    fun evaluate(
        batch: NotebookImportBatchDraft,
        expected: NotebookFixtureExpectation,
    ): NotebookFixtureMetricReport {
        val exercises = batch.workouts.flatMap { it.exercises }
        val sets = exercises.flatMap { it.sets }
        return NotebookFixtureMetricReport(
            actualWorkoutCount = batch.workouts.size,
            actualExerciseCount = exercises.size,
            actualSetCount = sets.size,
            unresolvedFieldCount = batch.unresolvedFieldCount(),
            expected = expected,
        )
    }

    private fun NotebookImportBatchDraft.unresolvedFieldCount(): Int =
        workouts.sumOf { workout ->
            fieldUnresolved(workout.startedAtEpochMillis) +
                fieldUnresolved(workout.title) +
                fieldUnresolved(workout.notes) +
                workout.exercises.sumOf { exercise ->
                    fieldUnresolved(exercise.recognizedName) +
                        fieldUnresolved(exercise.recognizedMode) +
                        resolutionUnresolved(exercise.exerciseResolution) +
                        exercise.sets.sumOf { set ->
                            fieldUnresolved(set.repetitions) +
                                fieldUnresolved(set.weight) +
                                fieldUnresolved(set.weightUnit) +
                                fieldUnresolved(set.notes) +
                                stateUnresolved(set.reviewState)
                        } +
                        stateUnresolved(exercise.reviewState)
                } +
                stateUnresolved(workout.reviewState)
        } + stateUnresolved(reviewState)

    private fun fieldUnresolved(field: RecognizedField<*>?): Int =
        if (field?.needsReview == true || field?.isLowConfidence == true || field?.value == null) 1 else 0

    private fun resolutionUnresolved(resolution: ExerciseResolution): Int =
        if (!resolution.isResolvedForImport) 1 else 0

    private fun stateUnresolved(state: ReviewState): Int =
        if (state == ReviewState.NEEDS_REVIEW) 1 else 0
}
