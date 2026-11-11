package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.ProjectInfo

/**
 * Projects known to the engine.
 *
 * Read-only: the app lists projects to find the one a workspace belongs
 * to. Renaming and re-pointing a project (`PATCH /api/project/{id}`) are
 * writes the app deliberately does not call.
 *
 * Note the shape: unlike the `{location, data}` list endpoints,
 * `GET /api/project` answers a **bare array** of `Project.Info`
 * (verified against the fork source at pin 92b2ca0,
 * `packages/protocol/src/groups/project.ts`), so the body is decoded as a
 * list directly, not through a wrapper.
 */
class ProjectApi(private val client: EngineClient) {

    suspend fun list(): ApiResult<List<ProjectInfo>> {
        val request = client.request("/api/project").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<List<ProjectInfo>>(raw)
        }
    }
}
