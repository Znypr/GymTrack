package com.example.gymtrack.feature.editor

internal fun shouldShowActiveWorkoutControls(
    noteId: Long,
    activeNoteTimestamp: Long?,
): Boolean = noteId == -1L || activeNoteTimestamp == noteId
