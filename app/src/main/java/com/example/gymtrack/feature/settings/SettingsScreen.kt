package com.example.gymtrack.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.ThemeChoice
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.ui.components.AppBottomSheet
import com.example.gymtrack.core.ui.components.AppCard
import com.example.gymtrack.core.ui.components.AppIcon
import com.example.gymtrack.core.ui.components.AppIconView
import com.example.gymtrack.core.ui.components.CategoryKind
import com.example.gymtrack.core.ui.components.CategoryMark
import com.example.gymtrack.core.ui.components.PrimaryButton
import com.example.gymtrack.core.ui.components.activeMark
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.util.presetColors
import com.example.gymtrack.feature.about.AboutScreen
import com.example.gymtrack.feature.help.HelpScreen
import com.example.gymtrack.feature.help.sendFeedback
import com.example.gymtrack.feature.suggest.GoalOptions

const val SETTINGS_ADD_CATEGORY_TAG = "settings-add-category"
const val SETTINGS_RESTORE_DEFAULTS_TAG = "settings-restore-default-categories"
const val SETTINGS_HELP_TAG = "settings-help"
const val SETTINGS_ABOUT_TAG = "settings-about"
fun settingsCategoryTestTag(name: String): String = "settings-category-$name"

private enum class SettingsPage { MAIN, HELP, ABOUT }

/**
 * Settings (wave 2 redesign), grouped: Preferences, Categories (#296), Data, Help & feedback,
 * About & legal. One hand: the top is a read-only title, every action is a full-width row,
 * nothing sits top-right. Back = system back ([onBack] is kept for the old route and unused).
 *
 * @param onExportCsv shows "Export workouts (CSV)" under Data when set.
 * @param onOpenHelp / [onOpenAbout] open those screens as routes; when null, Settings shows them
 *   itself (system back returns here).
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SettingsScreen(
    settings: Settings,
    onUpdate: (Settings) -> Unit,
    onBack: () -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    dataOperationInProgress: Boolean,
    onExportCsv: (() -> Unit)? = null,
    onOpenHelp: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    BackHandler(enabled = page != SettingsPage.MAIN) { page = SettingsPage.MAIN }
    when (page) {
        SettingsPage.HELP -> HelpScreen()
        SettingsPage.ABOUT -> AboutScreen()
        SettingsPage.MAIN -> SettingsMain(
            settings = settings,
            onUpdate = onUpdate,
            onCreateBackup = onCreateBackup,
            onRestoreBackup = onRestoreBackup,
            dataOperationInProgress = dataOperationInProgress,
            onExportCsv = onExportCsv,
            onOpenHelp = onOpenHelp ?: { page = SettingsPage.HELP },
            onOpenAbout = onOpenAbout ?: { page = SettingsPage.ABOUT },
        )
    }
}

/** Editing state of the category sheet: [original] null = a new category. */
private data class CategoryDraft(val original: Category?)

