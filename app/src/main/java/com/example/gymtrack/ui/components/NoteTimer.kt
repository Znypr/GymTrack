package com.example.gymtrack.ui.components


import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.gymtrack.timer.NoteTimerService
import com.example.gymtrack.util.formatSecondsToMinutesSeconds

@Composable
fun NoteTimer(noteTimestamp: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startTimerService(context, noteTimestamp)
        }
    }

    LaunchedEffect(noteTimestamp) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            startTimerService(context, noteTimestamp)
        } else {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

    }

    val elapsed by NoteTimerService.elapsedSeconds.collectAsState()
    val running by NoteTimerService.isRunning.collectAsState()

    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            formatSecondsToMinutesSeconds(elapsed),
            style = MaterialTheme.typography.titleLarge
        )
        IconButton(onClick = {
            val action = if (running) NoteTimerService.ACTION_PAUSE else NoteTimerService.ACTION_RESUME
            ContextCompat.startForegroundService(
                context,
                Intent(context, NoteTimerService::class.java).apply { this.action = action }
            )

        }) {
            Icon(
                imageVector = if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (running) "Pause" else "Resume"
            )
        }
        IconButton(onClick = {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NoteTimerService::class.java).apply {
                    action = NoteTimerService.ACTION_STOP
                }
            )

        }) {
            Icon(imageVector = Icons.Filled.Stop, contentDescription = "Stop")
        }
    }
}


private fun startTimerService(context: Context, noteTimestamp: Long) {
    ContextCompat.startForegroundService(
        context,
        Intent(context, NoteTimerService::class.java).apply {
            action = NoteTimerService.ACTION_START
            putExtra(NoteTimerService.EXTRA_NOTE, noteTimestamp)
        }
    )
}

