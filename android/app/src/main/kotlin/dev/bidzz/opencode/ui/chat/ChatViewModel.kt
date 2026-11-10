package dev.bidzz.opencode.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.R
import dev.bidzz.opencode.api.EngineApi
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
    /** What went wrong, as a value; the screen words it in the app’s language. */
    val failure: ApiResult.Failure? = null,
    /** A message with no failure behind it, e.g. the engine never started. */
    val failureKey: Int? = null,
    /** True while the engine answers requests — `DEGRADED` counts, it keeps its credentials. */
    val workspaceReady: Boolean = false,
    val workspacePath: String? = null,
    val draft: String = "",
    /**
     * The last prompt actually sent, kept so a failed send can be retried.
     * It is the text that was sent — not the draft, which the user may
     * have edited since — so retrying never sends a half-edited message
     * the user did not mean to send. Cleared when the session changes.
     */
    val lastSentText: String? = null,
    /** Prompts actually sent in this session, oldest first, for the
     * composer's in-memory history navigation (C-F). In-memory only —
     * never persisted — and cleared when the session changes, like
     * [lastSentText]. Capped so a long session cannot grow it without bound. */
    val sentPrompts: List<String> = emptyList(),
    /** Active permission request; the agent is blocked until it is answered. */
    val pendingPermission: dev.bidzz.opencode.core.api.dto.PendingPermission? = null,
    /** A permission request the user deferred ("Answer later"). Kept so it can
     * be answered later; dropped when the run ends, since the engine no longer
     * waits for it then. */
    val deferredPermission: dev.bidzz.opencode.core.api.dto.PendingPermission? = null,
    /** True when the last permission reply failed, so the prompt re-arms its
     * buttons instead of staying spent (see [ChatViewModel.replyPermission]). */
    val permissionReplyFailed: Boolean = false,
    /** What the banner's single Retry button means for the current failure
     * ([RetryKind.NONE] hides it — see [ChatViewModel.retryError]). */
    val retryKind: RetryKind = RetryKind.NONE,
    /** A follow-up typed while the agent is working, sent when the run ends. */
    val queuedPrompt: String? = null,
    /** True after Stop was tapped until the run actually ends. */
    val stopping: Boolean = false,
    /** Id of the user message being edited in the composer, or null. */
    val editingMessageId: String? = null,
    /** Selectable agents — the CLI's modes (build, plan, and user-defined ones). */
    val agents: List<dev.bidzz.opencode.core.api.dto.AgentInfo> = emptyList(),
    val activeAgent: String? = null,
    val models: List<dev.bidzz.opencode.core.api.dto.ModelInfo> = emptyList(),
    val activeModel: dev.bidzz.opencode.core.api.dto.ModelRef? = null,
    /** Slash commands offered by the engine and the project's `commands/` folder. */
    val commands: List<dev.bidzz.opencode.core.api.dto.CommandInfo> = emptyList(),
    /** Active git branch when inside a version-controlled workspace. */
    val branch: String? = null,
)

