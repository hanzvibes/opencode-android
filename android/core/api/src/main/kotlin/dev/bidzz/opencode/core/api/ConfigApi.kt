package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.ConfigEntry

/**
 * Engine configuration documents.
 *
 * Read-only: the app lists the configuration documents (and search
 * directories) the engine resolved, lowest to highest priority, with the
 * key settings of each. Editing is `PATCH /api/experimental/config`, an
 * experimental endpoint the app deliberately does not call.
 *
 * Note the shape: `GET /api/config` answers a **bare array** of
 * `Config.Entry` (verified against the fork source at pin 92b2ca0,
 * `packages/protocol/src/groups/config.ts`,
 * `packages/schema/src/config.ts`), not the `{location, data}` wrapper.
 * An entry is either a `document` (a config file with its parsed `info`)
 * or a `directory` (a searched path with no document).
 */
class ConfigApi(private val client: EngineClient) {

    suspend fun list(): ApiResult<List<ConfigEntry>> {
        val request = client.request("/api/config").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<List<ConfigEntry>>(raw)
        }
    }
}
