package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.CreateSessionRequest
import dev.bidzz.opencode.core.api.dto.LocationRef
import dev.bidzz.opencode.core.api.dto.MessagesResponse
import dev.bidzz.opencode.core.api.dto.ModelRef
import dev.bidzz.opencode.core.api.dto.PromptRequest
import dev.bidzz.opencode.core.api.dto.SessionInfo
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.api.dto.SessionsResponse
import dev.bidzz.opencode.core.api.dto.SetModelRequest
import dev.bidzz.opencode.core.api.dto.SingleResponse
import kotlinx.serialization.encodeToString

/**
 * Session endpoints.
 *
 * Only the calls the chat path needs are wrapped. Paths are relative to the
 * client's base URL; the client owns auth and timeouts.
 */
class SessionApi(private val client: EngineClient) {

    /**
     * Creates a session bound to [directory].
     *
     * The engine expects `{"location":{"directory":"..."}}`. A bare top-level
     * `directory` is **silently ignored** — the request succeeds, the session is
     * created at whatever the engine's working directory happens to be, and the
     * mismatch only surfaces later as "the agent edited the wrong project". The
     * signature makes that mistake impossible to express.
     */
    suspend fun create(directory: String, title: String? = null): ApiResult<SessionInfo> {
        val body = ApiJson.encodeToString(CreateSessionRequest(location = LocationRef(directory), title = title))
        val request = client.request("/api/session")
            .post(client.jsonBody(body))
            .build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SingleResponse<SessionInfo>>(raw).data
        }
    }

    /** Lists sessions. `directory` narrows to one project when provided. */
    suspend fun list(directory: String? = null, limit: Int = 50): ApiResult<List<SessionInfo>> {
        val query = buildString {
            append("/api/session?limit=").append(limit)
            if (directory != null) append("&directory=").append(java.net.URLEncoder.encode(directory, "UTF-8"))
        }
        val request = client.request(query).get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SessionsResponse>(raw).data
        }
    }

    /**
     * Sends a prompt. Returns once the engine has *admitted* the input, not when
     * the model finishes: the response arrives immediately with a queue entry and
     * the actual output streams over the event channel.
     */
    suspend fun prompt(sessionID: String, text: String): ApiResult<SessionMessage> {
        val body = ApiJson.encodeToString(PromptRequest(text = text))
        val request = client.request("/api/session/$sessionID/prompt")
            .post(client.jsonBody(body))
            .build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SingleResponse<SessionMessage>>(raw).data
        }
    }

    /**
     * Full message history, **newest first**.
     *
     * The engine returns the most recent entry at the head (an `idle` terminator
     * once the loop has finished, then the assistant message, then the user's).
     * [dev.bidzz.opencode.core.api.ChatRepository] reverses it for display; callers
     * that use this directly must do the same.
     */
    suspend fun messages(sessionID: String, limit: Int? = null): ApiResult<List<SessionMessage>> {
        val path = buildString {
            append("/api/session/").append(sessionID).append("/message")
            if (limit != null) append("?limit=").append(limit)
        }
        val request = client.request(path).get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<MessagesResponse>(raw).data
        }
    }

    /**
     * Selects the model for a session.
     *
     * Responds **204 No Content**, not 200. Treating anything other than 2xx as an
     * error is correct; treating 204 as "no answer" and retrying is not.
     */
    suspend fun setModel(sessionID: String, model: ModelRef): ApiResult<Unit> {
        val body = ApiJson.encodeToString(SetModelRequest(model))
        val request = client.request("/api/session/$sessionID/model")
            .post(client.jsonBody(body))
            .build()
        return client.executeUnit(request)
    }

    /** Stops the running agent loop for this session. */
    suspend fun interrupt(sessionID: String): ApiResult<Unit> {
        val request = client.request("/api/session/$sessionID/interrupt")
            .post(client.emptyBody())
            .build()
        return client.executeUnit(request)
    }

    /** One session's metadata. */
    suspend fun get(sessionID: String): ApiResult<SessionInfo> {
        val request = client.request("/api/session/$sessionID").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SingleResponse<SessionInfo>>(raw).data
        }
    }
}
