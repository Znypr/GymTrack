package com.example.gymtrack.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.muscle.Muscle
import com.example.gymtrack.core.muscle.MuscleExerciseSets
import com.example.gymtrack.core.muscle.MuscleLevel
import com.example.gymtrack.core.muscle.MuscleWeek
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.GymTrackTokens
import com.example.gymtrack.core.ui.theme.notebookPaper

/*
 * Body map previews (#311): every level state, unmapped, highlight and the three views, each in
 * Normal dark, Normal light and Notebook (ThemeTokensProvider). These are the visual review
 * set; Roborazzi/Paparazzi are not in the build.
 */

/** The artwork's demo levels (Anatomy.dc.html AN_DEMO): every level plus one unmapped region. */
internal val BodyMapDemoLevels: Map<Muscle, MuscleLevel> = Muscle.entries.associateWith { MuscleLevel.NONE } + mapOf(
    Muscle.CHEST_UPPER to MuscleLevel.HIGH,
    Muscle.CHEST_LOWER to MuscleLevel.HIGH,
    Muscle.DELT_FRONT to MuscleLevel.MODERATE,
    Muscle.DELT_SIDE to MuscleLevel.MODERATE,
    Muscle.TRICEPS to MuscleLevel.LOW,
    Muscle.LATS to MuscleLevel.MODERATE,
    Muscle.RHOMBOIDS_TERES to MuscleLevel.MODERATE,
    Muscle.TRAPS_MID_LOWER to MuscleLevel.LOW,
    Muscle.BICEPS to MuscleLevel.LOW,
    Muscle.QUADS to MuscleLevel.HIGH,
    Muscle.GLUTE_MAX to MuscleLevel.MODERATE,
    Muscle.HAMSTRINGS to MuscleLevel.LOW,
    Muscle.SOLEUS to MuscleLevel.UNMAPPED,
)

/** A demo week behind [BodyMapDemoLevels], for sheet and card previews. */
internal val BodyMapDemoWeek: MuscleWeek = run {
    val sets = mapOf(
        Muscle.CHEST_UPPER to 10f, Muscle.CHEST_LOWER to 12f, Muscle.DELT_FRONT to 6f, Muscle.DELT_SIDE to 6f,
        Muscle.TRICEPS to 4f, Muscle.LATS to 8f, Muscle.RHOMBOIDS_TERES to 6f, Muscle.TRAPS_MID_LOWER to 3f,
        Muscle.BICEPS to 3f, Muscle.QUADS to 11f, Muscle.GLUTE_MAX to 7.5f, Muscle.HAMSTRINGS to 2.5f,
    )
    MuscleWeek(
        levels = BodyMapDemoLevels,
        sets = sets,
        exercises = mapOf(
            Muscle.QUADS to listOf(MuscleExerciseSets("Back Squat", 5f), MuscleExerciseSets("Leg Press", 4f), MuscleExerciseSets("Leg Extension", 2f)),
            Muscle.CHEST_LOWER to listOf(MuscleExerciseSets("Bench Press", 8f), MuscleExerciseSets("Dips", 4f)),
        ),
    )
}

@Composable
private fun MapPage(tokens: GymTrackTokens, content: @Composable () -> Unit) {
    GymTrackTheme(tokens = tokens) {
        val c = tokens.colors
        Column(
            Modifier
                .fillMaxWidth()
                .notebookPaper(paper = c.bg, rule = c.paperRule, margin = c.paperMargin, marginX = null)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Preview(name = "BodyMap · all levels", widthDp = 360)
@Composable
private fun BodyMapLevelsPreview(@PreviewParameter(ThemeTokensProvider::class) tokens: GymTrackTokens) = MapPage(tokens) {
    BodyMap(BodyMapDemoLevels)
    BodyMapLegend(showUnmapped = true)
    BodyMapLevelList(BodyMapDemoLevels)
}

@Preview(name = "BodyMap · none", widthDp = 360)
@Composable
private fun BodyMapNonePreview(@PreviewParameter(ThemeTokensProvider::class) tokens: GymTrackTokens) = MapPage(tokens) {
    BodyMap(Muscle.entries.associateWith { MuscleLevel.NONE })
}

@Preview(name = "BodyMap · all unmapped", widthDp = 360)
@Composable
private fun BodyMapUnmappedPreview(@PreviewParameter(ThemeTokensProvider::class) tokens: GymTrackTokens) = MapPage(tokens) {
    BodyMap(MuscleWeek.unmapped.levels)
    BodyMapLegend(showUnmapped = true)
}

@Preview(name = "BodyMap · every level once", widthDp = 360)
@Composable
private fun BodyMapEachLevelPreview(@PreviewParameter(ThemeTokensProvider::class) tokens: GymTrackTokens) = MapPage(tokens) {
    val levels = MuscleLevel.entries
    BodyMap(Muscle.entries.withIndex().associate { (i, m) -> m to levels[i % levels.size] })
}

@Preview(name = "BodyMap · front, highlight", widthDp = 360)
@Composable
private fun BodyMapFrontPreview(@PreviewParameter(ThemeTokensProvider::class) tokens: GymTrackTokens) = MapPage(tokens) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BodyMap(BodyMapDemoLevels, Modifier.weight(1f), view = BodyMapView.FRONT, highlight = Muscle.QUADS)
        BodyMap(BodyMapDemoLevels, Modifier.weight(1f), view = BodyMapView.BACK, highlight = Muscle.HAMSTRINGS)
    }
    Text("Highlight: Quads (front), Hamstrings (back)", style = GymTrackTheme.tokens.type.small, color = GymTrackTheme.tokens.colors.inkMuted)
}

@Preview(name = "BodyMap · small regions high", widthDp = 360)
@Composable
private fun BodyMapSmallRegionsPreview(@PreviewParameter(ThemeTokensProvider::class) tokens: GymTrackTokens) = MapPage(tokens) {
    BodyMap(
        Muscle.entries.associateWith { if (it in BODY_MAP_SMALL_REGIONS) MuscleLevel.HIGH else MuscleLevel.NONE },
        view = BodyMapView.FRONT,
        highlight = Muscle.BRACHIALIS,
    )
}
