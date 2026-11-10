package com.example.gymtrack.core.data

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
    private val DEFAULT_WEIGHT_UNIT = stringPreferencesKey("default_weight_unit")
    private val THEME_CHOICE = stringPreferencesKey("theme_choice")

    suspend fun load(context: Context): Settings {
        val prefs = context.settingsDataStore.data.first()
        val custom = prefs[CATEGORIES]?.takeIf { it.isNotEmpty() }?.split("|")?.map {
            val parts = it.split(":")
            val name = parts.getOrNull(0) ?: "Category"
            val color = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            Category(name, color)
        } ?: emptyList()
        val cats = (custom + DEFAULT_CATEGORIES).distinctBy { it.name }

        // Missing keys fall back to the model defaults, so there is one default per setting.
        val defaults = Settings()
        return Settings(
            is24Hour = prefs[IS_24_HOUR] ?: defaults.is24Hour,
            roundingSeconds = prefs[ROUNDING_SECONDS] ?: defaults.roundingSeconds,
            // v2 migration: no stored theme yet → the old dark-mode switch picks Normal dark/light.
            themeChoice = ThemeChoice.fromStorage(prefs[THEME_CHOICE], legacyDarkMode = prefs[DARK_MODE]),
            categories = cats,
            defaultWeightUnit = WeightUnit.fromStorage(prefs[DEFAULT_WEIGHT_UNIT]),
        )
    }

    suspend fun save(context: Context, settings: Settings) {
        context.settingsDataStore.edit { prefs ->
            val custom = settings.categories.filter { it.name !in DEFAULT_CATEGORY_NAMES }
            prefs[IS_24_HOUR] = settings.is24Hour
            prefs[ROUNDING_SECONDS] = settings.roundingSeconds
            prefs[THEME_CHOICE] = settings.themeChoice.storageValue
            // Still written so an older app version installed over this one keeps a sane look.
            prefs[DARK_MODE] = settings.themeChoice.isDark(systemDark = true)
            prefs[CATEGORIES] = custom.joinToString("|") { "${it.name}:${it.color}" }
            prefs[DEFAULT_WEIGHT_UNIT] = settings.defaultWeightUnit.storageValue
        }
    }
}