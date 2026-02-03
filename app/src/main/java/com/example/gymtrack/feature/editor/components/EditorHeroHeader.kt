package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EditorHeroHeader(
    timestamp: Long,
    settings: Settings,
    selectedCategory: Category?,
    onCategorySelect: (Category) -> Unit,
    topPadding: Dp
) {
    // [FIX] Use the actual color from the selected category object
    val baseColor = if (selectedCategory != null) Color(selectedCategory.color) else Color(0xFF666666)

    val backgroundColor = MaterialTheme.colorScheme.background
    // Create gradient fading to background
    val blendGradient = remember(baseColor, backgroundColor) {
        Brush.verticalGradient(listOf(baseColor.copy(alpha = 0.25f), backgroundColor))
    }

    var menuExpanded by remember { mutableStateOf(false) }
    val date = Date(timestamp)
    val weekdayFormat = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat(if (settings.is24Hour) "HH:mm" else "hh:mm a", Locale.getDefault()) }
    val textColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .background(blendGradient)
            .padding(top = topPadding, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(weekdayFormat.format(date), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = textColor)
                    Text(timeFormat.format(date), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(dateFormat.format(date), style = MaterialTheme.typography.titleMedium, color = textColor.copy(alpha = 0.8f))
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            // Category Selector
            Box {
                Surface(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { menuExpanded = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(baseColor))
                        Spacer(Modifier.width(8.dp))
                        Text(selectedCategory?.name ?: "Select Category", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    settings.categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name, color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(Color(cat.color))) },
                            onClick = { onCategorySelect(cat); menuExpanded = false }
                        )
                    }
                }
            }
        }
    }
}