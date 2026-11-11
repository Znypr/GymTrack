package com.example.gymtrack.feature.editor.guardrail

import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.util.buildNoteRowMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #304 input guardrails: one test group per rule, plus the history gate and the edge cases. */
class SetGuardrailsTest {

    private fun note(timestamp: Long, lines: List<String>, flag: ExerciseFlag = ExerciseFlag.BILATERAL) = NoteLine(
        title = "",
        text = lines.joinToString("\n"),
        timestamp = timestamp,
        rowMetadata = buildNoteRowMetadata(List(lines.size) { "" }, List(lines.size) { flag }),
    )

    /** [sessions] earlier workouts of one exercise, each with the same [entries], 2'00'' apart. */
    private fun workouts(
        exercise: String,
        entries: List<String>,
        sessions: Int = 4,
        flag: ExerciseFlag = ExerciseFlag.BILATERAL,
        rest: String = "2'00''",
    ): List<NoteLine> = (1..sessions).map { n ->
        note(n * 86_400_000L, listOf(exercise) + entries.map { "      $it ($rest)" }, flag)
    }

    private fun guard(notes: List<NoteLine>, unit: String = "KG", exclude: Long? = null) =
        SetGuardrails(SetHistory.build(notes, exclude, unit), unit)

    private val bench = workouts("Bench press", listOf("8x 75", "8x 80", "6x 80"))

    private fun SetGuardrails.at(
        entry: String,
        earlier: List<String> = emptyList(),
        exercise: String = "Bench press",
        flag: ExerciseFlag = ExerciseFlag.BILATERAL,
        rest: Int? = 120,
        ignored: Set<Int> = emptySet(),
    ) = check(GuardrailBlock(exercise, flag, earlier, entry, rest, ignored))

    // Decimal slip ------------------------------------------------------------------------------

    @Test
    fun `825 after 80s is a decimal slip with a one-tap fix`() {
        val hint = guard(bench).at("8x 825")!!
        assertEquals(GuardrailRule.DECIMAL_SLIP, hint.rule)
        assertEquals("Meant 82.5? (last 80×6)", hint.message)
        assertEquals(GuardrailFix.REPLACE_ENTRY, hint.fix)
        assertEquals("8x 82.5", hint.fixEntry)
    }

    @Test
    fun `a typed decimal is never a decimal slip`() {
        assertNull(guard(bench).at("8x 82.5"))
    }

    // Over 2x recent max ------------------------------------------------------------------------

    @Test
    fun `over twice the recent max asks to edit`() {
        val hint = guard(bench).at("5x 250")!!
        assertEquals(GuardrailRule.OVER_RECENT_MAX, hint.rule)
        assertEquals(GuardrailFix.EDIT, hint.fix)
        assertTrue(hint.message, hint.message.contains("80 kg"))
    }

    @Test
    fun `a real PR jump under twice the max is quiet`() {
        assertNull(guard(bench).at("3x 100"))
        assertNull(guard(bench).at("1x 150"))
    }

    @Test
    fun `heavier sets earlier in this workout raise the reference`() {
        // 170 is over 2x the history (80) but the user already did 160 today.
        assertNull(guard(bench).at("3x 170", earlier = listOf("5x 120", "3x 160")))
    }

    @Test
    fun `an earlier set with an open hint is not a reference`() {
        val hint = guard(bench).at("3x 900", earlier = listOf("5x 825"), ignored = setOf(0))
        assertNotNull(hint)
    }

    @Test
    fun `an unfixed old typo in history does not lift the reference`() {
        val notes = bench + note(9 * 86_400_000L, listOf("Bench press", "      8x 800 (2'00'')"))
        assertEquals(GuardrailRule.DECIMAL_SLIP, guard(notes).at("8x 825")!!.rule)
    }

    // Unit mismatch (kg/lb mix) -----------------------------------------------------------------

