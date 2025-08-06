package com.example.gymtrack.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
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
import com.example.gymtrack.data.ExerciseFlag
import com.example.gymtrack.ui.theme.SupersetBlue

@Composable
fun ExerciseFlagTag(
    flag: ExerciseFlag,
    relColor: Color,
    modifier: Modifier = Modifier
) {
    val color = when (flag) {
        ExerciseFlag.UNILATERAL -> MaterialTheme.colorScheme.error
        ExerciseFlag.SUPERSET -> SupersetBlue
        ExerciseFlag.BILATERAL -> relColor
    }
    OutlinedButton(
        onClick = {},
        enabled = false,
        border = BorderStroke(1.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            disabledContentColor = color,
        ),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        modifier = modifier.height(28.dp)
    ) {
        val text = when (flag) {
            ExerciseFlag.UNILATERAL -> "2x"
            ExerciseFlag.SUPERSET -> "SS"
            ExerciseFlag.BILATERAL -> "1x"
        }
        Text(text, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
    }
}
