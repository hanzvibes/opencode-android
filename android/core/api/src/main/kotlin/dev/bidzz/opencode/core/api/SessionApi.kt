package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.CreateSessionRequest
import dev.bidzz.opencode.core.api.dto.FileDiffInfo
import dev.bidzz.opencode.core.api.dto.FormInfo
import dev.bidzz.opencode.core.api.dto.FormReplyRequest
import dev.bidzz.opencode.core.api.dto.FormsResponse
import dev.bidzz.opencode.core.api.dto.LocationRef
import dev.bidzz.opencode.core.api.dto.MessagesResponse
import dev.bidzz.opencode.core.api.dto.ModelRef
import dev.bidzz.opencode.core.api.dto.MoveSessionRequest
import dev.bidzz.opencode.core.api.dto.SessionTransferData
import dev.bidzz.opencode.core.api.dto.SessionTransferResponse
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
import java.net.URLEncoder

/**
 * Session endpoints.
 *
 * Only the calls the chat path needs are wrapped. Paths are relative to the
 * client's base URL; the client owns auth and timeouts.
 */
class SessionApi(private val client: EngineClient) {

    /**
     * Builds an `/api/session/{id}...` path with the id encoded per segment
     * (audit-100 56-F3, defense in depth): a raw interpolation lets a `/`,
     * `?` or `#` inside an id reshape the request path. Valid ids (the
     * `isValidSessionId` charset) pass through unchanged, so legitimate
     * traffic is byte-identical.
     */
    private fun sessionPath(sessionID: String, suffix: String = ""): String =
        "/api/session/${encodePathSegment(sessionID)}$suffix"

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
    suspend fun list(directory: String? = null, limit: Int = 50): ApiResult<List<SessionInfo>> =
        when (val page = listPage(directory, limit)) {
            is ApiResult.Success -> ApiResult.Success(page.value.data)
            is ApiResult.Failure -> page
        }

