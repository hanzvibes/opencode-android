package dev.bidzz.opencode.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.AppLanguage
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import androidx.fragment.app.FragmentActivity
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.PermissionSavedInfo
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.core.design.ThemeMode
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.ui.AppRadioButton
import dev.bidzz.opencode.ui.AppTag
import dev.bidzz.opencode.ui.CardGroup
import dev.bidzz.opencode.ui.HatchSection
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.IconBadge
import dev.bidzz.opencode.ui.LocalizedDraggableBottomSheet
import dev.bidzz.opencode.ui.SearchField
import dev.bidzz.opencode.ui.engineDownFailure
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.SheetStop
import dev.bidzz.opencode.ui.StatusDot
import dev.bidzz.opencode.ui.security.AppLockPrefs
import dev.bidzz.opencode.ui.security.LockAvailability
import dev.bidzz.opencode.ui.security.authenticateWithBiometrics
import dev.bidzz.opencode.ui.security.lockAvailability
import dev.bidzz.opencode.ui.message
import kotlinx.coroutines.launch

/**
 * Settings screen.
 *
 * Organized into Apple-style grouped settings cards:
 * - Engine status card (state, port, workspace, all-files access) at the top
 * - Project & Workspace (Folder, All Files Permission)
 * - Saved permissions ("Always" answers, each revocable)
 * - Engine & Diagnostics (Engine Port, Diagnostics, MCP servers)
 * - Preferences & Models (Language, Appearance, Providers)
 * - Information (About)
 *
 * The search field filters rows by title, subtitle or value; sections with no
 * matching row are hidden, and an empty state replaces the list when nothing
 * matches. Icon badges are neutral (one blue, one meaning): colour is reserved
 * for semantic status (engine state, granted / not granted) and for the
 * selected theme radio / search cursor.
 *
 * Each row presents a neutral icon badge, descriptive title & subtitle,
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
    onPlugins: () -> Unit,
    onWorktrees: () -> Unit,
    onEngineConfig: () -> Unit,
    onSessionStats: () -> Unit,
    onUsageLimits: () -> Unit,
    onAbout: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val status by app.engineStatus.collectAsState()
    var pickingLanguage by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val query = searchQuery
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
            // No engine, no permissions to show — and the empty text must
            // not claim "no saved permissions yet" (audit-100 64-F10):
            // record the failure so the section renders the engine-down
            // error instead. Re-reading happens when the engine attaches
            // (engineHandle changes) — there is no manual retry here.
            savedPermissionsFailure = engineDownFailure()
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

    // Semantic engine colours, computed once and shared by the status card
    // and the Diagnostics row: colour here means state, never decoration.
    val engineStateColor = when (status.state) {
        EngineState.RUNNING -> colors.success
        EngineState.STARTING, EngineState.INITIALIZING -> colors.warning
        EngineState.DEGRADED, EngineState.FAILED -> colors.danger
        EngineState.STOPPED, EngineState.STOPPING -> colors.onSurfaceMuted
    }
    val engineStateDotColor: Color? = when (status.state) {
        EngineState.STOPPED, EngineState.STOPPING -> null
        else -> engineStateColor
    }

    // Search index: the same localized strings the rows below render, so the
    // search field filters on what the user actually sees (title, subtitle,
    // value). Sections and rows share one match decision per row.
    val projectFolderTitle = stringResource(R.string.settings_project_folder)
    val projectFolderSubtitle = app.workspaceStore.resolve().absolutePath
    val allFilesTitle = stringResource(R.string.settings_all_files)
    val allFilesSubtitle = stringResource(
        if (hasAllFiles) R.string.settings_all_files_granted_hint
        else R.string.settings_all_files_missing_hint,
    )
    val allFilesValue = stringResource(
        if (hasAllFiles) R.string.settings_granted else R.string.settings_not_granted,
    )
    val savedPermissionsTitle = stringResource(R.string.settings_saved_permissions)
    val savedPermissionsBody = stringResource(R.string.settings_saved_permissions_body)
    val savedPermissionsEmptyText = stringResource(R.string.settings_saved_permissions_empty)
    val savedPermissionsLoadingText = stringResource(R.string.common_loading)
    val savedPermissionsFailureText = savedPermissionsFailure?.message()
    val enginePortTitle = stringResource(R.string.settings_engine_port)
    val enginePortSubtitle = status.credentials?.baseUrl
        ?: stringResource(R.string.settings_engine_port_desc)
    val enginePortValue = status.port?.toString()
        ?: stringResource(R.string.settings_engine_inactive)
    val diagnosticsTitle = stringResource(R.string.settings_diag_title)
    val diagnosticsSubtitle = stringResource(
        R.string.settings_diag_body,
        engineStateWord(status.state),
    )
    val diagnosticsValue = engineStateWord(status.state).replaceFirstChar { it.uppercase() }
    val mcpTitle = stringResource(R.string.mcp_title)
    val mcpSubtitle = stringResource(R.string.mcp_body)
    val pluginTitle = stringResource(R.string.plugin_title)
    val pluginSubtitle = stringResource(R.string.plugin_body)
    val worktreeTitle = stringResource(R.string.worktree_title)
    val worktreeSubtitle = stringResource(R.string.worktree_body)
    val configTitle = stringResource(R.string.config_title)
    val configSubtitle = stringResource(R.string.config_body)
    val statsTitle = stringResource(R.string.stats_title)
    val statsSubtitle = stringResource(R.string.stats_body)
    val usageTitle = stringResource(R.string.usage_title)
    val usageSubtitle = stringResource(R.string.usage_body)
    val languageTitle = stringResource(R.string.settings_language)
    val languageValue = languageLabel(language)
    val providerTitle = stringResource(R.string.settings_provider_title)
    val providerSubtitle = stringResource(R.string.settings_provider_body)
    val aboutTitle = stringResource(R.string.settings_about_title)
    val aboutSubtitle = stringResource(R.string.settings_about_body)

    // App lock (Batch 7): one boolean in SharedPreferences, read once when
    // this screen opens. The availability is read once too — the sensor set
    // does not change while the screen is open.
    val lockContext = LocalContext.current
    val lockActivity = lockContext as? FragmentActivity
    var lockEnabled by remember { mutableStateOf(AppLockPrefs.isEnabled(lockContext)) }
    val lockAvailability = remember { lockAvailability(lockContext) }
    val lockTitle = stringResource(R.string.lock_title)
    val lockSubtitle = stringResource(R.string.lock_subtitle)
    val lockOnValue = stringResource(R.string.common_on)
    val lockOffValue = stringResource(R.string.common_off)
    val lockUnavailableText = stringResource(
        when (lockAvailability) {
            LockAvailability.NoHardware -> R.string.lock_unavailable_no_hardware
            LockAvailability.NoneEnrolled -> R.string.lock_unavailable_none_enrolled
            else -> R.string.lock_unavailable_generic
        },
    )
    val lockFailedMessage = stringResource(R.string.lock_failed)

    // Per-row visibility; a section renders only when at least one of its
    // rows matches. A blank query matches everything, so the screen is
    // unchanged from before searching.
    val projectFolderVisible = matchesSettingsQuery(
        query, projectFolderTitle, projectFolderSubtitle,
    )
    val allFilesVisible = matchesSettingsQuery(
        query, allFilesTitle, allFilesSubtitle, allFilesValue,
    )
    val projectVisible = projectFolderVisible || allFilesVisible
    val visibleSavedPermissions = savedPermissions.filter {
        matchesSettingsQuery(query, it.action, it.resource)
    }
    val savedPermissionsVisible = matchesSettingsQuery(
        query,
        savedPermissionsTitle,
        savedPermissionsBody,
        savedPermissionsEmptyText,
        savedPermissionsLoadingText,
        savedPermissionsFailureText,
    ) || visibleSavedPermissions.isNotEmpty()
    val enginePortVisible = matchesSettingsQuery(
        query, enginePortTitle, enginePortSubtitle, enginePortValue,
    )
    val diagnosticsVisible = matchesSettingsQuery(
        query, diagnosticsTitle, diagnosticsSubtitle, diagnosticsValue,
    )
    val mcpVisible = matchesSettingsQuery(query, mcpTitle, mcpSubtitle)
    val pluginVisible = matchesSettingsQuery(query, pluginTitle, pluginSubtitle)
    val worktreeVisible = matchesSettingsQuery(query, worktreeTitle, worktreeSubtitle)
    val configVisible = matchesSettingsQuery(query, configTitle, configSubtitle)
    val statsVisible = matchesSettingsQuery(query, statsTitle, statsSubtitle)
    val usageVisible = matchesSettingsQuery(query, usageTitle, usageSubtitle)
    val engineVisible = enginePortVisible || diagnosticsVisible || mcpVisible || pluginVisible || worktreeVisible || configVisible || statsVisible || usageVisible
    val languageVisible = matchesSettingsQuery(
        query,
        languageTitle,
        // 29-F4: match every language label, not just the active one —
        // searching for the language you want to switch TO must not hide
        // the row you need to switch with.
        languageLabel(AppLanguage.System),
        languageLabel(AppLanguage.English),
        languageLabel(AppLanguage.Indonesian),
    )
    val visibleThemeModes = ThemeMode.entries.filter { mode ->
        matchesSettingsQuery(query, themeModeLabel(mode), themeModeHint(mode))
    }
    val appearanceVisible = visibleThemeModes.isNotEmpty()
    val securityVisible = matchesSettingsQuery(
        query, lockTitle, lockSubtitle, lockOnValue, lockOffValue, lockUnavailableText,
    )
    val providerVisible = matchesSettingsQuery(query, providerTitle, providerSubtitle)
    val aboutVisible = matchesSettingsQuery(query, aboutTitle, aboutSubtitle)
    val statusCardVisible = matchesSettingsQuery(
        query,
        diagnosticsValue,
        enginePortValue,
        enginePortSubtitle,
        projectFolderSubtitle,
        allFilesValue,
    )
    val anyVisible = projectVisible || savedPermissionsVisible || engineVisible ||
        languageVisible || appearanceVisible || securityVisible || providerVisible ||
        aboutVisible || statusCardVisible

    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        ) {
            SearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = stringResource(R.string.settings_search_hint),
                imeAction = ImeAction.Search,
            )
            Spacer(Modifier.height(spacing.sm))

            // Engine status at a glance; tap opens Diagnostics.
            if (statusCardVisible) {
                EngineStatusCard(
                    stateWord = diagnosticsValue,
                    stateDotColor = engineStateDotColor,
                    portValue = enginePortValue,
                    baseUrl = enginePortSubtitle,
                    workspacePath = projectFolderSubtitle,
                    allFilesGranted = hasAllFiles,
                    allFilesValue = allFilesValue,
                    onClick = onDiagnostics,
                )
            }

            // Workspace & Permissions
            if (projectVisible) {
                Section(R.string.settings_section_project) {
                    if (projectFolderVisible) {
                        SettingsRow(
                            glyph = Glyph.Folder,
                            title = projectFolderTitle,
                            subtitle = projectFolderSubtitle,
                            onClick = onProjects,
                        )
                    }
                    if (allFilesVisible) {
                        SettingsRow(
                            glyph = Glyph.File,
                            title = allFilesTitle,
                            subtitle = allFilesSubtitle,
                            value = allFilesValue,
                            valueColor = if (hasAllFiles) colors.success else colors.warning,
                            onClick = if (hasAllFiles) null else app::requestAllFilesAccess,
                            divider = projectFolderVisible,
                        )
                    }
                }
            }

            // Saved permissions ("Always" answers), each revocable.
            if (savedPermissionsVisible) {
                Section(R.string.settings_saved_permissions) {
                    Text(
                        text = savedPermissionsBody,
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
                                text = savedPermissionsLoadingText,
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
                                text = savedPermissionsEmptyText,
                                style = OpenCodeTypography.bodySmall,
                                color = colors.onSurfaceFaint,
                                modifier = Modifier.padding(
                                    start = spacing.md,
                                    end = spacing.md,
                                    bottom = spacing.md,
                                ),
                            )
                        }
                        visibleSavedPermissions.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.settings_search_no_results),
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
                            visibleSavedPermissions.forEachIndexed { index, saved ->
                                if (index > 0) RowDivider(inset = spacing.md)
                                SavedPermissionRow(
                                    saved = saved,
                                    onRevoke = { revokeSavedPermission(saved) },
                                )
                            }
                        }
                    }
                }
            }

            // Engine & Connectivity
            if (engineVisible) {
                Section(R.string.settings_section_engine) {
                    if (enginePortVisible) {
                        SettingsRow(
                            glyph = Glyph.Code,
                            title = enginePortTitle,
                            subtitle = enginePortSubtitle,
                            value = enginePortValue,
                            valueColor = if (status.port != null) colors.neutral else colors.onSurfaceMuted,
                            valueIsMono = status.port != null,
                            onClick = onDiagnostics,
                        )
                    }
                    if (diagnosticsVisible) {
                        SettingsRow(
                            glyph = Glyph.Sliders,
                            title = diagnosticsTitle,
                            subtitle = diagnosticsSubtitle,
                            value = diagnosticsValue,
                            valueColor = engineStateColor,
                            statusDotColor = engineStateDotColor,
                            onClick = onDiagnostics,
                            divider = enginePortVisible,
                        )
                    }
                    if (mcpVisible) {
                        SettingsRow(
                            glyph = Glyph.External,
                            title = mcpTitle,
                            subtitle = mcpSubtitle,
                            onClick = onMcpServers,
                        )
                    }
                    if (pluginVisible) {
                        SettingsRow(
                            glyph = Glyph.Sparkle,
                            title = pluginTitle,
                            subtitle = pluginSubtitle,
                            onClick = onPlugins,
                        )
                    }
                    if (worktreeVisible) {
                        SettingsRow(
                            glyph = Glyph.GitBranch,
                            title = worktreeTitle,
                            subtitle = worktreeSubtitle,
                            onClick = onWorktrees,
                        )
                    }
                    if (configVisible) {
                        SettingsRow(
                            glyph = Glyph.Info,
                            title = configTitle,
                            subtitle = configSubtitle,
                            onClick = onEngineConfig,
                        )
                    }
                    if (statsVisible) {
                        SettingsRow(
                            glyph = Glyph.Pulse,
                            title = statsTitle,
                            subtitle = statsSubtitle,
                            onClick = onSessionStats,
                        )
                    }
                    if (usageVisible) {
                        SettingsRow(
                            glyph = Glyph.Sliders,
                            title = usageTitle,
                            subtitle = usageSubtitle,
                            onClick = onUsageLimits,
                        )
                    }
                }
            }

            // Preferences & Providers
            if (languageVisible) {
                Section(R.string.settings_section_language) {
                    SettingsRow(
                        glyph = Glyph.Globe,
                        title = languageTitle,
                        value = languageValue,
                        onClick = { pickingLanguage = true },
                    )
                }
            }

            // Appearance (Phase 3): the theme is picked inline, not in a
            // dialog — three radio rows, one tap to switch, applied at once
            // over the whole tree (see OpenCodeApp). System follows the
            // phone; Dark / Light pin the palette.
            if (appearanceVisible) {
                Section(R.string.settings_section_appearance) {
                    visibleThemeModes.forEachIndexed { index, mode ->
                        if (index > 0) RowDivider(inset = spacing.md)
                        ThemeModeRow(
                            mode = mode,
                            selected = mode == themeMode,
                            onSelect = { if (mode != themeMode) onThemeModeChange(mode) },
                        )
                    }
                }
            }

            // App lock (Batch 7): enabling runs one biometric prompt first
            // and only persists on success — a failed or cancelled prompt
            // leaves the lock off. Disabling is immediate: the user is
            // already past the lock to be here. When the device cannot
            // offer an unlock at all the switch is disabled and the row
            // says why instead of offering a dead toggle.
            if (securityVisible) {
                Section(R.string.settings_section_security) {
                    SettingsToggleRow(
                        glyph = Glyph.Lock,
                        title = lockTitle,
                        subtitle = if (lockAvailability == LockAvailability.Available) {
                            lockSubtitle
                        } else {
                            lockUnavailableText
                        },
                        checked = lockEnabled,
                        enabled = lockAvailability == LockAvailability.Available,
                        onCheckedChange = { wantOn ->
                            if (!wantOn) {
                                AppLockPrefs.setEnabled(lockContext, false)
                                lockEnabled = false
                            } else {
                                val activity = lockActivity ?: return@SettingsToggleRow
                                authenticateWithBiometrics(
                                    activity,
                                    lockTitle,
                                    lockSubtitle,
                                    lockFailedMessage,
                                    onSuccess = {
                                        AppLockPrefs.setEnabled(lockContext, true)
                                        lockEnabled = true
                                    },
                                    onError = {},
                                )
                            }
                        },
                    )
                }
            }

            if (providerVisible) {
                Section(R.string.settings_section_provider) {
                    SettingsRow(
                        glyph = Glyph.Key,
                        title = providerTitle,
                        subtitle = providerSubtitle,
                        onClick = onProviders,
                    )
                }
            }

            // About
            if (aboutVisible) {
                Section(R.string.settings_section_about) {
                    SettingsRow(
                        glyph = Glyph.Info,
                        title = aboutTitle,
                        subtitle = aboutSubtitle,
                        onClick = onAbout,
                    )
                }
            }

            if (query.isNotBlank() && !anyVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GlyphIcon(
                            glyph = Glyph.Search,
                            tint = colors.onSurfaceFaint,
                            size = 20.dp,
                        )
                        Text(
                            text = stringResource(R.string.settings_search_no_results),
                            style = OpenCodeTypography.bodySmall,
                            color = colors.onSurfaceMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = spacing.sm),
                        )
                    }
                }
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

/**
 * Engine status at a glance: state + port, server URL, workspace path and
 * all-files access. Tap opens Diagnostics, the same destination as the
 * Engine Port and Diagnostics rows below.
 */
