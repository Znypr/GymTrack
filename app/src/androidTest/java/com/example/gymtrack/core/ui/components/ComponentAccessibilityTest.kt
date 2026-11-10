package com.example.gymtrack.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.GymTrackTokens
import com.example.gymtrack.core.ui.theme.NormalDarkTokens
import com.example.gymtrack.core.ui.theme.NotebookTokens
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Touch targets ≥48 dp and spoken labels, in both skins. */
class ComponentAccessibilityTest {

    @get:Rule
    val rule = createComposeRule()

    private var fixes = 0
    private var selected = 0

    private fun render(tokens: GymTrackTokens) = rule.setContent {
        GymTrackTheme(tokens = tokens) {
            Column {
                HintRow("800 kg looks like a typo", onFix = { fixes++ }, onKeep = {})
                GhostPill("8 × 80", onClick = {})
                PrimaryButton("Start Pull", onClick = {})
                CategoryMark(CategoryKind.PULL)
                BottomNavBar(
                    items = listOf(NavItem("Today", AppIcon.TODAY), NavItem("History", AppIcon.HISTORY), NavItem("Progress", AppIcon.PROGRESS)),
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )
            }
        }
    }

    @Test
    fun normalTargetsAndLabels() = check(NormalDarkTokens)

    @Test
    fun notebookTargetsAndLabels() = check(NotebookTokens)

    private fun check(tokens: GymTrackTokens) {
        render(tokens)
        rule.onNodeWithText("Fix").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        assertEquals(1, fixes)
        rule.onNodeWithText("Keep").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("8 × 80", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Start Pull").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithContentDescription("Pull category").assertExists()
        rule.onNodeWithText("Today").assertIsSelected()
        rule.onNodeWithText("History").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, selected)
    }
}
