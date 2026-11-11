package com.example.gymtrack.feature.editor

/**
 * Whether the editor shows the running-workout controls (timer + Finish) for [noteId].
 *
 * - A new workout (`-1`) always does: every Start goes through the start sheet first
 *   (`decideWorkoutStart`), so a new workout never silently takes the timer from an unfinished one.
 * - An existing workout does when it owns the timer, unless it is already finished (a stale
 *   timer left on a finished workout must not offer a second Finish).
 * - Any other workout does not; an unfinished one is finished from History ("Mark finished").
 */
internal fun shouldShowActiveWorkoutControls(
    noteId: Long,
    activeNoteTimestamp: Long?,
    isFinished: Boolean = false,
): Boolean = noteId == -1L || (activeNoteTimestamp == noteId && !isFinished)
