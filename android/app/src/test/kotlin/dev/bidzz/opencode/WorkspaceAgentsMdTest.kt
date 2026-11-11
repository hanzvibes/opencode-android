package dev.bidzz.opencode

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The seed must be invisible when the user already has a guide, and must
 * never write outside a real project directory: a storage root or a
 * half-written file would both be worse than no file at all.
 */
class WorkspaceAgentsMdTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val template = "line one\nline two\n".toByteArray()

    private fun stream(bytes: ByteArray = template) = ByteArrayInputStream(bytes)

    @Test
    fun `missing file is seeded byte-identical`() {
        val workspace = tempFolder.newFolder("project")
        assertTrue(WorkspaceAgentsMd.copyIfMissing(stream(), workspace))
        assertEquals("line one\nline two\n", File(workspace, "AGENTS.md").readText())
        assertFalse(File(workspace, "AGENTS.md.tmp").exists())
    }

    @Test
    fun `existing file is never touched`() {
        val workspace = tempFolder.newFolder("project")
        File(workspace, "AGENTS.md").writeText("mine\n")
        assertFalse(WorkspaceAgentsMd.copyIfMissing(stream(), workspace))
        assertEquals("mine\n", File(workspace, "AGENTS.md").readText())
    }

    @Test
    fun `empty existing file counts as existing`() {
        val workspace = tempFolder.newFolder("project")
        File(workspace, "AGENTS.md").writeText("")
        assertFalse(WorkspaceAgentsMd.copyIfMissing(stream(), workspace))
        assertEquals("", File(workspace, "AGENTS.md").readText())
    }

    @Test
    fun `storage root is refused`() {
        val root = File("/storage/emulated/0")
        assertFalse(WorkspaceAgentsMd.copyIfMissing(stream(), root))
        assertFalse(File(root, "AGENTS.md").exists())
    }

    @Test
    fun `a file instead of a directory fails cleanly`() {
        val notADir = tempFolder.newFile("not-a-dir")
        assertFalse(WorkspaceAgentsMd.copyIfMissing(stream(), notADir))
        assertFalse(File(notADir, "AGENTS.md").exists())
    }

    @Test
    fun `failing template leaves nothing behind`() {
        val workspace = tempFolder.newFolder("project")
        val broken = object : InputStream() {
            override fun read(): Int = throw java.io.IOException("boom")
        }
        assertFalse(WorkspaceAgentsMd.copyIfMissing(broken, workspace))
        assertFalse(File(workspace, "AGENTS.md").exists())
        assertFalse(File(workspace, "AGENTS.md.tmp").exists())
    }
}
