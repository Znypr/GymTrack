package com.example.gymtrack.feature.editor

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class EditorSaveKind {
    DRAFT,
    FINALIZE,
}

internal sealed interface EditorSaveOutcome {
    data object Persisted : EditorSaveOutcome
    data object Superseded : EditorSaveOutcome
}

/**
 * Serializes editor writes and drops queued draft snapshots that have already
 * been replaced by a newer request. Finalization is never dropped.
 */
internal class EditorSaveCoordinator<T>(
    private val persistDraft: suspend (T) -> Unit,
    private val finalizeWorkout: suspend (T) -> Unit,
) {
    private val mutex = Mutex()
    private val latestRevision = AtomicLong(0L)

    fun reserveRevision(): Long = latestRevision.incrementAndGet()

    suspend fun persist(
        revision: Long,
        kind: EditorSaveKind,
        value: T,
    ): EditorSaveOutcome = mutex.withLock {
        if (kind == EditorSaveKind.DRAFT && revision < latestRevision.get()) {
            return@withLock EditorSaveOutcome.Superseded
        }

        when (kind) {
            EditorSaveKind.DRAFT -> persistDraft(value)
            EditorSaveKind.FINALIZE -> finalizeWorkout(value)
        }
        EditorSaveOutcome.Persisted
    }
}
