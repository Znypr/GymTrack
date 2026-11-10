package com.example.gymtrack.feature.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.ui.components.AppCard
import com.example.gymtrack.core.ui.components.AppDivider
import com.example.gymtrack.core.ui.components.AppIcon
import com.example.gymtrack.core.ui.components.AppIconView
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.feature.help.appVersionLabel
import com.example.gymtrack.feature.help.openUrl
import com.example.gymtrack.feature.settings.InfoText
import com.example.gymtrack.feature.settings.NavRow
import com.example.gymtrack.feature.settings.RowDivider
import com.example.gymtrack.feature.settings.ScreenHeader
import com.example.gymtrack.feature.settings.SettingsGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val ABOUT_DISCLAIMER_TAG = "about-disclaimer"

const val MEDICAL_DISCLAIMER =
    "Not medical advice. ZnyprFit is a training log. It does not give medical, health or training " +
        "advice. Ask a doctor or qualified coach before starting or changing training, especially " +
        "with an injury or health condition."

private const val PAGE_MAIN = "main"
private const val PAGE_LICENCES = "licences"
private const val PAGE_DOC = "doc:"
private const val PAGE_FILE = "file:"

/**
 * About & legal (#338): version, privacy policy (EN/DE, offline text + public link), Impressum,
 * open-source licences and the "Not medical advice" disclaimer. Sub-pages open inside this
 * screen; system back returns to the list, then leaves the screen.
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableStateOf(PAGE_MAIN) }
    BackHandler(enabled = page != PAGE_MAIN) {
        page = if (page.startsWith(PAGE_FILE)) PAGE_LICENCES else PAGE_MAIN
    }
    val tokens = GymTrackTheme.tokens
    Column(modifier.fillMaxSize().background(tokens.colors.bg)) {
        when {
            page == PAGE_LICENCES -> LicencesPage(onOpenFile = { page = PAGE_FILE + it })
            page.startsWith(PAGE_DOC) -> LegalDocPage(LegalDoc.valueOf(page.removePrefix(PAGE_DOC)))
            page.startsWith(PAGE_FILE) -> PlainAssetPage(page.removePrefix(PAGE_FILE))
            else -> AboutMain(
                onOpenDoc = { page = PAGE_DOC + it.name },
                onOpenLicences = { page = PAGE_LICENCES },
            )
        }
    }
}

@Composable
private fun AboutMain(onOpenDoc: (LegalDoc) -> Unit, onOpenLicences: () -> Unit) {
    val tokens = GymTrackTheme.tokens
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = tokens.spacing.md),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
    ) {
        item { ScreenHeader("About & legal", "ZnyprFit ${appVersionLabel()}") }
        item {
            AppCard(Modifier.fillMaxWidth().testTag(ABOUT_DISCLAIMER_TAG), seed = 91) {
                Row {
                    AppIconView(AppIcon.ALERT, contentDescription = null, tint = tokens.colors.ink, size = 18.dp)
                    Spacer(Modifier.width(tokens.spacing.xs))
                    Text(MEDICAL_DISCLAIMER, style = tokens.type.small, color = tokens.colors.ink)
                }
            }
        }
        item {
            SettingsGroup("Privacy") {
                InfoText("Your workouts stay on this phone. No account, no ads, no analytics, no internet permission.")
                RowDivider(1)
                NavRow("Privacy policy", onClick = { onOpenDoc(LegalDoc.PRIVACY_EN) }, subtitle = "English, works offline")
                RowDivider(2)
                NavRow("Datenschutzerklärung", onClick = { onOpenDoc(LegalDoc.PRIVACY_DE) }, subtitle = "Deutsch, offline lesbar")
                RowDivider(3)
                NavRow(
                    "Privacy policy online",
                    onClick = { openUrl(context, PRIVACY_POLICY_URL) },
                    subtitle = PRIVACY_POLICY_URL,
                )
            }
        }
        item {
            SettingsGroup("Legal") {
                NavRow("Impressum", onClick = { onOpenDoc(LegalDoc.IMPRESSUM) }, subtitle = "Legal notice (Germany)")
                RowDivider(4)
                NavRow("Open-source licences", onClick = onOpenLicences, subtitle = "Libraries and fonts in this app")
            }
        }
        item { Spacer(Modifier.height(tokens.spacing.lg)) }
    }
}

@Composable
private fun LegalDocPage(doc: LegalDoc) {
    val tokens = GymTrackTheme.tokens
    val text = rememberAsset(doc.assetPath)
    val blocks = remember(text) { if (text == null) emptyList() else LegalText.blocks(text) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = tokens.spacing.md),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
    ) {
        item { Spacer(Modifier.height(tokens.spacing.lg)) }
        if (text == null) item { InfoText("Loading…") }
        items(blocks) { block ->
            when (block) {
                is LegalBlock.Heading -> Text(
                    text = block.text,
                    style = when (block.level) {
                        1 -> tokens.type.display
                        2 -> tokens.type.title
                        else -> tokens.type.body.copy(fontWeight = FontWeight.SemiBold)
                    },
                    color = tokens.colors.ink,
                    modifier = Modifier.padding(top = tokens.spacing.xs).semantics { heading() },
                )
                is LegalBlock.Paragraph -> SpanText(block.spans)
                is LegalBlock.Bullet -> Row {
                    Text("•", style = tokens.type.body, color = tokens.colors.ink)
                    Spacer(Modifier.width(tokens.spacing.xs))
                    SpanText(block.spans)
                }
            }
        }
        item { Spacer(Modifier.height(tokens.spacing.xl)) }
    }
}

@Composable
private fun SpanText(spans: List<Span>) {
    val tokens = GymTrackTheme.tokens
    val annotated = buildAnnotatedString {
        spans.forEach { span ->
            if (span.bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) } else append(span.text)
        }
    }
    Text(annotated, style = tokens.type.body, color = tokens.colors.ink)
}

@Composable
private fun LicencesPage(onOpenFile: (String) -> Unit) {
    val tokens = GymTrackTheme.tokens
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = tokens.spacing.md),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
    ) {
        item { ScreenHeader("Open-source licences", "ZnyprFit is built with these. Thank you.") }
        items(OpenSourceNotices.all) { notice ->
            AppCard(Modifier.fillMaxWidth(), seed = notice.name.hashCode()) {
                Text(notice.name, style = tokens.type.body, color = tokens.colors.ink, fontWeight = FontWeight.Medium)
                Text(notice.copyright, style = tokens.type.small, color = tokens.colors.inkMuted)
                Text("${notice.license} · ${notice.licenseUrl}", style = tokens.type.small, color = tokens.colors.inkMuted)
            }
        }
        item {
            SettingsGroup("Font licence texts") {
                OpenSourceNotices.bundledLicenseFiles.forEachIndexed { index, path ->
                    if (index > 0) AppDivider(seed = index)
                    NavRow(path.substringAfterLast('/'), onClick = { onOpenFile(path) }, subtitle = "Full licence text")
                }
            }
        }
        item { Spacer(Modifier.height(tokens.spacing.lg)) }
    }
}

@Composable
private fun PlainAssetPage(path: String) {
    val tokens = GymTrackTheme.tokens
    val text = rememberAsset(path)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = tokens.spacing.md)) {
        item { Spacer(Modifier.height(tokens.spacing.lg)) }
        item { Text(text ?: "Loading…", style = tokens.type.small, color = tokens.colors.ink) }
        item { Spacer(Modifier.height(tokens.spacing.xl)) }
    }
}

/** Reads a small text asset off the main thread; null while loading. */
@Composable
private fun rememberAsset(path: String): String? {
    val context = LocalContext.current
    val state by produceState<String?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }
                .getOrElse { "Could not open $path." }
        }
    }
    return state
}
