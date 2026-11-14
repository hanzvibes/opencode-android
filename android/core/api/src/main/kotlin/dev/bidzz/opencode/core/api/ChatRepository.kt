package dev.bidzz.opencode.core.api

import android.util.Log
import dev.bidzz.opencode.core.api.dto.CacheUsage
import dev.bidzz.opencode.core.api.dto.EngineEvent
import dev.bidzz.opencode.core.api.dto.EventTypes
import dev.bidzz.opencode.core.api.dto.MessageContent
import dev.bidzz.opencode.core.api.dto.MessageTime
import dev.bidzz.opencode.core.api.dto.ModelRef
import dev.bidzz.opencode.core.api.dto.TokenUsage
import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.core.api.dto.toolOutput
import dev.bidzz.opencode.core.api.dto.PendingPermission
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.api.dto.TextDelta
import dev.bidzz.opencode.core.api.dto.TextEnded
import dev.bidzz.opencode.core.api.dto.TextStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.serializer
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What a chat screen needs, derived from REST plus the event stream.
 *
 * The split matters: REST is the source of truth for completed history, the stream
 * is only a live tail. Because the stream drops events while disconnected, every
 * reconnect re-reads history and rebuilds from it rather than trying to patch the
 * gap.
 */
data class ChatState(
    val sessionID: String? = null,
    /** Completed and in-flight messages, oldest first. */
    val messages: List<SessionMessage> = emptyList(),
    /** Text of the assistant message currently streaming, keyed by message id. */
    val streaming: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
    /** What went wrong, as a value: the screen words it in the app's language. */
    val lastFailure: ApiResult.Failure? = null,
    /**
     * True when [lastFailure] came from a failed resync (03-F2), not from a
     * failed send: the prompt already ran, so the banner's Retry must
     * re-open the session (re-fetching the finished answer) instead of
     * re-sending the prompt. Cleared wherever [lastFailure] is set from any
     * other origin or cleared.
     */
    val resyncFailed: Boolean = false,
    /** Active permission request waiting for the operator to reply. */
    val pendingPermission: PendingPermission? = null,
    /** Tool calls seen on the stream this run, keyed by call id. Cleared on resync. */
    val liveTools: Map<String, LiveTool> = emptyMap(),
    /** True when the transcript window cut older messages: more are available above. */
    val hasMoreOlder: Boolean = false,
    /** True while an explicit "load earlier" fetch is in flight. */
    val loadingEarlier: Boolean = false,
)

/** A tool call from the live stream, tied to the assistant message that owns it. */
data class LiveTool(val messageID: String, val call: ToolCall)

/**
 * Sums two token usages bucket by bucket (Fase 2, 01-F3).
 *
 * The engine reports tokens per assistant chunk, and [ChatRepository] merges
 * contiguous chunks into one message — but the merge kept only the last
 * chunk's tokens while summing the cost, so the footer under-reported tokens
 * for every multi-chunk run. A bucket is null only when both sides report
 * nothing for it; cache read/write are subsets of input, summed like the
 * rest, and the caller's total (which adds input + output + reasoning, never
 * cache) still cannot double-count.
 */
internal fun mergeTokenUsage(a: TokenUsage?, b: TokenUsage?): TokenUsage? {
    if (a == null) return b
    if (b == null) return a
    return TokenUsage(
        input = sumOrNull(a.input, b.input),
        output = sumOrNull(a.output, b.output),
        reasoning = sumOrNull(a.reasoning, b.reasoning),
        cache = mergeCacheUsage(a.cache, b.cache),
    )
}

internal fun mergeCacheUsage(a: CacheUsage?, b: CacheUsage?): CacheUsage? {
    if (a == null) return b
    if (b == null) return a
    return CacheUsage(
        read = sumOrNull(a.read, b.read),
        write = sumOrNull(a.write, b.write),
    )
}

private fun sumOrNull(a: Long?, b: Long?): Long? =
    if (a == null && b == null) null else (a ?: 0L) + (b ?: 0L)

/**
 * Merges two message timestamps (Fase 2, 01-F3).
 *
 * The merge used to keep the first chunk's time untouched, so a merged
 * message never showed when its run actually finished. Created comes from
 * the earlier chunk, completed from the later one, streamed is the latest
 * of the two — the newest progress either chunk reported.
 */
internal fun mergeMessageTime(a: MessageTime?, b: MessageTime?): MessageTime? {
    if (a == null) return b
    if (b == null) return a
    return MessageTime(
        created = a.created ?: b.created,
        streamed = listOfNotNull(a.streamed, b.streamed).maxOrNull(),
        completed = b.completed ?: a.completed,
    )
}

