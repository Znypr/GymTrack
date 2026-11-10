package com.example.gymtrack.feature.home

import com.example.gymtrack.core.data.NoteLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Workouts the user deleted that can still be restored with Undo. */
data class PendingWorkoutDelete(
    val id: Long,
    val notes: Set<NoteLine>,
) {
    val timestamps: Set<Long> = notes.mapTo(mutableSetOf()) { it.timestamp }
    val count: Int get() = notes.size
}

/**
 * Deferred delete with Undo (#292).
 *
 * A delete request only hides the workouts; nothing is removed from the database until
 * [commit] runs (Undo snackbar dismissed, a new delete, an import, or the screen closing).
 * [undo] therefore restores every row exactly — compatibility note, derived sets, canonical
 * workout and outbox entry — because none of them were touched. If the app dies during the
 * undo window the workouts are kept: the safe failure for "never silently lose data".
 */
class PendingWorkoutDeletion(
    private val scope: CoroutineScope,
    private val delete: suspend (Set<NoteLine>) -> Unit,
    private val onDeleteError: (Throwable) -> Unit = {},
) {
    private val lock = Any()
    private var nextId = 1L
    private var lastCommit: Job? = null

    private val _pending = MutableStateFlow<PendingWorkoutDelete?>(null)
    val pending: StateFlow<PendingWorkoutDelete?> = _pending.asStateFlow()

    /** Timestamps being deleted right now; kept hidden so rows do not flash back. */
    private val committing = MutableStateFlow<Set<Long>>(emptySet())

    /** Timestamps the Home list must not show. */
    val hiddenTimestamps: Flow<Set<Long>> = combine(_pending, committing) { pending, inFlight ->
        if (pending == null) inFlight else inFlight + pending.timestamps
    }

    /** Hides [notes] and makes them undoable. Any earlier pending delete is committed first. */
    fun request(notes: Set<NoteLine>) {
        if (notes.isEmpty()) return
        synchronized(lock) {
            commitLocked()
            _pending.value = PendingWorkoutDelete(nextId++, notes)
        }
    }

    /** Restores the pending workouts. Returns false if there was nothing left to undo. */
    fun undo(): Boolean = synchronized(lock) {
        val hadPending = _pending.value != null
        _pending.value = null
        hadPending
    }

    /** Deletes the pending workouts now. The returned job completes when all deletes finished. */
    fun commit(): Job = synchronized(lock) { commitLocked() }

    private fun commitLocked(): Job {
        val pending = _pending.value ?: return lastCommit ?: CompletableDeferred(Unit)
        // Mark in-flight before clearing pending so the rows never become visible in between.
        committing.update { it + pending.timestamps }
        _pending.value = null
        val previous = lastCommit
        val job = scope.launch {
            previous?.join()
            try {
                delete(pending.notes)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Deletes run in one transaction per workout; failed ones stay in the
                // database and become visible again below.
                onDeleteError(error)
            } finally {
                committing.update { it - pending.timestamps }
            }
        }
        lastCommit = job
        return job
    }
}

/** Selecting every workout and deleting must always be confirmed (#292). */
fun deleteNeedsConfirmation(selected: Set<NoteLine>, allNotes: List<NoteLine>): Boolean =
    selected.isNotEmpty() && allNotes.isNotEmpty() && allNotes.all { it in selected }

fun deletedWorkoutsMessage(count: Int): String =
    if (count == 1) "Workout deleted" else "$count workouts deleted"
