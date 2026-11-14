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
        JSONObject(EngineConfigStore.stripJsonc(configFile(home).readText()))
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

    @Test
    fun `a non-object provider entry is listed as broken, not skipped (M-014)`() {
        val home = configHome()
        writeConfig(
            home,
            """{"providers":{"old":"disabled","good":{"name":"Good","settings":{"baseURL":"https://g.invalid/v1","apiKey":"k"},"models":{"m":{}}}}}""",
        )

        val providers = EngineConfigStore(home).customProviders()

        assertEquals(listOf("old", "good"), providers.map { it.id })
        val broken = providers.first()
        assertTrue(broken.nonObject)
        assertTrue(broken.malformed)
        assertEquals("old", broken.name)
        // The broken entry can be deleted from the UI now — the delete
        // path never needed an object, only the list hid it.
        assertTrue(EngineConfigStore(home).deleteCustomProvider("old"))
        assertEquals(listOf("good"), EngineConfigStore(home).customProviders().map { it.id })
    }

    @Test
    fun `editing keeps hand-written keys inside the entry (M-250, M-507)`() {
        val home = configHome()
        writeConfig(
            home,
            """{"providers":{"probe":{"name":"Probe","package":"@opencode/ai/providers/openai-compatible","disabled":true,"settings":{"baseURL":"https://old.invalid/v1","apiKey":"secret-key","timeout":30},"models":{"m/one":{"name":"One","limit":{"context":8192}}}}}}""",
        )
        val store = EngineConfigStore(home)

        store.putOpenAICompatible(
            id = "probe", name = "Probe 2", baseURL = "https://new.invalid/v1", apiKey = null,
            // The form keeps the model name it was given ("One"): only the
            // keys the form never shows (limit, timeout, disabled) are the
            // merge's job — a name the form lists without one is a deliberate
            // clear, covered by the clearing test below.
            models = EngineConfigStore.parseModels("m/one = One, m/two"),
        )

        val entry = JSONObject(configFile(home).readText())
            .getJSONObject("providers").getJSONObject("probe")
        // The form's own fields changed; everything hand-written stayed.
        assertEquals("Probe 2", entry.getString("name"))
        assertTrue(entry.getBoolean("disabled"))
        val settings = entry.getJSONObject("settings")
        assertEquals("https://new.invalid/v1", settings.getString("baseURL"))
        assertEquals("secret-key", settings.getString("apiKey"))
        assertEquals(30, settings.getInt("timeout"))
        val models = entry.getJSONObject("models")
        assertEquals("One", models.getJSONObject("m/one").getString("name"))
        assertEquals(8192, models.getJSONObject("m/one").getJSONObject("limit").getInt("context"))
        assertTrue(models.has("m/two"))
    }

    @Test
    fun `clearing a model name removes its name key instead of keeping the old one (M-250)`() {
        val home = configHome()
        val store = EngineConfigStore(home)
        store.putOpenAICompatible(
            id = "probe", name = "Probe", baseURL = "https://probe.invalid/v1", apiKey = "k",
            models = EngineConfigStore.parseModels("m/one = One"),
        )

        store.putOpenAICompatible(
            id = "probe", name = "Probe", baseURL = "https://probe.invalid/v1", apiKey = null,
            models = EngineConfigStore.parseModels("m/one"),
        )

        val model = JSONObject(configFile(home).readText())
            .getJSONObject("providers").getJSONObject("probe")
            .getJSONObject("models").getJSONObject("m/one")
        assertFalse(model.has("name"))
    }

    @Test
    fun `renaming a provider moves its entry instead of leaving a second one (M-126)`() {
        val home = configHome()
        val store = EngineConfigStore(home)
        store.putOpenAICompatible(
            id = "old-name", name = "Old Name", baseURL = "https://old.invalid/v1", apiKey = "k",
            models = EngineConfigStore.parseModels("m"),
        )

        store.putOpenAICompatible(
            id = "new-name", name = "New Name", baseURL = "https://old.invalid/v1", apiKey = null,
            models = EngineConfigStore.parseModels("m"),
            previousId = "old-name",
        )

        assertEquals(listOf("new-name"), store.customProviders().map { it.id })
        assertEquals("k", settingsOf(home, "new-name").getString("apiKey"))
    }

    @Test
    fun `saving to a commented json keeps comments outside providers (M-248, M-249)`() {
        val home = configHome()
        writeConfig(
            home,
            "{\n" +
                "  // my note about the key below\n" +
                "  \"providers\": {\n" +
                "    \"probe\": {\"name\": \"Probe\", \"settings\": {\"baseURL\": \"https://old.invalid/v1\", \"apiKey\": \"k\"}, \"models\": {\"m\": {}}}\n" +
                "  },\n" +
                "  // the theme I picked by hand\n" +
                "  \"theme\": \"dark\"\n" +
                "}\n",
        )
        val store = EngineConfigStore(home)

        store.putOpenAICompatible(
            id = "probe", name = "Probe", baseURL = "https://new.invalid/v1", apiKey = null,
            models = EngineConfigStore.parseModels("m"),
        )

        val text = configFile(home).readText()
        assertTrue(text.contains("// my note about the key below"))
        assertTrue(text.contains("// the theme I picked by hand"))
        assertTrue(text.contains("\"theme\": \"dark\""))
        assertEquals("https://new.invalid/v1", settingsOf(home, "probe").getString("baseURL"))
    }

    @Test
    fun `shadowed json providers are named while jsonc is effective (M-248)`() {
        val home = configHome()
        writeConfig(home, """{"providers":{"in-json":{"name":"In Json","settings":{"baseURL":"https://j.invalid/v1","apiKey":"k"},"models":{}}}}""")
        File(home, "opencode/opencode.jsonc").writeText("""{"providers":{}}""")
        val store = EngineConfigStore(home)

        assertEquals(listOf("in-json"), store.shadowedJsonProviderIds())
        // The effective list comes from the .jsonc, so the shadowed one
        // is not listed as a provider.
        assertEquals(emptyList<String>(), store.customProviders().map { it.id })
    }

    @Test
    fun `no shadowed providers when there is no jsonc`() {
        val home = configHome()
        writeConfig(home, """{"providers":{"a":{"name":"A","settings":{"baseURL":"https://a.invalid/v1","apiKey":"k"},"models":{}}}}""")

        assertEquals(emptyList<String>(), EngineConfigStore(home).shadowedJsonProviderIds())
    }
}
