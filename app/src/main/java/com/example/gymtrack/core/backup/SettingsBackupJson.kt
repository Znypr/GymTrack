package com.example.gymtrack.core.backup

import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.ThemeChoice
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.data.WorkoutTemplate
import com.example.gymtrack.feature.suggest.TrainingGoal
import org.json.JSONObject

internal fun Settings.asBackupJson(): JSONObject = JSONObject()
    .put("is24Hour", is24Hour)
    .put("roundingSeconds", roundingSeconds)
    .put("themeChoice", themeChoice.storageValue)
    // Kept so older app versions (which require it) can still restore this backup.
    .put("darkMode", themeChoice.isDark(systemDark = true))
    .put("defaultWeightUnit", defaultWeightUnit.storageValue)
    // Optional since #312; older versions ignore it, older backups lack it (= no goal).
    .put("trainingGoal", trainingGoal.storageValue)
    .put("categories", categories.asJsonArray { value ->
        JSONObject().put("name", value.name).put("color", value.color)
    })
    // Optional since #314; older versions ignore it, older backups lack it (= no templates).
    .put("templates", templates.asJsonArray { t ->
        JSONObject().put("name", t.name).put("category", t.categoryName)
            .put("exercises", org.json.JSONArray(t.exercises))
    })

internal fun JSONObject.asBackupSettings(): Settings = Settings(
    is24Hour = getBoolean("is24Hour"),
    roundingSeconds = getInt("roundingSeconds"),
    // Optional since v2: old backups only have darkMode, which maps to Normal dark/light.
    themeChoice = ThemeChoice.fromStorage(
        value = optString("themeChoice", "").ifEmpty { null },
        legacyDarkMode = if (has("darkMode")) optBoolean("darkMode") else null,
    ),
    categories = getJSONArray("categories").mapJsonObjects { value ->
        Category(value.getString("name"), value.getLong("color"))
    },
    defaultWeightUnit = WeightUnit.fromStorage(optString("defaultWeightUnit", "KG")),
    trainingGoal = TrainingGoal.fromStorage(optString("trainingGoal", "").ifEmpty { null }),
    templates = optJSONArray("templates")?.mapJsonObjects { t ->
        val list = t.optJSONArray("exercises")
        val exercises = (0 until (list?.length() ?: 0)).map { list!!.optString(it).trim() }.filter { it.isNotEmpty() }
        WorkoutTemplate(t.optString("name").trim(), t.optString("category").trim(), exercises)
    }.orEmpty().filter { it.name.isNotEmpty() && it.exercises.isNotEmpty() },
)