package com.example.gymtrack.feature.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.gymtrack.feature.editor.components.EDITOR_EMPTY_AFFORDANCE_TEST_TAG
import com.example.gymtrack.feature.editor.components.StarterInputAffordance
import org.junit.Rule
import org.junit.Test

class EditorEmptyAffordanceTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyInputAffordanceExplainsFirstStep() {
        composeTestRule.setContent {
            MaterialTheme {
                StarterInputAffordance(isFocused = false)
            }
        }

        composeTestRule.onNodeWithTag(EDITOR_EMPTY_AFFORDANCE_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Start your workout").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap here and type your first exercise").assertIsDisplayed()
        composeTestRule.onNodeWithText("Press Enter after each exercise or set.").assertIsDisplayed()
    }

    @Test
    fun focusedInputAffordanceKeepsCursorContextVisible() {
        composeTestRule.setContent {
            MaterialTheme {
                StarterInputAffordance(isFocused = true)
            }
        }

        composeTestRule.onNodeWithTag(EDITOR_EMPTY_AFFORDANCE_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("First exercise").assertIsDisplayed()
        composeTestRule.onNodeWithText("Press Enter after each exercise or set.").assertIsDisplayed()
    }
}
