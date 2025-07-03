package com.example.gymtrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gymtrack.data.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(
    categories: List<Category>,
    selected: Category?,
    onSelected: (Category?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Category",
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            modifier = modifier.menuAnchor().width(140.dp),
            readOnly = true,
            value = selected?.name ?: "None",
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = gymTrackOutlinedTextFieldColors(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(14.dp)
                                .background(Color(cat.color.toInt()))
                        )
                    },
                    onClick = {
                        onSelected(cat)
                        expanded = false
                    },
                )
            }
        }
    }
}
