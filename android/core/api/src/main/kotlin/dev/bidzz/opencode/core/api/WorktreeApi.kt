package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.WorktreeDirectory

/**
 * Saved worktrees for one project.
 *
 * Read-only: the app lists worktrees. Creating, removing and refreshing
 * are writes (`POST /api/worktree`, `DELETE /api/worktree`,
 * `POST /api/worktree/refresh`) the app deliberately does not call.
 *
 * Shapes verified against the engine fork source at pin 92b2ca0
 * (`packages/protocol/src/groups/worktree.ts`,
 * `packages/schema/src/worktree.ts`): the list needs a `projectID` query
 * parameter, and answers a **bare array** of `Worktree.Directory`
 * (`{ directory, strategy? }`) — not the `{location, data}` wrapper, and
 * not `Worktree.Info`, which is only the create response.
 */
class WorktreeApi(private val client: EngineClient) {

    suspend fun list(projectID: String): ApiResult<List<WorktreeDirectory>> {
        // Audit-200 C16 (M-024): the project id is user-visible data — a
        // project folder with a space is ordinary — and the engine reads
        // query values as percent-encoded, so a form-encoded `+` arrives
        // as a literal plus and the project simply has no worktrees. The
        // shared encoder turns spaces into %20 (see FileApi.enc).
        val encoded = encodeQueryValue(projectID)
        val request = client.request("/api/worktree?projectID=$encoded").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<List<WorktreeDirectory>>(raw)
        }
    }
}
