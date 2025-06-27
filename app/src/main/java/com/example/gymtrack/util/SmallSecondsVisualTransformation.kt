package com.example.gymtrack.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.TextUnit

/**
 * VisualTransformation that makes the seconds portion of a time string smaller
 * and raised. Works with patterns like "0'05''".
 */
class SmallSecondsVisualTransformation(
    private val baseFontSize: TextUnit
) : VisualTransformation {

    private val timePattern = "\\d+'\\d{2}''".toRegex()

    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)

        timePattern.findAll(text.text).forEach { match ->
            val value = match.value
            val firstPrime = value.indexOf("'")
            if (firstPrime != -1 && value.endsWith("''")) {
                val secStart = match.range.first + firstPrime + 1
                val secEnd = secStart + 2
                builder.addStyle(
                    SpanStyle(
                        fontSize = baseFontSize * 0.7f,
                        baselineShift = BaselineShift(0.3f),
                    ),
                    secStart,
                    secEnd,
                )
            }
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun rememberSmallSecondsVisualTransformation(fontSize: TextUnit): VisualTransformation {
    return remember(fontSize) { SmallSecondsVisualTransformation(fontSize) }
}