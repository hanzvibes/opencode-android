package dev.bidzz.opencode.core.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One server-sent event, as delivered on `GET /api/event`.
 *
 * Wire format, captured from a live engine:
 *
 * ```
 * data: {"id":"evt_...","type":"server.connected","data":{}}
 * <blank line>
 * : heartbeat
 * ```
 *
 * Note there is no `event:` field: the event kind is inside the JSON. And
 * `: heartbeat` is an SSE comment used to keep the connection alive, which the
 * parser must skip without treating it as an event.
 *
 * [data] is kept as raw JSON and decoded lazily by [payload]. Decoding eagerly
 * would mean a single unknown or malformed event kills the whole stream, and the
 * stream carries ~25 unrelated event types that this client does not care about.
 */
@Serializable
data class EngineEvent(
    val id: String? = null,
    val type: String,
    val data: kotlinx.serialization.json.JsonElement? = null,
)

/**
 * Text streaming, in three events.
 *
 * A single assistant response can contain several text blocks, each identified by
 * [ordinal]. Appending to "the last message" is therefore wrong as soon as a
 * response has more than one block; deltas must be keyed by message id and ordinal.
 */
@Serializable
data class TextStarted(
    val sessionID: String,
    val assistantMessageID: String,
    val ordinal: Int = 0,
)

@Serializable
data class TextDelta(
    val sessionID: String,
    val assistantMessageID: String,
    val ordinal: Int = 0,
    val delta: String,
)

@Serializable
data class TextEnded(
    val sessionID: String,
    val assistantMessageID: String,
    val ordinal: Int = 0,
    val text: String,
)

/** A step is one model turn inside an execution; several may run per prompt. */
@Serializable
data class StepStarted(
    val sessionID: String,
    val assistantMessageID: String? = null,
    val agent: String? = null,
    val model: ModelRef? = null,
    val started: Long? = null,
)

/** Marks the beginning and end of an agent loop for a session. */
@Serializable
data class ExecutionState(
    val sessionID: String,
)

/** A tool call or other non-text block, if the engine reports one. */
@Serializable
data class ToolPart(
    val sessionID: String? = null,
    @SerialName("callID") val callID: String? = null,
    val tool: String? = null,
    val status: String? = null,
)

/**
 * A permission request the engine is waiting for the operator to answer.
 *
 * Emitted as `permission.asked` when a tool calls `Permission.assert` and no rule
 * matches. The engine then blocks the tool call until this request is replied to
 * via `POST /api/session/{sessionID}/permission/{requestID}/reply`.
 *
 * The engine can ask the same question several times in a row; each reply is
 * `once`, `always` or `reject`, and `always` is persisted to the project's saved
 * permissions so the next request of the same shape is answered automatically.
 */
@Serializable
data class PendingPermission(
    val id: String,
    val sessionID: String,
    val action: String,
    val resources: Set<String> = emptySet(),
    val save: Set<String> = emptySet(),
    val metadata: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    val message: String? = null,
)

/** Event type names this client acts on. Everything else is ignored. */
object EventTypes {
    const val SERVER_CONNECTED = "server.connected"
    const val TEXT_STARTED = "session.text.started"
    const val TEXT_DELTA = "session.text.delta"
    const val TEXT_ENDED = "session.text.ended"
    const val STEP_STARTED = "session.step.started"
    const val STEP_ENDED = "session.step.ended"
    const val EXECUTION_STARTED = "session.execution.started"
    const val EXECUTION_SUCCEEDED = "session.execution.succeeded"
    const val EXECUTION_FAILED = "session.execution.failed"

    /**
     * The third way a run ends: `user` (stop button), `shutdown`, `superseded` or
     * `inactivity`. Rejecting a permission lands here too, via an aborted step, so
     * missing it leaves the screen spinning "Working" forever.
     */
    const val EXECUTION_INTERRUPTED = "session.execution.interrupted"
    const val INBOX_ENQUEUED = "session.inbox.enqueued"
    const val INBOX_DELIVERED = "session.inbox.delivered"
    const val SESSION_CREATED = "session.created"
    const val SESSION_RENAMED = "session.renamed"
    const val PERMISSION_ASKED = "permission.asked"
}
