package com.example.gymtrack

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*
import com.example.gymtrack.data.Settings
import com.example.gymtrack.util.formatRelativeTime

class FormatRelativeTimeTest {
    @Test
    fun `recent time shows today prefix`() {
        val now = System.currentTimeMillis()
        val threeHoursAgo = now - 3 * 60 * 60 * 1000

        val expectedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(threeHoursAgo))
        assertEquals("Today $expectedTime", formatRelativeTime(threeHoursAgo, Settings()))

    }

    @Test
    fun `yesterday prefix used for timestamps between 24h and 48h`() {
        val now = System.currentTimeMillis()
        val thirtyHoursAgo = now - 30 * 60 * 60 * 1000
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(thirtyHoursAgo))
        assertEquals("Yesterday $time", formatRelativeTime(thirtyHoursAgo, Settings()))
    }
}
