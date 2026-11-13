package dev.bidzz.opencode.ui

import dev.bidzz.opencode.ui.files.previewOrNull
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase A4 (C-005): the preview failure helper.
 *
 * A preview step that runs out of memory used to crash the app; it now
 * lands in the "Could not open this preview" state instead. What is
 * pinned here, on the JVM, is the helper both preview paths share:
 * an out-of-memory allocation and any other failure become null, a
 * value passes through, and cancellation is never swallowed (a
 * cancelled render must still cancel, so its caller can recycle the
 * bitmap it already made).
 *
 * The biometric guard (C-004, `runCatching` around
 * `BiometricPrompt.authenticate` plus an explicit error when the
 * activity is missing) needs the framework and is verified by compile
 * + the fast-verify run, not here.
 */
class AuditA4Test {

    @Test
    fun `previewOrNull passes a value through`() {
        assertEquals(42, previewOrNull { 42 })
    }

    @Test
    fun `previewOrNull turns an out-of-memory allocation into null`() {
        assertNull(previewOrNull<String> { throw OutOfMemoryError("no memory") })
    }

    @Test
    fun `previewOrNull turns an ordinary failure into null`() {
        assertNull(previewOrNull<String> { throw IllegalStateException("bad file") })
    }

    @Test
    fun `previewOrNull never swallows cancellation`() {
        var rethrown = false
        try {
            previewOrNull<String> { throw CancellationException("cancelled") }
        } catch (_: CancellationException) {
            rethrown = true
        }
        assertTrue(rethrown)
    }
}
