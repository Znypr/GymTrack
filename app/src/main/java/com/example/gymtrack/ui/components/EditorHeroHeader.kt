package com.example.gymtrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.gymtrack.data.Category
import com.example.gymtrack.data.Settings
import com.example.gymtrack.ui.theme.DefaultGradient
import com.example.gymtrack.ui.theme.LegsGradient
import com.example.gymtrack.ui.theme.PullGradient
import com.example.gymtrack.ui.theme.PushGradient
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
    val baseColor = when (selectedCategory?.name?.lowercase()) {
        "push" -> PushGradient.first()
        "pull" -> PullGradient.first()
        "legs" -> LegsGradient.first()
        else -> DefaultGradient.first()
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    val startColor = baseColor.copy(alpha = 0.25f)

    val blendGradient = remember(baseColor, backgroundColor) {
        Brush.verticalGradient(
            colors = listOf(startColor, backgroundColor)
        )
    }

    var menuExpanded by remember { mutableStateOf(false) }

    // Date Formatters
    val weekdayFormat = remember { SimpleDateFormat("EEEE", Locale.getDefault()) } // "Monday"
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val timeFormat = remember {
        SimpleDateFormat(
            if (settings.is24Hour) "HH:mm" else "hh:mm a",
            Locale.getDefault()
        )
    }

    val textColor = MaterialTheme.colorScheme.onSurface

    val date = Date(timestamp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor) // Ensure solid base
            .background(blendGradient)   // Apply the fading gradient
            .padding(top = topPadding, start = 20.dp, end = 20.dp, bottom = 24.dp)
    ) {
        Column {
            // [CHANGE] New Layout: Weekday Big, Date & Time Small
            Text(
                text = weekdayFormat.format(date),
                style = MaterialTheme.typography.displaySmall, // Much Bigger
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Time (Bold)
                Text(
                    text = timeFormat.format(date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )

                Text(
                    text = "  •  ",
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor.copy(alpha = 0.6f)
                )

                // Date
                Text(
                    text = dateFormat.format(date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = textColor.copy(alpha = 0.9f)
                )
            }

            Spacer(Modifier.height(20.dp))

            // Category Pill (Unchanged)
            Box {
                Surface(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { menuExpanded = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(selectedCategory?.color ?: 0xFFFFFFFF))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = selectedCategory?.name ?: "Select Category",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
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

