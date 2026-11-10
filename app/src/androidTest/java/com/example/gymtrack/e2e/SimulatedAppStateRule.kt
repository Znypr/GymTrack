package com.example.gymtrack.e2e

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.SettingsStore
import com.example.gymtrack.core.time.AppClock
import com.example.gymtrack.core.time.Clock
import com.example.gymtrack.core.time.SystemWallClock
import com.example.gymtrack.core.timer.NoteTimerStore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.rules.ExternalResource

/** Wall clock that only moves when the test says so. */
class FakeClock(startMillis: Long) : Clock {
    private val now = AtomicLong(startMillis)

    override fun nowMillis(): Long = now.get()

    fun advanceSeconds(seconds: Long) {
        require(seconds >= 0) { "Time only moves forward in a workout simulation" }
        now.addAndGet(seconds * 1_000L)
    }

    /** Jumps to an absolute time (long-horizon runs move between simulated days). */
    fun setTo(epochMillis: Long) {
        now.set(epochMillis)
    }
}

/**
 * Runs OUTSIDE the activity rule. Before the activity starts it:
 * 1. refuses to run on a physical device, because it wipes the debug app's data;
 * 2. clears the Room database, timer state, settings and the stats cache;
 * 3. swaps [AppClock] for a [FakeClock] so rests are simulated instead of waited on.
 */
class SimulatedAppStateRule : ExternalResource() {
    val context: Context = ApplicationProvider.getApplicationContext()

    lateinit var clock: FakeClock
        private set

    override fun before() {
        assumeTrue(
            "Workout simulation wipes app data, so it only runs on an emulator " +
                "(fingerprint=${Build.FINGERPRINT}, hardware=${Build.HARDWARE})",
            isEmulator(),
        )
        resetAppData()
        // Start two hours in the past, on a whole second, so simulated workouts stay in the past.
        val start = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)
        clock = FakeClock(start - start % 1_000L)
        AppClock.current = clock
    }

    override fun after() {
        AppClock.current = SystemWallClock
        resetAppData()
    }

    private fun resetAppData() = runBlocking {
        NoteDatabase.getDatabase(context).clearAllTables()
        NoteTimerStore.stop(context)
        SettingsStore.save(context, Settings(darkMode = false))
        // Same name as StatsPersistentCacheStore's private PREFERENCES_NAME.
        context.getSharedPreferences("stats_cache", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun isEmulator(): Boolean =
        Build.HARDWARE.contains("ranchu") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.PRODUCT.contains("sdk")
}