    /**
     * One page of sessions, with the cursor for the next page (19-F3).
     *
     * The envelope's `cursor.next` is the only way past the first 50:
     * [list] drops it, so sessions 51+ were unreachable from the UI and
     * their pins could never be unpinned. Pass the previous page's
     * `cursor.next` as [cursor]; null starts from the newest.
     */
    suspend fun listPage(directory: String? = null, limit: Int = 50, cursor: String? = null): ApiResult<SessionsResponse> {
        val query = buildString {
            append("/api/session?limit=").append(limit)
            if (directory != null) append("&directory=").append(java.net.URLEncoder.encode(directory, "UTF-8"))
            if (cursor != null) append("&cursor=").append(java.net.URLEncoder.encode(cursor, "UTF-8"))
        }
        val request = client.request(query).get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SessionsResponse>(raw)
        }
    }

    /**
     * Sends a prompt. Returns once the engine has *admitted* the input, not when
     * the model finishes: the response arrives immediately with a queue entry and
     * the actual output streams over the event channel.
     */
    suspend fun prompt(sessionID: String, text: String): ApiResult<SessionMessage> {
        val body = ApiJson.encodeToString(PromptRequest(text = text))
        val request = client.request(sessionPath(sessionID, "/prompt"))
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
    suspend fun messages(sessionID: String, limit: Int? = null): ApiResult<List<SessionMessage>> =
        when (val page = messagesPage(sessionID, limit)) {
            is ApiResult.Success -> ApiResult.Success(page.value.data)
            is ApiResult.Failure -> page
        }

    /**
     * One page of messages, with the cursor for the next (older) page
     * (19-F2).
     *
     * The envelope's `cursor.next` walks toward the oldest message: [list]
     * and [messages] drop it, so a caller that assumed "no limit = whole
     * history" silently got only the engine's default page when the
     * engine paginates — and the session preview (the first user message)
     * came from the wrong end. Pass the previous page's `cursor.next` as
     * [cursor]; null starts from the newest.
     */
    suspend fun messagesPage(sessionID: String, limit: Int? = null, cursor: String? = null): ApiResult<MessagesResponse> {
        val path = buildString {
            append(sessionPath(sessionID, "/message"))
            val params = listOfNotNull(
                limit?.let { "limit=$it" },
                cursor?.let { "cursor=${java.net.URLEncoder.encode(it, "UTF-8")}" },
            )
            if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
        }
        val request = client.request(path).get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<MessagesResponse>(raw)
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
     * pre-compaction turns are replaced by the summary. Each assistant
     * message's `input` is the whole prompt that call sent, so summing
     * assistant [TokenUsage] over the listing counts the shared history
     * once per turn; the meter instead takes the LAST assistant message's
     * usage, which is the prompt the next turn builds on (audit-100
     * Fase 6a, 23-F1). Summing is still the right rule for a session's
     * lifetime total — just not for what is in the window right now.
     */
    suspend fun context(sessionID: String): ApiResult<List<SessionMessage>> {
        val request = client.request(sessionPath(sessionID, "/context")).get().build()
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
            append(sessionPath(sessionID, "/diff"))
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
        val request = client.request(sessionPath(sessionID, "/model"))
            .post(client.jsonBody(body))
            .build()
        return client.executeUnit(request)
    }

    /** Stops the running agent loop for this session. */
    suspend fun interrupt(sessionID: String): ApiResult<Unit> {
        val request = client.request(sessionPath(sessionID, "/interrupt"))
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
        val request = client.request(sessionPath(sessionID, "/permission/$requestID/reply"))
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
        val request = client.request(sessionPath(sessionID, "/permission")).get().build()
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
        val request = client.request(sessionPath(sessionID, "/form")).get().build()
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
        val request = client.request(sessionPath(sessionID, "/form/$formID/reply"))
            .post(client.jsonBody(body))
            .build()
        return client.executeUnit(request)
    }

    /** One session's metadata. */
    suspend fun get(sessionID: String): ApiResult<SessionInfo> {
        val request = client.request(sessionPath(sessionID)).get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SingleResponse<SessionInfo>>(raw).data
        }
    }

    /**
     * Moves a session to another project directory
     * (`POST /api/session/{id}/move`).
     *
     * The request is just the destination `{ directory }` — verified
     * against the pinned engine source (see [MoveSessionRequest]): the
     * engine resolves the project from the directory itself. Answers
     * **204 No Content**; a bad directory fails with `InvalidRequestError`.
     * The session keeps its history; its project changes, so callers must
     * refresh the session list afterwards.
     */
    suspend fun move(sessionID: String, directory: String): ApiResult<Unit> {
        val body = ApiJson.encodeToString(MoveSessionRequest(directory = directory))
        val request = client.request(sessionPath(sessionID, "/move"))
            .post(client.jsonBody(body))
            .build()
        return client.executeUnit(request)
    }

    /**
     * A session's complete projected transcript
     * (`GET /api/experimental/session/{id}/export`).
     *
     * The raw JSON comes back with the decoded data: the screen shows the
     * summary from [SessionTransferData] and copies the verbatim bytes, so
     * the export the user shares is exactly what the engine sent — no
     * re-encoding round-trip that could drop a field the app does not
     * model. Shape verified against the pinned engine source (see
     * [SessionTransferData]); `?sanitize=true` is not sent.
     */
    suspend fun export(sessionID: String): ApiResult<SessionExport> {
        val request = client.request("/api/experimental/session/${encodePathSegment(sessionID)}/export").get().build()
        return client.execute(request) { raw ->
            SessionExport(
                data = ApiJson.decodeFromString<SessionTransferResponse>(raw).data,
                rawJson = raw,
            )
        }
    }

    /** Deletes a session from the engine. */
    suspend fun delete(sessionID: String): ApiResult<Unit> {
        val request = client.request(sessionPath(sessionID)).delete().build()
        return client.executeUnit(request)
    }

    /** Renames a session. */
    suspend fun rename(sessionID: String, title: String): ApiResult<Unit> {
        val body = ApiJson.encodeToString(UpdateSessionRequest(title = title))
        val request = client.request(sessionPath(sessionID))
            .patch(client.jsonBody(body))
            .build()
        return client.executeUnit(request)
    }
}

/**
 * Percent-encodes one path segment (audit-100 56-F3, defense in depth).
 *
 * `URLEncoder` is form encoding — a space becomes `+`, which is a literal
 * plus in a path — so `+` is rewritten to `%20`. Valid session ids (the
 * `isValidSessionId` charset: letters, digits, `_`, `-`, `.`) pass through
 * unchanged, so legitimate traffic is byte-identical.
 */
internal fun encodePathSegment(segment: String): String =
    URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")

/**
 * A session export: the decoded transcript plus the verbatim JSON it
 * came from, so the copy the user shares is exactly what the engine
 * sent.
 */
data class SessionExport(
    val data: SessionTransferData,
    val rawJson: String,
)