@Composable
private fun EngineStatusCard(
    stateWord: String,
    stateDotColor: Color?,
    portValue: String,
    baseUrl: String,
    workspacePath: String,
    allFilesGranted: Boolean,
    allFilesValue: String,
    onClick: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    // Floating white card (White B): the status card lifts off the #F9F9FA
    // page with the shadow tokens instead of sitting as a flat grey fill.
    CardGroup {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = spacing.md, vertical = spacing.sm + 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (stateDotColor != null) {
                    StatusDot(
                        color = stateDotColor,
                        size = 8.dp,
                        modifier = Modifier.padding(end = spacing.sm),
                    )
                }
                Text(
                    text = stateWord,
                    style = OpenCodeTypography.bodyStrong,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = portValue,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
                GlyphIcon(
                    glyph = Glyph.ChevronRight,
                    tint = colors.onSurfaceFaint,
                    size = 14.dp,
                    modifier = Modifier.padding(start = spacing.xs),
                )
            }
            Text(
                text = baseUrl,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = spacing.xxs),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = spacing.xs),
            ) {
                GlyphIcon(
                    glyph = Glyph.Folder,
                    tint = colors.onSurfaceMuted,
                    size = 14.dp,
                )
                Text(
                    text = workspacePath,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = spacing.xs),
                )
                AppTag(
                    label = allFilesValue,
                    color = if (allFilesGranted) colors.success else colors.warning,
                )
            }
        }
    }
}

