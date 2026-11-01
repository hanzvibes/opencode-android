package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.CredentialCreateRequest
import dev.bidzz.opencode.core.api.dto.CredentialEntry
import dev.bidzz.opencode.core.api.dto.CredentialsResponse
import dev.bidzz.opencode.core.api.dto.IntegrationInfo
import dev.bidzz.opencode.core.api.dto.IntegrationsResponse
import dev.bidzz.opencode.core.api.dto.KeyValue
import dev.bidzz.opencode.core.api.dto.SingleResponse
import kotlinx.serialization.encodeToString

/** Providers the engine can talk to, and which of them are already connected. */
class IntegrationApi(private val client: EngineClient) {

    suspend fun list(): ApiResult<List<IntegrationInfo>> {
        val request = client.request("/api/integration").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<IntegrationsResponse>(raw).data
        }
    }
}

/**
 * Stored credentials.
 *
 * The engine keeps these in its own database, not in a file the app could write —
 * `auth.json` is legacy and the migration that imports it has already run on any
 * existing install, so writing it would silently do nothing.
 */
class CredentialApi(private val client: EngineClient) {

    /** Lists credentials. The secret itself is never decoded; see [CredentialEntry]. */
    suspend fun list(): ApiResult<List<CredentialEntry>> {
        val request = client.request("/api/credential").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<CredentialsResponse>(raw).data
        }
    }

    /**
     * Stores an API key for [integrationID] and makes it active.
     *
     * [activate] matters: a provider can hold several keys and only the active one is
     * used, so saving without activating leaves the operator with a key that appears
     * to do nothing.
     */
    suspend fun createKey(integrationID: String, key: String, label: String? = null): ApiResult<CredentialEntry> {
        val body = ApiJson.encodeToString(
            CredentialCreateRequest(
                integrationID = integrationID,
                value = KeyValue(type = "key", key = key),
                label = label,
                activate = true,
            ),
        )
        val request = client.request("/api/credential")
            .post(client.jsonBody(body))
            .build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SingleResponse<CredentialEntry>>(raw).data
        }
    }

    suspend fun activate(credentialID: String): ApiResult<Unit> {
        val request = client.request("/api/credential/$credentialID/activate")
            .post(client.emptyBody())
            .build()
        return client.executeUnit(request)
    }

    suspend fun remove(credentialID: String): ApiResult<Unit> {
        val request = client.request("/api/credential/$credentialID").delete().build()
        return client.executeUnit(request)
    }
}
