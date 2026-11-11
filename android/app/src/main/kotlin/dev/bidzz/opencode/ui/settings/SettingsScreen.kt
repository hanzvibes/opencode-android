package dev.bidzz.opencode.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.AppLanguage
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.PermissionSavedInfo
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.core.design.ThemeMode
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.ui.CardGroup
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.IconBadge
import dev.bidzz.opencode.ui.LocalizedAlertDialog
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.StatusDot
import dev.bidzz.opencode.ui.message
import kotlinx.coroutines.launch

/**
 * Settings screen.
 *
 * Organized into Apple-style grouped settings cards:
 * - Project & Workspace (Folder, All Files Permission)
 * - Engine & Diagnostics (Engine Port, Diagnostics)
 * - Preferences & Models (Language, Providers)
 * - Information (About)
 *
 * Each row presents a clean squircle icon badge, descriptive title & subtitle,
 * inline status value, and navigation chevron when actionable.
 */
@Composable
fun SettingsScreen(
    app: OpenCodeApplication,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onDiagnostics: () -> Unit,
    onProjects: () -> Unit,
    onProviders: () -> Unit,
    onMcpServers: () -> Unit,
    onAbout: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val status by app.engineStatus.collectAsState()
    var pickingLanguage by remember { mutableStateOf(false) }
    val hasAllFiles = app.workspaceStore.hasAllFilesAccess()

    // Saved permissions live on the engine (`GET /api/permission/saved`) and
    // are read when this screen opens. The list is re-read from the engine
    // after a revoke instead of trusting the local removal: a failed DELETE
    // must leave the entry in place, and the engine is the source of truth.
    var savedPermissions by remember { mutableStateOf<List<PermissionSavedInfo>>(emptyList()) }
    var savedPermissionsLoading by remember { mutableStateOf(true) }
    var savedPermissionsFailure by remember { mutableStateOf<ApiResult.Failure?>(null) }
    val permissionsScope = rememberCoroutineScope()
    val engineHandle = app.engineApi.handle()
    LaunchedEffect(engineHandle) {
        if (engineHandle == null) {
            savedPermissionsLoading = false
            return@LaunchedEffect
        }
        when (val result = engineHandle.permissions.listSaved()) {
            is ApiResult.Success -> {
                savedPermissions = result.value
                savedPermissionsLoading = false
            }
            is ApiResult.Failure -> {
                savedPermissionsFailure = result
                savedPermissionsLoading = false
            }
        }
    }
    fun revokeSavedPermission(saved: PermissionSavedInfo) {
        val handle = app.engineApi.handle() ?: return
        permissionsScope.launch {
            when (val result = handle.permissions.deleteSaved(saved.id)) {
                is ApiResult.Success -> savedPermissions = savedPermissions - saved
                is ApiResult.Failure -> savedPermissionsFailure = result
            }
        }
    }

    // Apple-style categorized badge colors
    val blueBadge = Color(0xFF007AFF)
    val greenBadge = Color(0xFF34C759)
    val amberBadge = Color(0xFFFF9500)
    val purpleBadge = Color(0xFF5856D6)
    val violetBadge = Color(0xFFAF52DE)
    val tealBadge = Color(0xFF30B0C7)
    val grayBadge = Color(0xFF8E8E93)

    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        ) {
            // Workspace & Permissions
            Section(R.string.settings_section_project) {
                SettingsRow(
                    glyph = Glyph.Folder,
                    badgeColor = blueBadge,
                    title = stringResource(R.string.settings_project_folder),
                    subtitle = app.workspaceStore.resolve().absolutePath,
                    onClick = onProjects,
                )
                SettingsRow(
                    glyph = Glyph.File,
                    badgeColor = if (hasAllFiles) greenBadge else amberBadge,
                    title = stringResource(R.string.settings_all_files),
                    subtitle = stringResource(
                        if (hasAllFiles) R.string.settings_all_files_granted_hint
                        else R.string.settings_all_files_missing_hint,
                    ),
                    value = stringResource(
                        if (hasAllFiles) R.string.settings_granted else R.string.settings_not_granted,
                    ),
                    valueColor = if (hasAllFiles) colors.success else colors.warning,
                    onClick = if (hasAllFiles) null else app::requestAllFilesAccess,
                    divider = true,
                )
            }

            // Saved permissions ("Always" answers), each revocable.
            Section(R.string.settings_saved_permissions) {
                Text(
                    text = stringResource(R.string.settings_saved_permissions_body),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(
                        horizontal = spacing.md,
                        vertical = spacing.sm,
                    ),
                )
                when {
                    savedPermissionsLoading -> {
                        Text(
                            text = stringResource(R.string.common_loading),
                            style = OpenCodeTypography.bodySmall,
                            color = colors.onSurfaceFaint,
                            modifier = Modifier.padding(
                                start = spacing.md,
                                end = spacing.md,
                                bottom = spacing.md,
                            ),
                        )
                    }
                    savedPermissionsFailure != null -> {
                        Text(
                            text = savedPermissionsFailure!!.message(),
                            style = OpenCodeTypography.bodySmall,
                            color = colors.danger,
                            modifier = Modifier.padding(
                                start = spacing.md,
                                end = spacing.md,
                                bottom = spacing.md,
                            ),
                        )
                    }
                    savedPermissions.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.settings_saved_permissions_empty),
                            style = OpenCodeTypography.bodySmall,
                            color = colors.onSurfaceFaint,
                            modifier = Modifier.padding(
                                start = spacing.md,
                                end = spacing.md,
                                bottom = spacing.md,
                            ),
                        )
                    }
                    else -> {
                        savedPermissions.forEachIndexed { index, saved ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            SavedPermissionRow(
                                saved = saved,
                                onRevoke = { revokeSavedPermission(saved) },
                            )
                        }
                    }
                }
            }

            // Engine & Connectivity
            Section(R.string.settings_section_engine) {
                SettingsRow(
                    glyph = Glyph.Code,
                    badgeColor = tealBadge,
                    title = stringResource(R.string.settings_engine_port),
                    subtitle = status.credentials?.baseUrl ?: stringResource(R.string.settings_engine_port_desc),
                    value = status.port?.toString() ?: stringResource(R.string.settings_engine_inactive),
                    valueColor = if (status.port != null) colors.neutral else colors.onSurfaceMuted,
                    valueIsMono = status.port != null,
                    onClick = onDiagnostics,
                )
                SettingsRow(
                    glyph = Glyph.Sliders,
                    badgeColor = amberBadge,
                    title = stringResource(R.string.settings_diag_title),
                    subtitle = stringResource(R.string.settings_diag_body, engineStateWord(status.state)),
                    value = engineStateWord(status.state).replaceFirstChar { it.uppercase() },
                    valueColor = when (status.state) {
                        EngineState.RUNNING -> colors.success
                        EngineState.STARTING, EngineState.INITIALIZING -> colors.warning
                        EngineState.DEGRADED, EngineState.FAILED -> colors.danger
                        EngineState.STOPPED, EngineState.STOPPING -> colors.onSurfaceMuted
                    },
                    statusDotColor = when (status.state) {
                        EngineState.RUNNING -> colors.success
                        EngineState.STARTING, EngineState.INITIALIZING -> colors.warning
                        EngineState.DEGRADED, EngineState.FAILED -> colors.danger
                        EngineState.STOPPED, EngineState.STOPPING -> null
                    },
                    onClick = onDiagnostics,
                    divider = true,
                )
                SettingsRow(
                    glyph = Glyph.External,
                    badgeColor = purpleBadge,
                    title = stringResource(R.string.mcp_title),
                    subtitle = stringResource(R.string.mcp_body),
                    onClick = onMcpServers,
                )
            }

            // Preferences & Providers
            Section(R.string.settings_section_language) {
                SettingsRow(
                    glyph = Glyph.Globe,
                    badgeColor = purpleBadge,
                    title = stringResource(R.string.settings_language),
                    value = languageLabel(language),
                    onClick = { pickingLanguage = true },
                )
            }

            // Appearance (Phase 3): the theme is picked inline, not in a
            // dialog — three radio rows, one tap to switch, applied at once
            // over the whole tree (see OpenCodeApp). System follows the
            // phone; Dark / Light pin the palette.
            Section(R.string.settings_section_appearance) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) RowDivider(inset = spacing.md)
                    ThemeModeRow(
                        mode = mode,
                        selected = mode == themeMode,
                        onSelect = { if (mode != themeMode) onThemeModeChange(mode) },
                    )
                }
            }

            Section(R.string.settings_section_provider) {
                SettingsRow(
                    glyph = Glyph.Key,
                    badgeColor = violetBadge,
                    title = stringResource(R.string.settings_provider_title),
                    subtitle = stringResource(R.string.settings_provider_body),
                    onClick = onProviders,
                )
            }

            // About
            Section(R.string.settings_section_about) {
                SettingsRow(
                    glyph = Glyph.Info,
                    badgeColor = grayBadge,
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

/** A titled group: Apple-style inset uppercase section header above a grouped card surface. */
@Composable
private fun Section(titleRes: Int, content: @Composable ColumnScope.() -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Text(
        text = stringResource(titleRes).uppercase(),
        style = OpenCodeTypography.caption.copy(letterSpacing = 0.6.sp),
        color = colors.onSurfaceFaint,
        modifier = Modifier.padding(start = spacing.sm, top = spacing.xl, bottom = spacing.xs),
    )
    CardGroup(content = content)
}

/**
 * Apple-style grouped settings row.
 *
 * @param glyph The icon glyph to render inside the squircle badge.
 * @param badgeColor Background color of the icon badge.
 * @param title Primary title of the setting.
 * @param onClick Action callback; null renders an informational row without navigation chevron.
 * @param subtitle Secondary explanatory text.
 * @param value Setting value or status text shown on the right side.
 * @param valueColor Color for the status text.
 * @param valueIsMono Whether to render the value in monospace font (e.g. for port numbers).
 * @param statusDotColor Optional status dot shown next to the value.
 * @param divider Whether to draw a hairline separator above this row (inset past the badge).
 */
@Composable
private fun SettingsRow(
    glyph: Glyph,
    badgeColor: Color,
    title: String,
    onClick: (() -> Unit)?,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color = OpenCodeTheme.colors.onSurfaceMuted,
    valueIsMono: Boolean = false,
    statusDotColor: Color? = null,
    divider: Boolean = false,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    // Inset separator starts cleanly beneath the text, skipping the badge column
    if (divider) {
        RowDivider(inset = spacing.md + 30.dp + spacing.md)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = spacing.md, vertical = spacing.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Apple-style squircle icon badge
        IconBadge(
            glyph = glyph,
            tint = Color.White,
            background = badgeColor,
            shape = RoundedCornerShape(radius.sm),
            size = 30.dp,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.md, end = spacing.xs),
        ) {
            Text(
                text = title,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
            )
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

        if (value != null || statusDotColor != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = spacing.xs),
            ) {
                if (statusDotColor != null) {
                    StatusDot(
                        color = statusDotColor,
                        size = 7.dp,
                        modifier = Modifier.padding(end = spacing.xs),
                    )
                }
                if (value != null) {
                    Text(
                        text = value,
                        style = if (valueIsMono) OpenCodeTypography.code else OpenCodeTypography.bodySmall,
                        color = valueColor,
                    )
                }
            }
        }

        if (onClick != null) {
            GlyphIcon(
                glyph = Glyph.ChevronRight,
                tint = colors.onSurfaceFaint,
                size = 14.dp,
                modifier = Modifier.padding(start = spacing.xs),
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

/** One inline theme row: radio + label + hint, 48dp, whole row tappable. */
@Composable
private fun ThemeModeRow(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.accent,
                unselectedColor = colors.onSurfaceMuted,
            ),
        )
        Column(
            modifier = Modifier.padding(start = spacing.md),
        ) {
            Text(
                text = stringResource(
                    when (mode) {
                        ThemeMode.System -> R.string.settings_theme_system
                        ThemeMode.Dark -> R.string.settings_theme_dark
                        ThemeMode.Light -> R.string.settings_theme_light
                    },
                ),
                style = OpenCodeTypography.body,
                color = colors.onSurface,
            )
            Text(
                text = stringResource(
                    when (mode) {
                        ThemeMode.System -> R.string.settings_theme_system_hint
                        ThemeMode.Dark -> R.string.settings_theme_dark_hint
                        ThemeMode.Light -> R.string.settings_theme_light_hint
                    },
                ),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
            )
        }
    }
}

/** One saved permission with a revoke affordance. */
@Composable
private fun SavedPermissionRow(
    saved: PermissionSavedInfo,
    onRevoke: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    // The action is what was allowed ("bash", "edit", ...); the resource
    // narrows it (a command, a path). The project id is a hash the operator
    // cannot read, so it stays out of the row.
    val title = saved.action.ifBlank { saved.resource.orEmpty() }.ifBlank { saved.id }
    val subtitle = saved.resource?.takeIf { it.isNotBlank() && it != title }.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = spacing.xs),
        ) {
            Text(
                text = title,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onRevoke) {
            Text(
                text = stringResource(R.string.settings_saved_permissions_revoke),
                style = OpenCodeTypography.label,
                color = colors.danger,
            )
        }
    }
}

/** Language picker dialog. */
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
                text = stringResource(R.string.settings_language_dialog_title),
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
