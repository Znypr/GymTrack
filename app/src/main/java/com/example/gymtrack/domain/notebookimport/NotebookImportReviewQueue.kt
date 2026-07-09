package com.example.gymtrack.domain.notebookimport

/**
 * UI-facing review queue derived from notebook import drafts.
 *
 * This model flattens unresolved review work without changing draft state. Compose screens can later
 * render these items and call explicit review transitions.
 */
enum class NotebookReviewItemKind {
    BATCH,
    WORKOUT_DATE,
    WORKOUT_TITLE,
    WORKOUT_NOTES,
    EXERCISE_NAME,
    EXERCISE_MODE,
    EXERCISE_RESOLUTION,
    SET_REPETITIONS,
    SET_WEIGHT,
    SET_WEIGHT_UNIT,
    SET_NOTES,
    SET_STATE,
    EXERCISE_STATE,
    WORKOUT_STATE,
}

data class NotebookReviewItem(
    val id: String,
    val kind: NotebookReviewItemKind,
    val workoutId: String? = null,
    val exerciseId: String? = null,
    val setId: String? = null,
    val pageId: String? = null,
    val lineNumber: Int? = null,
    val label: String,
    val currentValue: String? = null,
    val confidence: RecognitionConfidence? = null,
) {
    init {
        require(id.isNotBlank()) { "Review item id must not be blank" }
        require(label.isNotBlank()) { "Review item label must not be blank" }
        require(workoutId == null || workoutId.isNotBlank()) { "Review item workout id must not be blank" }
        require(exerciseId == null || exerciseId.isNotBlank()) { "Review item exercise id must not be blank" }
        require(setId == null || setId.isNotBlank()) { "Review item set id must not be blank" }
        require(pageId == null || pageId.isNotBlank()) { "Review item page id must not be blank" }
        require(lineNumber == null || lineNumber > 0) { "Review item line numbers are one-based" }
        require(currentValue == null || currentValue.isNotBlank()) {
            "Review item current value must not be blank"
        }
    }
}

data class NotebookReviewQueue(
    val items: List<NotebookReviewItem>,
) {
    init {
        require(items.map { it.id }.distinct().size == items.size) {
            "Review queue item ids must be unique"
        }
    }

    val pendingCount: Int
        get() = items.size

    val isEmpty: Boolean
        get() = items.isEmpty()
}

object NotebookImportReviewQueueBuilder {

    fun build(batch: NotebookImportBatchDraft): NotebookReviewQueue = NotebookReviewQueue(
        items = buildList {
            if (batch.reviewState == ReviewState.NEEDS_REVIEW) {
                add(
                    NotebookReviewItem(
                        id = "batch:${batch.id}:state",
                        kind = NotebookReviewItemKind.BATCH,
                        label = "Import batch needs review",
                    )
                )
            }
            batch.workouts.sortedBy { it.startedAtEpochMillis.value ?: Long.MAX_VALUE }
                .forEach { workout -> addWorkoutItems(workout) }
        }
    )

    private fun MutableList<NotebookReviewItem>.addWorkoutItems(workout: NotebookWorkoutDraft) {
        addFieldItemIfNeeded(
            id = "workout:${workout.id}:date",
            kind = NotebookReviewItemKind.WORKOUT_DATE,
            workoutId = workout.id,
            label = "Workout date/start needs review",
            field = workout.startedAtEpochMillis,
        )
        addFieldItemIfNeeded(
            id = "workout:${workout.id}:title",
            kind = NotebookReviewItemKind.WORKOUT_TITLE,
            workoutId = workout.id,
            label = "Workout title needs review",
            field = workout.title,
        )
        addFieldItemIfNeeded(
            id = "workout:${workout.id}:notes",
            kind = NotebookReviewItemKind.WORKOUT_NOTES,
            workoutId = workout.id,
            label = "Workout notes need review",
            field = workout.notes,
        )
        workout.exercises.sortedBy { it.position }.forEach { exercise ->
            addExerciseItems(workout.id, exercise)
        }
        if (workout.reviewState == ReviewState.NEEDS_REVIEW) {
            add(
                NotebookReviewItem(
                    id = "workout:${workout.id}:state",
                    kind = NotebookReviewItemKind.WORKOUT_STATE,
                    workoutId = workout.id,
                    label = "Workout confirmation needed",
                )
            )
        }
    }