/** What the error banner's Retry button does for the current failure. */
enum class RetryKind {
    /** No retry is offered (non-retryable failure, or nothing failed). */
    NONE,
    /** Re-send the last sent prompt as-is. */
    RESEND,
    /** The engine's credentials rotated: restart the engine to re-auth. */
    REAUTH,
    /** HTTP 429: re-sending on the same model would fail again immediately,
     * so Retry shows switch-model / wait guidance instead of re-sending. */
    RATE_LIMITED,
    /** The session is gone server-side: reload (re-open) it. */
    RELOAD_SESSION,
}

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
    val reasoning: String? = null,
    val parts: List<ChatTurnPart> = emptyList(),
    /** Engine `time.created` for this message, epoch millis, or null when the
     * engine sent none. Drives date dividers and the tool-trace start time;
     * nothing else reads it, so a null simply hides those decorations. */
    val createdAtMillis: Long? = null,
    /** Turn footer (Phase 2, finding 6), from the engine DTO: total tokens
     * (`tokens.input + tokens.output`; reasoning/cache are subsets of those,
     * never added on top), cost, turn duration (`time.completed -
     * time.created`) and the engine `finish` string. All null until the
     * engine reports them; the footer hides while all four are null. */
    val usageTokens: Long? = null,
    val usageCostUsd: Double? = null,
    val turnDurationMs: Long? = null,
    val stopReason: String? = null,
    /** Thinking time for this turn in seconds (Phase 2, finding 7): the
     * engine's own `time.streamed - time.created` while reasoning was
     * present, i.e. how long the model thought before its first token. Null
     * when the engine sent no usable timestamps; the screen then falls back
     * to its live timer while the turn streams. Stored per turn so a
     * finished turn never shows the current run's clock (or 0 when idle). */
    val thinkingSeconds: Int? = null,
    /** Per-tool durations in milliseconds, keyed by tool id (Phase 2,
     * finding 20): client-side stamps — the engine sends no per-tool
     * timestamps — so a tool the client never saw live has no entry here
     * rather than an invented duration. */
    val toolDurations: Map<String, Long> = emptyMap(),
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
    /**
     * Optional handle that owns the draft across process death. Null when the
     * caller constructs the view model without one (the draft then lives only
     * in [ChatUiState], as before); the session-switch clearing below works
     * either way.
     */
    private val savedStateHandle: SavedStateHandle? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChatUiState(
            workspacePath = workspace(),
            draft = savedStateHandle?.get<String>(DRAFT_KEY) ?: "",
        ),
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var repository: ChatRepository? = null
    private var collector: Job? = null
    private var attached: dev.bidzz.opencode.core.engine.EngineCredentials? = null

    /** The attached handle, or null until the engine reports credentials. */
    private var handle: dev.bidzz.opencode.api.EngineApi.Handle? = null

    /** Client-side tool timestamps (Phase 2, finding 20): the engine sends no
     * per-tool times, so a tool is stamped when its live event first arrives
     * and when it reaches a terminal status. Both maps are keyed by tool id
     * and survive resyncs (which clear the repository's live tools); they are
     * cleared when the session changes, so an id is never reused across
     * sessions. A tool the client never saw live has no stamps and therefore
     * no duration — never an invented one. */
    private val toolStartedAt = mutableMapOf<String, Long>()
    private val toolCompletedAt = mutableMapOf<String, Long>()
    private var lastCollectorSessionID: String? = null

    /** Draft as it was before an edit loaded a message into the composer;
     * cancelling the edit restores it. Not persisted: on process death the
     * edit is abandoned and the draft stays as typed. */
    private var draftBeforeEdit: String? = null

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
                        stopping = false,
                        queuedPrompt = null,
                        editingMessageId = null,
                        retryKind = RetryKind.NONE,
                        failureKey = if (status.state == EngineState.FAILED) {
                            R.string.error_engine_not_running
                        } else {
                            null
                        },
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

        collectRepo(repo)
        loadControls()
    }

    /** (Re)starts collecting one repository's state into [_state]. */
    private fun collectRepo(repo: ChatRepository) {
        collector?.cancel()
        collector = viewModelScope.launch {
            repo.state.collect { chat -> onRepoState(chat) }
        }
    }

    /**
     * Folds one repository emission into [_state].
     *
     * Besides copying the chat over, this is where the cross-cutting
     * Phase 2 wiring lives: client-side tool stamps (finding 20), the typed
     * retry kind (finding 18), the queued follow-up flush (finding 10) and
     * the deferred-permission lifecycle (finding 17).
     */
    private fun onRepoState(chat: ChatState) {
        val previous = _state.value
        val wasBusy = previous.busy

        // A new session restarts the tool clock: ids must never be reused
        // across sessions, or a new tool would inherit an old tool's stamps.
        if (chat.sessionID != lastCollectorSessionID) {
            toolStartedAt.clear()
            toolCompletedAt.clear()
            lastCollectorSessionID = chat.sessionID
        }
        // Stamp live tools: first sight starts the clock, a terminal status
        // stops it. History-only tools are never stamped — resync must not
        // invent a duration for a tool that finished before this client
        // attached.
        chat.liveTools.values.forEach { live ->
            val id = live.call.id
            val now = System.currentTimeMillis()
            if (live.call.isDone) {
                toolStartedAt.getOrPut(id) { now }
                toolCompletedAt.getOrPut(id) { now }
            } else {
                toolStartedAt.getOrPut(id) { now }
            }
        }
        val toolDurations = toolCompletedAt.mapNotNull { (id, end) ->
            toolStartedAt[id]?.let { start ->
                if (end >= start) id to (end - start) else null
            }
        }.toMap()

        // A deferred permission stays hidden while the engine still waits on
        // that same request; a different request supersedes it, and a
        // finished run drops it — the engine no longer waits for it then,
        // and a late reply would only fail with NoPendingPermission.
        val repoPending = chat.pendingPermission
        val deferred = previous.deferredPermission
        val effectivePending = when {
            repoPending == null -> null
            deferred != null && repoPending.id == deferred.id -> null
            else -> repoPending
        }
        val effectiveDeferred = when {
            repoPending == null && !chat.busy -> null
            repoPending != null && deferred != null && repoPending.id != deferred.id -> null
            else -> deferred
        }

        _state.value = previous.copy(
            sessionID = chat.sessionID,
            messages = chat.toUiMessages(toolDurations),
            busy = chat.busy,
            failure = chat.lastFailure,
            pendingPermission = effectivePending,
            deferredPermission = effectiveDeferred,
            // While a permission prompt is outstanding the prompt is the
            // action, not the banner: Retry must not offer to re-send a
            // prompt into a run that is blocked waiting for an answer.
            retryKind = if (effectivePending != null) {
                RetryKind.NONE
            } else {
                retryKindFor(chat.lastFailure)
            },
            stopping = if (!chat.busy) false else previous.stopping,
            // A new request re-arms the prompt; the same request keeps the
            // failed-reply flag so the prompt stays re-armed until answered.
            permissionReplyFailed = if (effectivePending?.id != previous.pendingPermission?.id) {
                false
            } else {
                previous.permissionReplyFailed
            },
        )

        // The run just ended and a follow-up was queued while it worked:
        // send it now. `send()` reads the draft, so the queued text is put
        // back into the draft first; it is cleared from the queue before
        // sending so a failed send cannot re-queue it in a loop.
        if (wasBusy && !chat.busy) {
            val queued = _state.value.queuedPrompt
            if (queued != null) {
                _state.value = _state.value.copy(queuedPrompt = null, draft = queued)
                send()
            }
        }
    }

    /**
     * What Retry means for [failure], from existing signals only — no new
     * error-kind field was needed: [ApiResult.Failure.reason] already
     * distinguishes auth/session-missing, and [EngineClient] preserves the
     * HTTP code, so 429 is `code == 429`. The assistant-message error type
     * `provider.free-limit` is NOT covered here: it surfaces as a Role.ERROR
     * bubble, not a banner failure, so the banner never sees it.
     */
    private fun retryKindFor(failure: ApiResult.Failure?): RetryKind {
        if (failure == null) return RetryKind.NONE
        return when {
            failure.isUnauthorized -> RetryKind.REAUTH
            failure.code == 429 -> RetryKind.RATE_LIMITED
            failure.isSessionMissing -> RetryKind.RELOAD_SESSION
            failure.isLocationMissing || failure.isNoSession ||
                failure.isBinaryFile || failure.isNoPendingPermission -> RetryKind.NONE
            else -> if (_state.value.lastSentText != null) RetryKind.RESEND else RetryKind.NONE
        }
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
                    // A control-list failure is not a send failure: Retry
                    // must not offer to re-send the last prompt for it.
                    is ApiResult.Failure -> _state.value = _state.value.copy(failure = r, retryKind = RetryKind.NONE)
                }
            }
            h.models.list().let { r ->
                when (r) {
                    is ApiResult.Success -> _state.value = _state.value.copy(models = r.value)
                    is ApiResult.Failure -> _state.value = _state.value.copy(failure = r, retryKind = RetryKind.NONE)
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
                is ApiResult.Failure -> _state.value = _state.value.copy(failure = result)
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
                is ApiResult.Failure -> _state.value = _state.value.copy(failure = result)
                is ApiResult.Success -> Unit
            }
        }
    }

    fun onDraftChange(text: String) {
        savedStateHandle?.set(DRAFT_KEY, text)
        _state.value = _state.value.copy(draft = text)
    }

    /** Clears the draft in both the UI state and the saved-state handle. */
    private fun clearDraft() {
        savedStateHandle?.remove<String>(DRAFT_KEY)
        _state.value = _state.value.copy(draft = "")
    }

    /** Sends the draft, creating a session first if there is none. */
    fun send() {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) return
        // While the agent works the composer stays typable, and sending queues
        // the follow-up instead of dropping it: the queue flushes when the
        // run ends (see onRepoState). The draft is cleared so the queued text
        // is not also still sitting in the composer.
        if (_state.value.busy || sendInFlight) {
            _state.value = _state.value.copy(queuedPrompt = text)
            clearDraft()
            return
        }
        sendText(text)
    }

    private var sendInFlight = false

    /**
     * Sends [text] as a new prompt, recording it as the retry target and in
     * prompt history. Shared by [send], [submitEdit] and [resendMessage] so
     * all three record the same retry state.
     */
    private fun sendText(text: String) {
        val repo = repository ?: run {
            _state.value = _state.value.copy(failureKey = R.string.error_engine_not_running)
            return
        }
        _state.value = _state.value.copy(
            lastSentText = text,
            // Consecutive duplicates add nothing to history navigation.
            sentPrompts = if (_state.value.sentPrompts.lastOrNull() == text) {
                _state.value.sentPrompts
            } else {
                (_state.value.sentPrompts + text).takeLast(50)
            },
        )
        sendInFlight = true
        viewModelScope.launch {
            try {
                sendNow(repo, text)
            } finally {
                sendInFlight = false
            }
        }
    }

    /**
     * Loads a user message back into the composer in editing mode.
     *
     * The composer shows an editing header with Submit/Cancel; submitting
     * sends the edited text as a new prompt in the same session
     * ([submitEdit]). It is deliberately NOT a history rewrite: the engine
     * exposes `POST /api/session/{id}/revert/stage` + `/revert/commit` and
     * `/fork` (docs/API-REFERENCE.md), but neither request shape is verified
     * against a live engine — `Session.ForkBoundary` is an untyped `anyOf` —
     * and guessing would risk corrupting the session's real history. The
     * original message stays visible and the edited text follows it as a new
     * turn, so the transcript is never silently rewritten.
     */
    fun startEdit(messageId: String, text: String) {
        if (_state.value.editingMessageId == messageId) return
        draftBeforeEdit = _state.value.draft
        _state.value = _state.value.copy(editingMessageId = messageId)
        onDraftChange(text)
    }

    /** Abandons an edit and restores the draft as it was before it. */
    fun cancelEdit() {
        val restore = draftBeforeEdit
        draftBeforeEdit = null
        _state.value = _state.value.copy(editingMessageId = null)
        if (restore != null) onDraftChange(restore)
    }

    /** Submits the edited text (see [startEdit] for why this re-sends). */
    fun submitEdit() {
        val text = _state.value.draft.trim()
        val editingId = _state.value.editingMessageId
        if (editingId == null) {
            send()
            return
        }
        if (text.isEmpty()) {
            cancelEdit()
            return
        }
        draftBeforeEdit = null
        _state.value = _state.value.copy(editingMessageId = null)
        if (_state.value.busy || sendInFlight) {
            _state.value = _state.value.copy(queuedPrompt = text)
            clearDraft()
            return
        }
        sendText(text)
    }

    /** Re-sends one user message's text (its own Resend pill). */
    fun resendMessage(messageId: String) {
        val text = _state.value.messages
            .firstOrNull { it.id == messageId && it.role == Role.USER }
            ?.text?.trim()
            .takeIf { !it.isNullOrBlank() } ?: return
        if (_state.value.busy || sendInFlight) {
            _state.value = _state.value.copy(queuedPrompt = text)
            return
        }
        sendText(text)
    }

    /**
     * Re-sends the last prompt that was sent, after a failure.
     *
     * Retries the recorded [ChatUiState.lastSentText], never the current
     * draft: the draft is what the user is typing now, and it may already
     * be a different message. A retry that silently sent the new draft
     * would be a different request from the one that failed. The old
     * error is cleared first so the bubble does not linger over the retry.
     */
    fun retryLastSend() {
        val text = _state.value.lastSentText?.takeIf { it.isNotBlank() } ?: return
        if (_state.value.busy || sendInFlight) return
        val repo = repository ?: run {
            _state.value = _state.value.copy(failureKey = R.string.error_engine_not_running)
            return
        }
        _state.value = _state.value.copy(failure = null, failureKey = null)
        sendInFlight = true
        viewModelScope.launch {
            try {
                sendNow(repo, text)
            } finally {
                sendInFlight = false
            }
        }
    }

    /**
     * The banner's single Retry, dispatched by [ChatUiState.retryKind].
     *
     * - RESEND: re-sends the last prompt.
     * - RELOAD_SESSION: the session is gone server-side; re-open it.
     * - RATE_LIMITED: does NOT re-send — the same model would fail again
     *   immediately. It swaps the banner for switch-model / wait guidance
     *   and hides Retry until a new failure arrives.
     * - REAUTH: nothing to do here; the screen routes it to the engine
     *   restart (re-auth), which re-issues credentials and re-attaches.
     * - NONE: Retry is not offered at all for non-retryable failures.
     */
    fun retryError() {
        when (_state.value.retryKind) {
            RetryKind.RESEND -> retryLastSend()
            RetryKind.RELOAD_SESSION -> reloadSession()
            RetryKind.RATE_LIMITED -> _state.value = _state.value.copy(
                failure = null,
                failureKey = R.string.chat_rate_limited_guidance,
                retryKind = RetryKind.NONE,
            )
            RetryKind.REAUTH, RetryKind.NONE -> Unit
        }
    }

    /**
     * Re-opens the current session after a SESSION_MISSING failure.
     *
     * The repository is rebuilt so the stream re-attaches over a fresh
     * socket; the session id is kept, so a session that is actually still
     * there reloads in place. If the engine answers 404 again the failure
     * resurfaces through the collector.
     */
    fun reloadSession() {
        val id = _state.value.sessionID ?: return
        val attachedHandle = handle ?: return
        repository?.close()
        val repo = ChatRepository(attachedHandle.sessions, attachedHandle.stream, viewModelScope)
        repository = repo
        _state.value = _state.value.copy(failure = null, failureKey = null, retryKind = RetryKind.NONE)
        collectRepo(repo)
        repo.open(id)
    }

    private suspend fun sendNow(repo: ChatRepository, text: String) {
        val sessionID = _state.value.sessionID ?: createSession(repo) ?: return
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
            is ApiResult.Failure -> _state.value = _state.value.copy(failure = result)
            is ApiResult.Success -> clearSentDraft(text)
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
            clearDraft()
        }
    }

    /** Surfaces a control-API failure instead of swallowing it. */
    private fun reportFailure(result: ApiResult<*>) {
        if (result is ApiResult.Failure) {
            _state.value = _state.value.copy(failure = result)
        }
    }

    private suspend fun createSession(repo: ChatRepository): String? {
        val h = handle ?: return null
        _state.value = _state.value.copy(connecting = true, failure = null, failureKey = null)
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
                _state.value = _state.value.copy(failure = result)
                null
            }
        }
    }

    fun stop() {
        val repo = repository ?: return
        // "Stopping…" until the run actually ends: the interrupt is async and
        // the button must not flip back to Send while the agent still works.
        // The collector clears it when busy goes false.
        _state.value = _state.value.copy(stopping = true)
        viewModelScope.launch {
            when (val result = repo.interrupt()) {
                is ApiResult.Failure -> _state.value = _state.value.copy(failure = result, stopping = false)
                is ApiResult.Success -> _state.value = _state.value.copy(busy = false, stopping = false)
            }
        }
    }

    /**
     * Answers the pending permission request.
     *
     * The engine blocks the tool call until this is sent, so every decision path
     * (allow once, allow always, reject) must reach it. A failure leaves the
     * prompt visible so the operator can retry rather than losing the request —
     * and sets [ChatUiState.permissionReplyFailed], which re-arms the prompt's
     * buttons (the composable spends them on tap and must not stay spent
     * after a failed reply).
     */
    fun replyPermission(decision: String) {
        val repo = repository ?: return
        _state.value = _state.value.copy(permissionReplyFailed = false)
        viewModelScope.launch {
            when (val result = repo.replyPermission(decision)) {
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    failure = result,
                    // The prompt is still outstanding, so the banner offers
                    // no Retry for this failure (see onRepoState).
                    retryKind = RetryKind.NONE,
                    permissionReplyFailed = true,
                )
                is ApiResult.Success -> _state.value = _state.value.copy(
                    deferredPermission = null,
                    permissionReplyFailed = false,
                )
            }
        }
    }

    /**
     * Defers the pending permission ("Answer later").
     *
     * Nothing is sent to the engine: the request is not answered, rejected or
     * saved, and the agent stays blocked on it — deferring only hides the
     * prompt. The request is kept as [ChatUiState.deferredPermission] and the
     * screen offers it back as a "Permission waiting" row, so it can be
     * answered later via [restoreDeferredPermission] against the same request
     * id. If the run ends first the deferred request is dropped (see
     * [onRepoState]): the engine no longer waits for it, and a late reply
     * would only fail with NoPendingPermission.
     */
    fun deferPermission() {
        val pending = _state.value.pendingPermission ?: return
        _state.value = _state.value.copy(pendingPermission = null, deferredPermission = pending)
    }

    /** Brings a deferred permission request back as the pending prompt. */
    fun restoreDeferredPermission() {
        val deferred = _state.value.deferredPermission ?: return
        _state.value = _state.value.copy(pendingPermission = deferred, deferredPermission = null)
    }

    /** Starts a fresh conversation. */
    fun newSession() {
        repository?.close()
        val attached = handle
        if (attached != null) {
            val repo = ChatRepository(attached.sessions, attached.stream, viewModelScope)
            repository = repo
            collectRepo(repo)
        }
        // A draft belongs to the session it was typed in; it must not follow
        // the user into the fresh session. The last sent text goes with it:
        // retrying it in a new session would send the old session's prompt.
        // The queued follow-up, the edit in progress, any deferred permission
        // and the stop state belong to the old session too.
        _state.value = _state.value.copy(sessionID = null, messages = emptyList(), failure = null, failureKey = null, draft = "", lastSentText = null, sentPrompts = emptyList(), queuedPrompt = null, editingMessageId = null, deferredPermission = null, stopping = false, retryKind = RetryKind.NONE)
        savedStateHandle?.remove<String>(DRAFT_KEY)
    }

    /** Loads an existing session. */
    fun openSession(session: SessionInfo) {
        // Read-only sync: the session's own model and agent become the
        // active ones, so the header stops showing the previous session's
        // model. selectModel()/selectAgent() are NOT called — they write to
        // the engine and could clobber the session's real model/agent.
        // NOTE: agent sync reads SessionInfo.agent, which the engine returns
        // in Session.Info.agent (docs/API-REFERENCE.md) — no new endpoint.
        _state.value = _state.value.copy(
            activeModel = session.model ?: _state.value.activeModel,
            activeAgent = session.agent ?: _state.value.activeAgent,
        )
        openSessionById(session.id)
    }

    /** Loads an existing session by id, from the history list. */
    fun openSession(sessionID: String) {
        openSessionById(sessionID, fetchMeta = true)
    }

    private fun openSessionById(sessionID: String, fetchMeta: Boolean = false) {
        val repo = repository ?: return
        // A draft belongs to the session it was typed in; opening another
        // session starts with an empty composer — and no retry target from
        // the previous session.
        _state.value = _state.value.copy(draft = "", lastSentText = null, sentPrompts = emptyList(), queuedPrompt = null, editingMessageId = null, deferredPermission = null, retryKind = RetryKind.NONE)
        savedStateHandle?.remove<String>(DRAFT_KEY)
        repo.open(sessionID)
        // The id-only path has no SessionInfo to sync from, so the session's
        // own model/agent are read back (GET /api/session/{id}) and copied
        // into state — read-only, never written back to the engine. A failed
        // read is best-effort: chat still opens on the previous selection.
        if (!fetchMeta) return
        val h = handle ?: return
        viewModelScope.launch {
            when (val result = h.sessions.get(sessionID)) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    activeModel = result.value.model ?: _state.value.activeModel,
                    activeAgent = result.value.agent ?: _state.value.activeAgent,
                )
                is ApiResult.Failure -> Unit
            }
        }
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
        _state.value = _state.value.copy(failure = null, failureKey = null, retryKind = RetryKind.NONE)
    }

    override fun onCleared() {
        collector?.cancel()
        repository?.close()
        super.onCleared()
    }

    companion object {
        private const val DRAFT_KEY = "chat_draft"
    }
}

