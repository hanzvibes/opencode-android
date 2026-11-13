package dev.bidzz.opencode.ui.chat

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.R
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.ChatRepository
import dev.bidzz.opencode.core.api.ChatState
import dev.bidzz.opencode.core.api.LiveTool
import dev.bidzz.opencode.core.api.VcsApi
import dev.bidzz.opencode.core.api.dto.ModelRef
import dev.bidzz.opencode.core.api.dto.SessionInfo
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.api.dto.TokenUsage
import dev.bidzz.opencode.core.api.newMessageId
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.core.engine.EngineStatus
import dev.bidzz.opencode.ui.engineDownFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import dev.bidzz.opencode.core.api.dto.toToolCall
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** What the chat screen renders. */
data class ChatUiState(
    val engineState: EngineState = EngineState.STOPPED,
    val sessionID: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    /** True when the transcript window cut older messages: more are available above. */
    val hasMoreOlder: Boolean = false,
    /** True while an explicit "load earlier" fetch is in flight. */
    val loadingEarlier: Boolean = false,
    val busy: Boolean = false,
    /**
     * True from [ChatViewModel.sendText] until the engine echoes the prompt
     * back as a user message — or the send fails. While true the transcript
     * ends in the optimistic bubble (see [ChatViewModel.stageOptimistic])
     * and the working placeholder stays hidden: the bubble itself is the
     * sign something is happening.
     */
    val sending: Boolean = false,
    val connecting: Boolean = false,
    /** What went wrong, as a value; the screen words it in the app’s language. */
    val failure: ApiResult.Failure? = null,
    /** A message with no failure behind it, e.g. the engine never started. */
    val failureKey: Int? = null,
    /**
     * A model/agent selection the engine rejected (audit-100 Fase 6b,
     * 43-F2), owned by the view model — NOT by the repository. `onRepoState`
     * copies the repo's `lastFailure` into [failure] on every emission, and
     * a successful send clears the repo's failure, so a selection failure
     * stored only in [failure] blinked out as soon as the send landed and
     * the chip kept showing the rejected model. This field survives the
     * collector: [failure] shows it until it is dismissed, superseded by
     * a new selection, or the session changes. A rejected pick reverts
     * the chip to null (engine default) — the same rule `selectModel`
     * already applies for an existing session.
     */
    val selectionFailure: ApiResult.Failure? = null,
    /** True while the engine answers requests — `DEGRADED` counts, it keeps its credentials. */
    val workspaceReady: Boolean = false,
    val workspacePath: String? = null,
    val draft: String = "",
    /**
     * Files picked from outside the workspace, read in as text and waiting
     * to be inlined into the next send as fenced blocks (see
     * [buildMessageWithAttachments]). Per session, like the draft: a
     * session switch or a new session clears them, and the palette
     * draft-restore path never touches them.
     */
    val pendingAttachments: List<AttachedFile> = emptyList(),
    /**
     * A refused attachment, as a string resource id plus the filename to
     * format into it (file too large, not a text file, unreadable). Shown
     * as a dismissable row above the composer — not the pinned error
     * banner, which is for engine failures. Null when nothing was refused.
     */
    val attachNoticeKey: Int? = null,
    val attachNoticeArg: String? = null,
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
    /** A follow-up typed while the agent is working, sent when the run ends.
     * A list, not one slot: queuing a second follow-up must not overwrite
     * the first (item 6). [fullText] is what the engine receives — typed
     * text plus any fenced attachment blocks already baked in — so a flush
     * never re-reads the draft or re-appends attachments. [typedText] is
     * what the user typed: only that goes into prompt history, so pressing
     * Up never restores a 64 KB file dump into the composer. */
    val queuedPrompts: List<QueuedPrompt> = emptyList(),
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
    /** Skills offered by the engine (`GET /api/skill`); shown in the `/` palette, read-only. */
    val skills: List<dev.bidzz.opencode.core.api.dto.SkillInfo> = emptyList(),
    /** Active git branch when inside a version-controlled workspace. */
    val branch: String? = null,
    /** Branch names for the workspace (`GET /api/vcs/branch`); null until loaded. */
    val branches: List<String>? = null,
    val branchesLoading: Boolean = false,
    val branchesFailed: Boolean = false,
    /**
     * Current/default branch and the review base (`GET /api/vcs`,
     * `GET /api/vcs/base`, Tier 3 item 4); null until loaded. A null base
     * after a successful load is a fact about the repository (no base
     * metadata), not a failure — the dialog hides the row instead of
     * reporting it. [vcsDetailsFailed] is set when either call fails, so a
     * missing row is never silently blamed on the repository.
     */
    val vcsInfo: dev.bidzz.opencode.core.api.dto.VcsInfo? = null,
    val vcsBase: dev.bidzz.opencode.core.api.dto.VcsBase? = null,
    val vcsDetailsFailed: Boolean = false,
    /**
     * Tokens currently in the session's context window (`GET /api/session/{id}/context`),
     * from the LAST assistant message that reported usage — its `input` is
     * the whole prompt the next turn builds on, so summing every message
     * would count the same history once per turn (audit-100 Fase 6a, 23-F1).
     * Null while unknown — the meter hides rather than showing a stale number.
     * [contextModel] is the model that produced that last usage: the
     * denominator must come from it, not from the model selected now, or a
     * mid-session model switch mixes two windows in one percentage (77-F3).
     */
    val contextUsedTokens: Long? = null,
    val contextModel: ModelRef? = null,
    /** True while a fork is in flight. Guards double taps (see [ChatViewModel.forkMessage]). */
    val forking: Boolean = false,
    /** True while a manual compaction is in flight. */
    val compacting: Boolean = false,
    /** A staged session revert waiting for commit or clear, or null. */
    val stagedRevert: dev.bidzz.opencode.core.api.dto.SessionRevert? = null,
    /** True while a revert commit or clear is in flight. */
    val revertBusy: Boolean = false,
)

/** One follow-up waiting for the run to end (see [ChatUiState.queuedPrompts]). */
data class QueuedPrompt(
    /** What the engine receives: typed text plus baked-in attachment blocks. */
    val fullText: String,
    /** What the user typed, for prompt history (never the attachment blocks). */
    val typedText: String,
    /**
     * The files baked into [fullText]. They are carried so the queue can
     * give them back as chips when the engine dies (audit-70 13-F3) instead
     * of losing the file content with the baked block. The drain sends
     * [fullText] directly, so these are never baked a second time.
     */
    val attachments: List<AttachedFile> = emptyList(),
    /**
     * Unique identity (audit-200 A5, DL-001): entries are matched by [id],
     * never by text — two follow-ups with the same words are two different
     * follow-ups, and removing one must not remove both. Fresh entries get
     * a random id at construction; entries read back from storage written
     * before ids existed carry a null id at runtime (Java deserialization
     * fills the missing field with null) and get one from [withUniqueIds].
     */
    val id: String = UUID.randomUUID().toString(),
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Gives every entry without an id one (audit-200 A5, DL-001): entries
 * read back from storage written before [QueuedPrompt.id] existed have a
 * null id at runtime. Entries that already have one keep it, so a second
 * pass is a no-op.
 */
internal fun List<QueuedPrompt>.withUniqueIds(): List<QueuedPrompt> =
    map { entry ->
        if ((entry.id as Any?) == null) entry.copy(id = UUID.randomUUID().toString()) else entry
    }

/**
 * Drops the flushed head entry after the engine accepted it (13-F1). Pure,
 * so the rule is unit-tested here rather than through a send: the head
 * leaves only when it is the entry that was sent — a different head (the
 * user queued more while the send was in flight) stays put, and a null
 * entry drops nothing. Matched by id (audit-200 A5, DL-001), not by value:
 * a head with the same words as the sent entry is a different follow-up.
 */
internal fun resolveFlushDrop(queue: List<QueuedPrompt>, sent: QueuedPrompt?): List<QueuedPrompt> =
    if (sent != null && queue.firstOrNull()?.id == sent.id) queue.drop(1) else queue

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
     * (`tokens.input + tokens.output + tokens.reasoning`; cache reads sit
     * inside input and are never added on top), cost, turn duration
     * (`time.completed - time.created`) and the engine `finish` string.
     * Same total as the session list shows for the same usage, so a turn
     * and its session never disagree. All null until the engine reports
     * them; the footer hides while all four are null. */
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
    /** Per-tool start stamps in epoch millis, keyed by tool id (Fase 2,
     * item 5): the stamps the durations are computed from, passed through
     * so a still-running step can show a live elapsed time. A tool the
     * client never saw live has no entry. */
    val toolStartedAtMillis: Map<String, Long> = emptyMap(),
    /**
     * The engine's error for a turn that still produced partial content
     * (01-F4): the run died mid-turn (e.g. a 429 after some tokens), and
     * without this the partial answer looks complete. The bubble shows
     * the text and names the error under it; a turn with no content at
     * all still becomes a [Role.ERROR] bubble instead. Null when the
     * engine reported no error for this turn.
     */
    val errorText: String? = null,
)

/** What the busy→idle edge does with the queued follow-ups (Fase 2, 13-F2/17-F1). */
internal enum class RunEndAction {
    /** Send the first queued entry now, as before. */
    FLUSH,
    /** Send nothing: the queue goes back to the composer instead. */
    RETURN_TO_DRAFT,
    /** No edge, no queue, or the flush cannot start — leave it alone. */
    NONE,
}

/**
 * Decides what the busy→idle edge does with the queued follow-ups (Fase 2,
 * 13-F2/17-F1). Pure so the decision is testable without a live engine.
 *
 * The old code flushed whenever the run ended, which raced Stop two ways:
 * when the interrupt event landed first the follow-up sent and restarted
 * the run the user had just stopped, and when the HTTP interrupt returned
 * first the queue stuck until another run ended. Stop now decides the
 * queue explicitly: anything queued up to the end of a stopped run goes
 * back to the composer — visible, sendable, never auto-sent — and the
 * flush only fires for a run that ended on its own.
 */
internal fun resolveRunEndAction(
    wasBusy: Boolean,
    busy: Boolean,
    stopping: Boolean,
    hasQueued: Boolean,
    sendInFlight: Boolean,
    flushing: Boolean,
    hasRepository: Boolean,
): RunEndAction {
    if (!wasBusy || busy) return RunEndAction.NONE
    if (!hasQueued || sendInFlight || flushing || !hasRepository) return RunEndAction.NONE
    return if (stopping) RunEndAction.RETURN_TO_DRAFT else RunEndAction.FLUSH
}

/**
 * What the model/agent chip shows after a `set` call the engine rejected
 * (audit-100 Fase 6b, 43-F2).
 *
 * A rejected pick reverts to null — the engine default — instead of
 * keeping the chip on a model/agent the session never runs on. A pick
 * whose `set` succeeded keeps its value. The caller applies the result
 * and records the failure separately (see `selectionFailure`).
 */
/**
 * Appends an undeliverable question answer to the draft (10-F2): the
 * same answer is never appended twice. A line equal to the answer means
 * it is already there — a re-tap on an expired card, or a second
 * undeliverable attempt, must not duplicate the line.
 */
internal fun resolveDraftAppend(current: String, displayText: String): String {
    if (current.lines().any { it == displayText }) return current
    return if (current.isBlank()) displayText else "$current\n$displayText"
}

/**
 * Whether [ChatViewModel.onResume] may still open the remembered session
 * after its verification GET returns (58-F7).
 *
 * The user may have opened another session while the GET was in flight;
 * opening the remembered one then would clobber their choice. The guard
 * mirrors [ChatViewModel.openSessionById]'s post-await check (item 23):
 * only proceed when the repository is still the one the launch captured
 * and no different session has attached since.
 */
internal fun shouldOpenAfterResume(
    attachedSessionID: String?,
    targetSessionID: String,
    repositoryChanged: Boolean,
): Boolean =
    !repositoryChanged && (attachedSessionID == null || attachedSessionID == targetSessionID)

/**
 * Whether opening a session must be refused, and with which banner
 * (56-F2/56-F6). Pure, so it is unit-testable without an engine.
 *
 * - [meta] null + [isMissing] (404 / `SessionNotFoundError`): the engine
 *   says the session is gone — refuse, keep the current session.
 * - [meta] present but [directoryMatches] false: the session belongs to
 *   another project — refuse, or prompts would land in the wrong project
 *   under the active workspace's header (the same class as item 14).
 * - [directoryMatches] null (older engines report no location): allowed
 *   through, like onResume — a null location cannot be verified, so it
 *   must not break the open.
 * - [meta] null without [isMissing]: a transient failure — allowed
 *   unchecked, and the resync banner reports the session if it is
 *   really gone.
 *
 * Returns the `@StringRes` banner key, or null to proceed.
 */
internal fun sessionOpenRefusal(
    meta: SessionInfo?,
    isMissing: Boolean,
    directoryMatches: Boolean?,
): Int? = when {
    meta == null && isMissing -> R.string.session_link_gone
    meta != null && directoryMatches == false -> R.string.session_link_wrong_workspace
    else -> null
}

