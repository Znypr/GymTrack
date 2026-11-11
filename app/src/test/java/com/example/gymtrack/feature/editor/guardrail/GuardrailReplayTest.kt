package com.example.gymtrack.feature.editor.guardrail

import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.util.buildNoteRowMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The replay tool behind the "< 2 % false positives on real history" criterion, run here on a
 * synthetic 40-workout PPL history with the variation real logs have (progression, deloads,
 * warm-ups, straight sets, carried weights, myo-reps, timed core, an lb machine).
 */
class GuardrailReplayTest {

    private fun note(day: Int, vararg blocks: List<String>): NoteLine {
        val lines = blocks.flatMapIndexed { i, block -> (if (i > 0) listOf("") else emptyList()) + block }
        return NoteLine(
            title = "",
            text = lines.joinToString("\n"),
            timestamp = day * 86_400_000L,
            rowMetadata = buildNoteRowMetadata(List(lines.size) { "" }, List(lines.size) { ExerciseFlag.BILATERAL }),
        )
    }

    private fun sets(name: String, vararg entries: String) =
        listOf(name) + entries.mapIndexed { i, e -> "      $e (${if (i == 0) "3'10" else "${1 + i % 2}'${10 + i * 7}"}'')" }

    private fun realisticHistory(): List<NoteLine> = (1..40).map { day ->
        val w = 60 + (day % 12) * 2.5 - (if (day % 13 == 0) 15 else 0) // progression with deloads
        val squat = 100 + (day % 10) * 5
        when (day % 3) {
            0 -> note(
                day,
                sets("Bench press", "10x 40", "8x ${w}", "8x", "6+2+2x ${w - 10}"),
                sets("Incline db press", "10x 26", "10x", "9x 28"),
                sets("Plank", "60S", "45S"),
            )
            1 -> note(
                day,
                sets("Squat", "5x 60", "5x $squat", "5x", "5x"),
                sets("Leg press hs", "12x 300 lb", "12x 320 lb", "10x"),
                sets("Calf raise", "15x 80", "15x", "12x"),
            )
            else -> note(
                day,
                sets("Lat pulldown", "12x 55", "10x 60", "10x"),
                sets("Cable row", "12x 50", "12x"),
                sets("Pull up", "8x", "7x", "6x"),
            )
        }
    }

    @Test
    fun `clean realistic history stays under the 2 percent false-positive budget`() {
        val report = replayGuardrails(realisticHistory())
        assertTrue(report.sets > 300)
        assertTrue("${report.hinted}/${report.sets} hinted: ${report.examples}", report.rate < 0.02)
    }

    @Test
    fun `planted slips are caught`() {
        val slipped = realisticHistory() + note(41, sets("Bench press", "8x 825", "8x 82.5")) +
            note(42, sets("Squat", "5x 330")) // lb number typed as kg
        val report = replayGuardrails(slipped)
        assertEquals(1, report.hints[GuardrailRule.DECIMAL_SLIP])
        assertEquals(1, report.hints[GuardrailRule.UNIT_MISMATCH])
    }
}
