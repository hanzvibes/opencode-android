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
    fun `a file is not a workspace`() {
        val file = temp.newFile("notadir.txt")
        assertFalse(isAcceptable(file))
    }

    @Test
    fun `a missing directory is refused`() {
        assertFalse(isAcceptable(File(temp.root, "does-not-exist")))
    }

    /**
     * Mirrors `WorkspaceStore.isAcceptableWorkspace`, which cannot run on the JVM
     * because it reads SharedPreferences. Keeping the rule in one testable shape
     * means the policy is verified even though the store itself needs a device.
     *
     * Split on `File.separator` rather than `'/'`: on Windows a canonical path is
     * `C:\Users\...\project`, which contains no slash at all and would otherwise
     * count as depth 1 and be refused. The device is always POSIX, but the rule
     * has to be testable off-device to be worth anything.
     */
    private fun isAcceptable(dir: File): Boolean {
        if (!dir.isAbsolute || !dir.isDirectory || !dir.canWrite()) return false
        val path = dir.canonicalPath.trimEnd(File.separatorChar)
        if (path.isEmpty()) return false
        val depth = path.split(File.separatorChar).count { it.isNotEmpty() }
        return depth >= 2
    }
}