/**
 * Maps repository state to renderable messages.
 *
 * Completed history and in-flight streaming text are merged here so the screen
 * never has to know that they come from two different sources.
 *
 * @param toolDurations client-side per-tool durations keyed by tool id (see
 * [ChatViewModel]); passed through to each assistant turn.
 */
private fun ChatState.toUiMessages(toolDurations: Map<String, Long> = emptyMap()): List<ChatMessage> {
    val result = mutableListOf<ChatMessage>()
    messages.forEach { message ->
        when {
            message.isUser -> result += ChatMessage(
                id = message.id,
                role = Role.USER,
                text = message.text.orEmpty(),
                createdAtMillis = message.time?.created,
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
                    createdAtMillis = message.time?.created,
                )
            message.isAssistant -> {
                val msgLiveTools = liveTools.values.filter { it.messageID == message.id }.map { it.call }
                val msgStreamingText = streaming[message.id]
                val isStreamingMsg = streaming.containsKey(message.id) || msgLiveTools.any { it.status == "running" || it.status == "streaming" }
                val initialTools = message.content.mapNotNull { it.toToolCall() }
                val allTools = (initialTools + msgLiveTools).distinctBy { it.id }

                val parts = buildTurnParts(
                    content = message.content,
                    streamingText = msgStreamingText,
                    isStreaming = isStreamingMsg,
                    liveTools = msgLiveTools,
                    explicitReasoning = message.reasoningText,
                    fallbackText = message.plainText,
                )

                result += ChatMessage(
                    id = message.id,
                    role = Role.ASSISTANT,
                    text = msgStreamingText ?: message.plainText,
                    reasoning = message.reasoningText,
                    tools = allTools,
                    parts = parts,
                    streaming = isStreamingMsg,
                    agent = message.agent,
                    model = message.model,
                    createdAtMillis = message.time?.created,
                    // Turn footer from the engine DTO (finding 6): tokens
                    // are input + output only — reasoning/cache are subsets
                    // of those, never added on top. Duration is completed −
                    // created; thinking time is streamed − created while
                    // reasoning is present. Any missing timestamp leaves its
                    // field null rather than a guessed value.
                    usageTokens = message.tokens?.let { tokens ->
                        listOfNotNull(tokens.input, tokens.output).sum()
                            .takeIf { tokens.input != null || tokens.output != null }
                    },
                    usageCostUsd = message.cost,
                    turnDurationMs = message.time?.let { time ->
                        if (time.created != null && time.completed != null &&
                            time.completed >= time.created
                        ) {
                            time.completed - time.created
                        } else {
                            null
                        }
                    },
                    stopReason = message.finish?.takeIf { it.isNotBlank() },
                    thinkingSeconds = message.time?.let { time ->
                        if (!message.reasoningText.isNullOrBlank() &&
                            time.created != null && time.streamed != null &&
                            time.streamed >= time.created
                        ) {
                            ((time.streamed - time.created) / 1000).toInt()
                        } else {
                            null
                        }
                    },
                    toolDurations = toolDurations,
                )
            }
            // `idle` markers are engine bookkeeping, not conversation.
        }
    }

    // Append streaming text for messages not yet in history.
    streaming.forEach { (messageID, text) ->
        val existing = result.indexOfFirst { it.id == messageID }
        if (existing >= 0) {
            val curr = result[existing]
            val updatedParts = if (curr.parts.isNotEmpty()) {
                val copy = curr.parts.toMutableList()
                val last = copy.lastOrNull()
                if (last is ChatTurnPart.Text) {
                    copy[copy.lastIndex] = ChatTurnPart.Text(text)
                } else {
                    copy += ChatTurnPart.Text(text)
                }
                copy
            } else {
                listOf(ChatTurnPart.Text(text))
            }
            result[existing] = curr.copy(text = text, streaming = true, parts = updatedParts)
        } else {
            result += ChatMessage(
                id = messageID,
                role = Role.ASSISTANT,
                text = text,
                streaming = true,
                parts = listOf(ChatTurnPart.Text(text)),
                // No history entry yet, so no engine timestamp: "now" is
                // the honest value, and it keeps a fresh turn from
                // inheriting yesterday's divider.
                createdAtMillis = System.currentTimeMillis(),
            )
        }
    }

    // Live tool calls win over history for the same call id; calls for a message
    // that has no text yet still need a message to hang off.
    liveTools.values.groupBy { it.messageID }.forEach { (messageID, live) ->
        var idx = result.indexOfFirst { it.id == messageID }
        val liveCalls = live.map { it.call }
        val liveIds = liveCalls.map { it.id }.toSet()
        if (idx < 0) {
            val parts = buildTurnParts(
                content = emptyList(),
                liveTools = liveCalls,
                isStreaming = true,
            )
            result += ChatMessage(
                id = messageID,
                role = Role.ASSISTANT,
                text = "",
                tools = liveCalls,
                parts = parts,
                streaming = true,
                createdAtMillis = System.currentTimeMillis(),
            )
        } else {
            val curr = result[idx]
            val mergedTools = curr.tools.filter { it.id !in liveIds } + liveCalls
            val parts = if (curr.parts.isEmpty()) {
                buildTurnParts(content = emptyList(), liveTools = mergedTools, isStreaming = curr.streaming)
            } else {
                curr.parts
            }
            result[idx] = curr.copy(tools = mergedTools, parts = parts)
        }
    }
    // Merge contiguous assistant messages into one unified turn
    val merged = mutableListOf<ChatMessage>()
    result.forEach { msg ->
        val last = merged.lastOrNull()
        if (last != null && last.role == Role.ASSISTANT && msg.role == Role.ASSISTANT) {
            val combinedText = listOf(last.text, msg.text).filter { it.isNotBlank() }.joinToString("\n\n")
            val combinedReasoning = listOfNotNull(last.reasoning, msg.reasoning).filter { it.isNotBlank() }.joinToString("\n\n").ifEmpty { null }
            val toolMap = last.tools.associateBy { it.id }.toMutableMap()
            msg.tools.forEach { tool -> toolMap[tool.id] = tool }
            val orderedIds = (last.tools.map { it.id } + msg.tools.map { it.id }).distinct()
            val mergedTools = orderedIds.mapNotNull { toolMap[it] }
            val mergedParts = mergeTurnParts(last.parts, msg.parts)
            merged[merged.lastIndex] = last.copy(
                text = combinedText,
                reasoning = combinedReasoning,
                tools = mergedTools,
                parts = mergedParts,
                streaming = last.streaming || msg.streaming,
                agent = msg.agent ?: last.agent,
                model = msg.model ?: last.model,
                // Footer fields combine across the merged chunks: token/cost/
                // duration totals sum, the stop reason and thinking time come
                // from the last chunk that has them, tool durations union.
                usageTokens = sumOrNull(last.usageTokens, msg.usageTokens),
                usageCostUsd = sumOrNull(last.usageCostUsd, msg.usageCostUsd),
                turnDurationMs = sumOrNull(last.turnDurationMs, msg.turnDurationMs),
                stopReason = msg.stopReason ?: last.stopReason,
                thinkingSeconds = msg.thinkingSeconds ?: last.thinkingSeconds,
                toolDurations = last.toolDurations + msg.toolDurations,
            )
        } else {
            merged += msg
        }
    }
    return merged
}

/** Sums two nullable totals; null when both are absent. */
private fun sumOrNull(a: Long?, b: Long?): Long? =
    if (a == null && b == null) null else (a ?: 0L) + (b ?: 0L)

/** Sums two nullable totals; null when both are absent. */
private fun sumOrNull(a: Double?, b: Double?): Double? =
    if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)
