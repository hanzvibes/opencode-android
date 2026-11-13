package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.PluginCheckRequest
import dev.bidzz.opencode.core.api.dto.PluginInfo
import dev.bidzz.opencode.core.api.dto.PluginUpdateRequest
import dev.bidzz.opencode.core.api.dto.PluginsResponse
import kotlinx.serialization.encodeToString

/**
 * Server plugins enabled on the engine.
 *
 * Listing is `GET /api/plugin`. The writes are `POST /api/plugin/check`
 * and `POST /api/plugin/update` — shapes verified against the engine fork
 * source at pin 92b2ca0 (`packages/protocol/src/groups/plugin.ts`,
 * `packages/server/src/handlers/plugin.ts`):
 *
 * - Check takes an optional `target` (one package target); omitted it
 *   checks every package plugin. It answers the **full inventory** with
 *   `outdated: true` stamped onto the package sources that have an
 *   update — the caller replaces its list with the answer, it does not
 *   merge. A target that is not in the inventory fails with
 *   `InvalidRequestError`.
 * - Update takes `targets` (package targets from the inventory) and
 *   answers **204 No Content** once every update has finished. It runs
 *   the updates concurrently and lets each run to completion; an unknown
 *   target fails with `InvalidRequestError`, and any update failure fails
 *   with `ServiceUnavailableError` after the rest have finished.
 * - Only `package` sources can be checked or updated. Built-in, local
 *   and sdk plugins never carry `outdated`, so the UI never offers them
 *   an update.
 */
class PluginApi(private val client: EngineClient) {

    suspend fun list(): ApiResult<List<PluginInfo>> {
        val request = client.request("/api/plugin").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<PluginsResponse>(raw).data
        }
    }

    /**
     * Checks one package plugin for updates (`target`), or every package
     * plugin when `target` is null. Returns the full inventory with the
     * `outdated` flags stamped in.
     */
    suspend fun check(target: String? = null): ApiResult<List<PluginInfo>> {
        val body = ApiJson.encodeToString(PluginCheckRequest(target = target))
        val request = client.request("/api/plugin/check")
            .post(client.jsonBody(body))
            .build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<PluginsResponse>(raw).data
        }
    }

    /**
     * Updates package plugins by target. Answers 204 once every update
     * has finished; see the class comment for the failure rules.
     */
    suspend fun update(targets: List<String>): ApiResult<Unit> {
        val body = ApiJson.encodeToString(PluginUpdateRequest(targets = targets))
        val request = client.request("/api/plugin/update")
            .post(client.jsonBody(body))
            .build()
        return client.executeUnit(request)
    }
}
