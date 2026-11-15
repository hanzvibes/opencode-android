package dev.bidzz.opencode

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crash record survives a restart (audit-200 C13, M-179): the app's
 * last uncaught exception is written to `last-crash.txt` in filesDir and
 * read back by Diagnostics, so a crash is no longer gone the moment the
 * user re-opens the app.
 */
class CrashLogTest {

    private fun filesDir(): File = Files.createTempDirectory("crashlog").toFile()

    @Test
    fun `write and read round-trip the report`() {
        val dir = filesDir()
        val thread = Thread.currentThread()

        val file = CrashLog.writeReport(dir, thread, IllegalStateException("boom"), "1.2.3 (45)")

        assertTrue(file.isFile)
        val read = CrashLog.readLastCrash(dir)
        assertNotNull(read)
        read!!
        assertEquals("1.2.3 (45)", read.version)
        assertEquals(thread.name, read.thread)
        assertTrue(read.trace.contains("IllegalStateException: boom"))
        assertTrue(read.timestampMs > 0)
    }

    @Test
    fun `missing file reads as null`() {
        assertNull(CrashLog.readLastCrash(filesDir()))
    }

    @Test
    fun `a later report overwrites the earlier one`() {
        val dir = filesDir()
        val thread = Thread.currentThread()
        CrashLog.writeReport(dir, thread, IllegalStateException("first"), "1.0")
        CrashLog.writeReport(dir, thread, IllegalArgumentException("second"), "1.1")

        val read = CrashLog.readLastCrash(dir)

        assertNotNull(read)
        assertEquals("1.1", read!!.version)
        assertTrue(read.trace.contains("IllegalArgumentException: second"))
    }
}
