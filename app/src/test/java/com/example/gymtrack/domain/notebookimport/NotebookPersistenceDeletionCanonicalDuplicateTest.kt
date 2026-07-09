package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.ExerciseMode
import com.example.gymtrack.domain.model.WeightUnit
import com.example.gymtrack.domain.model.Workout
import com.example.gymtrack.domain.model.WorkoutExercise
import com.example.gymtrack.domain.model.WorkoutRecord
import com.example.gymtrack.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookPersistenceDeletionCanonicalDuplicateTest {

    @Test
    fun canonicalDuplicateDetectorFlagsExactExistingWorkout() {
        val report = NotebookCanonicalDuplicateDetector.detectAgainstExistingHistory(
            plan = importPlan(),
            existingHistory = listOf(existingWorkout(startedAt = 1_000L, title = "Push")),
        )

        assertTrue(report.hasCandidates)
        assertEquals(
            NotebookCanonicalDuplicateSeverity.EXACT_CANONICAL_DUPLICATE,
            report.candidates.single().severity,
        )
        assertEquals("workout-1", report.candidates.single().existingWorkoutId)
    }

    @Test
    fun canonicalDuplicateDetectorFlagsSameStartAsPossibleDuplicate() {
        val report = NotebookCanonicalDuplicateDetector.detectAgainstExistingHistory(
            plan = importPlan(),
            existingHistory = listOf(existingWorkout(startedAt = 1_000L, title = "Pull")),
        )

        assertEquals(
            NotebookCanonicalDuplicateSeverity.POSSIBLE_SAME_START,
            report.candidates.single().severity,
        )
    }

    @Test
    fun canonicalDuplicateDetectorAllowsDifferentWorkout() {
        val report = NotebookCanonicalDuplicateDetector.detectAgainstExistingHistory(
            plan = importPlan(),
            existingHistory = listOf(existingWorkout(startedAt = 2_000L, title = "Pull")),
        )

        assertFalse(report.hasCandidates)
    }

    @Test
    fun preflightRejectsCanonicalDuplicateCandidates() {
        val duplicateReport = NotebookCanonicalDuplicateReport(
            candidates = listOf(
                NotebookCanonicalDuplicateCandidate(
                    plannedWorkoutDraftId = "draft-workout-1",
                    existingWorkoutId = "workout-1",
                    severity = NotebookCanonicalDuplicateSeverity.EXACT_CANONICAL_DUPLICATE,
                    reason = "Same workout",
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            NotebookCanonicalImportPreflight.validate(
                plan = importPlan(),
                canonicalDuplicateReport = duplicateReport,
            )
        }
    }

    @Test
    fun preflightPassesWhenNoDuplicatesRemain() {
        val report = NotebookCanonicalImportPreflight.validate(plan = importPlan())

        assertTrue(report.canCommit)
    }

    @Test
    fun deletionPlanDeletesIntermediateDataWithoutCanonicalWorkouts() {
        val plan = NotebookImportDeletionPlanner.planDeletion(
            state = state(status = NotebookImportBatchStatus.AWAITING_REVIEW),
            target = NotebookImportDeletionTarget.INTERMEDIATE_IMPORT_DATA,
        )

        assertEquals("batch-1", plan.batchId)
        assertTrue(NotebookImportDataKind.EXTRACTED_TEXT in plan.dataKinds)
        assertTrue(NotebookImportDataKind.CONFIDENCE_DATA in plan.dataKinds)
        assertTrue(NotebookImportDataKind.DRAFT_WORKOUTS in plan.dataKinds)
        assertFalse(NotebookImportDataKind.CANONICAL_WORKOUTS in plan.dataKinds)
        assertTrue(plan.pageIds.isEmpty())
    }

    @Test
    fun sourceImageDeletionRequiresEligibleBatchState() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookImportDeletionPlanner.planDeletion(
                state = state(status = NotebookImportBatchStatus.AWAITING_REVIEW),
                target = NotebookImportDeletionTarget.SOURCE_IMAGES_ONLY,
            )
        }

        val importedPlan = NotebookImportDeletionPlanner.planDeletion(
            state = state(status = NotebookImportBatchStatus.IMPORTED),
            target = NotebookImportDeletionTarget.SOURCE_IMAGES_ONLY,
        )

        assertEquals(setOf("page-1"), importedPlan.pageIds)
        assertEquals(setOf(NotebookImportDataKind.SOURCE_IMAGE), importedPlan.dataKinds)
    }

    @Test
    fun keepUntilUserDeletesAllowsSourceImageDeletionBeforeImport() {
        val plan = NotebookImportDeletionPlanner.planDeletion(
            state = state(
                status = NotebookImportBatchStatus.AWAITING_REVIEW,
                retentionPolicy = SourceImageRetentionPolicy.KEEP_UNTIL_USER_DELETES,
            ),
            target = NotebookImportDeletionTarget.SOURCE_IMAGES_ONLY,
        )

        assertEquals(setOf("page-1"), plan.pageIds)
    }

    @Test
    fun deletionPlanRejectsCanonicalWorkoutDataKind() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookImportDeletionPlan(
                batchId = "batch-1",
                target = NotebookImportDeletionTarget.INTERMEDIATE_IMPORT_DATA,
                dataKinds = setOf(NotebookImportDataKind.CANONICAL_WORKOUTS),
            )
        }
    }

    @Test
    fun canonicalImportResultRequiresUniqueImportedWorkoutIds() {
        assertThrows(IllegalArgumentException::class.java) {
            NotebookCanonicalImportResult(
                batchId = "batch-1",
                importedWorkoutIds = listOf("workout-1", "workout-1"),
            )
        }
    }

    private fun importPlan(): NotebookCanonicalImportPlan = NotebookCanonicalImportPlan(
        batchId = "batch-1",
        workouts = listOf(
            NotebookPlannedWorkout(
                draftWorkoutId = "draft-workout-1",
                sourcePageIds = setOf("page-1"),
                startedAtEpochMillis = 1_000L,
                title = "Push",
                exercises = listOf(
                    NotebookPlannedExercise(
                        draftExerciseId = "draft-exercise-1",
                        position = 0,
                        mode = ExerciseMode.BILATERAL,
                        resolutionKind = ExerciseResolutionKind.MATCH_EXISTING,
                        exerciseId = "bench",
                        canonicalName = "Bench Press",
                        sets = listOf(
                            NotebookPlannedSet(
                                draftSetId = "draft-set-1",
                                position = 0,
                                repetitions = 8,
                                weight = 80.0,
                                weightUnit = WeightUnit.KILOGRAM,
                            )
                        ),
                    )
                ),
            )
        ),
    )

    private fun existingWorkout(
        startedAt: Long,
        title: String,
    ): WorkoutRecord = WorkoutRecord(
        workout = Workout(
            id = "workout-1",
            startedAtEpochMillis = startedAt,
            title = title,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        ),
        exercises = listOf(
            WorkoutExercise(
                id = "workout-exercise-1",
                workoutId = "workout-1",
                exerciseId = "bench",
                position = 0,
                mode = ExerciseMode.BILATERAL,
            )
        ),
        sets = listOf(
            WorkoutSet(
                id = "set-1",
                workoutExerciseId = "workout-exercise-1",
                position = 0,
                repetitions = 8,
                weight = 80.0,
                weightUnit = WeightUnit.KILOGRAM,
            )
        ),
    )

    private fun state(
        status: NotebookImportBatchStatus,
        retentionPolicy: SourceImageRetentionPolicy = SourceImageRetentionPolicy.DELETE_AFTER_CONFIRMATION,
    ): NotebookImportBatchState = NotebookImportBatchState(
        batch = NotebookImportBatchDraft(
            id = "batch-1",
            pages = listOf(
                NotebookPageDraft(
                    id = "page-1",
                    position = 0,
                    sourceFingerprintSha256 = "fingerprint-page-1",
                )
            ),
            consent = NotebookImportConsent(sourceImageRetentionPolicy = retentionPolicy),
        ),
        pageStates = listOf(
            NotebookPageProcessingState(
                pageId = "page-1",
                status = NotebookPageProcessingStatus.PROCESSED,
                updatedAtEpochMillis = 1L,
            )
        ),
        status = status,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )
}
