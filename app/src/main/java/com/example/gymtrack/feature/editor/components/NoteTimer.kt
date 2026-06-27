package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.gymtrack.core.timer.NoteTimerState
import com.example.gymtrack.core.timer.NoteTimerStore
import com.example.gymtrack.core.util.formatSecondsToMinutesSeconds

@Composable
fun NoteTimer(noteTimestamp: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val timerFlow = remember(context) { NoteTimerStore.observe(context) }
    val timerState by timerFlow.collectAsState(initial = NoteTimerState())

    LaunchedEffect(noteTimestamp) {
        NoteTimerStore.startOrRestore(context, noteTimestamp)
    }

    Row(modifier = modifier) {
        Text(formatSecondsToMinutesSeconds(timerState.elapsedSeconds(System.currentTimeMillis())))
    }
}
