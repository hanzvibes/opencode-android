package dev.bidzz.opencode.ui.export

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The copy cap (audit-100 65-F7; audit-200 D2, m-1027): a verbatim export
 * crosses a Binder transaction (~1 MB) on its way to the clipboard, so an
 * uncapped copy of a multi-megabyte transcript can throw
 * TransactionTooLarge. The cap measures bytes, not characters — the old
 * character cap let 500k CJK characters (1.5 MB of UTF-8) through.
 */
class ExportCopyGuardTest {

    @Test
    fun `a small export is not blocked`() {
        assertFalse(shouldBlockCopy("a".repeat(1_000)))
    }

    @Test
    fun `an export exactly at the unit cap is not blocked`() {
        assertFalse(shouldBlockCopy("a".repeat(EXPORT_COPY_MAX_UNITS)))
    }

    @Test
    fun `an export one unit over the unit cap is blocked`() {
        assertTrue(shouldBlockCopy("a".repeat(EXPORT_COPY_MAX_UNITS + 1)))
    }

    @Test
    fun `a 400k-char CJK export is blocked`() {
        // 400k CJK characters are 1.2 MB of UTF-8 — past the Binder buffer
        // even though the character count looks modest.
        assertTrue(shouldBlockCopy("中".repeat(400_000)))
    }

    @Test
    fun `a 100k-char CJK export is not blocked`() {
        assertFalse(shouldBlockCopy("中".repeat(100_000)))
    }

    @Test
    fun `a 500k-char ASCII export is blocked`() {
        // The parcel writes UTF-16, so 500k ASCII characters are ~1 MB in
        // the parcel even before the ClipData overhead.
        assertTrue(shouldBlockCopy("a".repeat(500_000)))
    }
}
