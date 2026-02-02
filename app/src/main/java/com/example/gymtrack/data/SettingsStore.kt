package com.example.gymtrack.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore("settings")

object SettingsStore {
    private val IS_24_HOUR = booleanPreferencesKey("is_24_hour")
    private val ROUNDING_SECONDS = intPreferencesKey("rounding_seconds")
    private val DARK_MODE = booleanPreferencesKey("dark_mode")
    private val CATEGORIES = stringPreferencesKey("categories")

    suspend fun load(context: Context): Settings {
        val prefs = context.settingsDataStore.data.first()
        val custom = prefs[CATEGORIES]?.takeIf { it.isNotEmpty() }?.split("|")?.map {
            val parts = it.split(":")
            val name = parts.getOrNull(0) ?: "Category"
            val color = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            Category(name, color)
        } ?: emptyList()
        val cats = (custom + DEFAULT_CATEGORIES).distinctBy { it.name }

        return Settings(
            is24Hour = prefs[IS_24_HOUR] ?: true,
            roundingSeconds = prefs[ROUNDING_SECONDS] ?: 5,
            darkMode = prefs[DARK_MODE] ?: false,
            categories = cats
        )
    }

    suspend fun save(context: Context, settings: Settings) {
        context.settingsDataStore.edit { prefs ->
            val custom = settings.categories.filter { it.name !in DEFAULT_CATEGORY_NAMES }
            prefs[IS_24_HOUR] = settings.is24Hour
            prefs[ROUNDING_SECONDS] = settings.roundingSeconds
            prefs[DARK_MODE] = settings.darkMode
            prefs[CATEGORIES] = custom.joinToString("|") { "${it.name}:${it.color}" }
        }
    }
}

