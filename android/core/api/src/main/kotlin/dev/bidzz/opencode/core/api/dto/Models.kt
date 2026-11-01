package dev.bidzz.opencode.core.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for the endpoints the chat path actually uses.
 *
 * Hand-written rather than generated from `openapi.json` (134 paths, 351 schemas).
 * The spec is machine-generated from Effect internals and encodes two things that
 * make generation counterproductive here: 77 schemas use `anyOf` without a
 * discriminator, which generators turn into `JsonElement`, and 63 schemas are
 * named `*Encoded`, which are serialisation details rather than a contract worth
 * committing to. Only the shapes verified against real responses are modelled;
 * `ignoreUnknownKeys` keeps that honest as the server evolves.
 */

/** Absolute path on the device. The engine rejects relative paths. */
@Serializable
data class LocationRef(
    val directory: String,
)

@Serializable
data class SessionInfo(
    val id: String,
    val projectID: String? = null,
    val title: String? = null,
    val model: ModelRef? = null,
    val cost: Double? = null,
    val tokens: TokenUsage? = null,
    val outcome: String? = null,
    val time: SessionTime? = null,
    val location: LocationRef? = null,
)

@Serializable
data class SessionTime(
    val created: Long? = null,
    val updated: Long? = null,
    val busy: Long? = null,
    val idle: Long? = null,
)

@Serializable
data class TokenUsage(
    val input: Long? = null,
    val output: Long? = null,
    val reasoning: Long? = null,
    val cache: CacheUsage? = null,
)

@Serializable
data class CacheUsage(
    val read: Long? = null,
    val write: Long? = null,
)

/** Reference to a model: the pair the engine needs to resolve it. */
@Serializable
data class ModelRef(
    val id: String,
    val providerID: String,
    val variant: String? = null,
)

/**
 * Cursor for the paginated list endpoints.
 *
 * Opaque strings; the client must not decode them. `next` is null on the last page.
 */
@Serializable
data class PageCursor(
    val next: String? = null,
    val previous: String? = null,
)

@Serializable
data class SessionsResponse(
    val data: List<SessionInfo> = emptyList(),
    val cursor: PageCursor? = null,
)

/** Envelope for endpoints returning a single object. */
@Serializable
data class SingleResponse<T>(
    val data: T,
)

@Serializable
data class MessagesResponse(
    val data: List<SessionMessage> = emptyList(),
    val cursor: PageCursor? = null,
)

/**
 * One entry in a session's history.
 *
 * The engine returns three kinds, distinguished by `type`:
 * - `user` — what the operator typed (`text`).
 * - `assistant` — model output, as ordered content blocks.
 * - `idle` — a terminator marking the session finished, carrying `outcome`.
 *
 * Modelled as one class with nullable fields rather than a sealed hierarchy,
 * because the discriminator is a plain string on a wire format that gains new
 * types without notice. A sealed type would throw on the first unknown kind,
 * which is exactly when a chat client should keep working.
 */
@Serializable
data class SessionMessage(
    val id: String,
    val type: String,
    val time: MessageTime? = null,
    /** Present on `user` messages. */
    val text: String? = null,
    /** Present on `assistant` messages. */
    val content: List<MessageContent> = emptyList(),
    val agent: String? = null,
    val model: ModelRef? = null,
    val finish: String? = null,
    val cost: Double? = null,
    val tokens: TokenUsage? = null,
    /** Present on `idle` messages: "succeeded", "failed", ... */
    val outcome: String? = null,
    /** Present on a failed `assistant` message, e.g. `provider.free-limit` (HTTP 429). */
    val error: ToolError? = null,
) {
    val isUser: Boolean get() = type == "user"
    val isAssistant: Boolean get() = type == "assistant"
    val isIdle: Boolean get() = type == "idle"

    /** Concatenated text of all text blocks, in order. */
    val plainText: String
        get() = if (isUser) text.orEmpty()
        else content.filter { it.type == "text" }.joinToString("") { it.text.orEmpty() }
}

@Serializable
data class MessageTime(
    val created: Long? = null,
    val streamed: Long? = null,
    val completed: Long? = null,
)

/**
 * One content block of an assistant message.
 *
 * `type` is `text` for prose, `tool` for a tool call, `reasoning` for the model's
 * private thinking. Unknown kinds are preserved so an unrecognised block does not
 * lose the rest of the message.
 */
@Serializable
data class MessageContent(
    val type: String,
    val text: String? = null,
    /** Tool call id, on `tool` blocks. */
    val id: String? = null,
    /** Tool name (`read`, `shell`, ...), on `tool` blocks. */
    val name: String? = null,
    /** Tool state, on `tool` blocks. */
    val state: ToolState? = null,
)

/**
 * What a tool call is doing.
 *
 * `status` is `streaming` (the model is still emitting arguments, and `input` is a
 * raw JSON *string* rather than an object), `running`, `completed` or `error`.
 */
@Serializable
data class ToolState(
    val status: String,
    val input: kotlinx.serialization.json.JsonElement? = null,
    val content: List<MessageContent> = emptyList(),
    val error: ToolError? = null,
)

@Serializable
data class ToolError(
    val type: String? = null,
    val message: String? = null,
)

/** A model the server can run, as returned by `GET /api/model`. */
@Serializable
data class ModelInfo(
    val id: String,
    val modelID: String? = null,
    val providerID: String,
    val name: String? = null,
    val family: String? = null,
    val type: String? = null,
    val status: String? = null,
    val enabled: Boolean? = null,
    val limit: ModelLimit? = null,
) {
    /**
     * The pair `POST /api/session/{id}/model` expects.
     *
     * The engine's own answer for the free `big-pickle` model used the bare model id,
     * not the composite record id, so `modelID` wins when present. Verified against a
     * live engine rather than inferred — if switching models ever starts returning 400,
     * this is the first thing to re-test.
     */
    fun ref(): ModelRef = ModelRef(id = modelID ?: id, providerID = providerID)

    /** Label for the picker: the human name, falling back to the identifiers. */
    fun label(): String = name?.takeIf { it.isNotBlank() } ?: modelID ?: id
}

@Serializable
data class ModelLimit(
    val context: Long? = null,
    val input: Long? = null,
    val output: Long? = null,
)

@Serializable
data class ModelsResponse(
    val data: List<ModelInfo> = emptyList(),
    val location: LocationRef? = null,
)

/** `GET /api/model/default` answers `data: null` when the engine has no default. */
@Serializable
data class ModelDefaultResponse(
    val data: ModelInfo? = null,
    val location: LocationRef? = null,
)

/** Body of `POST /api/session`. The engine requires `location`, not a bare path. */
@Serializable
data class CreateSessionRequest(
    val location: LocationRef,
    val title: String? = null,
    val agent: String? = null,
    val model: ModelRef? = null,
)

/** Body of `POST /api/session/{id}/prompt`. `text` is the only required field. */
@Serializable
data class PromptRequest(
    val text: String,
    @SerialName("resume") val resume: Boolean? = null,
)

/** Body of `POST /api/session/{id}/model`. Responds 204, not 200. */
@Serializable
data class SetModelRequest(
    val model: ModelRef,
)

/**
 * Body of `POST /api/session/{id}/permission/{requestID}/reply`.
 *
 * The field is `decision`, not `reply` — the OpenAPI spec defines
 * `{"decision": "once" | "always" | "reject", "message"?: string}`. Sending the
 * wrong field name returns 400 and leaves the engine blocked.
 */
@Serializable
data class PermissionReplyRequest(
    val decision: String,
    val message: String? = null,
)
