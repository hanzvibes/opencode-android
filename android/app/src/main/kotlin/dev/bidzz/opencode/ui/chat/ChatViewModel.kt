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
import dev.bidzz.opencode.core.api.dto.toToolCall
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
    /** True while the engine answers requests — `DEGRADED` counts, it keeps its credentials. */
    val workspaceReady: Boolean = false,
    val workspacePath: String? = null,
    val draft: String = "",
    /** Active permission request; the agent is blocked until it is answered. */
    val pendingPermission: dev.bidzz.opencode.core.api.dto.PendingPermission? = null,
    /** Selectable agents — the CLI's modes (build, plan, and user-defined ones). */
    val agents: List<dev.bidzz.opencode.core.api.dto.AgentInfo> = emptyList(),
    val activeAgent: String? = null,
    val models: List<dev.bidzz.opencode.core.api.dto.ModelInfo> = emptyList(),
    val activeModel: dev.bidzz.opencode.core.api.dto.ModelRef? = null,
    /** Slash commands offered by the engine and the project's `commands/` folder. */
    val commands: List<dev.bidzz.opencode.core.api.dto.CommandInfo> = emptyList(),
)

/** A single rendered message. */
data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String,
    val streaming: Boolean = false,
    val tools: List<dev.bidzz.opencode.core.api.dto.ToolCall> = emptyList(),
    /** Raw identity the engine recorded for this reply; labelled at render time so a
     * catalog that arrives late still produces human names. See [provenanceLabel]. */
    val agent: String? = null,
    val model: dev.bidzz.opencode.core.api.dto.ModelRef? = null,
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
    private var attached: dev.bidzz.opencode.core.engine.EngineCredentials? = null

    /** The attached handle, or null until the engine reports credentials. */
    private var handle: dev.bidzz.opencode.api.EngineApi.Handle? = null

    /** Called by the screen whenever engine status changes. */
    fun onEngineStatus(status: EngineStatus) {
        val previous = _state.value.engineState
        _state.value = _state.value.copy(
            engineState = status.state,
            workspaceReady = status.credentials != null,
        )
        if (status.state != previous) {
            when (status.state) {
                EngineState.FAILED, EngineState.STOPPED -> {
                    repository?.close()
                    repository = null
                    handle = null
                    attached = null
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
     * Builds the API handle and repository whenever the credentials move.
     *
     * The base URL and the password both change on every engine restart, so a handle
     * is only reusable while the whole credential matches. Credentials come from the
     * engine status rather than from [EngineApi.handle], which is empty until this
     * method runs.
     */
    private fun attachIfNeeded(credentials: dev.bidzz.opencode.core.engine.EngineCredentials) {
        if (!isStale(attached, credentials) && repository != null) return

        repository?.close()
        val attachedHandle = api.attach(credentials)
        handle = attachedHandle
        attached = credentials
        // The header names the folder the engine just started in, so this is the moment
        // to record it: earlier than this the value would be a guess.
        _state.value = _state.value.copy(workspacePath = workspace())
        val repo = ChatRepository(attachedHandle.sessions, attachedHandle.stream, viewModelScope)
        repository = repo

        collector?.cancel()
        collector = viewModelScope.launch {
            repo.state.collect { chat ->
                _state.value = _state.value.copy(
                    sessionID = chat.sessionID,
                    messages = chat.toUiMessages(),
                    busy = chat.busy,
                    error = chat.lastError,
                    pendingPermission = chat.pendingPermission,
                )
            }
        }
        loadControls()
    }

    /**
     * Fetches the control lists for a freshly attached engine.
     *
     * These describe the engine, not the session, so one fetch per attach is enough —
     * an engine restart is the only thing that changes them, and that rebuilds the
     * handle anyway. Failures are reported but not fatal: chat works without them.
     */
    private fun loadControls() {
        val h = handle ?: return
        viewModelScope.launch {
            h.agents.list().let { r ->
                when (r) {
                    is ApiResult.Success -> _state.value = _state.value.copy(
                        agents = dev.bidzz.opencode.core.api.AgentApi.selectable(r.value),
                    )
                    is ApiResult.Failure -> _state.value = _state.value.copy(error = r.describe())
                }
            }
            h.models.list().let { r ->
                when (r) {
                    is ApiResult.Success -> _state.value = _state.value.copy(models = r.value)
                    is ApiResult.Failure -> _state.value = _state.value.copy(error = r.describe())
                }
            }
            h.commands.list().let { r ->
                when (r) {
                    is ApiResult.Success -> _state.value = _state.value.copy(commands = r.value)
                    is ApiResult.Failure -> Unit // commands are optional; an empty set is normal
                }
            }
        }
    }

    /** Switches the session's agent (mode). Applies to the next prompt. */
    fun selectAgent(agentID: String) {
        val sessionID = _state.value.sessionID
        val h = handle ?: return
        _state.value = _state.value.copy(activeAgent = agentID)
        // A mode chosen before the first message has no session to bind to yet;
        // createSession picks it up from activeAgent when the session is made.
        if (sessionID == null) return
        viewModelScope.launch {
            when (val result = h.agents.set(sessionID, agentID)) {
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.describe())
                is ApiResult.Success -> Unit
            }
        }
    }

    /** Switches the session's model. */
    fun selectModel(model: dev.bidzz.opencode.core.api.dto.ModelRef) {
        val sessionID = _state.value.sessionID
        val h = handle ?: return
        _state.value = _state.value.copy(activeModel = model)
        if (sessionID == null) return
        viewModelScope.launch {
            when (val result = h.sessions.setModel(sessionID, model)) {
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.describe())
                is ApiResult.Success -> Unit
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

        viewModelScope.launch {
            val sessionID = _state.value.sessionID ?: createSession(repo) ?: return@launch
            if (repository?.state?.value?.sessionID != sessionID) {
                repo.open(sessionID)
            }
            // A leading slash is a command, not a prompt. The engine runs it and answers
            // 204; the output arrives over the event stream exactly like a normal turn.
            val result = if (text.startsWith("/")) {
                val name = text.substring(1).substringBefore(' ')
                val args = text.substringAfter(' ', "").trim()
                handle?.commands?.run(sessionID, name, args)
                    ?: ApiResult.Failure(code = -1, message = "Engine is not running")
            } else {
                repo.send(text)
            }
            when (result) {
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.describe())
                is ApiResult.Success -> clearSentDraft(text)
            }
        }
    }

    /**
     * Empties the composer after the engine accepted the prompt.
     *
     * The draft survives the round trip because the user's bubble only appears once the
     * message comes back from the engine — clearing first made a slow send look like the
     * message had been thrown away. Typing during the send wins instead of being erased.
     */
    private fun clearSentDraft(sent: String) {
        if (_state.value.draft.trim() == sent) {
            _state.value = _state.value.copy(draft = "")
        }
    }

    /** Surfaces a control-API failure instead of swallowing it. */
    private fun reportFailure(result: ApiResult<*>) {
        if (result is ApiResult.Failure) {
            _state.value = _state.value.copy(error = result.describe())
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
                // A mode or model picked before the first message has no session to bind
                // to at the time it is chosen, so it is applied here instead. Failures are
                // reported: a silently rejected selection would look like it worked while
                // the agent ran on the engine default.
                _state.value.activeAgent?.let {
                    reportFailure(h.agents.set(result.value.id, it))
                }
                _state.value.activeModel?.let {
                    reportFailure(h.sessions.setModel(result.value.id, it))
                }
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

    /**
     * Answers the pending permission request.
     *
     * The engine blocks the tool call until this is sent, so every decision path
     * (allow once, allow always, reject) must reach it. A failure leaves the
     * prompt visible so the operator can retry rather than losing the request.
     */
    fun replyPermission(decision: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            when (val result = repo.replyPermission(decision)) {
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.describe())
                is ApiResult.Success -> Unit
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
                        pendingPermission = chat.pendingPermission,
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
        val target = lastSessionID ?: return
        if (_state.value.messages.isNotEmpty()) return
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
            // A failed run with nothing to show would otherwise be an empty bubble.
            // The model that failed is named too: "which model errored" is the first
            // thing the transcript has to answer after a switch goes wrong.
            message.isAssistant && message.error != null && message.content.isEmpty() ->
                result += ChatMessage(
                    id = message.id,
                    role = Role.ERROR,
                    text = message.error?.message ?: message.error?.type ?: "The agent failed",
                    agent = message.agent,
                    model = message.model,
                )
            message.isAssistant -> result += ChatMessage(
                id = message.id,
                role = Role.ASSISTANT,
                text = message.plainText,
                tools = message.content.mapNotNull { it.toToolCall() },
                agent = message.agent,
                model = message.model,
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

    // Live tool calls win over history for the same call id; calls for a message
    // that has no text yet still need a message to hang off.
    liveTools.values.groupBy { it.messageID }.forEach { (messageID, live) ->
        var idx = result.indexOfFirst { it.id == messageID }
        if (idx < 0) {
            result += ChatMessage(id = messageID, role = Role.ASSISTANT, text = "", streaming = true)
            idx = result.lastIndex
        }
        val liveCalls = live.map { it.call }
        val liveIds = liveCalls.map { it.id }.toSet()
        result[idx] = result[idx].copy(tools = result[idx].tools.filter { it.id !in liveIds } + liveCalls)
    }
    return result
}
