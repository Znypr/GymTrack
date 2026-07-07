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

    @Test
    fun selectsMostCompleteSnapshotPerTimestamp() {
        val timestamp = 1_800_000_000_000L
        val incomplete = LegacyCsvImportCandidate(
            displayName = "note-a.csv",
            note = note(
                timestamp = timestamp,
                text = "Tbar Rows Prime",
            ),
            fileSizeBytes = 120L,
        )
        val complete = LegacyCsvImportCandidate(
            displayName = "note-b.csv",
            note = note(
                timestamp = timestamp,
                text = "Tbar Rows Prime\n    8x 50kg\n    7x 80kg\nLatpulldown\n    10x 60kg",
                rowMetadata = "0'00''|bi\n0'05''|1x\n2'55''|1x\n23'55''|uni\n24'00''|1x",
            ),
            fileSizeBytes = 500L,
        )
        val exactDuplicate = complete.copy(displayName = "note-b-copy.csv")

        val selection = selectBestLegacyCsvCandidates(
            listOf(incomplete, complete, exactDuplicate),
        )

        assertEquals(listOf(complete), selection.selected)
        assertEquals(1, selection.exactDuplicates)
        assertEquals(1, selection.supersededSnapshots)
    }

    private fun note(
        timestamp: Long,
        text: String = "leg press\n    7x 75kg",
        rowMetadata: String = "0'00''|bi\n0'05''|bi",
    ): NoteLine = NoteLine(
        title = "",
        text = text,
        timestamp = timestamp,
        categoryName = "Legs",
        categoryColor = null,
        learnings = "",
        rowMetadata = rowMetadata,
    )
}
