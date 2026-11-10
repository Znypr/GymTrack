package com.example.gymtrack.feature.editor

import com.example.gymtrack.core.data.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorRowDisplayTest {

    private fun display(text: String, earlier: List<String> = emptyList(), unit: WeightUnit = WeightUnit.KG) =
        setRowDisplay(text, earlier, unit)

    @Test
    fun `split keeps indent and rest time apart from the entry`() {
        val parts = splitSetRow("      10x 60 (1'38'')")
        assertEquals("      ", parts.indent)
        assertEquals("10x 60", parts.entry)
        assertEquals(" (1'38'')", parts.restSuffix)
        assertEquals("      8x 62.5 (1'38'')", parts.withEntry("8x 62.5"))
    }

    @Test
    fun `a draft without indent or time splits cleanly`() {
        val parts = splitSetRow("8x 8")
        assertEquals("", parts.indent)
        assertEquals("8x 8", parts.entry)
        assertEquals("", parts.restSuffix)
    }

    @Test
    fun `reps first, rest shown once without the trailing primes`() {
        val d = display("      8x 80 (2'30'')")
        assertEquals("8", d.reps)
        assertEquals("80", d.weight)
        assertFalse(d.weightIsCarried)
        assertNull(d.unit)
        assertEquals("2'30", d.rest)
    }

    @Test
    fun `reps only set shows the carried weight muted`() {
        val d = display("      8x (2'44'')", earlier = listOf("      10x 60 (1'38'')", "      8x 70 (2'51'')"))
        assertEquals("70", d.weight)
        assertTrue(d.weightIsCarried)
    }

    @Test
    fun `timed sets neither give nor take the carried weight`() {
        val d = display("      8x (1'00'')", earlier = listOf("      8x 70 (1'00'')", "      60s 10 (1'00'')"))
        assertEquals("70", d.weight)
        val timed = display("      45s (1'00'')", earlier = listOf("      8x 70 (1'00'')"))
        assertNull(timed.weight)
        assertEquals("45", timed.seconds)
    }

    @Test
    fun `no earlier weight means a bodyweight set, nothing invented`() {
        val d = display("      12x (1'00'')")
        assertNull(d.weight)
        assertFalse(d.weightIsCarried)
    }

    @Test
    fun `unit only when it differs from the default, carried with the weight`() {
        assertNull(display("      8x 70 kg (1'00'')").unit)
        assertEquals("lb", display("      8x 70 lb (1'00'')").unit)
        assertEquals("lb", display("      8x (1'00'')", earlier = listOf("      8x 70 lbs (1'00'')")).unit)
        assertNull(display("      8x 70 lb (1'00'')", unit = WeightUnit.LB).unit)
    }

    @Test
    fun `myo reps keep the typed parts and add a total`() {
        val d = display("      10+3+2x 45 (2'40'')")
        assertEquals("10+3+2", d.reps)
        assertEquals(15, d.myoTotal)
        assertEquals("45", d.weight)
    }

    @Test
    fun `timed set with weight`() {
        val d = display("      45s 10 (2'10'')")
        assertEquals("45", d.seconds)
        assertEquals("10", d.weight)
        assertNull(d.reps)
    }

    @Test
    fun `unknown text is shown as typed, never the raw indent or time`() {
        val d = display("      felt heavy (0'30'')")
        assertEquals("felt heavy", d.freeText)
        assertEquals("0'30", d.rest)
    }

    @Test
    fun `keypad entry is drawn in display grammar`() {
        assertEquals("8 × 8", prettyKeypadEntry("8x 8"))
        assertEquals("8 × ", prettyKeypadEntry("8x "))
        assertEquals("10+3+2 × 45", prettyKeypadEntry("10+3+2x 45"))
        assertEquals("60 s ", prettyKeypadEntry("60s "))
        assertEquals("45 s × 10", prettyKeypadEntry("45s 10"))
        assertEquals("", prettyKeypadEntry(""))
    }
}
