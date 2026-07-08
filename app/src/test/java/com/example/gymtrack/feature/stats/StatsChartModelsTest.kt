package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.NoteLine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class StatsChartModelsTest {
    @Test
    fun buildWeeklyWorkoutCountsGroupsByIsoWeek() {
        val counts = buildWeeklyWorkoutCounts(
            listOf(
                note(timestamp = localTimestamp(2024, 1, 1)),
                note(timestamp = localTimestamp(2024, 1, 2)),
                note(timestamp = localTimestamp(2024, 1, 8)),
            )
        )

        assertEquals(listOf(2, 1), counts.map { it.count })
    }

    @Test
    fun buildVolumeByCategoryRanksLoadedSetsByVolume() {
        val volume = buildVolumeByCategory(
            listOf(
                note(
                    text = "Bench Press\n    8x 100kg\n    10x 90kg",
                    categoryName = "Push",
                ),
                note(
                    text = "Row\n    10x 80kg",
                    categoryName = "Pull",
                ),
            )
        )

        assertEquals("Push", volume.first().category)
        assertEquals(1_700f, volume.first().totalVolume)
        assertEquals(2, volume.first().setCount)
    }

    private fun note(
        text: String = "Bench Press\n    8x 100kg",
        timestamp: Long = 1L,
        categoryName: String? = null,
    ): NoteLine {
        return NoteLine(
            title = "Workout",
            text = text,
            timestamp = timestamp,
            categoryName = categoryName,
        )
    }

    private fun localTimestamp(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }.timeInMillis
    }
}