@Composable
private fun SettingsMain(
    settings: Settings,
    onUpdate: (Settings) -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    dataOperationInProgress: Boolean,
    onExportCsv: (() -> Unit)?,
    onOpenHelp: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val tokens = GymTrackTheme.tokens
    val context = LocalContext.current
    var draft by remember { mutableStateOf<CategoryDraft?>(null) }
    fun update(block: Settings.() -> Settings) = onUpdate(settings.block())

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(tokens.colors.bg).padding(horizontal = tokens.spacing.md),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xxs),
    ) {
        item { ScreenHeader("Settings") }

        item {
            SettingsGroup("Preferences") {
                ChoiceBlock(
                    title = "Weight unit",
                    note = "Used when a set has no kg/lb. Saved workouts are never changed.",
                ) {
                    SegmentedChoice(
                        options = WeightUnit.values().map { it.displayLabel },
                        selectedIndex = WeightUnit.values().indexOf(settings.defaultWeightUnit),
                        onSelect = { i -> update { copy(defaultWeightUnit = WeightUnit.values()[i]) } },
                        contentDescriptions = listOf("Kilograms", "Pounds"),
                    )
                }
                RowDivider(11)
                ChoiceBlock(title = "Clock") {
                    SegmentedChoice(
                        options = listOf("24 h", "12 h"),
                        selectedIndex = if (settings.is24Hour) 0 else 1,
                        onSelect = { i -> update { copy(is24Hour = i == 0) } },
                        contentDescriptions = listOf("24-hour clock", "12-hour clock"),
                    )
                }
                RowDivider(12)
                ThemeChoiceBlock(settings.themeChoice) { choice -> update { copy(themeChoice = choice) } }
            }
        }

        item {
            SettingsGroup("Training goal") {
                InfoText("Off by default. Suggestions from your history work without a goal.")
                GoalOptions(
                    selected = settings.trainingGoal,
                    onSelect = { goal -> update { copy(trainingGoal = goal) } },
                )
            }
        }

        item {
            SettingsGroup("Categories") {
                InfoText("Tap one to rename, recolour or remove it. Saved workouts keep their category name.")
                settings.categories.forEach { category ->
                    RowDivider(category.name.hashCode())
                    CategoryRow(category) { draft = CategoryDraft(category) }
                }
                RowDivider(13)
                NavRow(
                    title = "Add category",
                    icon = AppIcon.ADD,
                    showChevron = false,
                    onClick = { draft = CategoryDraft(null) },
                    modifier = Modifier.testTag(SETTINGS_ADD_CATEGORY_TAG),
                )
                val missing = CategoryEditing.missingDefaults(settings.categories)
                if (missing.isNotEmpty()) {
                    RowDivider(14)
                    NavRow(
                        title = "Restore ${missing.joinToString(" · ") { it.name }}",
                        subtitle = "Adds the default categories back",
                        icon = AppIcon.ADD,
                        showChevron = false,
                        onClick = { update { copy(categories = CategoryEditing.restoreDefaults(categories)) } },
                        modifier = Modifier.testTag(SETTINGS_RESTORE_DEFAULTS_TAG),
                    )
                }
            }
        }

        item {
            SettingsGroup("Data") {
                NavRow(
                    title = "Back up all data",
                    subtitle = "One ZnyprFit backup file, saved where you choose",
                    enabled = !dataOperationInProgress,
                    onClick = onCreateBackup,
                )
                RowDivider(21)
                NavRow(
                    title = "Restore from backup",
                    subtitle = "Checks the file, then replaces the data on this phone",
                    enabled = !dataOperationInProgress,
                    onClick = onRestoreBackup,
                )
                if (onExportCsv != null) {
                    RowDivider(22)
                    NavRow(
                        title = "Export workouts (CSV)",
                        subtitle = "For spreadsheets and other apps",
                        enabled = !dataOperationInProgress,
                        onClick = onExportCsv,
                    )
                }
                if (dataOperationInProgress) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = tokens.colors.action,
                        )
                        Spacer(Modifier.width(tokens.spacing.sm))
                        Text("Working…", style = tokens.type.body, color = tokens.colors.ink)
                    }
                }
            }
        }

        item {
            SettingsGroup("Help & feedback") {
                NavRow(
                    "Help & FAQ",
                    subtitle = "How to type sets, common questions",
                    onClick = onOpenHelp,
                    modifier = Modifier.testTag(SETTINGS_HELP_TAG),
                )
                RowDivider(31)
                NavRow(
                    "Send feedback",
                    subtitle = "Opens your email app. No workout data is sent.",
                    showChevron = false,
                    onClick = { sendFeedback(context) },
                )
            }
        }

        item {
            SettingsGroup("About & legal") {
                NavRow(
                    "About & legal",
                    subtitle = "Version, privacy policy, Impressum, licences",
                    onClick = onOpenAbout,
                    modifier = Modifier.testTag(SETTINGS_ABOUT_TAG),
                )
            }
        }

        item { Spacer(Modifier.height(tokens.spacing.xl)) }
    }

    draft?.let { current ->
        CategorySheet(
            original = current.original,
            all = settings.categories,
            onDismiss = { draft = null },
            onSave = { name, color ->
                val list = current.original
                    ?.let { CategoryEditing.update(settings.categories, it, name, color) }
                    ?: CategoryEditing.add(settings.categories, name, color)
                update { copy(categories = list) }
                draft = null
            },
            onRemove = { category ->
                update { copy(categories = CategoryEditing.remove(categories, category)) }
                draft = null
            },
        )
    }
}

@Composable
private fun ChoiceBlock(title: String, note: String? = null, content: @Composable () -> Unit) {
    val tokens = GymTrackTheme.tokens
    Column(Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs)) {
        Text(title, style = tokens.type.body, color = tokens.colors.ink, fontWeight = if (tokens.isNotebook) null else FontWeight.Medium)
        if (note != null) Text(note, style = tokens.type.small, color = tokens.colors.inkMuted)
        Spacer(Modifier.height(tokens.spacing.xxs))
        content()
    }
}

private val THEME_OPTIONS = listOf(
    ThemeChoice.NORMAL_DARK to "Black OLED background",
    ThemeChoice.NORMAL_LIGHT to "Light background",
    ThemeChoice.SYSTEM to "Normal, light or dark like your phone",
    ThemeChoice.NOTEBOOK to "Hand-drawn paper and pencil",
)

