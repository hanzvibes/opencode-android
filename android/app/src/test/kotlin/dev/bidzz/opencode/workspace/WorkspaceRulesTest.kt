package dev.bidzz.opencode.workspace

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The workspace rules are what stop the agent from being handed the filesystem
 * root. A session created with `directory: "/"` is accepted by the engine and the
 * `shell` tool then writes anywhere (see `handoff.md` §2.11), so the check has to
 * hold regardless of what the UI allows.
 */
class WorkspaceRulesTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a normal project directory is acceptable`() {
        val dir = temp.newFolder("project")
        assertTrue(isAcceptable(dir))
    }

    @Test
    fun `the filesystem root is refused`() {
        assertFalse(isAcceptable(File("/")))
    }

    @Test
    fun `a top-level volume is refused`() {
        // /storage and /sdcard are one step from the root and expose every project.
        assertFalse(isAcceptable(File("/storage")))
        assertFalse(isAcceptable(File("/sdcard")))
    }

    @Test
    fun `the primary storage root is refused`() {
        // Picking "Internal storage" in the folder picker maps to this path
        // (item 30): it used to pass the depth check and hand the agent the
        // whole shared storage.
        assertFalse(isAcceptableWorkspacePath("/storage/emulated/0"))
    }

    @Test
    fun `the emulated container is refused`() {
        assertFalse(isAcceptableWorkspacePath("/storage/emulated"))
    }

    @Test
    fun `an SD card root is refused`() {
        assertFalse(isAcceptableWorkspacePath("/storage/ABCD-1234"))
    }

    @Test
    fun `folders inside a storage root are acceptable`() {
        assertTrue(isAcceptableWorkspacePath("/storage/emulated/0/MyProject"))
        assertTrue(isAcceptableWorkspacePath("/storage/emulated/0/Download"))
        assertTrue(isAcceptableWorkspacePath("/storage/ABCD-1234/Project"))
    }

    @Test
    fun `a file is not a workspace`() {
        val file = temp.newFile("notadir.txt")
        assertFalse(isAcceptable(file))
    }

    @Test
    fun `a missing directory is refused`() {
        assertFalse(isAcceptable(File(temp.root, "does-not-exist")))
    }

    /**
     * The filesystem half of `WorkspaceStore.isAcceptableWorkspace`, which
     * cannot run on the JVM because the store reads SharedPreferences. The
     * path half is the real rule now ([isAcceptableWorkspacePath]), so only
     * the exists/writable checks are mirrored here.
     *
     * Split on `File.separator` rather than `'/'`: on Windows a canonical path is
     * `C:\Users\...\project`, which contains no slash at all and would otherwise
     * count as depth 1 and be refused. The device is always POSIX, but the rule
     * has to be testable off-device to be worth anything.
     */
    private fun isAcceptable(dir: File): Boolean {
        if (!dir.isAbsolute || !dir.isDirectory || !dir.canWrite()) return false
        val path = dir.canonicalPath.trimEnd(File.separatorChar)
        return isAcceptableWorkspacePath(path)
    }
}
