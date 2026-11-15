package dev.bidzz.opencode.ui.providers

import dev.bidzz.opencode.engine.EngineConfigStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One source of truth for the provider "connected" badge (audit-100
 * Fase 6b, 31-F6/31-F16): the hero, the Featured card and the catalog
 * row must agree, and a custom provider that merely mentions a provider
 * in its id or name must never badge it CONNECTED.
 */
class ProvidersStatusTest {

    private fun custom(id: String, name: String) =
        EngineConfigStore.CustomProvider(id = id, name = name, baseURL = "https://x", models = listOf("m"))

    @Test
    fun `a lookalike custom provider does not mark Google connected`() {
        val customs = listOf(custom("my-google-proxy", "My Google Proxy"))
        assertFalse(isCustomConfiguredFor(customs, "google"))
        assertFalse(isProviderConnected("google", false, customs, null))
    }

    @Test
    fun `an exact-id custom provider marks its provider connected`() {
        val customs = listOf(custom("google", "Google"))
        assertTrue(isCustomConfiguredFor(customs, "google"))
        assertTrue(isProviderConnected("google", false, customs, null))
    }

    @Test
    fun `Antigravity alone connects Google everywhere, nothing else`() {
        assertTrue(isProviderConnected("google", false, emptyList(), "rhn@example.com"))
        assertFalse(isProviderConnected("anthropic", false, emptyList(), "rhn@example.com"))
        assertFalse(isProviderConnected("google", false, emptyList(), null))
    }

    @Test
    fun `M-220 Antigravity without its plugin does not badge Google connected`() {
        // The account on disk says nothing about whether the engine
        // loaded opencode-antigravity-auth — a token no plugin can use
        // must not read CONNECTED. Unknown (null, not checked yet)
        // keeps the old reading; only a confirmed absence demotes it.
        assertFalse(
            isProviderConnected("google", false, emptyList(), "rhn@example.com", antigravityPluginRegistered = false),
        )
        assertTrue(
            isProviderConnected("google", false, emptyList(), "rhn@example.com", antigravityPluginRegistered = true),
        )
        assertTrue(
            isProviderConnected("google", false, emptyList(), "rhn@example.com", antigravityPluginRegistered = null),
        )
        // The engine flag and a custom provider still connect on their
        // own — the plugin gate only applies to the Antigravity path.
        assertTrue(
            isProviderConnected("anthropic", true, emptyList(), null, antigravityPluginRegistered = false),
        )
        assertTrue(
            isProviderConnected(
                "google",
                false,
                listOf(custom("google", "Google")),
                "rhn@example.com",
                antigravityPluginRegistered = false,
            ),
        )
    }

    @Test
    fun `the engine flag still connects on its own`() {
        assertTrue(isProviderConnected("anthropic", true, emptyList(), null))
        assertFalse(isProviderConnected("anthropic", false, emptyList(), null))
    }
}
