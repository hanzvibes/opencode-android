package dev.bidzz.opencode.ui.files

import dev.bidzz.opencode.core.api.dto.FsEntry
import dev.bidzz.opencode.core.api.dto.FsListResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repository detection.
 *
 * This exists because the obvious approach does not work. `GET /api/vcs/status`
 * returns **200 with an empty array** both when the tree is clean and when the
 * directory is not a repository at all, so the status code cannot distinguish them.
 *
 * Verified against a live engine on a real git repository:
 *
 * ```
 * {"data":[{"path":".git/","type":"directory"},{"path":"src/","type":"directory"}, ...]}
 * ```
 *
 * Note the trailing slash on `.git/` — matching on `.git` alone would miss it.
 */
class RepositoryDetectionTest {

    /** Same settings the client uses; kept local because ApiJson is module-internal. */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Mirrors `FileApi.hasRepository`: a `.git` directory in the listing,
     * or a `.git` file whose content is a `gitdir:` pointer (worktree /
     * submodule). The file content is the engine's `/api/fs/read` answer
     * for `.git`, passed in because the listing alone cannot see it.
     */
    private fun hasRepository(entries: List<FsEntry>, dotGitContent: String? = null): Boolean {
        val dotGit = entries.firstOrNull { it.path.trimEnd('/') == ".git" } ?: return false
        if (dotGit.isDirectory) return true
        return dotGitContent?.trimStart()?.startsWith("gitdir:") == true
    }

    @Test
    fun `a git repository is detected from its dot-git entry`() {
        // Verbatim shape from the engine.
        val raw = """
            {"location":{"directory":"/storage/emulated/0/Projects/demo2"},
             "data":[{"path":".git/","type":"directory"},
                     {"path":"src/","type":"directory"},
                     {"path":"main.py","type":"file"},
                     {"path":"README.md","type":"file"}]}
        """.trimIndent()

        val entries = json.decodeFromString<FsListResponse>(raw).data
        assertTrue(hasRepository(entries))
    }

    @Test
    fun `a plain directory is not a repository`() {
        val raw = """
            {"data":[{"path":"src/","type":"directory"},
                     {"path":"main.py","type":"file"}]}
        """.trimIndent()

        val entries = json.decodeFromString<FsListResponse>(raw).data
        assertFalse(hasRepository(entries))
    }

    @Test
    fun `M-011 a gitdir pointer file is a repository`() {
        // `.git` as a file is how a worktree or submodule pointer is
        // stored — the engine's vcs.status/diff answer for it either way.
        val entries = listOf(FsEntry(path = ".git", type = "file"))
        assertTrue(hasRepository(entries, "gitdir: /storage/.git/worktrees/wt\n"))
    }

    @Test
    fun `M-011 a dot-git file without gitdir content is not a repository`() {
        val entries = listOf(FsEntry(path = ".git", type = "file"))
        assertFalse(hasRepository(entries, "not a pointer"))
        assertFalse(hasRepository(entries, null))
    }

    @Test
    fun `empty listing is not a repository`() {
        assertFalse(hasRepository(emptyList()))
    }

    @Test
    fun `directories carry a trailing slash and names strip it`() {
        // The explorer displays `name`, so a trailing slash would show as "src/".
        val entry = FsEntry(path = "src/", type = "directory")
        assertTrue(entry.isDirectory)
        assertTrue(entry.name == "src")

        val nested = FsEntry(path = "a/b/c.js", type = "file")
        assertFalse(nested.isDirectory)
        assertTrue(nested.name == "c.js")
    }
}
