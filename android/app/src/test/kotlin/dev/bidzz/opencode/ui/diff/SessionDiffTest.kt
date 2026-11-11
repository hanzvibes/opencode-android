package dev.bidzz.opencode.ui.diff

import dev.bidzz.opencode.core.api.dto.FileDiffInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Turn-diff list rows.
 *
 * The session-diff list reuses the working-tree list, so each session file
 * becomes the same `VcsFileStatus` row — the counts and status the list
 * shows must be the ones the engine reported for that file, not defaults.
 */
class SessionDiffTest {

    @Test
    fun `session file becomes a list row with its own counts`() {
        val row = FileDiffInfo(
            file = "src/main.kt",
            patch = "@@ -1,2 +1,3 @@\n context\n-old\n+new\n",
            additions = 2,
            deletions = 1,
            status = "modified",
            more = true,
        ).toVcsFileStatus()

        assertEquals("src/main.kt", row.file)
        assertEquals(2, row.additions)
        assertEquals(1, row.deletions)
        assertEquals("modified", row.status)
    }

    @Test
    fun `added file keeps a zero count instead of a default`() {
        val row = FileDiffInfo(
            file = "README.md",
            patch = "",
            additions = 0,
            deletions = 0,
            status = "added",
        ).toVcsFileStatus()

        assertEquals(0, row.additions)
        assertEquals(0, row.deletions)
        assertEquals("added", row.status)
    }
}