/**
 * Message ids whose streaming buffers survive a resync (Fase 2, 03-F1).
 *
 * [chronological] is the history just fetched, oldest first. Live are the
 * trailing assistant messages of the current run that have no completed
 * time — the run's own tail — plus any id with a streaming entry that has
 * no history row at all (the engine had not persisted it yet). A finished
 * message is always the newest row, so it never qualifies through either
 * arm, and a run that ended leaves this set empty.
 */
internal fun liveStreamingIds(
    chronological: List<SessionMessage>,
    streamingIds: Set<String>,
): Set<String> {
    val live = mutableSetOf<String>()
    for (msg in chronological.asReversed()) {
        if (!msg.isAssistant || msg.time?.completed != null) break
        live += msg.id
    }
    val historyIds = chronological.map { it.id }.toSet()
    live += streamingIds.filter { it !in historyIds }
    return live
}

/**
 * Whether a permission request the engine still lists may be restored by a
 * resync (58-F5).
 *
 * A request that is being answered right now, or was answered moments ago,
 * must not come back: the engine can keep listing it until it processes
 * the reply, and restoring it shows a dead prompt that fails again when
 * answered. The suppression window is deliberately short — a genuinely new
 * ask carries a new id, so only a stale listing of the just-answered one
 * is hidden, and only briefly.
 */
internal fun shouldRestorePermission(
    requestId: String,
    replyingPermissionId: String?,
    lastAnsweredPermissionId: String?,
    lastAnsweredPermissionAtMs: Long,
    nowMs: Long,
    windowMs: Long = ANSWERED_PERMISSION_SUPPRESS_MS,
): Boolean {
    if (requestId == replyingPermissionId) return false
    if (requestId == lastAnsweredPermissionId && nowMs - lastAnsweredPermissionAtMs < windowMs) return false
    return true
}

/** How long a just-answered permission id stays suppressed from resync restore (58-F5). */
internal const val ANSWERED_PERMISSION_SUPPRESS_MS = 60_000L

/**
 * Permission-restore memory shared across [ChatRepository] instances
 * (audit-200 B3, M-547/M-081).
 *
 * The repository is rebuilt on every attach, reload and new session, but
 * the answers live in the view model that outlives it: a permission
 * answered moments ago must stay suppressed when the next repository
 * resyncs (the engine can keep listing it until it processes the reply),
 * and a permission left over from a run that ended — stopped or finished
 * — must not come back until that session sends again (the run is over,
 * so the ask is dead; restoring it shows a prompt whose reply can only
 * fail). Both live here, owned by the view model and handed to every
 * repository it builds; a repository built without one (unit tests) gets
 * a fresh, empty gate. The suppression is per session: stopping session
 * A must not hide a live ask in session B.
 */
class PermissionRestoreGate {
    var lastAnsweredId: String? = null
    var lastAnsweredAtMs: Long = 0L
    var lastAnsweredSessionID: String? = null
    /** Session whose dead-run asks must not be restored until it sends again. */
    var suppressedSessionID: String? = null
}

/**
 * Owns one session's conversation state.
 *
 * Streaming text is accumulated per assistant message id, not per "last message":
 * a single response can contain several text blocks (distinct `ordinal`), and
 * appending blindly would interleave them.
 */
