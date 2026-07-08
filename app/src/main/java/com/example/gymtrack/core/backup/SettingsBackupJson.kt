package com.example.gymtrack.core.backup

import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.HomeCardMetric
import com.example.gymtrack.core.data.HomeOverviewWidget
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.data.WorkoutIntensityFormula
import org.json.JSONObject

internal fun Settings.asBackupJson(): JSONObject = JSONObject()
    .put("is24Hour", is24Hour)
    .put("roundingSeconds", roundingSeconds)
    .put("darkMode", darkMode)
    .put("defaultWeightUnit", defaultWeightUnit.storageValue)
    .put("homeCardMetric", homeCardMetric.storageValue)
    .put("workoutIntensityFormula", workoutIntensityFormula.storageValue)
    .put("homeOverviewWidget", homeOverviewWidget.storageValue)
    .put("categories", categories.asJsonArray { value ->
        JSONObject().put("name", value.name).put("color", value.color)
    })

internal fun JSONObject.asBackupSettings(): Settings = Settings(
    is24Hour = getBoolean("is24Hour"),
    roundingSeconds = getInt("roundingSeconds"),
    darkMode = getBoolean("darkMode"),
    categories = getJSONArray("categories").mapJsonObjects { value ->
        Category(value.getString("name"), value.getLong("color"))
    },
    defaultWeightUnit = WeightUnit.fromStorage(optString("defaultWeightUnit", "KG")),
    homeCardMetric = HomeCardMetric.fromStorage(optString("homeCardMetric", "SETS_PER_MINUTE")),
    workoutIntensityFormula = WorkoutIntensityFormula.fromStorage(optString("workoutIntensityFormula", "SET_DENSITY")),
    homeOverviewWidget = HomeOverviewWidget.fromStorage(optString("homeOverviewWidget", "LAST_WORKOUT")),
)
