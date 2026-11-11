package com.example.gymtrack.feature.help

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.ui.components.AppCard
import com.example.gymtrack.core.ui.components.AppDivider
import com.example.gymtrack.core.ui.components.AppIcon
import com.example.gymtrack.core.ui.components.AppIconView
import com.example.gymtrack.core.ui.components.PrimaryButton
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.feature.settings.ScreenHeader
import com.example.gymtrack.feature.settings.SectionLabel

const val HELP_SEND_FEEDBACK_TAG = "help-send-feedback"

/**
 * Help & feedback (#345): the set-grammar cheat-sheet, the FAQ and "Send feedback" (pinned at
 * the bottom, in the thumb zone). Back = system back.
 *
 * @param onSendFeedback defaults to the email intent ([sendFeedback]).
 */
@Composable
fun HelpScreen(
    modifier: Modifier = Modifier,
    onSendFeedback: (() -> Unit)? = null,
) {
    val tokens = GymTrackTheme.tokens
    val context = LocalContext.current
    val send = onSendFeedback ?: { sendFeedback(context) }
    Column(modifier.fillMaxSize().background(tokens.colors.bg)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
        ) {
            item { ScreenHeader("Help", "How to type sets, and answers to common questions") }
            item { SectionLabel("Typing sets") }
            item { GrammarCard() }
            item { SectionLabel("Questions") }
            item { FaqCard() }
            item { Spacer(Modifier.height(tokens.spacing.md)) }
        }
        PrimaryButton(
            text = "Send feedback",
            onClick = send,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm)
                .testTag(HELP_SEND_FEEDBACK_TAG),
        )
    }
}

@Composable
private fun GrammarCard() {
    val tokens = GymTrackTheme.tokens
    AppCard(Modifier.fillMaxWidth(), seed = 71) {
        Text("Sets are reps × weight.", style = tokens.type.small, color = tokens.colors.inkMuted)
        HelpContent.grammar.forEachIndexed { index, entry ->
            if (index > 0) AppDivider(seed = index * 5)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = tokens.spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.example,
                    style = tokens.type.setNumber,
                    color = tokens.colors.ink,
                    modifier = Modifier.widthIn(min = 112.dp),
                )
                Spacer(Modifier.width(tokens.spacing.sm))
                Text(entry.meaning, style = tokens.type.body, color = tokens.colors.ink, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FaqCard() {
    val tokens = GymTrackTheme.tokens
    var open by rememberSaveable { mutableStateOf(-1) }
    AppCard(Modifier.fillMaxWidth(), seed = 73) {
        HelpContent.faq.forEachIndexed { index, entry ->
            if (index > 0) AppDivider(seed = index * 3)
            val expanded = open == index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = tokens.spacing.touchTarget)
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "Hide answer" else "Show answer") {
                        open = if (expanded) -1 else index
                    }
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .padding(vertical = tokens.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.question,
                    style = tokens.type.body,
                    color = tokens.colors.ink,
                    fontWeight = if (tokens.isNotebook) null else FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (expanded) {
                    Text("−", style = tokens.type.title, color = tokens.colors.inkMuted)
                } else {
                    AppIconView(AppIcon.ADD, contentDescription = null, tint = tokens.colors.inkMuted, size = 18.dp)
                }
            }
            if (expanded) {
                Text(
                    text = entry.answer,
                    style = tokens.type.body,
                    color = tokens.colors.inkMuted,
                    modifier = Modifier.padding(bottom = tokens.spacing.sm),
                )
            }
        }
    }
}
