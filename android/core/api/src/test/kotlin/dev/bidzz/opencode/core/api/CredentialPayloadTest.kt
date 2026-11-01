package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.CredentialCreateRequest
import dev.bidzz.opencode.core.api.dto.CredentialEntry
import dev.bidzz.opencode.core.api.dto.CredentialsResponse
import dev.bidzz.opencode.core.api.dto.KeyValue
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Credential request and response shapes, against the contract in
 * `packages/protocol/openapi.json` at the pinned upstream commit.
 *
 * Two traps both bite only once a real request or response is handled: the tagged
 * `Credential.Value` union loses its discriminator when a default is encoded away, and
 * `Credential.Entry.value` carries the secret itself.
 */
class CredentialPayloadTest {

    private val listBody = """
        {"data":[{"id":"cred_17e4b2f0a9c3d8e5","integrationID":"302ai","label":"",
        "active":true,"value":{"type":"key","key":"sk-SUPERSECRET-never-keep-this"}}]}
    """.trimIndent()

    @Test
    fun `the fixture really carries the secret`() {
        // Control for the test below: without this, asserting the secret is absent
        // from the decoded object proves nothing, because it would also pass on a
        // fixture that never contained one.
        assertTrue(listBody.contains("sk-SUPERSECRET"))
    }

    @Test
    fun `decodes a credential list and drops the secret with it`() {
        val parsed = ApiJson.decodeFromString<CredentialsResponse>(listBody)
        val entry: CredentialEntry = parsed.data.single()
        // Decoding worked, so the absence below is a decision and not a parse failure.
        assertEquals("cred_17e4b2f0a9c3d8e5", entry.id)
        assertEquals("302ai", entry.integrationID)
        assertTrue(entry.active)

        // Re-encoding what was decoded is how the absence of `value` is observed
        // without reflection: if the DTO ever grows the field, the key comes back out.
        val roundTrip = ApiJson.encodeToString(parsed)
        assertFalse(roundTrip, roundTrip.contains("sk-SUPERSECRET"))
    }

    @Test
    fun `a stored key keeps the union discriminator`() {
        // `ApiJson` sets `encodeDefaults = false`. Giving `type` a default would drop it
        // from the body, and the engine rejects the payload with
        // `Expected Credential.Value` — a 400 that is invisible until the request runs.
        val body = ApiJson.encodeToString(
            CredentialCreateRequest(
                integrationID = "302ai",
                value = KeyValue(type = "key", key = "sk-example"),
                activate = true,
            ),
        )
        assertTrue(body, body.contains("\"type\":\"key\""))
        assertTrue(body, body.contains("\"key\":\"sk-example\""))
        assertTrue(body, body.contains("\"activate\":true"))
        assertTrue(body, body.contains("\"integrationID\":\"302ai\""))
    }

    @Test
    fun `an unset label is left out rather than sent as null`() {
        val body = ApiJson.encodeToString(
            CredentialCreateRequest(
                integrationID = "302ai",
                value = KeyValue(type = "key", key = "sk-example"),
                activate = true,
            ),
        )
        // `additionalProperties: false` on the engine's schema, so a stray field —
        // including an explicit null — is a rejected request, not an ignored one.
        assertFalse(body, body.contains("label"))
    }
}
