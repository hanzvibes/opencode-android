package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.McpAddRequest
import dev.bidzz.opencode.core.api.dto.McpResourceCatalog
import dev.bidzz.opencode.core.api.dto.McpResourceCatalogResponse
import dev.bidzz.opencode.core.api.dto.McpServer
import dev.bidzz.opencode.core.api.dto.McpServerConfig
import dev.bidzz.opencode.core.api.dto.McpServersResponse
import kotlinx.serialization.encodeToString
import java.net.URLEncoder

/**
 * MCP servers configured on the engine.
 *
 * Listing is `GET /api/mcp`. The writes are the experimental endpoints
 * (`/api/experimental/mcp/...`) — shapes verified against the engine fork
 * source at pin 92b2ca0 (`packages/protocol/src/groups/mcp.ts`,
 * `packages/schema/src/mcp.ts`):
 *
 * - Add is runtime-only: it adds "at runtime or replaces an existing one,
 *   connecting it immediately", and a removed server is gone "until
 *   restart". Nothing here touches the engine's config file.
 * - Add / remove / connect / disconnect all answer **204 No Content**.
 * - Connect overrides a disabled configuration until restart; disconnect
 *   removes the server's tools until reconnected.
 * - The resource catalog (`GET /api/mcp/resource`) has no per-server
 *   filter — it is the whole catalog at once.
 */
class McpApi(private val client: EngineClient) {

    suspend fun list(): ApiResult<List<McpServer>> {
        val request = client.request("/api/mcp").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<McpServersResponse>(raw).data
        }
    }

    /**
     * Adds a server at runtime (or replaces one with the same name) and
     * connects it immediately. `config` carries the whole server definition;
     * see [McpServerConfig] for the local/remote shapes.
     */
    suspend fun add(server: String, config: McpServerConfig): ApiResult<Unit> {
        val body = ApiJson.encodeToString(McpAddRequest(config = config))
        val request = client.request("/api/experimental/mcp/${encode(server)}")
            .put(client.jsonBody(body))
            .build()
        return client.executeUnit(request)
    }

    /** Stops a server and removes it from the runtime set until restart. */
    suspend fun remove(server: String): ApiResult<Unit> {
        val request = client.request("/api/experimental/mcp/${encode(server)}")
            .delete()
            .build()
        return client.executeUnit(request)
    }

    /** Connects a server at runtime, overriding a disabled configuration until restart. */
    suspend fun connect(server: String): ApiResult<Unit> {
        val request = client.request("/api/experimental/mcp/${encode(server)}/connect")
            .post(client.emptyBody())
            .build()
        return client.executeUnit(request)
    }

    /** Disconnects a server at runtime, removing its tools until reconnected. */
    suspend fun disconnect(server: String): ApiResult<Unit> {
        val request = client.request("/api/experimental/mcp/${encode(server)}/disconnect")
            .post(client.emptyBody())
            .build()
        return client.executeUnit(request)
    }

    /** Resources and resource templates from every connected MCP server. */
    suspend fun resourceCatalog(): ApiResult<McpResourceCatalog> {
        val request = client.request("/api/mcp/resource").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<McpResourceCatalogResponse>(raw).data
        }
    }

    /**
     * A server name as one path segment. `URLEncoder` is form-encoding,
     * which writes a space as `+`; the engine reads path segments, so `+`
     * becomes `%20` first — the same rule as [FileApi].
     */
    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
