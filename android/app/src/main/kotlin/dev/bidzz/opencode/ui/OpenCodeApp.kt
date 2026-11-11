package dev.bidzz.opencode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.bidzz.opencode.LanguageStore
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.ThemeMode
import dev.bidzz.opencode.core.design.ThemeStore
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.ui.about.AboutScreen
import dev.bidzz.opencode.ui.chat.ChatScreen
import dev.bidzz.opencode.ui.chat.ChatUiState
import dev.bidzz.opencode.ui.chat.ChatViewModel
import dev.bidzz.opencode.ui.diagnostics.DiagnosticsScreen
import dev.bidzz.opencode.ui.diff.DiffListScreen
import dev.bidzz.opencode.ui.diff.DiffScreen
import dev.bidzz.opencode.ui.files.DiffViewModel
import dev.bidzz.opencode.ui.files.ExplorerScreen
import dev.bidzz.opencode.ui.files.ExplorerViewModel
import dev.bidzz.opencode.ui.files.FileViewScreen
import dev.bidzz.opencode.ui.files.FileViewViewModel
import dev.bidzz.opencode.ui.onboarding.OnboardingScreen
import dev.bidzz.opencode.ui.onboarding.OnboardingStore
import dev.bidzz.opencode.ui.onboarding.SampleProject
import dev.bidzz.opencode.ui.projects.ProjectOption
import dev.bidzz.opencode.ui.projects.ProjectPickerScreen
import dev.bidzz.opencode.ui.providers.ProvidersScreen
import dev.bidzz.opencode.ui.providers.ProvidersViewModel
import dev.bidzz.opencode.ui.sessions.PinnedSessionStore
import dev.bidzz.opencode.ui.sessions.SessionListPane
import dev.bidzz.opencode.ui.sessions.SessionListScreen
import dev.bidzz.opencode.ui.sessions.SessionListUiState
import dev.bidzz.opencode.ui.sessions.SessionListViewModel
import dev.bidzz.opencode.ui.sessions.SessionRow
import dev.bidzz.opencode.ui.settings.SettingsScreen
import java.io.File

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
    data class FileView(val path: String) : Screen
    data object DiffList : Screen
    data class Diff(val file: String) : Screen
    data object Settings : Screen
    data object Providers : Screen
    data object Diagnostics : Screen
    data object About : Screen
}

/**
 * Where back goes from [this]: the screen it was opened from.
 *
 * The same targets as each screen's own back arrow, so the arrow and the system gesture
 * agree. A single "always Chat" handler skipped the parent: back from a file jumped past
 * the file list, back from Providers past Settings.
 */
internal fun Screen.parent(): Screen = when (this) {
    is Screen.FileView, Screen.DiffList -> Screen.Files
    is Screen.Diff -> Screen.DiffList
    Screen.Providers -> Screen.Settings
    Screen.About -> Screen.Chat
    else -> Screen.Chat
}

