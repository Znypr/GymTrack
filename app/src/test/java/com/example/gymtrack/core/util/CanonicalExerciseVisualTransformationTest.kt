package com.example.gymtrack.core.util

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalExerciseVisualTransformationTest {

    @Test
    fun `hammer strength marker is hidden from exercise title`() {
        val transformed = CanonicalExerciseVisualTransformation()
            .filter(AnnotatedString("dips hs"))

        assertEquals("Dip", transformed.text.text)
    }

    @Test
    fun `realleader marker is hidden from exercise title`() {
        val transformed = CanonicalExerciseVisualTransformation()
            .filter(AnnotatedString("dips rl"))

        assertEquals("Dip", transformed.text.text)
    }

    @Test
    fun `full brand names are hidden from exercise title`() {
        val hammerStrength = CanonicalExerciseVisualTransformation()
            .filter(AnnotatedString("dips hammer strength"))
        val realleader = CanonicalExerciseVisualTransformation()
            .filter(AnnotatedString("dips realleader"))

        assertEquals("Dip", hammerStrength.text.text)
        assertEquals("Dip", realleader.text.text)
    }

    private fun title(raw: String) = CanonicalExerciseVisualTransformation().filter(AnnotatedString(raw)).text.text

    @Test
    fun `every brand shown as a chip is hidden from the title`() {
        assertEquals("Chest Press", title("chest press prime"))
        assertEquals("Chest Press", title("chest press cybex"))
        assertEquals("Chest Press", title("chest press cy"))
        assertEquals("Chest Press", title("chest press g80"))
        assertEquals("Chest Press", title("chest press at"))
        assertEquals("Shoulder Press", title("shoulder press lf"))
        assertEquals("Shoulder Press", title("shoulder press real leader"))
    }

    @Test
    fun `equipment and attachment words shown as chips are hidden from the title`() {
        assertEquals("Incline Press", title("incline db press"))
        assertEquals("Good Morning", title("bar good morning"))
        assertEquals("Face Pull", title("face pull rope"))
        assertEquals("Chest Press", title("chest press machine"))
        assertEquals("Shoulder Press", title("smith shoulder press"))
    }

    @Test
    fun `words that are part of the exercise name stay in the title`() {
        assertEquals("Hammer Curl", title("hammer curl hs"))
        assertEquals("T-Bar Row", title("tbar row prime"))
        assertEquals("Lat Pulldown", title("latpulldown rl"))
    }

    @Test
    fun `title is display only and maps back onto the raw text`() {
        val raw = AnnotatedString("chest press prime")
        val transformed = CanonicalExerciseVisualTransformation().filter(raw)

        assertEquals("chest press prime", raw.text)
        assertEquals("Chest Press".length, transformed.offsetMapping.originalToTransformed(raw.length))
        assertEquals(raw.length, transformed.offsetMapping.transformedToOriginal("Chest Press".length))
    }
}
