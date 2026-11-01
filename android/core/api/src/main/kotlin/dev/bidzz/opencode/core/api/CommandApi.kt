package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.CommandInfo
import dev.bidzz.opencode.core.api.dto.CommandsResponse
import dev.bidzz.opencode.core.api.dto.ProviderInfo
import dev.bidzz.opencode.core.api.dto.ProvidersResponse
import dev.bidzz.opencode.core.api.dto.RunCommandRequest
import kotlinx.serialization.encodeToString

/** Slash commands, as defined by the engine's config and any project `commands/` folder. */
class CommandApi(private val client: EngineClient) {

    suspend fun list(): ApiResult<List<CommandInfo>> {
        val request = client.request("/api/command").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<CommandsResponse>(raw).data
        }
    }

    /**
     * Runs a command in an existing session.
     *
     * [name] is the command without its leading slash, [text] the argument that followed
     * it. Answers **204 No Content**: like a prompt, admission is immediate and the output
     * arrives over the event stream.
     */
    suspend fun run(sessionID: String, name: String, text: String = ""): ApiResult<Unit> {
        val body = ApiJson.encodeToString(RunCommandRequest(name = name, text = text))
        val request = client.request("/api/session/$sessionID/command")
            .post(client.jsonBody(body))
            .build()
        return client.executeUnit(request)
    }
}

/** Model providers the engine has loaded. */
class ProviderApi(private val client: EngineClient) {

    suspend fun list(): ApiResult<List<ProviderInfo>> {
        val request = client.request("/api/provider").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<ProvidersResponse>(raw).data
        }
    }
}
