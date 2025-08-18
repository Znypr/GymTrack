package com.example.gymtrack.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.gymtrack.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

class NoteTimerService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var startTime = 0L
    private var elapsedBeforePause = 0L
    private var job = null as kotlinx.coroutines.Job?

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                activeNoteTimestamp = intent.getLongExtra(EXTRA_NOTE, 0L)
                elapsedBeforePause = 0L
                startTimer()
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> {
                stopTimer()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startTimer() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0))
        startTime = System.currentTimeMillis()
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val elapsed = elapsedBeforePause + ((System.currentTimeMillis() - startTime) / 1000)
                elapsedSeconds.value = elapsed
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(elapsed))
                delay(1000)
            }
        }
        isRunning.value = true
    }

    private fun pause() {
        if (job == null) return
        job?.cancel()
        elapsedBeforePause = elapsedSeconds.value
        isRunning.value = false
    }

    private fun resume() {
        startTime = System.currentTimeMillis()
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val elapsed = elapsedBeforePause + ((System.currentTimeMillis() - startTime) / 1000)
                elapsedSeconds.value = elapsed
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(elapsed))
                delay(1000)
            }
        }
        isRunning.value = true
    }

    private fun stopTimer() {
        job?.cancel()
        isRunning.value = false
        elapsedBeforePause = 0L
        elapsedSeconds.value = 0L
        activeNoteTimestamp = null
        stopForeground(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun buildNotification(elapsed: Long): Notification {
        val minutes = elapsed / 60
        val seconds = elapsed % 60
        val text = String.format("%02d:%02d", minutes, seconds)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Note Timer")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_gymtrack_logo)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Note Timer",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.example.gymtrack.timer.START"
        const val ACTION_PAUSE = "com.example.gymtrack.timer.PAUSE"
        const val ACTION_RESUME = "com.example.gymtrack.timer.RESUME"
        const val ACTION_STOP = "com.example.gymtrack.timer.STOP"
        const val EXTRA_NOTE = "extra_note_timestamp"
        val elapsedSeconds = MutableStateFlow(0L)
        val isRunning = MutableStateFlow(false)
        var activeNoteTimestamp: Long? = null
        private const val CHANNEL_ID = "note_timer"
        private const val NOTIFICATION_ID = 42
    }
}

