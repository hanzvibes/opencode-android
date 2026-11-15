package dev.bidzz.opencode.core.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A location-scoped shell command (`POST /api/shell`, `GET /api/shell/{id}`).
 *
 * Shape verified against the engine fork source at pin fedc6f20
 * (`packages/schema/src/shell.ts`, `packages/protocol/src/groups/shell.ts`,
 * `packages/server/src/handlers/shell.ts`): a non-interactive command
 * whose combined stdout/stderr is captured to a file, pageable via
 * `GET /api/shell/{id}/output`. `time` and `metadata` are server fields
 * the client does not read, so they are not modelled here.
 */
@Serializable
data class ShellInfo(
    val id: String,
    /** "running" | "exited" | "timeout" | "killed". */
    val status: String,
    val command: String,
    val cwd: String,
    val shell: String,
    /** Absolute path of the file capturing combined stdout/stderr. */
    val file: String,
    val pid: Int? = null,
    val exit: Int? = null,
    val signal: String? = null,
) {
    val isTerminal: Boolean get() = status != "running"
}

/**
 * One page of a shell's captured output (`GET /api/shell/{id}/output`).
 *
 * `cursor` is absolute: a consumer has more to page while
 * `cursor < size`. `truncated` is the server's own flag for the same.
 */
@Serializable
data class ShellOutput(
    val output: String,
    val cursor: Long,
    val size: Long,
    val truncated: Boolean = false,
)

/** Body of `POST /api/shell`. `cwd` defaults server-side to the project directory. */
@Serializable
data class ShellCreateRequest(
    val command: String,
    val cwd: String? = null,
    val timeout: Long? = null,
    val metadata: Map<String, JsonElement>? = null,
)
