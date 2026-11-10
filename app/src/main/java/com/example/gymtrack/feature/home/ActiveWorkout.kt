package com.example.gymtrack.feature.home

import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.timer.NoteTimerState
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * The running workout and how a new start treats it (#293). Pure logic, no Android imports
 * (README rule 9), so it is unit-tested on the JVM and reused by the long-horizon simulation.
 *
 * Rule: no workout may become un-finishable. Only one workout owns the timer. Starting a new
 * one while another is active-but-unfinished first asks "Finish Pull from Tue first?":
 *  - Finish it:     the old one is finished now (Finish time = now, docs/WORKOUT_DURATION.md).
 *  - Discard timer: the old one is finished without the timer's running time: it ends at its
 *                   last logged row.
 *  - Keep both:     the old one stays unfinished; History marks it "Unfinished" and offers
 *                   "Mark finished" at any time.
 */

/** What the start sheet offers for the workout that still owns the timer. */
enum class OrphanChoice(val label: String, val hint: String) {
    FINISH_IT("Finish it", "Counts it now, ends now"),
    DISCARD_TIMER("Discard timer", "Counts it, ends at its last set"),
    KEEP_BOTH("Keep both", "Stays unfinished; finish it later from History"),
}

/** What to do when the user taps Start (any Start: category, other, blank). */
sealed interface WorkoutStartDecision {
    /** No workout owns the timer: start right away. */
    data object StartNow : WorkoutStartDecision

    /**
     * The timer points at nothing worth asking about (a workout that is already finished, was
     * deleted, or has nothing logged): clear it without a question. [deleteEmptyNote] removes
     * a saved workout that has no text at all (Start tapped, then Back).
     */
    data class ClearStale(val activeTimestamp: Long, val deleteEmptyNote: Boolean) : WorkoutStartDecision

    /** A real unfinished workout owns the timer: ask with the start sheet first. */
    data class AskFirst(val active: NoteLine) : WorkoutStartDecision
}

fun decideWorkoutStart(
    activeTimestamp: Long?,
    activeNote: NoteLine?,
    activeIsFinished: Boolean,
): WorkoutStartDecision {
    if (activeTimestamp == null) return WorkoutStartDecision.StartNow
    if (activeNote == null || activeNote.timestamp != activeTimestamp) {
        return WorkoutStartDecision.ClearStale(activeTimestamp, deleteEmptyNote = false)
    }
    if (activeIsFinished) return WorkoutStartDecision.ClearStale(activeTimestamp, deleteEmptyNote = false)
    if (!activeNote.hasLoggedContent()) {
        return WorkoutStartDecision.ClearStale(
            activeTimestamp,
            deleteEmptyNote = activeNote.title.isBlank() && activeNote.learnings.isBlank(),
        )
    }
    return WorkoutStartDecision.AskFirst(activeNote)
}

/** True when the workout has any typed row (an exercise or a set). */
fun NoteLine.hasLoggedContent(): Boolean = text.isNotBlank()

/**
 * A workout History can finish with "Mark finished": not finished yet and something logged.
 * Every workout the start sheet leaves behind ("Keep both") satisfies this, so none is lost.
 */
fun canMarkFinished(note: NoteLine, isFinished: Boolean): Boolean = !isFinished && note.hasLoggedContent()

/** The short name of a workout in bars and prompts: its category, else its title, else "Workout". */
fun NoteLine.shortName(): String =
    categoryName?.trim()?.takeIf(String::isNotEmpty)
        ?: title.trim().takeIf(String::isNotEmpty)
        ?: "Workout"

/** "Finish Pull from Tue first?" — "today", "yesterday", a weekday within a week, else "3 Nov". */
fun orphanPrompt(
    note: NoteLine,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = "Finish ${note.shortName()} from ${dayReference(note.timestamp, nowMillis, zone, locale)} first?"

internal fun dayReference(timestamp: Long, nowMillis: Long, zone: ZoneId, locale: Locale): String {
    val day = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(day, today)
    return when {
        daysAgo == 0L -> "today"
        daysAgo == 1L -> "yesterday"
        daysAgo in 2L..6L -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        else -> "${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.SHORT, locale)}"
    }
}

/** What the active-workout bar shows, or null when no bar is shown. */
data class ActiveWorkoutInfo(
    val timestamp: Long,
    val note: NoteLine,
    val isRunning: Boolean,
)

/**
 * The bar shows while a workout owns the timer, is saved (so Resume can open it) and is not
 * finished. A timer left on a finished or deleted workout shows nothing.
 */
fun activeWorkoutInfo(
    timer: NoteTimerState,
    notes: List<NoteLine>,
    isFinished: (Long) -> Boolean,
): ActiveWorkoutInfo? {
    val timestamp = timer.activeNoteTimestamp ?: return null
    val note = notes.firstOrNull { it.timestamp == timestamp } ?: return null
    if (isFinished(timestamp)) return null
    return ActiveWorkoutInfo(timestamp, note, timer.isRunning)
}

/** Timer text for the bar: "24:10", "1:02:05". */
fun formatElapsedClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = (s % 60).toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$seconds" else "$minutes:$seconds"
}

/** A free timestamp for a new workout: [nowMillis], moved forward past any that is taken. */
fun freeWorkoutTimestamp(nowMillis: Long, taken: Set<Long>): Long {
    var candidate = nowMillis
    while (candidate in taken) candidate++
    return candidate
}
