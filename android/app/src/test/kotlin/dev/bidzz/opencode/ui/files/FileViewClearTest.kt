package dev.bidzz.opencode.ui.files

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.ui.fileDetailVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C11: M-164 (the Files detail pane shows an eternal
 * "Reading file…" spinner before any file is opened) and M-165 (the
 * close button and system Back do nothing in the tablet detail pane).
 *
 * The view model runs without an engine, so a load bails out before
 * any network call — which is exactly what pins the empty-path guards:
 * without them, load("") reports "engine not running" instead of
 * staying empty.
 */
class FileViewClearTest {

    private fun viewModel() =
        FileViewViewModel(EngineApi { "/tmp" }, { "/tmp" })

    @Test
    fun `M-164 clear resets the viewer to nothing open and not loading`() {
        val vm = viewModel()
        // The initial state is loading=true with an empty path — the
        // spinner the tablet pane used to paint forever.
        assertTrue(vm.state.value.loading)
        vm.clear()
        assertEquals("", vm.state.value.path)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.failureKey)
    }

    @Test
    fun `M-164 loading an empty path is a no-op`() {
        val vm = viewModel()
        vm.clear()
        vm.load("")
        assertEquals("", vm.state.value.path)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.failureKey)
    }

    @Test
    fun `M-164 reloading from disk with nothing open loads nothing`() {
        val vm = viewModel()
        vm.clear()
        vm.reloadFromDisk()
        assertEquals("", vm.state.value.path)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.failureKey)
    }

    @Test
    fun `M-164 the detail pane only shows on expanded widths with a file open`() {
        assertTrue(fileDetailVisible(isExpanded = true, path = "a.kt"))
        assertFalse(fileDetailVisible(isExpanded = true, path = ""))
        assertFalse(fileDetailVisible(isExpanded = false, path = "a.kt"))
        assertFalse(fileDetailVisible(isExpanded = false, path = ""))
    }
}
