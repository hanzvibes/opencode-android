package dev.bidzz.opencode.ui.diff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Diff parsing.
 *
 * Wrong line numbers or a lost `+` are immediately visible in a diff viewer, so
 * these cover the cases that actually appear in engine output: multiple hunks,
 * added and removed files, and the `\ No newline at end of file` marker.
 */
class DiffParserTest {

    @Test
    fun `parses a single hunk with context, addition and removal`() {
        val patch = """
            @@ -1,3 +1,4 @@
             def hello():
            -    print(1)
            +    print(2)
            +    return True
             
        """.trimIndent()

        val hunks = DiffParser.parse(patch)
        assertEquals(1, hunks.size)
        val lines = hunks[0].lines
        assertEquals(DiffLine.Kind.CONTEXT, lines[0].kind)
        assertEquals("def hello():", lines[0].text)
        assertEquals(DiffLine.Kind.REMOVED, lines[1].kind)
        assertEquals("    print(1)", lines[1].text)
        assertEquals(DiffLine.Kind.ADDED, lines[2].kind)
        assertEquals("    print(2)", lines[2].text)
        assertEquals(DiffLine.Kind.ADDED, lines[3].kind)
    }

    @Test
    fun `line numbers follow the hunk header`() {
        val patch = """
            @@ -10,3 +10,4 @@
             context
            -old
            +new
            +extra
        """.trimIndent()

        val lines = DiffParser.parse(patch).first().lines
        // Context line starts at old 10 / new 10.
        assertEquals(10, lines[0].oldLine)
        assertEquals(10, lines[0].newLine)
        // Removed line keeps the old counter.
        assertEquals(11, lines[1].oldLine)
        assertEquals(null, lines[1].newLine)
        // Added line keeps the new counter.
        assertEquals(null, lines[2].oldLine)
        assertEquals(11, lines[2].newLine)
        assertEquals(12, lines[3].newLine)
    }

    @Test
    fun `parses multiple hunks separately`() {
        val patch = """
            @@ -1,2 +1,2 @@
             a
            -b
            +B
            @@ -20,2 +20,2 @@
             c
            -d
            +D
        """.trimIndent()

        val hunks = DiffParser.parse(patch)
        assertEquals(2, hunks.size)
        assertTrue(hunks[0].header.contains("-1,2"))
        assertTrue(hunks[1].header.contains("-20,2"))
    }

    @Test
    fun `drops file headers but keeps hunk content`() {
        val patch = """
            diff --git a/main.py b/main.py
            index 1234567..89abcde 100644
            --- a/main.py
            +++ b/main.py
            @@ -1 +1 @@
            -old
            +new
        """.trimIndent()

        val hunks = DiffParser.parse(patch)
        assertEquals(1, hunks.size)
        assertEquals(2, hunks[0].lines.size)
        assertTrue(hunks[0].lines.none { it.text.contains("main.py") })
    }

    @Test
    fun `no newline marker is meta, not an added line`() {
        // Treating this as content would show a phantom "+" line in every diff of a
        // file without a trailing newline.
        val patch = """
            @@ -1 +1 @@
            -old
            \ No newline at end of file
            +new
            \ No newline at end of file
        """.trimIndent()

        val lines = DiffParser.parse(patch).first().lines
        val meta = lines.filter { it.kind == DiffLine.Kind.META }
        assertEquals(2, meta.size)
        assertEquals(1, lines.count { it.kind == DiffLine.Kind.ADDED })
        assertEquals(1, lines.count { it.kind == DiffLine.Kind.REMOVED })
    }

    @Test
    fun `new file diff counts only additions`() {
        val patch = """
            @@ -0,0 +1,2 @@
            +line one
            +line two
        """.trimIndent()

        val hunks = DiffParser.parse(patch)
        val (added, removed) = DiffParser.counts(hunks)
        assertEquals(2, added)
        assertEquals(0, removed)
    }

    @Test
    fun `empty patch yields no hunks`() {
        assertTrue(DiffParser.parse("").isEmpty())
        assertTrue(DiffParser.parse("   ").isEmpty())
    }

    @Test
    fun `truncated patch does not throw`() {
        // The engine can truncate with `more: true`; a half hunk must still render.
        val patch = """
            @@ -1,5 +1,5 @@
             a
            +b
        """.trimIndent()

        val hunks = DiffParser.parse(patch)
        assertEquals(1, hunks.size)
        assertEquals(2, hunks[0].lines.size)
    }

    @Test
    fun `blank context line keeps counters aligned`() {
        val patch = "@@ -1,3 +1,3 @@\n a\n\n-b\n+c\n"
        val lines = DiffParser.parse(patch).first().lines
        // The blank line is context, so old and new both advance past it.
        assertEquals(DiffLine.Kind.CONTEXT, lines[1].kind)
        assertEquals(2, lines[1].oldLine)
        assertEquals(2, lines[1].newLine)
        assertEquals(3, lines[2].oldLine)
    }
}
