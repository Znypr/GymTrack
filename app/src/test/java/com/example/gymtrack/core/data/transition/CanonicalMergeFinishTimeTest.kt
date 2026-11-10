package com.example.gymtrack.core.data.transition

import com.example.gymtrack.core.data.CanonicalWorkoutEntity
import com.example.gymtrack.domain.model.WorkoutStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** #323/#325: re-deriving a finished workout (edit or startup import) keeps its Finish time. */
class CanonicalMergeFinishTimeTest {
    private val projected = CanonicalWorkoutEntity(
        id = "w",
        legacyTimestamp = 1_000L,
        startedAt = 1_000L,
        endedAt = 50_000L,
        categoryId = null,
        title = "",
        learnings = "",
        status = WorkoutStatus.PARTIAL.name,
        rawDraftText = "",
        legacyMigrationStatus = "MIGRATED",
        legacyMigrationMessage = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    @Test
    fun completedRow_keepsItsEndedAt() {
        val completed = projected.copy(status = WorkoutStatus.COMPLETED.name, endedAt = 90_000L, updatedAt = 95_000L)
        val merged = CanonicalImportSource.merge(completed, projected.copy(updatedAt = 120_000L))
        assertEquals(WorkoutStatus.COMPLETED.name, merged.status)
        assertEquals(90_000L, merged.endedAt)
        assertEquals(120_000L, merged.updatedAt)
    }

    @Test
    fun partialRow_takesTheNewProjectionEnd() {
        val partial = projected.copy(endedAt = 10_000L)
        assertEquals(50_000L, CanonicalImportSource.merge(partial, projected).endedAt)
    }

    @Test
    fun completedRowWithoutEnd_takesTheProjectionEnd() {
        val completed = projected.copy(status = WorkoutStatus.COMPLETED.name, endedAt = null)
        assertEquals(50_000L, CanonicalImportSource.merge(completed, projected).endedAt)
    }
}
