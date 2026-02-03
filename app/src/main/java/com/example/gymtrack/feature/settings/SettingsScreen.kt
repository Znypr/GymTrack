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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.Settings

private val CardDeepDark = Color(0xFF181818)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onUpdate: (Settings) -> Unit,
    onBack: () -> Unit
) {
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    fun update(block: Settings.() -> Settings) {
        onUpdate(settings.block())
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onSurface

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, fontSize = 24.sp) },
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
                    SettingsSwitchRow("Dark Mode", settings.darkMode) { update { copy(darkMode = it) } }
                    Divider(color = textColor.copy(alpha = 0.1f))
                    SettingsSwitchRow("24-Hour Time", settings.is24Hour) { update { copy(is24Hour = it) } }
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
                            val newCat = Category("New Category", randomColor())
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
                    textColor = textColor,
                    onEdit = { editingCategory = cat },
                    onDelete = {
                        if (settings.categories.size > 1) {
                            update { copy(categories = categories - cat) }
                        }
                    }
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }

        if (editingCategory != null) {
            EditCategoryDialog(
                category = editingCategory!!,
                onDismiss = { editingCategory = null },
                onSave = { updatedCat ->
                    // Replace the old category with the new one
                    val newList = settings.categories.map {
                        if (it == editingCategory) updatedCat else it
                    }
                    update { copy(categories = newList) }
                    editingCategory = null
                }
            )
        }
    }
}

// --- COMPONENTS ---

@Composable
fun EditCategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit
) {
    var name by remember { mutableStateOf(category.name) }
    var color by remember { mutableStateOf(category.color) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Category") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Show color preview
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(color)))
                    Spacer(Modifier.width(16.dp))
                    Button(onClick = { color = randomColor() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("New Color")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(category.copy(name = name, color = color)) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// [FIXED] Correctly returns a Long compatible with Compose Color(Long)
fun randomColor(): Long {
    val hue = (0..360).random().toFloat()
    // 1. Generate HSV color
    // 2. Convert to ARGB Int (which might be negative)
    // 3. Convert to Long and Mask with 0xFFFFFFFF to make it a valid positive Unsigned Long
    return Color.hsv(hue, 0.8f, 0.9f).toArgb().toLong() and 0xFFFFFFFF
}

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
    val isDark = MaterialTheme.colorScheme.background.run { red < 0.5 && green < 0.5 && blue < 0.5 }
    val cardColor = if (isDark) CardDeepDark else MaterialTheme.colorScheme.surface
    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(if(isDark) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
fun SettingsSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun CategoryRow(
    category: Category,
    textColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.run { red < 0.5 && green < 0.5 && blue < 0.5 }
    val cardColor = if (isDark) CardDeepDark else MaterialTheme.colorScheme.surface

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onEdit() },
        elevation = CardDefaults.cardElevation(if(isDark) 0.dp else 2.dp)
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