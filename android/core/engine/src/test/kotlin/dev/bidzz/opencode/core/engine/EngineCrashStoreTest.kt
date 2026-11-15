package dev.bidzz.opencode.core.engine

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The supervisor's crash record (audit-200 C13, M-180): an unexpected
 * engine exit used to live only in logcat and in the in-memory
 * `lastError` — restart the app and the exit code, the time and the last
 * output were gone. The record is one small file under the engine root,
 * overwritten on each crash.
 */
class EngineCrashStoreTest {

    private fun root(): File = Files.createTempDirectory("enginecrash").toFile()

    @Test
    fun `save and read round-trip the report`() {
        val root = root()
        val report = EngineCrashReport(
            timestampMs = 1_700_000_000_000L,
            exitCode = 1,
            error = "Engine exited unexpectedly (code 1)",
            outputLines = listOf("starting opencode", "listening on 4096"),
        )

        EngineCrashStore.save(root, report)
        val read = EngineCrashStore.read(root)

        assertNotNull(read)
        assertEquals(report, read)
    }

    @Test
    fun `missing file reads as null`() {
        assertNull(EngineCrashStore.read(root()))
    }

    @Test
    fun `a later crash overwrites the earlier one`() {
        val root = root()
        EngineCrashStore.save(root, EngineCrashReport(1L, 1, "first", emptyList()))
        EngineCrashStore.save(root, EngineCrashReport(2L, null, "second", listOf("out")))

        val read = EngineCrashStore.read(root)

        assertNotNull(read)
        assertEquals(2L, read!!.timestampMs)
        assertEquals("second", read.error)
    }

    @Test
    fun `the report builder redacts the password from error and output`() {
        // Defense in depth: `lastError` and the console lines are already
        // redacted at capture time, but a writer that forgets must not
        // leak the credential into the file.
        val report = EngineCrashStore.buildReport(
            exitCode = 1,
            error = "auth failed: hunter2",
            outputLines = listOf("server password: hunter2", "clean line"),
            password = "hunter2",
        )

        assertFalse(report.error!!.contains("hunter2"))
        assertTrue(report.outputLines.none { it.contains("hunter2") })
        assertEquals("clean line", report.outputLines.last())
    }

    @Test
    fun `a corrupt file reads as null instead of throwing`() {
        val root = root()
        EngineCrashStore.file(root).writeText("not a report\n")

        assertNull(EngineCrashStore.read(root))
    }
}
