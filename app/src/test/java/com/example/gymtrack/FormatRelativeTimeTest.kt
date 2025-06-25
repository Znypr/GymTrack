package com.example.gymtrack

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*
import com.example.gymtrack.Settings

class FormatRelativeTimeTest {
    @Test
    fun `recent time shows only time`() {
        val now = System.currentTimeMillis()
        val threeHoursAgo = now - 3 * 60 * 60 * 1000
        val expected = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(threeHoursAgo))
        assertEquals(expected, formatRelativeTime(threeHoursAgo, Settings()))
    }

    @Test
    fun `yesterday prefix used for timestamps between 24h and 48h`() {
        val now = System.currentTimeMillis()
        val thirtyHoursAgo = now - 30 * 60 * 60 * 1000
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(thirtyHoursAgo))
        assertEquals("Yesterday $time", formatRelativeTime(thirtyHoursAgo, Settings()))
    }
}
