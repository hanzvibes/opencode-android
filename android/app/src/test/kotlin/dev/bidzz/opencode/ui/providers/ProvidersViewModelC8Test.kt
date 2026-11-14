package dev.bidzz.opencode.ui.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit-200 Fase C8 (M-127): a provider row stays PENDING until the
 * engine actually re-read the config — that is, until the handle
 * changes. The handles here are plain objects: the decision is pure
 * reference equality, and unit tests cannot construct an
 * [dev.bidzz.opencode.api.EngineApi.Handle].
 */
class ProvidersViewModelC8Test {

    @Test
    fun `M-127 no pending name clears to null`() {
        assertNull(resolveRestartPending(null, Any(), Any()))
    }

    @Test
    fun `M-127 a pending row survives when no restart was ever tracked`() {
        // E.g. the restart confirm was dismissed (M-457): no handle was
        // captured, so no refresh may claim the restart landed.
        assertEquals("my-proxy", resolveRestartPending("my-proxy", null, Any()))
    }

    @Test
    fun `M-127 a pending row survives while the engine is down`() {
        val before = Any()
        assertEquals("my-proxy", resolveRestartPending("my-proxy", before, null))
    }

    @Test
    fun `M-127 a pending row survives a re-entry with the same handle`() {
        val handle = Any()
        assertEquals("my-proxy", resolveRestartPending("my-proxy", handle, handle))
    }

    @Test
    fun `M-127 a pending row clears once a different handle appears`() {
        assertNull(resolveRestartPending("my-proxy", Any(), Any()))
    }

    @Test
    fun `M-127 the patch sentinel clears on a new handle like any other pending name`() {
        assertNull(
            resolveRestartPending(
                ProvidersViewModel.PATCH_RESTART_NAME,
                Any(),
                Any(),
            ),
        )
    }
}
