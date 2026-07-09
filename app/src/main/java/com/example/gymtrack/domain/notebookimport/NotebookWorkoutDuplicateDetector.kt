package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.WeightUnit

/**
 * Draft-level duplicate detection for notebook imports.
 *
 * This detector only flags duplicate candidates. It does not delete, merge, reject, confirm, or write
 * any workouts. Canonical duplicate checks against persisted history are a later layer.
 */
enum class NotebookDuplicateSeverity {
    EXACT_DRAFT_DUPLICATE,
    POSSIBLE_DATE_DUPLICATE,
}

data class NotebookWorkoutDuplicateCandidate(
    val firstWorkoutId: String,
    val secondWorkoutId: String,
    val severity: NotebookDuplicateSeverity,
    val reason: String,
) {
    init {
        require(firstWorkoutId.isNotBlank()) { "First duplicate workout id must not be blank" }
        require(secondWorkoutId.isNotBlank()) { "Second duplicate workout id must not be blank" }
        require(firstWorkoutId != secondWorkoutId) { "Duplicate candidate requires two different workouts" }
        require(reason.isNotBlank()) { "Duplicate candidate reason must not be blank" }
    }
}

data class NotebookWorkoutDuplicateReport(
    val candidates: List<NotebookWorkoutDuplicateCandidate>,
) {
    val hasCandidates: Boolean
        get() = candidates.isNotEmpty()
}

object NotebookWorkoutDuplicateDetector {

    fun detectWithinBatch(batch: NotebookImportBatchDraft): NotebookWorkoutDuplicateReport {
        val candidates = mutableListOf<NotebookWorkoutDuplicateCandidate>()
        batch.workouts.forEachIndexed { leftIndex, left ->
            batch.workouts.drop(leftIndex + 1).forEach { right ->
                val leftFingerprint = left.duplicateFingerprint()
                val rightFingerprint = right.duplicateFingerprint()
                val severity = when {
                    leftFingerprint == rightFingerprint -> NotebookDuplicateSeverity.EXACT_DRAFT_DUPLICATE
                    left.startedAtEpochMillis.value != null &&
                        left.startedAtEpochMillis.value == right.startedAtEpochMillis.value -> {
                        NotebookDuplicateSeverity.POSSIBLE_DATE_DUPLICATE
                    }
                    else -> null
                }
                if (severity != null) {
                    candidates += NotebookWorkoutDuplicateCandidate(
                        firstWorkoutId = left.id,
                        secondWorkoutId = right.id,
                        severity = severity,
                        reason = when (severity) {
                            NotebookDuplicateSeverity.EXACT_DRAFT_DUPLICATE -> {
                                "Same interpreted date, title, exercise order, and set values"
                            }
                            NotebookDuplicateSeverity.POSSIBLE_DATE_DUPLICATE -> {
                                "Same interpreted workout date; review before importing both"
                            }
                        },
                    )
                }
            }
        }
        return NotebookWorkoutDuplicateReport(candidates = candidates)
    }

    private fun NotebookWorkoutDraft.duplicateFingerprint(): String = buildString {
        append(startedAtEpochMillis.value ?: "unknown-date")
        append('|')
        append(title?.value?.normalizedForMatching().orEmpty())
        exercises.sortedBy { it.position }.forEach { exercise ->
            append("|ex=")
            append(exercise.recognizedName.value?.normalizedForMatching().orEmpty())
            exercise.sets.sortedBy { it.position }.forEach { set ->
                append("|set=")
                append(set.repetitions?.value ?: "?")
                append('x')
                append(set.weight?.value?.normalizedWeight() ?: "?")
                append(set.weightUnit?.value?.normalizedUnit() ?: "?")
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
