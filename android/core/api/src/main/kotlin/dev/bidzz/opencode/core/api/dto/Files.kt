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

/**
 * Branch names for a project (`GET /api/vcs/branch`).
 *
 * The engine's `Vcs.BranchList` is a plain array of strings
 * (packages/schema/src/vcs.ts) — the `current`/`default` struct is
 * `Vcs.Info.branch`, not this list. The current branch is therefore matched
 * client-side against the branch the header already shows; no entry carries
 * a flag of its own.
 */
@Serializable
data class VcsBranchesResponse(
    val location: LocationRef? = null,
    val data: List<String> = emptyList(),
)

/**
 * Current and default branch for a project (`GET /api/vcs`).
 *
 * Shape verified against the engine fork source at pin 92b2ca0
 * (`packages/schema/src/vcs.ts`): `Vcs.Info` is
 * `{ provider?, branch: { current?, default? } }`. Either name may be
 * absent — before the first commit there is no current branch — so both
 * are nullable and the dialog hides a row it does not know.
 */
@Serializable
data class VcsBranch(
    val current: String? = null,
    val default: String? = null,
)

@Serializable
data class VcsInfo(
    val provider: String? = null,
    val branch: VcsBranch = VcsBranch(),
)

@Serializable
data class VcsInfoResponse(
    val location: LocationRef? = null,
    val data: VcsInfo = VcsInfo(),
)

/**
 * The review base for a project (`GET /api/vcs/base`).
 *
 * Shape verified against the engine fork source at pin 92b2ca0
 * (`packages/schema/src/vcs.ts`): `Vcs.Base` is
 * `{ name, ref, source: "reflog" | "default" }`, and the endpoint answers
 * it nullable — null before the first commit, or when the provider has no
 * base metadata. `source` says how the engine inferred it: from the
 * branch-creation history (`reflog`) or the repository default.
 */
@Serializable
data class VcsBase(
    val name: String,
    val ref: String = "",
    val source: String = "",
)

@Serializable
data class VcsBaseResponse(
    val location: LocationRef? = null,
    val data: VcsBase? = null,
)
