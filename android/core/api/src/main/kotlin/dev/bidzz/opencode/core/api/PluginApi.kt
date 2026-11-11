package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.PluginInfo
import dev.bidzz.opencode.core.api.dto.PluginsResponse

/**
 * Server plugins enabled on the engine.
 *
 * Read-only: the app lists plugins and their status. Checking for updates
 * and updating are writes (`POST /api/plugin/check`,
 * `POST /api/plugin/update`) the app deliberately does not call.
 */
class PluginApi(private val client: EngineClient) {

    suspend fun list(): ApiResult<List<PluginInfo>> {
        val request = client.request("/api/plugin").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<PluginsResponse>(raw).data
        }
    }
}
