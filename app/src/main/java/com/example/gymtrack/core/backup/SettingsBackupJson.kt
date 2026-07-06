package com.example.gymtrack.core.backup

import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.Settings
import org.json.JSONObject

internal fun Settings.asBackupJson(): JSONObject = JSONObject()
    .put("is24Hour", is24Hour)
    .put("roundingSeconds", roundingSeconds)
    .put("darkMode", darkMode)
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
)
