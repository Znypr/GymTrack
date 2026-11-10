package com.example.gymtrack.feature.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpContentTest {
    @Test
    fun feedbackEmailHasDeviceFactsOnly() {
        val email = buildFeedbackEmail("0.1.0 (2026070701)", "15", 35, "Google Pixel 8")
        assertEquals("znyprfitness@gmail.com", email.to)
        assertTrue(email.subject.contains("0.1.0"))
        assertTrue(email.body.contains("Android: 15 (API 35)"))
        assertTrue(email.body.contains("Device: Google Pixel 8"))
        assertTrue(email.body.contains("No workout data"))
        // Nothing but these four facts: no notes, sets or exercise names.
        assertFalse(email.body.contains(" x ") || email.body.contains("kg"))
    }

    @Test
    fun cheatSheetCoversTheGrammar() {
        val examples = HelpContent.grammar.map { it.example }
        listOf("8x 80", "8x", "10+3+2x 45", "60s", "45s 10", "Enter twice", "L/R", "SS").forEach {
            assertTrue("cheat-sheet misses $it", it in examples)
        }
    }

    @Test
    fun faqNamesNoUnshippedFeature() {
        val text = HelpContent.faq.joinToString(" ") { it.question + " " + it.answer }.lowercase()
        listOf("muscle map", "suggestion", "rest timer", "account sync", "cloud sync").forEach {
            assertFalse("FAQ mentions unshipped '$it'", text.contains(it))
        }
        assertTrue(HelpContent.faq.size in 8..12)
    }
}