    @Test
    fun `176 typed as kg on an 80 kg exercise looks like lb`() {
        val hint = guard(bench).at("8x 176")!!
        assertEquals(GuardrailRule.UNIT_MISMATCH, hint.rule)
        assertEquals("8x 176 lb", hint.fixEntry)
        assertEquals("Use lb", hint.fixLabel)
    }

    @Test
    fun `80 lb on a kg exercise looks like kg and the fix drops the unit`() {
        val hint = guard(bench).at("8x 80 lb")!!
        assertEquals(GuardrailRule.UNIT_MISMATCH, hint.rule)
        assertEquals("8x 80", hint.fixEntry)
    }

    @Test
    fun `history logged in lb is compared in kg`() {
        val lbBench = workouts("Bench press", listOf("8x 175 lb", "8x 180 lb"))
        // 82 kg is about 180 lb: fine. 180 typed as kg is not.
        assertNull(guard(lbBench).at("8x 82"))
        assertEquals(GuardrailRule.UNIT_MISMATCH, guard(lbBench).at("8x 180")!!.rule)
    }

    @Test
    fun `lb default unit suggests kg for a kg number`() {
        val lbUser = workouts("Bench press", listOf("8x 175", "8x 180"))
        val hint = guard(lbUser, unit = "LB").at("8x 80")!!
        assertEquals(GuardrailRule.UNIT_MISMATCH, hint.rule)
        assertEquals("8x 80 kg", hint.fixEntry)
    }

    // Reps over 50 ------------------------------------------------------------------------------

    @Test
    fun `60 reps on an 6-8 rep exercise suggests 6`() {
        val hint = guard(bench).at("60x 80")!!
        assertEquals(GuardrailRule.HIGH_REPS, hint.rule)
        assertEquals("6x 80", hint.fixEntry)
    }

    @Test
    fun `high reps with no single-digit candidate asks to edit`() {
        val hint = guard(bench).at("95x 80")!!
        assertEquals(GuardrailRule.HIGH_REPS, hint.rule)
        assertEquals(GuardrailFix.EDIT, hint.fix)
    }

    @Test
    fun `high reps are fine when the user's history has them`() {
        val crunch = workouts("Cable crunch", listOf("60x 20", "55x 20"))
        assertNull(guard(crunch).at("60x 20", exercise = "Cable crunch"))
    }

    // Duplicate ---------------------------------------------------------------------------------

    @Test
    fun `the same set within seconds is a double log`() {
        val hint = guard(emptyList()).at("8x 80", earlier = listOf("8x 80"), rest = 4)!!
        assertEquals(GuardrailRule.DUPLICATE, hint.rule)
        assertEquals(GuardrailFix.REMOVE, hint.fix)
    }

    @Test
    fun `straight sets after a real rest are not duplicates`() {
        assertNull(guard(emptyList()).at("8x 80", earlier = listOf("8x 80"), rest = 95))
        assertNull(guard(bench).at("8x", earlier = listOf("8x 80"), rest = 120))
    }

    @Test
    fun `an edited set has no rest and is never a duplicate`() {
        assertNull(guard(emptyList()).at("8x 80", earlier = listOf("8x 80"), rest = null))
    }

    // Unparsed line -----------------------------------------------------------------------------

    @Test
    fun `reps without any weight to carry are flagged even without history`() {
        val hint = guard(emptyList()).at("8x", exercise = "Leg press")!!
        assertEquals(GuardrailRule.UNPARSED, hint.rule)
        assertEquals(GuardrailFix.EDIT, hint.fix)
    }

    @Test
    fun `a lone number is flagged as reps-or-weight`() {
        assertEquals(GuardrailRule.UNPARSED, guard(emptyList()).at("80", exercise = "Leg press")!!.rule)
    }

    @Test
    fun `a carried weight parses and is quiet`() {
        assertNull(guard(emptyList()).at("8x", earlier = listOf("10x 60"), exercise = "Leg press"))
    }

    @Test
    fun `bodyweight reps without weight are fine`() {
        assertNull(guard(emptyList()).at("10x", exercise = "Pull up"))
    }

    // History gate ------------------------------------------------------------------------------

