package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.McpServer
import dev.bidzz.opencode.core.api.dto.McpServersResponse

/**
 * MCP servers configured on the engine.
 *
 * Read-only: the app lists servers and their status. Adding, removing,
 * connecting and disconnecting are experimental endpoints
 * (`/api/experimental/mcp/...`) the app deliberately does not call.
 */
class McpApi(private val client: EngineClient) {

    suspend fun list(): ApiResult<List<McpServer>> {
        val request = client.request("/api/mcp").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<McpServersResponse>(raw).data
        }
    }
}
