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
    val fallbackColor = MaterialTheme.colorScheme.primary
    val baseColor = if (selectedCategory != null) Color(selectedCategory.color) else fallbackColor
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val mutedTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val blendGradient = remember(baseColor, backgroundColor) {
        Brush.verticalGradient(listOf(baseColor.copy(alpha = 0.30f), backgroundColor))
    }

    var menuExpanded by remember { mutableStateOf(false) }
    val date = Date(timestamp)
    val weekdayFormat = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat(if (settings.is24Hour) "HH:mm" else "hh:mm a", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .background(blendGradient)
            .padding(top = topPadding, start = 18.dp, end = 18.dp, bottom = 18.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        weekdayFormat.format(date),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor,
                    )
                    Text(
                        timeFormat.format(date),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = mutedTextColor,
                    )
                }
                Text(
                    dateFormat.format(date),
                    style = MaterialTheme.typography.titleMedium,
                    color = mutedTextColor,
                )
            }

            Spacer(Modifier.height(14.dp))

            Box {
                Surface(
                    color = surfaceColor.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.clickable { menuExpanded = true },
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(baseColor))
                        Spacer(Modifier.width(9.dp))
                        Text(
                            selectedCategory?.name ?: "Select Category",
                            color = textColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = mutedTextColor)
                    }
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                ) {
                    settings.categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name, color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(Color(cat.color))) },
                            onClick = { onCategorySelect(cat); menuExpanded = false },
                        )
                    }
                }
            }
        }
    }
}