/**
 * Whether the draft may be cleared after a send succeeded (13-F4).
 *
 * The old check compared values: a user who retyped the identical text
 * while the send was in flight lost the retype, because the draft still
 * equalled what was sent. Identity is what matters, not the value: the
 * draft may only be cleared when nothing changed it since the send
 * started ([editsSinceSend] == 0) and it still holds the sent text.
 */
internal fun shouldClearSentDraft(currentDraft: String, sentText: String, editsSinceSend: Int): Boolean =
    editsSinceSend == 0 && currentDraft.trim() == sentText

/** Which parts of the VCS status dialog [ChatViewModel.loadBranches] still needs to load (24-F5). */
internal data class VcsLoadPlan(val loadBranches: Boolean, val loadDetails: Boolean)

/**
 * Decides what [ChatViewModel.loadBranches] loads for [path]: the branch
 * list is ready when it was loaded for this workspace, the details when
 * `info()` AND `base()` both succeeded for it. A failed details load is
 * retried on the next expand even when the branch list is already loaded
 * — the old early-return skipped it.
 */
internal fun planVcsLoad(
    hasBranches: Boolean,
    branchesWorkspace: String?,
    detailsWorkspace: String?,
    path: String,
): VcsLoadPlan {
    val branchesReady = hasBranches && branchesWorkspace == path
    val detailsReady = detailsWorkspace == path
    return VcsLoadPlan(loadBranches = !branchesReady, loadDetails = !detailsReady)
}

internal data class SelectionRevert(val agent: String?, val model: ModelRef?)

