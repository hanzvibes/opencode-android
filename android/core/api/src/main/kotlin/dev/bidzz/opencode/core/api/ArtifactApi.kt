package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.ArtifactContent
import dev.bidzz.opencode.core.api.dto.ArtifactCreateInput
import dev.bidzz.opencode.core.api.dto.ArtifactInfo
import dev.bidzz.opencode.core.api.dto.ArtifactProvidersResponse
import dev.bidzz.opencode.core.api.dto.ArtifactUpdateInput
import dev.bidzz.opencode.core.api.dto.SingleResponse
import kotlinx.serialization.encodeToString
import java.net.URLEncoder

/**
 * Published artifacts (mockup group 3, engine v2.2.7).
 *
 * Every route is experimental and needs `experimental.artifacts` in the
 * Location config — a disabled engine answers 403 `ForbiddenError`
 * (see `Artifact.DisabledError` in the fork source at pin fedc6f20,
 * `packages/server/src/handlers/artifact.ts`). The app does not enable
 * the flag itself (`ConfigApi` is list-only); the Artifacts screen
 * explains the 403 instead of showing a bare error.
 *
 * - List is newest-update-first; filter by owning session or provider.
 * - Create publishes a new artifact through the selected (or default)
 *   provider; update replaces its files at the same ID and URL.
 * - The view endpoint serves the artifact's files; see [viewPath].
 */
class ArtifactApi(private val client: EngineClient) {

    suspend fun providers(): ApiResult<List<dev.bidzz.opencode.core.api.dto.ArtifactProvider>> {
        val request = client.request("/api/experimental/artifacts/providers").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<ArtifactProvidersResponse>(raw).data
        }
    }

    suspend fun list(sessionID: String? = null, provider: String? = null): ApiResult<List<ArtifactInfo>> {
        val query = buildString {
            append("/api/experimental/artifacts")
            var first = true
            fun param(name: String, value: String) {
                append(if (first) "?" else "&")
                first = false
                append(name).append("=").append(URLEncoder.encode(value, "UTF-8"))
            }
            if (sessionID != null) param("sessionID", sessionID)
            if (provider != null) param("provider", provider)
        }
        val request = client.request(query).get().build()
        // Audit-200 B1 (M-314): decode per entry, not the whole list at
        // once — one malformed artifact used to hide the entire list.
        return client.execute(request) { raw ->
            client.decodeDataList<ArtifactInfo>(raw)
        }
    }

    suspend fun create(input: ArtifactCreateInput): ApiResult<ArtifactInfo> {
        val body = ApiJson.encodeToString(input)
        val request = client.request("/api/experimental/artifacts")
            .post(client.jsonBody(body)).build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SingleResponse<ArtifactInfo>>(raw).data
        }
    }

    suspend fun get(id: String): ApiResult<ArtifactContent> {
        val request = client.request("/api/experimental/artifacts/${encode(id)}").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SingleResponse<ArtifactContent>>(raw).data
        }
    }

    suspend fun update(id: String, input: ArtifactUpdateInput): ApiResult<ArtifactInfo> {
        val body = ApiJson.encodeToString(input)
        val request = client.request("/api/experimental/artifacts/${encode(id)}")
            .patch(client.jsonBody(body)).build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SingleResponse<ArtifactInfo>>(raw).data
        }
    }

    suspend fun delete(id: String): ApiResult<Unit> {
        val request = client.request("/api/experimental/artifacts/${encode(id)}").delete().build()
        return client.executeUnit(request)
    }

    /**
     * Relative view path for an artifact (`Artifact.Info.url` has the
     * same value). Prepend the engine base URL to open it in a browser;
     * the view endpoint 308-redirects to the entry file. The browser
     * must send the engine credentials (Basic `opencode:<password>`),
     * like every other engine call.
     */
    fun viewPath(id: String): String =
        "/api/experimental/artifacts/${encode(id)}/view/"

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
