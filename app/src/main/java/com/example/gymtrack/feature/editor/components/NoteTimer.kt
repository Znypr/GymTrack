package com.example.gymtrack.feature.editor.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.gymtrack.core.time.AppClock
import com.example.gymtrack.core.timer.NoteTimerState
import com.example.gymtrack.core.timer.NoteTimerStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val NOTE_TIMER_ELAPSED_TEST_TAG = "note-timer-elapsed"

/** The workout timer as the editor shows it: elapsed in the top line, pause/resume in the ⋯ menu. */
@Stable
class WorkoutTimerUi internal constructor(
    private val noteTimestamp: Long,
    private val timerState: State<NoteTimerState>,
    private val now: State<Long>,
    val togglePause: () -> Unit,
) {
    /** Read only where it is drawn, so the per-second tick recomposes just the top line. */
    val elapsedSeconds: Long
        get() = if (timerState.value.activeNoteTimestamp == noteTimestamp) timerState.value.elapsedSeconds(now.value) else 0L

    val isRunning: Boolean
        get() = timerState.value.activeNoteTimestamp == noteTimestamp && timerState.value.isRunning
}

/** Top-line elapsed format: `24:10`, or `1:05:10` past an hour. */
internal fun formatTopLineElapsed(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = (s % 60).toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$seconds" else "$minutes:$seconds"
}

/**
 * Observes the persisted workout timer (docs/TIMER.md). A new workout starts it on open; Back
 * keeps it running; Finish stops it. Redraws once per second only while running.
 */
@Composable
fun rememberWorkoutTimer(noteTimestamp: Long, startOnOpen: Boolean): WorkoutTimerUi {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val states = remember(context) { NoteTimerStore.observe(context) }
    val timerState = states.collectAsState(initial = NoteTimerState())
    val nowState = remember { mutableLongStateOf(AppClock.nowMillis()) }
    val running by remember { derivedStateOf { timerState.value.isRunning } }
    val startedAt by remember { derivedStateOf { timerState.value.startedAtEpochMillis } }

    LaunchedEffect(noteTimestamp, startOnOpen) {
        if (startOnOpen) NoteTimerStore.startOrRestore(context, noteTimestamp)
    }
    LaunchedEffect(running, startedAt) {
        nowState.longValue = AppClock.nowMillis()
        while (running) {
            delay(1_000L)
            nowState.longValue = AppClock.nowMillis()
        }
    }

    return remember(noteTimestamp, context) {
        lateinit var ui: WorkoutTimerUi
        ui = WorkoutTimerUi(
            noteTimestamp = noteTimestamp,
            timerState = timerState,
            now = nowState,
            togglePause = {
                val pause = ui.isRunning
                scope.launch {
                    if (pause) NoteTimerStore.pause(context, noteTimestamp)
                    else NoteTimerStore.resume(context, noteTimestamp)
                }
            },
        )
        ui
    }
}