@Composable
fun OpenCodeApp(app: OpenCodeApplication) {
    var screen by remember { mutableStateOf<Screen>(Screen.Chat) }
    var sessionScopeToProject by remember { mutableStateOf(true) }

    // File open in the files list-detail pane (expanded widths only).
    var selectedFilePath by remember { mutableStateOf<String?>(null) }

    val engineStatus by app.engineStatus.collectAsState()

    val chatViewModel: ChatViewModel = viewModel(factory = viewModelFactory { extras ->
        ChatViewModel(
            app.engineApi,
            { app.workspaceStore.resolve().absolutePath },
            savedStateHandle = extras.createSavedStateHandle(),
            onSessionCleared = { app.lastSessionID = null },
        )
    })

    val sessionViewModel: SessionListViewModel = viewModel(factory = viewModelFactory { _ ->
        SessionListViewModel(
            app.engineApi,
            { app.workspaceStore.resolve().absolutePath },
            PinnedSessionStore(app),
        )
    })

    val chatState by chatViewModel.state.collectAsState()
    val sessionsState by sessionViewModel.state.collectAsState()

    val explorerViewModel: ExplorerViewModel = viewModel(factory = viewModelFactory { _ ->
        ExplorerViewModel(app.engineApi, { app.workspaceStore.resolve().absolutePath })
    })
    val fileViewModel: FileViewViewModel = viewModel(factory = viewModelFactory { _ ->
        FileViewViewModel(app.engineApi, { app.workspaceStore.resolve().absolutePath })
    })
    val diffViewModel: DiffViewModel = viewModel(factory = viewModelFactory { _ ->
        DiffViewModel(app.engineApi, { app.workspaceStore.resolve().absolutePath })
    })
    val providersViewModel: ProvidersViewModel = viewModel(factory = viewModelFactory { _ ->
        ProvidersViewModel(
            api = app.engineApi,
            configStore = dev.bidzz.opencode.engine.EngineConfigStore(app.engineEnvironment.configHome),
            restartEngine = app::restartEngine,
        )
    })

    val explorerState by explorerViewModel.state.collectAsState()
    val fileState by fileViewModel.state.collectAsState()
    val diffState by diffViewModel.state.collectAsState()
    val providersState by providersViewModel.state.collectAsState()

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
    LaunchedEffect(engineStatus.state) {
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
        chatState.sessionID?.let { app.lastSessionID = it }
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

    // First-run onboarding (Phase 3): shown once, while the workspace is
    // still empty. Read once on first composition; dismissing or finishing
    // marks it completed in prefs, so it never reappears.
    var showOnboarding by remember {
        mutableStateOf(OnboardingStore.shouldShow(hostContext, app.workspaceStore))
    }

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalAppContext provides localized,
    ) {
    OpenCodeTheme(themeMode = themeMode) {
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
                    SampleProject.seed(app.workspaceStore.resolve())?.absolutePath
                },
                onDismiss = {
                    OnboardingStore.markCompleted(hostContext)
                    showOnboarding = false
                },
            )
        } else when (screen) {
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
                fileIndex = explorerState.quickOpenIndex,
                fileIndexLoading = explorerState.quickOpenLoading,
                onEnsureFileIndex = explorerViewModel::ensureQuickOpenIndex,
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
                fileState = if (isExpanded) fileState else null,
                onOpenFileDetail = if (isExpanded) {
                    { row ->
                        if (fileViewModel.requestLoad(row.path)) selectedFilePath = row.path
                    }
                } else {
                    null
                },
                onCloseFileDetail = { selectedFilePath = null },
                onFileStartEdit = fileViewModel::startEditing,
                onFileDraftChange = fileViewModel::editDraft,
                onFileSave = { fileViewModel.save() },
                onFileCancelEdit = fileViewModel::stopEditing,
                onFileOverwrite = { fileViewModel.save(overwrite = true) },
                onFileReloadFromDisk = fileViewModel::reloadFromDisk,
                onFileDismissConflict = fileViewModel::dismissConflict,
                onFileLoadFull = fileViewModel::loadFull,
                onFileConfirmSwitchDiscard = fileViewModel::confirmSwitchDiscard,
                onFileCancelSwitch = fileViewModel::cancelSwitch,
                onEnsureQuickOpenIndex = explorerViewModel::ensureQuickOpenIndex,
            )

            is Screen.FileView -> FileViewScreen(
                state = fileState,
                onBack = { screen = Screen.Files },
                onStartEdit = fileViewModel::startEditing,
                onDraftChange = fileViewModel::editDraft,
                onSave = { fileViewModel.save() },
                onCancelEdit = fileViewModel::stopEditing,
                onOverwrite = { fileViewModel.save(overwrite = true) },
                onReloadFromDisk = fileViewModel::reloadFromDisk,
                onDismissConflict = fileViewModel::dismissConflict,
                onConfirmSwitchDiscard = fileViewModel::confirmSwitchDiscard,
                onCancelSwitch = fileViewModel::cancelSwitch,
            )

            Screen.DiffList -> DiffListScreen(
                changed = explorerState.changed,
                onBack = { screen = Screen.Files },
                onOpen = { file -> screen = Screen.Diff(file) },
            )

            is Screen.Diff -> DiffScreen(
                state = diffState,
                onBack = { screen = Screen.DiffList },
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
                            sessionViewModel.rename(row.id, newTitle, sessionScopeToProject)
                        },
                        onDelete = { row ->
                            if (chatState.sessionID == row.id) {
                                chatViewModel.newSession()
                            }
                            sessionViewModel.delete(row.id, sessionScopeToProject)
                        },
                        onTogglePin = { row ->
                            sessionViewModel.togglePin(row.id)
                        },
                        details = sessionsState.details,
                        selectedSessionId = chatState.sessionID,
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
                            fileIndex = explorerState.quickOpenIndex,
                            fileIndexLoading = explorerState.quickOpenLoading,
                            onEnsureFileIndex = explorerViewModel::ensureQuickOpenIndex,
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
                        sessionViewModel.rename(row.id, newTitle, sessionScopeToProject)
                    },
                    onDelete = { row ->
                        if (chatState.sessionID == row.id) {
                            chatViewModel.newSession()
                        }
                        sessionViewModel.delete(row.id, sessionScopeToProject)
                    },
                    onTogglePin = { row ->
                        sessionViewModel.togglePin(row.id)
                    },
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
                        app.switchWorkspace(File(option.path))
                        // A different project means a different session: the engine
                        // binds a session to the directory it was created in.
                        chatViewModel.newSession()
                        screen = Screen.Chat
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
                onDiagnostics = { screen = Screen.Diagnostics },
                onProjects = { screen = Screen.Projects },
                onProviders = { screen = Screen.Providers },
                onAbout = { screen = Screen.About },
            )

            Screen.About -> AboutScreen(
                versionName = remember {
                    runCatching { hostContext.packageManager.getPackageInfo(hostContext.packageName, 0).versionName }
                        .getOrNull().orEmpty()
                },
                onBack = { screen = Screen.Settings },
            )

            Screen.Providers -> ProvidersScreen(
                state = providersState,
                onBack = { screen = Screen.Settings },
                onRefresh = providersViewModel::refresh,
                onSaveKey = providersViewModel::saveKey,
                onSaveCustom = providersViewModel::saveCustomProvider,
                onDeleteCredential = providersViewModel::deleteCredential,
                onReplaceCredential = providersViewModel::replaceCredential,
            )

            Screen.Diagnostics -> DiagnosticsScreen(
                app = app,
                onBack = { screen = Screen.Chat },
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
    /** Workspace file index for the @-mention / attach picker. */
    fileIndex: List<dev.bidzz.opencode.ui.files.FileRow> = emptyList(),
    fileIndexLoading: Boolean = false,
    onEnsureFileIndex: () -> Unit = {},
) {
    ChatScreen(
        state = chatState,
        sessions = sessionsState.rows,
        onOpenSession = chatViewModel::openSession,
        onDrawerOpen = { sessionViewModel.load(sessionScopeToProject) },
        onDraftChange = chatViewModel::onDraftChange,
        onSend = chatViewModel::send,
        onStop = chatViewModel::stop,
        onPermissionReply = chatViewModel::replyPermission,
        onAgent = chatViewModel::selectAgent,
        onModel = chatViewModel::selectModel,
        onStatusClick = { onNavigate(Screen.Diagnostics) },
        onNewSession = chatViewModel::newSession,
        onDismissError = chatViewModel::dismissError,
        onRetryError = chatViewModel::retryError,
        onReauth = app::restartEngine,
        onEditMessage = { id, text -> chatViewModel.startEdit(id, text) },
        onResendMessage = chatViewModel::resendMessage,
        onAnswerQuestion = chatViewModel::answerQuestion,
        onSubmitEdit = chatViewModel::submitEdit,
        onCancelEdit = chatViewModel::cancelEdit,
        onDeferPermission = chatViewModel::deferPermission,
        onRestoreDeferredPermission = chatViewModel::restoreDeferredPermission,
        onRemoveAttachment = chatViewModel::removeAttachment,
        onAttachmentAdded = chatViewModel::addAttachment,
        onAttachmentRefused = { key, filename -> chatViewModel.reportAttachRefusal(key, filename) },
        onDismissAttachNotice = chatViewModel::dismissAttachNotice,
        onRequestSafFile = app::requestSafFile,
        safFilePicked = app.safFilePicked,
        onSessionsClick = { onNavigate(Screen.Sessions) },
        onProjectClick = { onNavigate(Screen.Projects) },
        onFilesClick = { onNavigate(Screen.Files) },
        onSettingsClick = { onNavigate(Screen.Settings) },
        onDiagnosticsClick = { onNavigate(Screen.Diagnostics) },
        onAboutClick = { onNavigate(Screen.About) },
        onRenameSession = { id, title ->
            sessionViewModel.rename(id, title, sessionScopeToProject)
        },
        onDeleteSession = { id ->
            if (chatState.sessionID == id) {
                chatViewModel.newSession()
            }
            sessionViewModel.delete(id, sessionScopeToProject)
        },
        onTogglePinSession = { id ->
            sessionViewModel.togglePin(id)
        },
        fileIndex = fileIndex,
        fileIndexLoading = fileIndexLoading,
        onEnsureFileIndex = onEnsureFileIndex,
        isExpanded = isExpanded,
        showSidebar = showSidebar,
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
