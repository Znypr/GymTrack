package com.example.gymtrack.core.timer

data class NoteTimerState(
    val activeNoteTimestamp: Long? = null,
    val accumulatedSeconds: Long = 0L,
    val startedAtEpochMillis: Long? = null,
    val isRunning: Boolean = false,
)
