package dev.bidzz.opencode.core.api.dto

import kotlinx.serialization.Serializable

/**
 * Artifacts and background jobs (mockup group 3, engine v2.2.7).
 *
 * Shapes verified against the engine fork source at pin fedc6f20
 * (`packages/protocol/src/groups/artifact.ts` + `job.ts`,
 * `packages/schema/src/artifact.ts` + `job.ts`,
 * `packages/server/src/handlers/artifact.ts` + `job.ts`):
 *
 * - Both groups live under `/api/experimental/` and are experimental:
 *   artifacts need `experimental.artifacts` in the Location config
 *   (default off in releases; a disabled engine answers 403
 *   `ForbiddenError`, mapped from `Artifact.DisabledError`).
 * - Artifact list/create/get/update all answer `{location, data}`;
 *   `ApiJson` ignores the unknown `location` key, so the DTOs below
 *   only model `data` (same as every other `{data}` envelope here).
 * - `Artifact.Info.url` is a **relative** view path:
 *   `/api/experimental/artifacts/{id}/view/` (core `viewURL`). The
 *   view endpoint 308-redirects to the entry file and serves the
 *   artifact's files with `content-security-policy: sandbox
 *   allow-scripts`. Prepend the engine base URL to open it.
 * - Job list answers plain `{data: Job.Info[]}` (no location wrapper)
 *   with optional `sessionID`, `kind`, `status` and `descendants`
 *   (`"true"`/`"false"` strings) query filters.
 * - `Job.Info` is a tagged union on `kind` (`shell` | `subagent` |
 *   `image`); like every engine union in this codebase it is modelled
 *   as one class with nullable kind-specific fields, never a sealed
 *   hierarchy (`ApiJson` has no `SerializersModule`).
 * - IDs: artifact IDs are `"art_" + Identifier.ascending()` (same
 *   scheme as `msg_…`); job IDs are plain strings.
 */
@Serializable
data class ArtifactProvider(
    val id: String,
    val name: String,
    val capabilities: List<String> = emptyList(),
    val default: Boolean = false,
)

@Serializable
data class ArtifactTime(
    val created: Long,
    val updated: Long,
)

@Serializable
data class ArtifactInfo(
    val id: String,
    val provider: String,
    val sessionID: String? = null,
    val title: String,
    val entry: String,
    val url: String,
    val time: ArtifactTime,
)

@Serializable
data class ArtifactContent(
    val info: ArtifactInfo,
    val files: Map<String, String> = emptyMap(),
)

@Serializable
data class ArtifactCreateInput(
    val title: String,
    val files: Map<String, String>,
    val entry: String? = null,
    val provider: String? = null,
    val sessionID: String? = null,
)

@Serializable
data class ArtifactUpdateInput(
    val title: String? = null,
    val files: Map<String, String>,
    val entry: String? = null,
    val sessionID: String? = null,
)

@Serializable
data class ArtifactProvidersResponse(
    val data: List<ArtifactProvider> = emptyList(),
)

@Serializable
data class ArtifactsResponse(
    val data: List<ArtifactInfo> = emptyList(),
)

@Serializable
data class JobTime(
    val started: Long,
    val completed: Long? = null,
)

/**
 * One background job. `kind` is `"shell"`, `"subagent"` or `"image"`;
 * only the fields of that kind are present:
 * - shell: [shellID] + [command]
 * - subagent: [childSessionID] + [agent] + [description]
 * - image: [path] + [model]
 */
@Serializable
data class JobInfo(
    val id: String,
    val sessionID: String,
    val status: String,
    val title: String? = null,
    val time: JobTime,
    val kind: String,
    val shellID: String? = null,
    val command: String? = null,
    val childSessionID: String? = null,
    val agent: String? = null,
    val description: String? = null,
    val path: String? = null,
    val model: String? = null,
) {
    /** The line the list row shows under the kind: command, description, path, or the id. */
    fun detailLine(): String = title ?: command ?: description ?: path ?: id
}

@Serializable
data class JobsResponse(
    val data: List<JobInfo> = emptyList(),
)
