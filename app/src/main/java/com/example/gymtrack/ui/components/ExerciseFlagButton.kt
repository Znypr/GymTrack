package com.example.gymtrack.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
    val color = when (flag) {
        ExerciseFlag.UNILATERAL -> MaterialTheme.colorScheme.error
        ExerciseFlag.SUPERSET -> SupersetBlue
        ExerciseFlag.BILATERAL -> relColor
    }
    OutlinedButton(
        onClick = onToggle,
        border = BorderStroke(1.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = color
        ),
        shape = RoundedCornerShape(4.dp),
        // RESTORED: Standard padding
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        // RESTORED: Default minimum size for touch targets
        modifier = modifier.defaultMinSize(minWidth = 36.dp, minHeight = 28.dp)
    ) {
        val text = when (flag) {
            ExerciseFlag.BILATERAL -> "Bi"
            ExerciseFlag.UNILATERAL -> "Uni"
            ExerciseFlag.SUPERSET -> "SS"
        }
        Text(text, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}