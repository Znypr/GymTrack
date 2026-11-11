package com.example.gymtrack.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gymtrack.feature.suggest.TrainingGoal
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore("settings")

object SettingsStore {
    private val IS_24_HOUR = booleanPreferencesKey("is_24_hour")
    private val ROUNDING_SECONDS = intPreferencesKey("rounding_seconds")
    private val DARK_MODE = booleanPreferencesKey("dark_mode")
    /** v1: user-made categories only; defaults were always re-added. Still written for downgrades. */
    private val CATEGORIES = stringPreferencesKey("categories")
    /** v2 (#296): the full ordered list, defaults included, so removed defaults stay removed. */
    private val CATEGORY_LIST = stringPreferencesKey("category_list_v2")
    private val DEFAULT_WEIGHT_UNIT = stringPreferencesKey("default_weight_unit")
    private val THEME_CHOICE = stringPreferencesKey("theme_choice")
    /** #312: optional; missing = no goal. */
    private val TRAINING_GOAL = stringPreferencesKey("training_goal")

    suspend fun load(context: Context): Settings {
        val prefs = context.settingsDataStore.data.first()
        val cats = CategoryPrefs.decode(stored = prefs[CATEGORY_LIST], legacy = prefs[CATEGORIES])

        // Missing keys fall back to the model defaults, so there is one default per setting.
        val defaults = Settings()
        return Settings(
            is24Hour = prefs[IS_24_HOUR] ?: defaults.is24Hour,
            roundingSeconds = prefs[ROUNDING_SECONDS] ?: defaults.roundingSeconds,
            // v2 migration: no stored theme yet → the old dark-mode switch picks Normal dark/light.
            themeChoice = ThemeChoice.fromStorage(prefs[THEME_CHOICE], legacyDarkMode = prefs[DARK_MODE]),
            categories = cats,
            defaultWeightUnit = WeightUnit.fromStorage(prefs[DEFAULT_WEIGHT_UNIT]),
            trainingGoal = TrainingGoal.fromStorage(prefs[TRAINING_GOAL]),
        )
    }

    suspend fun save(context: Context, settings: Settings) {
        context.settingsDataStore.edit { prefs ->
            prefs[IS_24_HOUR] = settings.is24Hour
            prefs[ROUNDING_SECONDS] = settings.roundingSeconds
            prefs[THEME_CHOICE] = settings.themeChoice.storageValue
            // Still written so an older app version installed over this one keeps a sane look.
            prefs[DARK_MODE] = settings.themeChoice.isDark(systemDark = true)
            prefs[CATEGORY_LIST] = CategoryPrefs.encode(settings.categories)
            prefs[CATEGORIES] = CategoryPrefs.encodeLegacy(settings.categories)
            prefs[DEFAULT_WEIGHT_UNIT] = settings.defaultWeightUnit.storageValue
            prefs[TRAINING_GOAL] = settings.trainingGoal.storageValue
        }
    }
}

/**
 * Category list persistence (#296), pure so it is unit-tested.
 *
 * - The v2 value holds the whole ordered list, defaults included. A removed default stays
 *   removed and a renamed default does not come back under its old name.
 * - Without a v2 value (an install from before #296), the v1 value is read the old way:
 *   user categories followed by any missing default.
 * - Names are kept exactly as typed, including names the app does not know. Workouts store
 *   their category name themselves, so editing this list never changes a saved workout.
 */
internal object CategoryPrefs {
    private const val ITEM = '|'
    private const val FIELD = ':'

    fun encode(categories: List<Category>): String =
        categories.distinctBy { it.name }.joinToString(ITEM.toString()) { "${escape(it.name)}$FIELD${it.color}" }

    /** v1 format for older app versions: user-made categories only, names unescaped. */
    fun encodeLegacy(categories: List<Category>): String =
        categories.filter { it.name !in DEFAULT_CATEGORY_NAMES }
            .joinToString(ITEM.toString()) { "${it.name.replace(ITEM, ' ').replace(FIELD, ' ')}$FIELD${it.color}" }

    fun decode(stored: String?, legacy: String?): List<Category> {
        if (stored != null) {
            return if (stored.isEmpty()) emptyList() else stored.split(ITEM).map(::decodeItem).distinctBy { it.name }
        }
        val custom = legacy?.takeIf { it.isNotEmpty() }?.split(ITEM)?.map {
            val parts = it.split(FIELD)
            Category(parts.getOrNull(0) ?: "Category", parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        }.orEmpty()
        return (custom + DEFAULT_CATEGORIES).distinctBy { it.name }
    }

    private fun decodeItem(item: String): Category {
        val cut = item.lastIndexOf(FIELD)
        if (cut < 0) return Category(unescape(item), 0L)
        return Category(unescape(item.substring(0, cut)), item.substring(cut + 1).toLongOrNull() ?: 0L)
    }

    private fun escape(name: String): String = buildString {
        for (ch in name) {
            when (ch) {
                '%' -> append("%25")
                ITEM -> append("%7C")
                FIELD -> append("%3A")
                else -> append(ch)
            }
        }
    }

    private fun unescape(text: String): String =
        text.replace("%7C", ITEM.toString()).replace("%3A", FIELD.toString()).replace("%25", "%")
}
