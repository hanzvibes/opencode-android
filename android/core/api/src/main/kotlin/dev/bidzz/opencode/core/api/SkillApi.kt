package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.SkillInfo
import dev.bidzz.opencode.core.api.dto.SkillsResponse

/**
 * Skills, as defined by the engine and any project `skills/` folder.
 *
 * Read-only: the app lists skills so the operator can find and mention
 * them. A pick sends the skill as a structured attachment with the
 * prompt (`PromptInput.SkillAttachment {id, mention}`, audit-200 C3,
 * M-067) — the engine resolves and activates it from the id. The
 * standalone activation endpoint (`POST
 * /api/experimental/session/{id}/skill`) is deliberately not called:
 * the prompt attachment is the activation.
 */
class SkillApi(private val client: EngineClient) {

    suspend fun list(): ApiResult<List<SkillInfo>> {
        val request = client.request("/api/skill").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SkillsResponse>(raw).data
        }
    }
}
