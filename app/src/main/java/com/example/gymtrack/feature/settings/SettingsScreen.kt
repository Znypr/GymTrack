package com.example.gymtrack.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import com.example.gymtrack.core.data.HomeCardMetric
import com.example.gymtrack.core.data.HomeOverviewWidget
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.data.WorkoutIntensityFormula

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onUpdate: (Settings) -> Unit,
    onBack: () -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    dataOperationInProgress: Boolean,
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
                title = { Text("Settings", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = textColor,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SettingsSectionTitle("Preferences")
                SettingsCard {
                    SettingsSwitchRow("Dark Mode", settings.darkMode) { update { copy(darkMode = it) } }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    SettingsSwitchRow("24-Hour Time", settings.is24Hour) { update { copy(is24Hour = it) } }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    SettingsWeightUnitRow(
                        selected = settings.defaultWeightUnit,
                        onSelected = { unit -> update { copy(defaultWeightUnit = unit) } },
                    )
                }
            }

            item {
                SettingsSectionTitle("Home")
                SettingsCard {
                    SettingsChoiceRow(
                        title = "Workout card metric",
                        subtitle = "Controls the bottom-left metric shown on workout cards.",
                        options = HomeCardMetric.entries,
                        selected = settings.homeCardMetric,
                        label = { it.displayLabel },
                        onSelected = { metric -> update { copy(homeCardMetric = metric) } },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    SettingsChoiceRow(
                        title = "Intensity flames",
                        subtitle = settings.workoutIntensityFormula.description + "\n🔥 light · 🔥🔥 normal · 🔥🔥🔥 strong reference.",
                        options = WorkoutIntensityFormula.entries,
                        selected = settings.workoutIntensityFormula,
                        label = { it.displayLabel },
                        onSelected = { formula -> update { copy(workoutIntensityFormula = formula) } },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    SettingsChoiceRow(
                        title = "Default home overview",
                        subtitle = "Choose the top Home widget. No random cycling; this is deterministic.",
                        options = HomeOverviewWidget.entries,
                        selected = settings.homeOverviewWidget,
                        label = { it.displayLabel },
                        onSelected = { widget -> update { copy(homeOverviewWidget = widget) } },
                    )
                }
            }

            item {
                SettingsSectionTitle("Data Management")
                SettingsCard {
                    SettingsActionRow(
                        title = "Back up all data",
                        subtitle = "Create one portable GymTrack backup file",
                        enabled = !dataOperationInProgress,
                        onClick = onCreateBackup,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    SettingsActionRow(
                        title = "Restore from backup",
                        subtitle = "Replace local data after validating the file",
                        enabled = !dataOperationInProgress,
                        onClick = onRestoreBackup,
                    )
                    if (dataOperationInProgress) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Working…", color = textColor)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsSectionTitle("Workout Categories")
                    IconButton(
                        onClick = {
                            val newCat = Category("New Category", randomColor())
                            update { copy(categories = categories + newCat) }
                        },
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
                    },
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }

        if (editingCategory != null) {
            EditCategoryDialog(
                category = editingCategory!!,
                onDismiss = { editingCategory = null },
                onSave = { updatedCat ->
                    val newList = settings.categories.map {
                        if (it == editingCategory) updatedCat else it
                    }
                    update { copy(categories = newList) }
                    editingCategory = null
                },
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SettingsWeightUnitRow(
    selected: WeightUnit,
    onSelected: (WeightUnit) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "Default weight unit",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Used when new set lines omit kg/lb. Existing workouts are not reinterpreted.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeightUnit.values().forEach { unit ->
                FilterChip(
                    selected = selected == unit,
                    onClick = { onSelected(unit) },
                    label = { Text(unit.displayLabel) },
                )
            }
        }
    }
}

@Composable
private fun <T> SettingsChoiceRow(
    title: String,
    subtitle: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(2).forEach { rowOptions ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowOptions.forEach { option ->
                        FilterChip(
                            selected = option == selected,
                            onClick = { onSelected(option) },
                            label = { Text(label(option)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditCategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
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
                    singleLine = true,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
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
        },
    )
}

fun randomColor(): Long {
    val hue = (0..360).random().toFloat()
    return Color.hsv(hue, 0.62f, 0.78f).toArgb().toLong() and 0xFFFFFFFF
}

@Composable
fun SettingsSectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
fun SettingsSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun CategoryRow(
    category: Category,
    textColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onEdit() },
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(category.color)))
                Spacer(Modifier.width(16.dp))
                Text(category.name, fontWeight = FontWeight.Bold, color = textColor, fontSize = 16.sp)
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
            }
        }
    }
}