    private fun MutableList<NotebookReviewItem>.addExerciseItems(
        workoutId: String,
        exercise: NotebookExerciseDraft,
    ) {
        addFieldItemIfNeeded(
            id = "exercise:${exercise.id}:name",
            kind = NotebookReviewItemKind.EXERCISE_NAME,
            workoutId = workoutId,
            exerciseId = exercise.id,
            label = "Exercise name needs review",
            field = exercise.recognizedName,
        )
        addFieldItemIfNeeded(
            id = "exercise:${exercise.id}:mode",
            kind = NotebookReviewItemKind.EXERCISE_MODE,
            workoutId = workoutId,
            exerciseId = exercise.id,
            label = "Exercise mode needs review",
            field = exercise.recognizedMode,
        )
        if (!exercise.exerciseResolution.isResolvedForImport) {
            add(
                NotebookReviewItem(
                    id = "exercise:${exercise.id}:resolution",
                    kind = NotebookReviewItemKind.EXERCISE_RESOLUTION,
                    workoutId = workoutId,
                    exerciseId = exercise.id,
                    label = "Exercise mapping needs review",
                    currentValue = exercise.exerciseResolution.canonicalName,
                )
            )
        }
        exercise.sets.sortedBy { it.position }.forEach { set -> addSetItems(workoutId, exercise.id, set) }
        if (exercise.reviewState == ReviewState.NEEDS_REVIEW) {
            add(
                NotebookReviewItem(
                    id = "exercise:${exercise.id}:state",
                    kind = NotebookReviewItemKind.EXERCISE_STATE,
                    workoutId = workoutId,
                    exerciseId = exercise.id,
                    label = "Exercise confirmation needed",
                )
            )
        }
    }

    private fun MutableList<NotebookReviewItem>.addSetItems(
        workoutId: String,
        exerciseId: String,
        set: NotebookSetDraft,
    ) {
        addFieldItemIfNeeded(
            id = "set:${set.id}:repetitions",
            kind = NotebookReviewItemKind.SET_REPETITIONS,
            workoutId = workoutId,
            exerciseId = exerciseId,
            setId = set.id,
            label = "Set repetitions need review",
            field = set.repetitions,
        )
        addFieldItemIfNeeded(
            id = "set:${set.id}:weight",
            kind = NotebookReviewItemKind.SET_WEIGHT,
            workoutId = workoutId,
            exerciseId = exerciseId,
            setId = set.id,
            label = "Set weight needs review",
            field = set.weight,
        )
        addFieldItemIfNeeded(
            id = "set:${set.id}:weightUnit",
            kind = NotebookReviewItemKind.SET_WEIGHT_UNIT,
            workoutId = workoutId,
            exerciseId = exerciseId,
            setId = set.id,
            label = "Set weight unit needs review",
            field = set.weightUnit,
        )
        addFieldItemIfNeeded(
            id = "set:${set.id}:notes",
            kind = NotebookReviewItemKind.SET_NOTES,
            workoutId = workoutId,
            exerciseId = exerciseId,
            setId = set.id,
            label = "Set notes need review",
            field = set.notes,
        )
        if (set.reviewState == ReviewState.NEEDS_REVIEW) {
            add(
                NotebookReviewItem(
                    id = "set:${set.id}:state",
                    kind = NotebookReviewItemKind.SET_STATE,
                    workoutId = workoutId,
                    exerciseId = exerciseId,
                    setId = set.id,
                    label = "Set confirmation needed",
                )
            )
        }
    }

    private fun MutableList<NotebookReviewItem>.addFieldItemIfNeeded(
        id: String,
        kind: NotebookReviewItemKind,
        label: String,
        field: RecognizedField<*>?,
        workoutId: String? = null,
        exerciseId: String? = null,
        setId: String? = null,
    ) {
        if (field == null) return
        if (!field.needsReview && !field.isLowConfidence && field.value != null) return
        add(
            NotebookReviewItem(
                id = id,
                kind = kind,
                workoutId = workoutId,
                exerciseId = exerciseId,
                setId = setId,
                pageId = field.provenance.pageId,
                lineNumber = field.provenance.lineNumber,
                label = label,
                currentValue = field.value?.toString(),
                confidence = field.confidence,
            )
        )
    }
}
