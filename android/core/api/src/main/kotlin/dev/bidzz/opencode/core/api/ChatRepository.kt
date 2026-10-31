package dev.bidzz.opencode.core.api

import android.util.Log
import dev.bidzz.opencode.core.api.dto.EngineEvent
import dev.bidzz.opencode.core.api.dto.EventTypes
import dev.bidzz.opencode.core.api.dto.ModelRef
import dev.bidzz.opencode.core.api.dto.PendingPermission
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.api.dto.TextDelta
import dev.bidzz.opencode.core.api.dto.TextEnded
import dev.bidzz.opencode.core.api.dto.TextStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    val lastError: String? = null,
    /** Active permission request waiting for the operator to reply. */
    val pendingPermission: PendingPermission? = null,
)

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
) {
    companion object {
        private const val TAG = "ChatRepository"
    }

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var sessionID: String? = null
    private var streamJob: Job? = null

    /** Text received per assistant message id, for the message being streamed. */
    private val buffers = mutableMapOf<String, StringBuilder>()

    /**
     * Attaches to [sessionID]: loads history, then follows the stream.
     *
     * Re-entrant: calling it again for the same session is a no-op, so a UI that
     * recreates itself on rotation does not open a second stream.
     */
    fun open(sessionID: String) {
        if (this.sessionID == sessionID && streamJob?.isActive == true) return
        close()
        this.sessionID = sessionID
        _state.value = ChatState(sessionID = sessionID)

        streamJob = scope.launch {
            resync()
            eventStream.events(onReconnect = { resync() }).collect { event ->
                if (event.type == EventTypes.SERVER_CONNECTED) return@collect
                if (event.sessionIDOf() != sessionID) return@collect
                handle(event)
            }
        }
    }

    fun close() {
        streamJob?.cancel()
        streamJob = null
        sessionID = null
        buffers.clear()
    }

    /** Rebuilds from REST. Called on open and after every reconnect. */
    private suspend fun resync() {
        val id = sessionID ?: return
        when (val result = sessionApi.messages(id)) {
            is ApiResult.Success -> {
                // The engine returns history newest-first (idle, assistant, user...).
                // Reversing here means the UI and every consumer can assume
                // oldest-first, which is the only order a chat transcript can use.
                val chronological = result.value.reversed()
                val newest = result.value.firstOrNull()
                buffers.clear()
                _state.value = _state.value.copy(
                    messages = chronological,
                    streaming = emptyMap(),
                    // A run is over when the newest entry is `idle`, or when the
                    // newest assistant message is finished. The second case matters:
                    // a rejected permission aborts the run without ever writing an
                    // `idle` message, so an idle-only check spins "Working" forever.
                    busy = newest != null && !newest.isIdle && newest.time?.completed == null,
                    lastError = null,
                )
            }
            is ApiResult.Failure -> {
                Log.w(TAG, "resync failed: ${result.display}")
                _state.value = _state.value.copy(lastError = result.display)
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
        val id = sessionID ?: return ApiResult.Failure(code = -1, message = "No session open")
        val result = sessionApi.prompt(id, text)
        if (result is ApiResult.Success) {
            // History is stored oldest-first here, so the new user message goes last.
            _state.value = _state.value.copy(
                messages = _state.value.messages + result.value,
                busy = true,
                lastError = null,
            )
        } else if (result is ApiResult.Failure) {
            _state.value = _state.value.copy(lastError = result.display)
        }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }
    }

    suspend fun setModel(model: ModelRef): ApiResult<Unit> {
        val id = sessionID ?: return ApiResult.Failure(code = -1, message = "No session open")
        return sessionApi.setModel(id, model)
    }

    suspend fun interrupt(): ApiResult<Unit> {
        val id = sessionID ?: return ApiResult.Failure(code = -1, message = "No session open")
        return sessionApi.interrupt(id)
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
            ?: return ApiResult.Failure(code = -1, message = "No pending permission request")
        return when (val result = sessionApi.replyPermission(pending.sessionID, pending.id, decision, message)) {
            is ApiResult.Success -> {
                _state.value = _state.value.copy(pendingPermission = null)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> {
                _state.value = _state.value.copy(lastError = result.display)
                result
            }
        }
    }

    private fun handle(event: EngineEvent) {
        val payload = event.data?.jsonObject ?: return
        when (event.type) {
            EventTypes.TEXT_STARTED -> {
                val started = event.decode<TextStarted>() ?: return
                buffers.getOrPut(started.assistantMessageID) { StringBuilder() }
                publish(started.assistantMessageID)
            }

            EventTypes.TEXT_DELTA -> {
                val delta = event.decode<TextDelta>() ?: return
                buffers.getOrPut(delta.assistantMessageID) { StringBuilder() }.append(delta.delta)
                publish(delta.assistantMessageID)
            }

            EventTypes.TEXT_ENDED -> {
                val ended = event.decode<TextEnded>() ?: return
                // The final text is authoritative: replace whatever the deltas built,
                // so a dropped delta does not leave a truncated message.
                buffers[ended.assistantMessageID] = StringBuilder(ended.text)
                publish(ended.assistantMessageID)
            }

            EventTypes.EXECUTION_STARTED -> _state.value = _state.value.copy(busy = true)

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
                    lastError = null,
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
                // Pull the finished message from REST so tool calls and token counts
                // that never appeared as deltas are included.
                scope.launch { resync() }
            }

            else -> {
                // Unhandled types are expected: the stream carries ~25 of them.
                if (payload.isEmpty()) Log.v(TAG, "event ${event.type} had no payload")
            }
        }
    }

    private fun publish(messageID: String) {
        val text = buffers[messageID]?.toString() ?: return
        _state.value = _state.value.copy(streaming = _state.value.streaming + (messageID to text))
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
