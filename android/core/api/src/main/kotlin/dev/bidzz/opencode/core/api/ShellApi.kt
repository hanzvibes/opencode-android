package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.ShellCreateRequest
import dev.bidzz.opencode.core.api.dto.ShellInfo
import dev.bidzz.opencode.core.api.dto.ShellOutput
import dev.bidzz.opencode.core.api.dto.SingleResponse
import kotlinx.serialization.encodeToString

/**
 * Location-scoped shell commands (audit-200 C14, M-195/M-196).
 *
 * Shapes verified against the engine fork source at pin fedc6f20
 * (`packages/schema/src/shell.ts`, `packages/protocol/src/groups/shell.ts`,
 * `packages/server/src/handlers/shell.ts`):
 *
 * - `POST /api/shell` takes `{command, cwd?, timeout?, metadata?}` and
 *   answers the created [ShellInfo]. `cwd` defaults server-side to the
 *   project directory, so callers that want "run it in this project"
 *   omit it. The command is non-interactive; combined stdout/stderr is
 *   captured to a file.
 * - `GET /api/shell/{id}` returns the command, including its status and
 *   exit code once exited.
 * - `GET /api/shell/{id}/output?cursor&limit` pages the captured output
 *   by absolute byte cursor; a consumer has more while `cursor < size`.
 * - `DELETE /api/shell/{id}` terminates and removes the command and its
 *   retained output; answers 204.
 *
 * All four answers are wrapped in the location envelope
 * (`{location, data}`), decoded through [SingleResponse] like the other
 * single-object endpoints.
 */
class ShellApi(private val client: EngineClient) {

    /** One output page; 64 KiB keeps a long log readable without stalling the UI. */
    companion object {
        const val OUTPUT_PAGE_LIMIT = 65_536L
    }

    suspend fun create(
        command: String,
        cwd: String? = null,
        timeout: Long? = null,
    ): ApiResult<ShellInfo> {
        val body = ApiJson.encodeToString(ShellCreateRequest(command = command, cwd = cwd, timeout = timeout))
        val request = client.request("/api/shell")
            .post(client.jsonBody(body))
            .build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SingleResponse<ShellInfo>>(raw).data
        }
    }

    suspend fun get(id: String): ApiResult<ShellInfo> {
        val request = client.request("/api/shell/$id").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SingleResponse<ShellInfo>>(raw).data
        }
    }

    suspend fun output(
        id: String,
        cursor: Long = 0L,
        limit: Long = OUTPUT_PAGE_LIMIT,
    ): ApiResult<ShellOutput> {
        val request = client.request("/api/shell/$id/output?cursor=$cursor&limit=$limit").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SingleResponse<ShellOutput>>(raw).data
        }
    }

    suspend fun remove(id: String): ApiResult<Unit> {
        val request = client.request("/api/shell/$id").delete().build()
        return client.executeUnit(request)
    }
}
