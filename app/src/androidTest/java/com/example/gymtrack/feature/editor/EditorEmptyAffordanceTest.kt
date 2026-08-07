package com.example.gymtrack.feature.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.gymtrack.feature.editor.components.EDITOR_INPUT_FRAME_TEST_TAG
import com.example.gymtrack.feature.editor.components.NotebookInputFrame
import org.junit.Rule
import org.junit.Test

class EditorEmptyAffordanceTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyInputUsesSubtleNotebookFieldInsteadOfInstructionCard() {
        composeTestRule.setContent {
            MaterialTheme {
                NotebookInputFrame(
                    isFocused = false,
                    placeholder = "Exercise",
                )
            }
        }

        composeTestRule.onNodeWithTag(EDITOR_INPUT_FRAME_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Exercise").assertIsDisplayed()
    }

    @Test
    fun focusedNotebookFieldRemainsVisibleForCaretContext() {
        composeTestRule.setContent {
            MaterialTheme {
                NotebookInputFrame(
                    isFocused = true,
                    placeholder = "Exercise",
                )
            }
        }

        composeTestRule.onNodeWithTag(EDITOR_INPUT_FRAME_TEST_TAG).assertIsDisplayed()
    }
}
