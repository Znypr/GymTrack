package com.example.gymtrack.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color

/**
 * VisualTransformation that highlights relative times like "(00'30'')" within the text.
 */
class RelativeTimeVisualTransformation : VisualTransformation {
    private val pattern = "\\((?:\\d+'\\d{2}''|\\d+s)\\)".toRegex()

    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        pattern.findAll(text.text).forEach { match ->
            builder.addStyle(
                SpanStyle(color = Color.LightGray),
                match.range.first,
                match.range.last + 1
            )
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