/** Theme picker (#333, built in #363): one selectable row per choice; selected = check + mark. */
@Composable
private fun ThemeChoiceBlock(selected: ThemeChoice, onSelected: (ThemeChoice) -> Unit) {
    val tokens = GymTrackTheme.tokens
    Column(Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs)) {
        Text("Theme", style = tokens.type.body, color = tokens.colors.ink, fontWeight = if (tokens.isNotebook) null else FontWeight.Medium)
        Column(Modifier.selectableGroup()) {
            THEME_OPTIONS.forEachIndexed { index, (choice, description) ->
                val isSelected = choice == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelected(choice) })
                        .padding(vertical = tokens.spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (isSelected) AppIconView(AppIcon.CHECK, contentDescription = null, tint = tokens.colors.ink, size = 20.dp)
                    }
                    Spacer(Modifier.width(tokens.spacing.sm))
                    Column(Modifier.activeMark(isSelected, tokens, seed = index * 13 + 1).padding(horizontal = tokens.spacing.xxs)) {
                        Text(
                            choice.label,
                            style = tokens.type.body,
                            color = tokens.colors.ink,
                            fontWeight = if (isSelected && !tokens.isNotebook) FontWeight.SemiBold else null,
                        )
                        Text(description, style = tokens.type.small, color = tokens.colors.inkMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: Category, onClick: () -> Unit) {
    val tokens = GymTrackTheme.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = "Edit ${category.name}", onClick = onClick)
            .testTag(settingsCategoryTestTag(category.name)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryMark(
            kind = CategoryKind.fromName(category.name),
            label = category.name,
            size = 14.dp,
            customColor = Color(category.color),
        )
        Spacer(Modifier.width(tokens.spacing.sm))
        Text(category.name, style = tokens.type.body, color = tokens.colors.ink, modifier = Modifier.weight(1f))
        Text("Edit", style = tokens.type.small, color = tokens.colors.inkMuted)
        AppIconView(AppIcon.CHEVRON, contentDescription = null, tint = tokens.colors.inkMuted, size = 20.dp)
    }
}

/** Add / edit sheet. Save and Remove sit at the bottom (thumb zone). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(
    original: Category?,
    all: List<Category>,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
    onRemove: (Category) -> Unit,
) {
    val tokens = GymTrackTheme.tokens
    var name by rememberSaveable { mutableStateOf(original?.name ?: "") }
    var color by rememberSaveable { mutableStateOf(original?.color ?: presetColors.first()) }
    val problem = CategoryEditing.nameProblem(all, name, except = original)
    val colors = remember(original) { (listOfNotNull(original?.color) + presetColors).distinct() }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = if (original == null) "New category" else "Edit ${original.name}",
            style = tokens.type.title,
            color = tokens.colors.ink,
        )
        Spacer(Modifier.height(tokens.spacing.sm))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(CategoryEditing.MAX_NAME_LENGTH + 1) },
            label = { Text("Name") },
            singleLine = true,
            isError = name.isNotEmpty() && problem != null,
            supportingText = {
                if (name.isNotEmpty() && problem != null) Text(problem)
                else if (original != null) Text("Workouts saved as \"${original.name}\" keep that name.")
            },
            modifier = Modifier.fillMaxWidth().testTag("settings-category-name"),
        )
        Spacer(Modifier.height(tokens.spacing.xs))
        Text("Colour", style = tokens.type.small, color = tokens.colors.inkMuted)
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xxs),
        ) {
            colors.forEachIndexed { index, value ->
                val isSelected = value == color
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = tokens.spacing.touchTarget)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { color = value })
                        .semantics { contentDescription = "Colour ${index + 1}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.activeMark(isSelected, tokens, seed = index * 7 + 2).padding(tokens.spacing.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        CategoryMark(CategoryKind.OTHER, label = "Colour ${index + 1}", size = 20.dp, customColor = Color(value))
                    }
                }
            }
        }
        Spacer(Modifier.height(tokens.spacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = tokens.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (original != null) {
                val canRemove = CategoryEditing.canRemove(all)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .alpha(if (canRemove) 1f else 0.4f)
                        .clickable(enabled = canRemove, role = Role.Button) { onRemove(original) }
                        .testTag("settings-category-remove"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (canRemove) "Remove" else "Keep one category",
                        style = tokens.type.body,
                        // Not red: red means Push and errors only (README rule 5).
                        color = tokens.colors.ink,
                    )
                }
            }
            PrimaryButton(
                text = "Save",
                onClick = { onSave(name, color) },
                enabled = problem == null,
                icon = AppIcon.CHECK,
                modifier = Modifier.weight(1f).testTag("settings-category-save"),
            )
        }
    }
}

/** A random saturated colour (ARGB as Long), kept for callers of the v1 settings screen. */
fun randomColor(): Long {
    val hue = (0..360).random().toFloat()
    return Color.hsv(hue, 0.8f, 0.9f).toArgb().toLong() and 0xFFFFFFFF
}
