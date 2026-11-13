package dev.bidzz.opencode.ui

import dev.bidzz.opencode.ui.files.formatFileBytes
import dev.bidzz.opencode.ui.files.resolveTruncatedPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase A8 (DL-005): a file over the 8 MB read cap must never
 * look whole and editable.
 *
 * Pure JVM: whether a load is a truncated preview, and how the banner
 * names the file's real size. The view-model wiring (the flag reaching
 * the banner, editing staying blocked) is verified by compile +
 * fast-verify — a load needs a live engine handle.
 */
class AuditA8Test {

    @Test
    fun `a capped read with one line is truncated, not whole`() {
        assertTrue(
            resolveTruncatedPreview(
                cappedRead = true,
                allowFull = false,
                text = "x".repeat(100),
                totalLines = 1,
            ),
        )
    }

    @Test
    fun `a capped read with a hundred long lines is truncated`() {
        assertTrue(
            resolveTruncatedPreview(
                cappedRead = true,
                allowFull = false,
                text = "x".repeat(100),
                totalLines = 100,
            ),
        )
    }

    @Test
    fun `a capped read stays truncated even when the full file was asked for`() {
        // The caller refuses that case with a failure first; the helper's
        // answer is still "truncated" — the content is a prefix either way.
        assertTrue(
            resolveTruncatedPreview(
                cappedRead = true,
                allowFull = true,
                text = "x".repeat(100),
                totalLines = 1,
            ),
        )
    }

    @Test
    fun `a small uncapped file is not truncated`() {
        assertFalse(
            resolveTruncatedPreview(
                cappedRead = false,
                allowFull = false,
                text = "one\ntwo\n",
                totalLines = 2,
            ),
        )
    }

    @Test
    fun `an uncapped large file is truncated only past the preview line count`() {
        val large = "x".repeat(1_048_577)
        assertTrue(resolveTruncatedPreview(false, false, large, 3_000))
        assertFalse(resolveTruncatedPreview(false, false, large, 100))
    }

    @Test
    fun `an uncapped large file loaded in full is not truncated`() {
        val large = "x".repeat(1_048_577)
        assertFalse(resolveTruncatedPreview(false, true, large, 3_000))
    }

    @Test
    fun `formatFileBytes names bytes, kilobytes, megabytes and gigabytes`() {
        assertEquals("0 B", formatFileBytes(0))
        assertEquals("512 B", formatFileBytes(512))
        assertEquals("1 KB", formatFileBytes(1_024))
        assertEquals("1.5 MB", formatFileBytes(1_572_864))
        assertEquals("8 MB", formatFileBytes(8_388_608))
        assertEquals("9 MB", formatFileBytes(9_437_184))
        assertEquals("1 GB", formatFileBytes(1_073_741_824))
    }
}
