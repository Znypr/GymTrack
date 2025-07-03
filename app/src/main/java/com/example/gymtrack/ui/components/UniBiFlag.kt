package com.example.gymtrack.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun UniBiFlag(
    isUni: Boolean,
    relColor: Color,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = {},
        enabled = false,
        border = BorderStroke(1.dp, if (isUni) MaterialTheme.colorScheme.error else relColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            disabledContentColor = if (isUni) MaterialTheme.colorScheme.error else relColor,
        ),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        modifier = modifier.height(28.dp)
    ) {
        Text(
            if (isUni) "2x" else "1x",
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
    }
}
