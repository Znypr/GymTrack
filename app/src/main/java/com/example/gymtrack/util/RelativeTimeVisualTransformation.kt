package com.example.gymtrack.util

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation

/**
 * VisualTransformation that highlights relative times like "(00'30'')" within the text.
 */
class RelativeTimeVisualTransformation(private val color: Color) : VisualTransformation {
    private val pattern = "\\((?:\\d+'\\d{2}''|\\d+s)\\)".toRegex()
    private val secondsTrans = SmallSecondsVisualTransformation()

    override fun filter(text: AnnotatedString): TransformedText {
        val withSeconds = secondsTrans.filter(text).text
        val builder = AnnotatedString.Builder(withSeconds)
        pattern.findAll(withSeconds.text).forEach { match ->
            builder.addStyle(
                SpanStyle(color = color),
                match.range.first,
                match.range.last + 1
            )
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun rememberRelativeTimeVisualTransformation(): RelativeTimeVisualTransformation {
    val color = MaterialTheme.colorScheme.onSurface
    val tint = color.copy(alpha = if (isSystemInDarkTheme()) 0.85f else 0.45f)
    return remember(tint) { RelativeTimeVisualTransformation(tint) }
}
