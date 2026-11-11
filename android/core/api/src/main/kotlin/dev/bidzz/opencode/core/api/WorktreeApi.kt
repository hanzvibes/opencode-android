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
        val encoded = java.net.URLEncoder.encode(projectID, "UTF-8")
        val request = client.request("/api/worktree?projectID=$encoded").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<List<WorktreeDirectory>>(raw)
        }
    }
}
