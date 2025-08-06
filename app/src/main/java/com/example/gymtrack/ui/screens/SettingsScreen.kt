package com.example.gymtrack.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.R
import com.example.gymtrack.BuildConfig
import com.example.gymtrack.data.Category
import com.example.gymtrack.data.Settings
import com.example.gymtrack.data.DEFAULT_CATEGORIES
import com.example.gymtrack.data.DEFAULT_CATEGORY_NAMES
import com.example.gymtrack.ui.components.ColorDropdown
import com.example.gymtrack.util.presetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: Settings, onChange: (Settings) -> Unit, onBack: () -> Unit) {
    var is24 by remember { mutableStateOf(settings.is24Hour) }
    var rounding by remember { mutableStateOf(settings.roundingSeconds.toString()) }
    var dark by remember { mutableStateOf(settings.darkMode) }
    val defaultCategories = DEFAULT_CATEGORIES
    var customCategories by remember {
        mutableStateOf(
            settings.categories.filter { it.name !in DEFAULT_CATEGORY_NAMES }.toMutableList()
        )
    }
    var newName by remember { mutableStateOf("") }
    var newColor by remember { mutableStateOf(presetColors.first()) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontSize = 20.sp,
                    )
                },
                navigationIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_gymtrack_logo),
                        contentDescription = "GymTrack logo",
                        modifier = Modifier.size(45.dp) // optional size
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("24-hour format", modifier = Modifier.weight(1f))
                Switch(checked = is24, onCheckedChange = {
                    is24 = it
                    onChange(
                        settings.copy(
                            is24Hour = it,
                            roundingSeconds = rounding.toIntOrNull() ?: settings.roundingSeconds,
                            darkMode = dark,
                            categories = defaultCategories + customCategories,
                        ),
                    )
                })
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dark mode", modifier = Modifier.weight(1f))
                Switch(checked = dark, onCheckedChange = {
                    dark = it
                    onChange(
                        settings.copy(
                            is24Hour = is24,
                            roundingSeconds = rounding.toIntOrNull() ?: settings.roundingSeconds,
                            darkMode = it,
                            categories = defaultCategories + customCategories,
                        ),
                    )
                })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = rounding,
                onValueChange = {
                    val filtered = it.filter { ch -> ch.isDigit() }
                    rounding = filtered
                    onChange(
                        settings.copy(
                            is24Hour = is24,
                            roundingSeconds = filtered.toIntOrNull() ?: settings.roundingSeconds,
                            darkMode = dark,
                            categories = defaultCategories + customCategories,
                        ),
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.background,
                    unfocusedBorderColor = MaterialTheme.colorScheme.background,
                    cursorColor = MaterialTheme.colorScheme.onSurface,
                ),
                label = { Text("Rounding seconds") },
            )
            Spacer(Modifier.height(16.dp))
            Text("Categories", fontWeight = FontWeight.Bold)
            defaultCategories.forEach { cat ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cat.name, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(65.dp)
                            .background(Color(cat.color.toInt()))
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            customCategories.forEachIndexed { index, cat ->
                var name by remember { mutableStateOf(cat.name) }
                var colorValue by remember { mutableStateOf(cat.color) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            customCategories = customCategories.toMutableList().also { list ->
                                list[index] = list[index].copy(name = it)
                            }
                            onChange(
                                settings.copy(
                                    is24Hour = is24,
                                    roundingSeconds = rounding.toIntOrNull()
                                        ?: settings.roundingSeconds,
                                    darkMode = dark,
                                    categories = defaultCategories + customCategories,
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.background,
                            unfocusedBorderColor = MaterialTheme.colorScheme.background,
                            cursorColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    Spacer(Modifier.width(4.dp))
                    ColorDropdown(
                        selected = colorValue,
                        modifier = Modifier
                            .width(100.dp)
                            .height(65.dp),
                        onSelected = { clr ->
                            colorValue = clr
                            customCategories = customCategories.toMutableList().also { list ->
                                list[index] = list[index].copy(color = clr)
                            }
                            onChange(
                                settings.copy(
                                    is24Hour = is24,
                                    roundingSeconds = rounding.toIntOrNull()
                                        ?: settings.roundingSeconds,
                                    darkMode = dark,
                                    categories = defaultCategories + customCategories,
                                ),
                            )
                        },
                    )
                    IconButton(onClick = {
                        customCategories = customCategories.toMutableList().also { it.removeAt(index) }
                        onChange(
                            settings.copy(
                                is24Hour = is24,
                                roundingSeconds = rounding.toIntOrNull()
                                    ?: settings.roundingSeconds,
                                darkMode = dark,
                                categories = defaultCategories + customCategories,
                            ),
                        )
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("New category") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.background,
                        unfocusedBorderColor = MaterialTheme.colorScheme.background,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                Spacer(Modifier.width(4.dp))
                ColorDropdown(
                    selected = newColor,
                    modifier = Modifier
                        .width(100.dp)
                        .height(65.dp),
                    onSelected = { newColor = it },
                )
            IconButton(onClick = {
                customCategories = (customCategories + Category(
                    newName.ifBlank { "Category" },
                    newColor,
                )).toMutableList()
                onChange(
                    settings.copy(
                        is24Hour = is24,
                        roundingSeconds = rounding.toIntOrNull() ?: settings.roundingSeconds,
                        darkMode = dark,
                        categories = defaultCategories + customCategories,
                    ),
                )
                newName = ""
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
}
