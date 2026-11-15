package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.SkillInfo
import dev.bidzz.opencode.core.api.dto.SkillsResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.net.URLEncoder

/**
 * Skills, as defined by the engine and any project `skills/` folder.
 *
 * Read-only, except for standalone activation: the app lists skills so
 * the operator can find and mention them. A pick sends the skill as a
 * structured attachment with the prompt (`PromptInput.SkillAttachment
 * {id, mention}`, audit-200 C3, M-067) — the engine resolves and
 * activates it from the id. The standalone endpoint ([activate],
 * audit-200 C17, M-259) activates a skill WITHOUT a prompt: it appends
 * a skill message and resumes execution, for skills that run on their
 * own. Both paths exist; the prompt attachment is for "run this skill
 * on my message", activation is for "run this skill now".
 */
class SkillApi(private val client: EngineClient) {

    suspend fun list(): ApiResult<List<SkillInfo>> {
        val request = client.request("/api/skill").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SkillsResponse>(raw).data
        }
    }

    /**
     * Activates a skill for a session (`POST
     * `/api/experimental/session/{id}/skill`, audit-200 C17, M-259):
     * appends a skill message and resumes execution. [resume] restarts
     * a skill that was already activated. Answers 204 No Content.
     * Verified against the engine fork at pin fedc6f20
     * (`packages/protocol/src/groups/session.ts`).
     */
    suspend fun activate(sessionID: String, skillID: String, resume: Boolean = false): ApiResult<Unit> {
        val body = ApiJson.encodeToString(ActivateSkillRequest(id = skillID, resume = resume))
        val request = client.request("/api/experimental/session/${encode(sessionID)}/skill")
            .post(client.jsonBody(body))
            .build()
        return client.executeUnit(request)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

/** `POST /api/experimental/session/{id}/skill` body. */
@Serializable
internal data class ActivateSkillRequest(
    val id: String,
    val resume: Boolean = false,
)
