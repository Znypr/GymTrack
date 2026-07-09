package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.ExerciseMode
import com.example.gymtrack.domain.model.WeightUnit

/**
 * Explicit review transitions for notebook import drafts.
 *
 * These helpers make confirmation/correction intent visible before any UI or persistence layer is
 * added. They only mutate immutable draft copies; they do not write canonical workout history.
 */
object NotebookDraftReview {

    fun <T> confirmField(
        field: RecognizedField<T>,
        value: T,
    ): RecognizedField<T> = field.copy(
        value = value,
        reviewState = ReviewState.CONFIRMED,
    )

    fun <T> rejectField(field: RecognizedField<T>): RecognizedField<T> = field.copy(
        reviewState = ReviewState.REJECTED,
    )

    fun confirmSet(
        set: NotebookSetDraft,
        repetitions: Int,
        weight: Double? = set.weight?.value,
        weightUnit: WeightUnit? = set.weightUnit?.value,
        notes: String? = set.notes?.value,
    ): NotebookSetDraft {
        require(repetitions > 0) { "Confirmed repetitions must be positive" }
        require(weight == null || weight >= 0.0) { "Confirmed weight must not be negative" }
        require(weight == null || weightUnit != null) {
            "Confirmed weighted sets require an explicit unit"
        }
        require(weightUnit != WeightUnit.UNKNOWN) {
            "Confirmed weighted sets require a known unit"
        }
        require(notes == null || notes.isNotBlank()) { "Confirmed set notes must not be blank" }

        val provenance = set.primaryProvenance()
        return set.copy(
            repetitions = set.repetitions?.let { confirmField(it, repetitions) }
                ?: confirmedField(repetitions, provenance),
            weight = if (weight == null) null else set.weight?.let { confirmField(it, weight) }
                ?: confirmedField(weight, provenance),
            weightUnit = if (weight == null) null else set.weightUnit?.let { confirmField(it, weightUnit!!) }
                ?: confirmedField(weightUnit!!, provenance),
            notes = when {
                notes == null -> null
                set.notes != null -> confirmField(set.notes, notes)
                else -> confirmedField(notes, provenance)
            },
            reviewState = ReviewState.CONFIRMED,
        )
    }

    fun rejectSet(set: NotebookSetDraft): NotebookSetDraft = set.copy(reviewState = ReviewState.REJECTED)

    fun confirmExercise(
        exercise: NotebookExerciseDraft,
        mode: ExerciseMode,
        resolution: ExerciseResolution,
    ): NotebookExerciseDraft {
        require(!exercise.recognizedName.value.isNullOrBlank()) {
            "Confirmed exercise requires a recognized or corrected name"
        }
        require(resolution.reviewState == ReviewState.CONFIRMED) {
            "Confirmed exercise requires a confirmed exercise resolution"
        }
        require(resolution.isResolvedForImport) {
            "Confirmed exercise requires an importable exercise resolution"
        }
        require(exercise.sets.all { it.isReadyForCanonicalImport }) {
            "Confirmed exercise requires every selected set to be confirmed"
        }

        return exercise.copy(
            recognizedName = confirmField(exercise.recognizedName, exercise.recognizedName.value),
            recognizedMode = confirmField(exercise.recognizedMode, mode),
            exerciseResolution = resolution,
            reviewState = ReviewState.CONFIRMED,
        )
    }

    fun confirmExistingExerciseResolution(
        exerciseId: String,
        canonicalName: String,
    ): ExerciseResolution = ExerciseResolution(
        kind = ExerciseResolutionKind.MATCH_EXISTING,
        exerciseId = exerciseId,
        canonicalName = canonicalName,
        reviewState = ReviewState.CONFIRMED,
    )

    fun confirmNewExerciseResolution(canonicalName: String): ExerciseResolution = ExerciseResolution(
        kind = ExerciseResolutionKind.CREATE_NEW,
        canonicalName = canonicalName,
        reviewState = ReviewState.CONFIRMED,
    )

    fun rejectExercise(exercise: NotebookExerciseDraft): NotebookExerciseDraft = exercise.copy(
        reviewState = ReviewState.REJECTED,
    )

    fun confirmWorkout(
        workout: NotebookWorkoutDraft,
        startedAtEpochMillis: Long,
        title: String? = workout.title?.value,
        notes: String? = workout.notes?.value,
    ): NotebookWorkoutDraft {
        require(startedAtEpochMillis >= 0) { "Confirmed workout start must not be negative" }
        require(title == null || title.isNotBlank()) { "Confirmed workout title must not be blank" }
        require(notes == null || notes.isNotBlank()) { "Confirmed workout notes must not be blank" }
        require(workout.exercises.all { it.isReadyForCanonicalImport }) {
            "Confirmed workout requires all selected exercises to be confirmed"
        }

        return workout.copy(
            startedAtEpochMillis = confirmField(workout.startedAtEpochMillis, startedAtEpochMillis),
            title = when {
                title == null -> null
                workout.title != null -> confirmField(workout.title, title)
                else -> confirmedField(title, workout.startedAtEpochMillis.provenance)
            },
            notes = when {
                notes == null -> null
                workout.notes != null -> confirmField(workout.notes, notes)
                else -> confirmedField(notes, workout.startedAtEpochMillis.provenance)
            },
            reviewState = ReviewState.CONFIRMED,
        )
    }

    fun rejectWorkout(workout: NotebookWorkoutDraft): NotebookWorkoutDraft = workout.copy(
        reviewState = ReviewState.REJECTED,
    )

    fun confirmBatch(batch: NotebookImportBatchDraft): NotebookImportBatchDraft {
        require(batch.workouts.isNotEmpty()) { "Confirmed import batch requires at least one workout" }
        require(batch.workouts.all { it.isReadyForCanonicalImport }) {
            "Confirmed import batch requires all workouts to be ready for canonical import"
        }
        return batch.copy(reviewState = ReviewState.CONFIRMED)
    }

    fun rejectBatch(batch: NotebookImportBatchDraft): NotebookImportBatchDraft = batch.copy(
        reviewState = ReviewState.REJECTED,
    )

    private fun <T> confirmedField(
        value: T,
        provenance: NotebookLineProvenance,
    ): RecognizedField<T> = RecognizedField(
        value = value,
        confidence = RecognitionConfidence(1.0),
        reviewState = ReviewState.CONFIRMED,
        provenance = provenance,
    )

    private fun NotebookSetDraft.primaryProvenance(): NotebookLineProvenance =
        repetitions?.provenance ?: weight?.provenance ?: weightUnit?.provenance ?: notes?.provenance
        ?: NotebookLineProvenance(pageId = "unknown")
}
