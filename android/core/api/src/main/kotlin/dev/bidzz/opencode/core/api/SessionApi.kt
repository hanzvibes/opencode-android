package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.CreateSessionRequest
import dev.bidzz.opencode.core.api.dto.FileDiffInfo
import dev.bidzz.opencode.core.api.dto.FormInfo
import dev.bidzz.opencode.core.api.dto.FormReplyRequest
import dev.bidzz.opencode.core.api.dto.FormsResponse
import dev.bidzz.opencode.core.api.dto.LocationRef
import dev.bidzz.opencode.core.api.dto.MessagesResponse
import dev.bidzz.opencode.core.api.dto.ModelRef
import dev.bidzz.opencode.core.api.dto.PendingPermission
import dev.bidzz.opencode.core.api.dto.PermissionReplyRequest
import dev.bidzz.opencode.core.api.dto.PermissionsResponse
import dev.bidzz.opencode.core.api.dto.PromptRequest
import dev.bidzz.opencode.core.api.dto.SessionInfo
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.api.dto.SessionsResponse
import dev.bidzz.opencode.core.api.dto.SetModelRequest
import dev.bidzz.opencode.core.api.dto.SingleResponse
import dev.bidzz.opencode.core.api.dto.UpdateSessionRequest
import dev.bidzz.opencode.core.api.dto.VcsDiffResponse
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
     * The session's current context: the message history the engine would
     * send the model next (`GET /api/session/{id}/context`).
     *
     * Same `{data: SessionMessage[]}` envelope as [messages]; the shape is
     * verified against the pinned engine source (`SessionStore.context` in
     * packages/core/src/session/store.ts loads the latest history). Unlike
     * [messages] this is the *active* context — after a compaction the
     * pre-compaction turns are replaced by the summary — so summing assistant
     * [TokenUsage] over it is what the next turn actually costs, not the
     * session's lifetime total.
     */
    suspend fun context(sessionID: String): ApiResult<List<SessionMessage>> {
        val request = client.request("/api/session/$sessionID/context").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<MessagesResponse>(raw).data
        }
    }

    /**
     * Structured diffs of the files a turn changed (`GET /api/session/{id}/diff`).
     *
     * Same `{data: FileDiff.Info[]}` envelope as the VCS diff; verified against
     * the pinned engine source (`SessionDiff.turn` returns `Snapshot.diff`,
     * whose entries are `FileDiff.Info`). No `from`/`to` means the latest
     * turn; `from`/`to` are user-message ids bounding the range. Only a step
     * still running in the active session compares against the working copy.
     */
    suspend fun diff(sessionID: String, from: String? = null, to: String? = null): ApiResult<List<FileDiffInfo>> {
        val query = buildString {
            append("/api/session/").append(sessionID).append("/diff")
            var first = true
            fun param(name: String, value: String) {
                append(if (first) "?" else "&").append(name).append("=")
                    .append(java.net.URLEncoder.encode(value, "UTF-8"))
                first = false
            }
            if (from != null) param("from", from)
            if (to != null) param("to", to)
        }
        val request = client.request(query).get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<VcsDiffResponse>(raw).data
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

    /**
     * Answers a pending permission request.
     *
     * The engine blocks the tool call until this arrives. [decision] must be
     * `once`, `always` or `reject`; `always` writes a saved permission for the
     * project so the same request is not asked again.
     */
    suspend fun replyPermission(
        sessionID: String,
        requestID: String,
        decision: String,
        message: String? = null,
    ): ApiResult<Unit> {
        val body = ApiJson.encodeToString(PermissionReplyRequest(decision = decision, message = message))
        val request = client.request("/api/session/$sessionID/permission/$requestID/reply")
            .post(client.jsonBody(body))
            .build()
        return client.executeUnit(request)
    }

    /**
     * Pending permission requests for a session.
     *
     * The engine keeps waiting on these until they are replied to; the client
     * only learns about them from the `permission.asked` stream event, so a
     * reconnect or a reopened session would lose the prompt without this call
     * (item 22). The response is the same `Permission.Request` shape as the
     * stream event, decoded as [PendingPermission] (unknown fields ignored).
     */
    suspend fun listPermissions(sessionID: String): ApiResult<List<PendingPermission>> {
        val request = client.request("/api/session/$sessionID/permission").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<PermissionsResponse>(raw).data
        }
    }

    /**
     * Pending forms for a session (`GET /api/session/{id}/form`).
     *
     * The agent's question tool waits on a form (`forms.ask()` in the
     * engine): the run stays paused inside the tool call until the form is
     * replied to or cancelled, so a prompt sent as an ordinary message
     * never reaches it — that was the question-card deadlock (audit-70
     * 10-F1). The list is how the app finds the form id for a question
     * card: the form's metadata ties it to its tool call
     * (`FormInfo.isQuestionFor`). Verified against the pinned engine
     * source (protocol `session.form.list`, `packages/protocol/src/groups/
     * session.ts` at fork pin 92b2ca0).
     */
    suspend fun listForms(sessionID: String): ApiResult<List<FormInfo>> {
        val request = client.request("/api/session/$sessionID/form").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<FormsResponse>(raw).data
        }
    }

    /**
     * Submits an answer to a pending form (`POST
     * /api/session/{id}/form/{formID}/reply`).
     *
     * Responds **204 No Content**. The answer is keyed by field
     * (`q0`, `q1`, …): a single-choice answer is the option label as a
     * string, a multi-choice answer is an array of labels. A partial
     * answer is accepted — the engine only requires fields the form
     * marks required, and the question tool marks none — so each
     * question on a card can be answered on its own. Verified against
     * the pinned engine source (protocol `session.form.reply`, `Form.
     * Reply = {answer: Record<string, Value>}`).
     */
    suspend fun replyForm(
        sessionID: String,
        formID: String,
        answer: Map<String, kotlinx.serialization.json.JsonElement>,
    ): ApiResult<Unit> {
        val body = ApiJson.encodeToString(FormReplyRequest(answer = answer))
        val request = client.request("/api/session/$sessionID/form/$formID/reply")
            .post(client.jsonBody(body))
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

    /** Deletes a session from the engine. */
    suspend fun delete(sessionID: String): ApiResult<Unit> {
        val request = client.request("/api/session/$sessionID").delete().build()
        return client.executeUnit(request)
    }

    /** Renames a session. */
    suspend fun rename(sessionID: String, title: String): ApiResult<Unit> {
        val body = ApiJson.encodeToString(UpdateSessionRequest(title = title))
        val request = client.request("/api/session/$sessionID")
            .patch(client.jsonBody(body))
            .build()
        return client.executeUnit(request)
    }
}
