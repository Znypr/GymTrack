package com.example.gymtrack.feature.editor

import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EditorSaveCoordinatorTest {
    @Test
    fun supersededQueuedDraftIsNotWritten() = runBlocking {
        val drafts = mutableListOf<String>()
        val coordinator = EditorSaveCoordinator<String>(
            persistDraft = { value -> drafts += value },
            finalizeWorkout = {},
        )

        val oldRevision = coordinator.reserveRevision()
        val latestRevision = coordinator.reserveRevision()

        val oldOutcome = coordinator.persist(
            revision = oldRevision,
            kind = EditorSaveKind.DRAFT,
            value = "old",
        )
        val latestOutcome = coordinator.persist(
            revision = latestRevision,
            kind = EditorSaveKind.DRAFT,
            value = "latest",
        )

        assertSame(EditorSaveOutcome.Superseded, oldOutcome)
        assertSame(EditorSaveOutcome.Persisted, latestOutcome)
        assertEquals(listOf("latest"), drafts)
    }

    @Test
    fun inFlightDraftFinishesBeforeNewerDraft() = runBlocking {
        val writes = Collections.synchronizedList(mutableListOf<String>())
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val coordinator = EditorSaveCoordinator<String>(
            persistDraft = { value ->
                if (value == "first") {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                writes += value
            },
            finalizeWorkout = {},
        )

        val firstRevision = coordinator.reserveRevision()
        val first = async(Dispatchers.Default) {
            coordinator.persist(firstRevision, EditorSaveKind.DRAFT, "first")
        }

        firstStarted.await()
        val secondRevision = coordinator.reserveRevision()
        val second = async(Dispatchers.Default) {
            coordinator.persist(secondRevision, EditorSaveKind.DRAFT, "second")
        }

        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf("first", "second"), writes)
    }

    @Test
    fun finalizationUsesCompletionWriterAndIsNeverDropped() = runBlocking {
        val drafts = mutableListOf<String>()
        val completed = mutableListOf<String>()
        val coordinator = EditorSaveCoordinator<String>(
            persistDraft = { value -> drafts += value },
            finalizeWorkout = { value -> completed += value },
        )

        val finalizationRevision = coordinator.reserveRevision()
        coordinator.reserveRevision()

        val outcome = coordinator.persist(
            revision = finalizationRevision,
            kind = EditorSaveKind.FINALIZE,
            value = "workout",
        )

        assertSame(EditorSaveOutcome.Persisted, outcome)
        assertEquals(emptyList<String>(), drafts)
        assertEquals(listOf("workout"), completed)
    }
}
