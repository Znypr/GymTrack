package com.example.gymtrack.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * VisualTransformation that prepends a bullet to each line of text.
 * Used for displaying learnings as a bullet list without modifying the content.
 */
class BulletListTransformation(private val bullet: String = "\u2022") : VisualTransformation {
    private val prefix = "$bullet "

    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val lines = original.split('\n')
        val transformed = buildString {
            lines.forEachIndexed { idx, line ->
                append(prefix)
                append(line)
                if (idx != lines.lastIndex) append('\n')
            }
        }
        val bulletLen = prefix.length
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val before = original.substring(0, offset)
                val lineBreaks = before.count { it == '\n' }
                return offset + bulletLen * (lineBreaks + 1)
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= bulletLen) return 0
                var orig = 0
                var trans = bulletLen
                while (orig < original.length && trans < offset) {
                    if (original[orig] == '\n') {
                        orig++
                        trans++ // newline
                        if (trans >= offset) break
                        trans += bulletLen
                    } else {
                        orig++
                        trans++
                    }
                }
                return orig
            }
        }
        return TransformedText(AnnotatedString(transformed), offsetMapping)
    }
}

@Composable
fun rememberBulletListTransformation(): VisualTransformation {
    return remember { BulletListTransformation() }
}