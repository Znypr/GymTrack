package com.example.gymtrack.feature.editor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #325: which editor saves also re-derive the workout's Stats data. */
class HistoryEditRefreshPolicyTest {
    @Test
    fun pastWorkout_isRefreshedWithoutAskingForItsStatus() = runBlocking {
        var asked = false
        assertTrue(shouldRefreshDerivedDataOnSave(isActiveWorkout = false) { asked = true; false })
        assertFalse("status lookup skipped for a non-active workout", asked)
    }

    @Test
    fun runningWorkout_staysDraftOnlyUntilFinish() = runBlocking {
        assertFalse(shouldRefreshDerivedDataOnSave(isActiveWorkout = true) { false })
    }

    @Test
    fun runningButAlreadyCompletedWorkout_isRefreshed() = runBlocking {
        assertTrue(shouldRefreshDerivedDataOnSave(isActiveWorkout = true) { true })
    }
}