internal fun resolveSelectionAfterSetFailure(
    activeAgent: String?,
    activeModel: ModelRef?,
    agentSetFailed: Boolean,
    modelSetFailed: Boolean,
): SelectionRevert = SelectionRevert(
    agent = if (agentSetFailed) null else activeAgent,
    model = if (modelSetFailed) null else activeModel,
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
    /**
     * Called when the session is reset ([newSession]): the caller forgets
     * the remembered session id, so a later resume cannot resurrect the
     * session that was just abandoned (item 14).
     */
    private val onSessionCleared: () -> Unit = {},
    /**
     * Where parked composers live (Fase 1a). Defaults to in-memory, which
     * is what unit tests get; the app passes the SharedPreferences-backed
     * store so parked drafts survive process death (57-F1).
     */
    private val parkedStore: ParkedComposerStore = InMemoryParkedComposerStore(),
) : ViewModel() {

    /**
     * What the last process left in the saved-state handle (audit-70 69-F2):
     * the queue, the pending attachment chips, the retry target and the
     * prompt history. Only the draft was persisted before, so a process
     * death silently dropped everything else the user had lined up.
     */
    private val restoredQueue: List<QueuedPrompt> =
        savedStateHandle?.get<ArrayList<QueuedPrompt>>(QUEUE_KEY)?.toList().orEmpty()
            // Audit-200 A5 (DL-001): a queue saved before ids existed
            // carries null ids at runtime; give them ids before anything
            // matches on them.
            .withUniqueIds()
    private val restoredAttachments: List<AttachedFile> =
        savedStateHandle?.get<ArrayList<AttachedFile>>(ATTACHMENTS_KEY)?.toList().orEmpty()
    private val restoredLastSent: String? = savedStateHandle?.get<String>(LAST_SENT_KEY)
    private val restoredSentPrompts: List<String> =
        savedStateHandle?.get<ArrayList<String>>(SENT_PROMPTS_KEY)?.toList().orEmpty()

    /**
     * True until the first repo state after a restore decides the restored
     * queue's fate: if the run it was waiting on is still going, the normal
     * flush sends it when the run ends; if the run is already over, the
     * queue goes back to the composer instead of sitting there forever.
     */
    private var restoredQueuePending = restoredQueue.isNotEmpty()

    private val _state = MutableStateFlow(
        ChatUiState(
            workspacePath = workspace(),
            draft = savedStateHandle?.get<String>(DRAFT_KEY) ?: "",
            queuedPrompts = restoredQueue,
            pendingAttachments = restoredAttachments,
            lastSentText = restoredLastSent,
            sentPrompts = restoredSentPrompts,
        ),
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        // Keep the handle's copy of the queue, attachments, retry target
        // and history in step with the live state, so a process death at
        // any moment restores what the user had lined up (audit-70 69-F2).
        // Only when a handle exists: unit tests construct this view model
        // without one, and touching the scope there would need a Main
        // dispatcher the tests do not have. Writes happen only on change,
        // and only into the handle's in-memory map — the system serialises
        // it when it actually saves state.
        val handle = savedStateHandle
        if (handle != null) {
            viewModelScope.launch {
                var lastQueue: List<QueuedPrompt>? = null
                var lastAttachments: List<AttachedFile>? = null
                var lastSent: String? = null
                var lastHistory: List<String>? = null
                _state.collect { state ->
                    if (state.queuedPrompts != lastQueue) {
                        lastQueue = state.queuedPrompts
                        if (state.queuedPrompts.isEmpty()) {
                            handle.remove<ArrayList<QueuedPrompt>>(QUEUE_KEY)
                        } else {
                            handle[QUEUE_KEY] = ArrayList(state.queuedPrompts)
                        }
                    }
                    if (state.pendingAttachments != lastAttachments) {
                        lastAttachments = state.pendingAttachments
                        if (state.pendingAttachments.isEmpty()) {
                            handle.remove<ArrayList<AttachedFile>>(ATTACHMENTS_KEY)
                        } else {
                            handle[ATTACHMENTS_KEY] = ArrayList(state.pendingAttachments)
                        }
                    }
                    if (state.lastSentText != lastSent) {
                        lastSent = state.lastSentText
                        if (state.lastSentText == null) {
                            handle.remove<String>(LAST_SENT_KEY)
                        } else {
                            handle[LAST_SENT_KEY] = state.lastSentText
                        }
                    }
                    if (state.sentPrompts != lastHistory) {
                        lastHistory = state.sentPrompts
                        if (state.sentPrompts.isEmpty()) {
                            handle.remove<ArrayList<String>>(SENT_PROMPTS_KEY)
                        } else {
                            handle[SENT_PROMPTS_KEY] = ArrayList(state.sentPrompts)
                        }
                    }
                }
            }
        }
    }

    private var repository: ChatRepository? = null
    private var collector: Job? = null
    /** In-flight context-usage load, cancelled when a newer one starts. */
    private var contextUsageJob: Job? = null
    /** Last wall-clock refresh of the context meter, for the 30s busy throttle (23-F3). */
    private var lastContextRefreshMillis: Long = 0L
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

    /**
     * Last unmerged transcript, for the incremental path (item 28). The
     * history loop parses every message's parts, so when only the streamed
     * text moved, the overlay re-runs on this cached list instead of
     * rebuilding everything. The refs pin the inputs: a different history
     * list, live-tool map or durations forces a full rebuild.
     */
    private var lastUnmergedMessages: List<ChatMessage> = emptyList()
    private var lastMessagesRef: List<SessionMessage>? = null
    private var lastLiveToolsRef: Map<String, LiveTool>? = null
    private var lastToolDurations: Map<String, Long> = emptyMap()
    private var lastToolStartedAt: Map<String, Long> = emptyMap()

    /** Draft as it was before an edit loaded a message into the composer;
     * cancelling the edit restores it. Not persisted: on process death the
     * edit is abandoned and the draft stays as typed. */
    private var draftBeforeEdit: String? = null

    /**
     * Generation counter for the draft (13-F4): every change to the
     * composer text bumps it — typing, clearing, a programmatic set — so
     * a send can tell "the draft is still exactly what I sent" from "the
     * user retyped the same text while I was sending". See
     * [shouldClearSentDraft].
     */
    private var draftEditCount = 0
    /** [draftEditCount] at the moment the in-flight send started. */
    private var sentDraftEditCount = 0

    /**
     * Key the live draft was last parked under, or null. After the engine
     * dies the live draft belongs to a session that no longer has an id in
     * state (80-F5): parking it again on the next switch must refresh that
     * session's entry, not file the same text under the unsent key as well
     * — two copies would let the text come back twice. Typing clears it:
     * the parked copy no longer matches the live draft.
     */
    private var lastParkKey: String? = null

    /**
     * A deep link / notification / widget / shortcut session id that
     * arrived before the repository existed (Fase 5, 56-F1). On a cold
     * start the engine takes seconds to attach while the UI composes
     * immediately, so `openSession` used to hit `repository ?: return`
     * and the link died silently — the app opened the remembered
     * session instead, with no sign the link failed. The id waits here
     * until [attachIfNeeded] can open it; it clears when the repository
     * confirms the session ([onRepoState]) or when the user navigates
     * elsewhere explicitly ([newSession], [openSession] with a live
     * repository). In-memory only: a process death in the few seconds
     * before attach loses it, and the user taps the link again.
     */
    private var pendingOpenSessionID: String? = null

    /** Test seam for the pending open (56-F1): the id waiting for attach, if any. */
    internal fun pendingOpenForTest(): String? = pendingOpenSessionID

    /**
     * Parks the current composer — draft, pending attachments and queued
     * follow-ups (13-F6) — under the current session's id, or under
     * [UNSENT_SESSION_KEY] when there is no session yet (95-F6), or under
     * [lastParkKey] when the live draft was already parked for a session
     * that has since lost its id (80-F5). Parking nothing removes the
     * entry, so a sent draft never comes back.
     */
    private fun parkComposer() {
        val id = _state.value.sessionID ?: lastParkKey ?: UNSENT_SESSION_KEY
        val draft = _state.value.draft
        val attachments = _state.value.pendingAttachments
        val queued = _state.value.queuedPrompts
        if (draft.isNotBlank() || attachments.isNotEmpty() || queued.isNotEmpty()) {
            parkedStore.put(id, ParkedComposer(draft, attachments, queued))
            lastParkKey = id
        } else {
            parkedStore.remove(id)
            if (lastParkKey == id) lastParkKey = null
        }
    }

    /** Takes the parked composer for [sessionID], if the user left one there. */
    private fun takeParkedComposer(sessionID: String): ParkedComposer? {
        val parked = parkedStore.get(sessionID) ?: return null
        parkedStore.remove(sessionID)
        if (lastParkKey == sessionID) lastParkKey = null
        return parked
    }

    /**
     * Drops the parked composer for a deleted session (80-F6).
     *
     * Called only after the engine confirms the delete; a failed delete
     * keeps the parked draft, since the session (and its row) comes back.
     * Audit-200 A5 (DL-009): an unsent draft/queue for the deleted
     * session is still the user's words — it moves to the unsent key
     * (merging with anything already waiting there) instead of being
     * destroyed, so the next fresh chat brings it back.
     */
    fun pruneParkedSession(sessionID: String) {
        val parked = parkedStore.get(sessionID)
        parkedStore.remove(sessionID)
        if (lastParkKey == sessionID) lastParkKey = null
        if (parked != null &&
            (parked.draft.isNotBlank() || parked.attachments.isNotEmpty() || parked.queued.isNotEmpty())
        ) {
            val existing = parkedStore.get(UNSENT_SESSION_KEY)
            parkedStore.put(
                UNSENT_SESSION_KEY,
                if (existing == null) parked else mergeParkedComposers(existing, parked),
            )
        }
    }

    /**
     * Reports a parked composer that exists on disk but cannot be decoded
     * (audit-200 A5, DL-008): the saved draft is already unrecoverable,
     * so the user is told once via the error banner and the corrupt entry
     * is dropped so the notice does not repeat on every open. Returns
     * true when a notice was shown.
     */
    private fun reportUnreadableParked(sessionID: String): Boolean {
        if (!parkedStore.hasUnreadableEntry(sessionID)) return false
        parkedStore.remove(sessionID)
        _state.value = _state.value.copy(failureKey = R.string.chat_parked_unreadable)
        return true
    }

    /**
     * The follow-up the flush is currently sending (13-F1). It stays at
     * the head of the queue until the engine answers: dropping it first
     * meant a failed send lost the text with no way back, and the rest of
     * the queue stuck behind it. [sendNow] settles it — dropping it on
     * success, leaving it queued on failure or abandonment.
     */
    private var flushingEntry: QueuedPrompt? = null

    /** Takes the entry the flush is sending, if one is in flight. */
    private fun takeFlushingEntry(): QueuedPrompt? {
        val entry = flushingEntry ?: return null
        flushingEntry = null
        return entry
    }

    /**
     * Settles a flushed entry once the send resolves. On success the head
     * entry leaves the live queue and its parked copy goes too, so
     * reopening the session cannot send it a second time; on failure or
     * abandonment the entry stays queued (and parked, if the user has
     * switched away since) for the next run to end or the next retry.
     */
    private fun settleFlushedEntry(sessionID: String?, sent: Boolean) {
        val entry = takeFlushingEntry() ?: return
        if (!sent) return
        _state.value = _state.value.copy(
            queuedPrompts = resolveFlushDrop(_state.value.queuedPrompts, entry),
        )
        if (sessionID != null) parkedStore.removeQueued(sessionID, entry)
    }

    /**
     * Settles a flushed entry against the parked copy only (audit-200 A5,
     * DL-007): the send succeeded, but the user has since switched
     * sessions — the live queue now belongs to the new session, so the
     * head entry leaves only the old session's parked copy and the live
     * state is left alone.
     */
    private fun settleFlushedEntryParkedOnly(sessionID: String?) {
        val entry = takeFlushingEntry() ?: return
        if (sessionID != null) parkedStore.removeQueued(sessionID, entry)
    }

    /**
     * Moves the queued follow-ups (and their attachments) back into the
     * composer. Shared by the engine-death path and the process-death
     * restore: in both cases the run the queue was waiting on is gone, so
     * the text belongs in the composer, not in a queue that will never
     * flush. Returns true when anything was returned. An attachment-only
     * entry has no typed text, so its draft part is empty — the chips carry
     * the content, and re-baking the fullText block as well would duplicate
     * it (audit-70 13-F3).
     */
    private fun returnQueueToDraft(): Boolean {
        val queued = _state.value.queuedPrompts
        val returned = queued.map {
            when {
                it.typedText.isNotBlank() -> it.typedText
                it.attachments.isEmpty() -> it.fullText
                else -> ""
            }
        }.filter { it.isNotBlank() }
        val returnedAttachments = queued.flatMap { it.attachments }.distinctBy { it.id }
        val anythingReturned = returned.isNotEmpty() || returnedAttachments.isNotEmpty()
        if (!anythingReturned) return false
        val draft = _state.value.draft
        val restoredDraft = when {
            returned.isEmpty() -> draft
            draft.isBlank() -> returned.joinToString("\n")
            else -> draft.trimEnd() + "\n" + returned.joinToString("\n")
        }
        if (returned.isNotEmpty()) {
            savedStateHandle?.set(DRAFT_KEY, restoredDraft)
        }
        _state.value = _state.value.copy(
            queuedPrompts = emptyList(),
            draft = restoredDraft,
            pendingAttachments = (returnedAttachments +
                _state.value.pendingAttachments).distinctBy { it.id },
        )
        return true
    }

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
                    // Follow-ups queued while the agent worked are the user's
                    // words: the engine dying must not throw them away
                    // (item 8). They go back into the composer — after
                    // whatever the user typed since — with a notice, instead
                    // of vanishing with the queue.
                    val anythingReturned = returnQueueToDraft()
                    // Park the draft before the session id is zeroed below
                    // (80-F5): the switch that follows calls parkComposer()
                    // when the id is already null, which no-ops for a real
                    // session — and the draft would be overwritten by the
                    // next session's composer with no copy anywhere.
                    parkComposer()
                    // The edit in progress dies with the session too
                    // (draftBeforeEdit): same staleness as the session
                    // switches in newSession/openSessionById.
                    draftBeforeEdit = null
                    _state.value = _state.value.copy(
                        sessionID = null,
                        busy = false,
                        stopping = false,
                        editingMessageId = null,
                        // 69-F1: the transcript belongs to the dead session.
                        // Leaving it while the id is null strands the UI:
                        // onResume refuses to restore the remembered
                        // session while messages are non-empty, so the
                        // stale transcript blocks the restore forever.
                        // The restore re-opens the session and reloads
                        // the messages from the engine.
                        messages = emptyList(),
                        // The engine is gone, so a prompt it was waiting on
                        // is dead too (item 22): leaving it would show
                        // buttons that silently do nothing — the repository
                        // is closed and replyPermission has nowhere to go.
                        pendingPermission = null,
                        deferredPermission = null,
                        permissionReplyFailed = false,
                        retryKind = RetryKind.NONE,
                        failureKey = when {
                            anythingReturned -> R.string.chat_queued_returned
                            status.state == EngineState.FAILED -> R.string.error_engine_not_running
                            else -> null
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
        consumePendingOpen()
    }

    /**
     * Opens the session a deep link asked for before the repository
     * existed (56-F1). The id stays pending until the repository
     * confirms it in [onRepoState], so [onResume] — which runs right
     * after attach — cannot resurrect the remembered session over it.
     * A pending id equal to the session already open clears without
     * opening: re-opening it would only clear the draft and queue.
     */
    private fun consumePendingOpen() {
        val pending = pendingOpenSessionID ?: return
        if (pending == _state.value.sessionID) {
            pendingOpenSessionID = null
            return
        }
        openSessionById(pending, fetchMeta = true)
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
    /**
     * Builds the transcript for the UI. Mid-stream only the streamed text
     * moves — history, live tools and durations are untouched — so the
     * expensive per-message part rebuild is skipped and only the streaming
     * overlay re-runs on the cached list (item 28). The contiguous merge
     * still runs every time; it is list surgery, not parsing.
     */
    private fun buildUiMessages(
        chat: ChatState,
        toolDurations: Map<String, Long>,
        toolStartedAt: Map<String, Long> = emptyMap(),
    ): List<ChatMessage> {
        if (chat.messages === lastMessagesRef && chat.liveTools === lastLiveToolsRef &&
            toolDurations == lastToolDurations && toolStartedAt == lastToolStartedAt
        ) {
            val overlaid = lastUnmergedMessages.toMutableList()
            applyStreamingOverlay(overlaid, chat.streaming)
            // A ghost streaming bubble (03-F5): the overlay only adds or
            // replaces, so an entry that came only from a past overlay — no
            // history row — and is no longer in the streaming map sticks
            // after its resync failed and cleared the map, because the
            // history reference did not change and this path runs. Drop
            // those; a history message still streaming keeps its row, and
            // an entry still in the map was just refreshed above.
            val historyIds = chat.messages.map { it.id }.toSet()
            overlaid.removeAll { it.streaming && it.id !in historyIds && it.id !in chat.streaming }
            lastUnmergedMessages = overlaid
            return mergeContiguousAssistant(overlaid)
        }
        lastUnmergedMessages = chat.buildUnmergedMessages(toolDurations, toolStartedAt)
        lastMessagesRef = chat.messages
        lastLiveToolsRef = chat.liveTools
        lastToolDurations = toolDurations
        lastToolStartedAt = toolStartedAt
        return mergeContiguousAssistant(lastUnmergedMessages)
    }

    /** Grows the transcript window and re-fetches older messages (item 28). */
    fun loadEarlier() {
        repository?.loadEarlier()
    }

    /**
     * Folds one repository emission into [_state]. Internal so
     * [ChatViewModelTest] can drive the optimistic-bubble dedup with
     * synthetic [ChatState]s: the repository itself needs a live engine.
     */
    internal fun onRepoState(chat: ChatState) {
        val previous = _state.value
        val wasBusy = previous.busy

        // A pending deep-link open (56-F1) is done once the repository
        // confirms the session id — including the 404 case, where open()
        // still installs the id and the session-gone banner follows.
        if (pendingOpenSessionID != null && chat.sessionID == pendingOpenSessionID) {
            pendingOpenSessionID = null
        }

        // A load that succeeds resets the reload budget (item 24): the
        // session-gone guidance only fires after consecutive failed reloads.
        if (chat.lastFailure == null) sessionReloadAttempts = 0

        // A new session restarts the tool clock: ids must never be reused
        // across sessions, or a new tool would inherit an old tool's stamps.
        val sessionChanged = chat.sessionID != lastCollectorSessionID
        if (sessionChanged) {
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

        // The optimistic bubble (#6a) hands over to the engine's echo —
        // or stays, until it does. The echo is a user message with the same
        // text under an id the transcript did not have when the bubble was
        // staged: matching on the text alone would let an older message with
        // the same text stand in for the echo and drop the bubble early.
        // A run that ends without an echo (a command the engine does not
        // record as a user message) drops the bubble too — its job is done.
        // And a bubble staged for another session never lands in this one.
        val repoMessages = buildUiMessages(chat, toolDurations, toolStartedAt.toMap())
        val optimistic = pendingOptimistic
        val (finalMessages, optimisticAlive) = when {
            optimistic == null -> repoMessages to false
            optimisticSessionID != null && chat.sessionID != optimisticSessionID -> {
                pendingOptimistic = null
                repoMessages to false
            }
            repoMessages.any {
                it.role == Role.USER && it.text == optimistic.text && it.id !in optimisticKnownIds
            } -> {
                pendingOptimistic = null
                repoMessages to false
            }
            wasBusy && !chat.busy -> {
                pendingOptimistic = null
                repoMessages to false
            }
            else -> (repoMessages + optimistic) to true
        }

        _state.value = previous.copy(
            sessionID = chat.sessionID,
            messages = finalMessages,
            sending = optimisticAlive,
            hasMoreOlder = chat.hasMoreOlder,
            loadingEarlier = chat.loadingEarlier,
            busy = chat.busy,
            // A rejected model/agent selection survives the collector
            // (43-F2): the repo's lastFailure clears on a successful send,
            // which used to wipe the banner while the chip still showed
            // the rejected pick. The selection failure is the banner until
            // it is dismissed, superseded, or the session changes.
            failure = previous.selectionFailure ?: chat.lastFailure,
            // A stale guidance key outlives its failure (item 24): the
            // banner shows while failureKey != null, so the 429 guidance
            // stayed up after a later send succeeded. It clears with the
            // failure — except for the session-gone case below, which is
            // guidance in place of a failure, not after one.
            failureKey = when {
                previous.selectionFailure != null -> null
                chat.lastFailure == null -> null
                sessionGone(chat.lastFailure) -> R.string.chat_session_gone_guidance
                else -> previous.failureKey
            },
            pendingPermission = effectivePending,
            deferredPermission = effectiveDeferred,
            // While a permission prompt is outstanding the prompt is the
            // action, not the banner: Retry must not offer to re-send a
            // prompt into a run that is blocked waiting for an answer.
            retryKind = if (previous.selectionFailure != null) {
                // A rejected selection offers no Retry: re-sending the
                // prompt would not re-apply it (43-F2).
                RetryKind.NONE
            } else if (effectivePending != null || sessionGone(chat.lastFailure)) {
                RetryKind.NONE
            } else {
                retryKindFor(chat.lastFailure, chat.resyncFailed)
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

        // The run just ended and follow-ups were queued while it worked:
        // send the first one now — unless this run was stopped (13-F2/
        // 17-F1), in which case the queue goes back to the composer
        // instead of starting a new run right after the Stop. The flush
        // goes straight to sendText — never back through the draft, so
        // text the user typed after queueing is not overwritten, and never
        // through send(), so pending attachments picked for the next
        // message are not baked into it (item 16). The entry stays at the
        // head of the queue while it sends (13-F1): dropping it first
        // meant a failed send — 429, 401, session gone — lost the text
        // with no way back, and the rest of the queue stuck behind it.
        // [sendNow] settles it once the engine answers. Nothing is
        // flushed while a send is still in flight, or while the engine
        // is gone — the queue belongs to the draft then (item 8).
        when (
            resolveRunEndAction(
                wasBusy = wasBusy,
                busy = chat.busy,
                stopping = previous.stopping,
                hasQueued = _state.value.queuedPrompts.isNotEmpty(),
                sendInFlight = sendInFlight,
                flushing = flushingEntry != null,
                hasRepository = repository != null,
            )
        ) {
            RunEndAction.FLUSH -> {
                val next = _state.value.queuedPrompts.firstOrNull()
                if (next != null) {
                    flushingEntry = next
                    if (!sendText(next.fullText, next.typedText, emptyList())) {
                        flushingEntry = null
                    }
                }
            }
            RunEndAction.RETURN_TO_DRAFT -> returnQueueToDraft()
            RunEndAction.NONE -> Unit
        }

        // A queue restored after process death (audit-70 69-F2): the first
        // repo state decides its fate. A run still going clears the flag
        // and the normal flush above sends the entries when it ends; a run
        // already over means the queue would never flush, so the text goes
        // back to the composer instead of sitting there forever.
        if (restoredQueuePending) {
            restoredQueuePending = false
            if (!chat.busy) returnQueueToDraft()
        }

        // Context meter (Tier 2): a new session starts unknown, and a
        // finished run changed the context — refresh both times. A failure
        // clears the value (23-F2): keeping the old number would show a
        // stale, too-small figure, the same lie the session-switch path
        // avoids by clearing first. While a run is still going the meter
        // refreshes at most every 30s (23-F3) — throttled, so a long run's
        // streaming state does not hammer the endpoint. A run that ends
        // into a queued follow-up still refreshes: the new send will end
        // later and refresh again.
        if (sessionChanged) {
            _state.value = _state.value.copy(contextUsedTokens = null, contextModel = null)
            refreshContextUsage()
        } else if (wasBusy && !chat.busy) {
            refreshContextUsage()
        } else if (chat.busy && contextRefreshDue(System.currentTimeMillis(), lastContextRefreshMillis)) {
            refreshContextUsage()
        }
    }

    /**
     * Loads the session's context usage (Tier 2).
     *
     * An in-flight load is cancelled when a newer one starts — a slow load
     * for the previous session must not overwrite the current one. Failures
     * clear the value: the meter hides instead of showing a stale number
     * for a best-effort figure.
     */
    private fun refreshContextUsage() {
        val sessionID = _state.value.sessionID ?: return
        val h = handle ?: return
        lastContextRefreshMillis = System.currentTimeMillis()
        contextUsageJob?.cancel()
        contextUsageJob = viewModelScope.launch {
            when (val result = h.sessions.context(sessionID)) {
                is ApiResult.Success -> {
                    if (_state.value.sessionID != sessionID) return@launch
                    val usage = contextUsage(result.value)
                    _state.value = _state.value.copy(
                        contextUsedTokens = usage.tokens,
                        contextModel = usage.model,
                    )
                }
                is ApiResult.Failure -> {
                    if (_state.value.sessionID != sessionID) return@launch
                    _state.value = _state.value.copy(contextUsedTokens = null, contextModel = null)
                }
            }
        }
    }

    /**
     * Loads the workspace's branch list (Tier 2), lazily from the status
     * dialog.
     *
     * A loaded list is reused until the workspace changes. A failure sets
     * the failed flag so the dialog can say so instead of spinning
     * forever; the next expand retries — and so do the details (24-F5):
     * the old code early-returned on a loaded branch list, so a transient
     * `info()`/`base()` failure left the dialog's current-branch and base
     * rows blank forever, with a KDoc that promised a retry that never
     * happened.
     */
    private var branchesJob: Job? = null
    private var branchesWorkspace: String? = null
    /** Workspace whose `info()` AND `base()` both succeeded; details for any other workspace are stale. */
    private var vcsDetailsWorkspace: String? = null

    fun loadBranches() {
        val path = workspace()
        val h = handle ?: return
        val plan = planVcsLoad(
            hasBranches = _state.value.branches != null,
            branchesWorkspace = branchesWorkspace,
            detailsWorkspace = vcsDetailsWorkspace,
            path = path,
        )
        if (!plan.loadBranches && !plan.loadDetails) return
        branchesJob?.cancel()
        _state.value = _state.value.copy(
            // No list spinner when only the details retry: the list is
            // already on screen, only its detail rows are missing.
            branchesLoading = plan.loadBranches,
            branchesFailed = false,
            vcsDetailsFailed = false,
        )
        branchesJob = viewModelScope.launch {
            val vcs = VcsApi(h.client)
            if (plan.loadBranches) {
                when (val result = vcs.branches(path)) {
                    is ApiResult.Success -> {
                        branchesWorkspace = path
                        _state.value = _state.value.copy(
                            branches = result.value,
                            branchesLoading = false,
                            branchesFailed = false,
                        )
                    }
                    is ApiResult.Failure -> {
                        _state.value = _state.value.copy(branchesLoading = false, branchesFailed = true)
                    }
                }
            }
            // Current/default branch and the review base ride the same load
            // as the branch list: they describe the same repository, and the
            // dialog shows them together. Each is independent — one failing
            // must not hide the others.
            if (plan.loadDetails) {
                var detailsFailed = false
                var infoOk = false
                var baseOk = false
                when (val result = vcs.info(path)) {
                    is ApiResult.Success -> {
                        _state.value = _state.value.copy(vcsInfo = result.value)
                        infoOk = true
                    }
                    is ApiResult.Failure -> detailsFailed = true
                }
                when (val result = vcs.base(path)) {
                    is ApiResult.Success -> {
                        _state.value = _state.value.copy(vcsBase = result.value)
                        baseOk = true
                    }
                    is ApiResult.Failure -> detailsFailed = true
                }
                if (detailsFailed) {
                    _state.value = _state.value.copy(vcsDetailsFailed = true)
                } else if (infoOk && baseOk) {
                    vcsDetailsWorkspace = path
                }
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
    private fun retryKindFor(failure: ApiResult.Failure?, resyncFailed: Boolean = false): RetryKind {
        if (failure == null) return RetryKind.NONE
        // A failed resync (03-F2) is not a failed send: the prompt already
        // ran to completion, so RESEND would run it a second time. Re-open
        // the session instead — it re-fetches the finished answer. This
        // outranks the code branches below, including RATE_LIMITED, whose
        // handler only knows how to re-send.
        if (resyncFailed) return RetryKind.RELOAD_SESSION
        return when {
            // A stream rejected mid-session is a lost connection, not a
            // stale password (item 24): the banner already says "Reopen
            // the session", so Retry must reopen it — REAUTH restarts the
            // engine and throws the session away. Checked first because
            // the same 401 also matches isUnauthorized below. If the
            // credentials are truly dead, the reload's REST calls fail
            // with UnauthorizedError and escalate to REAUTH there.
            failure.isLostConnection -> RetryKind.RELOAD_SESSION
            failure.isUnauthorized -> RetryKind.REAUTH
            failure.code == 429 -> RetryKind.RATE_LIMITED
            failure.isSessionMissing -> RetryKind.RELOAD_SESSION
            failure.isLocationMissing || failure.isNoSession ||
                failure.isBinaryFile || failure.isNoPendingPermission -> RetryKind.NONE
            // RESEND only re-sends into a live session: a network error, a
            // 5xx, or a timeout may pass on retry, but a 403 or any other
            // 4xx fails the same way against a dead stream.
            else -> if (isTransient(failure) && _state.value.lastSentText != null) RetryKind.RESEND else RetryKind.NONE
        }
    }

    /** Retryable by re-sending: the failure is in the transport, not the request. */
    private fun isTransient(failure: ApiResult.Failure): Boolean =
        failure.isNetwork || failure.code in 500..599

    /**
     * True when the session is gone server-side and reloads keep failing.
     *
     * [reloadSession] re-opens the same id; a session the engine no longer
     * has answers 404 every time, so after [SESSION_RELOAD_LIMIT] attempts
     * the banner stops offering another reload and points at starting a
     * new session instead (item 24).
     */
    private fun sessionGone(failure: ApiResult.Failure?): Boolean =
        failure?.isSessionMissing == true && sessionReloadAttempts >= SESSION_RELOAD_LIMIT

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
                    is ApiResult.Failure -> reportFailure(r, RetryKind.NONE)
                }
            }
            h.models.list().let { r ->
                when (r) {
                    is ApiResult.Success -> _state.value = _state.value.copy(models = r.value)
                    is ApiResult.Failure -> reportFailure(r, RetryKind.NONE)
                }
            }
            h.commands.list().let { r ->
                when (r) {
                    is ApiResult.Success -> _state.value = _state.value.copy(commands = r.value)
                    is ApiResult.Failure -> Unit // commands are optional; an empty set is normal
                }
            }
            h.skills.list().let { r ->
                when (r) {
                    is ApiResult.Success -> _state.value = _state.value.copy(skills = r.value)
                    is ApiResult.Failure -> Unit // skills are optional; an empty set is normal
                }
            }
        }
    }

    /** Switches the session's agent (mode). Applies to the next prompt. */
    fun selectAgent(agentID: String) {
        val sessionID = _state.value.sessionID
        val h = handle ?: return
        // Optimistic: the chip moves at once. If the engine rejects the
        // switch the chip must move back (item 23) — otherwise it shows an
        // agent the prompt never runs on. Only revert when the user is still
        // on this session showing the rejected agent; a newer pick wins.
        val previous = _state.value.activeAgent
        // A new pick supersedes a rejected one (43-F2): the old selection
        // failure described a pick that is no longer current, so it clears
        // with the pick — but only the selection failure; an unrelated
        // send failure underneath stays visible.
        val picked = _state.value
        _state.value = picked.copy(
            activeAgent = agentID,
            failure = if (picked.selectionFailure != null) null else picked.failure,
            selectionFailure = null,
        )
        // A mode chosen before the first message has no session to bind to yet;
        // createSession picks it up from activeAgent when the session is made.
        if (sessionID == null) return
        viewModelScope.launch {
            when (val result = h.agents.set(sessionID, agentID)) {
                is ApiResult.Failure -> {
                    val current = _state.value
                    if (current.sessionID == sessionID && current.activeAgent == agentID) {
                        _state.value = current.copy(activeAgent = previous)
                    }
                    reportSelectionFailure(result)
                }
                is ApiResult.Success -> Unit
            }
        }
    }

    /** Switches the session's model. */
    fun selectModel(model: dev.bidzz.opencode.core.api.dto.ModelRef) {
        val sessionID = _state.value.sessionID
        val h = handle ?: return
        val previous = _state.value.activeModel
        // A new pick supersedes a rejected one (43-F2) — see selectAgent.
        val picked = _state.value
        _state.value = picked.copy(
            activeModel = model,
            failure = if (picked.selectionFailure != null) null else picked.failure,
            selectionFailure = null,
        )
        if (sessionID == null) return
        viewModelScope.launch {
            when (val result = h.sessions.setModel(sessionID, model)) {
                is ApiResult.Failure -> {
                    val current = _state.value
                    if (current.sessionID == sessionID && current.activeModel == model) {
                        _state.value = current.copy(activeModel = previous)
                    }
                    reportSelectionFailure(result)
                }
                is ApiResult.Success -> Unit
            }
        }
    }

    fun onDraftChange(text: String) {
        // New typing supersedes whatever was parked: the parked copy no
        // longer matches the live draft, so the next park must file it
        // under the live session (or the unsent key), not refresh a stale
        // entry for a session the user has left (see [lastParkKey]).
        // Audit-200 A5 (DL-020): only while a live session owns the draft.
        // After the engine dies the session id is null and the draft still
        // belongs to lastParkKey's session — clearing the anchor here files
        // the edited draft under the unsent key, where New Chat picks it
        // up, while the old session keeps a stale snapshot.
        if (_state.value.sessionID != null) lastParkKey = null
        savedStateHandle?.set(DRAFT_KEY, text)
        draftEditCount++
        _state.value = _state.value.copy(draft = text)
    }

    /** Clears the draft in both the UI state and the saved-state handle. */
    private fun clearDraft() {
        // Audit-200 A5 (DL-020): same anchor rule as onDraftChange — a
        // clear with no live session must not drop the anchor either;
        // the next park then removes the anchored entry instead of
        // leaving a stale snapshot behind.
        if (_state.value.sessionID != null) lastParkKey = null
        savedStateHandle?.remove<String>(DRAFT_KEY)
        draftEditCount++
        _state.value = _state.value.copy(draft = "")
    }

    /** Sends the draft, creating a session first if there is none. */
    fun send() {
        val typed = _state.value.draft.trim()
        val attachments = _state.value.pendingAttachments
        val full = buildMessageWithAttachments(typed, attachments)
        if (full.isBlank()) return
        // While the agent works the composer stays typable, and sending queues
        // the follow-up instead of dropping it: the queue flushes one entry
        // at a time when each run ends (see onRepoState). The draft is
        // cleared so the queued text is not also still sitting in the
        // composer. The attachments are baked into the queued text and
        // cleared with it, so the flush — which sends the entry directly —
        // cannot append them a second time. When the entry is dropped as a
        // duplicate the draft is kept, not cleared: the text was never
        // accepted anywhere, so clearing it would silently destroy what the
        // user typed (audit-70 13-F1). The files stay pending too: they were
        // never baked anywhere.
        if (_state.value.busy || sendInFlight) {
            if (queuePrompt(full, typed, attachments)) {
                _state.value = _state.value.copy(pendingAttachments = emptyList())
                clearDraft()
            }
            return
        }
        sendText(full, typed, attachments)
    }

    /**
     * Appends a follow-up to the queue (see [ChatUiState.queuedPrompts]).
     * Returns false when the entry was dropped as a duplicate.
     *
     * The queue is a list, so a second follow-up never overwrites the first.
     * A text identical to the last prompt actually sent — or to the entry
     * already at the back of the queue — is dropped: that is a double-tap
     * on Send, not a second message, and sending it twice would run the
     * same prompt twice. Queueing never touches
     * [ChatUiState.pendingAttachments] itself: callers that baked files
     * into the queued text clear them, and callers that did not (a resend,
     * a question answer) leave them pending for the message being typed.
     */
    private fun queuePrompt(
        fullText: String,
        typedText: String,
        attachments: List<AttachedFile> = emptyList(),
    ): Boolean {
        val current = _state.value.queuedPrompts
        val duplicate = fullText == _state.value.lastSentText ||
            current.lastOrNull()?.fullText == fullText
        if (duplicate) return false
        _state.value = _state.value.copy(
            queuedPrompts = current + QueuedPrompt(fullText, typedText, attachments),
        )
        return true
    }

    private var sendInFlight = false

    /**
     * The optimistic user bubble (#6a): appended to [ChatUiState.messages]
     * the moment [sendText] runs, so the transcript answers a send at once
     * instead of staying silent until the engine echoes the prompt. The
     * echo is a user message with the same text under an engine id; the
     * bubble hands over to it in [onRepoState]. [optimisticKnownIds] is the
     * set of message ids the transcript already had when the bubble was
     * staged — an older message with the same text never counts as the
     * echo. [optimisticSessionID] is the session the bubble belongs to
     * (null while the session is still being created).
     */
    private var pendingOptimistic: ChatMessage? = null
    private var optimisticKnownIds: Set<String> = emptySet()
    private var optimisticSessionID: String? = null

    /**
     * Stages the optimistic bubble for [fullText] (#6a). Internal so
     * [ChatViewModelTest] can drive the dedup without an engine: the
     * repository is the only thing a unit test cannot fake cheaply.
     */
    internal fun stageOptimistic(fullText: String) {
        // A bubble left over from an abandoned send must not linger under
        // the new one: a send is serialised by sendInFlight, so at most
        // one optimistic bubble can be alive at a time.
        val previous = pendingOptimistic
        val optimistic = ChatMessage(
            id = "local:${UUID.randomUUID()}",
            role = Role.USER,
            text = fullText,
            createdAtMillis = System.currentTimeMillis(),
        )
        pendingOptimistic = optimistic
        optimisticKnownIds = _state.value.messages.map { it.id }.toSet()
        optimisticSessionID = _state.value.sessionID
        _state.value = _state.value.copy(
            messages = _state.value.messages
                .filter { it.id != previous?.id } + optimistic,
            sending = true,
        )
    }

    /** Drops the optimistic bubble: the send failed or never happened. */
    private fun clearOptimistic() {
        val optimistic = pendingOptimistic ?: return
        pendingOptimistic = null
        optimisticKnownIds = emptySet()
        optimisticSessionID = null
        _state.value = _state.value.copy(
            messages = _state.value.messages.filter { it.id != optimistic.id },
            sending = false,
        )
    }

    /** Consecutive reloadSession() attempts since the last successful load (item 24). */
    private var sessionReloadAttempts = 0

    /**
     * Sends [fullText] as a new prompt, recording it as the retry target and
     * the typed part in prompt history. Shared by [send], [submitEdit] and
     * [resendMessage] so all three record the same retry state.
     *
     * [fullText] is what the engine receives — typed text plus any fenced
     * attachment blocks — so a retry re-sends exactly what was sent,
     * attachments included. [typedText] is what the user typed: only that
     * goes into history navigation, so pressing Up never restores a 64 KB
     * file dump into the composer. [attachments] are the files baked into
     * [fullText]; they are cleared once the engine accepts the prompt, by
     * id, so a file picked mid-send survives.
     *
     * Returns false when no send started (no repository): the flush uses
     * that to clear its in-flight marker instead of waiting on a send that
     * will never resolve (13-F1).
     */
    private fun sendText(fullText: String, typedText: String, attachments: List<AttachedFile>): Boolean {
        val repo = repository ?: run {
            _state.value = _state.value.copy(failureKey = R.string.error_engine_not_running)
            return false
        }
        _state.value = _state.value.copy(
            lastSentText = fullText,
            // Consecutive duplicates add nothing to history navigation.
            // A send that is only attachments adds no typed text at all.
            sentPrompts = if (typedText.isBlank() || _state.value.sentPrompts.lastOrNull() == typedText) {
                _state.value.sentPrompts
            } else {
                (_state.value.sentPrompts + typedText).takeLast(50)
            },
        )
        // The bubble goes up now, before the coroutine runs: the user
        // sees their message at once, and the echo hands over to it later.
        stageOptimistic(fullText)
        sendInFlight = true
        // Snapshot the draft generation (13-F4): anything the user types
        // from here on — even the identical text — is new typing, not the
        // draft this send owns, and must survive the send succeeding.
        sentDraftEditCount = draftEditCount
        // The session and the repository are captured here, before the
        // coroutine runs: reading them inside sendNow — after a suspension —
        // could pick up a session the user has already left (item 15).
        val sessionID = _state.value.sessionID
        viewModelScope.launch {
            try {
                sendNow(repo, sessionID, fullText, typedText, attachments)
            } finally {
                sendInFlight = false
            }
        }
        return true
    }

    /**
     * Adds a file the system picker returned (already read in as text by
     * [readSafFile]). A re-pick of the same file — same name and same
     * content — is ignored instead of stacking a duplicate chip; a
     * different file with the same name is kept, so two folders' notes
     * can share a filename. Adding a file clears any refusal notice: the
     * notice was about the previous pick, not this one.
     */
    fun addAttachment(file: AttachedFile) {
        val current = _state.value.pendingAttachments
        if (isDuplicateAttachment(current, file)) return
        _state.value = _state.value.copy(
            pendingAttachments = current + file,
            attachNoticeKey = null,
            attachNoticeArg = null,
        )
    }

    /** Removes one pending attachment by id; its chip and its fenced block go with it. */
    fun removeAttachment(id: String) {
        _state.value = _state.value.copy(
            pendingAttachments = withoutAttachment(_state.value.pendingAttachments, id),
        )
    }

    /** Shows a refusal for a file the picker returned but we would not attach. */
    fun reportAttachRefusal(@androidx.annotation.StringRes key: Int, filename: String?) {
        _state.value = _state.value.copy(attachNoticeKey = key, attachNoticeArg = filename)
    }

    /** Collects system-picker (SAF) results in [viewModelScope] (15-F4). */
    private var safPicksJob: Job? = null

    /**
     * Subscribes to picked files from the system file picker and reads each
     * one in as a composer attachment.
     *
     * 15-F4: the collection used to live in a `LaunchedEffect` inside the
     * chat screen, so leaving the screen mid-read cancelled the read and
     * the pick vanished without a trace. The view model outlives the chat
     * composition (it is created in OpenCodeApp, which hosts every
     * screen), so a read started in chat still lands when the user comes
     * back. [readFile] is a parameter — not a direct `readSafFile` call —
     * so tests can drive this with a fake reader: the read itself needs a
     * `ContentResolver`, which JVM unit tests cannot construct.
     */
    fun observeSafPicks(picks: Flow<Uri>, readFile: suspend (Uri) -> SafReadResult) {
        if (safPicksJob != null) return
        safPicksJob = viewModelScope.launch {
            picks.collect { uri ->
                val result = withContext(Dispatchers.IO) { readFile(uri) }
                handleSafReadResult(result)
            }
        }
    }

    /** Applies one [SafReadResult]: attach it, or show why it was refused. */
    internal fun handleSafReadResult(result: SafReadResult) {
        when (result) {
            is SafReadResult.Success -> addAttachment(result.file)
            is SafReadResult.TooLarge -> reportAttachRefusal(R.string.attach_file_too_large, result.name)
            is SafReadResult.NotText -> reportAttachRefusal(R.string.attach_not_text_file, result.name)
            is SafReadResult.Unreadable -> reportAttachRefusal(R.string.attach_unreadable_file, result.name)
        }
    }

    /** Dismisses the attachment refusal row above the composer. */
    fun dismissAttachNotice() {
        _state.value = _state.value.copy(attachNoticeKey = null, attachNoticeArg = null)
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
        // Already editing another message: finish that edit first, so the
        // draft it was guarding is restored before a new backup is taken
        // (13-F3). Taking a backup now would overwrite the original draft
        // with the first edit's text, and Cancel would restore the wrong one.
        if (_state.value.editingMessageId != null) cancelEdit()
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
        // An edit submits like a send, attachments included: a file picked
        // while editing is part of the message being submitted, not the
        // draft that was there before the edit.
        val attachments = _state.value.pendingAttachments
        val full = buildMessageWithAttachments(text, attachments)
        if (_state.value.busy || sendInFlight) {
            if (queuePrompt(full, text, attachments)) {
                _state.value = _state.value.copy(pendingAttachments = emptyList())
                draftBeforeEdit = null
                _state.value = _state.value.copy(editingMessageId = null)
                clearDraft()
            }
            // Dropped as a duplicate: stay in edit mode. draftBeforeEdit and
            // editingMessageId are untouched, so Cancel still restores the
            // pre-edit draft instead of losing both the edit and its backup
            // (audit-70 13-F1).
            return
        }
        draftBeforeEdit = null
        _state.value = _state.value.copy(editingMessageId = null)
        sendText(full, text, attachments)
    }

    /** Re-sends one user message's text (its own Resend pill). */
    fun resendMessage(messageId: String) {
        val text = _state.value.messages
            .firstOrNull { it.id == messageId && it.role == Role.USER }
            ?.text?.trim()
            .takeIf { !it.isNullOrBlank() } ?: return
        if (_state.value.busy || sendInFlight) {
            // Queued as text only: the resend carries no attachments, and
            // queueing must not burn the files pending for the message the
            // user is typing (item 16).
            queuePrompt(text, text)
            return
        }
        sendText(text, text, emptyList())
    }

    /**
     * Forks the session from one user message (`POST /api/session/{id}/fork`,
     * verified against the pinned engine source at fork pin 92b2ca0 — see
     * [ForkRequest]): the forked session contains everything up to
     * (excluding) [messageId], and opens in place of this one. A fork
     * while the agent is busy is allowed — the fork copies messages, not
     * the run — but a second fork waits for the first: the [ChatUiState.forking]
     * guard means a double tap cannot fork twice. A failure is reported
     * through the error banner; an empty session cannot be forked at all
     * (the engine answers `InvalidRequestError`, kind `empty_session`).
     */
    fun forkMessage(messageId: String) {
        val sessionID = _state.value.sessionID ?: return
        val sessions = handle?.sessions ?: return
        if (_state.value.forking) return
        _state.value = _state.value.copy(forking = true)
        viewModelScope.launch {
            when (val result = sessions.fork(sessionID, before = messageId)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(forking = false)
                    openSession(result.value.id)
                }
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(forking = false)
                    reportFailure(result)
                }
            }
        }
    }

    /**
     * Forks the whole session through its latest message. Same endpoint
     * as [forkMessage] with no `before`: the fork inherits the parent's
     * instructions and opens in place of it.
     */
    fun forkSession() {
        val sessionID = _state.value.sessionID ?: return
        val sessions = handle?.sessions ?: return
        if (_state.value.forking) return
        _state.value = _state.value.copy(forking = true)
        viewModelScope.launch {
            when (val result = sessions.fork(sessionID)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(forking = false)
                    openSession(result.value.id)
                }
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(forking = false)
                    reportFailure(result)
                }
            }
        }
    }

    /**
     * Compacts the session (`POST /api/session/{id}/compact`, verified
     * against the pinned engine source — see [CompactRequest]). Only
     * while idle: a compaction queued behind a run would replace the
     * transcript under the reader later with no visible trigger, so the
     * button disables while busy instead. The compaction runs as a fresh
     * inbox item ([newMessageId], delivery `"queue"`); afterwards the
     * transcript is reloaded, because the engine replaces the history
     * with the summary, and the context meter is refreshed.
     */
    fun requestCompact() {
        val sessionID = _state.value.sessionID ?: return
        val sessions = handle?.sessions ?: return
        if (_state.value.busy || _state.value.compacting) return
        _state.value = _state.value.copy(compacting = true)
        viewModelScope.launch {
            when (val result = sessions.compact(sessionID, newMessageId())) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(compacting = false)
                    reloadSession()
                    refreshContextUsage()
                }
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(compacting = false)
                    reportFailure(result)
                }
            }
        }
    }

    /**
     * Stages a revert to just before one user message (`POST
     * /api/session/{id}/revert/stage`, verified against the pinned engine
     * source — see [RevertStageRequest]). Nothing is undone yet: the
     * staged revert is shown in a banner until [commitRevert] applies it
     * or [clearRevert] drops it. Only while idle — staging while the
     * session is busy fails server-side.
     */
    fun stageRevert(messageId: String) {
        val sessionID = _state.value.sessionID ?: return
        val sessions = handle?.sessions ?: return
        if (_state.value.busy || _state.value.revertBusy) return
        _state.value = _state.value.copy(revertBusy = true)
        viewModelScope.launch {
            when (val result = sessions.revertStage(sessionID, messageId)) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    revertBusy = false,
                    stagedRevert = result.value,
                )
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(revertBusy = false)
                    reportFailure(result)
                }
            }
        }
    }

    /** Applies the staged revert, then reloads the transcript. */
    fun commitRevert() {
        val sessionID = _state.value.sessionID ?: return
        val sessions = handle?.sessions ?: return
        if (_state.value.stagedRevert == null || _state.value.revertBusy) return
        _state.value = _state.value.copy(revertBusy = true)
        viewModelScope.launch {
            when (val result = sessions.revertCommit(sessionID)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(revertBusy = false, stagedRevert = null)
                    reloadSession()
                }
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(revertBusy = false)
                    reportFailure(result)
                }
            }
        }
    }

    /** Drops the staged revert without applying it. */
    fun clearRevert() {
        val sessionID = _state.value.sessionID ?: return
        val sessions = handle?.sessions ?: return
        if (_state.value.stagedRevert == null || _state.value.revertBusy) return
        _state.value = _state.value.copy(revertBusy = true)
        viewModelScope.launch {
            when (val result = sessions.revertClear(sessionID)) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    revertBusy = false,
                    stagedRevert = null,
                )
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(revertBusy = false)
                    reportFailure(result)
                }
            }
        }
    }

    /**
     * Answers one question on a question card (audit-70 10-F1, 10-F3,
     * 44-F1). Returns true only when the engine confirmed the reply.
     *
     * The old path sent the answer as an ordinary prompt — which the
     * engine never delivered: the question tool waits on a form
     * (`forms.ask()`), the run stays paused inside the tool call, and a
     * queued prompt only drains when the run ends. The card already
     * showed "Answered" while the answer sat in a queue that could
     * never drain. Now the answer goes straight to the pending form
     * (`POST /api/session/{id}/form/{formID}/reply`, verified against
     * the pinned engine source at fork pin 92b2ca0), matched to its
     * tool call by the form metadata — so identical answers to
     * different questions go to different forms and never collide in a
     * queue dedup.
     *
     * Anything that keeps the reply from reaching the engine — engine
     * down, no session, no pending form for this card, a rejected
     * reply — returns false and puts the answer back into the composer
     * (appended, never overwriting what the user is typing) with a
     * failure notice, so the answer is never silently lost and the
     * card stays open for another try.
     */
    suspend fun answerQuestion(answer: QuestionAnswer): Boolean {
        if (answer.labels.isEmpty() || answer.displayText.isBlank()) return false
        val sessionID = _state.value.sessionID
        val sessions = handle?.sessions
        if (sessionID == null || sessions == null) {
            returnAnswerToDraft(answer.displayText)
            return false
        }
        val form = when (val result = sessions.listForms(sessionID)) {
            is ApiResult.Success -> result.value.firstOrNull { it.isQuestionFor(answer.callId) }
            is ApiResult.Failure -> null
        } ?: run {
            returnAnswerToDraft(answer.displayText)
            return false
        }
        val value = if (answer.multiple) {
            JsonArray(answer.labels.map { JsonPrimitive(it) })
        } else {
            JsonPrimitive(answer.labels.first())
        }
        return when (sessions.replyForm(sessionID, form.id, mapOf("q${answer.questionIndex}" to value))) {
            is ApiResult.Success -> true
            is ApiResult.Failure -> {
                returnAnswerToDraft(answer.displayText)
                false
            }
        }
    }

    /**
     * Puts an undeliverable question answer back into the composer
     * (audit-70 44-F1): appended after what the user is typing, never
     * overwriting it, with a failure notice so the return is visible.
     * The pending attachments are left alone — they belong to the
     * message being typed, not to the answer. The same answer is never
     * appended twice (10-F2): an expired card re-tapped after a reload
     * used to duplicate the line on every tap.
     */
    private fun returnAnswerToDraft(displayText: String) {
        onDraftChange(resolveDraftAppend(_state.value.draft, displayText))
        _state.value = _state.value.copy(failureKey = R.string.question_answer_failed)
    }

    /**
     * Asks the agent to revert a rejected edit (Fase 1, item 2).
     *
     * This is a prompt, not an engine revert: there is no verified
     * endpoint that un-applies one finished edit (see [DiffDecision]),
     * so the agent is asked to put [relativePath] back in a new turn.
     * It travels exactly like a question answer — never through the
     * draft, never with the pending attachments, queued while busy.
     */
    fun requestDiffRevert(relativePath: String, change: String? = null) {
        sendStandalonePrompt(revertPromptFor(relativePath, change))
    }

    /**
     * Sends [text] as its own prompt, without touching the draft or the
     * pending attachments. Used by [requestDiffRevert]: an operator
     * decision that must reach the engine even while the user is typing
     * something else. (Question answers used to share this path; they
     * now go to the engine as a form reply — see [answerQuestion].)
     */
    private fun sendStandalonePrompt(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (_state.value.busy || sendInFlight) {
            queuePrompt(trimmed, trimmed)
            return
        }
        sendText(trimmed, trimmed, emptyList())
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
        _state.value = _state.value.copy(failure = null, failureKey = null, selectionFailure = null)
        sendInFlight = true
        viewModelScope.launch {
            try {
                // A retry re-sends the recorded prompt verbatim — typed text
                // plus any attachment blocks it already contains — so it
                // never re-appends the (already cleared) pending files.
                // The session is captured now, like sendText does (item 15).
                sendNow(repo, _state.value.sessionID, text, text, emptyList())
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
                selectionFailure = null,
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
        sessionReloadAttempts++
        repository?.close()
        val repo = ChatRepository(attachedHandle.sessions, attachedHandle.stream, viewModelScope)
        repository = repo
        _state.value = _state.value.copy(failure = null, failureKey = null, retryKind = RetryKind.NONE, selectionFailure = null)
        collectRepo(repo)
        repo.open(id)
    }

    /**
     * Sends one prompt through [repo] into [capturedSessionID].
     *
     * Both are captured by the caller before the coroutine runs (item 15):
     * the session the user meant is the one current when Send was tapped,
     * not whichever session is current after a suspension. If the user has
     * since started a new session (a different repository) or opened
     * another session on this one, the send is abandoned — a prompt to the
     * wrong session is worse than an unsent one, and the draft still holds
     * the text. `open()` is never called here: opening a session belongs
     * to the open/switch flow only, and calling it from a send attached a
     * fresh stream to a repository the screen had already left behind.
     */
    private suspend fun sendNow(
        repo: ChatRepository,
        capturedSessionID: String?,
        text: String,
        typedText: String,
        attachments: List<AttachedFile>,
    ) {
        val sessionID = capturedSessionID ?: createSession(repo)
        if (sessionID == null) {
            clearOptimistic()
            settleFlushedEntry(null, sent = false)
            return
        }
        // The session changed under us while the send was in flight.
        if (repository !== repo) {
            clearOptimistic()
            settleFlushedEntry(sessionID, sent = false)
            return
        }
        if (repo.state.value.sessionID != sessionID) {
            clearOptimistic()
            settleFlushedEntry(sessionID, sent = false)
            return
        }
        // A leading slash is a command, not a prompt. The engine runs it and answers
        // 204; the output arrives over the event stream exactly like a normal turn.
        val result = if (text.startsWith("/")) {
            val name = text.substring(1).substringBefore(' ')
            val args = text.substringAfter(' ', "").trim()
            handle?.commands?.run(sessionID, name, args)
                ?: engineDownFailure()
        } else {
            repo.send(text)
        }
        when (result) {
            is ApiResult.Failure -> {
                clearOptimistic()
                reportFailure(result)
                settleFlushedEntry(sessionID, sent = false)
            }
            is ApiResult.Success -> {
                // Audit-200 A5 (DL-007, DL-030): the session may have
                // changed while the send was in flight. The guards above
                // only cover a switch before the await — re-check here,
                // before touching live state. A success that belongs to
                // the old session settles its parked copy only (the
                // optimistic bubble is removed by its unique id, so it
                // cannot take a new session's message with it); the new
                // session's live draft, attachments and queue are not
                // ours to clear.
                if (repository !== repo || repo.state.value.sessionID != sessionID) {
                    clearOptimistic()
                    settleFlushedEntryParkedOnly(sessionID)
                } else {
                    clearSentDraft(typedText)
                    clearSentAttachments(attachments)
                    settleFlushedEntry(sessionID, sent = true)
                }
            }
        }
    }

    /**
     * Empties the composer after the engine accepted the prompt.
     *
     * The draft survives the round trip because the user's bubble only appears once the
     * message comes back from the engine — clearing first made a slow send look like the
     * message had been thrown away. Typing during the send wins instead of being erased.
     * Identity, not value, decides (13-F4): retyping the identical text during the
     * send bumps [draftEditCount], so the retype is kept even though it equals [sent].
     */
    private fun clearSentDraft(sent: String) {
        if (shouldClearSentDraft(_state.value.draft, sent, draftEditCount - sentDraftEditCount)) {
            clearDraft()
        }
    }

    /**
     * Removes exactly the attachments that were sent, by id. A file picked
     * while the send was in flight was not part of the sent prompt, so it
     * stays pending instead of being swept away with the sent ones.
     */
    private fun clearSentAttachments(sent: List<AttachedFile>) {
        if (sent.isEmpty()) return
        val sentIds = sent.map { it.id }.toSet()
        _state.value = _state.value.copy(
            pendingAttachments = _state.value.pendingAttachments.filter { it.id !in sentIds },
        )
    }

    /**
     * The single place that records a failure (item 24): every failure
     * assignment pairs the failure with its retry kind and drops any stale
     * guidance key — the banner shows the failure's own message while one
     * is set, and a leftover key would resurface as a stale banner once
     * the failure clears. A success is a no-op. Callers that must not
     * offer a retry pass [RetryKind.NONE] explicitly (a control-list or
     * switch failure is not a send failure).
     */
    private fun reportFailure(result: ApiResult<*>, retryKind: RetryKind? = null) {
        if (result is ApiResult.Failure) {
            _state.value = _state.value.copy(
                failure = result,
                failureKey = null,
                retryKind = retryKind ?: retryKindFor(result),
            )
        }
    }

    /**
     * Records a model/agent selection the engine rejected (43-F2).
     *
     * Unlike [reportFailure] this also stores the failure in
     * [ChatUiState.selectionFailure], which `onRepoState` preserves: a
     * plain failure is overwritten by the repo's `lastFailure` on the
     * next emission, and a successful send clears the repo's failure —
     * so without this the banner blinked out as soon as the prompt
     * landed. No Retry is offered: re-sending the prompt would not
     * re-apply the rejected selection.
     */
    private fun reportSelectionFailure(result: ApiResult.Failure) {
        _state.value = _state.value.copy(
            failure = result,
            failureKey = null,
            retryKind = RetryKind.NONE,
            selectionFailure = result,
        )
    }

    private suspend fun createSession(repo: ChatRepository): String? {
        // The session was already attached while this send was queued:
        // the state just had not flowed into the UI yet. Reuse it instead
        // of creating a duplicate empty session (item 15).
        repo.state.value.sessionID?.let { return it }
        // The repository was replaced (a new session) while this send was
        // queued: do not create anything on the abandoned one.
        if (repository !== repo) return null
        val h = handle ?: return null
        _state.value = _state.value.copy(
            connecting = true,
            failure = null,
            failureKey = null,
            selectionFailure = null,
        )
        val result = h.sessions.create(workspace())
        _state.value = _state.value.copy(connecting = false)
        return when (result) {
            is ApiResult.Success -> {
                // The user opened another session while the create call was
                // in flight: leave that choice alone instead of clobbering
                // it by opening the session we just made.
                if (repository !== repo || repo.state.value.sessionID != null) return null
                repo.open(result.value.id)
                // A mode or model picked before the first message has no session to bind
                // to at the time it is chosen, so it is applied here instead. A rejected
                // selection is reported AND reverted (43-F2): the old code only reported
                // it, and the next repo emission wiped the banner while the chip kept
                // showing the rejected model — the send below runs on the engine
                // default, so the chip must say so (null = default). The prompt
                // still sends: refusing the whole message over a rejected
                // preference would lose the user's text for a setting they
                // can re-pick after the send.
                val pickedAgent = _state.value.activeAgent
                val pickedModel = _state.value.activeModel
                var agentFailed = false
                var modelFailed = false
                pickedAgent?.let {
                    when (val setResult = h.agents.set(result.value.id, it)) {
                        is ApiResult.Failure -> {
                            agentFailed = true
                            reportSelectionFailure(setResult)
                        }
                        is ApiResult.Success -> Unit
                    }
                }
                pickedModel?.let {
                    when (val setResult = h.sessions.setModel(result.value.id, it)) {
                        is ApiResult.Failure -> {
                            modelFailed = true
                            reportSelectionFailure(setResult)
                        }
                        is ApiResult.Success -> Unit
                    }
                }
                if (agentFailed || modelFailed) {
                    val reverted = resolveSelectionAfterSetFailure(
                        pickedAgent,
                        pickedModel,
                        agentFailed,
                        modelFailed,
                    )
                    _state.value = _state.value.copy(
                        activeAgent = reverted.agent,
                        activeModel = reverted.model,
                    )
                }
                result.value.id
            }
            is ApiResult.Failure -> {
                reportFailure(result)
                null
            }
        }
    }

    fun stop() {
        val repo = repository ?: return
        // A second tap while the first interrupt is still in flight must
        // not fire a second POST: the engine already stops the run, and a
        // duplicate only risks a second failure banner for the same tap.
        // Stopping with nothing running is a no-op for the same reason —
        // the composer only offers Stop while busy, so this guards callers
        // that do not check first.
        if (!_state.value.busy || _state.value.stopping) return
        // "Stopping…" until the run actually ends: the interrupt is async and
        // the button must not flip back to Send while the agent still works.
        // The collector clears it when busy goes false.
        _state.value = _state.value.copy(stopping = true)
        viewModelScope.launch {
            // The interrupt POST has its own timeout (17-F10): without it
            // a hung engine held "Stopping…" until the client's 120s call
            // timeout, with no way to retry. On timeout the button is
            // released so Stop can be tapped again — the first POST was
            // cancelled client-side, and a second interrupt is harmless
            // if the first still lands server-side.
            val result = try {
                withTimeout(STOP_TIMEOUT_MS) { repo.interrupt() }
            } catch (_: TimeoutCancellationException) {
                _state.value = _state.value.copy(
                    stopping = false,
                    failure = null,
                    failureKey = R.string.stop_unconfirmed,
                    retryKind = RetryKind.NONE,
                    selectionFailure = null,
                )
                return@launch
            }
            when (result) {
                is ApiResult.Failure -> {
                    // Stopping the run failed; re-sending the prompt would
                    // not stop anything, so no Retry is offered for it.
                    reportFailure(result, RetryKind.NONE)
                    _state.value = _state.value.copy(stopping = false)
                }
                is ApiResult.Success -> {
                    // The interrupt only asked the engine to stop — it is
                    // not the run ending (Fase 2, 13-F2/17-F1). Setting
                    // busy=false here was optimistic and raced the queue
                    // flush two ways: an interrupt event landing first let
                    // the flush send a follow-up and restart the run just
                    // stopped, and this branch landing first left the queue
                    // stuck until another run ended. So busy and stopping
                    // stay until the terminal event (or resync) clears
                    // them, and the queue's fate is decided now: Stop
                    // means stop, so the follow-ups go back to the
                    // composer instead of auto-sending after it.
                    returnQueueToDraft()
                }
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
                is ApiResult.Failure -> {
                    reportFailure(result, RetryKind.NONE)
                    _state.value = _state.value.copy(
                        // The prompt is still outstanding, so the banner offers
                        // no Retry for this failure (see onRepoState).
                        permissionReplyFailed = true,
                    )
                }
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
        // A fresh session is an explicit navigation: a deep link waiting
        // for attach (56-F1) must not yank the user back to it once the
        // engine comes up.
        pendingOpenSessionID = null
        sessionReloadAttempts = 0
        repository?.close()
        val attached = handle
        if (attached != null) {
            val repo = ChatRepository(attached.sessions, attached.stream, viewModelScope)
            repository = repo
            collectRepo(repo)
        }
        // A draft belongs to the session it was typed in; it must not follow
        // the user into the fresh session. It is parked under the old
        // session's id instead of discarded, so opening that session again
        // restores the composer (audit-70 20-F4). The last sent text goes
        // with it: retrying it in a new session would send the old session's
        // prompt. The queued follow-up, the edit in progress, any deferred
        // permission and the stop state belong to the old session too.
        // Pending attachments go with the draft: they were picked for the old
        // session's message, and the refusal notice with them.
        parkComposer()
        // The edit in progress dies with the old session (draftBeforeEdit):
        // its backup belongs to the old composer, and leaving it behind
        // lets a later Cancel restore the old session's draft into this
        // one. The edited text itself is parked above as the draft, so
        // nothing typed is lost — only the pre-edit backup goes.
        draftBeforeEdit = null
        // Text typed before any session exists lives under the unsent key
        // (95-F6): a fresh session is where it belongs, so it comes back
        // as this session's composer instead of starting empty.
        val restored = takeParkedComposer(UNSENT_SESSION_KEY)
        _state.value = _state.value.copy(sessionID = null, messages = emptyList(), failure = null, failureKey = null, selectionFailure = null, draft = restored?.draft ?: "", pendingAttachments = restored?.attachments ?: emptyList(), attachNoticeKey = null, attachNoticeArg = null, lastSentText = null, sentPrompts = emptyList(), queuedPrompts = restored?.queued ?: emptyList(), editingMessageId = null, pendingPermission = null, deferredPermission = null, stopping = false, retryKind = RetryKind.NONE, stagedRevert = null, forking = false, compacting = false, revertBusy = false)
        // Audit-200 A5 (DL-008): after the copy above resets the banner,
        // so the notice survives.
        reportUnreadableParked(UNSENT_SESSION_KEY)
        if (restored?.draft.isNullOrBlank()) {
            savedStateHandle?.remove<String>(DRAFT_KEY)
        } else {
            savedStateHandle?.set(DRAFT_KEY, restored.draft)
        }
        // The abandoned session must not come back on the next resume:
        // without this, switching workspaces reopened the old workspace's
        // session under the new project's header (item 14).
        onSessionCleared()
    }

    /** Loads an existing session. */
    fun openSession(session: SessionInfo) {
        // 56-F2: the list hands us the session's own location for free —
        // refuse a cross-workspace pick before parking the composer, the
        // same check the id-only path does over the network below.
        val refusal = sessionOpenRefusal(
            meta = session,
            isMissing = false,
            directoryMatches = session.location?.directory?.let { sameDirectory(it, workspace()) },
        )
        if (refusal != null) {
            refuseSessionOpen(refusal, session.id)
            return
        }
        // Read-only sync: the session's own model and agent become the
        // active ones, so the header stops showing the previous session's
        // model. selectModel()/selectAgent() are NOT called — they write to
        // the engine and could clobber the session's real model/agent.
        // NOTE: agent sync reads SessionInfo.agent, which the engine returns
        // in Session.Info.agent (docs/API-REFERENCE.md) — no new endpoint.
        // A null model/agent clears the chip (item 23): keeping the previous
        // session's value would show a model this session never runs on.
        _state.value = _state.value.copy(
            activeModel = session.model,
            activeAgent = session.agent,
        )
        // An explicit open supersedes a deep link still waiting for
        // attach (56-F1).
        if (repository != null) pendingOpenSessionID = null
        openSessionById(session.id)
    }

    /** Loads an existing session by id, from the history list. */
    fun openSession(sessionID: String) {
        // Cold start (56-F1): the repository does not exist yet, so the
        // id waits for [attachIfNeeded] instead of dying on `?: return`.
        // Opening the session already open stores nothing.
        if (repository == null) {
            if (sessionID != _state.value.sessionID) pendingOpenSessionID = sessionID
            return
        }
        pendingOpenSessionID = null
        openSessionById(sessionID, fetchMeta = true)
    }

    private fun openSessionById(sessionID: String, fetchMeta: Boolean = false) {
        // Tapping the session that is already open is a no-op: repo.open()
        // itself no-ops for the same session, so re-opening it would only
        // clear the draft, queue, attachments and edit in progress for
        // nothing (audit-70 13-F6). onResume restores via repo.open()
        // directly and is unaffected by this guard.
        if (sessionID == _state.value.sessionID) return
        val repo = repository ?: return
        if (fetchMeta) {
            // 56-F2/56-F6: the id-only path (deep link, widget, notification,
            // pending open — and the session list, whose rows carry only an
            // id) must prove the target exists and belongs to the active
            // workspace BEFORE parking the current composer and switching
            // state. The old code parked first and asked afterwards: a stale
            // link evicted the user from their working session, and the
            // failed id poisoned lastSessionID for the next launch.
            // The GET is the same one the meta sync below used to make
            // after opening — moved earlier and reused, so this adds no
            // extra request.
            val h = handle
            if (h == null) {
                // No handle yet: cannot verify. Fall through to the old
                // path — the resync banner still reports a gone session.
                openSessionUnchecked(sessionID, repo, meta = null)
                return
            }
            val attachedAtRequest = repo.state.value.sessionID
            viewModelScope.launch {
                when (val result = h.sessions.get(sessionID)) {
                    is ApiResult.Success -> {
                        val refusal = sessionOpenRefusal(
                            meta = result.value,
                            isMissing = false,
                            directoryMatches = result.value.location?.directory?.let {
                                sameDirectory(it, workspace())
                            },
                        )
                        if (!stillOn(attachedAtRequest, repo)) return@launch
                        if (refusal != null) {
                            refuseSessionOpen(refusal, sessionID)
                        } else {
                            openSessionUnchecked(sessionID, repo, meta = result.value)
                        }
                    }
                    is ApiResult.Failure -> {
                        if (!stillOn(attachedAtRequest, repo)) return@launch
                        val refusal = sessionOpenRefusal(
                            meta = null,
                            isMissing = result.isSessionMissing,
                            directoryMatches = null,
                        )
                        if (refusal != null) {
                            refuseSessionOpen(refusal, sessionID)
                        } else {
                            // A transient failure (engine mid-restart, a
                            // dropped connection) must not block a deep
                            // link: open unchecked and let the resync
                            // banner report the session if it is really gone.
                            openSessionUnchecked(sessionID, repo, meta = null)
                        }
                    }
                }
            }
            return
        }
        openSessionUnchecked(sessionID, repo, meta = null)
    }

    /**
     * The user has not switched sessions since the pre-check started
     * (56-F2/56-F6): the attached session is still the one the check
     * started from, on the same repository. A late GET result must never
     * clobber an explicit choice the user made while it was in flight.
     */
    private fun stillOn(attachedAtRequest: String?, repo: ChatRepository): Boolean =
        repository === repo && repo.state.value.sessionID == attachedAtRequest

    /**
     * Refuses a deep-link / list open without touching the current session
     * (56-F2/56-F6): no parked composer, no cleared draft, no poisoned
     * lastSessionID — the banner says why, on the session the user is
     * still looking at. Also clears a pending deep-link open for this id:
     * the pending id is only confirmed by onRepoState when the repository
     * installs it, so a refused id would otherwise stall onResume forever.
     */
    private fun refuseSessionOpen(@StringRes failureKey: Int, sessionID: String) {
        if (pendingOpenSessionID == sessionID) pendingOpenSessionID = null
        _state.value = _state.value.copy(failureKey = failureKey, retryKind = RetryKind.NONE)
    }

    /**
     * The switch itself, after the target has been proven (or when the
     * caller already holds its info). Parks the outgoing composer, restores
     * the incoming one, and opens the repository. [meta], when present,
     * syncs the session's model/agent without a second GET — read-only,
     * never written back to the engine. A failed read is best-effort:
     * chat still opens on the previous selection.
     */
    private fun openSessionUnchecked(sessionID: String, repo: ChatRepository, meta: SessionInfo?) {
        sessionReloadAttempts = 0
        // A draft belongs to the session it was typed in; opening another
        // session starts with that session's own composer — the outgoing
        // session's draft and attachments are parked under its id, and the
        // incoming session's parked composer (if any) is restored, so
        // coming back finds the text still there (audit-70 20-F4). No retry
        // target from the previous session: the refusal notice goes with it.
        parkComposer()
        // Same as newSession (draftBeforeEdit): the pre-edit backup belongs
        // to the old session's composer and must not survive the switch.
        draftBeforeEdit = null
        val restored = takeParkedComposer(sessionID)
        // Audit-200 A5 (DL-008): a parked composer that cannot be decoded
        // is reported instead of silently coming back empty.
        reportUnreadableParked(sessionID)
        // Audit-200 A5 (DL-030): the visible draft changed under any
        // in-flight send, so its post-send clear must not fire on the
        // restored text.
        draftEditCount++
        _state.value = _state.value.copy(draft = restored?.draft ?: "", pendingAttachments = restored?.attachments ?: emptyList(), attachNoticeKey = null, attachNoticeArg = null, lastSentText = null, sentPrompts = emptyList(), queuedPrompts = restored?.queued ?: emptyList(), editingMessageId = null, pendingPermission = null, deferredPermission = null, retryKind = RetryKind.NONE, selectionFailure = null, stagedRevert = null, forking = false, compacting = false, revertBusy = false)
        if (restored?.draft.isNullOrBlank()) {
            savedStateHandle?.remove<String>(DRAFT_KEY)
        } else {
            savedStateHandle?.set(DRAFT_KEY, restored.draft)
        }
        repo.open(sessionID)
        // Guarded by the repository's attached session (item 23), not the
        // UI state's: opening A then B fast must not let A's late GET
        // overwrite B's chip. The repository sets its session id
        // synchronously in open(), so this holds even before the
        // collector's first emission.
        if (meta != null && repository?.state?.value?.sessionID == sessionID) {
            _state.value = _state.value.copy(
                activeModel = meta.model,
                activeAgent = meta.agent,
                // A revert staged earlier (on this device or another client)
                // is still staged: surface it in the banner instead of
                // hiding it until the user happens to stage another one.
                stagedRevert = meta.revert,
            )
        }
    }

    /**
     * Called when the screen appears.
     *
     * If the engine is already running and the user has an open session, reload its
     * history: the process survives a task swipe, so returning to the app should
     * show the conversation that is still there rather than an empty screen.
     *
     * The remembered id is verified first (item 14): the session must belong
     * to the active workspace, or a workspace switch would resurrect the old
     * project's session under the new project's header and prompts would land
     * in the wrong project. A session the engine no longer has is skipped
     * silently — an empty chat beats an error banner for a session that is
     * simply gone. A null location cannot be verified (older engines), so it
     * is allowed through rather than breaking resume.
     */
    fun onResume(lastSessionID: String?) {
        // A deep link waiting for attach (56-F1) wins over the remembered
        // session: resuming the old session here would open it first and
        // the pending open would have to fight it afterwards.
        if (pendingOpenSessionID != null) return
        val repo = repository ?: return
        val target = lastSessionID ?: return
        if (_state.value.messages.isNotEmpty()) return
        val h = handle ?: return
        viewModelScope.launch {
            when (val result = h.sessions.get(target)) {
                is ApiResult.Success -> {
                    val dir = result.value.location?.directory
                    if (dir == null || sameDirectory(dir, workspace())) {
                        // 58-F7: the user may have opened another session
                        // while this GET was in flight — opening the
                        // remembered one now would clobber their choice.
                        // Same post-await guard as openSessionById (item
                        // 23), on the repository's attached session: the
                        // repository sets it synchronously in open(), so
                        // it holds even before the collector's first
                        // emission.
                        if (shouldOpenAfterResume(
                                attachedSessionID = repository?.state?.value?.sessionID,
                                targetSessionID = target,
                                repositoryChanged = repository !== repo,
                            )
                        ) {
                            repo.open(target)
                        }
                    }
                }
                is ApiResult.Failure -> Unit
            }
        }
    }

    /** Same directory, allowing for symlinked storage roots. */
    private fun sameDirectory(a: String, b: String): Boolean {
        if (a == b) return true
        return runCatching {
            java.io.File(a).canonicalPath == java.io.File(b).canonicalPath
        }.getOrDefault(false)
    }

    /**
     * The stored workspace was unusable, so the app fell back to the
     * internal one (item 12). A banner says so: silently working in a
     * different project would strand the user's files where they cannot
     * find them.
     */
    fun reportWorkspaceUnavailable() {
        _state.value = _state.value.copy(failureKey = R.string.workspace_unavailable)
    }

    /** Remembers the session id so a relaunch can restore it. */
    fun currentSessionID(): String? = _state.value.sessionID

    fun dismissError() {
        _state.value = _state.value.copy(failure = null, failureKey = null, retryKind = RetryKind.NONE, selectionFailure = null)
    }

    override fun onCleared() {
        collector?.cancel()
        repository?.close()
        super.onCleared()
    }

    companion object {
        private const val DRAFT_KEY = "chat_draft"
        /** Queued follow-ups, so they survive process death (audit-70 69-F2). */
        private const val QUEUE_KEY = "chat_queue"
        /** Pending attachment chips, same reason as [QUEUE_KEY]. */
        private const val ATTACHMENTS_KEY = "chat_attachments"
        /** Retry target, so Retry still works after a restore (audit-70 69-F2). */
        private const val LAST_SENT_KEY = "chat_last_sent"
        /** Prompt history (Up navigation), same reason as [LAST_SENT_KEY]. */
        private const val SENT_PROMPTS_KEY = "chat_sent_prompts"
        /** Failed reloads before the banner points at a new session (item 24). */
        private const val SESSION_RELOAD_LIMIT = 2
        /**
         * How long an interrupt POST may hang before Stop is released for
         * another tap (17-F10): the client's 120s call timeout left
         * "Stopping…" stuck for up to two minutes on a hung engine, with
         * no retry. Fifteen seconds is long enough for a healthy engine
         * and short enough to notice.
         */
        internal const val STOP_TIMEOUT_MS = 15_000L
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
/**
 * History parts, streaming overlay and live tools — the expensive part
 * (item 28): every message's parts are parsed here, so the incremental
 * path in [ChatViewModel.buildUiMessages] reuses this list and only
 * re-runs the streaming overlay when nothing else moved.
 */
private fun ChatState.buildUnmergedMessages(
    toolDurations: Map<String, Long> = emptyMap(),
    toolStartedAt: Map<String, Long> = emptyMap(),
): List<ChatMessage> {
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
            // The turn keeps its footer (66-F3): a run that died after burning
            // input still spent those tokens, so the error bubble shows them
            // instead of hiding the cost of the failure. The text itself stays
            // blank here when the engine gave no message and no type — the
            // bubble renders the localised "The agent failed" (54-F7) instead
            // of a hardcoded English sentence, because this mapping has no
            // Context to resolve a string with.
            message.isAssistant && message.error != null && message.content.isEmpty() ->
                turnFooterValues(message).let { footer ->
                    result += ChatMessage(
                        id = message.id,
                        role = Role.ERROR,
                        text = message.error?.message ?: message.error?.type ?: "",
                        agent = message.agent,
                        model = message.model,
                        createdAtMillis = message.time?.created,
                        usageTokens = footer.usageTokens,
                        usageCostUsd = footer.usageCostUsd,
                        turnDurationMs = footer.turnDurationMs,
                        stopReason = footer.stopReason,
                        thinkingSeconds = footer.thinkingSeconds,
                    )
                }
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

                val footer = turnFooterValues(message)
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
                    // Turn footer from the engine DTO (finding 6): shared
                    // with the failed-turn branch (66-F3) so both render
                    // the same numbers.
                    usageTokens = footer.usageTokens,
                    usageCostUsd = footer.usageCostUsd,
                    turnDurationMs = footer.turnDurationMs,
                    stopReason = footer.stopReason,
                    thinkingSeconds = footer.thinkingSeconds,
                    toolDurations = toolDurations,
                    toolStartedAtMillis = toolStartedAt,
                    // A failed run with partial content (01-F4): the error
                    // used to be dropped here — the branch above only
                    // handles the empty-content case — so the partial
                    // answer rendered as a complete one.
                    errorText = message.error?.message ?: message.error?.type,
                )
            }
            // `idle` markers are engine bookkeeping, not conversation.
        }
    }

    applyStreamingOverlay(result, streaming)

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
                toolDurations = toolDurations,
                toolStartedAtMillis = toolStartedAt,
            )
        } else {
            val curr = result[idx]
            val mergedTools = curr.tools.filter { it.id !in liveIds } + liveCalls
            // Rebuild the trace from the merged tools (item 20): keeping
            // curr.parts as-is froze the live trace — a tool that arrived
            // after the first streamed sentence never appeared until the
            // end-of-run resync. The merge is by id, last-wins, so
            // re-running it on every rebuild is a no-op. mergedTools (not
            // just the live calls) is passed so a turn whose parts are
            // empty but whose tools came from history keeps them too.
            val parts = mergeLiveToolsIntoParts(curr.parts, mergedTools, curr.streaming)
            result[idx] = curr.copy(tools = mergedTools, parts = parts)
        }
    }
    return result
}

