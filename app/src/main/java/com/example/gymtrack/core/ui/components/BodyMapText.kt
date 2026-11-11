package com.example.gymtrack.core.ui.components

import com.example.gymtrack.core.muscle.Muscle
import com.example.gymtrack.core.muscle.MuscleLevel
import java.util.Locale

/*
 * Words for the body map (#311): the TalkBack description and the visible text list. The map
 * never carries meaning by colour or pattern alone; these say the same thing in words.
 */

/** Level words, shared by the legend and the text list ("Low 1–4" etc. in the legend). */
fun MuscleLevel.word(): String = when (this) {
    MuscleLevel.NONE -> "None"
    MuscleLevel.LOW -> "Low"
    MuscleLevel.MODERATE -> "Moderate"
    MuscleLevel.HIGH -> "High"
    MuscleLevel.UNMAPPED -> "Not mapped"
}

/** Legend caption with the weekly-set thresholds (ANATOMY.md: none 0 / low 1–4 / moderate 5–9 / high 10+). */
fun MuscleLevel.legendCaption(): String = when (this) {
    MuscleLevel.NONE -> "None"
    MuscleLevel.LOW -> "Low 1–4"
    MuscleLevel.MODERATE -> "Moderate 5–9"
    MuscleLevel.HIGH -> "High 10+"
    MuscleLevel.UNMAPPED -> "Not mapped"
}

/** Regions shown in [view], in [Muscle] order. */
fun bodyMapMuscles(view: BodyMapView): List<Muscle> {
    val shown = BodyMapGeometry.figures(view).flatMapTo(HashSet()) { it.muscles }
    return Muscle.entries.filter { it in shown }
}

/** The regions of [view] at each level, highest first; levels with no region are left out. */
fun bodyMapGroups(levels: Map<Muscle, MuscleLevel>, view: BodyMapView): List<Pair<MuscleLevel, List<Muscle>>> {
    val muscles = bodyMapMuscles(view)
    return listOf(MuscleLevel.HIGH, MuscleLevel.MODERATE, MuscleLevel.LOW, MuscleLevel.NONE, MuscleLevel.UNMAPPED)
        .map { level -> level to muscles.filter { (levels[it] ?: MuscleLevel.NONE) == level } }
        .filter { it.second.isNotEmpty() }
}

/**
 * TalkBack description of the map, e.g. "Muscle map, front and back. High: Upper chest, Quads.
 * Low: Biceps. Not mapped: Soleus. All other regions none."
 */
fun bodyMapDescription(levels: Map<Muscle, MuscleLevel>, view: BodyMapView): String {
    val where = when (view) {
        BodyMapView.FRONT -> "front view"
        BodyMapView.BACK -> "back view"
        BodyMapView.BOTH -> "front and back"
    }
    val groups = bodyMapGroups(levels, view).toMap()
    val shown = bodyMapMuscles(view)
    if (shown.all { (levels[it] ?: MuscleLevel.NONE) == MuscleLevel.UNMAPPED }) {
        return "Muscle map, $where. No exercises are mapped to muscles yet."
    }
    val parts = listOf(MuscleLevel.HIGH, MuscleLevel.MODERATE, MuscleLevel.LOW, MuscleLevel.UNMAPPED).mapNotNull { level ->
        groups[level]?.let { list -> "${level.word()}: ${list.joinToString { it.label }}." }
    }
    val trained = listOf(MuscleLevel.HIGH, MuscleLevel.MODERATE, MuscleLevel.LOW).any { groups[it] != null }
    val tail = if (trained) "All other regions none." else "No regions trained."
    return (listOf("Muscle map, $where.") + parts + tail).joinToString(" ")
}

/** Fractional sets as people say them: `4`, `2.5`, `0.5`. */
fun formatSets(sets: Float): String {
    val rounded = Math.round(sets * 10f) / 10f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else String.format(Locale.ROOT, "%.1f", rounded)
}

/** "4 sets", "1 set", "2.5 sets". */
fun setsPhrase(sets: Float): String = formatSets(sets).let { if (it == "1") "1 set" else "$it sets" }
