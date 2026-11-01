package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.ModelDefaultResponse
import dev.bidzz.opencode.core.api.dto.ModelInfo
import dev.bidzz.opencode.core.api.dto.ModelsResponse

/** Model and provider discovery. */
class ModelApi(private val client: EngineClient) {

    /**
     * Lists models the server can run.
     *
     * The response carries a `location` for the engine's *working directory*, not
     * for the session, so it is not a reliable indicator of which project a model
     * list belongs to. Callers should treat the list as server-wide.
     */
    suspend fun list(): ApiResult<List<ModelInfo>> {
        val request = client.request("/api/model").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<ModelsResponse>(raw).data
        }
    }

    /**
     * The model a session starts on when nobody chooses one.
     *
     * `data` is null when the engine has no default, which is a valid answer rather than
     * an error — so this returns `Success(null)` in that case.
     */
    suspend fun default(): ApiResult<ModelInfo?> {
        val request = client.request("/api/model/default").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<ModelDefaultResponse>(raw).data
        }
    }
}
