package com.example.gymtrack.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * #311 acceptance "protan/deutan simulation check": the body map's low · moderate · high levels
 * must stay apart by lightness for colour-blind users, in every theme. The map also hatches low
 * and moderate (pattern, not colour alone); this test guards the colour half.
 *
 * Simulation: Machado, Oliveira & Fernandes 2009, severity 1.0, applied in linear RGB.
 */
class MuscleRampColourBlindTest {

    private val themes = mapOf(
        "Normal dark" to NormalDarkColors,
        "Normal light" to NormalLightColors,
        "Notebook" to NotebookColors,
    )

    private val vision: Map<String, Array<DoubleArray>?> = mapOf(
        "typical" to null,
        "protan" to arrayOf(
            doubleArrayOf(0.152286, 1.052583, -0.204868),
            doubleArrayOf(0.114503, 0.786281, 0.099216),
            doubleArrayOf(-0.003882, -0.048116, 1.051998),
        ),
        "deutan" to arrayOf(
            doubleArrayOf(0.367322, 0.860646, -0.227968),
            doubleArrayOf(0.280085, 0.672501, 0.047413),
            doubleArrayOf(-0.011820, 0.042940, 0.968881),
        ),
        "tritan" to arrayOf(
            doubleArrayOf(1.255528, -0.076749, -0.178779),
            doubleArrayOf(-0.078411, 0.930809, 0.147602),
            doubleArrayOf(0.004733, 0.691367, 0.303900),
        ),
    )

    @Test
    fun `adjacent muscle levels stay apart under colour-blind simulation`() {
        themes.forEach { (theme, c) ->
            // The map is drawn on a card.
            val ramp = listOf(c.muscleLow, c.muscleModerate, c.muscleHigh).map { it.compositeOver(c.surface) }
            vision.forEach { (kind, matrix) ->
                val lum = ramp.map { luminance(simulate(it, matrix)) }
                lum.zipWithNext().forEachIndexed { i, (a, b) ->
                    val ratio = (max(a, b) + 0.05) / (min(a, b) + 0.05)
                    assertTrue("$theme $kind level $i→${i + 1} ratio $ratio < 1.3", ratio >= 1.3)
                }
                val rising = lum.zipWithNext().all { (a, b) -> b > a }
                val falling = lum.zipWithNext().all { (a, b) -> b < a }
                assertTrue("$theme $kind ramp is not monotonic: $lum", rising || falling)
            }
        }
    }

    private fun linear(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

    private fun simulate(color: Color, m: Array<DoubleArray>?): DoubleArray {
        val rgb = doubleArrayOf(linear(color.red), linear(color.green), linear(color.blue))
        if (m == null) return rgb
        return DoubleArray(3) { r -> (m[r][0] * rgb[0] + m[r][1] * rgb[1] + m[r][2] * rgb[2]).coerceIn(0.0, 1.0) }
    }

    private fun luminance(rgb: DoubleArray): Double = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]
}
