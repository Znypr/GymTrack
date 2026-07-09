package com.example.gymtrack.domain.notebookimport

import com.example.gymtrack.domain.model.Exercise
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookRepresentativeFixturesTest {

    @Test
    fun cleanFixturePassesExpectedCounts() {
        val result = runFixture(NotebookRepresentativeFixtures.cleanPushDay)

        assertTrue(result.metricsReport?.passes == true)
        assertFalse(result.draftDuplicateReport.hasCandidates)
    }

    @Test
    fun missingDateFixtureStillPassesCountsButRequiresDateReview() {
        val result = runFixture(NotebookRepresentativeFixtures.missingDatePullDay)

        assertTrue(result.metricsReport?.workoutCountMatches == true)
        assertTrue(result.metricsReport?.exerciseCountMatches == true)
        assertTrue(result.metricsReport?.setCountMatches == true)
        assertTrue(result.reviewQueue.items.any { it.kind == NotebookReviewItemKind.WORKOUT_DATE })
    }

    @Test
    fun ambiguousValuesFixturePassesCountsAndRequiresSetReview() {
        val result = runFixture(NotebookRepresentativeFixtures.ambiguousLegDay)

        assertTrue(result.metricsReport?.workoutCountMatches == true)
        assertTrue(result.metricsReport?.exerciseCountMatches == true)
        assertTrue(result.metricsReport?.setCountMatches == true)
        assertTrue(result.reviewQueue.items.any { it.kind == NotebookReviewItemKind.SET_REPETITIONS })
        assertTrue(result.reviewQueue.items.any { it.kind == NotebookReviewItemKind.SET_WEIGHT })
    }

    private fun runFixture(
        fixture: NotebookRepresentativeFixture,
    ): NotebookImportDomainPipelineResult = NotebookImportDomainPipeline.run(
        request = NotebookRecognitionRequest(batch = fixture.batch),
        provider = FixtureNotebookRecognitionProvider(linesByPageId = fixture.linesByPageId),
        exerciseCatalog = fixture.exerciseCatalog,
        fixtureExpectation = fixture.expectation,
    )
}

data class NotebookRepresentativeFixture(
    val name: String,
    val batch: NotebookImportBatchDraft,
    val linesByPageId: Map<String, List<String>>,
    val exerciseCatalog: List<Exercise>,
    val expectation: NotebookFixtureExpectation,
)

object NotebookRepresentativeFixtures {
    val cleanPushDay = NotebookRepresentativeFixture(
        name = "clean-push-day",
        batch = batch("clean-push-day"),
        linesByPageId = mapOf(
            "page-1" to listOf(
                "2026-07-09 Push",
                "Bench Press 80 kg x 8",
                "Bench Press 82.5 kg x 6",
                "Curl 12 kg x 10",
            )
        ),
        exerciseCatalog = listOf(
            Exercise(id = "bench", canonicalName = "Bench Press"),
            Exercise(id = "curl", canonicalName = "Curl"),
        ),
        expectation = NotebookFixtureExpectation(
            workoutCount = 1,
            exerciseCount = 2,
            setCount = 3,
            unresolvedFieldCountMaximum = 30,
        ),
    )

    val missingDatePullDay = NotebookRepresentativeFixture(
        name = "missing-date-pull-day",
        batch = batch("missing-date-pull-day"),
        linesByPageId = mapOf(
            "page-1" to listOf(
                "Row 70 kg x 10",
                "Lat Pulldown 60 kg x 12",
            )
        ),
        exerciseCatalog = listOf(
            Exercise(id = "row", canonicalName = "Row"),
            Exercise(id = "lat-pulldown", canonicalName = "Lat Pulldown"),
        ),
        expectation = NotebookFixtureExpectation(
            workoutCount = 1,
            exerciseCount = 2,
            setCount = 2,
            unresolvedFieldCountMaximum = 30,
        ),
    )

    val ambiguousLegDay = NotebookRepresentativeFixture(
        name = "ambiguous-leg-day",
        batch = batch("ambiguous-leg-day"),
        linesByPageId = mapOf(
            "page-1" to listOf(
                "2026-07-10 Legs",
                "Squat ? kg x 8",
                "Leg Press 160 kg x ?",
            )
        ),
        exerciseCatalog = listOf(
            Exercise(id = "squat", canonicalName = "Squat"),
            Exercise(id = "leg-press", canonicalName = "Leg Press"),
        ),
        expectation = NotebookFixtureExpectation(
            workoutCount = 1,
            exerciseCount = 2,
            setCount = 2,
            unresolvedFieldCountMaximum = 40,
        ),
    )

    private fun batch(id: String): NotebookImportBatchDraft = NotebookImportBatchDraft(
        id = id,
        pages = listOf(
            NotebookPageDraft(
                id = "page-1",
                position = 0,
                sourceFingerprintSha256 = "fingerprint-$id-page-1",
            )
        ),
    )
}
