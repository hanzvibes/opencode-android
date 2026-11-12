package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.SessionStatsInfo
import dev.bidzz.opencode.core.api.dto.SessionStatsResponse

/**
 * Aggregate session statistics.
 *
 * Read-only: the app shows the engine's totals for its default range.
 * The endpoint is experimental (`/api/experimental/session/stats`); a
 * failure hides the screen's content behind the empty state instead of
 * crashing, because an experimental endpoint may change shape.
 *
 * Shape verified against the engine fork source at pin 92b2ca0
 * (`packages/protocol/src/groups/session.ts`,
 * `packages/schema/src/session-stats.ts`): `{data: SessionStats.Info}`
 * (see `SessionStatsInfo`).
 */
class StatsApi(private val client: EngineClient) {

    suspend fun stats(): ApiResult<SessionStatsInfo> {
        val request = client.request("/api/experimental/session/stats").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SessionStatsResponse>(raw).data
        }
    }
}
