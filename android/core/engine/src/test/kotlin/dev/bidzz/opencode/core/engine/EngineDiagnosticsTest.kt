package dev.bidzz.opencode.core.engine

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

/**
 * Covers the two things this class has to get right before a bug report can trust
 * it: reading the tail of an unbounded log without loading the whole file, and
 * telling a missing tool source apart from a tool that was never linked.
 */
class EngineDiagnosticsTest {

    /** Diagnostics wired to a throwaway tree, plus the log file it should read. */
    private class Fixture(root: File) {
        val diagnostics: EngineDiagnostics
        val log: File

        init {
            val filesDir = File(root, "files").apply { mkdirs() }
            val cacheDir = File(root, "cache").apply { mkdirs() }
            val workspace = File(root, "workspace").apply { mkdirs() }
            val nativeLib = File(root, "lib").apply { mkdirs() }
            val environment = EngineEnvironment(filesDir, cacheDir, workspace)
            val logDir = File(File(environment.dataHome, "opencode"), "log").apply { mkdirs() }
            log = File(logDir, "opencode.log")
            diagnostics = EngineDiagnostics(environment, nativeLib)
        }
    }

    private fun fixture(): Fixture = Fixture(Files.createTempDirectory("diag").toFile())

    @Test
    fun logTailReturnsNewestLinesLast() = runTest {
        val fixture = fixture()
        fixture.log.writeText((1..50).joinToString("\n") { "line $it" } + "\n")

        val tail = fixture.diagnostics.engineLogTail(lines = 5)

        assertEquals(listOf("line 46", "line 47", "line 48", "line 49", "line 50"), tail.lines)
        // Audit-200 C9 (M-136): a small file is read whole — nothing is hidden.
        assertFalse(tail.wasTruncated)
        assertEquals(fixture.log.length(), tail.fileBytes)
    }

    @Test
    fun logTailDropsTheLineCutInHalfByTheReadWindow() = runTest {
        val fixture = fixture()
        // Long enough that the backwards read cannot take the whole file, so the seek
        // lands in the middle of this run of x and the first fragment it reads is the
        // tail of it — hundreds of kilobytes that are not a log entry.
        fixture.log.writeText("OLD FIRST LINE\n${"x".repeat(300 * 1024)}\nNEW LINE A\nNEW LINE B\n")

        val tail = fixture.diagnostics.engineLogTail(lines = 10)

        assertEquals("a fragment of the cut line reached the tail", 2, tail.lines.size)
        assertEquals("NEW LINE A", tail.lines.first())
        assertEquals("NEW LINE B", tail.lines.last())
        assertTrue(
            "the tail carries a line no log writes",
            tail.lines.all { it.length < 1_000 },
        )
        // Audit-200 C9 (M-136): the file is larger than the 256 KB window,
        // so the result must say older lines exist beyond the tail.
        assertTrue(tail.wasTruncated)
        assertEquals(fixture.log.length(), tail.fileBytes)
    }

    @Test
    fun missingLogReadsAsEmptyInsteadOfThrowing() = runTest {
        val fixture = fixture()

        val tail = fixture.diagnostics.engineLogTail()

        assertEquals(emptyList<String>(), tail.lines)
        assertFalse(tail.wasTruncated)
        assertEquals(0, tail.fileBytes)
    }

    @Test
    fun pathsReportTheLogPathAndWhetherItExists() {
        val fixture = fixture()
        val reported = fixture.diagnostics.paths().first { it.first == "engineLog" }.second
        // The path alone: whether to say "(missing)" is wording, and wording belongs to the
        // screen, which knows the app's language. The fact the screen needs is `isFile`.
        assertEquals(fixture.log.absolutePath, reported)
        assertFalse(fixture.diagnostics.engineLogFile.isFile)

        fixture.log.writeText("hello\n")
        assertTrue(fixture.diagnostics.engineLogFile.isFile)
        val present = fixture.diagnostics.paths().first { it.first == "engineLog" }.second
        assertEquals(fixture.log.absolutePath, present)
    }

    @Test
    fun unlinkedToolReportsLinkAndSourceAsSeparateFacts() {
        val fixture = fixture()

        val tools = fixture.diagnostics.tools()
        assertTrue("no tools declared", tools.isNotEmpty())
        tools.forEach { tool ->
            assertFalse("${tool.name} should not resolve", tool.resolves)
            assertFalse("${tool.name} has no source .so in this tree", tool.sourcePresent)
        }
    }
}
