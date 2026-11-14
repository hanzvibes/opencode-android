package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.JobInfo
import java.net.URLEncoder

/**
 * Background jobs (mockup group 3, engine v2.2.7).
 *
 * `GET /api/experimental/job` lists active and unconsumed background
 * jobs — shell commands, subagents and image generations that run
 * behind a session. Shapes verified against the engine fork source at
 * pin fedc6f20 (`packages/protocol/src/groups/job.ts`,
 * `packages/schema/src/job.ts`,
 * `packages/server/src/handlers/job.ts`):
 *
 * - Filters are all optional query params: `sessionID` (a session
 *   that does not exist answers 404), `kind` (`shell` | `subagent` |
 *   `image`), `status` (`running` | `completed` | `error` |
 *   `cancelled`) and `descendants` (the strings `"true"`/`"false"`;
 *   only meaningful with `sessionID` — it also includes jobs owned by
 *   child and nested subagent sessions).
 * - The answer is plain `{data: Job.Info[]}` with no location wrapper.
 */
class JobApi(private val client: EngineClient) {

    suspend fun list(
        sessionID: String? = null,
        kind: String? = null,
        status: String? = null,
        descendants: Boolean? = null,
    ): ApiResult<List<JobInfo>> {
        val query = buildString {
            append("/api/experimental/job")
            var first = true
            fun param(name: String, value: String) {
                append(if (first) "?" else "&")
                first = false
                append(name).append("=").append(URLEncoder.encode(value, "UTF-8"))
            }
            if (sessionID != null) param("sessionID", sessionID)
            if (kind != null) param("kind", kind)
            if (status != null) param("status", status)
            if (descendants != null) param("descendants", descendants.toString())
        }
        val request = client.request(query).get().build()
        // Audit-200 B1 (M-314): decode per entry, not the whole list at
        // once — one malformed job used to hide the entire list.
        return client.execute(request) { raw ->
            client.decodeDataList<JobInfo>(raw)
        }
    }
}
