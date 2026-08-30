package com.example.gymtrack.core.util

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalExerciseVisualTransformationTest {

    @Test
    fun `hammer strength marker is hidden from exercise title`() {
        val transformed = CanonicalExerciseVisualTransformation()
            .filter(AnnotatedString("dips hs"))

        assertEquals("Dips", transformed.text.text)
    }

    @Test
    fun `realleader marker is hidden from exercise title`() {
        val transformed = CanonicalExerciseVisualTransformation()
            .filter(AnnotatedString("dips rl"))

        assertEquals("Dips", transformed.text.text)
    }

    @Test
    fun `full brand names are hidden from exercise title`() {
        val hammerStrength = CanonicalExerciseVisualTransformation()
            .filter(AnnotatedString("dips hammer strength"))
        val realleader = CanonicalExerciseVisualTransformation()
            .filter(AnnotatedString("dips realleader"))

        assertEquals("Dips", hammerStrength.text.text)
        assertEquals("Dips", realleader.text.text)
    }
}
