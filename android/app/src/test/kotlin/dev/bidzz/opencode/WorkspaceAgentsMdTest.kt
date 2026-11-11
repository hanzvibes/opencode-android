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
        assertTrue(workspace.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
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
        assertTrue(workspace.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    /**
     * The seed used to write through a fixed `AGENTS.md.tmp`, truncating a
     * user file of that name — or following a symlink of that name and
     * overwriting its target (audit-70 42-F1). The scratch file is now
     * uniquely named, so the user's file is not in the way at all.
     */
    @Test
    fun `a pre-existing tmp file is never touched`() {
        val workspace = tempFolder.newFolder("project")
        File(workspace, "AGENTS.md.tmp").writeText("mine\n")
        assertTrue(WorkspaceAgentsMd.copyIfMissing(stream(), workspace))
        assertEquals("line one\nline two\n", File(workspace, "AGENTS.md").readText())
        assertEquals("mine\n", File(workspace, "AGENTS.md.tmp").readText())
    }

    @Test
    fun `a tmp symlink is never followed`() {
        val workspace = tempFolder.newFolder("project")
        val victim = File(workspace, "victim.txt").apply { writeText("do not touch\n") }
        java.nio.file.Files.createSymbolicLink(
            File(workspace, "AGENTS.md.tmp").toPath(),
            victim.toPath(),
        )
        assertTrue(WorkspaceAgentsMd.copyIfMissing(stream(), workspace))
        assertEquals("line one\nline two\n", File(workspace, "AGENTS.md").readText())
        assertEquals("do not touch\n", victim.readText())
        assertTrue(java.nio.file.Files.isSymbolicLink(File(workspace, "AGENTS.md.tmp").toPath()))
    }

    /**
     * `File.exists()` answers false for a dangling symlink, so the seed
     * used to replace the user's link with a regular file (audit-70 42-F2).
     */
    @Test
    fun `a dangling target symlink is preserved`() {
        val workspace = tempFolder.newFolder("project")
        val link = File(workspace, "AGENTS.md")
        java.nio.file.Files.createSymbolicLink(link.toPath(), File(workspace, "elsewhere.md").toPath())
        assertFalse(WorkspaceAgentsMd.copyIfMissing(stream(), workspace))
        assertTrue(java.nio.file.Files.isSymbolicLink(link.toPath()))
        assertFalse(link.exists())
    }

    /**
     * A file that appears while the template is being copied — the user,
     * a sync, another process — wins over the seed instead of being
     * replaced by the rename (audit-70 42-F3).
     */
    @Test
    fun `a target created mid-copy wins`() {
        val workspace = tempFolder.newFolder("project")
        val racing = object : InputStream() {
            private var created = false
            override fun read(): Int {
                if (!created) {
                    created = true
                    File(workspace, "AGENTS.md").writeText("user won\n")
                }
                return -1
            }
        }
        assertFalse(WorkspaceAgentsMd.copyIfMissing(racing, workspace))
        assertEquals("user won\n", File(workspace, "AGENTS.md").readText())
    }
}