    @Test
    fun `history rules need at least three earlier workouts`() {
        val two = workouts("Bench press", listOf("8x 80"), sessions = 2)
        assertNull(guard(two).at("8x 825"))
        val three = workouts("Bench press", listOf("8x 80"), sessions = 3)
        assertNotNull(guard(three).at("8x 825"))
    }

    @Test
    fun `an unknown exercise gets no history hint`() {
        assertNull(guard(bench).at("8x 825", exercise = "Hack squat"))
    }

    // Unilateral / superset / timed / edited old workout ---------------------------------------

    @Test
    fun `unilateral history is separate from the bilateral one`() {
        val uni = workouts("Dumbbell row", listOf("10x 30", "10x 32"), flag = ExerciseFlag.UNILATERAL)
        val g = guard(uni)
        assertEquals(GuardrailRule.DECIMAL_SLIP, g.at("10x 315", exercise = "Dumbbell row", flag = ExerciseFlag.UNILATERAL)!!.rule)
        // The same name logged bilaterally has no history of its own yet.
        assertNull(g.at("10x 315", exercise = "Dumbbell row", flag = ExerciseFlag.BILATERAL))
    }

    @Test
    fun `superset exercises are checked against their own history`() {
        val notes = (1..4).map { n ->
            note(
                n * 86_400_000L,
                listOf("Bench press", "      8x 80 (2'00'')", "", "Lat pulldown", "      10x 60 (0'40'')"),
                ExerciseFlag.SUPERSET,
            )
        }
        val g = guard(notes)
        assertEquals(GuardrailRule.DECIMAL_SLIP, g.at("10x 605", exercise = "Lat pulldown", flag = ExerciseFlag.SUPERSET)!!.rule)
        assertNull(g.at("10x 65", exercise = "Lat pulldown", flag = ExerciseFlag.SUPERSET))
    }

    @Test
    fun `timed sets skip the weight and reps rules`() {
        val plank = workouts("Plank", listOf("60S", "45S"))
        assertNull(guard(plank).at("90S", exercise = "Plank"))
        assertNull(guard(bench).at("60S 200"))
    }

    @Test
    fun `an edited old workout is not checked against its own sets`() {
        // Workout 4 holds the typo; editing it must compare with workouts 1-3 only.
        val notes = workouts("Bench press", listOf("8x 80"), sessions = 3) +
            note(4 * 86_400_000L, listOf("Bench press", "      8x 825 (2'00'')"))
        val hint = guard(notes, exclude = 4 * 86_400_000L).at("8x 825", rest = null)
        assertEquals(GuardrailRule.DECIMAL_SLIP, hint!!.rule)
    }

    // History facts used by the rest timer (#349) ---------------------------------------------

    @Test
    fun `usual rest is the median relative time rounded to 15 s`() {
        val notes = workouts("Bench press", listOf("8x 80", "8x 80", "8x 80"), rest = "1'32''")
        assertEquals(90, guard(notes).usualRestSeconds("Bench press", ExerciseFlag.BILATERAL))
        assertNull(guard(emptyList()).usualRestSeconds("Bench press", ExerciseFlag.BILATERAL))
    }

    // Budget ------------------------------------------------------------------------------------

    @Test
    fun `a check stays well under the 2 ms main-thread budget`() {
        val notes = (1..60).flatMap { n ->
            listOf(note(n * 86_400_000L, listOf("Bench press", "      8x 80 (2'00'')", "      8x 82.5 (2'00'')", "", "Squat", "      5x 120 (3'00'')")))
        }
        val g = guard(notes)
        val earlier = listOf("10x 60", "8x 70", "8x 75", "8x 80", "6x 80")
        repeat(300) { g.at("8x 825", earlier = earlier) } // warm-up (JIT, regex, name cache)
        val runs = 500
        val start = System.nanoTime()
        repeat(runs) { g.at("8x 825", earlier = earlier) }
        val averageMs = (System.nanoTime() - start) / runs / 1_000_000.0
        assertTrue("average check took $averageMs ms", averageMs < 2.0)
    }
}
