package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.timer.NoteTimerState
import com.example.gymtrack.core.timer.NoteTimerStore
import com.example.gymtrack.core.util.formatSecondsToMinutesSeconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NoteTimer(noteTimestamp: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timerFlow = remember(context) { NoteTimerStore.observe(context) }
    val timerState by timerFlow.collectAsState(initial = NoteTimerState())
    var nowEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(noteTimestamp) {
        NoteTimerStore.startOrRestore(context, noteTimestamp)
    }

    LaunchedEffect(timerState.isRunning, timerState.startedAtEpochMillis) {
        nowEpochMillis = System.currentTimeMillis()
        while (timerState.isRunning) {
            delay(1_000L)
            nowEpochMillis = System.currentTimeMillis()
        }
    }

    val isActiveNote = timerState.activeNoteTimestamp == noteTimestamp
    val running = isActiveNote && timerState.isRunning
    val elapsed = if (isActiveNote) timerState.elapsedSeconds(nowEpochMillis) else 0L

    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatSecondsToMinutesSeconds(elapsed),
            style = MaterialTheme.typography.titleLarge,
        )
        IconButton(
            onClick = {
                scope.launch {
                    if (running) {
                        NoteTimerStore.pause(context, noteTimestamp)
                    } else {
                        NoteTimerStore.resume(context, noteTimestamp)
                    }
                }
            },
        ) {
            Icon(
                imageVector = if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (running) "Pause timer" else "Start timer",
            )
        }
        IconButton(
            onClick = {
                scope.launch {
                    NoteTimerStore.stop(context, noteTimestamp)
                }
            },
        ) {
            Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = "Stop timer",
            )
        }
    }
}
