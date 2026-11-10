package com.example.gymtrack.feature.stats

import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.util.buildNoteRowMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

/** #321: an overnight-resumed workout is charted with its real session, not dropped as 13 h. */
class StatsDurationGapTest {
    @Test
    fun overnightResume_isChartedWithTheEveningDurationInsteadOfDropped() {
        val overnight = NoteLine(
            title = "",
            text = "Bench\n      8x 80\n      8x 80\n      8x 80",
            timestamp = 1_700_000_000_000L,
            rowMetadata = buildNoteRowMetadata(
                times = listOf("0'00''", "30'00''", "60'00''", "804'00''"),
                flags = List(4) { ExerciseFlag.BILATERAL },
            ),
        )

        assertEquals(0, countUnreasonableWorkoutDurations(listOf(overnight)))
        assertEquals(60f, buildWeeklyAverageWorkoutDurations(listOf(overnight)).single().second)
    }
}
