package dev.bidzz.opencode.engine

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Custom providers the app did not write (audit-100 38-F9, 30-F2): a
 * hand-edited config may hold settings/models in a shape the reader does
 * not expect, and a provider must be deletable and editable without
 * retyping a key the app never shows.
 */
class CustomProviderStoreTest {

    @JvmField
    @Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private fun configHome(): File = temp.newFolder("config-home")

    private fun configFile(home: File) = File(home, "opencode/opencode.json")

    private fun writeConfig(home: File, text: String) {
        File(home, "opencode").mkdirs()
        configFile(home).writeText(text)
    }

    private fun settingsOf(home: File, providerID: String): JSONObject =
        JSONObject(configFile(home).readText())
            .getJSONObject("providers")
            .getJSONObject(providerID)
            .getJSONObject("settings")

    @Test
    fun `a non-object settings value flags the provider malformed, not empty`() {
        val home = configHome()
        writeConfig(
            home,
            """{"providers":{"weird":{"name":"Weird","settings":"disabled","models":{"m1":{}}}}}""",
        )

        val providers = EngineConfigStore(home).customProviders()

        assertEquals(1, providers.size)
        assertTrue(providers.single().malformed)
        // The entry is still listed — hiding it would look like the
        // provider vanished, and the row shows a warning instead.
        assertEquals("weird", providers.single().id)
    }

    @Test
    fun `a non-object models value flags the provider malformed`() {
        val home = configHome()
        writeConfig(
            home,
            """{"providers":{"weird":{"name":"Weird","settings":{"baseURL":"https://w.invalid/v1"},"models":["m1"]}}}""",
        )

        val providers = EngineConfigStore(home).customProviders()

        assertTrue(providers.single().malformed)
        assertEquals(emptyList<String>(), providers.single().models)
    }

    @Test
    fun `a well-formed provider is not malformed`() {
        val home = configHome()
        EngineConfigStore(home).putOpenAICompatible(
            id = "probe",
            name = "Probe",
            baseURL = "https://probe.invalid/v1",
            apiKey = "k",
            models = EngineConfigStore.parseModels("m/one"),
        )

        assertFalse(EngineConfigStore(home).customProviders().single().malformed)
    }

    @Test
    fun `deleting a custom provider removes only that entry`() {
        val home = configHome()
        val store = EngineConfigStore(home)
        store.putOpenAICompatible(
            id = "a", name = "A", baseURL = "https://a.invalid/v1", apiKey = "k",
            models = EngineConfigStore.parseModels("m"),
        )
        store.putOpenAICompatible(
            id = "b", name = "B", baseURL = "https://b.invalid/v1", apiKey = "k",
            models = EngineConfigStore.parseModels("m"),
        )

        assertTrue(store.deleteCustomProvider("a"))
        assertFalse(store.deleteCustomProvider("a"))

        assertEquals(listOf("b"), store.customProviders().map { it.id })
    }

    @Test
    fun `saving with a null key keeps the stored key`() {
        val home = configHome()
        val store = EngineConfigStore(home)
        store.putOpenAICompatible(
            id = "probe", name = "Probe", baseURL = "https://probe.invalid/v1", apiKey = "secret-key",
            models = EngineConfigStore.parseModels("m/one"),
        )

        store.putOpenAICompatible(
            id = "probe", name = "Probe 2", baseURL = "https://probe.invalid/v2", apiKey = null,
            models = EngineConfigStore.parseModels("m/one, m/two"),
        )

        assertEquals("secret-key", settingsOf(home, "probe").getString("apiKey"))
        assertEquals("https://probe.invalid/v2", settingsOf(home, "probe").getString("baseURL"))
        val entry = JSONObject(configFile(home).readText())
            .getJSONObject("providers").getJSONObject("probe")
        assertEquals("Probe 2", entry.getString("name"))
    }

    @Test
    fun `saving a new provider with a null key is refused`() {
        val home = configHome()
        assertThrows(java.io.IOException::class.java) {
            EngineConfigStore(home).putOpenAICompatible(
                id = "probe", name = "Probe", baseURL = "https://probe.invalid/v1", apiKey = null,
                models = EngineConfigStore.parseModels("m/one"),
            )
        }
    }
}
