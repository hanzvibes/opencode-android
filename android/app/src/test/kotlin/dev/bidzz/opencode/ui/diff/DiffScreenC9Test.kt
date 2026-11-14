package dev.bidzz.opencode.ui.diff

import dev.bidzz.opencode.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit-200 C9 (M-146): a file that changed but has no text hunks is not
 * "no changes" — the raw patch text tells a binary file and a mode-only
 * change apart, and the screen must say which one it is.
 */
class DiffScreenC9Test {

    @Test
    fun `binary patch reports a binary change`() {
        val patch = "Binary files a/logo.png and b/logo.png differ\n"
        assertEquals(R.string.diff_binary_changed, noTextDiffMessage(patch))
    }

    @Test
    fun `mode only patch reports a mode change`() {
        val patch = """
            diff --git a/run.sh b/run.sh
            old mode 100644
            new mode 100755
        """.trimIndent()
        assertEquals(R.string.diff_mode_changed, noTextDiffMessage(patch))
    }

    @Test
    fun `other hunkless patch reports no text diff`() {
        val patch = "diff --git a/empty.txt b/empty.txt\n"
        assertEquals(R.string.diff_no_text_diff, noTextDiffMessage(patch))
    }

    @Test
    fun `blank patch reports nothing`() {
        assertNull(noTextDiffMessage(""))
        assertNull(noTextDiffMessage("   \n"))
    }
}
