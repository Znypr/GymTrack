package com.example.gymtrack.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.Settings

// Matches Home Screen Card Color
private val CardDeepDark = Color(0xFF181818)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onUpdate: (Settings) -> Unit,
    onBack: () -> Unit
) {
    fun update(block: Settings.() -> Settings) {
        onUpdate(settings.block())
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onSurface

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = textColor
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- PREFERENCES ---
            item {
                SettingsSectionTitle("Preferences")
                SettingsCard {
                    SettingsSwitchRow(
                        title = "Dark Mode",
                        checked = settings.darkMode,
                        onCheckedChange = { update { copy(darkMode = it) } }
                    )
                    Divider(color = textColor.copy(alpha = 0.1f))

                    SettingsSwitchRow(
                        title = "24-Hour Time Format",
                        checked = settings.is24Hour,
                        onCheckedChange = { update { copy(is24Hour = it) } }
                    )
                    Divider(color = textColor.copy(alpha = 0.1f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val opts = listOf(1, 5, 10, 30, 60)
                                val next = opts[(opts.indexOf(settings.roundingSeconds) + 1) % opts.size]
                                update { copy(roundingSeconds = next) }
                            }
                            .padding(16.dp)
                    ) {
                        Text("Rounding Seconds", color = textColor, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${settings.roundingSeconds} seconds",
                            color = textColor.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // --- CATEGORIES ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsSectionTitle("Workout Categories")
                    IconButton(
                        onClick = {
                            val newCat = Category("New", (0xFF000000..0xFFFFFFFF).random())
                            update { copy(categories = categories + newCat) }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            items(settings.categories) { cat ->
                CategoryRow(
                    category = cat,
                    onDelete = {
                        if (settings.categories.size > 1) {
                            update { copy(categories = categories - cat) }
                        }
                    },
                    textColor = textColor
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// --- COMPONENTS ---

@Composable
fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    // Logic: If background is dark, use DeepDark (181818). If light, use Surface (White).
    val isDark = MaterialTheme.colorScheme.background.run { red < 0.5 && green < 0.5 && blue < 0.5 }
    val cardColor = if (isDark) CardDeepDark else MaterialTheme.colorScheme.surface

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if(isDark) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun SettingsSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun CategoryRow(category: Category, onDelete: () -> Unit, textColor: Color) {
    val isDark = MaterialTheme.colorScheme.background.run { red < 0.5 && green < 0.5 && blue < 0.5 }
    val cardColor = if (isDark) CardDeepDark else MaterialTheme.colorScheme.surface

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if(isDark) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(category.color)))
                Spacer(Modifier.width(16.dp))
                Text(category.name, fontWeight = FontWeight.Bold, color = textColor, fontSize = 16.sp)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
            }
        }
    }
}