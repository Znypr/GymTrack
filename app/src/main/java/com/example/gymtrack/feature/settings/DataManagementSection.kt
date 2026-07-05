package com.example.gymtrack.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun DataManagementSection(
    enabled: Boolean,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
    SettingsSectionTitle("Data Management")
    SettingsCard {
        DataManagementAction(
            title = "Back up all data",
            description = "Create one portable GymTrack backup file",
            enabled = enabled,
            onClick = onCreateBackup,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        DataManagementAction(
            title = "Restore from backup",
            description = "Validate a backup before replacing local data",
            enabled = enabled,
            onClick = onRestoreBackup,
        )
    }
}

@Composable
private fun DataManagementAction(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
