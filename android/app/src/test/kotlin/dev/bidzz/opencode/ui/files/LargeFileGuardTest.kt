package dev.bidzz.opencode.ui.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 6 batch 6c (59-F5): a capped read keeps only a prefix but still
 * counts the whole file, so the preview banner must use the reader's
 * line count instead of counting the kept text.
 */
class LargeFileGuardTest {

    @Test
    fun `59-F5 preview of a capped read keeps the true line count`() {
        val kept = "line1\nline2\nline3"
        val trueTotal = 2_000_000

        val (shown, total) = LargeFileGuard.preview(kept, trueTotal)

        assertEquals(kept, shown)
        assertEquals(trueTotal, total)
    }

    @Test
    fun `59-F5 preview of a large capped read cuts to the preview lines with the true total`() {
        val kept = (1..3_000).joinToString("\n") { "x".repeat(500) }
        val trueTotal = 2_000_000

        val (shown, total) = LargeFileGuard.preview(kept, trueTotal)

        assertEquals(2_000, shown.lines().size)
        assertEquals(trueTotal, total)
        assertTrue(LargeFileGuard.isLarge(kept))
    }

    @Test
    fun `D1 m-360 single-arg preview counts lines without splitting the text`() {
        val (shown, total) = LargeFileGuard.preview("a\nb\nc")
        assertEquals("a\nb\nc", shown)
        assertEquals(3, total)
        val (emptyShown, emptyTotal) = LargeFileGuard.preview("")
        assertEquals("", emptyShown)
        assertEquals(1, emptyTotal)
    }

    @Test
    fun `D1 m-360 large preview keeps the first lines verbatim`() {
        val big = (1..3_000).joinToString("\n") { "x".repeat(500) }
        val (shown, _) = LargeFileGuard.preview(big, 3_000)
        assertEquals((1..2_000).joinToString("\n") { "x".repeat(500) }, shown)
        assertEquals(2_000, shown.lines().size)
    }
}
