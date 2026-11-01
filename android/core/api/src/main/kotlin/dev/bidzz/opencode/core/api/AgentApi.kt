package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.AgentInfo
import dev.bidzz.opencode.core.api.dto.AgentsResponse
import dev.bidzz.opencode.core.api.dto.SetAgentRequest
import kotlinx.serialization.encodeToString

/**
 * Agent (mode) endpoints.
 *
 * The CLI's "tab to switch mode" is this: pick a primary agent, bind it to the session.
 */
class AgentApi(private val client: EngineClient) {

    /** Lists every agent the engine knows about for its current location. */
    suspend fun list(): ApiResult<List<AgentInfo>> {
        val request = client.request("/api/agent").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<AgentsResponse>(raw).data
        }
    }

    /**
     * Binds [agentID] to a session. Takes the agent's **id**, not its display name.
     *
     * Answers 204; the change takes effect on the next prompt in that session.
     */
    suspend fun set(sessionID: String, agentID: String): ApiResult<Unit> {
        val body = ApiJson.encodeToString(SetAgentRequest(agent = agentID))
        val request = client.request("/api/session/$sessionID/agent")
            .post(client.jsonBody(body))
            .build()
        return client.executeUnit(request)
    }

    companion object {
        /** Agents the operator can actually choose. */
        fun selectable(agents: List<AgentInfo>): List<AgentInfo> =
            agents.filter { it.hidden != true && it.mode != "subagent" }
    }
}
