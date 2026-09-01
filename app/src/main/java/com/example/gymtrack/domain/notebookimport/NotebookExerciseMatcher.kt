package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.Exercise

/**
 * Conservative exercise-name matching for notebook import review.
 *
 * The matcher can propose an existing exercise or a new exercise name, but it never confirms the
 * mapping. Ambiguous matches remain unresolved for explicit user review.
 */
enum class NotebookExerciseMatchKind {
    EXACT_CANONICAL_NAME,
    ALIAS,
}

data class NotebookExerciseMatchCandidate(
    val exerciseDraftId: String,
    val exerciseId: String,
    val canonicalName: String,
    val matchedText: String,
    val kind: NotebookExerciseMatchKind,
    val confidence: RecognitionConfidence,
) {
    init {
        require(exerciseDraftId.isNotBlank()) { "Exercise draft id must not be blank" }
        require(exerciseId.isNotBlank()) { "Matched exercise id must not be blank" }
        require(canonicalName.isNotBlank()) { "Matched canonical exercise name must not be blank" }
        require(matchedText.isNotBlank()) { "Matched exercise text must not be blank" }
    }
}

data class NotebookExerciseMatchingResult(
    val batch: NotebookImportBatchDraft,
    val candidatesByExerciseDraftId: Map<String, List<NotebookExerciseMatchCandidate>>, 
    val warnings: List<String> = emptyList(),
) {
    init {
        require(warnings.none { it.isBlank() }) { "Exercise matching warnings must not be blank" }
    }

    val requiresReview: Boolean
        get() = warnings.isNotEmpty() || batch.hasUnresolvedFields
}

object NotebookExerciseMatcher {

    fun matchExercises(
        batch: NotebookImportBatchDraft,
        exerciseCatalog: List<Exercise>,
    ): NotebookExerciseMatchingResult {
        require(exerciseCatalog.map { it.id }.distinct().size == exerciseCatalog.size) {
            "Exercise catalog ids must be unique"
        }

        val candidatesByDraftId = mutableMapOf<String, List<NotebookExerciseMatchCandidate>>()
        val warnings = mutableListOf<String>()
        val updatedWorkouts = batch.workouts.map { workout ->
            workout.copy(
                exercises = workout.exercises.map { draft ->
                    val name = draft.recognizedName.value?.trim()
                    if (name.isNullOrBlank()) {
                        warnings += "Exercise ${draft.id} has no recognized name to match"
                        draft
                    } else {
                        val candidates = findCandidates(draft.id, name, draft.recognizedName.confidence, exerciseCatalog)
                        candidatesByDraftId[draft.id] = candidates
                        when (candidates.size) {
                            0 -> draft.copy(
                                exerciseResolution = ExerciseResolution(
                                    kind = ExerciseResolutionKind.CREATE_NEW,
                                    canonicalName = name,
                                    reviewState = ReviewState.NEEDS_REVIEW,
                                ),
                            )
                            1 -> draft.copy(
                                exerciseResolution = ExerciseResolution(
                                    kind = ExerciseResolutionKind.MATCH_EXISTING,
                                    exerciseId = candidates.single().exerciseId,
                                    canonicalName = candidates.single().canonicalName,
                                    reviewState = ReviewState.NEEDS_REVIEW,
                                ),
                            )
                            else -> {
                                warnings += "Exercise ${draft.id} matched multiple catalog entries"
                                draft.copy(exerciseResolution = ExerciseResolution())
                            }
                        }
                    }
                }
            )
        }

        return NotebookExerciseMatchingResult(
            batch = batch.copy(workouts = updatedWorkouts),
            candidatesByExerciseDraftId = candidatesByDraftId,
            warnings = warnings,
        )
    }

    private fun findCandidates(
        exerciseDraftId: String,
        recognizedName: String,
        confidence: RecognitionConfidence,
        exerciseCatalog: List<Exercise>,
    ): List<NotebookExerciseMatchCandidate> {
        val normalized = recognizedName.normalizedForMatching()
        return exerciseCatalog.flatMap { exercise ->
            val exact = if (exercise.canonicalName.normalizedForMatching() == normalized) {
                listOf(
                    NotebookExerciseMatchCandidate(
                        exerciseDraftId = exerciseDraftId,
                        exerciseId = exercise.id,
                        canonicalName = exercise.canonicalName,
                        matchedText = exercise.canonicalName,
                        kind = NotebookExerciseMatchKind.EXACT_CANONICAL_NAME,
                        confidence = confidence,
                    )
                )
            } else {
                emptyList()
            }
            val aliases = exercise.aliases
                .filter { it.normalizedForMatching() == normalized }
                .map { alias ->
                    NotebookExerciseMatchCandidate(
                        exerciseDraftId = exerciseDraftId,
                        exerciseId = exercise.id,
                        canonicalName = exercise.canonicalName,
                        matchedText = alias,
                        kind = NotebookExerciseMatchKind.ALIAS,
                        confidence = confidence,
                    )
                }
            exact + aliases
        }.distinctBy { it.exerciseId to it.kind }
            .sortedWith(compareBy<NotebookExerciseMatchCandidate> { it.kind.ordinal }.thenBy { it.canonicalName })
    }
}

internal fun String.normalizedForMatching(): String = trim()
    .lowercase()
    .replace(Regex("""[\s_\-]+"""), " ")
    .replace(Regex("""[^a-z0-9äöüß ]"""), "")
    .replace(Regex("""\s+"""), " ")
    .trim()
