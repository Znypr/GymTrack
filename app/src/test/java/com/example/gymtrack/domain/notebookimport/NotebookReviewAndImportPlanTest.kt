package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.ExerciseMode
import com.example.gymtrack.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookReviewAndImportPlanTest {

    @Test
    fun reviewFlowConfirmsDraftAndBuildsCanonicalImportPlan() {
        val set = NotebookDraftReview.confirmSet(
            set = setDraft(),
            repetitions = 8,
            weight = 80.0,
            weightUnit = WeightUnit.KILOGRAM,
        )
        val exercise = NotebookDraftReview.confirmExercise(
            exercise = exerciseDraft(sets = listOf(set)),
            mode = ExerciseMode.BILATERAL,
            resolution = NotebookDraftReview.confirmExistingExerciseResolution(
                exerciseId = "bench",
                canonicalName = "Bench Press",
            ),
        )
        val workout = NotebookDraftReview.confirmWorkout(
            workout = workoutDraft(id = "workout-1", exercises = listOf(exercise)),
            startedAtEpochMillis = 1_000L,
            title = "Push",
        )
        val batch = NotebookDraftReview.confirmBatch(batch(workouts = listOf(workout)))

        val plan = NotebookCanonicalImportPlanner.buildPlan(batch)

        assertTrue(batch.canWriteCanonicalHistory)
        assertEquals("batch-1", plan.batchId)
        assertEquals("workout-1", plan.workouts.single().draftWorkoutId)
        assertEquals(1_000L, plan.workouts.single().startedAtEpochMillis)
        assertEquals("Push", plan.workouts.single().title)
        assertEquals("bench", plan.workouts.single().exercises.single().exerciseId)
        assertEquals("Bench Press", plan.workouts.single().exercises.single().canonicalName)
        assertEquals(80.0, plan.workouts.single().exercises.single().sets.single().weight ?: -1.0, 0.001)
        assertEquals(WeightUnit.KILOGRAM, plan.workouts.single().exercises.single().sets.single().weightUnit)
    }

    @Test
    fun confirmSetRejectsUnknownUnitForWeightedSet() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookDraftReview.confirmSet(
                set = setDraft(),
                repetitions = 8,
                weight = 80.0,
                weightUnit = WeightUnit.UNKNOWN,
            )
        }
    }

    @Test
    fun confirmExerciseRequiresConfirmedResolution() {
        val set = NotebookDraftReview.confirmSet(
            set = setDraft(),
            repetitions = 8,
            weight = 80.0,
            weightUnit = WeightUnit.KILOGRAM,
        )

        assertThrows(IllegalArgumentException::class.java) {
            NotebookDraftReview.confirmExercise(
                exercise = exerciseDraft(sets = listOf(set)),
                mode = ExerciseMode.BILATERAL,
                resolution = ExerciseResolution(
                    kind = ExerciseResolutionKind.MATCH_EXISTING,
                    exerciseId = "bench",
                    canonicalName = "Bench Press",
                    reviewState = ReviewState.NEEDS_REVIEW,
                ),
            )
        }
    }

    @Test
    fun importPlanRejectsUnconfirmedBatch() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookCanonicalImportPlanner.buildPlan(batch(workouts = listOf(confirmedWorkout("workout-1"))))
        }
    }

    @Test
    fun importPlanRejectsDuplicateCandidatesUntilResolved() {
        val batch = NotebookDraftReview.confirmBatch(
            batch(
                workouts = listOf(
                    confirmedWorkout("workout-1"),
                    confirmedWorkout("workout-2"),
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            NotebookCanonicalImportPlanner.buildPlan(batch)
        }
    }

    @Test
    fun importPlanRejectsUnconfirmedOptionalTitleEvenIfCoreReadinessPasses() {
        val workout = confirmedWorkout("workout-1").copy(
            title = recognized("Push"),
            reviewState = ReviewState.CONFIRMED,
        )
        val batch = NotebookDraftReview.confirmBatch(batch(workouts = listOf(workout)))

        assertTrue(batch.canWriteCanonicalHistory)
        assertThrows(IllegalArgumentException::class.java) {
            NotebookCanonicalImportPlanner.buildPlan(batch)
        }
    }

    @Test
    fun planCanRepresentNewExerciseCreationWithoutExistingExerciseId() {
        val set = NotebookDraftReview.confirmSet(
            set = setDraft(),
            repetitions = 10,
            weight = 12.0,
            weightUnit = WeightUnit.KILOGRAM,
        )
        val exercise = NotebookDraftReview.confirmExercise(
            exercise = exerciseDraft(name = "Machine Fly", sets = listOf(set)),
            mode = ExerciseMode.BILATERAL,
            resolution = NotebookDraftReview.confirmNewExerciseResolution("Machine Fly"),
        )
        val workout = NotebookDraftReview.confirmWorkout(
            workout = workoutDraft(id = "workout-1", exercises = listOf(exercise)),
            startedAtEpochMillis = 1_000L,
            title = "Push",
        )
        val batch = NotebookDraftReview.confirmBatch(batch(workouts = listOf(workout)))

        val plannedExercise = NotebookCanonicalImportPlanner.buildPlan(batch)
            .workouts.single()
            .exercises.single()

        assertEquals(ExerciseResolutionKind.CREATE_NEW, plannedExercise.resolutionKind)
        assertEquals(null, plannedExercise.exerciseId)
        assertEquals("Machine Fly", plannedExercise.canonicalName)
    }

    private fun confirmedWorkout(id: String): NotebookWorkoutDraft {
        val set = NotebookDraftReview.confirmSet(
            set = setDraft(),
            repetitions = 8,
            weight = 80.0,
            weightUnit = WeightUnit.KILOGRAM,
        )
        val exercise = NotebookDraftReview.confirmExercise(
            exercise = exerciseDraft(sets = listOf(set)),
            mode = ExerciseMode.BILATERAL,
            resolution = NotebookDraftReview.confirmExistingExerciseResolution(
                exerciseId = "bench",
                canonicalName = "Bench Press",
            ),
        )
        return NotebookDraftReview.confirmWorkout(
            workout = workoutDraft(id = id, exercises = listOf(exercise)),
            startedAtEpochMillis = 1_000L,
            title = "Push",
        )
    }

    private fun batch(workouts: List<NotebookWorkoutDraft>): NotebookImportBatchDraft = NotebookImportBatchDraft(
        id = "batch-1",
        pages = listOf(
            NotebookPageDraft(
                id = "page-1",
                position = 0,
                sourceFingerprintSha256 = "fingerprint-page-1",
            )
        ),
        workouts = workouts,
    )

    private fun workoutDraft(
        id: String,
        exercises: List<NotebookExerciseDraft>,
    ): NotebookWorkoutDraft = NotebookWorkoutDraft(
        id = id,
        sourcePageIds = setOf("page-1"),
        startedAtEpochMillis = recognized(1_000L),
        title = recognized("Push"),
        exercises = exercises,
    )

    private fun exerciseDraft(
        name: String = "Bench Press",
        sets: List<NotebookSetDraft>,
    ): NotebookExerciseDraft = NotebookExerciseDraft(
        id = "exercise-$name",
        position = 0,
        recognizedName = recognized(name),
        recognizedMode = RecognizedField(
            value = null,
            confidence = RecognitionConfidence(0.0),
            reviewState = ReviewState.NEEDS_REVIEW,
            provenance = provenance(),
        ),
        sets = sets,
    )

    private fun setDraft(): NotebookSetDraft = NotebookSetDraft(
        id = "set-1",
        position = 0,
        repetitions = recognized(8),
        weight = recognized(80.0),
        weightUnit = recognized(WeightUnit.KILOGRAM),
    )

    private fun provenance(): NotebookLineProvenance = NotebookLineProvenance(
        pageId = "page-1",
        lineNumber = 1,
    )

    private fun <T> recognized(value: T): RecognizedField<T> = RecognizedField(
        value = value,
        confidence = RecognitionConfidence(0.95),
        reviewState = ReviewState.NEEDS_REVIEW,
        provenance = provenance(),
    )
}
