package com.example.gymtrack.feature.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.feature.editor.components.EDITOR_BACK_TEST_TAG
import com.example.gymtrack.feature.editor.components.EDITOR_FLAG_SEGMENT_TEST_TAG
import com.example.gymtrack.feature.editor.components.EditorAccessoryBar
import com.example.gymtrack.feature.editor.components.EditorMenuPage
import com.example.gymtrack.feature.editor.components.NOTE_EDITOR_FINISH_ACTION_TEST_TAG
import com.example.gymtrack.feature.editor.components.editorFlagOptionTestTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Editor v2 accessory bar (EDITOR_V2 §4): Back · flag segment · ⋯ · Finish, all in the thumb zone. */
class NoteEditorControlVisibilityTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun activeWorkoutShowsFinishDisabledUntilASetIsLogged() {
        setBar(showFinish = true, finishEnabled = false)
        composeTestRule.onNodeWithTag(NOTE_EDITOR_FINISH_ACTION_TEST_TAG).assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithTag(EDITOR_BACK_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun activeWorkoutWithASetEnablesFinish() {
        setBar(showFinish = true, finishEnabled = true)
        composeTestRule.onNodeWithTag(NOTE_EDITOR_FINISH_ACTION_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun historicalWorkoutHasNoFinish() {
        setBar(showFinish = false, finishEnabled = true)
        composeTestRule.onAllNodesWithTag(NOTE_EDITOR_FINISH_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithTag(EDITOR_BACK_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun flagSegmentIsHiddenWithoutACurrentExercise() {
        setBar(showFinish = true, finishEnabled = true, flag = null)
        composeTestRule.onAllNodesWithTag(EDITOR_FLAG_SEGMENT_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun flagSegmentSelectsInOneTap() {
        var picked: ExerciseFlag? = null
        composeTestRule.setContent {
            MaterialTheme {
                var flag by remember { mutableStateOf(ExerciseFlag.BILATERAL) }
                Bar(showFinish = true, finishEnabled = true, flag = flag, onFlag = { flag = it; picked = it })
            }
        }
        composeTestRule.onNodeWithTag(editorFlagOptionTestTag(ExerciseFlag.BILATERAL)).assertIsSelected()
        composeTestRule.onNodeWithTag(editorFlagOptionTestTag(ExerciseFlag.SUPERSET)).performClick()
        composeTestRule.onNodeWithTag(editorFlagOptionTestTag(ExerciseFlag.SUPERSET)).assertIsSelected()
        assertEquals(ExerciseFlag.SUPERSET, picked)
    }

    private fun setBar(showFinish: Boolean, finishEnabled: Boolean, flag: ExerciseFlag? = ExerciseFlag.BILATERAL) {
        composeTestRule.setContent {
            MaterialTheme { Bar(showFinish, finishEnabled, flag, onFlag = {}) }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Bar(showFinish: Boolean, finishEnabled: Boolean, flag: ExerciseFlag?, onFlag: (ExerciseFlag) -> Unit) {
        EditorAccessoryBar(
            onBack = {},
            currentFlag = flag,
            onFlagSelected = onFlag,
            menuPage = EditorMenuPage.CLOSED,
            onMenuPageChange = {},
            timer = null,
            onNotes = {},
            categories = listOf(Category("Push", 0xFFFF3B30)),
            onCategorySelected = {},
            hintsEnabled = true,
            onHintsToggle = {},
            showFinish = showFinish,
            finishEnabled = finishEnabled,
            onFinish = {},
        )
    }
}
