package com.example.gymtrack.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun gymTrackOutlinedTextFieldColors(
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.background,
): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = borderColor,
    unfocusedBorderColor = borderColor,
    focusedContainerColor = containerColor,
    unfocusedContainerColor = containerColor,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.onSurface,
)
