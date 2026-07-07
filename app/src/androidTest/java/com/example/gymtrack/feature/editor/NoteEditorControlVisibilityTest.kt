package com.example.gymtrack.feature.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class NoteEditorControlVisibilityTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun newWorkoutShowsTimerControlsAndFinish() {
        setEditorControls(isLastNote = true)

        assertWorkoutControlsAreVisible()
    }

    @Test
    fun activeWorkoutShowsTimerControlsAndFinish() {
        setEditorControls(isLastNote = true)

        assertWorkoutControlsAreVisible()
    }

    @Test
    fun completedWorkoutHidesTimerControlsAndFinish() {
        setEditorControls(isLastNote = false)

        assertWorkoutControlsDoNotExist()
    }

    @Test
    fun historicalWorkoutHidesTimerControlsAndFinish() {
        setEditorControls(isLastNote = false)

        assertWorkoutControlsDoNotExist()
    }

    private fun setEditorControls(isLastNote: Boolean) {
        composeTestRule.setContent {
            MaterialTheme {
                Column {
                    NoteEditorFinishAction(
                        isVisible = isLastNote,
                        onFinish = {},
                    )
                    NoteEditorTimerControls(
                        isVisible = isLastNote,
                        noteTimestamp = 1L,
                        startTimerOnOpen = false,
                        timerContent = { modifier ->
                            Box(modifier = modifier) {
                                Text("Timer controls")
                            }
                        },
                    )
                }
            }
        }
    }

    private fun assertWorkoutControlsAreVisible() {
        composeTestRule.onNodeWithTag(NOTE_EDITOR_FINISH_ACTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(NOTE_EDITOR_TIMER_CONTROLS_TEST_TAG).assertIsDisplayed()
    }

    private fun assertWorkoutControlsDoNotExist() {
        composeTestRule.onNodeWithTag(NOTE_EDITOR_FINISH_ACTION_TEST_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(NOTE_EDITOR_TIMER_CONTROLS_TEST_TAG).assertDoesNotExist()
    }
}
