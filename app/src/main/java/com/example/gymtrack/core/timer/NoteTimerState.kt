package com.example.gymtrack.core.timer

data class NoteTimerState(
    val activeNoteTimestamp: Long? = null,
    val accumulatedSeconds: Long = 0L,
    val startedAtEpochMillis: Long? = null,
    val isRunning: Boolean = false,
) {
    fun elapsedSeconds(nowEpochMillis: Long): Long {
        val base = accumulatedSeconds.coerceAtLeast(0L)
        val startedAt = startedAtEpochMillis
        if (!isRunning || startedAt == null) return base

        val runningSeconds = ((nowEpochMillis - startedAt).coerceAtLeast(0L)) / 1_000L
        return base + runningSeconds
    }

    fun pauseAt(nowEpochMillis: Long): NoteTimerState {
        if (!isRunning) return this
        return copy(
            accumulatedSeconds = elapsedSeconds(nowEpochMillis),
            startedAtEpochMillis = null,
            isRunning = false,
        )
    }

    fun resumeAt(noteTimestamp: Long, nowEpochMillis: Long): NoteTimerState {
        if (activeNoteTimestamp == noteTimestamp && isRunning) return this
        if (activeNoteTimestamp != noteTimestamp) return started(noteTimestamp, nowEpochMillis)

        return copy(
            startedAtEpochMillis = nowEpochMillis,
            isRunning = true,
        )
    }

    companion object {
        fun started(noteTimestamp: Long, nowEpochMillis: Long): NoteTimerState = NoteTimerState(
            activeNoteTimestamp = noteTimestamp,
            accumulatedSeconds = 0L,
            startedAtEpochMillis = nowEpochMillis,
            isRunning = true,
        )
    }
}
