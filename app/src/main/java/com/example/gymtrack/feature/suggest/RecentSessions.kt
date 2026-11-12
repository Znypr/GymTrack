package com.example.gymtrack.feature.suggest

import com.example.gymtrack.core.data.ExerciseRecentEntity
import com.example.gymtrack.core.util.ExerciseIdentityResolver

/*
 * Adapter from the derived `exercise_recent` rows (#307) to the pure engine input. Kept apart
 * from the engine so SuggestionEngine / GoalPolicy stay free of app and Room types.
 */

internal fun ExerciseRecentEntity.toPastSession(): PastSession {
    val rowUnit = LoadUnit.fromLabel(topWeightUnit) ?: LoadUnit.KG
    return PastSession(workoutId, parseSetsSummary(setsSummary, rowUnit))
}

/**
 * The editor's snapshot: sessions per stored exercise name, newest first, only workouts that
 * started before [beforeTimestamp] (the workout being edited is never its own history, and an
 * edited old workout does not look at later ones). Typed names resolve like the parser does.
 */
fun buildSuggestionHistory(
    rowsByName: Map<String, List<ExerciseRecentEntity>>,
    beforeTimestamp: Long,
): SuggestionHistory {
    val byName = rowsByName.mapValues { (_, rows) ->
        rows.filter { it.workoutId < beforeTimestamp }
            .sortedByDescending { it.workoutId }
            .map { it.toPastSession() }
    }.filterValues { it.isNotEmpty() }
    return SuggestionHistory(byName) { typed ->
        val canonical = runCatching { ExerciseIdentityResolver.resolve(rawName = typed).canonicalName.trim() }.getOrNull()
        listOfNotNull(canonical?.takeIf(String::isNotEmpty), typed.trim()).distinct()
    }
}
