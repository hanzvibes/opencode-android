package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.InstructionEntriesResponse
import dev.bidzz.opencode.core.api.dto.InstructionEntry
import dev.bidzz.opencode.core.api.dto.PutInstructionRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement

/**
 * Per-session instruction entries.
 *
 * The list is read-only; put and delete are the only writes in this
 * batch, and both answer 204. The endpoints are experimental
 * (`/api/experimental/session/{id}/instructions/entries`).
 *
 * Shapes verified against the engine fork source at pin 92b2ca0
 * (`packages/protocol/src/groups/session.ts`,
 * `packages/schema/src/instruction-entry.ts` — see
 * `InstructionEntry`).
 */
class InstructionsApi(private val client: EngineClient) {

    suspend fun list(sessionID: String): ApiResult<List<InstructionEntry>> {
        val request = client.request(
            "/api/experimental/session/$sessionID/instructions/entries",
        ).get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<InstructionEntriesResponse>(raw).data
        }
    }

    suspend fun put(sessionID: String, key: String, value: JsonElement): ApiResult<Unit> {
        val body = ApiJson.encodeToString(PutInstructionRequest(value = value))
        val request = client.request(
            "/api/experimental/session/$sessionID/instructions/entries/$key",
        ).put(client.jsonBody(body)).build()
        return client.executeUnit(request)
    }

    suspend fun delete(sessionID: String, key: String): ApiResult<Unit> {
        val request = client.request(
            "/api/experimental/session/$sessionID/instructions/entries/$key",
        ).delete().build()
        return client.executeUnit(request)
    }
}
