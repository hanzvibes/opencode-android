package dev.bidzz.opencode.ui

import android.content.ComponentCallbacks
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.DragEvent
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.LanguageStore
import dev.bidzz.opencode.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.core.design.Motion
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.ThemeMode
import dev.bidzz.opencode.core.design.ThemeStore
import dev.bidzz.opencode.core.design.isNightModeActive
import dev.bidzz.opencode.core.engine.EngineCredentials
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.ui.about.AboutScreen
import dev.bidzz.opencode.ui.browser.InAppBrowserScreen
import dev.bidzz.opencode.ui.browser.isBrowsableUrl
import dev.bidzz.opencode.ui.browser.isForwardableUrl
import dev.bidzz.opencode.ui.cancelAgentDone
import dev.bidzz.opencode.ui.chat.ChatScreen
import dev.bidzz.opencode.ui.chat.ChatUiState
import dev.bidzz.opencode.ui.chat.ChatViewModel
import dev.bidzz.opencode.ui.chat.MIME_WORKSPACE_PATH
import dev.bidzz.opencode.ui.chat.SharedPrefsParkedComposerStore
import dev.bidzz.opencode.ui.chat.acceptsDropMimeTypes
import dev.bidzz.opencode.ui.chat.droppedContent
import dev.bidzz.opencode.ui.files.SharedPrefsFileDraftStore
import dev.bidzz.opencode.ui.chat.readSafFile
import dev.bidzz.opencode.ui.diagnostics.DiagnosticsScreen
import dev.bidzz.opencode.ui.diff.DiffListScreen
import dev.bidzz.opencode.ui.diff.DiffScreen
import dev.bidzz.opencode.ui.diff.SessionDiffScreen
import dev.bidzz.opencode.ui.diff.SessionDiffViewModel
import dev.bidzz.opencode.ui.inbox.InboxScreen
import dev.bidzz.opencode.ui.inbox.InboxViewModel
import dev.bidzz.opencode.ui.instructions.InstructionsScreen
import dev.bidzz.opencode.ui.instructions.InstructionsViewModel
import dev.bidzz.opencode.ui.schedule.ScheduleScreen
import dev.bidzz.opencode.ui.schedule.ScheduleViewModel
import dev.bidzz.opencode.ui.sessionlog.SessionLogScreen
import dev.bidzz.opencode.ui.sessionlog.SessionLogViewModel
import dev.bidzz.opencode.ui.export.ExportScreen
import dev.bidzz.opencode.ui.export.ExportViewModel
import dev.bidzz.opencode.ui.move.MoveSessionScreen
import dev.bidzz.opencode.ui.move.MoveSessionViewModel
import dev.bidzz.opencode.ui.stats.StatsScreen
import dev.bidzz.opencode.ui.stats.StatsViewModel
import dev.bidzz.opencode.ui.usage.UsageLimitsScreen
import dev.bidzz.opencode.ui.usage.UsageLimitsViewModel
import dev.bidzz.opencode.ui.files.DiffViewModel
import dev.bidzz.opencode.ui.files.ExplorerScreen
import dev.bidzz.opencode.ui.files.ExplorerViewModel
import dev.bidzz.opencode.ui.files.FileViewScreen
import dev.bidzz.opencode.ui.files.FileViewViewModel
import dev.bidzz.opencode.ui.artifacts.ArtifactsScreen
import dev.bidzz.opencode.ui.artifacts.ArtifactsViewModel
import dev.bidzz.opencode.ui.generate.GenerateScreen
import dev.bidzz.opencode.ui.generate.GenerateViewModel
import dev.bidzz.opencode.ui.jobs.JobsScreen
import dev.bidzz.opencode.ui.jobs.JobsViewModel
import dev.bidzz.opencode.ui.websearch.WebSearchScreen
import dev.bidzz.opencode.ui.websearch.WebSearchViewModel
import dev.bidzz.opencode.ui.mcp.McpServersScreen
import dev.bidzz.opencode.ui.mcp.McpServersViewModel
import dev.bidzz.opencode.ui.plugin.PluginsScreen
import dev.bidzz.opencode.ui.plugin.PluginsViewModel
import dev.bidzz.opencode.ui.config.ConfigScreen
import dev.bidzz.opencode.ui.config.ConfigViewModel
import dev.bidzz.opencode.ui.worktree.WorktreesScreen
import dev.bidzz.opencode.ui.worktree.WorktreesViewModel
import dev.bidzz.opencode.ui.onboarding.OnboardingScreen
import dev.bidzz.opencode.ui.onboarding.OnboardingStore
import dev.bidzz.opencode.ui.onboarding.SampleProject
import dev.bidzz.opencode.ui.projects.ProjectOption
import dev.bidzz.opencode.ui.projects.ProjectPickerScreen
import dev.bidzz.opencode.ui.providers.ProvidersScreen
import dev.bidzz.opencode.ui.providers.ProvidersViewModel
import dev.bidzz.opencode.ui.chat.ReadPositionStore
import dev.bidzz.opencode.ui.sessions.PinnedSessionStore
import dev.bidzz.opencode.ui.sessions.SessionListPane
import dev.bidzz.opencode.ui.sessions.SessionListScreen
import dev.bidzz.opencode.ui.sessions.SessionListUiState
import dev.bidzz.opencode.ui.sessions.SessionListViewModel
import dev.bidzz.opencode.ui.sessions.SessionRow
import dev.bidzz.opencode.ui.updateLastSessionShortcut
import dev.bidzz.opencode.ui.widget.updateSessionWidget
import dev.bidzz.opencode.ui.settings.SettingsScreen
import java.io.File
import kotlinx.coroutines.launch

/**
 * Top-level navigation.
 *
 * A sealed screen value rather than a navigation library: four destinations, no
 * deep links, no back stack beyond "return to chat". A graph would be machinery
 * without a job.
 */
internal sealed interface Screen {
    data object Chat : Screen
    data object Sessions : Screen
    data object Projects : Screen
    data object Files : Screen
    /**
     * Where Back returns to (audit-200 C10, M-160): a file is opened from
     * the Files list and from a chat tool call, and each of them is the
     * right place to come back to. Same pattern as [About.returnTo] (C6)
     * and [InAppBrowser.returnTo] (B7); like them, the return target is
     * not carried in the saved key, so a restore after process death
     * returns to Files.
     */
    data class FileView(
        val path: String,
        val returnTo: Screen = Screen.Files,
        /**
         * Content-search hit to land on (audit-200 C16, M-238): the
         * query is pre-filled in the find bar and the viewer scrolls
         * to the first match at or after [findLine]. Null means a plain
         * open. Like [returnTo], not carried in the saved key — a
         * restore after process death opens the file plainly.
         */
        val findQuery: String? = null,
        val findLine: Int? = null,
    ) : Screen
    data object DiffList : Screen
    data class Diff(val file: String) : Screen
    data object SessionDiff : Screen
    data class Inbox(val sessionID: String) : Screen
    data class MoveSession(val sessionID: String) : Screen
    data class SessionExport(val sessionID: String) : Screen
    data class Instructions(val sessionID: String) : Screen
    /** Scheduled prompts for one session (audit-200 C17, M-259). */
    data class Schedules(val sessionID: String) : Screen
    /** The durable event log of one session (audit-200 C17, M-259). */
    data class SessionLog(val sessionID: String) : Screen
    data object SessionStats : Screen
    data object UsageLimits : Screen
    data object Settings : Screen
    data object Providers : Screen
    /**
     * Where Back returns to (audit-200 C10, M-157): Diagnostics opens from
     * Settings and from the chat (status pill, drawer), and each of them
     * is the right place to come back to. Same pattern as [About.returnTo]
     * (C6); the return target is not carried in the saved key, so a
     * restore after process death returns to Chat.
     */
    data class Diagnostics(val returnTo: Screen = Screen.Chat) : Screen
    data object McpServers : Screen
    data object Plugins : Screen
    data object Artifacts : Screen
    data object Jobs : Screen
    data object Generate : Screen
    data object WebSearch : Screen
    data object Worktrees : Screen
    data object EngineConfig : Screen
    /**
     * Where Back returns to (audit-200 C6, M-109): About opens from
     * Settings and from the drawer footer, and each of them is the right
     * place to come back to — not always Chat, not always Settings. Same
     * pattern as [InAppBrowser.returnTo] (B7, M-158); like the browser,
     * the return target is not carried in the saved key, so a restore
     * after process death returns to Chat.
     */
    data class About(val returnTo: Screen = Screen.Chat) : Screen
    /** Optional Basic auth for engine URLs (artifact views); public links pass none. */
    /**
     * Where Back returns to (audit-200 B7, M-158): the browser is opened
     * from chat links, Artifacts and WebSearch, and each of them is the
     * right place to come back to — not always Chat.
     */
    data class InAppBrowser(
        val url: String,
        val basicAuth: Pair<String, String>? = null,
        val returnTo: Screen = Screen.Chat,
    ) : Screen

    /**
     * Short string key for [ScreenSaver] (audit-100 69-F4): the current
     * screen survives a recreate instead of resetting to Chat. The
     * `data class` screens carry one String payload after the colon.
     */
    fun toSavedKey(): String = when (this) {
        Chat -> "chat"
        Sessions -> "sessions"
        Projects -> "projects"
        Files -> "files"
        is FileView -> "fileview:$path"
        DiffList -> "difflist"
        is Diff -> "diff:$file"
        SessionDiff -> "sessiondiff"
        is Inbox -> "inbox:$sessionID"
        is MoveSession -> "movesession:$sessionID"
        is SessionExport -> "sessionexport:$sessionID"
        is Instructions -> "instructions:$sessionID"
        is Schedules -> "schedules:$sessionID"
        is SessionLog -> "sessionlog:$sessionID"
        SessionStats -> "sessionstats"
        UsageLimits -> "usagelimits"
        Settings -> "settings"
        Providers -> "providers"
        is Diagnostics -> "diagnostics"
        McpServers -> "mcpservers"
        Plugins -> "plugins"
        Artifacts -> "artifacts"
        Jobs -> "jobs"
        Generate -> "generate"
        WebSearch -> "websearch"
        Worktrees -> "worktrees"
        EngineConfig -> "engineconfig"
        is About -> "about"
        is InAppBrowser -> "browser:$url"
    }
}

