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

    /** Mirrors `FileApi.hasRepository`: a `.git` directory in the listing. */
    private fun hasRepository(entries: List<FsEntry>): Boolean =
        entries.any { it.path.trimEnd('/') == ".git" && it.isDirectory }

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
    fun `a file named dot-git is not a repository`() {
        // `.git` as a file is how a worktree or submodule pointer is stored; it is
        // not a repository the engine can diff.
        val entries = listOf(FsEntry(path = ".git", type = "file"))
        assertFalse(hasRepository(entries))
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
