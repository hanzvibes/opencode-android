package dev.bidzz.opencode.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.ui.chat.ChatScreen
import dev.bidzz.opencode.ui.chat.ChatViewModel
import dev.bidzz.opencode.ui.diagnostics.DiagnosticsScreen
import dev.bidzz.opencode.ui.settings.SettingsScreen

/**
 * Top-level navigation.
 *
 * A sealed screen value rather than a navigation library: there are three
 * destinations and no deep links, so a graph would be machinery without a job.
 * Back is handled by the host activity.
 */
private sealed interface Screen {
    data object Chat : Screen
    data object Settings : Screen
    data object Diagnostics : Screen
}

@Composable
fun OpenCodeApp(app: OpenCodeApplication) {
    var screen by remember { mutableStateOf<Screen>(Screen.Chat) }
    var showSettings by remember { mutableStateOf(false) }

    val engineStatus by app.engineStatus.collectAsState()
    val chatViewModel: ChatViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(app.engineApi, { app.workspaceStore.resolve().absolutePath }) as T
            }
        },
    )

    // Feed engine state into the view model; it decides when to attach the API.
    val chatState by chatViewModel.state.collectAsState()

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

    // Remember the session so the restore above has something to open.
    LaunchedEffect(chatState.sessionID) {
        chatState.sessionID?.let { app.lastSessionID = it }
    }

    OpenCodeTheme {
        when (screen) {
            Screen.Chat -> ChatScreen(
                state = chatState,
                onDraftChange = chatViewModel::onDraftChange,
                onSend = chatViewModel::send,
                onStop = chatViewModel::stop,
                onStatusClick = { screen = Screen.Diagnostics },
                onNewSession = chatViewModel::newSession,
                onSessionsClick = { showSettings = true },
            )

            Screen.Settings -> SettingsScreen(
                app = app,
                onBack = { screen = Screen.Chat },
                onDiagnostics = { screen = Screen.Diagnostics },
            )

            Screen.Diagnostics -> DiagnosticsScreen(
                app = app,
                onBack = { screen = Screen.Chat },
            )
        }
    }
}