/**
 * Folds streamed text into the built messages (in place): a message in
 * history gets its text replaced, a message not yet in history is
 * appended. Replacing (not appending) makes re-running the overlay on
 * the same list a no-op for text that did not move — the incremental
 * path relies on that.
 */
private fun applyStreamingOverlay(result: MutableList<ChatMessage>, streaming: Map<String, String>) {
    streaming.forEach { (messageID, text) ->
        val existing = result.indexOfFirst { it.id == messageID }
        if (existing >= 0) {
            val curr = result[existing]
            val updatedParts = if (curr.parts.isNotEmpty()) {
                val copy = curr.parts.toMutableList()
                val last = copy.lastOrNull()
                if (last is ChatTurnPart.Text) {
                    // 01-F5: the streaming text joins every text block of
                    // the turn, so replacing the trailing block with the
                    // whole stream repeats the earlier blocks when more
                    // than one text part exists. Only the part past the
                    // earlier blocks is new for this block.
                    val shownBeforeLast = copy.dropLast(1)
                        .filterIsInstance<ChatTurnPart.Text>()
                        .joinToString("\n\n") { it.markdown }
                    copy[copy.lastIndex] = ChatTurnPart.Text(
                        streamingTextSuffix(text, shownBeforeLast),
                    )
                } else {
                    // 01-F5: same root, other branch — the text before the
                    // tool is already rendered as an earlier Text part, so
                    // appending the whole stream repeats it. Append only
                    // the suffix past what is shown; nothing is appended
                    // when the stream holds nothing new.
                    val shown = copy.filterIsInstance<ChatTurnPart.Text>()
                        .joinToString("\n\n") { it.markdown }
                    val suffix = streamingTextSuffix(text, shown)
                    if (suffix.isNotBlank()) copy += ChatTurnPart.Text(suffix)
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
}

/** Merges contiguous assistant messages into one unified turn. */
private fun mergeContiguousAssistant(messages: List<ChatMessage>): List<ChatMessage> {
    // Merge contiguous assistant messages into one unified turn
    val merged = mutableListOf<ChatMessage>()
    messages.forEach { msg ->
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
                toolStartedAtMillis = last.toolStartedAtMillis + msg.toolStartedAtMillis,
                // A failed run's error (01-F4) survives the merge: the
                // later chunk is the one that carries it when the error
                // lands after the partial text.
                errorText = msg.errorText ?: last.errorText,
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

/**
 * Turn-footer token total: input + output + reasoning.
 *
 * Reasoning is its own bucket in this engine's accounting — a real payload
 * reports reasoning 83 against output 9 — so leaving it out undercounts
 * the turn, the way the footer did before this fix. Cache reads sit inside
 * input and are never added on top. Null when the engine reported no usage
 * at all; a reported zero stays zero, matching the footer's old behaviour
 * for an all-zero turn.
 */
internal fun turnUsageTokens(tokens: TokenUsage?): Long? {
    if (tokens == null) return null
    return listOfNotNull(tokens.input, tokens.output, tokens.reasoning).sum()
        .takeIf { tokens.input != null || tokens.output != null || tokens.reasoning != null }
}

/**
 * Turn footer values (Phase 2, finding 6), from one engine message.
 *
 * One function for both the assistant branch and the failed-turn branch
 * (audit-100 Fase 6b, 66-F3): the error branch used to build its bubble
 * without any of these, so a turn that died after burning input showed
 * no tokens, no cost, no duration and no stop reason — the numbers were
 * in the DTO all along. Duration is `completed - created`; thinking time
 * is `streamed - created` while reasoning is present, rounded to the
 * nearest second (66-F8). Any missing timestamp leaves its field null
 * rather than a guessed value.
 */
internal data class TurnFooterValues(
    val usageTokens: Long?,
    val usageCostUsd: Double?,
    val turnDurationMs: Long?,
    val stopReason: String?,
    val thinkingSeconds: Int?,
)

internal fun turnFooterValues(message: SessionMessage): TurnFooterValues {
    // Local copies: time.* are public API properties from another module,
    // so smart casts on them are impossible.
    val time = message.time
    val created = time?.created
    val completed = time?.completed
    val streamed = time?.streamed
    return TurnFooterValues(
        usageTokens = turnUsageTokens(message.tokens),
        usageCostUsd = message.cost,
        turnDurationMs = if (created != null && completed != null && completed >= created) {
            completed - created
        } else {
            null
        },
        stopReason = message.finish?.takeIf { it.isNotBlank() },
        thinkingSeconds = if (!message.reasoningText.isNullOrBlank() &&
            created != null && streamed != null && streamed >= created
        ) {
            (((streamed - created) + 500) / 1000).toInt()
        } else {
            null
        },
    )
}

/**
 * Context-meter usage (Tier 2): the LAST assistant message in a context
 * listing that reported usage, and the model that produced it.
 *
 * Each assistant message's `input` is the whole prompt that call sent, so
 * summing over all messages counts the same history once per turn — the
 * meter would hit 100% on a healthy session (audit-100 Fase 6a, 23-F1).
 * The next turn builds on the last call's prompt, so the last reported
 * usage is the honest occupancy. Messages without usage are skipped
 * backwards; `compaction` summaries are not assistant messages and are
 * skipped too (their tokens' meaning is unproven — 77-F4 — so they are
 * neither counted nor treated as the last turn).
 */
internal data class ContextUsage(val tokens: Long?, val model: ModelRef?)

internal fun contextUsage(messages: List<SessionMessage>): ContextUsage {
    for (message in messages.asReversed()) {
        if (!message.isAssistant) continue
        val tokens = turnUsageTokens(message.tokens) ?: continue
        return ContextUsage(tokens, message.model)
    }
    return ContextUsage(null, null)
}

/**
 * Context-meter total (Tier 2): [contextUsage] for callers that only need
 * the number. User, system and idle markers carry no usage, so they
 * contribute nothing; null when no assistant message reported usage at
 * all, and a reported zero stays zero.
 */
internal fun contextUsageTokens(messages: List<SessionMessage>): Long? =
    contextUsage(messages).tokens

/** Minimum gap between context-meter refreshes while a run is in progress (23-F3). */
private const val CONTEXT_REFRESH_BUSY_INTERVAL_MS = 30_000L

/** Pure throttle decision for the busy-run meter refresh: due once the gap has passed. */
internal fun contextRefreshDue(nowMs: Long, lastMs: Long, intervalMs: Long = CONTEXT_REFRESH_BUSY_INTERVAL_MS): Boolean =
    nowMs - lastMs >= intervalMs
