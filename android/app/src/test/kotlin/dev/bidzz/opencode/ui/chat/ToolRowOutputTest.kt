package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.ui.diff.DiffLine
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C2: `patch` / `apply_patch` / `multiedit` get a diff and
 * review buttons (M-051), and long output truncation is honest (M-056).
 * The extraction is pure — the composable only renders what these return.
 */
class ToolRowOutputTest {

    private fun call(name: String, inputJson: String) =
        ToolCall(id = "t1", name = name, status = "completed", input = Json.parseToJsonElement(inputJson))

    @Test
    fun `M-051 a patch tool parses its unified diff into hunks`() {
        val hunks = extractPatchHunks(
            call(
                "patch",
                """{"filePath":"src/Foo.kt","patch":"@@ -1,2 +1,2 @@\n fun a() = 1\n-fun b() = 1\n+fun b() = 2\n"}""",
            ),
        )
        assertTrue(hunks!!.size == 1)
        val lines = hunks[0].lines
        assertEquals(DiffLine.Kind.CONTEXT, lines[0].kind)
        assertEquals(DiffLine.Kind.REMOVED, lines[1].kind)
        assertEquals("fun b() = 1", lines[1].text)
        assertEquals(DiffLine.Kind.ADDED, lines[2].kind)
        assertEquals("fun b() = 2", lines[2].text)
    }

    @Test
    fun `M-051 apply_patch parses the same way as patch`() {
        val hunks = extractPatchHunks(
            call(
                "apply_patch",
                """{"patch":"@@ -10,1 +10,1 @@\n-old\n+new\n"}""",
            ),
        )
        assertTrue(hunks!!.size == 1)
        assertEquals(DiffLine.Kind.REMOVED, hunks[0].lines[0].kind)
        assertEquals(DiffLine.Kind.ADDED, hunks[0].lines[1].kind)
    }

    @Test
    fun `M-051 a multiedit parses each edit into its own hunks`() {
        val hunks = extractPatchHunks(
            call(
                "multiedit",
                """{"filePath":"src/Foo.kt","edits":[
                    {"oldString":"fun a() = 1","newString":"fun a() = 2"},
                    {"oldString":"fun b() = 1","newString":"fun b() = 2"}
                ]}""",
            ),
        )
        assertTrue(hunks!!.size == 2)
        assertTrue(hunks.all { hunk ->
            hunk.lines.any { it.kind == DiffLine.Kind.REMOVED } &&
                hunk.lines.any { it.kind == DiffLine.Kind.ADDED }
        })
        assertEquals("fun a() = 1", hunks[0].lines.first { it.kind == DiffLine.Kind.REMOVED }.text)
        assertEquals("fun b() = 2", hunks[1].lines.first { it.kind == DiffLine.Kind.ADDED }.text)
    }

    @Test
    fun `M-051 a multiedit with mismatched edit pairs falls back to raw input`() {
        // One edit without a newString: pairing the wrong texts would show
        // a diff for an edit that never happened — null keeps the raw input.
        assertNull(
            extractPatchHunks(
                call(
                    "multiedit",
                    """{"edits":[
                        {"oldString":"a","newString":"b"},
                        {"oldString":"c"}
                    ]}""",
                ),
            ),
        )
    }

    @Test
    fun `M-051 a patch without a patch field falls back to raw input`() {
        assertNull(extractPatchHunks(call("patch", """{"filePath":"src/Foo.kt"}""")))
        assertNull(extractPatchHunks(call("edit", """{"oldString":"a","newString":"b"}""")))
    }

    @Test
    fun `M-056 short output is shown whole`() {
        val out = "line 1\nline 2\n"
        assertEquals(out, truncateOutput(out))
    }

    @Test
    fun `M-056 long output is cut at 8000 characters with a marker`() {
        val out = "x".repeat(8001)
        val shown = truncateOutput(out)
        assertTrue(shown.startsWith("x".repeat(8000)))
        assertTrue(shown.endsWith("\n… [truncated]"))
        assertEquals(8000 + "\n… [truncated]".length, shown.length)
    }

    @Test
    fun `M-056 exactly 8000 characters is not truncated`() {
        val out = "x".repeat(8000)
        assertEquals(out, truncateOutput(out))
    }
}
