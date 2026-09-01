package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.WeightUnit
import com.example.gymtrack.domain.model.WorkoutRecord

/**
 * Canonical-history duplicate detection for validated notebook import plans.
 *
 * This still only reports candidates. It does not merge, reject, delete, or write workouts.
 */
enum class NotebookCanonicalDuplicateSeverity {
    EXACT_CANONICAL_DUPLICATE,
    POSSIBLE_SAME_START,
}

data class NotebookCanonicalDuplicateCandidate(
    val plannedWorkoutDraftId: String,
    val existingWorkoutId: String,
    val severity: NotebookCanonicalDuplicateSeverity,
    val reason: String,
) {
    init {
        require(plannedWorkoutDraftId.isNotBlank()) {
            "Planned duplicate workout draft id must not be blank"
        }
        require(existingWorkoutId.isNotBlank()) { "Existing duplicate workout id must not be blank" }
        require(reason.isNotBlank()) { "Canonical duplicate reason must not be blank" }
    }
}

data class NotebookCanonicalDuplicateReport(
    val candidates: List<NotebookCanonicalDuplicateCandidate>,
) {
    val hasCandidates: Boolean
        get() = candidates.isNotEmpty()
}

object NotebookCanonicalDuplicateDetector {

    fun detectAgainstExistingHistory(
        plan: NotebookCanonicalImportPlan,
        existingHistory: List<WorkoutRecord>,
    ): NotebookCanonicalDuplicateReport {
        require(existingHistory.map { it.workout.id }.distinct().size == existingHistory.size) {
            "Existing workout ids must be unique"
        }

        val candidates = plan.workouts.flatMap { planned ->
            existingHistory.mapNotNull { existing ->
                val severity = when {
                    planned.fingerprint() == existing.fingerprint() -> {
                        NotebookCanonicalDuplicateSeverity.EXACT_CANONICAL_DUPLICATE
                    }
                    planned.startedAtEpochMillis == existing.workout.startedAtEpochMillis -> {
                        NotebookCanonicalDuplicateSeverity.POSSIBLE_SAME_START
                    }
                    else -> null
                }
                severity?.let {
                    NotebookCanonicalDuplicateCandidate(
                        plannedWorkoutDraftId = planned.draftWorkoutId,
                        existingWorkoutId = existing.workout.id,
                        severity = it,
                        reason = when (it) {
                            NotebookCanonicalDuplicateSeverity.EXACT_CANONICAL_DUPLICATE -> {
                                "Planned workout matches an existing canonical workout by start, title, exercise order, and set values"
                            }
                            NotebookCanonicalDuplicateSeverity.POSSIBLE_SAME_START -> {
                                "Planned workout has the same start time as an existing canonical workout"
                            }
                        },
                    )
                }
            }
        }

        return NotebookCanonicalDuplicateReport(candidates = candidates)
    }

    private fun NotebookPlannedWorkout.fingerprint(): String = buildString {
        append(startedAtEpochMillis)
        append('|')
        append(title?.normalizedForMatching().orEmpty())
        exercises.sortedBy { it.position }.forEach { exercise ->
            append("|ex=")
            append(exercise.exerciseId ?: "new:${exercise.canonicalName.normalizedForMatching()}")
            exercise.sets.sortedBy { it.position }.forEach { set ->
                append("|set=")
                append(set.repetitions)
                append('x')
                append(set.weight?.normalizedWeight() ?: "?")
                append(set.weightUnit?.normalizedUnit() ?: "?")
            }
        }
    }

    private fun WorkoutRecord.fingerprint(): String = buildString {
        append(workout.startedAtEpochMillis)
        append('|')
        append(workout.title.normalizedForMatching())
        exercises.sortedBy { it.position }.forEach { exercise ->
            append("|ex=")
            append(exercise.exerciseId)
            sets.filter { it.workoutExerciseId == exercise.id }
                .sortedBy { it.position }
                .forEach { set ->
                    append("|set=")
                    append(set.repetitions ?: "?")
                    append('x')
                    append(set.weight?.normalizedWeight() ?: "?")
                    append(set.weightUnit?.normalizedUnit() ?: "?")
                }
        }
    }

    private fun Double.normalizedWeight(): String =
        if (this % 1.0 == 0.0) toInt().toString() else toString()

    private fun WeightUnit.normalizedUnit(): String = when (this) {
        WeightUnit.KILOGRAM -> "kg"
        WeightUnit.POUND -> "lb"
        WeightUnit.UNKNOWN -> "unknown"
    }
}
