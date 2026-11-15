package dev.bidzz.opencode.ui.files

import dev.bidzz.opencode.api.EngineApi
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C16 (M-238): content search over the workspace with the
 * bundled `rg`. The parser is tested against real `rg --json` line shapes;
 * the runner is tested against a fake `rg` script so the tests do not
 * depend on the binary being installed on the machine running them.
 */
class ContentSearchTest {

    private fun workspace(): File =
        File(System.getProperty("java.io.tmpdir"), "c16-search-${System.nanoTime()}").apply { mkdirs() }

    private fun matchLine(path: String, line: Int, text: String): String {
        // Real `rg --json` escapes the line text like JSON does; a raw
        // newline inside the value would make the line unparseable.
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r")
        return """{"type":"match","data":{"path":{"text":"$path"},"lines":{"text":"$escaped"},"line_number":$line,"absolute_offset":0,"submatches":[]}}"""
    }

    /** A fake `rg` that prints [lines] to stdout no matter the arguments. */
    private fun fakeRg(lines: List<String>): File {
        val script = File.createTempFile("fake-rg-", ".sh")
        script.writeText("#!/bin/sh\n" + lines.joinToString("\n") { "printf '%s\\n' '$it'" } + "\n")
        script.setExecutable(true)
        return script
    }

    @Test
    fun `parseRgJsonLine reads a match line into a workspace-relative hit`() {
        val ws = workspace()
        val hit = parseRgJsonLine(
            matchLine("${ws.absolutePath}/src/Main.kt", 42, "    val foo = bar\n"),
            ws,
        )
        assertEquals(ContentMatch("src/Main.kt", 42, "    val foo = bar"), hit)
    }

    @Test
    fun `parseRgJsonLine ignores summary and context lines`() {
        val ws = workspace()
        assertNull(parseRgJsonLine("""{"type":"summary","data":{"elapsed_total":{"human":"1ms","nanos":1000}}}""", ws))
        assertNull(
            parseRgJsonLine(
                matchLine("${ws.absolutePath}/a.kt", 1, "x").replace("\"match\"", "\"context\""),
                ws,
            ),
        )
    }

    @Test
    fun `parseRgJsonLine skips binary matches and paths outside the workspace`() {
        val ws = workspace()
        // A binary match carries lines.bytes (base64), never lines.text.
        assertNull(
            parseRgJsonLine(
                """{"type":"match","data":{"path":{"text":"${ws.absolutePath}/bin.dat"},"lines":{"bytes":"aGVsbG8="},"line_number":1}}""",
                ws,
            ),
        )
        assertNull(parseRgJsonLine(matchLine("/elsewhere/a.kt", 1, "x"), ws))
        // A sibling directory sharing the workspace prefix
        // ("/tmp/ws-evil" vs workspace "/tmp/ws") is outside too.
        assertNull(parseRgJsonLine(matchLine("${ws.absolutePath}-evil/a.kt", 1, "x"), ws))
        assertNull(parseRgJsonLine("not json at all", ws))
        assertNull(
            parseRgJsonLine(
                """{"type":"match","data":{"path":{"text":"${ws.absolutePath}/a.kt"},"lines":{"text":"x"}}}""",
                ws,
            ),
        )
    }

    @Test
    fun `runContentSearch returns the fake rg matches in order`() {
        val ws = workspace()
        val script = fakeRg(
            listOf(
                matchLine("${ws.absolutePath}/a.kt", 3, "foo one"),
                matchLine("${ws.absolutePath}/b.kt", 1, "foo two"),
                """{"type":"summary","data":{}}""",
            ),
        )
        val result = runBlocking { runContentSearch(script, ws, "foo") }
        assertEquals(
            listOf(ContentMatch("a.kt", 3, "foo one"), ContentMatch("b.kt", 1, "foo two")),
            result.matches,
        )
        assertFalse(result.truncated)
    }

    @Test
    fun `runContentSearch caps the matches and reports truncation`() {
        val ws = workspace()
        val script = fakeRg((1..5).map { matchLine("${ws.absolutePath}/f$it.kt", it, "foo $it") })
        val result = runBlocking { runContentSearch(script, ws, "foo", maxMatches = 2) }
        assertEquals(2, result.matches.size)
        assertTrue(result.truncated)
    }

    @Test
    fun `runContentSearch never throws for a missing binary or a blank query`() {
        val ws = workspace()
        val missing = File(ws, "no-such-rg")
        val noBinary = runBlocking { runContentSearch(missing, ws, "foo") }
        assertEquals(emptyList<ContentMatch>(), noBinary.matches)
        assertFalse(noBinary.truncated)
        val blank = runBlocking { runContentSearch(fakeRg(emptyList()), ws, "   ") }
        assertEquals(emptyList<ContentMatch>(), blank.matches)
    }

    @Test
    fun `M-238 searchContent publishes the matches and clears loading`() {
        val ws = workspace()
        val script = fakeRg(listOf(matchLine("${ws.absolutePath}/src/Main.kt", 7, "TODO fix this")))
        val vm = ExplorerViewModel(EngineApi { ws.absolutePath }, { ws.absolutePath }, rgBinary = { script })
        vm.searchContent("TODO")
        val done = runBlocking {
            withTimeoutOrNull(20_000) {
                while (vm.state.value.contentSearchLoading) delay(25)
                true
            }
        }
        assertEquals(true, done)
        assertEquals("TODO", vm.state.value.contentSearchQuery)
        assertEquals(listOf(ContentMatch("src/Main.kt", 7, "TODO fix this")), vm.state.value.contentSearchResults)
        assertFalse(vm.state.value.contentSearchFailed)
    }

    @Test
    fun `M-238 searchContent with no rg binary reports failure instead of an empty result`() {
        val ws = workspace()
        val vm = ExplorerViewModel(EngineApi { ws.absolutePath }, { ws.absolutePath }, rgBinary = { null })
        vm.searchContent("TODO")
        val done = runBlocking {
            withTimeoutOrNull(20_000) {
                while (vm.state.value.contentSearchLoading) delay(25)
                true
            }
        }
        assertEquals(true, done)
        assertTrue(vm.state.value.contentSearchFailed)
        assertEquals(emptyList<ContentMatch>(), vm.state.value.contentSearchResults)
    }
}
