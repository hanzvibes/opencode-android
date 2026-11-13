package dev.bidzz.opencode.ui.providers

import dev.bidzz.opencode.R
import dev.bidzz.opencode.engine.EngineConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The custom-provider form validation (audit-100 30-F1/31-F12/53-F7),
 * shared by the dialog and the view model: the inline error and the
 * enforced rule cannot drift apart.
 */
class ProvidersValidationTest {

    @Test
    fun `a https endpoint with models is valid`() {
        assertNull(
            validateCustomProvider("Mine", "https://api.example.com/v1", "sk-x", "gpt-4 = GPT-4"),
        )
    }

    @Test
    fun `http is allowed for loopback only`() {
        assertNull(validateCustomProvider("Local", "http://localhost:11434/v1", "k", "llama"))
        assertNull(validateCustomProvider("Local", "http://127.0.0.1:8080/v1", "k", "llama"))
        assertNull(validateCustomProvider("Local", "http://[::1]:8080/v1", "k", "llama"))
        assertEquals(
            R.string.providers_custom_url_http,
            validateCustomProvider("Remote", "http://api.example.com/v1", "k", "m"),
        )
    }

    @Test
    fun `a bare host or a missing scheme is not a URL`() {
        assertEquals(
            R.string.providers_custom_url_invalid,
            validateCustomProvider("X", "not-a-url", "k", "m"),
        )
        assertEquals(
            R.string.providers_custom_url_invalid,
            validateCustomProvider("X", "api.example.com/v1", "k", "m"),
        )
        assertEquals(
            R.string.providers_custom_url_invalid,
            validateCustomProvider("X", "https://", "k", "m"),
        )
        assertEquals(
            R.string.providers_custom_url_invalid,
            validateCustomProvider("X", "", "k", "m"),
        )
        assertEquals(
            R.string.providers_custom_url_invalid,
            validateCustomProvider("X", "ftp://api.example.com/v1", "k", "m"),
        )
    }

    @Test
    fun `an empty model list and a model id with a space are refused`() {
        assertEquals(
            R.string.providers_custom_no_models,
            validateCustomProvider("X", "https://api.example.com/v1", "k", ",, "),
        )
        assertEquals(
            R.string.providers_custom_model_invalid,
            validateCustomProvider("X", "https://api.example.com/v1", "k", "my model"),
        )
    }

    @Test
    fun `a blank name and a missing key are refused for a new provider`() {
        assertEquals(
            R.string.providers_custom_name_blank,
            validateCustomProvider("", "https://api.example.com/v1", "k", "m"),
        )
        assertEquals(
            R.string.providers_custom_key_blank,
            validateCustomProvider("X", "https://api.example.com/v1", "", "m"),
        )
    }

    @Test
    fun `editing may leave the key blank, and an escaped comma is one model`() {
        assertNull(
            validateCustomProvider("X", "https://api.example.com/v1", "", "m", requireKey = false),
        )
        assertNull(
            validateCustomProvider("X", "https://api.example.com/v1", "k", "gpt-4 = GPT\\, 4 Turbo"),
        )
    }

    @Test
    fun `a name collision is only taken when it is not the provider being edited`() {
        val existing = listOf(
            EngineConfigStore.CustomProvider("mine", "Mine", "https://a.invalid/v1", listOf("m")),
        )
        assertTrue(isNameTaken(existing, "mine", editingId = null))
        assertFalse(isNameTaken(existing, "mine", editingId = "mine"))
        assertFalse(isNameTaken(existing, "other", editingId = null))
    }

    @Test
    fun `loopback is localhost, 127.0.0.1 and ipv6 loopback only`() {
        assertTrue(isLoopbackHost("localhost"))
        assertTrue(isLoopbackHost("LOCALHOST"))
        assertTrue(isLoopbackHost("127.0.0.1"))
        assertTrue(isLoopbackHost("::1"))
        assertFalse(isLoopbackHost("api.example.com"))
        assertFalse(isLoopbackHost("127.0.0.2"))
    }
}
