package com.example.gymtrack.feature.stats.components.charts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.util.formatRelativeTime

@Composable
fun LearningsOverview(notes: List<NoteLine>, settings: Settings) {
    val items = notes.flatMap { note ->
        val time = formatRelativeTime(note.timestamp, settings)
        val category = note.categoryName ?: "Other"
        note.learnings.split("\n").mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) null else Triple(trimmed, time, category)
        }
    }

    if (items.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Learnings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        items.forEach { (text, time, cat) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(time, style = MaterialTheme.typography.bodySmall)
                    Text(cat, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
