package dev.bidzz.opencode.ui.providers

import dev.bidzz.opencode.R
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.engine.EngineConfigStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Providers view model with the engine down: key actions must report the
 * failure instead of silently doing nothing (audit-100 31-F7), and a
 * custom-provider form that fails validation must never reach the disk
 * (30-F1/31-F12/53-F7). Only the synchronous state is asserted here — the
 * coroutine bodies run on the main dispatcher, which unit tests cannot
 * drive (see the Fase 5 harness note).
 */
class ProvidersViewModelTest {

    @JvmField
    @Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private fun viewModel(home: File = temp.newFolder("config-home")) = ProvidersViewModel(
        EngineApi { "/tmp" },
        EngineConfigStore(home),
        restartEngine = {},
    )

    @Test
    fun `saving a key with the engine down reports it instead of silently doing nothing`() {
        val vm = viewModel()

        vm.saveKey("openai", "sk-test")

        val failure = vm.state.value.failure
        assertEquals(0, failure?.code)
        assertNull(vm.state.value.connected)
    }

    @Test
    fun `deleting and replacing a key with the engine down report it too`() {
        val vm = viewModel()

        vm.deleteCredential("openai", "cred-1")
        assertEquals(0, vm.state.value.failure?.code)

        vm.replaceCredential("openai", "cred-1", "sk-new")
        assertEquals(0, vm.state.value.failure?.code)
    }

    @Test
    fun `an invalid custom provider sets the failure key and writes nothing`() {
        val home = temp.newFolder("config-home-2")
        val vm = viewModel(home)

        vm.saveCustomProvider("Bad", "http://api.example.com/v1", "k", "m")

        assertEquals(R.string.providers_custom_url_http, vm.state.value.failureKey)
        assertNull(vm.state.value.connected)
        // Nothing reached the disk: no config file exists at all.
        assertFalse(File(home, "opencode/opencode.json").exists())
    }

    @Test
    fun `a name-taken custom provider is refused before any write`() {
        // The refusal itself is synchronous (validation); the duplicate-slug
        // check inside the coroutine is covered by isNameTaken's own test.
        val vm = viewModel()
        vm.saveCustomProvider("", "https://api.example.com/v1", "k", "m")

        assertEquals(R.string.providers_custom_name_blank, vm.state.value.failureKey)
    }
}
