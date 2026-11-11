package com.example.gymtrack.feature.editor

/** Rest after a set when nothing better is known (#349). */
const val DEFAULT_REST_SECONDS = 120

/** Rest lengths the accessory-bar chip offers, besides the exercise's usual rest. */
val REST_CHOICES_SECONDS = listOf(60, 90, 120, 150, 180)

/**
 * The optional rest countdown (#349). Pure: it holds an end time and answers from a clock, so it
 * needs no service and survives recomposition; the UI ticks it once a second only while it runs.
 */
class RestCountdown {
    /** Wall-clock end of the running rest, or null when idle. */
    var endsAtMillis: Long? = null
        private set

    var durationSeconds: Int = DEFAULT_REST_SECONDS
        private set

    /** True once the zero alert fired for the current rest, so it buzzes once. */
    private var alerted = false

    /**
     * Starts a rest after a set. [overrideSeconds] (picked on the chip) wins over the exercise's
     * [usualSeconds] from history, which wins over [DEFAULT_REST_SECONDS].
     */
    fun start(nowMillis: Long, usualSeconds: Int?, overrideSeconds: Int?) {
        durationSeconds = overrideSeconds ?: usualSeconds ?: DEFAULT_REST_SECONDS
        endsAtMillis = nowMillis + durationSeconds * 1_000L
        alerted = false
    }

    fun stop() {
        endsAtMillis = null
        alerted = false
    }

    fun isRunning(nowMillis: Long): Boolean = endsAtMillis?.let { nowMillis < it } == true

    /** Whole seconds left, rounded up (a rest shows 2:00 at its start, 0:00 only at its end). */
    fun remainingSeconds(nowMillis: Long): Int {
        val end = endsAtMillis ?: return 0
        val left = end - nowMillis
        return if (left <= 0) 0 else ((left + 999) / 1_000).toInt()
    }

    /** True exactly once when the rest reaches zero: the moment for the quiet vibration. */
    fun consumeZero(nowMillis: Long): Boolean {
        val end = endsAtMillis ?: return false
        if (alerted || nowMillis < end) return false
        alerted = true
        endsAtMillis = null
        return true
    }
}

/** `95` → `1:35`. */
fun formatRest(seconds: Int): String = "%d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)
