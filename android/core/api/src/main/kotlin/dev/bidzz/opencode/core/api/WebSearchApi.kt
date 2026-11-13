package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.WebSearchInput
import dev.bidzz.opencode.core.api.dto.WebSearchProvider
import dev.bidzz.opencode.core.api.dto.WebSearchProvidersResponse
import dev.bidzz.opencode.core.api.dto.WebSearchResponse
import kotlinx.serialization.encodeToString

/**
 * Direct web search (mockup group 5, engine v2.2.7).
 *
 * Shapes verified against the fork source at pin fedc6f20
 * (`packages/protocol/src/groups/websearch.ts`,
 * `packages/schema/src/websearch.ts`):
 *
 * - Providers: `GET /api/websearch/provider` → `{location, data:
 *   [{id, name}]}`.
 * - Query: `POST /api/websearch` with `{query, providerID?}` →
 *   `{location, data: {providerID, results: [{url, title?,
 *   content?, time: {published?}}]}}`. Without `providerID` the
 *   engine uses the configured default provider.
 * - A provider that cannot serve the query answers
 *   `ServiceUnavailableError`; a bad request answers
 *   `InvalidRequestError`.
 */
class WebSearchApi(private val client: EngineClient) {

    suspend fun providers(): ApiResult<List<WebSearchProvider>> {
        val request = client.request("/api/websearch/provider").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<WebSearchProvidersResponse>(raw).data
        }
    }

    suspend fun query(query: String, providerID: String? = null): ApiResult<WebSearchResponse> {
        val body = ApiJson.encodeToString(WebSearchInput(query = query, providerID = providerID))
        val request = client.request("/api/websearch")
            .post(client.jsonBody(body)).build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<dev.bidzz.opencode.core.api.dto.SingleResponse<WebSearchResponse>>(raw).data
        }
    }
}
