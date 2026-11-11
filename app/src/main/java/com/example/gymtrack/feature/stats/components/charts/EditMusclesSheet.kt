package com.example.gymtrack.feature.stats.components.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.data.ExerciseMuscleMappingState
import com.example.gymtrack.core.muscle.Muscle
import com.example.gymtrack.core.muscle.MuscleMappingOrigin
import com.example.gymtrack.core.muscle.MuscleRole
import com.example.gymtrack.core.ui.components.AppBottomSheet

/** The 26 regions in checklist groups (top to bottom of the body). */
internal val MUSCLE_CHECKLIST_GROUPS: List<Pair<String, List<Muscle>>> = listOf(
    "Shoulders" to listOf(Muscle.DELT_FRONT, Muscle.DELT_SIDE, Muscle.DELT_REAR),
    "Chest" to listOf(Muscle.CHEST_UPPER, Muscle.CHEST_LOWER, Muscle.SERRATUS),
    "Back" to listOf(Muscle.TRAPS_UPPER, Muscle.TRAPS_MID_LOWER, Muscle.RHOMBOIDS_TERES, Muscle.LATS, Muscle.ERECTORS),
    "Arms" to listOf(Muscle.BICEPS, Muscle.BRACHIALIS, Muscle.TRICEPS, Muscle.FOREARM_FLEXORS, Muscle.FOREARM_EXTENSORS),
    "Core" to listOf(Muscle.ABS, Muscle.OBLIQUES),
    "Hips & legs" to listOf(
        Muscle.GLUTE_MAX, Muscle.GLUTE_MED, Muscle.QUADS, Muscle.ADDUCTORS, Muscle.HAMSTRINGS,
        Muscle.GASTROCNEMIUS, Muscle.SOLEUS, Muscle.TIBIALIS,
    ),
)

/** One line for the card: primary muscles, then secondary ones after a "+". */
internal fun muscleSummaryText(state: ExerciseMuscleMappingState?): String {
    if (state == null) return "Muscles: …"
    val mapping = state.mapping ?: return "Muscles: not mapped yet (shown as unmapped)"
    val primary = mapping.primary.joinToString(", ") { it.label }
    val secondary = mapping.secondary.joinToString(", ") { it.label }
    return if (secondary.isEmpty()) "Muscles: $primary" else "Muscles: $primary + $secondary"
}

/**
 * "Edit muscles" (#310): a grouped checklist, one Primary / Secondary choice per region. Save
 * stores the user's mapping for the whole exercise group and re-counts past workouts; Reset goes
 * back to the built-in, sourced mapping. Logged sets are never changed. A body-map picker comes later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditMusclesSheet(
    exerciseName: String,
    state: ExerciseMuscleMappingState,
    onSave: (primary: Set<Muscle>, secondary: Set<Muscle>) -> Unit,
    onResetToDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember(state) {
        val mapping = state.mapping
        buildMap {
            mapping?.secondary?.forEach { put(it, MuscleRole.SECONDARY) }
            mapping?.primary?.forEach { put(it, MuscleRole.PRIMARY) }
        }
    }
    var roles by remember(state) { mutableStateOf(initial) }
    val primary = roles.filterValues { it == MuscleRole.PRIMARY }.keys
    val secondary = roles.filterValues { it == MuscleRole.SECONDARY }.keys
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)

    AppBottomSheet(onDismissRequest = onDismiss) {
        Text("Edit muscles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(exerciseName, style = MaterialTheme.typography.bodyMedium, color = muted)
        Spacer(Modifier.height(8.dp))
        Text(sourceLine(state), style = MaterialTheme.typography.labelSmall, color = muted)
        Text(
            "Each working set counts 1 set for a primary muscle and ½ set for a secondary one.",
            style = MaterialTheme.typography.labelSmall,
            color = muted,
        )
        Spacer(Modifier.height(8.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            MUSCLE_CHECKLIST_GROUPS.forEach { (group, muscles) ->
                Text(
                    group,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                muscles.forEach { muscle ->
                    MuscleRoleRow(
                        muscle = muscle,
                        role = roles[muscle],
                        onRoleChange = { role ->
                            roles = if (role == null) roles - muscle else roles + (muscle to role)
                        },
                    )
                }
            }
        }

        if (primary.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Pick at least one primary muscle to save.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onResetToDefault, enabled = state.isUserEdit) { Text("Reset to default") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Button(
                onClick = { onSave(primary, secondary) },
                enabled = primary.isNotEmpty() && roles != initial,
            ) { Text("Save") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MuscleRoleRow(muscle: Muscle, role: MuscleRole?, onRoleChange: (MuscleRole?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(muscle.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        listOf(MuscleRole.PRIMARY to "Primary", MuscleRole.SECONDARY to "Secondary").forEach { (option, label) ->
            val selected = role == option
            FilterChip(
                selected = selected,
                onClick = { onRoleChange(if (selected) null else option) },
                label = { Text(label) },
                modifier = Modifier.semantics { contentDescription = "${muscle.label}: $label${if (selected) ", selected" else ""}" },
            )
        }
    }
}

private fun sourceLine(state: ExerciseMuscleMappingState): String {
    val mapping = state.mapping
    val seed = state.seed
    return when {
        mapping == null -> "No built-in mapping for this exercise yet: its sets are shown as unmapped, never as zero."
        mapping.origin == MuscleMappingOrigin.USER -> "Your edit." + (seed?.let { " Default: ${it.exercise} (${it.status.label})." } ?: "")
        mapping.origin == MuscleMappingOrigin.PARENT -> "Taken from the parent exercise."
        else -> mapping.seed?.let { "Default · ${it.status.label}: ${it.source}" } ?: "Default mapping."
    }
}
