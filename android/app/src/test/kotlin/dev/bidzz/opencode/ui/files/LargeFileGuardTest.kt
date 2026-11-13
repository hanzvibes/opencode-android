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
}
