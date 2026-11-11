package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.SkillInfo
import dev.bidzz.opencode.core.api.dto.SkillsResponse

/**
 * Skills, as defined by the engine and any project `skills/` folder.
 *
 * Read-only: the app lists skills so the operator can find and mention
 * them. Activating a skill is `POST /api/experimental/session/{id}/skill`,
 * an experimental endpoint the app deliberately does not call.
 */
class SkillApi(private val client: EngineClient) {

    suspend fun list(): ApiResult<List<SkillInfo>> {
        val request = client.request("/api/skill").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SkillsResponse>(raw).data
        }
    }
}