/**
 * A titled group: an Aura section ([HatchSection]) — sentence-case faint
 * label above an Aura card (two-layer shadow, #E9EAED border in light).
 * It replaces the Apple-style uppercase header + whiteCard group: same
 * rows, Aura treatment. Rows carry their own 12dp horizontal padding,
 * so the card itself gets 0.dp inner padding here.
 */
@Composable
private fun Section(titleRes: Int, content: @Composable ColumnScope.() -> Unit) {
    val spacing = OpenCodeTheme.tokens.spacing

    HatchSection(
        title = stringResource(titleRes),
        modifier = Modifier.padding(top = spacing.xl),
        contentPadding = 0.dp,
        content = content,
    )
}

/**
 * Grouped settings row with a neutral icon badge.
 *
 * The badge is always the same neutral fill with a muted glyph: colour is
 * reserved for semantic status (the value text and status dot), never for
 * decorating one row differently from the next.
 *
 * @param glyph The icon glyph to render inside the badge.
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
        // Neutral badge: same fill for every row, muted glyph.
        IconBadge(
            glyph = glyph,
            tint = colors.onSurface,
            background = colors.fill,
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

/**
 * Grouped settings row with a trailing switch, for on/off preferences.
 * Mirrors [SettingsRow]'s badge + title + subtitle layout; the switch
 * takes the value slot, so there is no chevron. The whole row is a button
 * that flips the switch (a11y #8: the row, not just the small switch, is
 * the tap target); tapping the switch itself fires only its own
 * onCheckedChange, never both.
 */
