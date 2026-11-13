package dev.bidzz.opencode.ui.files

import dev.bidzz.opencode.core.api.ApiResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tapping the file that is already open reloads it ONLY when the current
 * view is a failed load (audit-100 89-F5): a failed load has no other way
 * back, while a loaded file needs no reload.
 */
class RequestLoadTest {

    @Test
    fun `a failed load reloads on re-tap`() {
        assertTrue(shouldReloadSamePath(ApiResult.Failure(code = 500), null))
    }

    @Test
    fun `a keyed failure reloads on re-tap`() {
        assertTrue(shouldReloadSamePath(null, 123))
    }

    @Test
    fun `a loaded file does not reload on re-tap`() {
        assertFalse(shouldReloadSamePath(null, null))
    }
}
