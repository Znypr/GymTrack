package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.gymtrack.core.timer.NoteTimerState
import com.example.gymtrack.core.timer.NoteTimerStore
import com.example.gymtrack.core.util.formatSecondsToMinutesSeconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NoteTimer(noteTimestamp: Long, startOnOpen: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val states = remember(context) { NoteTimerStore.observe(context) }
    val state by states.collectAsState(initial = NoteTimerState())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(noteTimestamp, startOnOpen) {
        if (startOnOpen) NoteTimerStore.startOrRestore(context, noteTimestamp)
    }
    LaunchedEffect(state.isRunning, state.startedAtEpochMillis) {
        now = System.currentTimeMillis()
        while (state.isRunning) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }

    val active = state.activeNoteTimestamp == noteTimestamp
    val running = active && state.isRunning
    val elapsed = if (active) state.elapsedSeconds(now) else 0L

    Row(modifier = modifier) {
        Text(formatSecondsToMinutesSeconds(elapsed))
        IconButton(onClick = {
            scope.launch {
                if (running) NoteTimerStore.pause(context, noteTimestamp)
                else NoteTimerStore.resume(context, noteTimestamp)
            }
        }) {
            Icon(
                if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (running) "Pause timer" else "Start timer",
            )
        }
        IconButton(onClick = {
            scope.launch { NoteTimerStore.stop(context, noteTimestamp) }
        }) {
            Icon(Icons.Filled.Stop, "Stop timer")
        }
    }
}
