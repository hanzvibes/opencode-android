package dev.bidzz.opencode.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.AppLanguage
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.ui.CardGroup
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.IconBadge
import dev.bidzz.opencode.ui.LocalizedAlertDialog
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold

/**
 * Settings.
 *
 * Deliberately sparse: only what exists and works. Empty rows for future features
 * would be placeholder content presented as real, which `plan.md` §12 forbids.
 *
 * Rows are grouped into cards by topic. Each row says what it is (icon, title), what it is
 * set to (subtitle or status), and whether it opens something (chevron).
 */
@Composable
fun SettingsScreen(
    app: OpenCodeApplication,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    onDiagnostics: () -> Unit,
    onProjects: () -> Unit,
    onProviders: () -> Unit,
    onAbout: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val status by app.engineStatus.collectAsState()
    var pickingLanguage by remember { mutableStateOf(false) }
    val hasAllFiles = app.workspaceStore.hasAllFilesAccess()

    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        ) {
            Section(R.string.settings_section_project) {
                SettingsRow(
                    glyph = Glyph.Folder,
                    title = stringResource(R.string.settings_project_folder),
                    subtitle = app.workspaceStore.resolve().absolutePath,
                    onClick = onProjects,
                )
                SettingsRow(
                    glyph = Glyph.File,
                    title = stringResource(R.string.settings_all_files),
                    subtitle = stringResource(
                        if (hasAllFiles) R.string.settings_all_files_granted_hint
                        else R.string.settings_all_files_missing_hint,
                    ),
                    value = stringResource(
                        if (hasAllFiles) R.string.settings_granted else R.string.settings_not_granted,
                    ),
                    valueColor = if (hasAllFiles) colors.success else colors.warning,
                    // Only actionable while the permission is missing; granted is just a status.
                    onClick = if (hasAllFiles) null else app::requestAllFilesAccess,
                    divider = true,
                )
            }

            Section(R.string.settings_section_language) {
                SettingsRow(
                    glyph = Glyph.Globe,
                    title = stringResource(R.string.settings_language),
                    value = languageLabel(language),
                    onClick = { pickingLanguage = true },
                )
            }

            Section(R.string.settings_section_provider) {
                SettingsRow(
                    glyph = Glyph.Sliders,
                    title = stringResource(R.string.settings_provider_title),
                    subtitle = stringResource(R.string.settings_provider_body),
                    onClick = onProviders,
                )
            }

            Section(R.string.settings_section_engine) {
                SettingsRow(
                    glyph = Glyph.Terminal,
                    title = stringResource(R.string.settings_diag_title),
                    subtitle = stringResource(R.string.settings_diag_body, engineStateWord(status.state)),
                    onClick = onDiagnostics,
                )
            }

            Section(R.string.settings_section_about) {
                SettingsRow(
                    glyph = Glyph.Info,
                    title = stringResource(R.string.settings_about_title),
                    subtitle = stringResource(R.string.settings_about_body),
                    onClick = onAbout,
                )
            }
        }
    }

    if (pickingLanguage) {
        LanguageDialog(
            selected = language,
            onSelect = { chosen ->
                pickingLanguage = false
                if (chosen != language) onLanguageChange(chosen)
            },
            onDismiss = { pickingLanguage = false },
        )
    }
}

/** A titled group: small label above, rows inside one rounded card. */
@Composable
private fun Section(titleRes: Int, content: @Composable ColumnScope.() -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Text(
        text = stringResource(titleRes).uppercase(),
        style = OpenCodeTypography.label,
        color = colors.onSurfaceFaint,
        modifier = Modifier.padding(start = spacing.xs, top = spacing.xl, bottom = spacing.sm),
    )
    CardGroup(content = content)
}

/**
 * One row. [onClick] null means it is information only: no chevron, not tappable.
 * [value] is the short current setting or status shown at the end of the row.
 */
@Composable
private fun SettingsRow(
    glyph: Glyph,
    title: String,
    onClick: (() -> Unit)?,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color = OpenCodeTheme.colors.onSurfaceMuted,
    divider: Boolean = false,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    // Starts under the text, not under the icon, so the icons read as one column.
    if (divider) RowDivider(inset = spacing.md + 36.dp + spacing.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = spacing.md, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(glyph)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.md),
        ) {
            Text(title, style = OpenCodeTypography.body, color = colors.onSurface)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = OpenCodeTypography.bodySmall,
                color = valueColor,
                modifier = Modifier.padding(start = spacing.sm),
            )
        }
        if (onClick != null) {
            GlyphIcon(
                Glyph.ChevronRight,
                tint = colors.onSurfaceFaint,
                size = 18.dp,
                modifier = Modifier.padding(start = spacing.sm),
            )
        }
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.System -> R.string.language_system
        AppLanguage.English -> R.string.language_english
        AppLanguage.Indonesian -> R.string.language_indonesian
    },
)

/** Picks apply at once; there is nothing to confirm, so tapping a choice closes the dialog. */
@Composable
private fun LanguageDialog(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    LocalizedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.settings_language_dialog_title),
                style = OpenCodeTypography.title,
                color = colors.onSurface,
            )
        },
        text = {
            Column {
                AppLanguage.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.RadioButton) { onSelect(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colors.accent,
                                unselectedColor = colors.onSurfaceMuted,
                            ),
                        )
                        Text(
                            text = languageLabel(option),
                            style = OpenCodeTypography.body,
                            color = colors.onSurface,
                            modifier = Modifier.padding(start = spacing.md),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = colors.onSurfaceMuted)
            }
        },
    )
}

/** Lower-case state for use inside a sentence ("Engine is running"). */
@Composable
private fun engineStateWord(state: EngineState): String = stringResource(
    when (state) {
        EngineState.RUNNING -> R.string.engine_state_running
        EngineState.INITIALIZING -> R.string.engine_state_preparing
        EngineState.STARTING -> R.string.engine_state_starting
        EngineState.DEGRADED -> R.string.engine_state_unhealthy
        EngineState.STOPPING -> R.string.engine_state_stopping
        EngineState.FAILED -> R.string.engine_state_failed
        EngineState.STOPPED -> R.string.engine_state_stopped
    },
)
