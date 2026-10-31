package dev.bidzz.opencode.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.api.describe
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.ChatRepository
import dev.bidzz.opencode.core.api.ChatState
import dev.bidzz.opencode.core.api.dto.SessionInfo
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.core.engine.EngineStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the chat screen renders. */
data class ChatUiState(
    val engineState: EngineState = EngineState.STOPPED,
    val sessionID: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
    val connecting: Boolean = false,
    val error: String? = null,
    /** True once a project directory is known. */
    val workspaceReady: Boolean = false,
    val workspacePath: String? = null,
    val draft: String = "",
)

/** A single rendered message. */
data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String,
    val streaming: Boolean = false,
)

/**
 * Drives the chat screen.
 *
 * Owns the [ChatRepository] rather than letting the screen do it, so a rotation
 * does not tear down the event stream and lose in-flight text.
 *
 * A session is created lazily on first send: opening the app should not litter the
 * engine with empty sessions, and the engine only needs a session once there is
 * something to say.
 */
class ChatViewModel(
    private val api: EngineApi,
    private val workspace: () -> String,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var repository: ChatRepository? = null
    private var collector: Job? = null
    private var attachedBaseUrl: String? = null

    /** The attached handle, or null until the engine reports credentials. */
    private var handle: dev.bidzz.opencode.api.EngineApi.Handle? = null

    /** Called by the screen whenever engine status changes. */
    fun onEngineStatus(status: EngineStatus) {
        val previous = _state.value.engineState
        _state.value = _state.value.copy(
            engineState = status.state,
            workspaceReady = status.state == EngineState.RUNNING,
        )
        if (status.state != previous) {
            when (status.state) {
                EngineState.FAILED, EngineState.STOPPED -> {
                    repository?.close()
                    repository = null
                    handle = null
                    attachedBaseUrl = null
                    _state.value = _state.value.copy(
                        sessionID = null,
                        busy = false,
                        error = if (status.state == EngineState.FAILED) status.lastError else null,
                    )
                }
                else -> Unit
            }
        }
        // Attach whenever credentials exist, not only on a state change: the first
        // status carrying credentials can arrive on a recomposition that does not
        // change the state value.
        status.credentials?.let { attachIfNeeded(it) }
    }

    /**
     * Builds the API handle and repository once the engine is reachable.
     *
     * The base URL changes on every engine restart (dynamic port) and the password
     * is re-printed to stdout, so a handle is only reusable while the URL matches.
     * Credentials come from the engine status rather than from [EngineApi.handle],
     * which is empty until this method runs.
     */
    private fun attachIfNeeded(credentials: dev.bidzz.opencode.core.engine.EngineCredentials) {
        if (attachedBaseUrl == credentials.baseUrl && repository != null) return

        repository?.close()
        val attached = api.attach(credentials)
        handle = attached
        attachedBaseUrl = credentials.baseUrl
        val repo = ChatRepository(attached.sessions, attached.stream, viewModelScope)
        repository = repo

        collector?.cancel()
        collector = viewModelScope.launch {
            repo.state.collect { chat ->
                _state.value = _state.value.copy(
                    sessionID = chat.sessionID,
                    messages = chat.toUiMessages(),
                    busy = chat.busy,
                    error = chat.lastError,
                )
            }
        }
    }

    fun onDraftChange(text: String) {
        _state.value = _state.value.copy(draft = text)
    }

    /** Sends the draft, creating a session first if there is none. */
    fun send() {
        val text = _state.value.draft.trim()
        if (text.isEmpty() || _state.value.busy) return
        val repo = repository ?: run {
            _state.value = _state.value.copy(error = "Engine is not running")
            return
        }
        _state.value = _state.value.copy(draft = "")

        viewModelScope.launch {
            val sessionID = _state.value.sessionID ?: createSession(repo) ?: return@launch
            if (repository?.state?.value?.sessionID != sessionID) {
                repo.open(sessionID)
            }
            when (val result = repo.send(text)) {
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.describe())
                is ApiResult.Success -> Unit
            }
        }
    }

    private suspend fun createSession(repo: ChatRepository): String? {
        val h = handle ?: return null
        _state.value = _state.value.copy(connecting = true, error = null)
        val result = h.sessions.create(workspace())
        _state.value = _state.value.copy(connecting = false)
        return when (result) {
            is ApiResult.Success -> {
                repo.open(result.value.id)
                result.value.id
            }
            is ApiResult.Failure -> {
                _state.value = _state.value.copy(error = result.describe())
                null
            }
        }
    }

    fun stop() {
        val repo = repository ?: return
        viewModelScope.launch {
            when (val result = repo.interrupt()) {
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.describe())
                is ApiResult.Success -> _state.value = _state.value.copy(busy = false)
            }
        }
    }

    /** Starts a fresh conversation. */
    fun newSession() {
        repository?.close()
        collector?.cancel()
        val attached = handle
        if (attached != null) {
            val repo = ChatRepository(attached.sessions, attached.stream, viewModelScope)
            repository = repo
            collector = viewModelScope.launch {
                repo.state.collect { chat ->
                    _state.value = _state.value.copy(
                        sessionID = chat.sessionID,
                        messages = chat.toUiMessages(),
                        busy = chat.busy,
                        error = chat.lastError,
                    )
                }
            }
        }
        _state.value = _state.value.copy(sessionID = null, messages = emptyList(), error = null)
    }

    /** Loads an existing session. */
    fun openSession(session: SessionInfo) {
        openSession(session.id)
    }

    /** Loads an existing session by id, from the history list. */
    fun openSession(sessionID: String) {
        val repo = repository ?: return
        repo.open(sessionID)
    }

    /**
     * Called when the screen appears.
     *
     * If the engine is already running and the user has an open session, reload its
     * history: the process survives a task swipe, so returning to the app should
     * show the conversation that is still there rather than an empty screen.
     */
    fun onResume(lastSessionID: String?) {
        val repo = repository ?: return
        if (_state.value.messages.isNotEmpty()) return
        val target = lastSessionID ?: return
        repo.open(target)
    }

    /** Remembers the session id so a relaunch can restore it. */
    fun currentSessionID(): String? = _state.value.sessionID

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        collector?.cancel()
        repository?.close()
        super.onCleared()
    }
}

/**
 * Maps repository state to renderable messages.
 *
 * Completed history and in-flight streaming text are merged here so the screen
 * never has to know that they come from two different sources.
 */
private fun ChatState.toUiMessages(): List<ChatMessage> {
    val result = mutableListOf<ChatMessage>()
    messages.forEach { message ->
        when {
            message.isUser -> result += ChatMessage(
                id = message.id,
                role = Role.USER,
                text = message.text.orEmpty(),
            )
            message.isAssistant -> result += ChatMessage(
                id = message.id,
                role = Role.ASSISTANT,
                text = message.plainText,
            )
            // `idle` markers are engine bookkeeping, not conversation.
        }
    }

    // Append streaming text for messages not yet in history.
    streaming.forEach { (messageID, text) ->
        val existing = result.indexOfFirst { it.id == messageID }
        if (existing >= 0) {
            result[existing] = result[existing].copy(text = text, streaming = true)
        } else {
            result += ChatMessage(id = messageID, role = Role.ASSISTANT, text = text, streaming = true)
        }
    }
    return result
}
