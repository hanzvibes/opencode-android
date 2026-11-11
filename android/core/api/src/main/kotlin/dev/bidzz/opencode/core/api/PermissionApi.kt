package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.PermissionSavedInfo
import dev.bidzz.opencode.core.api.dto.SavedPermissionsResponse

/**
 * Permissions saved with an "Always" answer.
 *
 * Answering `always` to a permission prompt writes one of these on the
 * engine; the same request shape is then answered automatically until the
 * entry is revoked here.
 */
class PermissionApi(private val client: EngineClient) {

    /** All saved permissions, across projects. */
    suspend fun listSaved(): ApiResult<List<PermissionSavedInfo>> {
        val request = client.request("/api/permission/saved").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SavedPermissionsResponse>(raw).data
        }
    }

    /**
     * Revokes one saved permission, so the engine asks again next time.
     * Answers **204 No Content**.
     */
    suspend fun deleteSaved(id: String): ApiResult<Unit> {
        val request = client.request("/api/permission/saved/$id").delete().build()
        return client.executeUnit(request)
    }
}
