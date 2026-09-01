package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.ExerciseMode
import com.example.gymtrack.domain.model.WeightUnit

/**
 * Canonical import plan generated from a fully reviewed notebook import batch.
 *
 * This is still not a database write. It is the validated payload that a later repository
 * transaction can consume when canonical import is implemented.
 */
data class NotebookCanonicalImportPlan(
    val batchId: String,
    val workouts: List<NotebookPlannedWorkout>,
) {
    init {
        require(batchId.isNotBlank()) { "Import plan batch id must not be blank" }
        require(workouts.isNotEmpty()) { "Import plan requires at least one workout" }
        require(workouts.map { it.draftWorkoutId }.distinct().size == workouts.size) {
            "Import plan workout draft ids must be unique"
        }
    }
}

data class NotebookPlannedWorkout(
    val draftWorkoutId: String,
    val sourcePageIds: Set<String>,
    val startedAtEpochMillis: Long,
    val title: String? = null,
    val notes: String? = null,
    val exercises: List<NotebookPlannedExercise>,
) {
    init {
        require(draftWorkoutId.isNotBlank()) { "Planned workout id must not be blank" }
        require(sourcePageIds.isNotEmpty()) { "Planned workout requires source page provenance" }
        require(sourcePageIds.none { it.isBlank() }) { "Planned source page ids must not be blank" }
        require(startedAtEpochMillis >= 0) { "Planned workout start must not be negative" }
        require(title == null || title.isNotBlank()) { "Planned workout title must not be blank" }
        require(notes == null || notes.isNotBlank()) { "Planned workout notes must not be blank" }
        require(exercises.isNotEmpty()) { "Planned workout requires at least one exercise" }
        require(exercises.map { it.draftExerciseId }.distinct().size == exercises.size) {
            "Planned exercise draft ids must be unique within a workout"
        }
        require(exercises.map { it.position }.distinct().size == exercises.size) {
            "Planned exercise positions must be unique within a workout"
        }
    }
}

data class NotebookPlannedExercise(
    val draftExerciseId: String,
    val position: Int,
    val mode: ExerciseMode,
    val resolutionKind: ExerciseResolutionKind,
    val exerciseId: String? = null,
    val canonicalName: String,
    val sets: List<NotebookPlannedSet>,
) {
    init {
        require(draftExerciseId.isNotBlank()) { "Planned exercise draft id must not be blank" }
        require(position >= 0) { "Planned exercise position must not be negative" }
        require(canonicalName.isNotBlank()) { "Planned canonical exercise name must not be blank" }
        when (resolutionKind) {
            ExerciseResolutionKind.MATCH_EXISTING -> require(!exerciseId.isNullOrBlank()) {
                "Existing planned exercises require an exercise id"
            }
            ExerciseResolutionKind.CREATE_NEW -> require(exerciseId == null) {
                "New planned exercises must not carry an existing exercise id"
            }
            ExerciseResolutionKind.UNRESOLVED -> error("Unresolved exercise cannot be planned for import")
        }
        require(sets.isNotEmpty()) { "Planned exercises require at least one set" }
        require(sets.map { it.draftSetId }.distinct().size == sets.size) {
            "Planned set draft ids must be unique within an exercise"
        }
        require(sets.map { it.position }.distinct().size == sets.size) {
            "Planned set positions must be unique within an exercise"
        }
    }
}

data class NotebookPlannedSet(
    val draftSetId: String,
    val position: Int,
    val repetitions: Int,
    val weight: Double? = null,
    val weightUnit: WeightUnit? = null,
    val notes: String? = null,
) {
    init {
        require(draftSetId.isNotBlank()) { "Planned set draft id must not be blank" }
        require(position >= 0) { "Planned set position must not be negative" }
        require(repetitions > 0) { "Planned repetitions must be positive" }
        require(weight == null || weight >= 0.0) { "Planned weight must not be negative" }
        require(weight == null || weightUnit != null) { "Weighted planned sets require a unit" }
        require(weightUnit != WeightUnit.UNKNOWN) { "Planned weighted sets require a known unit" }
        require(notes == null || notes.isNotBlank()) { "Planned set notes must not be blank" }
    }
}

object NotebookCanonicalImportPlanner {

    fun buildPlan(
        batch: NotebookImportBatchDraft,
        duplicateReport: NotebookWorkoutDuplicateReport = NotebookWorkoutDuplicateDetector.detectWithinBatch(batch),
    ): NotebookCanonicalImportPlan {
        require(batch.canWriteCanonicalHistory) {
            "Notebook import plan requires a fully confirmed import batch"
        }
        require(!duplicateReport.hasCandidates) {
            "Notebook import plan requires duplicate candidates to be resolved first"
        }

        return NotebookCanonicalImportPlan(
            batchId = batch.id,
            workouts = batch.workouts.map { it.toPlan() },
        )
    }

    private fun NotebookWorkoutDraft.toPlan(): NotebookPlannedWorkout = NotebookPlannedWorkout(
        draftWorkoutId = id,
        sourcePageIds = sourcePageIds,
        startedAtEpochMillis = startedAtEpochMillis.confirmedValue("workout start"),
        title = title.confirmedOptionalValue("workout title"),
        notes = notes.confirmedOptionalValue("workout notes"),
        exercises = exercises.sortedBy { it.position }.map { it.toPlan() },
    )

    private fun NotebookExerciseDraft.toPlan(): NotebookPlannedExercise = NotebookPlannedExercise(
        draftExerciseId = id,
        position = position,
        mode = recognizedMode.confirmedValue("exercise mode"),
        resolutionKind = exerciseResolution.kind,
        exerciseId = exerciseResolution.exerciseId,
        canonicalName = exerciseResolution.canonicalName
            ?: recognizedName.confirmedValue("exercise name"),
        sets = sets.sortedBy { it.position }.map { it.toPlan() },
    )

    private fun NotebookSetDraft.toPlan(): NotebookPlannedSet = NotebookPlannedSet(
        draftSetId = id,
        position = position,
        repetitions = repetitions.confirmedRequiredField("set repetitions"),
        weight = weight.confirmedOptionalValue("set weight"),
        weightUnit = weightUnit.confirmedOptionalValue("set weight unit"),
        notes = notes.confirmedOptionalValue("set notes"),
    )

    private fun <T> RecognizedField<T>.confirmedValue(fieldName: String): T {
        require(reviewState == ReviewState.CONFIRMED && value != null) {
            "Planned import requires confirmed $fieldName"
        }
        return value
    }

    private fun <T> RecognizedField<T>?.confirmedRequiredField(fieldName: String): T {
        require(this != null) { "Planned import requires $fieldName" }
        return confirmedValue(fieldName)
    }

    private fun <T> RecognizedField<T>?.confirmedOptionalValue(fieldName: String): T? {
        if (this == null) return null
        require(reviewState == ReviewState.CONFIRMED) {
            "Planned import cannot include unconfirmed $fieldName"
        }
        return value
    }
}