@Composable
private fun SettingsToggleRow(
    glyph: Glyph,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(
                role = Role.Button,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(horizontal = spacing.md, vertical = spacing.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            glyph = glyph,
            tint = colors.onSurface,
            background = colors.fill,
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
                color = if (enabled) colors.onSurface else colors.onSurfaceMuted,
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

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accentFill,
                uncheckedThumbColor = colors.onSurfaceMuted,
                uncheckedTrackColor = colors.fill,
                uncheckedBorderColor = colors.hairline,
            ),
        )
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

/** Label for one theme mode, shared by the row and the search index. */
@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.System -> R.string.settings_theme_system
        ThemeMode.Dark -> R.string.settings_theme_dark
        ThemeMode.Light -> R.string.settings_theme_light
    },
)

/** Hint for one theme mode, shared by the row and the search index. */
@Composable
private fun themeModeHint(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.System -> R.string.settings_theme_system_hint
        ThemeMode.Dark -> R.string.settings_theme_dark_hint
        ThemeMode.Light -> R.string.settings_theme_light_hint
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
            // A11y (#8c): the row is the single selectable node (see Move).
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppRadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Column(
            modifier = Modifier.padding(start = spacing.md),
        ) {
            Text(
                text = themeModeLabel(mode),
                style = OpenCodeTypography.body,
                color = colors.onSurface,
            )
            Text(
                text = themeModeHint(mode),
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

    LocalizedDraggableBottomSheet(
        onDismiss = onDismiss,
        initialStop = SheetStop.PEEK,
        title = {
            Text(
                text = stringResource(R.string.settings_language_dialog_title),
                style = OpenCodeTypography.title,
                color = colors.onSurface,
            )
        },
        content = {
            Column {
                AppLanguage.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            // A11y (#8c): the row is the single selectable node (see Move).
                            .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppRadioButton(
                            selected = option == selected,
                            onClick = null,
                            modifier = Modifier.clearAndSetSemantics { },
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
        actions = {
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
