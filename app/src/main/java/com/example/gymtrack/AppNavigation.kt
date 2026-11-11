package com.example.gymtrack

import androidx.compose.foundation.clickable
import com.example.gymtrack.core.data.Settings
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.ui.components.ActiveWorkoutBar
import com.example.gymtrack.core.ui.components.AppDivider
import com.example.gymtrack.core.ui.components.AppIcon
import com.example.gymtrack.core.ui.components.AppIconView
import com.example.gymtrack.core.ui.components.BottomNavBar
import com.example.gymtrack.core.ui.components.NavItem
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.feature.home.ActiveWorkoutInfo
import com.example.gymtrack.core.time.AppClock
import com.example.gymtrack.core.timer.NoteTimerState
import com.example.gymtrack.feature.home.categoryLook
import com.example.gymtrack.feature.home.formatElapsedClock
import com.example.gymtrack.feature.home.shortName

/**
 * Routes (DESIGN_LANGUAGE_V2 "New navigation"). Tabs: Today (start) · History · Progress.
 * The editor, Settings and the old Stats route are full screen without the bottom nav.
 * Back from History or Progress returns to Today; Back on Today leaves the app.
 */
object AppRoutes {
    const val TODAY = "today"
    const val HISTORY = "history"

    /** Progress tab: the redesigned ProgressRoute (Settings row at its end). */
    const val PROGRESS = "progress"
    const val EDITOR = "editor?noteId={noteId}"
    const val SETTINGS = "settings"

    /** Kept for old links; Progress shows the same screen. */
    const val STATS = "stats"

    val TABS = listOf(TODAY, HISTORY, PROGRESS)

    fun editor(noteId: Long): String = "editor?noteId=$noteId"
}

const val APP_BOTTOM_NAV_TEST_TAG = "app-bottom-nav"
const val ACTIVE_WORKOUT_BAR_TEST_TAG = "active-workout-bar"
const val PROGRESS_OPEN_SETTINGS_TEST_TAG = "progress-open-settings"

internal val TAB_ITEMS = listOf(
    NavItem("Today", AppIcon.TODAY),
    NavItem("History", AppIcon.HISTORY),
    NavItem("Progress", AppIcon.PROGRESS),
)

/**
 * The bottom of every tab, in the thumb zone: the active-workout bar (#293) while a workout runs,
 * then the bottom navigation.
 */
@Composable
internal fun TabChrome(
    selectedRoute: String?,
    active: ActiveWorkoutInfo?,
    timer: NoteTimerState,
    settings: Settings,
    onResume: (Long) -> Unit,
    onSelectTab: (String) -> Unit,
) {
    val tokens = GymTrackTheme.tokens
    Column(Modifier.fillMaxWidth()) {
        if (active != null) {
            // Ticks here only, so the once-a-second redraw stays inside the bar.
            var nowMillis by remember { mutableLongStateOf(AppClock.nowMillis()) }
            LaunchedEffect(active.timestamp, active.isRunning) {
                nowMillis = AppClock.nowMillis()
                while (active.isRunning) {
                    delay(1_000L)
                    nowMillis = AppClock.nowMillis()
                }
            }
            val elapsed = formatElapsedClock(timer.elapsedSeconds(nowMillis))
            val (kind, custom) = active.note.categoryLook(settings)
            ActiveWorkoutBar(
                category = kind,
                title = active.note.shortName(),
                elapsed = elapsed,
                onResume = { onResume(active.timestamp) },
                customColor = custom,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.xs, vertical = tokens.spacing.xxs)
                    .testTag(ACTIVE_WORKOUT_BAR_TEST_TAG),
            )
        }
        BottomNavBar(
            items = TAB_ITEMS,
            selectedIndex = AppRoutes.TABS.indexOf(selectedRoute).coerceAtLeast(0),
            onSelect = { index -> onSelectTab(AppRoutes.TABS[index]) },
            modifier = Modifier.testTag(APP_BOTTOM_NAV_TEST_TAG),
        )
    }
}

/** Progress placeholder's last row: Settings lives at the end of the Progress tab. */
@Composable
internal fun SettingsEntryRow(onClick: () -> Unit) {
    val tokens = GymTrackTheme.tokens
    Column(Modifier.fillMaxWidth()) {
        AppDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClickLabel = "Open settings", onClick = onClick)
                .testTag(PROGRESS_OPEN_SETTINGS_TEST_TAG)
                .padding(horizontal = tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconView(AppIcon.SETTINGS, contentDescription = null, tint = tokens.colors.ink)
            Spacer(Modifier.width(tokens.spacing.sm))
            Text("Settings", style = tokens.type.body, color = tokens.colors.ink, modifier = Modifier.weight(1f))
            AppIconView(AppIcon.CHEVRON, contentDescription = null, tint = tokens.colors.inkMuted, size = 20.dp)
        }
    }
}
