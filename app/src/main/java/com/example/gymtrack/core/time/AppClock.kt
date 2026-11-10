package com.example.gymtrack.core.time

import androidx.annotation.VisibleForTesting

/** Source of wall-clock time in epoch milliseconds. */
fun interface Clock {
    fun nowMillis(): Long
}

/** Production clock: the device wall clock. */
object SystemWallClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/**
 * App-wide clock used by the editor, timer, save pipeline and statistics.
 *
 * Production always reads [SystemWallClock]. Instrumented tests swap [current] for a fake clock
 * so a whole workout can be simulated without waiting through real rest periods.
 */
object AppClock {
    @Volatile
    @set:VisibleForTesting
    var current: Clock = SystemWallClock

    fun nowMillis(): Long = current.nowMillis()
}