/** Inverse of [Screen.toSavedKey]; an unknown key falls back to Chat. */
internal fun screenFromSavedKey(key: String): Screen {
    // A blank payload is a missing payload (an empty path, session id or
    // URL names nothing) — it falls back to Chat like an unknown key.
    val (kind, payload) = key.split(":", limit = 2).let {
        it[0] to it.getOrNull(1)?.takeIf { part -> part.isNotEmpty() }
    }
    return when (kind) {
        "chat" -> Screen.Chat
        "sessions" -> Screen.Sessions
        "projects" -> Screen.Projects
        "files" -> Screen.Files
        "fileview" -> payload?.let { Screen.FileView(it) } ?: Screen.Chat
        "difflist" -> Screen.DiffList
        "diff" -> payload?.let { Screen.Diff(it) } ?: Screen.Chat
        "sessiondiff" -> Screen.SessionDiff
        "inbox" -> payload?.let { Screen.Inbox(it) } ?: Screen.Chat
        "movesession" -> payload?.let { Screen.MoveSession(it) } ?: Screen.Chat
        "sessionexport" -> payload?.let { Screen.SessionExport(it) } ?: Screen.Chat
        "instructions" -> payload?.let { Screen.Instructions(it) } ?: Screen.Chat
        "schedules" -> payload?.let { Screen.Schedules(it) } ?: Screen.Chat
        "sessionlog" -> payload?.let { Screen.SessionLog(it) } ?: Screen.Chat
        "sessionstats" -> Screen.SessionStats
        "usagelimits" -> Screen.UsageLimits
        "settings" -> Screen.Settings
        "providers" -> Screen.Providers
        "diagnostics" -> Screen.Diagnostics()
        "mcpservers" -> Screen.McpServers
        "plugins" -> Screen.Plugins
        "artifacts" -> Screen.Artifacts
        "jobs" -> Screen.Jobs
        "generate" -> Screen.Generate
        "websearch" -> Screen.WebSearch
        "worktrees" -> Screen.Worktrees
        "engineconfig" -> Screen.EngineConfig
        "about" -> Screen.About()
        "browser" -> payload?.let { Screen.InAppBrowser(it) } ?: Screen.Chat
        else -> Screen.Chat
    }
}

/** Saves the current [Screen] across a recreate (69-F4). */
internal val ScreenSaver: Saver<Screen, String> =
    Saver(save = { it.toSavedKey() }, restore = { screenFromSavedKey(it) })

/**
 * Basic auth for an engine URL that lost it on restore (audit-200 C10,
 * M-002): the saved browser key holds only the URL, so a restored
 * [Screen.InAppBrowser] has `basicAuth == null` and an engine URL would
 * load without credentials and show the engine's 401 page. The engine's
 * password is the same for every URL on its loopback base, so the current
 * credentials re-authenticate it; a URL outside the engine's base is a
 * public link and keeps no auth. This only covers restores while the
 * engine is up: after a full process death the engine restarts on a new
 * ephemeral port, so a restored artifact URL points at a dead port
 * regardless of auth — that half needs a stable port, not this fix.
 */
internal fun engineBasicAuthFor(url: String, credentials: EngineCredentials?): Pair<String, String>? {
    val creds = credentials ?: return null
    if (url != creds.baseUrl && !url.startsWith(creds.baseUrl + "/")) return null
    return "opencode" to creds.password
}

/**
 * Where back goes from [this]: the screen it was opened from.
 *
 * The same targets as each screen's own back arrow, so the arrow and the system gesture
 * agree. A single "always Chat" handler skipped the parent: back from a file jumped past
 * the file list, back from Providers past Settings.
 */
/**
 * Whether the Files detail pane shows a file (audit-200 C11, M-164):
 * expanded widths only, and only when a file is actually open. An empty
 * path means "nothing open" — the placeholder shows instead of the
 * eternal spinner the view model's initial loading state used to paint.
 */
internal fun fileDetailVisible(isExpanded: Boolean, path: String): Boolean =
    isExpanded && path.isNotEmpty()

internal fun Screen.parent(): Screen = when (this) {
    is Screen.FileView -> this.returnTo
    Screen.DiffList -> Screen.Files
    is Screen.Diff -> Screen.DiffList
    Screen.Providers -> Screen.Settings
    Screen.McpServers -> Screen.Settings
    Screen.Plugins -> Screen.Settings
    Screen.Artifacts -> Screen.Settings
    Screen.Jobs -> Screen.Settings
    Screen.Generate -> Screen.Settings
    Screen.WebSearch -> Screen.Settings
    Screen.Worktrees -> Screen.Settings
    Screen.EngineConfig -> Screen.Settings
    is Screen.Inbox -> Screen.Sessions
    is Screen.MoveSession -> Screen.Sessions
    is Screen.SessionExport -> Screen.Sessions
    is Screen.Instructions -> Screen.Sessions
    // Audit-200 C17 (M-259): schedules and the session log open from the
    // chat status dialog, so Back returns to Chat, not Sessions.
    is Screen.Schedules -> Screen.Chat
    is Screen.SessionLog -> Screen.Chat
    Screen.SessionStats -> Screen.Settings
    Screen.UsageLimits -> Screen.Settings
    is Screen.About -> this.returnTo
    // Audit-200 C10 (M-157): Diagnostics returns to the screen that
    // opened it (Settings or Chat), not always to Chat.
    is Screen.Diagnostics -> this.returnTo
    // Audit-200 B7 (M-158): the browser returns to the screen that
    // opened it (chat, Artifacts or WebSearch), not always to Chat.
    is Screen.InAppBrowser -> this.returnTo
    else -> Screen.Chat
}

/**
 * Depth in the navigation hierarchy (Animation C): the screen-transition
 * slide direction. Chat is the root; a deeper screen slides up from
 * below on entry, a shallower one slides down — so forward and back read
 * differently instead of every transition sliding the same way.
 */
internal fun Screen.depth(): Int = when (this) {
    Screen.Chat -> 0
    Screen.Sessions -> 1
    Screen.Projects, Screen.Files, Screen.Settings, is Screen.Diagnostics -> 2
    is Screen.FileView, Screen.DiffList, Screen.SessionDiff,
    Screen.Providers, Screen.McpServers, Screen.Plugins, Screen.Artifacts, Screen.Jobs,
    Screen.Generate, Screen.WebSearch,
    Screen.Worktrees,
    Screen.EngineConfig, is Screen.About, is Screen.InAppBrowser,
    is Screen.Inbox, is Screen.MoveSession, is Screen.SessionExport,
    is Screen.Instructions, is Screen.Schedules, is Screen.SessionLog,
    Screen.SessionStats, Screen.UsageLimits -> 3
    is Screen.Diff -> 4
}

/**
 * Animation C shared press feedback: one pattern for every tappable
 * card/row — shrink to 0.97 on press, spring back on release with
 * [Motion.springResponsive]. The caller hoists a single
 * [InteractionSource] and passes the same instance to its `clickable`
 * and here, so the scale tracks the real press state (and the default
 * ripple indication is untouched).
 */
