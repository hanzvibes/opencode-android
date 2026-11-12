package dev.bidzz.opencode.ui.instructions

import dev.bidzz.opencode.api.EngineApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A reload must not wipe the instruction editor (audit-100 98-F9): the
 * key/value being typed survive the load, and only the list itself is
 * replaced on success. Tested with an engine that is not running, where the
 * load bails out before any network call — the editor must survive that
 * path as well as a successful one.
 */
class InstructionsLoadTest {

    private fun viewModel() = InstructionsViewModel(EngineApi { "/tmp" })

    @Test
    fun `reloading keeps the key and value being typed`() {
        val vm = viewModel()
        vm.setKey("my.key")
        vm.setValue("hello")

        vm.load("s1")

        assertEquals("my.key", vm.state.value.key)
        assertEquals("hello", vm.state.value.value)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `reloading with an empty editor stays empty`() {
        val vm = viewModel()

        vm.load("s1")

        assertEquals("", vm.state.value.key)
        assertEquals("", vm.state.value.value)
        assertFalse(vm.state.value.loading)
    }
}
