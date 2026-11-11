package com.example.gymtrack.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #349 rest countdown: default, history and picked lengths, rounding and the single zero alert. */
class RestCountdownTest {

    @Test
    fun `defaults to 2 minutes without history`() {
        val rest = RestCountdown()
        rest.start(nowMillis = 0, usualSeconds = null, overrideSeconds = null)
        assertEquals(120, rest.remainingSeconds(0))
        assertEquals("2:00", formatRest(rest.remainingSeconds(0)))
    }

    @Test
    fun `the exercise's usual rest wins over the default and a picked length wins over both`() {
        val rest = RestCountdown()
        rest.start(0, usualSeconds = 90, overrideSeconds = null)
        assertEquals(90, rest.durationSeconds)
        rest.start(0, usualSeconds = 90, overrideSeconds = 150)
        assertEquals(150, rest.durationSeconds)
    }

    @Test
    fun `remaining rounds up so it reads 0 only at the end`() {
        val rest = RestCountdown()
        rest.start(0, 60, null)
        assertEquals(60, rest.remainingSeconds(1))
        assertEquals(1, rest.remainingSeconds(59_001))
        assertEquals(0, rest.remainingSeconds(60_000))
        assertTrue(rest.isRunning(59_999))
        assertFalse(rest.isRunning(60_000))
    }

    @Test
    fun `zero fires exactly once and ends the rest`() {
        val rest = RestCountdown()
        rest.start(0, 60, null)
        assertFalse(rest.consumeZero(30_000))
        assertTrue(rest.consumeZero(60_000))
        assertFalse(rest.consumeZero(61_000))
        assertNull(rest.endsAtMillis)
    }

    @Test
    fun `a new set restarts the rest and skip stops it without an alert`() {
        val rest = RestCountdown()
        rest.start(0, 60, null)
        rest.start(50_000, 60, null)
        assertEquals(60, rest.remainingSeconds(50_000))
        rest.stop()
        assertFalse(rest.consumeZero(200_000))
        assertEquals(0, rest.remainingSeconds(200_000))
    }

    @Test
    fun `formats minutes and seconds`() {
        assertEquals("0:05", formatRest(5))
        assertEquals("1:35", formatRest(95))
        assertEquals("0:00", formatRest(-3))
    }
}
