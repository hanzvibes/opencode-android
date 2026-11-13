package dev.bidzz.opencode.ui.export

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The copy cap (audit-100 65-F7): a verbatim export crosses a Binder
 * transaction (~1 MB) on its way to the clipboard, so an uncapped copy
 * of a multi-megabyte transcript can throw TransactionTooLarge. The cap
 * sits at half a million characters, comfortably under the limit for
 * ASCII JSON and still far above any transcript a user copies by hand.
 */
class ExportCopyGuardTest {

    @Test
    fun `a small export is not blocked`() {
        assertFalse(shouldBlockCopy(1_000))
    }

    @Test
    fun `an export exactly at the cap is not blocked`() {
        assertFalse(shouldBlockCopy(EXPORT_COPY_MAX_CHARS))
    }

    @Test
    fun `an export one character over the cap is blocked`() {
        assertTrue(shouldBlockCopy(EXPORT_COPY_MAX_CHARS + 1))
    }
}
