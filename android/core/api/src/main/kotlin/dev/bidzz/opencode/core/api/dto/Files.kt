package dev.bidzz.opencode.core.api.dto

import kotlinx.serialization.Serializable

/**
 * Filesystem and VCS wire types.
 *
 * Shapes verified against a live engine, not inferred from the spec alone:
 *
 * - `GET /api/fs/list` returns entries whose `path` is relative to the requested
 *   location, and directories carry a **trailing slash** (`"src/"`).
 * - `GET /api/fs/read/<path>` returns `application/octet-stream`, raw bytes, not
 *   JSON and not text. The client must decode explicitly.
 * - `GET /api/vcs/status` and `/api/vcs/diff` return an empty array when the
 *   directory is not a git repository — they do not error.
 */

/** One directory entry. [path] is relative to the listed directory. */
@Serializable
data class FsEntry(
    val path: String,
    val type: String,
) {
    val isDirectory: Boolean get() = type == "directory"

    /** Display name, without the trailing slash directories carry. */
    val name: String get() = path.trimEnd('/').substringAfterLast('/')
}

@Serializable
data class FsListResponse(
    val location: LocationRef? = null,
    val data: List<FsEntry> = emptyList(),
)

/** One changed file, with counts but no patch text. */
@Serializable
data class VcsFileStatus(
    val file: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    /** "added", "deleted" or "modified". */
    val status: String,
)

@Serializable
data class VcsStatusResponse(
    val location: LocationRef? = null,
    val data: List<VcsFileStatus> = emptyList(),
)

/**
 * One changed file with its unified diff.
 *
 * [patch] is the raw unified diff text. It is parsed for display rather than
 * rendered as-is, because colouring requires knowing which lines are additions.
 */
@Serializable
data class FileDiffInfo(
    val file: String,
    val patch: String = "",
    val additions: Int = 0,
    val deletions: Int = 0,
    val status: String,
    /** Present and true when the engine truncated the patch. */
    val more: Boolean? = null,
)

@Serializable
data class VcsDiffResponse(
    val location: LocationRef? = null,
    val data: List<FileDiffInfo> = emptyList(),
)
