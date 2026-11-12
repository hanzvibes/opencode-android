package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.UsageLimitsInfo
import dev.bidzz.opencode.core.api.dto.UsageLimitsResponse

/**
 * Usage limits for the Console workspace the engine is signed in to.
 *
 * Read-only. A null result is normal: the engine answers `data: null`
 * when the location is not signed in to an OpenCode Console workspace
 * (see `UsageLimitsResponse`), so callers show the empty state instead
 * of an error. The endpoint is experimental
 * (`/api/experimental/usage/limits`).
 *
 * Shape verified against the engine fork source at pin 92b2ca0
 * (`packages/protocol/src/groups/usage.ts`,
 * `packages/schema/src/usage-limits.ts`).
 */
class UsageApi(private val client: EngineClient) {

    suspend fun limits(): ApiResult<UsageLimitsInfo?> {
        val request = client.request("/api/experimental/usage/limits").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<UsageLimitsResponse>(raw).data
        }
    }
}
