package dev.bidzz.opencode.engine

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Model names in the custom-provider form.
 *
 * The engine only shows a readable label when `models[id].name` is present in the
 * config it reads at startup, so the two halves worth pinning are the parsing of
 * `id = Name` from a text field and the shape that actually lands in the file.
 */
class ModelNamesTest {

    @JvmField
    @Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private fun configHome(): File = temp.newFolder("config-home")

    private fun configFile(home: File) = File(home, "opencode/opencode.json")

    private fun modelsOf(home: File, providerID: String): JSONObject =
        JSONObject(configFile(home).readText())
            .getJSONObject("providers")
            .getJSONObject(providerID)
            .getJSONObject("models")

    @Test
    fun `an id with a name parses both`() {
        val parsed = EngineConfigStore.parseModels("agnes-2.5-flash = Agnes 2.5 Flash, deepseek-v4")
        assertEquals(
            listOf(
                EngineConfigStore.ModelSpec("agnes-2.5-flash", "Agnes 2.5 Flash"),
                EngineConfigStore.ModelSpec("deepseek-v4", null),
            ),
            parsed,
        )
    }

    @Test
    fun `only the first equals sign splits`() {
        // Model names really do contain separators — "gpt-4o = GPT 4o (2024-08) = best"
        // should not have its tail chopped off by a second split.
        val parsed = EngineConfigStore.parseModels("m = a = b")
        assertEquals("a = b", parsed.single().name)
    }

    @Test
    fun `a name that is only whitespace is no name`() {
        // This is the case that decides the file shape: an empty string here would
        // write `"name": ""`, and the engine's `if (config.name)` skips falsy values,
        // so the picker would keep the raw id with no way to tell why.
        assertNull(EngineConfigStore.parseModels("m =").single().name)
    }

    @Test
    fun `repeating an id keeps one entry and the last name`() {
        val parsed = EngineConfigStore.parseModels("a = First, b, a = Second")
        assertEquals(
            listOf(
                EngineConfigStore.ModelSpec("a", "Second"),
                EngineConfigStore.ModelSpec("b", null),
            ),
            parsed,
        )
    }

    @Test
    fun `a bare repeat does not erase a name already given`() {
        val parsed = EngineConfigStore.parseModels("a = First, a")
        assertEquals("First", parsed.single().name)
    }

    @Test
    fun `junk entries are dropped`() {
        // A trailing comma is normal typing; `=` with nothing before it has no id to
        // store under and would become a model the engine cannot resolve.
        assertEquals(emptyList<EngineConfigStore.ModelSpec>(), EngineConfigStore.parseModels(" , = , "))
        assertEquals(emptyList<EngineConfigStore.ModelSpec>(), EngineConfigStore.parseModels(""))
    }

    @Test
    fun `lines and commas both separate`() {
        val parsed = EngineConfigStore.parseModels("one = One\ntwo,\nthree = Three\n")
        assertEquals(listOf("one", "two", "three"), parsed.map { it.id })
    }

    @Test
    fun `a named model writes a name and a bare one writes an empty object`() {
        val home = configHome()
        val result = EngineConfigStore(home).putOpenAICompatible(
            id = "probe",
            name = "Probe",
            baseURL = "https://probe.invalid/v1",
            apiKey = "not-a-real-key",
            models = EngineConfigStore.parseModels("m/one = One, m/two"),
        )

        // Control: if the write had not happened at all, the assertions below would
        // throw rather than pass quietly.
        assertEquals(EngineConfigStore.WriteResult.Saved("probe"), result)

        val models = modelsOf(home, "probe")
        assertEquals(2, models.length())
        assertEquals("One", models.getJSONObject("m/one").getString("name"))
        assertEquals(0, models.getJSONObject("m/two").length())
    }

    @Test
    fun `writing a provider keeps fields the app does not know about`() {
        // The merge rule this class exists for, re-checked because the write path
        // changed: rewriting the file from scratch would delete hand-made config.
        val home = configHome()
        File(home, "opencode").mkdirs()
        configFile(home).writeText(
            """{"providers":{"keep":{"name":"Keep"}},"agents":{"reviewer":{"description":"Reviews"}}}""",
        )

        EngineConfigStore(home).putOpenAICompatible(
            id = "probe",
            name = "Probe",
            baseURL = "https://probe.invalid/v1",
            apiKey = "not-a-real-key",
            models = EngineConfigStore.parseModels("m/one = One"),
        )

        val root = JSONObject(configFile(home).readText())
        assertEquals("Reviews", root.getJSONObject("agents").getJSONObject("reviewer").getString("description"))
        assertEquals("Keep", root.getJSONObject("providers").getJSONObject("keep").getString("name"))
        assertEquals("One", modelsOf(home, "probe").getJSONObject("m/one").getString("name"))
    }

    @Test
    fun `reading back lists the ids it was given`() {
        val home = configHome()
        EngineConfigStore(home).putOpenAICompatible(
            id = "probe",
            name = "Probe",
            baseURL = "https://probe.invalid/v1",
            apiKey = "not-a-real-key",
            models = EngineConfigStore.parseModels("m/one = One, m/two"),
        )

        val providers = EngineConfigStore(home).customProviders()
        assertEquals(1, providers.size)
        // Unordered on purpose: `org.json`'s JVM map is a HashMap, so the document
        // order the engine sees is an Android-side detail this test must not assert.
        assertEquals(setOf("m/one", "m/two"), providers.single().models.toSet())
    }
}
