package dev.bidzz.opencode.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val status by app.engineStatus.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = "Settings", onBack = onBack)

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

            SectionLabel("Engine")
            InfoRow("State", status.state.name.lowercase().replaceFirstChar { it.uppercase() })
            InfoRow("Port", status.port?.toString() ?: "—")
            InfoRow("Base URL", status.credentials?.baseUrl ?: "—")

            SectionLabel("Advanced")
            ActionRow("Diagnostics", "Engine log, credentials, runtime paths", onDiagnostics)
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(end = spacing.lg, top = spacing.xs, bottom = spacing.xs),
        ) {
            Text("Back", style = OpenCodeTypography.label, color = colors.accent)
        }
        Text(title, style = OpenCodeTypography.title, color = colors.onSurface)
    }
    Box(Modifier.fillMaxWidth().background(colors.outline).padding(top = 0.5.dp))
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
    ) {
        Text(title, style = OpenCodeTypography.body, color = colors.onSurface)
        Text(subtitle, style = OpenCodeTypography.bodySmall, color = colors.onSurfaceMuted)
    }
}
