package com.example.gymtrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gymtrack.util.presetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorDropdown(
    selected: Long,
    modifier: Modifier = Modifier,
    label: String = "Color",
    onSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            readOnly = true,
            value = String.format("#%06X", 0xFFFFFF and selected.toInt()),
            onValueChange = {},
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.background,
                unfocusedBorderColor = MaterialTheme.colorScheme.background,
                cursorColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = modifier.menuAnchor(),
            label = { Text(label) },
            leadingIcon = {
                Box(
                    Modifier
                        .size(16.dp)
                        .background(Color(selected.toInt()))
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presetColors.forEach { color ->
                DropdownMenuItem(
                    text = {
                        Box(
                            Modifier
                                .size(24.dp)
                                .background(Color(color.toInt()))
                        )
                    },
                    onClick = { onSelected(color); expanded = false },
                )
            }
        }
    }
}
