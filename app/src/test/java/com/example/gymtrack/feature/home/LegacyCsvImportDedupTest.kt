package com.example.gymtrack.feature.home

import com.example.gymtrack.core.data.NoteLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyCsvImportDedupTest {
    @Test
    fun contentFingerprintIgnoresTimestampSoAdjustedReimportsCanBeSkipped() {
        val original = note(timestamp = 1_800_000_000_000L)
        val adjusted = original.copy(timestamp = original.timestamp + 1_000L)

        assertEquals(
            legacyCsvContentFingerprint(original),
            legacyCsvContentFingerprint(adjusted),
        )
        assertFalse(legacyCsvFullFingerprint(original) == legacyCsvFullFingerprint(adjusted))
    }

    @Test
    fun allocatesFreeTimestampOffsetsForDistinctSameMinuteExports() {
        val usedTimestamps = mutableSetOf(1_800_000_000_000L)

        val first = allocateLegacyCsvTimestamp(1_800_000_000_000L, usedTimestamps)
        val second = allocateLegacyCsvTimestamp(1_800_000_000_000L, usedTimestamps)

        assertTrue(first.adjusted)
        assertEquals(1_800_000_001_000L, first.timestamp)
        assertTrue(second.adjusted)
        assertEquals(1_800_000_002_000L, second.timestamp)
    }

    private fun note(timestamp: Long): NoteLine = NoteLine(
        title = "",
        text = "leg press\n    7x 75kg",
        timestamp = timestamp,
        categoryName = "Legs",
        categoryColor = null,
        learnings = "",
        rowMetadata = "0'00''|bi\n0'05''|bi",
    )
}
