package com.example.gymtrack.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.TornTopShape
import com.example.gymtrack.core.ui.theme.notebookPaper
import com.example.gymtrack.core.ui.theme.sketchUnderline

/**
 * Modal bottom sheet in the app's skin (finish sheet, pickers). Content sits in the thumb zone.
 * Normal: surface, 24 dp top corners. Notebook: a torn-off ruled page with a pencil grab line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    if (tokens.isNotebook) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = TornTopShape(seed = 23),
            containerColor = c.bg,
            contentColor = c.ink,
            tonalElevation = 0.dp,
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 14.dp, bottom = 6.dp)
                        .width(40.dp)
                        .height(6.dp)
                        .sketchUnderline(c.inkMuted, seed = 29, strokeWidth = 2.dp),
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .notebookPaper(paper = c.bg, rule = c.paperRule, margin = c.paperMargin, marginX = null)
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
                content = content,
            )
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = tokens.radii.sheet, topEnd = tokens.radii.sheet),
            containerColor = c.surface,
            contentColor = c.ink,
            tonalElevation = 0.dp,
            dragHandle = {
                Box(
                    Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .background(c.inkMuted.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
                )
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
                horizontalAlignment = Alignment.Start,
                content = content,
            )
        }
    }
}
