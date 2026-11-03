package dev.bidzz.opencode.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.ScreenScaffold

/**
 * Settings.
 *
 * Deliberately sparse: only what exists and works. Empty rows for future features
 * would be placeholder content presented as real, which `plan.md` §12 forbids.
 */
@Composable
fun SettingsScreen(
    app: OpenCodeApplication,
    onBack: () -> Unit,
    onDiagnostics: () -> Unit,
    onProjects: () -> Unit,
    onProviders: () -> Unit,
) {
    val spacing = OpenCodeTheme.tokens.spacing
    val status by app.engineStatus.collectAsState()

    ScreenScaffold(title = "Settings", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            SectionLabel("Project")
            ActionRow("Project folder", app.workspaceStore.resolve().absolutePath, onProjects)
            InfoRow("All-files access", if (app.workspaceStore.hasAllFilesAccess()) "Granted" else "Not granted")

            SectionLabel("Provider")
            ActionRow(
                "Connect a provider",
                "Add an API key. Without one the agent is limited to opencode's free tier.",
                onProviders,
            )

            // Port and base URL are engine internals; they live in Diagnostics, which already
            // lists them, so this screen shows only the one-word state a user can act on.
            SectionLabel("Engine")
            ActionRow(
                title = "Diagnostics",
                subtitle = "Engine is " + status.state.name.lowercase() + ". Log, credentials, runtime paths.",
                onClick = onDiagnostics,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Text(
        text = text.uppercase(),
        style = OpenCodeTypography.label,
        color = colors.onSurfaceFaint,
        modifier = Modifier.padding(start = spacing.lg, end = spacing.lg, top = spacing.lg, bottom = spacing.sm),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
    ) {
        Text(label, style = OpenCodeTypography.bodySmall, color = colors.onSurfaceMuted)
        Text(
            text = value,
            style = OpenCodeTypography.code,
            color = colors.onSurface,
            modifier = Modifier.padding(top = spacing.xxs),
        )
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = OpenCodeTypography.body, color = colors.onSurface)
            Text(subtitle, style = OpenCodeTypography.bodySmall, color = colors.onSurfaceMuted)
        }
        // The chevron is what says this row opens something, rather than just showing a value.
        GlyphIcon(Glyph.ChevronRight, tint = colors.onSurfaceFaint, size = 18.dp, modifier = Modifier.padding(start = spacing.md))
    }
}
