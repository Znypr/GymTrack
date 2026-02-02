package com.example.gymtrack.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.data.ExerciseFlag
import com.example.gymtrack.ui.theme.SupersetBlue

@Composable
fun ExerciseFlagButton(
    flag: ExerciseFlag,
    relColor: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = when (flag) {
        ExerciseFlag.UNILATERAL -> MaterialTheme.colorScheme.error
        ExerciseFlag.SUPERSET -> SupersetBlue
        ExerciseFlag.BILATERAL -> MaterialTheme.colorScheme.onSurface // Brighter default
    }

    val containerColor = if (flag == ExerciseFlag.BILATERAL) {
        Color.Transparent
    } else {
        activeColor.copy(alpha = 0.15f) // Slightly stronger tint
    }

    Button(
        onClick = onToggle,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = activeColor
        ),
        // Thicker border for better visibility
        border = if (flag == ExerciseFlag.BILATERAL) BorderStroke(1.5.dp, activeColor.copy(alpha = 0.5f)) else null,
        shape = RoundedCornerShape(8.dp), // Slightly rounder
        contentPadding = PaddingValues(0.dp),
        // [CHANGE] Increased size significantly (was 32x24)
        modifier = modifier.defaultMinSize(minWidth = 44.dp, minHeight = 30.dp)
    ) {
        val text = when (flag) {
            ExerciseFlag.BILATERAL -> "Bi"
            ExerciseFlag.UNILATERAL -> "Uni"
            ExerciseFlag.SUPERSET -> "SS"
        }
        Text(
            text = text,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Black, // Boldest weight
            fontSize = 16.sp // Larger text
        )
    }
}