class ChatRepository(
    private val sessionApi: SessionApi,
    private val eventStream: EventStream,
    private val scope: CoroutineScope,
    private val permissionGate: PermissionRestoreGate = PermissionRestoreGate(),
) {
    companion object {
        private const val TAG = "ChatRepository"

        /** Minimum gap between streaming emissions; deltas inside the window coalesce (item 28). */
        private const val PUBLISH_THROTTLE_MS = 100L

        /** First transcript window: newest N messages; older ones load on demand (item 28). */
        private const val INITIAL_MESSAGE_LIMIT = 100

        /** Each "load earlier" grows the window by this many messages. */
        private const val MESSAGE_PAGE = 100

        /**
         * Terminal resync retries (03-F2): the finished answer only reaches
         * the transcript through that resync, so one transient failure hid
         * it until the next run. Three attempts, 1s then 2s apart.
         */
        private const val TERMINAL_RESYNC_ATTEMPTS = 3
        private val TERMINAL_RESYNC_DELAYS_MS = longArrayOf(1_000L, 2_000L)
    }

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var sessionID: String? = null
    private var streamJob: Job? = null

    /** Transcript window for resync: grows with "load earlier", resets on open (item 28). */
    private var messageLimit = INITIAL_MESSAGE_LIMIT

    /** Last streaming emission, for the publish throttle (item 28). */
    private var lastPublishAtMs = 0L

    /** Coalesced flush for publishes skipped by the throttle. */
    private var publishFlushJob: Job? = null

    /** Request id with a reply in flight: a double-tap (or a tap after a
     * rotation re-arms the buttons) must not answer twice (item 22). */
    private var replyingPermissionId: String? = null

    /**
     * Counts the runs this repository has started (audit-200 B3, M-375):
     * the terminal resync's retry loop must stop touching state the
     * moment a new run starts, or a late retry overwrites the new run's
     * streaming entries, live tools and failure with the old run's.
     * Bumped in [send] on success; read by [resyncAfterTerminal] and
     * [resync].
     */
    private var runGeneration = 0

    /**
     * True after a terminal run event until the next run starts
     * (audit-200 B3, M-558): any text event after this point is stale —
     * the finished answer already arrived through the terminal resync,
     * so a late delta must not resurrect a streaming entry over it.
     * Cleared in [send] and on EXECUTION_STARTED, and on [open].
     */
    private var runEnded = false

    /**
     * Text received per (assistant message id, block ordinal), for the message
     * being streamed. One assistant message carries several text blocks — a
     * reply before a tool call and one after it, for example — and keying by
     * id alone mixes them: a delta for block 1 lands in block 0's text, and
     * TEXT_ENDED for block 1 overwrites block 0 entirely (item 25).
     */
    private val buffers = mutableMapOf<Pair<String, Int>, StringBuilder>()

    /** Prompt text by message id, for user messages the engine returns without text. */
    private val sentText = mutableMapOf<String, String>()

    /**
     * Attaches to [sessionID]: loads history, then follows the stream.
     *
     * Re-entrant: calling it again for the same session is a no-op, so a UI that
     * recreates itself on rotation does not open a second stream.
     */
    fun open(sessionID: String) {
        if (this.sessionID == sessionID && streamJob?.isActive == true) return
        close()
        runEnded = false
        this.sessionID = sessionID
        // A fresh open starts at the default window (item 28): earlier
        // messages are one tap away, not fetched up front every time.
        messageLimit = INITIAL_MESSAGE_LIMIT
        _state.value = ChatState(sessionID = sessionID)

        streamJob = scope.launch {
            resync()
            try {
                eventStream.events(onReconnect = { resync() }).collect { event ->
                    if (event.type == EventTypes.SERVER_CONNECTED) return@collect
                    if (event.sessionIDOf() != sessionID) return@collect
                    handle(event)
                }
            } catch (e: EventStreamAuthException) {
                // Uncaught, this would take down the whole scope instead of one chat.
                _state.value = _state.value.copy(
                    lastFailure = ApiResult.Failure(
                        code = e.code,
                        tag = "LostConnection",
                        message = null,
                    ),
                    resyncFailed = false,
                )
            }
        }
    }

    fun close() {
        streamJob?.cancel()
        streamJob = null
        publishFlushJob?.cancel()
        publishFlushJob = null
        sessionID = null
        buffers.clear()
    }

    /**
     * Grows the transcript window by one page and re-fetches (item 28).
     * The engine's message endpoint only takes a limit, so paging grows
     * the window instead of cursoring: each load re-reads a bigger newest
     * slice. Loads are user-initiated and rare; the window resets on open.
     */
    fun loadEarlier() {
        val id = sessionID ?: return
        if (_state.value.loadingEarlier || !_state.value.hasMoreOlder) return
        messageLimit += MESSAGE_PAGE
        _state.value = _state.value.copy(loadingEarlier = true)
        scope.launch {
            // Pinned to the session the tap was for (audit-200 B3,
            // M-490): an unpinned resync would adopt whatever session is
            // current when it runs.
            resync(forSessionID = id)
            if (sessionID == id) _state.value = _state.value.copy(loadingEarlier = false)
        }
    }

    private fun isAssistantContinuation(current: SessionMessage, prev: SessionMessage): Boolean {
        // Any consecutive assistant messages between user turns belong to the same agent run
        return current.isAssistant && prev.isAssistant
    }

    private fun mergeAssistant(target: SessionMessage, addition: SessionMessage): SessionMessage {
        return target.copy(
            content = target.content + addition.content,
            finish = addition.finish ?: target.finish,
            cost = (target.cost ?: 0.0) + (addition.cost ?: 0.0),
            tokens = mergeTokenUsage(target.tokens, addition.tokens),
            time = mergeMessageTime(target.time, addition.time),
            error = addition.error ?: target.error,
            model = addition.model ?: target.model,
            agent = addition.agent ?: target.agent,
        )
    }

    /**
     * Rebuilds from REST. Called on open and after every reconnect.
     *
     * @param fromTerminal true when this resync follows a terminal run
     * event (03-F2/01-F4): the finished answer only arrives through it,
     * and a failed run's error is only visible in the history it returns.
     */
    /** What one [resync] call did — the terminal retry loop decides on this, not on the state it left behind (see [resyncAfterTerminal]). */
    private enum class ResyncOutcome { SUCCEEDED, FAILED, SKIPPED }

    private suspend fun resync(
        fromTerminal: Boolean = false,
        forSessionID: String? = null,
        terminalGeneration: Int? = null,
    ): ResyncOutcome {
        val id = forSessionID ?: sessionID ?: return ResyncOutcome.SKIPPED
        // Pinned to another session (audit-200 B3, M-490): the switch
        // already happened — do not even fetch for the abandoned one.
        if (sessionID != id) return ResyncOutcome.SKIPPED
        // A new run started since this terminal resync was launched
        // (audit-200 B3, M-375): its state is not this resync's to write.
        if (terminalGeneration != null && terminalGeneration != runGeneration) {
            return ResyncOutcome.SKIPPED
        }
        when (val result = sessionApi.messages(id, limit = messageLimit)) {
            is ApiResult.Success -> {
                // The session may have changed while the call was in flight:
                // the terminal-event resync is launched on the repository
                // scope, not the stream job, so close() does not cancel it.
                // An old transcript must never overwrite the new session's
                // (item 26) — and neither must its failure or permission.
                if (sessionID != id) return ResyncOutcome.SKIPPED
                if (terminalGeneration != null && terminalGeneration != runGeneration) {
                    return ResyncOutcome.SKIPPED
                }
                // The engine returns history newest-first (idle, assistant, user...).
                // Reversing here means the UI and every consumer can assume
                // oldest-first, which is the only order a chat transcript can use.
                val rawChronological = result.value.reversed().map { m ->
                    // A user entry that comes back without text keeps what we sent.
                    if (m.isUser && m.text.isNullOrBlank()) sentText[m.id]?.let { m.copy(text = it) } ?: m else m
                }

                // Merge contiguous assistant chunks (tools + response) into one cohesive assistant message
                val chronological = mutableListOf<SessionMessage>()
                for (msg in rawChronological) {
                    val last = chronological.lastOrNull()
                    if (last != null && isAssistantContinuation(msg, last)) {
                        chronological[chronological.lastIndex] = mergeAssistant(last, msg)
                    } else {
                        chronological += msg
                    }
                }

                val newest = result.value.firstOrNull()
                // A resync mid-run must not wipe the streaming text (Fase 2,
                // 03-F1): the success path used to clear every buffer and
                // every streaming entry, so the in-flight answer collapsed
                // to the last delta until TEXT_ENDED. Keep the buffers of
                // the messages that are still streaming — the trailing
                // assistant messages without a completed time — and drop
                // the rest: their text is in the history now. A message
                // with a streaming entry but no history row yet (the
                // engine had not persisted it when this call ran) is
                // still streaming by definition, so it is kept too; a
                // finished message is always the newest row, so it can
                // never hide in that set. When the run is over the live
                // set is empty and this is the old full clear.
                val liveIds = liveStreamingIds(rawChronological, _state.value.streaming.keys)
                // Audit-200 B3 (M-044): the resync keeps the buffers of
                // messages still streaming — but history may already hold
                // MORE text for one of them (deltas missed during a
                // disconnect). The overlay prefers streaming text, so a
                // shorter buffer would shadow the newer history until the
                // next delta. When history is longer, it wins: drop the
                // streaming entry and its buffers, and the transcript
                // shows the persisted text. The comparison is
                // deliberately one-sided — the buffer joins blocks with
                // blank lines, so it can look longer than history while
                // holding less; only a clearly longer history drops it.
                val historyById = rawChronological.associateBy { it.id }
                val staleIds = liveIds.filter { liveId ->
                    val historyText = historyById[liveId]?.plainText.orEmpty()
                    historyText.length > streamedText(liveId).length
                }.toSet()
                buffers.keys.retainAll { (id, _) -> id in liveIds && id !in staleIds }
                // A run that died after partial content (01-F4): the error
                // lives on the newest assistant message, and the old
                // unconditional clear hid the failure — the partial answer
                // looked complete. Only a terminal resync names it: on an
                // open or a reconnect the same message is an old failure,
                // not this run's, and the turn itself still carries the
                // error marker (see ChatViewModel). The banner offers no
                // Retry for it (code -1 is not transient): re-sending is
                // the operator's call, not the banner's.
                val terminalFailure = if (fromTerminal) {
                    chronological.asReversed().firstOrNull { it.isAssistant }?.error?.let { error ->
                        ApiResult.Failure(code = -1, tag = error.type, message = error.message)
                    }
                } else {
                    null
                }
                _state.value = _state.value.copy(
                    messages = chronological,
                    streaming = _state.value.streaming.filterKeys { it in liveIds && it !in staleIds },
                    liveTools = emptyMap(),
                    // A run is over when the newest entry is `idle`, or when the
                    // newest assistant message is finished. The second case matters:
                    // a rejected permission aborts the run without ever writing an
                    // `idle` message, so an idle-only check spins "Working" forever.
                    busy = newest != null && !newest.isIdle && newest.time?.completed == null,
                    lastFailure = terminalFailure,
                    resyncFailed = false,
                    // A full page back means the window may have cut older
                    // messages (item 28); a short page means we are at the
                    // beginning. Exactly one full page with nothing older
                    // costs one harmless extra tap.
                    hasMoreOlder = result.value.size >= messageLimit,
                )
                // A permission the engine still waits on survives reconnects
                // and reopens (item 22): the stream only announces new asks,
                // so restore it from REST. Only ever sets — an empty list
                // must not wipe a request that arrived via SSE while this
                // call was in flight, and a failed call leaves the prompt
                // untouched. A request answered just now is not restored
                // (58-F5): the engine can keep listing it until it
                // processes the reply, and bringing it back shows a dead
                // prompt that fails again when answered.
                when (val pending = sessionApi.listPermissions(id)) {
                    is ApiResult.Success -> {
                        if (sessionID != id) return ResyncOutcome.SUCCEEDED
                        if (terminalGeneration != null && terminalGeneration != runGeneration) {
                            return ResyncOutcome.SUCCEEDED
                        }
                        // Audit-200 B3 (M-081): asks from a run that ended
                        // stay unrestored until this session sends again —
                        // the engine still lists them, but the run is over
                        // and no answer can land anymore.
                        if (permissionGate.suppressedSessionID == id) {
                            return ResyncOutcome.SUCCEEDED
                        }
                        // Audit-200 B3 (M-550): the first entry is not
                        // always the restorable one — a just-answered
                        // request at the head used to hide a fresh request
                        // waiting behind it. Restore the first entry that
                        // passes the suppression check instead.
                        val nowMs = System.currentTimeMillis()
                        pending.value.firstOrNull { request ->
                            shouldRestorePermission(
                                requestId = request.id,
                                replyingPermissionId = replyingPermissionId,
                                lastAnsweredPermissionId = permissionGate.lastAnsweredId
                                    .takeIf { permissionGate.lastAnsweredSessionID == id },
                                lastAnsweredPermissionAtMs = permissionGate.lastAnsweredAtMs,
                                nowMs = nowMs,
                            )
                        }?.let { request ->
                            _state.value = _state.value.copy(pendingPermission = request)
                        }
                    }
                    is ApiResult.Failure -> Log.w(TAG, "permission restore failed: ${pending.message}")
                }
                return ResyncOutcome.SUCCEEDED
            }
            is ApiResult.Failure -> {
                if (sessionID != id) return ResyncOutcome.SKIPPED
                if (terminalGeneration != null && terminalGeneration != runGeneration) {
                    return ResyncOutcome.SKIPPED
                }
                Log.w(TAG, "resync failed: ${result.message}")
                // A failed resync must not leave the streaming entry behind:
                // the run is over, no more deltas are coming, and the entry
                // would keep the message's spinner and hide its Copy/footer
                // forever (item 25). The live tools go with it (audit-100
                // Fase 6b, 06-T1): a failed resync used to clear only the
                // streaming entries, so a tool kept showing "Running" with
                // its 1s tick until the next successful resync — the run
                // was over and nothing would ever update it again. The
                // buffers stay — a failed resync on a reconnect mid-run
                // must not lose the text accumulated so far; the next
                // delta re-publishes it in full. Live tools have no such
                // buffer to lose: their status cannot be recovered from
                // text, and the next successful resync refills them from
                // history (or the stream re-delivers them while the run
                // is still going).
                _state.value = _state.value.copy(
                    lastFailure = result,
                    // Marks the failure's origin (03-F2): the prompt already
                    // ran, so Retry must re-open the session instead of
                    // re-sending it. See retryKindFor.
                    resyncFailed = true,
                    streaming = emptyMap(),
                    liveTools = emptyMap(),
                )
                return ResyncOutcome.FAILED
            }
        }
    }

    /**
     * The resync after a terminal run event, retried with backoff (03-F2).
     *
     * The finished answer only reaches the transcript through this resync,
     * so one transient failure used to hide it until the next run. Retries
     * while resync() reports FAILED; success and a session change (SKIPPED)
     * both stop the loop. Gives up after [TERMINAL_RESYNC_ATTEMPTS]; the
     * banner (with its RELOAD_SESSION retry) is already set by the failed
     * resync.
     *
     * The loop decides on the outcome resync() reports, never on whether
     * [ChatState.lastFailure] changed: two identical failures (same
     * code/tag/message) are equal as values, so a MutableStateFlow
     * conflates them and keeps the first instance — an identity check
     * would read "unchanged" and give up after the first retry even
     * though the next attempt never ran.
     */
    private suspend fun resyncAfterTerminal(expectedSessionID: String) {
        // Audit-200 B3 (M-375): the generation is captured here — a new
        // run started after the terminal event stops the loop before its
        // next attempt, and [resync] re-checks it after every await, so
        // a late retry can neither fetch for nor write over the new run.
        // The session is pinned too (M-490): an orphan loop that wakes
        // after a switch must not adopt the new session and stick the old
        // run's failure onto it.
        val generation = runGeneration
        repeat(TERMINAL_RESYNC_ATTEMPTS) { attempt ->
            if (runGeneration != generation) return
            when (resync(fromTerminal = true, forSessionID = expectedSessionID, terminalGeneration = generation)) {
                ResyncOutcome.SUCCEEDED, ResyncOutcome.SKIPPED -> return
                ResyncOutcome.FAILED -> {
                    if (attempt + 1 < TERMINAL_RESYNC_ATTEMPTS) {
                        delay(TERMINAL_RESYNC_DELAYS_MS[attempt])
                    }
                }
            }
        }
    }

    /**
     * Sends a prompt.
     *
     * The HTTP call returns as soon as the engine admits the input; the answer
     * arrives on the stream. The user's message is appended optimistically so the
     * screen responds immediately.
     */
    suspend fun send(text: String): ApiResult<Unit> {
        val id = sessionID ?: return ApiResult.Failure(code = -1, tag = "NoSession")
        val result = sessionApi.prompt(id, text)
        if (result is ApiResult.Success) {
            // A new run starts: late text events are live again (M-558),
            // the terminal retry loop must stand down (M-375), and asks
            // from the previous run may be restored again (M-081).
            runEnded = false
            runGeneration++
            if (permissionGate.suppressedSessionID == id) {
                permissionGate.suppressedSessionID = null
            }
            // The admit response may carry no text; what the user typed is known here, so
            // the bubble never renders empty. Remembered by id for later resyncs too.
            val shown = if (result.value.text.isNullOrBlank()) result.value.copy(text = text) else result.value
            sentText[shown.id] = text
            // History is stored oldest-first here, so the new user message goes last.
            _state.value = _state.value.copy(
                messages = _state.value.messages + shown,
                busy = true,
                lastFailure = null,
                resyncFailed = false,
            )
        } else if (result is ApiResult.Failure) {
            _state.value = _state.value.copy(lastFailure = result, resyncFailed = false)
        }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }
    }

    suspend fun setModel(model: ModelRef): ApiResult<Unit> {
        val id = sessionID ?: return ApiResult.Failure(code = -1, tag = "NoSession")
        return sessionApi.setModel(id, model)
    }

    suspend fun interrupt(): ApiResult<Unit> {
        val id = sessionID ?: return ApiResult.Failure(code = -1, tag = "NoSession")
        val result = sessionApi.interrupt(id)
        // Audit-200 B3 (M-081): the interrupt was accepted, so the run is
        // ending — its ask is dead even if the terminal event never
        // arrives (stream dropped), and the resync that follows must not
        // restore it. The terminal branch sets the same flag; this covers
        // the event's absence.
        if (result is ApiResult.Success) permissionGate.suppressedSessionID = id
        return result
    }

    /**
     * Answers the pending permission request.
     *
     * [decision] is `once`, `always` or `reject`. The engine blocks the tool call
     * until this arrives, so a dropped reply leaves the agent waiting forever.
     * `always` persists a saved permission for the project, which is why the UI
     * must distinguish it from `once`.
     */
    suspend fun replyPermission(decision: String, message: String? = null): ApiResult<Unit> {
        val pending = _state.value.pendingPermission
            ?: return ApiResult.Failure(code = -1, tag = "NoPendingPermission")
        // A reply for this request is already on its way: the in-flight one
        // covers it, and a second POST would only fail against the engine
        // (already answered) and re-arm a dead prompt.
        if (replyingPermissionId == pending.id) return ApiResult.Success(Unit)
        replyingPermissionId = pending.id
        try {
            return when (val result = sessionApi.replyPermission(pending.sessionID, pending.id, decision, message)) {
                is ApiResult.Success -> {
                    // Clear by id (item 22): a newer request may have arrived
                    // via SSE while this reply was in flight, and wiping it
                    // would leave the run blocked with no prompt on screen.
                    // Remember the answer (58-F5): the engine can keep
                    // listing this id until it processes the reply, and a
                    // resync in that window must not resurrect it. The
                    // memory lives in the shared gate (audit-200 B3,
                    // M-547), not in this repository instance, so a
                    // rebuild does not lose the suppression.
                    permissionGate.lastAnsweredId = pending.id
                    permissionGate.lastAnsweredSessionID = pending.sessionID
                    permissionGate.lastAnsweredAtMs = System.currentTimeMillis()
                    _state.value = _state.value.copy(
                        pendingPermission = _state.value.pendingPermission?.takeIf { it.id != pending.id },
                    )
                    ApiResult.Success(Unit)
                }
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(lastFailure = result, resyncFailed = false)
                    result
                }
            }
        } finally {
            replyingPermissionId = null
        }
    }

    private fun handle(event: EngineEvent) {
        val payload = event.data?.jsonObject ?: return
        when (event.type) {
            EventTypes.TEXT_STARTED -> {
                val started = event.decode<TextStarted>() ?: return
                // Audit-200 B3 (M-558): a text event for a run that already
                // ended — or for a message the history already finished —
                // is stale (a replay after a reconnect). It must not
                // resurrect a streaming entry over the finished answer.
                if (runEnded || isCompletedMessage(started.assistantMessageID)) return
                buffers.getOrPut(started.assistantMessageID to started.ordinal) { StringBuilder() }
                publish(started.assistantMessageID)
            }

            EventTypes.TEXT_DELTA -> {
                val delta = event.decode<TextDelta>() ?: return
                if (runEnded || isCompletedMessage(delta.assistantMessageID)) return
                buffers.getOrPut(delta.assistantMessageID to delta.ordinal) { StringBuilder() }
                    .append(delta.delta)
                publish(delta.assistantMessageID)
            }

            EventTypes.TEXT_ENDED -> {
                val ended = event.decode<TextEnded>() ?: return
                if (runEnded || isCompletedMessage(ended.assistantMessageID)) return
                // The final text is authoritative: replace whatever the deltas
                // built, so a dropped delta does not leave a truncated
                // message — but only this block's buffer, never the whole
                // message (item 25).
                buffers[ended.assistantMessageID to ended.ordinal] = StringBuilder(ended.text)
                // Forced: the final text must land immediately, never up to
                // a throttle window late (item 28).
                publish(ended.assistantMessageID, force = true)
            }

            EventTypes.EXECUTION_STARTED -> {
                runEnded = false
                _state.value = _state.value.copy(busy = true)
            }

            EventTypes.TOOL_INPUT_STARTED, EventTypes.TOOL_CALLED,
            EventTypes.TOOL_SUCCESS, EventTypes.TOOL_FAILED -> applyToolEvent(event.type, payload)

            EventTypes.PERMISSION_ASKED -> {
                val payload = event.data?.jsonObject ?: return
                val id = payload["id"]?.jsonPrimitive?.contentOrNull ?: return
                val action = payload["action"]?.jsonPrimitive?.contentOrNull ?: return
                val resources = payload["resources"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.toSet()
                    .orEmpty()
                val save = payload["save"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.toSet()
                    .orEmpty()
                _state.value = _state.value.copy(
                    lastFailure = null,
                    resyncFailed = false,
                    // Stored so the UI can render the prompt and the operator can reply.
                    // The engine blocks the tool until this is answered.
                    pendingPermission = PendingPermission(
                        id = id,
                        sessionID = event.sessionIDOf() ?: return,
                        action = action,
                        resources = resources,
                        save = save,
                        message = payload["message"]?.jsonPrimitive?.contentOrNull,
                    ),
                )
            }

            EventTypes.EXECUTION_SUCCEEDED, EventTypes.EXECUTION_FAILED, EventTypes.EXECUTION_INTERRUPTED -> {
                // A finished run cannot still be waiting for an answer; drop the
                // prompt so a dead request does not sit on screen.
                _state.value = _state.value.copy(busy = false, pendingPermission = null)
                // Audit-200 B3 (M-558/M-081): the run is over — later text
                // events are stale, and any ask the engine still lists
                // belongs to this dead run, so no resync restores it
                // until the session sends again.
                runEnded = true
                permissionGate.suppressedSessionID = sessionID
                // The run is over: no more deltas will arrive for its
                // messages, so their buffers are stale (item 25). The
                // streaming entries themselves stay until the resync below
                // lands — clearing them here would blink the just-finished
                // answer out of the transcript on every turn; the resync
                // replaces them atomically on success and drops them on
                // failure, and drops the live tools the same way (06-T1),
                // so no indicator can stick either way.
                buffers.clear()
                publishFlushJob?.cancel()
                publishFlushJob = null
                // Pull the finished message from REST so tool calls and token counts
                // that never appeared as deltas are included. Retried with
                // backoff (03-F2): this resync is the only way the finished
                // answer reaches the transcript, so one transient failure
                // used to hide it until the next run. The session is
                // captured here (audit-200 B3, M-490): the loop must resync
                // THIS session, never whatever is current when it wakes.
                val terminalSessionID = sessionID
                if (terminalSessionID != null) {
                    scope.launch { resyncAfterTerminal(terminalSessionID) }
                }
            }

            else -> {
                // Unhandled types are expected: the stream carries ~25 of them.
                if (payload.isEmpty()) Log.v(TAG, "event ${event.type} had no payload")
            }
        }
    }

    /**
     * Folds one tool event into [ChatState.liveTools].
     *
     * Only `input.started` carries the tool name, so later events keep the name of
     * the entry they update. An update for an unseen id (stream joined mid-call) is
     * dropped; `resync()` at the end of the run supplies it from history.
     */
    private fun applyToolEvent(type: String, p: kotlinx.serialization.json.JsonObject) {
        val id = p["id"]?.jsonPrimitive?.contentOrNull ?: return
        val messageID = p["assistantMessageID"]?.jsonPrimitive?.contentOrNull ?: return
        val tools = _state.value.liveTools
        val prev = tools[id]?.call
        val next = when (type) {
            EventTypes.TOOL_INPUT_STARTED ->
                ToolCall(id, p["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), "streaming")
            EventTypes.TOOL_CALLED -> prev?.copy(status = "running", input = p["input"])
            EventTypes.TOOL_SUCCESS -> prev?.copy(
                status = "completed",
                output = runCatching {
                    ApiJson.decodeFromJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(serializer<MessageContent>()),
                        p["content"] ?: return@runCatching null,
                    ).toolOutput()
                }.getOrNull(),
            )
            else -> prev?.copy(
                status = "error",
                error = p["error"]?.jsonObject?.let { e ->
                    (e["message"] ?: e["type"])?.jsonPrimitive?.contentOrNull
                } ?: "failed",
            )
        } ?: return
        _state.value = _state.value.copy(liveTools = tools + (id to LiveTool(messageID, next)))
    }

    /**
     * Publishes one message's streamed text, at most once per
     * [PUBLISH_THROTTLE_MS]: a token delta used to emit a whole-transcript
     * rebuild each time (item 28). A skipped publish is not dropped — a
     * delayed flush lands the newest text at most one window later — and
     * TEXT_ENDED always forces, so the final text is never late.
     */
    private fun publish(messageID: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPublishAtMs < PUBLISH_THROTTLE_MS) {
            if (publishFlushJob?.isActive != true) {
                publishFlushJob = scope.launch {
                    delay(PUBLISH_THROTTLE_MS - (now - lastPublishAtMs))
                    flushPublishes()
                }
            }
            return
        }
        publishNow(messageID)
    }

    private fun publishNow(messageID: String) {
        lastPublishAtMs = System.currentTimeMillis()
        _state.value = _state.value.copy(streaming = _state.value.streaming + (messageID to streamedText(messageID)))
    }

    /**
     * Whether the history already finished this assistant message
     * (audit-200 B3, M-558): a completed time means the run's text for
     * it is persisted, so any later delta for the id is a stale replay.
     */
    private fun isCompletedMessage(messageID: String): Boolean =
        _state.value.messages.any { it.id == messageID && it.isAssistant && it.time?.completed != null }

    /** Current streamed text for one message: its blocks in ordinal order. */
    private fun streamedText(messageID: String): String =
        buffers.entries
            .filter { it.key.first == messageID }
            .sortedBy { it.key.second }
            .joinToString("\n\n") { it.value.toString() }

    /** Publishes every buffered message's current text (the coalesced flush). */
    private fun flushPublishes() {
        lastPublishAtMs = System.currentTimeMillis()
        val streaming = _state.value.streaming.toMutableMap()
        buffers.keys.map { it.first }.toSet().forEach { messageID ->
            streaming[messageID] = streamedText(messageID)
        }
        _state.value = _state.value.copy(streaming = streaming)
    }

    /** Session id from any event payload; every event carries one. */
    private fun EngineEvent.sessionIDOf(): String? =
        data?.jsonObject?.get("sessionID")?.jsonPrimitive?.contentOrNull

    private inline fun <reified T> EngineEvent.decode(): T? =
        data?.let { element ->
            runCatching {
                ApiJson.decodeFromJsonElement(serializer<T>(), element)
            }.getOrNull()
        }
}
