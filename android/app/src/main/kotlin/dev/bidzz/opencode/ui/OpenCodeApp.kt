package dev.bidzz.opencode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.ui.chat.ChatScreen
import dev.bidzz.opencode.ui.chat.ChatViewModel
import dev.bidzz.opencode.ui.diagnostics.DiagnosticsScreen
import dev.bidzz.opencode.ui.diff.DiffListScreen
import dev.bidzz.opencode.ui.diff.DiffScreen
import dev.bidzz.opencode.ui.files.DiffViewModel
import dev.bidzz.opencode.ui.files.ExplorerScreen
import dev.bidzz.opencode.ui.files.ExplorerViewModel
import dev.bidzz.opencode.ui.files.FileViewScreen
import dev.bidzz.opencode.ui.files.FileViewViewModel
import dev.bidzz.opencode.ui.projects.ProjectOption
import dev.bidzz.opencode.ui.projects.ProjectPickerScreen
import dev.bidzz.opencode.ui.providers.ProvidersScreen
import dev.bidzz.opencode.ui.providers.ProvidersViewModel
import dev.bidzz.opencode.ui.sessions.SessionListScreen
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
private sealed interface Screen {
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
}

@Composable
fun OpenCodeApp(app: OpenCodeApplication) {
    var screen by remember { mutableStateOf<Screen>(Screen.Chat) }
    var sessionScopeToProject by remember { mutableStateOf(true) }

    val engineStatus by app.engineStatus.collectAsState()

    val chatViewModel: ChatViewModel = viewModel(factory = viewModelFactory {
        ChatViewModel(app.engineApi, { app.workspaceStore.resolve().absolutePath })
    })

    val sessionViewModel: SessionListViewModel = viewModel(factory = viewModelFactory {
        SessionListViewModel(app.engineApi, { app.workspaceStore.resolve().absolutePath })
    })

    val chatState by chatViewModel.state.collectAsState()
    val sessionsState by sessionViewModel.state.collectAsState()

    val explorerViewModel: ExplorerViewModel = viewModel(factory = viewModelFactory {
        ExplorerViewModel(app.engineApi, { app.workspaceStore.resolve().absolutePath })
    })
    val fileViewModel: FileViewViewModel = viewModel(factory = viewModelFactory {
        FileViewViewModel(app.engineApi, { app.workspaceStore.resolve().absolutePath })
    })
    val diffViewModel: DiffViewModel = viewModel(factory = viewModelFactory {
        DiffViewModel(app.engineApi, { app.workspaceStore.resolve().absolutePath })
    })
    val providersViewModel: ProvidersViewModel = viewModel(factory = viewModelFactory {
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

    // Feed engine state into the view model; it decides when to attach the API.
    LaunchedEffect(engineStatus.state, engineStatus.credentials?.baseUrl) {
        chatViewModel.onEngineStatus(engineStatus)
    }

    // Restore the last conversation once the engine is reachable. The engine keeps
    // running across a task swipe, so returning to the app should show the session
    // that is still there rather than an empty screen.
    LaunchedEffect(engineStatus.state) {
        if (engineStatus.state == EngineState.RUNNING) {
            chatViewModel.onResume(app.lastSessionID)
        }
    }

    LaunchedEffect(chatState.sessionID) {
        chatState.sessionID?.let { app.lastSessionID = it }
    }

    // Load history whenever the screen is shown, and reload when the scope flips.
    LaunchedEffect(screen, sessionScopeToProject) {
        if (screen is Screen.Sessions) sessionViewModel.load(sessionScopeToProject)
    }

    // Load the explorer when it first appears or the project changes.
    LaunchedEffect(screen, engineStatus.credentials?.baseUrl) {
        if (screen is Screen.Files) explorerViewModel.load()
    }

    LaunchedEffect(screen) {
        when (val target = screen) {
            is Screen.FileView -> fileViewModel.load(target.path)
            is Screen.Diff -> diffViewModel.load(target.file)
            else -> Unit
        }
    }

    // Back always returns to the conversation.
    BackHandler(enabled = screen !is Screen.Chat) { screen = Screen.Chat }

    OpenCodeTheme {
        when (screen) {
            Screen.Chat -> ChatScreen(
                state = chatState,
                onDraftChange = chatViewModel::onDraftChange,
                onSend = chatViewModel::send,
                onStop = chatViewModel::stop,
                onPermissionReply = chatViewModel::replyPermission,
                onAgent = chatViewModel::selectAgent,
                onModel = chatViewModel::selectModel,
                onStatusClick = { screen = Screen.Diagnostics },
                onNewSession = chatViewModel::newSession,
                onSessionsClick = { screen = Screen.Sessions },
                onProjectClick = { screen = Screen.Projects },
                onFilesClick = { screen = Screen.Files },
                onSettingsClick = { screen = Screen.Settings },
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

            Screen.Sessions -> SessionListScreen(
                state = sessionsState,
                onBack = { screen = Screen.Chat },
                onOpen = { row: SessionRow ->
                    chatViewModel.openSession(row.id)
                    screen = Screen.Chat
                },
                onToggleScope = { sessionScopeToProject = !sessionScopeToProject },
                onNewSession = {
                    chatViewModel.newSession()
                    screen = Screen.Chat
                },
            )

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
                onBack = { screen = Screen.Chat },
                onDiagnostics = { screen = Screen.Diagnostics },
                onProjects = { screen = Screen.Projects },
                onProviders = { screen = Screen.Providers },
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

/** Small helper so each view model construction stays a single expression. */
private inline fun <reified VM : ViewModel> viewModelFactory(
    crossinline create: () -> VM,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
