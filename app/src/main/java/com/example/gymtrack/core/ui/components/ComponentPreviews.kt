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
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.GymTrackTokens
import com.example.gymtrack.core.ui.theme.NormalDarkTokens
import com.example.gymtrack.core.ui.theme.NormalLightTokens
import com.example.gymtrack.core.ui.theme.NotebookTokens
import com.example.gymtrack.core.ui.theme.notebookPaper

/** Every preview below renders once per theme: Normal dark, Normal light, Notebook. */
class ThemeTokensProvider : PreviewParameterProvider<GymTrackTokens> {
    override val values = sequenceOf(NormalDarkTokens, NormalLightTokens, NotebookTokens)
}

@Composable
private fun PreviewPage(tokens: GymTrackTokens, content: @Composable () -> Unit) {
    GymTrackTheme(tokens = tokens) {
        val c = tokens.colors
        Column(
            Modifier
                .fillMaxWidth()
                .notebookPaper(paper = c.bg, rule = c.paperRule, margin = c.paperMargin, marginX = if (tokens.isNotebook) 44.dp else null)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Preview(name = "InfoLine", widthDp = 360)
@Composable
private fun InfoLinePreview(@PreviewParameter(ThemeTokensProvider::class) tokens: GymTrackTokens) = PreviewPage(tokens) {
    InfoLine(listOf("Mon 10 Nov", "Push", "24:10"), leading = { CategoryMark(CategoryKind.PUSH) })
}

@Preview(name = "CategoryMark", widthDp = 360)
@Composable
private fun CategoryMarkPreview(@PreviewParameter(ThemeTokensProvider::class) tokens: GymTrackTokens) = PreviewPage(tokens) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CategoryKind.entries.forEach { CategoryMark(it, showLabel = true, size = 14.dp) }
    }
}

@Preview(name = "PrimaryButton", widthDp = 360)
@Composable
private fun PrimaryButtonPreview(@PreviewParameter(ThemeTokensProvider::class) tokens: GymTrackTokens) = PreviewPage(tokens) {
    PrimaryButton("Start Pull", onClick = {}, modifier = Modifier.fillMaxWidth(), icon = AppIcon.RESUME)
    PrimaryButton("Finish", onClick = {}, enabled = false)
}

@Preview(name = "GhostPill", widthDp = 360)
@Composable
private fun GhostPillPreview(@PreviewParameter(ThemeTokensProvider::class) tokens: GymTrackTokens) = PreviewPage(tokens) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostPill("8 × 80", onClick = {})
        GhostPill("Lat Pulldown", onClick = {})
    }
}

@Preview(name = "HintRow", widthDp = 360)
@Composable
private fun HintRowPreview(@PreviewParameter(ThemeTokensProvider::class) tokens: GymTrackTokens) = PreviewPage(tokens) {
    HintRow("800 kg looks like a typo", onFix = {}, onKeep = {})
}

@Preview(name = "AppCard", widthDp = 360)
@Composable
private fun AppCardPreview(@PreviewParameter(ThemeTokensProvider::class) tokens: GymTrackTokens) = PreviewPage(tokens) {
    AppCard(seed = 3) {
        InfoLine(listOf("Next up"))
        Text("Pull · usually Tue/Fri", style = tokens.type.title, color = tokens.colors.ink)
        Text("last 5 days ago", style = tokens.type.small, color = tokens.colors.inkMuted)
    }
    AppCard(active = true, seed = 4, onClick = {}) {
        Text("Bench Press", style = tokens.type.body, color = tokens.colors.ink)
        Text("8 × 80", style = tokens.type.setNumber, color = tokens.colors.ink, modifier = Modifier.activeMark(true, tokens, seed = 9))
    }
    AppDivider()
}

@Preview(name = "ActiveWorkoutBar", widthDp = 360)
@Composable
private fun ActiveWorkoutBarPreview(@PreviewParameter(ThemeTokensProvider::class) tokens: GymTrackTokens) = PreviewPage(tokens) {
    ActiveWorkoutBar(CategoryKind.PUSH, title = "Push", elapsed = "24:10", onResume = {})
}

@Preview(name = "BottomNavBar", widthDp = 360)
@Composable
private fun BottomNavBarPreview(@PreviewParameter(ThemeTokensProvider::class) tokens: GymTrackTokens) = PreviewPage(tokens) {
    BottomNavBar(
        items = listOf(
            NavItem("Today", AppIcon.TODAY),
            NavItem("History", AppIcon.HISTORY),
            NavItem("Progress", AppIcon.PROGRESS),
        ),
        selectedIndex = 0,
        onSelect = {},
    )
}

@Preview(name = "AppIcons", widthDp = 360)
@Composable
private fun AppIconsPreview(@PreviewParameter(ThemeTokensProvider::class) tokens: GymTrackTokens) = PreviewPage(tokens) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppIcon.entries.forEach { AppIconView(it, contentDescription = it.name) }
    }
}

/** The modal sheet only renders in interactive preview mode (it is a window). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Preview(name = "AppBottomSheet", widthDp = 360, heightDp = 400)
@Composable
private fun AppBottomSheetPreview(@PreviewParameter(ThemeTokensProvider::class) tokens: GymTrackTokens) = PreviewPage(tokens) {
    AppBottomSheet(onDismissRequest = {}) {
        Text("Finish workout?", style = tokens.type.title, color = tokens.colors.ink)
        PrimaryButton("Finish", onClick = {}, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
    }
}
