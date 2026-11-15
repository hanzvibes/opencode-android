package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.CredentialCreateRequest
import dev.bidzz.opencode.core.api.dto.CredentialEntry
import dev.bidzz.opencode.core.api.dto.IntegrationInfo
import dev.bidzz.opencode.core.api.dto.KeyValue
import dev.bidzz.opencode.core.api.dto.OAuthAttempt
import dev.bidzz.opencode.core.api.dto.OAuthAttemptStatus
import dev.bidzz.opencode.core.api.dto.SingleResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.net.URLEncoder

/** Providers the engine can talk to, and which of them are already connected. */
class IntegrationApi(private val client: EngineClient) {

    suspend fun list(): ApiResult<List<IntegrationInfo>> {
        val request = client.request("/api/integration").get().build()
        return client.execute(request) { raw ->
            client.decodeDataList<IntegrationInfo>(raw)
        }
    }

    /**
     * Starts an OAuth authorization attempt for [integrationID].
     *
     * The engine needs the OAuth method id (`Integration.OAuthMethod.id`
     * from [IntegrationInfo.methods]) — not the integration id alone.
     * Answers `Integration.Attempt` (`{data: {attemptID, url,
     * instructions, mode, paste, time}}`), verified against the engine
     * fork at pin fedc6f20 (`packages/protocol/src/groups/integration.ts`,
     * `packages/schema/src/integration.ts`).
     */
    suspend fun beginOAuth(integrationID: String, methodID: String): ApiResult<OAuthAttempt> {
        val body = ApiJson.encodeToString(BeginOAuthRequest(methodID = methodID))
        val request = client.request("/api/integration/${encode(integrationID)}/connect/oauth")
            .post(client.jsonBody(body))
            .build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SingleResponse<OAuthAttempt>>(raw).data
        }
    }

    /**
     * Polls one OAuth attempt (`Integration.AttemptStatus`: pending /
     * complete / failed / expired).
     */
    suspend fun getOAuthStatus(integrationID: String, attemptID: String): ApiResult<OAuthAttemptStatus> {
        val request = client.request(
            "/api/integration/${encode(integrationID)}/connect/oauth/${encode(attemptID)}",
        ).get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SingleResponse<OAuthAttemptStatus>>(raw).data
        }
    }

    /**
     * Completes a code-based OAuth attempt, storing the credential.
     *
     * [code] is the code the provider showed in the browser — or, for an
     * "auto" attempt with `paste`, the redirect URL copied from the
     * browser. Null for a plain "auto" attempt, where the result arrives
     * through the attempt status alone. Answers 204 No Content.
     */
    suspend fun completeOAuth(
        integrationID: String,
        attemptID: String,
        code: String? = null,
    ): ApiResult<Unit> {
        val body = ApiJson.encodeToString(CompleteOAuthRequest(code = code))
        val request = client.request(
            "/api/integration/${encode(integrationID)}/connect/oauth/${encode(attemptID)}/complete",
        ).post(client.jsonBody(body)).build()
        return client.executeUnit(request)
    }

    /**
     * Cancels an OAuth attempt and releases its resources.
     * Answers 204 No Content. Best effort from the caller: a failed
     * cancel leaves the attempt to expire on its own.
     */
    suspend fun cancelOAuth(integrationID: String, attemptID: String): ApiResult<Unit> {
        val request = client.request(
            "/api/integration/${encode(integrationID)}/connect/oauth/${encode(attemptID)}",
        ).delete().build()
        return client.executeUnit(request)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

/** `POST /api/integration/{id}/connect/oauth` body. */
@Serializable
internal data class BeginOAuthRequest(val methodID: String)

/** `POST .../oauth/{attemptID}/complete` body; null code is omitted. */
@Serializable
internal data class CompleteOAuthRequest(val code: String? = null)

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
            client.decodeDataList<CredentialEntry>(raw)
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
