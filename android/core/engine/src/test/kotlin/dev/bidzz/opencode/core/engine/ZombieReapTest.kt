package dev.bidzz.opencode.core.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The zombie reap (item 29) lives or dies by its /proc matching: a missed
 * orphan keeps holding a port, a false match kills a stranger. The scan is
 * therefore tested against a fake /proc tree; the kill itself is one
 * platform call plus the same wait loop `terminate()` already uses, and
 * the end-to-end (an orphan left by a killed app) needs a device.
 */
class ZombieReapTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val executable = "/data/data/dev.bidzz.opencode/lib/libopencode.so"

    private fun procEntry(pid: String, cmdline: String?): File {
        val dir = tmp.newFolder(pid)
        if (cmdline != null) File(dir, "cmdline").writeBytes(cmdline.toByteArray(Charsets.UTF_8))
        return dir
    }

    @Test
    fun `scan finds only the engine argv among proc entries`() {
        val procRoot = tmp.newFolder("proc")
        // The engine: exact binary path + "serve".
        procEntry("123", "$executable\u0000serve\u0000--port\u00001234\u0000")
        // Same binary, different argv: not a running engine.
        procEntry("124", "$executable\u0000--version\u0000")
        // "serve" from another binary: not ours.
        procEntry("125", "/system/bin/sh\u0000serve\u0000")
        // The engine, but it is us: never a candidate.
        procEntry("126", "$executable\u0000serve\u0000")
        // Non-numeric entries (kernel threads, "self") are skipped.
        procEntry("self", "$executable\u0000serve\u0000")
        // No cmdline at all (a process that just exited): skipped.
        procEntry("127", null)
        // Empty cmdline (a kernel thread): skipped, and must not crash the split.
        procEntry("128", "")

        val found = scanForEnginePids(executable, procRoot, ownPid = 126)

        assertEquals(listOf(123), found)
    }

    @Test
    fun `scan returns empty when proc is unreadable`() {
        val missing = File(tmp.root, "no-such-proc")
        assertTrue(scanForEnginePids(executable, missing, ownPid = -1).isEmpty())
    }

    @Test
    fun `scan returns empty when no engine runs`() {
        val procRoot = tmp.newFolder("proc")
        procEntry("200", "/system/bin/sh\u0000")
        assertTrue(scanForEnginePids(executable, procRoot, ownPid = -1).isEmpty())
    }
}
