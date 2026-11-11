package com.example.gymtrack.feature.editor

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.DEFAULT_CATEGORIES
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.NotebookTokens
import com.example.gymtrack.feature.editor.components.EDITOR_TOP_LINE_CATEGORY_TEST_TAG
import com.example.gymtrack.feature.editor.components.EditorTopLine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** #296 on screen: a workout whose category was removed keeps its stored name, never "Push". */
class EditorCategoryUiTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun unknownCategoryShowsItsStoredNameNotPush() {
        val category = EditorCategory().apply { loadStored("Old split", 0xFF123456) }
        rule.setContent {
            GymTrackTheme(tokens = NotebookTokens) {
                EditorTopLine(
                    category = categoryDisplay(category.name, listOf(Category("Pull", 1L))),
                    timestamp = 0L,
                    timer = null,
                    onLongPress = {},
                )
            }
        }
        rule.onNodeWithTag(EDITOR_TOP_LINE_CATEGORY_TEST_TAG).assertTextEquals("Old split")
        rule.onAllNodesWithText("Push").assertCountEquals(0)
        // Nothing on screen wrote back to the stored values.
        assertEquals("Old split", category.name)
        assertEquals(0xFF123456, category.color)
    }

    @Test
    fun newWorkoutShowsTodaysChoice() {
        val category = EditorCategory().apply { startNew(DEFAULT_CATEGORIES, chosenOnToday = "Legs") }
        rule.setContent {
            GymTrackTheme(tokens = NotebookTokens) {
                EditorTopLine(
                    category = categoryDisplay(category.name, DEFAULT_CATEGORIES),
                    timestamp = 0L,
                    timer = null,
                    onLongPress = {},
                )
            }
        }
        rule.onNodeWithTag(EDITOR_TOP_LINE_CATEGORY_TEST_TAG).assertTextEquals("Legs")
    }
}
