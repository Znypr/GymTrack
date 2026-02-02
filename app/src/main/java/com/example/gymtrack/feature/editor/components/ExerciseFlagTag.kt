package com.example.gymtrack.feature.editor.components

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
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.ui.theme.SupersetBlue

@Composable
fun ExerciseFlagTag(
    flag: ExerciseFlag,
    relColor: Color,
    modifier: Modifier = Modifier
) {
    val color = when (flag) {
        ExerciseFlag.UNILATERAL -> MaterialTheme.colorScheme.error
        ExerciseFlag.SUPERSET -> SupersetBlue
        ExerciseFlag.BILATERAL -> relColor.copy(alpha = 0.5f)
    }

    OutlinedButton(
        onClick = {},
        enabled = false,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            disabledContentColor = color,
        ),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.defaultMinSize(minWidth = 28.dp, minHeight = 15.dp)
    ) {
        val text = when (flag) {
            ExerciseFlag.BILATERAL -> "1x"
            ExerciseFlag.UNILATERAL -> "2x"
            ExerciseFlag.SUPERSET -> "2x"
        }
        Text(
            text,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}