package com.example.gymtrack.core.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import kotlin.math.roundToInt

class CanonicalExerciseVisualTransformation(
    private val isUnilateral: Boolean = false,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isBlank()) return TransformedText(text, OffsetMapping.Identity)

        val canonical = ExerciseIdentityResolver.resolve(
            rawName = raw,
            isUnilateral = isUnilateral,
        ).canonicalName.ifBlank { raw }

        return TransformedText(
            text = AnnotatedString(canonical),
            offsetMapping = ProportionalOffsetMapping(
                originalLength = raw.length,
                transformedLength = canonical.length,
            ),
        )
    }
}

private class ProportionalOffsetMapping(
    private val originalLength: Int,
    private val transformedLength: Int,
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        return mapOffset(
            offset = offset,
            sourceLength = originalLength,
            targetLength = transformedLength,
        )
    }

    override fun transformedToOriginal(offset: Int): Int {
        return mapOffset(
            offset = offset,
            sourceLength = transformedLength,
            targetLength = originalLength,
        )
    }

    private fun mapOffset(offset: Int, sourceLength: Int, targetLength: Int): Int {
        if (sourceLength <= 0 || targetLength <= 0) return 0
        if (offset <= 0) return 0
        if (offset >= sourceLength) return targetLength
        return ((offset.toFloat() / sourceLength.toFloat()) * targetLength.toFloat())
            .roundToInt()
            .coerceIn(0, targetLength)
    }
}