@Composable
internal fun Modifier.pressScale(
    motion: Motion,
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = motion.springResponsive(),
        label = "press_scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Animation C shared-element pilot: the key for the session-title flight
 * between a session row and the chat header. Both sides must call this
 * with the session id, so the keys can never drift apart. The key is the
 * raw session id: it is unique per session, and this is the only shared
 * pair in the app, so no namespace prefix is needed.
 */
internal fun sessionTitleSharedKey(sessionId: String): String = sessionId

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun OpenCodeApp(app: OpenCodeApplication) {
    // 69-F4: the current screen, the scope toggle and the open file
    // survive a recreate (fold, rotation) instead of resetting to Chat.
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Chat) }
    var sessionScopeToProject by rememberSaveable { mutableStateOf(true) }

    // #4d: one host for every snackbar in the app, overlaid at the root
    // (BottomCenter) below — see the collector in LaunchedEffect(Unit).
    val snackbarHostState = remember { SnackbarHostState() }

    // 69-F14: the snackbar showing when the activity is recreated (fold,
    // resize, process death). It was already consumed from the flow, so
    // without this the message — and its action, e.g. Retry — vanished
    // without a trace. The message survives in saved state and is
    // re-shown by the collector below; the action lambda cannot survive,
    // so the re-shown snackbar carries the label but no action.
    var inflightSnackbarMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var inflightSnackbarActionLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var inflightSnackbarLong by rememberSaveable { mutableStateOf(false) }

    // File open in the files list-detail pane (expanded widths only).
    // 69-F4: saveable, so a recreate does not close the open file.
    var selectedFilePath by rememberSaveable { mutableStateOf<String?>(null) }

    val engineStatus by app.engineStatus.collectAsStateWithLifecycle()

    val chatViewModel: ChatViewModel = viewModel(factory = viewModelFactory { extras ->
        ChatViewModel(
            app.engineApi,
            { app.workspaceStore.resolve().absolutePath },
            savedStateHandle = extras.createSavedStateHandle(),
            onSessionCleared = { app.lastSessionID = null },
            // Parked composers survive process death on disk (Fase 1a, 57-F1).
            parkedStore = SharedPrefsParkedComposerStore(app),
            // Audit-200 A9-3 (DL-029): the session with a run in flight, so
            // the next engine start knows an orphan may still be working.
            onBusySessionChanged = { app.lastBusySessionID = it },
        )
    })

    val sessionViewModel: SessionListViewModel = viewModel(factory = viewModelFactory { _ ->
        SessionListViewModel(
            app.engineApi,
            { app.workspaceStore.resolve().absolutePath },
            PinnedSessionStore(app),
            ReadPositionStore(app),
        )
    })

    val chatState by chatViewModel.state.collectAsStateWithLifecycle()
    val sessionsState by sessionViewModel.state.collectAsStateWithLifecycle()

    /**
     * Deletes a session from every entry point (80-F1/80-F6): the chat
     * moves away from the session and its parked composer is pruned ONLY
     * after the engine confirms the delete. The old call sites called
     * newSession() first, so a failed delete left the session alive with
     * its live view destroyed — and the draft parked under the deleted id
     * sat in the store forever. `chatState` is read when the callback
     * runs, not when the delete is tapped, so a session opened meanwhile
     * is not abandoned.
     */
    val onDeleteSession: (String) -> Unit = { id ->
        // Audit-200 C6 (M-108): stop the run before the delete leaves —
        // interrupting after the engine confirms it would target a
        // session id that no longer exists.
        chatViewModel.interruptSessionIfBusy(id)
        sessionViewModel.delete(id) { deletedId ->
            if (chatState.sessionID == deletedId) {
                chatViewModel.newSession()
            }
            chatViewModel.pruneParkedSession(deletedId)
        }
    }

    val explorerViewModel: ExplorerViewModel = viewModel(factory = viewModelFactory { _ ->
        ExplorerViewModel(
            app.engineApi,
            { app.workspaceStore.resolve().absolutePath },
            // Audit-200 C16 (M-238): the bundled `rg` for content
            // search — the same binary the agent's grep tool uses,
            // linked into filesDir/engine/tools/bin at startup.
            rgBinary = {
                java.io.File(app.filesDir, "engine/tools/bin/rg").takeIf { it.canExecute() }
            },
        )
    })
    val fileViewModel: FileViewViewModel = viewModel(factory = viewModelFactory { extras ->
        FileViewViewModel(
            app.engineApi,
            { app.workspaceStore.resolve().absolutePath },
            onSaved = explorerViewModel::invalidateQuickOpenIndex,
            savedStateHandle = extras.createSavedStateHandle(),
            // Audit-200 A9-3 (DL-033): the draft on disk too, so a swipe
            // or force-stop — which clears the handle — still finds it.
            fileDraftStore = SharedPrefsFileDraftStore(app),
        )
    })
    val diffViewModel: DiffViewModel = viewModel(factory = viewModelFactory { _ ->
        DiffViewModel(app.engineApi, { app.workspaceStore.resolve().absolutePath })
    })
    val sessionDiffViewModel: SessionDiffViewModel = viewModel(factory = viewModelFactory { _ ->
        SessionDiffViewModel(app.engineApi)
    })

    val inboxViewModel: InboxViewModel = viewModel(factory = viewModelFactory { _ ->
        InboxViewModel(app.engineApi)
    })

    val moveSessionViewModel: MoveSessionViewModel = viewModel(factory = viewModelFactory { _ ->
        MoveSessionViewModel(app.engineApi)
    })

    val exportViewModel: ExportViewModel = viewModel(factory = viewModelFactory { _ ->
        ExportViewModel(app.engineApi)
    })

    val instructionsViewModel: InstructionsViewModel = viewModel(factory = viewModelFactory { _ ->
        InstructionsViewModel(app.engineApi)
    })

    val scheduleViewModel: ScheduleViewModel = viewModel(factory = viewModelFactory { _ ->
        ScheduleViewModel(app.engineApi)
    })

    val sessionLogViewModel: SessionLogViewModel = viewModel(factory = viewModelFactory { _ ->
        SessionLogViewModel(app.engineApi)
    })

    val statsViewModel: StatsViewModel = viewModel(factory = viewModelFactory { _ ->
        StatsViewModel(app.engineApi)
    })

    val usageLimitsViewModel: UsageLimitsViewModel = viewModel(factory = viewModelFactory { _ ->
        UsageLimitsViewModel(app.engineApi)
    })
    // Shared by the MCP and Providers screens (audit-200 B4, M-457): a
    // provider restart must ask first when runtime MCP changes are
    // pending. Created here — one instance for both view models — not in
    // either view model, so the two never drift apart.
    val mcpRuntimeChangesSignal = remember { dev.bidzz.opencode.ui.mcp.McpRuntimeChangesSignal() }

    val providersViewModel: ProvidersViewModel = viewModel(factory = viewModelFactory { _ ->
        ProvidersViewModel(
            api = app.engineApi,
            configStore = dev.bidzz.opencode.engine.EngineConfigStore(app.engineEnvironment.configHome),
            restartEngine = app::restartEngine,
            mcpRuntimeChanges = mcpRuntimeChangesSignal,
        )
    })

    val mcpServersViewModel: McpServersViewModel = viewModel(factory = viewModelFactory { _ ->
        McpServersViewModel(app.engineApi, mcpRuntimeChangesSignal)
    })

    val pluginsViewModel: PluginsViewModel = viewModel(factory = viewModelFactory { _ ->
        PluginsViewModel(app.engineApi)
    })

    val artifactsViewModel: ArtifactsViewModel = viewModel(factory = viewModelFactory { _ ->
        ArtifactsViewModel(app.engineApi)
    })

    val jobsViewModel: JobsViewModel = viewModel(factory = viewModelFactory { _ ->
        JobsViewModel(app.engineApi)
    })

    val generateViewModel: GenerateViewModel = viewModel(factory = viewModelFactory { _ ->
        // Audit-200 A9-3 (DL-011): the last generation on disk, so a
        // prompt — and a paid image result — survive process death.
        GenerateViewModel(app.engineApi, app.filesDir)
    })

    val webSearchViewModel: WebSearchViewModel = viewModel(factory = viewModelFactory { _ ->
        WebSearchViewModel(app.engineApi)
    })

    val worktreesViewModel: WorktreesViewModel = viewModel(factory = viewModelFactory { _ ->
        WorktreesViewModel(app.engineApi)
    })

    val configViewModel: ConfigViewModel = viewModel(factory = viewModelFactory { _ ->
        ConfigViewModel(app.engineApi)
    })

    val explorerState by explorerViewModel.state.collectAsStateWithLifecycle()
    val fileState by fileViewModel.state.collectAsStateWithLifecycle()
    val diffState by diffViewModel.state.collectAsStateWithLifecycle()
    val sessionDiffState by sessionDiffViewModel.state.collectAsStateWithLifecycle()
    val inboxState by inboxViewModel.state.collectAsStateWithLifecycle()
    val moveSessionState by moveSessionViewModel.state.collectAsStateWithLifecycle()
    val exportState by exportViewModel.state.collectAsStateWithLifecycle()
    val instructionsState by instructionsViewModel.state.collectAsStateWithLifecycle()
    val scheduleState by scheduleViewModel.state.collectAsStateWithLifecycle()
    val sessionLogState by sessionLogViewModel.state.collectAsStateWithLifecycle()
    val statsState by statsViewModel.state.collectAsStateWithLifecycle()
    val usageLimitsState by usageLimitsViewModel.state.collectAsStateWithLifecycle()
    val providersState by providersViewModel.state.collectAsStateWithLifecycle()
    val mcpServersState by mcpServersViewModel.state.collectAsStateWithLifecycle()
    val pluginsState by pluginsViewModel.state.collectAsStateWithLifecycle()
    val artifactsState by artifactsViewModel.state.collectAsStateWithLifecycle()
    val jobsState by jobsViewModel.state.collectAsStateWithLifecycle()
    val generateState by generateViewModel.state.collectAsStateWithLifecycle()
    val webSearchState by webSearchViewModel.state.collectAsStateWithLifecycle()
    val worktreesState by worktreesViewModel.state.collectAsStateWithLifecycle()
    val configState by configViewModel.state.collectAsStateWithLifecycle()

    // Adaptive layout (Phase 3): at >=840dp the chat drawer becomes a
    // persistent sidebar and the sessions/files screens gain a detail
    // pane. Below it every screen keeps its phone behaviour exactly.
    val windowSizeClass = rememberWindowSizeClass()
    val isExpanded = windowSizeClass.isExpandedWidth

    // Feed engine state into the view model; it decides when to attach the API.
    LaunchedEffect(engineStatus.state, engineStatus.credentials?.baseUrl) {
        chatViewModel.onEngineStatus(engineStatus)
    }

    // Restore the last conversation once the engine is reachable. The engine keeps
    // running across a task swipe, so returning to the app should show the session
    // that is still there rather than an empty screen.
    // The stored workspace may have been unusable, so the app fell back to the
    // internal one (item 12): say so once per path, instead of on every engine
    // restart, and let the user re-pick it in Projects.
    var workspaceUnavailableNoted by remember { mutableStateOf<String?>(null) }
    // Audit-200 B5 (M-230): keyed on the credentials too, not only the
    // state. The engine's credentials rotate on every restart (new
    // baseUrl + password) while the state can stay RUNNING — with only
    // the state as the key this effect never re-ran, so the freshly
    // attached repository (see the attach effect above, which IS keyed
    // on baseUrl) never restored the remembered session. The attach
    // effect is declared first and attaches synchronously, so by the
    // time this runs the repository is the new one and onResume's own
    // guards (no session open, no pending deep link) decide.
    LaunchedEffect(engineStatus.state, engineStatus.credentials?.baseUrl) {
        if (engineStatus.state == EngineState.RUNNING) {
            chatViewModel.onResume(app.lastSessionID)
        }
        val unavailable = app.workspaceStore.lastUnavailablePath
        if (unavailable != null && unavailable != workspaceUnavailableNoted) {
            workspaceUnavailableNoted = unavailable
            chatViewModel.reportWorkspaceUnavailable()
        } else if (unavailable == null) {
            workspaceUnavailableNoted = null
        }
    }

    LaunchedEffect(chatState.sessionID) {
        chatState.sessionID?.let {
            app.lastSessionID = it
            // 55-F7: the session is open now — its agent-done notification
            // (if any) is stale; the user is looking at the answer.
            cancelAgentDone(app, it)
        }
    }

    // External entries (Native C): shared text and deep links arrive via
    // the application flows, not navigation arguments — the activity can
    // be re-created for them while the UI is already composed. A share
    // starts a fresh session with the text as the draft (editable before
    // sending); a deep link opens its session.
    LaunchedEffect(Unit) {
        // 15-F4: the SAF collector lives in the view model, not the chat
        // screen — a read started in chat survives leaving the screen.
        chatViewModel.observeSafPicks(app.safFilePicked) { uri ->
            readSafFile(app.contentResolver, uri)
        }
        launch {
            app.sharedTextReceived.collect { text ->
                chatViewModel.newSession()
                chatViewModel.onDraftChange(text)
                screen = Screen.Chat
            }
        }
        launch {
            app.sessionOpenRequested.collect { sessionID ->
                chatViewModel.openSession(sessionID)
                screen = Screen.Chat
            }
        }
        launch {
            app.newSessionRequested.collect {
                chatViewModel.newSession()
                screen = Screen.Chat
            }
        }
        // #4d: snackbars, one at a time. showSnackbar suspends until the
        // snackbar is dismissed, so a burst queues in the flow's buffer
        // (capacity 16) instead of overlapping on screen.
        launch {
            // 69-F14: re-show the snackbar that was on screen when the
            // activity was recreated — its message survived in saved
            // state, the action did not (see the note at the declaration).
            inflightSnackbarMessage?.let { message ->
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = inflightSnackbarActionLabel,
                    duration = if (inflightSnackbarLong) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                inflightSnackbarMessage = null
                inflightSnackbarActionLabel = null
            }
            app.snackbarRequests.collect { request ->
                inflightSnackbarMessage = request.message
                inflightSnackbarActionLabel = request.actionLabel
                inflightSnackbarLong = request.long
                val result = snackbarHostState.showSnackbar(
                    message = request.message,
                    actionLabel = request.actionLabel,
                    duration = if (request.long) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                inflightSnackbarMessage = null
                inflightSnackbarActionLabel = null
                if (result == SnackbarResult.ActionPerformed) request.onAction?.invoke()
            }
        }
    }

    // Load history whenever the screen is shown, and reload when the scope flips.
    // At expanded widths the chat sidebar shows the same list persistently,
    // so Chat loads it too — otherwise the sidebar would stay empty.
    LaunchedEffect(screen, sessionScopeToProject, isExpanded) {
        if (screen is Screen.Sessions || (isExpanded && screen is Screen.Chat)) {
            sessionViewModel.load(sessionScopeToProject)
        }
    }

    // A session created or opened in chat is not in the loaded rows yet;
    // reload once so the persistent sidebar (expanded) lists it and can
    // highlight it. Guarded on !loading so the entry load above does not
    // double-fire while it is still in flight; keyed only on the session
    // and the width class, so a finished load never re-triggers it.
    LaunchedEffect(chatState.sessionID, isExpanded) {
        val id = chatState.sessionID
        if (isExpanded && (screen is Screen.Chat || screen is Screen.Sessions) &&
            id != null && !sessionsState.loading &&
            sessionsState.rows.none { it.id == id }
        ) {
            sessionViewModel.load(sessionScopeToProject)
        }
    }

    // Load the explorer when it first appears or the project changes.
    LaunchedEffect(screen, engineStatus.credentials?.baseUrl) {
        if (screen is Screen.Files) explorerViewModel.load()
    }

    LaunchedEffect(screen) {
        when (val target = screen) {
            is Screen.FileView -> fileViewModel.requestLoad(target.path)
            is Screen.Diff -> diffViewModel.load(target.file)
            else -> Unit
        }
    }

    // The session diff loads for the active session's latest turn — and it
    // needs its own effect (audit-200 B6, M-159): after a process death the
    // restored screen is SessionDiff, but chatState.sessionID is still null
    // until the engine comes up and onResume restores it, so the screen
    // effect above fired once with no id and never again. Keyed on the id
    // too, the load re-fires the moment the id lands.
    LaunchedEffect(screen, chatState.sessionID) {
        if (screen is Screen.SessionDiff) {
            // The active session's latest turn; no session, no diff — the
            // entry point only exists while a session is open.
            chatState.sessionID?.let { sessionDiffViewModel.load(it) }
        }
    }

    // The detail pane follows what the viewer actually loaded: when a switch
    // is held for the user's decision (item 10) the highlight must stay on
    // the file still on screen, and move only once the new file loads.
    LaunchedEffect(fileState.path, isExpanded) {
        if (isExpanded && fileState.path.isNotEmpty()) selectedFilePath = fileState.path
    }

    // Back returns to the screen this one was opened from; from Chat it leaves the app.
    BackHandler(enabled = screen !is Screen.Chat) { screen = screen.parent() }

    // The chosen language is applied here, over the whole tree, so switching it in Settings
    // redraws every screen in place instead of restarting the activity.
    val hostContext = LocalContext.current
    var language by remember { mutableStateOf(LanguageStore.get(hostContext)) }
    val localized = remember(language) { LanguageStore.wrap(hostContext, language) }

    // The chosen theme is applied the same way: picked in Settings, read
    // once here, redraws the whole tree in place.
    var themeMode by remember { mutableStateOf(ThemeStore.get(hostContext)) }

    // Audit-200 C8 (M-130): System mode must follow the phone while the
    // app stays open. It did not: the manifest holds `uiMode` in
    // `configChanges`, so the activity is never recreated on a system
    // theme flip — and `isSystemInDarkTheme()` reads `LocalConfiguration`,
    // which this file shadows below with the language-wrapped copy, a
    // `Configuration` object the framework mutates in place, so Compose
    // never sees a new value. Instead the app watches the system value
    // itself: `ComponentCallbacks` fires on every configuration change
    // even when the activity handles it, and the state redraws the tree.
    // The value is read from the UNWRAPPED host context — the wrapped
    // copy only carries the language override.
    var systemDark by remember { mutableStateOf(isNightModeActive(hostContext.resources.configuration)) }
    DisposableEffect(hostContext) {
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                systemDark = isNightModeActive(newConfig)
            }

            override fun onLowMemory() = Unit
        }
        hostContext.applicationContext.registerComponentCallbacks(callbacks)
        onDispose { hostContext.applicationContext.unregisterComponentCallbacks(callbacks) }
    }

    // First-run onboarding (Phase 3): shown once, while the workspace is
    // still empty. Read once on first composition; dismissing or finishing
    // marks it completed in prefs, so it never reappears.
    var showOnboarding by remember {
        mutableStateOf(OnboardingStore.shouldShow(hostContext, app.workspaceStore))
    }

    // The launcher shortcut follows the last opened session (Native C):
    // its label is the session title when the list knows it. Placed
    // after hostContext on purpose — the effect reads it.
    LaunchedEffect(chatState.sessionID, sessionsState.rows) {
        val id = chatState.sessionID ?: return@LaunchedEffect
        val title = sessionsState.rows.find { it.id == id }?.title
        updateLastSessionShortcut(hostContext, id, title)
        updateSessionWidget(hostContext, id, title)
    }

    // Agent-done notification (Native C): the service watcher is the
    // only trigger now (see EngineService.updateAgentDoneWatcher). The
    // UI trigger that used to live here — a bare `wasBusy && !busy` —
    // is gone (audit-200 C16, M-236): it fired on any busy→false
    // transition, including `repo.open(B)` emitting busy=false after a
    // deep link into a session that never ran anything, and posted a
    // "finished" notification for that innocent session. The watcher
    // instead reads the session id from the terminal event itself, so a
    // switch can never invent a notification — and it covers the
    // backgrounded-but-alive case the UI trigger was kept for, because
    // it checks `isAppForeground` rather than composition. The
    // session-open cancel below stays: opening a session still clears
    // its (stale) notification, since the user is looking at the answer.

    // Links in chat open in the in-app browser (Native C): http/https
    // stay inside the app; only mailto: and a strictly-shaped tel: go
    // to the system (Fase 5, 53-F4 — the old code forwarded every
    // scheme, so a crafted link could open the dialer with MMI codes
    // or any other app). Anything else is ignored, never crashed on.
    // MarkdownText's safe handler delegates to this ambient handler,
    // so no call site needs threading.
    val browserUriHandler = object : UriHandler {
        override fun openUri(uri: String) {
            if (isBrowsableUrl(uri)) {
                screen = Screen.InAppBrowser(uri)
            } else if (isForwardableUrl(uri)) {
                runCatching {
                    hostContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                }
            }
        }
    }

    // Drag & drop into the composer (Batch 7, audit-200 C16 M-237):
    // files dropped from another app arrive as content:// URIs and are
    // read with the SAF rules; a workspace file dragged from the
    // Explorer arrives as a path under the private MIME type and becomes
    // an @path mention. The listener lives on the composition root view
    // because Compose 1.7's dragAndDropTarget cannot read the dropped
    // ClipData (see DropAttachments.kt) — and it lives HERE, in the
    // always-composed root, not in ChatScreen: the old home disposed it
    // the moment the user left Chat for Files, so a drag started in the
    // Explorer had no target at all in normal single-window use (the
    // only way it could land was a second window). The composer bounds
    // are hoisted here for the same reason: ChatScreen writes them,
    // this listener reads them.
    //
    // In Chat the composer bounds decide, as before — a drop outside
    // the composer is ignored. Anywhere else a drop of an accepted type
    // navigates to Chat first (one finger is holding the drag, so the
    // user cannot navigate themselves) and then delivers to the view
    // model, which parks the mention/file under the dropped session
    // when the user has since moved on (A9-2, DL-017/DL-018).
    val dropComposerBounds = remember { mutableStateOf<Rect?>(null) }
    var dragOverComposer by remember { mutableStateOf(false) }
    val dropRootView = LocalView.current
    DisposableEffect(dropRootView, screen, chatState.sessionID) {
        val inChat = screen is Screen.Chat
        val dropSessionId = chatState.sessionID
        val listener = View.OnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    val description = event.clipDescription
                        ?: return@OnDragListener false
                    val types = (0 until description.mimeTypeCount)
                        .map { description.getMimeType(it) }
                        .toSet()
                    acceptsDropMimeTypes(types)
                }
                DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_LOCATION -> {
                    dragOverComposer = inChat && dropComposerBounds.value
                        ?.contains(Offset(event.x, event.y)) == true
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    dragOverComposer = false
                    true
                }
                DragEvent.ACTION_DROP -> {
                    dragOverComposer = false
                    if (inChat) {
                        val inside = dropComposerBounds.value
                            ?.contains(Offset(event.x, event.y)) == true
                        if (!inside) return@OnDragListener false
                    }
                    val content = droppedContent(event.clipData)
                    if (content.uris.isEmpty() && content.texts.isEmpty()) {
                        return@OnDragListener false
                    }
                    // Only a drag carrying the private path MIME type may
                    // insert mentions: a URI drop from another app can
                    // carry a text label too, and that label is not a
                    // workspace path.
                    val pathDrop = event.clipDescription
                        ?.hasMimeType(MIME_WORKSPACE_PATH) == true
                    if (!inChat) screen = Screen.Chat
                    if (pathDrop) {
                        chatViewModel.handleDroppedPaths(content.texts, dropSessionId)
                    }
                    if (content.uris.isNotEmpty()) {
                        chatViewModel.handleDroppedFiles(content.uris, dropSessionId) { uri ->
                            readSafFile(app.contentResolver, uri)
                        }
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    dragOverComposer = false
                    true
                }
                else -> false
            }
        }
        dropRootView.setOnDragListener(listener)
        onDispose { dropRootView.setOnDragListener(null) }
    }

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalAppContext provides localized,
        LocalUriHandler provides browserUriHandler,
    ) {
    OpenCodeTheme(themeMode = themeMode, systemDark = systemDark) {
        // Animation C: screen transitions slide 24dp with a 320ms
        // emphasized fade. The pixel offset is resolved once here,
        // because a transitionSpec cannot read LocalDensity.
        val motion = OpenCodeTheme.tokens.motion
        val screenSlidePx = with(LocalDensity.current) { 24.dp.roundToPx() }
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (showOnboarding) {
                // First run, empty workspace: the 3-step flow replaces the
                // navigation until it is dismissed or finished. Either way it
                // is marked completed, so it never shows again.
                OnboardingScreen(
                    workspacePath = app.workspaceStore.resolve().absolutePath,
                    hasAllFilesAccess = app.workspaceStore.hasAllFilesAccess(),
                    onChooseFolder = app::requestWorkspaceFolder,
                    onGrantAllFilesAccess = app::requestAllFilesAccess,
                    onOpenProviders = {
                        OnboardingStore.markCompleted(hostContext)
                        showOnboarding = false
                        screen = Screen.Providers
                    },
                    onCreateSample = {
                        SampleProject.seed(app.workspaceStore.resolve())
                    },
                    onDismiss = {
                        OnboardingStore.markCompleted(hostContext)
                        showOnboarding = false
                    },
                )
            } else SharedTransitionLayout {
                // Animation C shared-element pilot: the SharedTransitionScope
                // both sides of the session-title flight share. Captured here,
                // once, so the row and the header create their shared states
                // in the same scope — a state created in any other scope
                // would never match its pair.
                val sharedTransitionScope = this
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        // Deeper = forward: the new screen slides up from below
                        // and the old one slides up out; back reverses it, so
                        // forward and back read differently instead of every
                        // transition sliding the same way. Only whole-screen
                        // changes animate — the expanded-width Sessions branch
                        // below keeps its list-detail Row as-is.
                        val forward = targetState.depth() >= initialState.depth()
                        val enterOffset = if (forward) screenSlidePx else -screenSlidePx
                        val exitOffset = if (forward) -screenSlidePx else screenSlidePx
                        (fadeIn(animationSpec = motion.tweenEmphasized()) +
                            slideInVertically(animationSpec = motion.tweenEmphasized()) { enterOffset }) togetherWith
                            (fadeOut(animationSpec = motion.tweenEmphasized()) +
                                slideOutVertically(animationSpec = motion.tweenEmphasized()) { exitOffset })
                    },
                    label = "screen_transition",
                ) { target ->
                    // The AnimatedContent scope the shared elements need: each
                    // side passes its own invocation's scope, and the shared
                    // transition matches them by key.
                    val animatedVisibilityScope = this@AnimatedContent
                    when (target) {
                    Screen.Chat -> ChatDestination(
                        chatState = chatState,
                        sessionsState = sessionsState,
                        chatViewModel = chatViewModel,
                        sessionViewModel = sessionViewModel,
                        app = app,
                        sessionScopeToProject = sessionScopeToProject,
                        onNavigate = { screen = it },
                        isExpanded = isExpanded,
                        showSidebar = true,
                        // Shared-element pilot: the chat header is the
                        // destination side of the session-title flight.
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        fileIndex = explorerState.quickOpenIndex,
                        fileIndexLoading = explorerState.quickOpenLoading,
                        fileIndexFailed = explorerState.quickOpenFailed,
                        onEnsureFileIndex = explorerViewModel::ensureQuickOpenIndex,
                        composerBounds = dropComposerBounds,
                        dragOverComposer = dragOverComposer,
                    )

                    Screen.Files -> ExplorerScreen(
                        state = explorerState,
                        onBack = { screen = Screen.Chat },
                        onOpenDirectory = { row -> explorerViewModel.open(row.path) },
                        onOpenFile = { row -> screen = Screen.FileView(row.path) },
                        onBreadcrumb = { path -> explorerViewModel.open(path) },
                        onUp = { explorerViewModel.up() },
                        onRefresh = { explorerViewModel.load() },
                        onDiff = { screen = Screen.DiffList },
                        // Files list-detail (Phase 3, W-C): at expanded widths a
                        // file opens into the detail pane beside the list instead
                        // of navigating to the full-screen viewer. The detail
                        // opener must stay gated on width: the list routes files
                        // to it whenever it is non-null, so passing it on a phone
                        // would swallow the tap — the file would load invisibly
                        // and never navigate anywhere.
                        selectedFilePath = if (isExpanded) selectedFilePath else null,
                        // Audit-200 C11 (M-164): the detail pane only
                        // exists when a file is actually open. The view
                        // model's state is never null, so passing it
                        // through unconditionally painted the initial
                        // loading=true state as an eternal "Reading
                        // file…" spinner before any file was tapped.
                        fileState = if (fileDetailVisible(isExpanded, fileState.path)) fileState else null,
                        onOpenFileDetail = if (isExpanded) {
                            { row ->
                                if (fileViewModel.requestLoad(row.path)) selectedFilePath = row.path
                            }
                        } else {
                            null
                        },
                        onCloseFileDetail = {
                            // Audit-200 C11 (M-165): closing the detail
                            // pane resets the viewer to "nothing open".
                            // Clearing only the highlight left fileState
                            // non-null, so the pane stayed rendered and
                            // the close button — and system Back, which
                            // lands here through FileViewScreen — did
                            // nothing at all.
                            selectedFilePath = null
                            fileViewModel.clear()
                        },
                        onFileStartEdit = fileViewModel::startEditing,
                        onFileDraftChange = fileViewModel::editDraft,
                        onFileSave = { fileViewModel.save() },
                        onFileCancelEdit = fileViewModel::stopEditing,
                        onFileOverwrite = { fileViewModel.save(overwrite = true) },
                        onFileReloadFromDisk = fileViewModel::reloadFromDisk,
                        onFileRetryLoad = fileViewModel::retryLoad,
                        onFileDismissConflict = fileViewModel::dismissConflict,
                        onFileLoadFull = fileViewModel::loadFull,
                        onFileConfirmSwitchDiscard = fileViewModel::confirmSwitchDiscard,
                        onFileCancelSwitch = fileViewModel::cancelSwitch,
                        onEnsureQuickOpenIndex = explorerViewModel::ensureQuickOpenIndex,
                        onToggleHidden = explorerViewModel::toggleHidden,
                        onSearchContent = explorerViewModel::searchContent,
                        // Audit-200 C16 (M-238): a content-search hit
                        // opens the file with the query in its find bar,
                        // scrolled to the hit's line.
                        onOpenContentResult = { path, line, query ->
                            screen = Screen.FileView(path, findQuery = query, findLine = line)
                        },
                        // Audit-200 B7 (M-022): the embedded viewer's
                        // image/PDF previews resolve against this root.
                        workspaceRoot = fileViewModel.workspaceRoot(),
                    )

                    is Screen.FileView -> FileViewScreen(
                        state = fileState,
                        // Audit-200 C10 (M-160): back goes where the file
                        // came from — Files, or Chat for a chat tool call.
                        onBack = { screen = screen.parent() },
                        onStartEdit = fileViewModel::startEditing,
                        onDraftChange = fileViewModel::editDraft,
                        onSave = { fileViewModel.save() },
                        onCancelEdit = fileViewModel::stopEditing,
                        onOverwrite = { fileViewModel.save(overwrite = true) },
                        onReloadFromDisk = fileViewModel::reloadFromDisk,
                        onRetryLoad = fileViewModel::retryLoad,
                        onDismissConflict = fileViewModel::dismissConflict,
                        onConfirmSwitchDiscard = fileViewModel::confirmSwitchDiscard,
                        onCancelSwitch = fileViewModel::cancelSwitch,
                        // Audit-200 C16 (M-238): a content-search hit
                        // lands with its query in the find bar.
                        initialFindQuery = target.findQuery ?: "",
                        initialFindLine = target.findLine,
                        // Audit-200 B7 (M-022): image/PDF previews resolve
                        // the relative path against the workspace root.
                        workspaceRoot = fileViewModel.workspaceRoot(),
                    )

                    Screen.DiffList -> DiffListScreen(
                        changed = explorerState.changed,
                        onBack = { screen = Screen.Files },
                        onOpen = { file -> screen = Screen.Diff(file) },
                    )

                    is Screen.Diff -> DiffScreen(
                        state = diffState,
                        onBack = { screen = Screen.DiffList },
                        onRetry = { diffViewModel.load(target.file) },
                    )

                    Screen.SessionDiff -> SessionDiffScreen(
                        state = sessionDiffState,
                        onBack = { screen = Screen.Chat },
                        onOpen = sessionDiffViewModel::openFile,
                        onCloseFile = sessionDiffViewModel::closeFile,
                        onRetry = { chatState.sessionID?.let { sessionDiffViewModel.load(it) } },
                    )

                    is Screen.InAppBrowser -> InAppBrowserScreen(
                        url = target.url,
                        // Audit-200 C10 (M-002): a restored browser screen
                        // carries no auth (the saved key holds only the
                        // URL). Re-supply the current engine credentials
                        // when the URL belongs to this engine; a public
                        // link keeps none.
                        basicAuth = target.basicAuth ?: engineBasicAuthFor(target.url, engineStatus.credentials),
                        onBack = { screen = target.returnTo },
                    )

                    Screen.Sessions -> if (isExpanded) {
                        // List-detail: the session list stays visible beside the
                        // conversation. Selecting a row opens it in the detail pane
                        // without leaving this screen, and a new session opens
                        // there too; the detail chat has no sidebar of its own
                        // because the list is already here. Back still returns to
                        // Chat (see the BackHandler above).
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(OpenCodeTheme.colors.background),
                        ) {
                            SessionListPane(
                                state = sessionsState,
                                onOpen = { row -> chatViewModel.openSession(row.id) },
                                onToggleScope = { sessionScopeToProject = !sessionScopeToProject },
                                onRename = { row, newTitle ->
                                    sessionViewModel.rename(row.id, newTitle)
                                },
                                onDelete = { row -> onDeleteSession(row.id) },
                                onTogglePin = { row ->
                                    sessionViewModel.togglePin(row.id)
                                },
                                onInbox = { row ->
                                    inboxViewModel.load(row.id)
                                    screen = Screen.Inbox(row.id)
                                },
                                onMove = { row ->
                                    moveSessionViewModel.load(row.id)
                                    screen = Screen.MoveSession(row.id)
                                },
                                onExport = { row ->
                                    exportViewModel.load(row.id)
                                    screen = Screen.SessionExport(row.id)
                                },
                                onInstructions = { row ->
                                    instructionsViewModel.load(row.id)
                                    screen = Screen.Instructions(row.id)
                                },
                                details = sessionsState.details,
                                selectedSessionId = chatState.sessionID,
                                onRefresh = { sessionViewModel.load(sessionScopeToProject) },
                                onDismissDeleteFailure = sessionViewModel::clearDeleteFailure,
                                onDismissRenameFailure = sessionViewModel::clearRenameFailure,
                                onDismissLoadFailure = sessionViewModel::clearLoadFailure,
                                onLoadMore = sessionViewModel::loadMore,
                                // Audit-200 C5 (M-089): the pane reports
                                // the rows actually visible, so previews
                                // are walked lazily instead of eagerly.
                                onVisibleSessions = sessionViewModel::requestPreviews,
                                // Shared-element pilot: NO flight here. This pane
                                // and the detail chat beside it are composed at
                                // the same time, so the same session title would
                                // exist twice under one key — a shared key may
                                // only exist once per transition. A row tap here
                                // only updates content (no screen change), so no
                                // flight is expected either. Null scopes disable
                                // the shared modifier on both sides.
                                sharedTransitionScope = null,
                                animatedVisibilityScope = null,
                                modifier = Modifier
                                    .width(ListPaneWidth)
                                    .fillMaxHeight()
                                    // The pane sits in a bare Row, not a scaffold,
                                    // so it pads for the system bars itself; the
                                    // detail chat does the same in its own column.
                                    .statusBarsPadding()
                                    .navigationBarsPadding(),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(OpenCodeTheme.tokens.dimensions.hairline)
                                    .background(OpenCodeTheme.colors.hairline),
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            ) {
                                ChatDestination(
                                    chatState = chatState,
                                    sessionsState = sessionsState,
                                    chatViewModel = chatViewModel,
                                    sessionViewModel = sessionViewModel,
                                    app = app,
                                    sessionScopeToProject = sessionScopeToProject,
                                    onNavigate = { screen = it },
                                    isExpanded = true,
                                    showSidebar = false,
                                    // Shared-element pilot: null, like the list
                                    // pane beside it — the same title is on
                                    // screen twice here (see the pane above).
                                    sharedTransitionScope = null,
                                    animatedVisibilityScope = null,
                                    fileIndex = explorerState.quickOpenIndex,
                                    fileIndexLoading = explorerState.quickOpenLoading,
                                    fileIndexFailed = explorerState.quickOpenFailed,
                                    onEnsureFileIndex = explorerViewModel::ensureQuickOpenIndex,
                                    composerBounds = dropComposerBounds,
                                    dragOverComposer = dragOverComposer,
                                )
                            }
                        }
                    } else {
                        SessionListScreen(
                            state = sessionsState,
                            onBack = { screen = Screen.Chat },
                            onOpen = { row: SessionRow ->
                                chatViewModel.openSession(row.id)
                                screen = Screen.Chat
                            },
                            details = sessionsState.details,
                            onToggleScope = { sessionScopeToProject = !sessionScopeToProject },
                            onNewSession = {
                                chatViewModel.newSession()
                                screen = Screen.Chat
                            },
                            onRename = { row, newTitle ->
                                sessionViewModel.rename(row.id, newTitle)
                            },
                            onDelete = { row -> onDeleteSession(row.id) },
                            onTogglePin = { row ->
                                sessionViewModel.togglePin(row.id)
                            },
                            onInbox = { row ->
                                inboxViewModel.load(row.id)
                                screen = Screen.Inbox(row.id)
                            },
                            onMove = { row ->
                                moveSessionViewModel.load(row.id)
                                screen = Screen.MoveSession(row.id)
                            },
                            onExport = { row ->
                                exportViewModel.load(row.id)
                                screen = Screen.SessionExport(row.id)
                            },
                            onInstructions = { row ->
                                instructionsViewModel.load(row.id)
                                screen = Screen.Instructions(row.id)
                            },
                            onRefresh = { sessionViewModel.load(sessionScopeToProject) },
                            onDismissDeleteFailure = sessionViewModel::clearDeleteFailure,
                            onDismissRenameFailure = sessionViewModel::clearRenameFailure,
                            onDismissLoadFailure = sessionViewModel::clearLoadFailure,
                            onLoadMore = sessionViewModel::loadMore,
                            // Audit-200 C5 (M-089): History reports the
                            // rows actually visible, so previews are
                            // walked lazily instead of eagerly.
                            onVisibleSessions = sessionViewModel::requestPreviews,
                            // Shared-element pilot: the full-screen list is the
                            // origin side of the session-title flight.
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }

                    Screen.Projects -> {
                        val current = app.workspaceStore.resolve().absolutePath
                        ProjectPickerScreen(
                            currentPath = current,
                            internalPath = app.workspaceStore.internalPath(),
                            hasAllFilesAccess = app.workspaceStore.hasAllFilesAccess(),
                            options = buildProjectOptions(app, current),
                            onBack = { screen = Screen.Chat },
                            onSelect = { option ->
                                // A refused switch stays on this screen (item 30):
                                // starting a new session and navigating away would
                                // pretend the switch happened.
                                if (app.switchWorkspace(File(option.path))) {
                                    // A different project means a different session: the engine
                                    // binds a session to the directory it was created in.
                                    // Audit-200 B4 (M-458): the fresh session waits for
                                    // the restarted engine — newSession() would build
                                    // it on the OLD handle, and a prompt sent before
                                    // the restart lands would edit the old project.
                                    chatViewModel.newSessionAfterRestart()
                                    // ... and a different file index: the cached one lists
                                    // the old project's files (item 27).
                                    explorerViewModel.invalidateQuickOpenIndex()
                                    screen = Screen.Chat
                                } else {
                                    app.showSnackbar(
                                        app.getString(R.string.workspace_too_broad),
                                        long = true,
                                    )
                                }
                            },
                            onChooseFolder = { app.requestWorkspaceFolder() },
                            onGrantAccess = { app.requestAllFilesAccess() },
                        )
                    }

                    Screen.Settings -> SettingsScreen(
                        app = app,
                        language = language,
                        onLanguageChange = { chosen ->
                            LanguageStore.set(hostContext, chosen)
                            language = chosen
                        },
                        themeMode = themeMode,
                        onThemeModeChange = { chosen ->
                            ThemeStore.set(hostContext, chosen)
                            themeMode = chosen
                        },
                        onBack = { screen = Screen.Chat },
                        onDiagnostics = { screen = Screen.Diagnostics(Screen.Settings) },
                        onProjects = { screen = Screen.Projects },
                        onProviders = { screen = Screen.Providers },
                        onMcpServers = { screen = Screen.McpServers },
                        onPlugins = { screen = Screen.Plugins },
                        onArtifacts = { screen = Screen.Artifacts },
                        onJobs = { screen = Screen.Jobs },
                        onGenerate = { screen = Screen.Generate },
                        onWebSearch = { screen = Screen.WebSearch },
                        onWorktrees = { screen = Screen.Worktrees },
                        onEngineConfig = { screen = Screen.EngineConfig },
                        onSessionStats = { screen = Screen.SessionStats },
                        onUsageLimits = { screen = Screen.UsageLimits },
                        onAbout = { screen = Screen.About(Screen.Settings) },
                    )

                    is Screen.About -> AboutScreen(
                        versionName = remember {
                            runCatching { hostContext.packageManager.getPackageInfo(hostContext.packageName, 0).versionName }
                                .getOrNull().orEmpty()
                        },
                        // Audit-200 C6 (M-109): back goes where About came
                        // from — the same returnTo the system back uses
                        // via parent(), not a hardcoded screen.
                        onBack = { screen = screen.parent() },
                    )

                    Screen.Providers -> ProvidersScreen(
                        state = providersState,
                        onBack = { screen = Screen.Settings },
                        onRefresh = providersViewModel::refresh,
                        onSaveKey = providersViewModel::saveKey,
                        onSaveCustom = providersViewModel::saveCustomProvider,
                        onDeleteCredential = providersViewModel::deleteCredential,
                        onReplaceCredential = providersViewModel::replaceCredential,
                        onDeleteCustomProvider = providersViewModel::deleteCustomProvider,
                        patchReport = engineStatus.antigravityPatch,
                        // Audit-200 B7 (M-209): whether the post-start
                        // re-patch poll has given up waiting for the plugin.
                        patchPollEnded = engineStatus.antigravityPatchPollEnded,
                        onSelectAntigravityAccount = providersViewModel::selectAntigravityAccount,
                        onSetAntigravityAccountEnabled = providersViewModel::setAntigravityAccountEnabled,
                        onTestAntigravity = providersViewModel::testAntigravityConnection,
                        onDeleteJsoncConfig = providersViewModel::deleteJsoncConfig,
                        onConfirmRestart = providersViewModel::confirmRestart,
                        onDismissRestart = providersViewModel::dismissRestart,
                        // Audit-200 B7 (M-206): restarts the engine so a
                        // patch that landed after start takes effect.
                        onRestartForPatch = providersViewModel::restartForPatch,
                    )

                    is Screen.Diagnostics -> DiagnosticsScreen(
                        app = app,
                        // Audit-200 C10 (M-157): back goes where
                        // Diagnostics came from — the same returnTo the
                        // system back uses via parent(), not Chat.
                        onBack = { screen = screen.parent() },
                    )

                    Screen.McpServers -> McpServersScreen(
                        state = mcpServersState,
                        onBack = { screen = Screen.Settings },
                        onRefresh = mcpServersViewModel::refresh,
                        onConnect = mcpServersViewModel::connect,
                        onDisconnect = mcpServersViewModel::disconnect,
                        onRemove = mcpServersViewModel::remove,
                        onAdd = mcpServersViewModel::addServer,
                        onClearActionFailure = mcpServersViewModel::clearActionFailure,
                        isRuntimeOnly = mcpServersViewModel::isRuntimeOnly,
                        onClearActionSuccess = mcpServersViewModel::clearActionSuccess,
                        onRestartEngine = app::restartEngine,
                        // Audit-200 C17 (M-262): OAuth re-auth for NEEDS_AUTH servers.
                        onReauth = mcpServersViewModel::beginReauth,
                        onSubmitReauthCode = mcpServersViewModel::submitReauthCode,
                        onCancelReauth = mcpServersViewModel::cancelReauth,
                        onClearReauthError = mcpServersViewModel::clearReauthError,
                    )

                    Screen.Plugins -> PluginsScreen(
                        state = pluginsState,
                        onBack = { screen = Screen.Settings },
                        onRefresh = pluginsViewModel::refresh,
                        onCheckUpdates = { pluginsViewModel.checkUpdates() },
                        onUpdate = { target -> pluginsViewModel.update(listOf(target)) },
                        onClearActionFailure = pluginsViewModel::clearActionFailure,
                    )

                    Screen.Artifacts -> ArtifactsScreen(
                        state = artifactsState,
                        onBack = { screen = Screen.Settings },
                        onRefresh = artifactsViewModel::refresh,
                        onOpen = { artifact ->
                            // The view URL is a relative engine path; the
                            // browser needs the engine credentials for it
                            // (Basic auth, like every engine call). The
                            // list only loads with a running engine, so
                            // credentials are present whenever this fires.
                            engineStatus.credentials?.let { creds ->
                                screen = Screen.InAppBrowser(
                                    url = creds.baseUrl + artifact.url,
                                    basicAuth = "opencode" to creds.password,
                                    // Audit-200 B7 (M-158): back returns to
                                    // Artifacts, where the link was tapped.
                                    returnTo = Screen.Artifacts,
                                )
                            }
                        },
                        onDelete = artifactsViewModel::delete,
                        onClearActionFailure = artifactsViewModel::clearActionFailure,
                    )

                    Screen.Jobs -> JobsScreen(
                        state = jobsState,
                        onBack = { screen = Screen.Settings },
                        onRefresh = jobsViewModel::refresh,
                        onKindFilter = jobsViewModel::setKindFilter,
                        onStatusFilter = jobsViewModel::setStatusFilter,
                    )

                    Screen.Generate -> GenerateScreen(
                        state = generateState,
                        onBack = { screen = Screen.Settings },
                        onRefreshModels = generateViewModel::refreshModels,
                        onPromptChange = generateViewModel::setPrompt,
                        onModeChange = generateViewModel::setMode,
                        onModelChange = generateViewModel::setModel,
                        onAspectChange = generateViewModel::setAspect,
                        onResolutionChange = generateViewModel::setResolution,
                        onGenerate = generateViewModel::generate,
                    )

                    Screen.WebSearch -> WebSearchScreen(
                        state = webSearchState,
                        onBack = { screen = Screen.Settings },
                        onRefreshProviders = webSearchViewModel::refreshProviders,
                        onQueryChange = webSearchViewModel::setQuery,
                        onProviderChange = webSearchViewModel::setProvider,
                        onSearch = webSearchViewModel::search,
                        // Audit-200 B7 (M-158): back returns to WebSearch,
                        // where the link was tapped.
                        onOpenUrl = { url -> screen = Screen.InAppBrowser(url, returnTo = Screen.WebSearch) },
                    )

                    Screen.Worktrees -> WorktreesScreen(
                        state = worktreesState,
                        onBack = { screen = Screen.Settings },
                        onRefresh = worktreesViewModel::refresh,
                    )

                    Screen.EngineConfig -> ConfigScreen(
                        state = configState,
                        onBack = { screen = Screen.Settings },
                        onRefresh = configViewModel::refresh,
                    )

                    is Screen.Inbox -> InboxScreen(
                        sessionID = target.sessionID,
                        state = inboxState,
                        onBack = { screen = Screen.Sessions },
                        // Audit-200 C17 (M-258): the list re-reads when an
                        // inbox event arrives while the screen is open.
                        inboxUpdatedAtMs = chatState.inboxUpdatedAtMs,
                        // Audit-200 C17 (M-257): cancel and delivery change.
                        onCancel = inboxViewModel::cancel,
                        onSetDelivery = inboxViewModel::setDelivery,
                        onClearActionFailure = inboxViewModel::clearActionFailure,
                        // Audit-200 B6 (M-594): refresh() is a no-op when
                        // the view model's session id was lost to a process
                        // death, so the screen stuck on its loading spinner
                        // forever after restore. The screen already knows
                        // the id — load with it directly.
                        onRefresh = { inboxViewModel.load(target.sessionID) },
                    )

                    is Screen.MoveSession -> MoveSessionScreen(
                        sessionID = target.sessionID,
                        state = moveSessionState,
                        onBack = { screen = Screen.Sessions },
                        // Audit-200 B6 (M-594): see Inbox above — load with
                        // the screen's id, not the view model's lost one.
                        onRefresh = { moveSessionViewModel.load(target.sessionID) },
                        onSelect = moveSessionViewModel::select,
                        onMove = moveSessionViewModel::move,
                        onMoved = {
                            sessionViewModel.load(sessionScopeToProject)
                            screen = Screen.Sessions
                        },
                    )

                    is Screen.SessionExport -> ExportScreen(
                        sessionID = target.sessionID,
                        state = exportState,
                        onBack = { screen = Screen.Sessions },
                        // Audit-200 B6 (M-594): see Inbox above — load with
                        // the screen's id, not the view model's lost one.
                        onRefresh = { exportViewModel.load(target.sessionID) },
                    )

                    is Screen.Instructions -> InstructionsScreen(
                        sessionID = target.sessionID,
                        state = instructionsState,
                        onBack = { screen = Screen.Sessions },
                        // Audit-200 B6 (M-594): see Inbox above — load with
                        // the screen's id, not the view model's lost one.
                        onRefresh = { instructionsViewModel.load(target.sessionID) },
                        onKeyChange = instructionsViewModel::setKey,
                        onValueChange = instructionsViewModel::setValue,
                        onSave = instructionsViewModel::save,
                        onEdit = instructionsViewModel::edit,
                        onDelete = instructionsViewModel::delete,
                    )

                    // Audit-200 C17 (M-259): schedules and the session log
                    // open from the chat status dialog, so Back returns to
                    // Chat. Load with the screen's id (M-594), not the view
                    // model's lost one.
                    is Screen.Schedules -> ScheduleScreen(
                        sessionID = target.sessionID,
                        state = scheduleState,
                        onBack = { screen = Screen.Chat },
                        onRefresh = { scheduleViewModel.load(target.sessionID) },
                        onCreate = scheduleViewModel::create,
                        onPause = scheduleViewModel::pause,
                        onResume = scheduleViewModel::resume,
                        onDelete = scheduleViewModel::delete,
                        onClearActionFailure = scheduleViewModel::clearActionFailure,
                    )

                    is Screen.SessionLog -> SessionLogScreen(
                        sessionID = target.sessionID,
                        state = sessionLogState,
                        onBack = { screen = Screen.Chat },
                        onRefresh = { sessionLogViewModel.load(target.sessionID) },
                    )

                    Screen.SessionStats -> StatsScreen(
                        state = statsState,
                        onBack = { screen = Screen.Settings },
                        onRefresh = statsViewModel::refresh,
                    )

                    Screen.UsageLimits -> UsageLimitsScreen(
                        state = usageLimitsState,
                        onBack = { screen = Screen.Settings },
                        onRefresh = usageLimitsViewModel::refresh,
                    )
                    }
                }
            }

            // #4d: the one snackbar host for the whole app,
            // overlaid at the bottom — default Material3 colors.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    }
}

