package com.example.gymtrack.feature.editor.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.gymtrack.core.time.AppClock
import com.example.gymtrack.feature.editor.NoteEditorState
import com.example.gymtrack.feature.editor.RestCountdown
import kotlinx.coroutines.delay

/** What the accessory bar needs from the rest countdown (#349). */
@Stable
class RestTimerUi internal constructor(
    enabled: Boolean,
    remainingSeconds: Int,
    usualSeconds: Int?,
    overrideSeconds: Int?,
    private val onEnabledChange: (Boolean) -> Unit,
    private val onOverrideChange: (Int?) -> Unit,
    private val onSkip: () -> Unit,
) {
    var enabled by mutableStateOf(enabled)
        internal set
    /** Seconds left of the running rest; 0 when no rest runs. */
    var remainingSeconds by mutableIntStateOf(remainingSeconds)
        internal set
    /** The current exercise's usual rest from history, or null. */
    var usualSeconds by mutableStateOf(usualSeconds)
        internal set
    /** A length picked on the menu; null = the usual rest (or 2:00). */
    var overrideSeconds by mutableStateOf(overrideSeconds)
        internal set

    val isRunning: Boolean get() = remainingSeconds > 0

    fun setEnabled(value: Boolean) = onEnabledChange(value)
    fun choose(seconds: Int?) = onOverrideChange(seconds)
    fun skip() {
        onSkip()
        remainingSeconds = 0
    }
}

/**
 * The optional rest countdown after each set (#349): off until the user turns it on from the bar,
 * then it restarts on every committed set. Runs in the editor's composition (no service, no
 * notification); ticks once a second only while a rest runs; a quiet double buzz at zero, no sound.
 */
@Composable
fun rememberRestTimer(state: NoteEditorState, active: Boolean): RestTimerUi? {
    if (!active) return null
    val context = LocalContext.current
    var enabled by rememberSaveable { mutableStateOf(false) }
    var overrideSeconds by rememberSaveable { mutableStateOf<Int?>(null) }
    // The set whose rest is running (or was skipped): enabling never starts a rest for an old set.
    var startedFor by rememberSaveable { mutableIntStateOf(state.lastSetCommit?.sequence ?: 0) }
    val countdown = remember { RestCountdown() }
    val commit = state.lastSetCommit
    val restUi = remember {
        RestTimerUi(
            enabled = enabled,
            remainingSeconds = 0,
            usualSeconds = null,
            overrideSeconds = overrideSeconds,
            onEnabledChange = { value ->
                enabled = value
                startedFor = state.lastSetCommit?.sequence ?: 0
                if (!value) countdown.stop()
            },
            onOverrideChange = { overrideSeconds = it },
            onSkip = { countdown.stop() },
        )
    }
    restUi.enabled = enabled
    restUi.overrideSeconds = overrideSeconds
    restUi.usualSeconds = commit?.let { state.usualRestSeconds(it.exerciseName, it.flag) }

    LaunchedEffect(commit, enabled) {
        if (!enabled || commit == null) {
            countdown.stop()
            restUi.remainingSeconds = 0
            return@LaunchedEffect
        }
        if (commit.sequence != startedFor) {
            startedFor = commit.sequence
            countdown.start(AppClock.nowMillis(), state.usualRestSeconds(commit.exerciseName, commit.flag), overrideSeconds)
        }
        while (true) {
            val now = AppClock.nowMillis()
            if (countdown.consumeZero(now)) {
                restUi.remainingSeconds = 0
                vibrateQuietly(context)
                break
            }
            val end = countdown.endsAtMillis
            if (end == null) {
                restUi.remainingSeconds = 0
                break
            }
            restUi.remainingSeconds = countdown.remainingSeconds(now)
            // Wake at the next whole-second boundary (or when a skip clears the end time).
            val untilTick = ((end - now) % 1_000L).let { if (it <= 0L) 1_000L else it }
            delay(minOf(untilTick, 250L).coerceAtLeast(50L))
        }
    }
    return restUi
}

/** A short, low double buzz: noticeable in a pocket, silent in the room. */
private fun vibrateQuietly(context: Context) {
    val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()
    if (vibrator == null || !vibrator.hasVibrator()) return
    runCatching {
        val timings = longArrayOf(0L, 90L, 110L, 90L)
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, intArrayOf(0, 90, 0, 90), -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        vibrator.vibrate(effect)
    }
}
