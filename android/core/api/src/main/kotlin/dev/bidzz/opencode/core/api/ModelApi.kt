package dev.bidzz.opencode.core.api

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
}