/**
 * The chat destination, shared by the Chat screen and the detail pane of
 * the expanded sessions list-detail layout: one place for the whole
 * ChatScreen wiring, so the two call sites cannot drift apart. Navigation
 * goes through [onNavigate]; [isExpanded]/[showSidebar] pick the shell
 * (modal drawer, persistent sidebar, or bare detail — see ChatScreen).
 */
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun ChatDestination(
    chatState: ChatUiState,
    sessionsState: SessionListUiState,
    chatViewModel: ChatViewModel,
    sessionViewModel: SessionListViewModel,
    app: OpenCodeApplication,
    sessionScopeToProject: Boolean,
    onNavigate: (Screen) -> Unit,
    isExpanded: Boolean,
    showSidebar: Boolean,
    /**
     * Animation C shared-element pilot: the scopes the chat header needs
     * for the session-title flight. Null (the expanded sessions detail
     * pane) disables the flight — see the call sites in [OpenCodeApp].
     */
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    /** Workspace file index for the @-mention / attach picker. */
    fileIndex: List<dev.bidzz.opencode.ui.files.FileRow> = emptyList(),
    fileIndexLoading: Boolean = false,
    fileIndexFailed: Boolean = false,
    onEnsureFileIndex: () -> Unit = {},
    /**
     * Drag & drop state hoisted to [OpenCodeApp] (audit-200 C16, M-237):
     * the drop listener lives on the composition root, but the composer
     * that draws the hover highlight is here, so the bounds and the
     * hover flag travel down through this destination.
     */
    composerBounds: MutableState<Rect?>,
    dragOverComposer: Boolean,
) {
    ChatScreen(
        state = chatState,
        sessions = sessionsState.rows,
        sessionsLoading = sessionsState.loading,
        sessionsFailure = sessionsState.failure,
        sessionsFailureKey = sessionsState.failureKey,
        onOpenSession = chatViewModel::openSession,
        // Audit-200 C17 (M-258): the inbox chip above the composer opens
        // the queue for the session on screen; without one there is no
        // queue to open.
        onOpenInbox = chatState.sessionID?.let { id -> { onNavigate(Screen.Inbox(id)) } } ?: {},
        // Audit-200 C17 (M-263): generic-form answers and cancels.
        onSubmitForm = chatViewModel::submitForm,
        onCancelForm = chatViewModel::cancelForm,
        onClearFormFailure = chatViewModel::clearFormFailure,
        // Audit-200 C17 (M-259): schedules, the session log, and skill
        // activation live in the status dialog; without a session there
        // is nothing to open or activate.
        onOpenSchedules = chatState.sessionID?.let { id -> { onNavigate(Screen.Schedules(id)) } },
        onOpenSessionLog = chatState.sessionID?.let { id -> { onNavigate(Screen.SessionLog(id)) } },
        onActivateSkill = chatState.sessionID?.let { chatViewModel::activateSkill },
        onClearSkillFailure = chatViewModel::clearSkillFailure,
        onDrawerOpen = { sessionViewModel.load(sessionScopeToProject) },
        // Audit-200 C5 (M-089): the drawer reports the rows actually
        // visible, so previews are walked lazily instead of eagerly.
        onVisibleSessions = sessionViewModel::requestPreviews,
        onDraftChange = chatViewModel::onDraftChange,
        onSend = chatViewModel::send,
        onFollowUpPick = chatViewModel::sendFollowUp,
        onFindQuery = chatViewModel::setFindQuery,
        onFindNext = chatViewModel::findNext,
        onFindPrevious = chatViewModel::findPrevious,
        onClearFind = chatViewModel::clearFind,
        onStop = chatViewModel::stop,
        onPermissionReply = chatViewModel::replyPermission,
        onAgent = chatViewModel::selectAgent,
        onModel = chatViewModel::selectModel,
        onStatusClick = { onNavigate(Screen.Diagnostics(Screen.Chat)) },
        onNewSession = chatViewModel::newSession,
        // Audit-200 C6 (M-104/M-105): the dialog's loads are user-triggered
        // (open/expand), so they always refresh — see loadBranches.
        onLoadBranches = { chatViewModel.loadBranches(force = true) },
        // Turn diff (Tier 2): only while a session is open — without one
        // there is no latest turn to diff, and the dialog hides the entry.
        // Audit-200 C5 (M-103): not while the turn is still running
        // either — a live turn's diff would compare against the working
        // copy with no provisional label, so the entry hides until the
        // run ends, the same treatment Compact already gets.
        onTurnDiff = if (chatState.sessionID != null && !chatState.busy) { { onNavigate(Screen.SessionDiff) } } else null,
        onDismissError = chatViewModel::dismissError,
        onRetryError = chatViewModel::retryError,
        onLoadEarlier = chatViewModel::loadEarlier,
        onReauth = app::restartEngine,
        onRestartEngine = app::restartEngine,
        onEditMessage = { id, text -> chatViewModel.startEdit(id, text) },
        onResendMessage = chatViewModel::resendMessage,
        onForkMessage = chatViewModel::forkMessage,
        onRevertMessage = chatViewModel::stageRevert,
        onCompact = chatViewModel::requestCompact,
        onForkSession = chatViewModel::forkSession,
        onCommitRevert = chatViewModel::commitRevert,
        onClearRevert = chatViewModel::clearRevert,
        onAnswerQuestion = chatViewModel::answerQuestion,
        // Audit-200 C3: skipping a question (M-066), picking a skill
        // from the palette (M-067), and the queued row's Send/Cancel
        // (M-069) all land in the view model.
        onSkipQuestion = chatViewModel::skipQuestion,
        onSkillPick = chatViewModel::pickSkill,
        onFlushQueue = chatViewModel::flushQueueNow,
        onCancelQueue = chatViewModel::cancelQueued,
        onRemoveQueuedEntry = chatViewModel::removeQueuedEntry,
        onOpenToolFile = { path -> onNavigate(Screen.FileView(path, Screen.Chat)) },
        onRequestDiffRevert = chatViewModel::requestDiffRevert,
        onDiffDecision = chatViewModel::recordDiffDecision,
        onSaveCode = { name, code -> app.requestCodeSave(name, code) },
        onRunCode = chatViewModel::runCodeBlock,
        onStopCodeRun = chatViewModel::stopCodeRun,
        onDismissCodeRun = chatViewModel::dismissCodeRun,
        onSubmitEdit = chatViewModel::submitEdit,
        onCancelEdit = chatViewModel::cancelEdit,
        onDeferPermission = chatViewModel::deferPermission,
        onRestoreDeferredPermission = chatViewModel::restoreDeferredPermission,
        onRemoveAttachment = chatViewModel::removeAttachment,
        composerBounds = composerBounds,
        dragOverComposer = dragOverComposer,
        onDismissAttachNotice = chatViewModel::dismissAttachNotice,
        onDismissQueueNotice = chatViewModel::dismissQueueNotice,
        onDismissStaleSelection = chatViewModel::clearStaleSelection,
        onRequestSafFile = app::requestSafFile,
        onRequestCameraPhoto = app::requestCameraPhoto,
        cameraPhotoPicked = app.cameraPhotoPicked,
        onRequestVoicePermission = app::requestVoicePermission,
        voicePermissionResult = app.voicePermissionResult,
        onSessionsClick = { onNavigate(Screen.Sessions) },
        onProjectClick = { onNavigate(Screen.Projects) },
        onFilesClick = { onNavigate(Screen.Files) },
        onSettingsClick = { onNavigate(Screen.Settings) },
        onDiagnosticsClick = { onNavigate(Screen.Diagnostics(Screen.Chat)) },
        onAboutClick = { onNavigate(Screen.About(Screen.Chat)) },
        onRenameSession = { id, title ->
            sessionViewModel.rename(id, title)
        },
        onDeleteSession = { id ->
            // Same rule as the shared handler in OpenCodeApp (80-F1/80-F6):
            // move away and prune the parked composer only after the
            // engine confirms the delete. The run stops first (C6, M-108),
            // before the delete request leaves.
            chatViewModel.interruptSessionIfBusy(id)
            sessionViewModel.delete(id) { deletedId ->
                if (chatState.sessionID == deletedId) {
                    chatViewModel.newSession()
                }
                chatViewModel.pruneParkedSession(deletedId)
            }
        },
        onTogglePinSession = { id ->
            sessionViewModel.togglePin(id)
        },
        fileIndex = fileIndex,
        fileIndexLoading = fileIndexLoading,
        fileIndexFailed = fileIndexFailed,
        onEnsureFileIndex = onEnsureFileIndex,
        // Sidebar rows (Opsi 2): previews come from the details lookup
        // the session view model already runs on load, and the footer
        // status reuses the chat's own engine state.
        sessionDetails = sessionsState.details,
        engineState = chatState.engineState,
        busy = chatState.busy,
        isExpanded = isExpanded,
        showSidebar = showSidebar,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
}

/** Recents plus the internal workspace, with availability checked up front. */
private fun buildProjectOptions(app: OpenCodeApplication, current: String): List<ProjectOption> {
    val internal = app.workspaceStore.internalPath()
    val paths = (listOf(internal) + app.workspaceStore.recentPaths() + current)
        .distinct()
        .filter { it.isNotBlank() }

    return paths.map { path ->
        val file = File(path)
        ProjectOption(
            path = path,
            label = file.name.ifEmpty { path },
            isCurrent = path == current,
            available = file.isDirectory && file.canWrite(),
        )
    }
}

/**
 * Small helper so each view model construction stays a single expression.
 *
 * The creation extras are passed through (item 7): the single-argument
 * `create()` drops them, which left ChatViewModel's SavedStateHandle null
 * and made the whole draft-persistence plumbing dead code. View models
 * that need nothing from the extras ignore the parameter.
 */
private inline fun <reified VM : ViewModel> viewModelFactory(
    crossinline create: (CreationExtras) -> VM,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        create(extras) as T
}
