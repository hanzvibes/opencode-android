package dev.bidzz.opencode.engine

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Antigravity / config-file fixes, audit-100 Fase 3:
 *
 * - 30-F3 / 38-F2: `readRoot()` only stripped full-line `//` comments, so any
 *   other valid JSONC (trailing commas, block comments, inline comments) made
 *   the whole config unreadable and the UI silently showed an empty list.
 * - 30-F5 / 38-F3: the app read `opencode.json` first while the engine reads
 *   `opencode.jsonc` first, so the UI showed providers the engine ignores.
 * - 38-F10: writing when only `.jsonc` existed copied its content into a
 *   shadowed `.json` and dropped the user's comments.
 * - 31-F5 / 38-F4: `activeAntigravityAccount()` ignored the file's `activeIndex`.
 * - 31-F10 / 38-F5: a corrupt accounts file silently looked like "no accounts".
 */
class EngineConfigStoreJsoncTest {

    @JvmField
    @Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private fun storeWith(vararg files: Pair<String, String>): Pair<EngineConfigStore, File> {
        val configHome = temp.newFolder("config-home")
        val ocDir = File(configHome, "opencode").apply { mkdirs() }
        for ((name, text) in files) File(ocDir, name).writeText(text)
        return EngineConfigStore(configHome) to ocDir
    }

    // stripJsonc (30-F3 / 38-F2) -------------------------------------------

    @Test
    fun `stripJsonc removes block comments, inline comments and trailing commas`() {
        val cleaned = EngineConfigStore.stripJsonc(
            """
            {
              // a full-line comment
              "a": 1, /* a block comment */
              "b": [1, 2,], // trailing comment after a value
              "c": { "d": 2, },
            }
            """.trimIndent(),
        )
        val obj = org.json.JSONObject(cleaned)
        assertEquals(1, obj.getInt("a"))
        assertEquals(2, obj.getJSONArray("b").length())
        assertEquals(2, obj.getJSONObject("c").getInt("d"))
    }

    @Test
    fun `stripJsonc keeps comment markers inside strings`() {
        val cleaned = EngineConfigStore.stripJsonc(
            """{"url": "https://example.com/a//b", "note": "not /* a comment */", "n": 1,}""",
        )
        val obj = org.json.JSONObject(cleaned)
        assertEquals("https://example.com/a//b", obj.getString("url"))
        assertEquals("not /* a comment */", obj.getString("note"))
        assertEquals(1, obj.getInt("n"))
    }

    @Test
    fun `stripJsonc drops a trailing comma hidden behind a comment`() {
        val cleaned = EngineConfigStore.stripJsonc(
            """
            {
              "a": 1, // the comma before this comment used to survive
            }
            """.trimIndent(),
        )
        assertEquals(1, org.json.JSONObject(cleaned).getInt("a"))
    }

    @Test
    fun `stripJsonc strips a leading BOM`() {
        val cleaned = EngineConfigStore.stripJsonc("\uFEFF{\"a\": 1,}")
        assertEquals(1, org.json.JSONObject(cleaned).getInt("a"))
    }

    // .json vs .jsonc (30-F5 / 38-F3 / 38-F10) ------------------------------

    @Test
    fun `read prefers opencode jsonc, the file the engine reads`() {
        val (store, _) = storeWith(
            "opencode.json" to """{"providers": {"from-json": {"name": "From JSON", "settings": {"baseURL": "https://json.invalid"}, "models": {}}}}""",
            "opencode.jsonc" to """{"providers": {"from-jsonc": {"name": "From JSONC", "settings": {"baseURL": "https://jsonc.invalid"}, "models": {}}}}""",
        )
        val providers = store.customProviders()
        assertEquals(listOf("from-jsonc"), providers.map { it.id })
    }

    @Test
    fun `a JSONC config with comments and trailing commas parses`() {
        val (store, _) = storeWith(
            "opencode.jsonc" to """
                {
                  // my providers
                  "providers": {
                    "probe": {
                      "name": "Probe",
                      "settings": { "baseURL": "https://probe.invalid", },
                      "models": { "gpt-4": {}, },
                    },
                  },
                }
            """.trimIndent(),
        )
        val providers = store.customProviders()
        assertEquals(1, providers.size)
        assertEquals("probe", providers[0].id)
        assertEquals("https://probe.invalid", providers[0].baseURL)
        assertEquals(listOf("gpt-4"), providers[0].models)
        assertNull(store.lastConfigReadProblem)
    }

    @Test
    fun `writing when only jsonc exists is refused and the jsonc file is untouched`() {
        val (store, ocDir) = storeWith(
            "opencode.jsonc" to """{"providers": {}}""",
        )
        val before = File(ocDir, "opencode.jsonc").readText()
        try {
            store.putOpenAICompatible("probe", "Probe", "https://probe.invalid", "key", emptyList())
            fail("expected JsoncBlockedException")
        } catch (e: EngineConfigStore.JsoncBlockedException) {
            // expected: the app never writes .jsonc
        }
        assertEquals(before, File(ocDir, "opencode.jsonc").readText())
        assertFalse(File(ocDir, "opencode.json").exists())
    }

    @Test
    fun `deleting the jsonc file unblocks writing to json`() {
        val (store, ocDir) = storeWith(
            "opencode.jsonc" to """{"providers": {}}""",
        )
        assertEquals(EngineConfigStore.JsoncDeleteResult.Deleted, store.deleteJsoncConfig())
        assertFalse(File(ocDir, "opencode.jsonc").exists())
        val result = store.putOpenAICompatible("probe", "Probe", "https://probe.invalid", "key", emptyList())
        assertEquals(EngineConfigStore.WriteResult.Saved("probe"), result)
        assertTrue(File(ocDir, "opencode.json").exists())
    }

    @Test
    fun `a malformed config reports which file failed instead of an empty list`() {
        // Truncated JSON: no amount of JSONC stripping repairs an unterminated
        // object. (A lone "{,}" would be repaired by the trailing-comma strip,
        // so it is not a malformed fixture.)
        // readRoot() throws a descriptive IOException; the ViewModel turns that
        // into an empty list via runCatching and shows lastConfigReadProblem.
        val (store, _) = storeWith(
            "opencode.json" to """{"providers": {"probe": {"name": "Probe", "settings": {"baseURL": "https://x"""",
        )
        val thrown = assertThrows(IOException::class.java) { store.customProviders() }
        assertTrue(thrown.message?.contains("opencode.json") == true)
        val problem = store.lastConfigReadProblem
        assertNotNull(problem)
        assertEquals("opencode.json", problem?.fileName)
        assertTrue(problem?.reason?.isNotBlank() == true)
    }

    @Test
    fun `writing to a plain json config still works`() {
        val (store, ocDir) = storeWith(
            "opencode.json" to """{"providers": {}}""",
        )
        val result = store.putOpenAICompatible(
            "probe", "Probe", "https://probe.invalid/", "key",
            listOf(EngineConfigStore.ModelSpec("gpt-4")),
        )
        assertEquals(EngineConfigStore.WriteResult.Saved("probe"), result)
        val written = org.json.JSONObject(File(ocDir, "opencode.json").readText())
        assertEquals("https://probe.invalid", written.getJSONObject("providers").getJSONObject("probe").getJSONObject("settings").getString("baseURL"))
    }

    // Antigravity accounts (31-F5 / 38-F4, 31-F10 / 38-F5) ------------------

    private fun accountsJson(activeIndex: Int?, vararg accounts: String): String = buildString {
        append("{ \"version\": 1,")
        if (activeIndex != null) append(" \"activeIndex\": $activeIndex,")
        append(" \"accounts\": [")
        append(accounts.joinToString(","))
        append("] }")
    }

    private fun account(id: String, email: String, enabled: Boolean = true, token: String = "tok"): String =
        """{"id": "$id", "email": "$email", "enabled": $enabled, "refreshToken": "$token"}"""

    @Test
    fun `activeIndex is honoured when it points at a usable account`() {
        val (store, _) = storeWith(
            "antigravity-accounts.json" to accountsJson(
                1,
                account("a1", "first@example.com"),
                account("a2", "second@example.com"),
            ),
        )
        assertEquals("second@example.com", store.activeAntigravityAccount()?.email)
    }

    @Test
    fun `activeIndex pointing at a disabled account falls back to the first usable one`() {
        val (store, _) = storeWith(
            "antigravity-accounts.json" to accountsJson(
                1,
                account("a1", "first@example.com"),
                account("a2", "second@example.com", enabled = false),
            ),
        )
        assertEquals("first@example.com", store.activeAntigravityAccount()?.email)
    }

    @Test
    fun `an out-of-range activeIndex falls back to the first usable account`() {
        val (store, _) = storeWith(
            "antigravity-accounts.json" to accountsJson(
                9,
                account("a1", "first@example.com"),
            ),
        )
        assertEquals("first@example.com", store.activeAntigravityAccount()?.email)
    }

    @Test
    fun `switching the active account writes activeIndex and survives a re-read`() {
        val configHome = temp.newFolder("config-home-switch")
        val ocDir = File(configHome, "opencode").apply { mkdirs() }
        File(ocDir, "antigravity-accounts.json").writeText(
            accountsJson(
                0,
                account("a1", "first@example.com"),
                account("a2", "second@example.com"),
            ),
        )
        val store = EngineConfigStore(configHome)
        assertTrue(store.setAntigravityActiveAccount("a2"))
        assertEquals("second@example.com", store.activeAntigravityAccount()?.email)
        // A fresh store over the same directory sees the same active account.
        assertEquals("second@example.com", EngineConfigStore(configHome).activeAntigravityAccount()?.email)
    }

    @Test
    fun `disabling the active account makes the next usable account active`() {
        val (store, _) = storeWith(
            "antigravity-accounts.json" to accountsJson(
                0,
                account("a1", "first@example.com"),
                account("a2", "second@example.com"),
            ),
        )
        assertTrue(store.setAntigravityAccountEnabled("a1", false))
        assertEquals("second@example.com", store.activeAntigravityAccount()?.email)
        assertFalse(store.antigravityAccounts().first { it.id == "a1" }.enabled)
    }

    @Test
    fun `switching to an unknown account id fails without touching the file`() {
        val (store, ocDir) = storeWith(
            "antigravity-accounts.json" to accountsJson(0, account("a1", "first@example.com")),
        )
        val before = File(ocDir, "antigravity-accounts.json").readText()
        assertFalse(store.setAntigravityActiveAccount("nope"))
        assertEquals(before, File(ocDir, "antigravity-accounts.json").readText())
    }

    @Test
    fun `a corrupt accounts file is Corrupt, not an empty list`() {
        val (store, _) = storeWith(
            "antigravity-accounts.json" to """{"accounts": [{"email": "a@example.com", """,
        )
        val result = store.antigravityAccountsResult()
        assertTrue(result is EngineConfigStore.AntigravityAccountsResult.Corrupt)
        assertEquals(emptyList<EngineConfigStore.AntigravityAccount>(), store.antigravityAccounts())
        assertNull(store.activeAntigravityAccount())
    }

    @Test
    fun `a missing accounts file is Missing, not Corrupt`() {
        val (store, _) = storeWith()
        assertEquals(
            EngineConfigStore.AntigravityAccountsResult.Missing,
            store.antigravityAccountsResult(),
        )
    }

    // deleteJsoncConfig (audit-200 A7, DL-002) ---------------------------

    @Test
    fun `deleting opencode_jsonc writes an identical bak backup before deleting`() {
        val handWritten = """
            {
              // a provider the app never wrote
              "provider": { "custom": { "options": { "apiKey": "k" } } },
              "mcp": { "local": { "type": "local", "command": ["npx", "x"] } }
            }
        """.trimIndent()
        val (store, dir) = storeWith("opencode.jsonc" to handWritten)

        assertEquals(EngineConfigStore.JsoncDeleteResult.Deleted, store.deleteJsoncConfig())

        assertFalse(File(dir, "opencode.jsonc").exists())
        assertEquals(handWritten, File(dir, "opencode.jsonc.bak").readText())
    }

    @Test
    fun `deleting a missing jsonc reports Missing and writes no backup`() {
        val (store, dir) = storeWith()

        assertEquals(EngineConfigStore.JsoncDeleteResult.Missing, store.deleteJsoncConfig())

        assertFalse(File(dir, "opencode.jsonc.bak").exists())
    }

    @Test
    fun `deleting overwrites a stale bak with the current file`() {
        val (store, dir) = storeWith(
            "opencode.jsonc" to """{ "provider": { "new": {} } }""",
            "opencode.jsonc.bak" to """{ "provider": { "old": {} } }""",
        )

        assertEquals(EngineConfigStore.JsoncDeleteResult.Deleted, store.deleteJsoncConfig())

        assertEquals(
            """{ "provider": { "new": {} } }""",
            File(dir, "opencode.jsonc.bak").readText(),
        )
    }

    @Test
    fun `deleting the jsonc leaves a shadow opencode_json untouched`() {
        val shadow = """{ "provider": { "stale": {} } }"""
        val (store, dir) = storeWith(
            "opencode.jsonc" to """{ "provider": { "live": {} } }""",
            "opencode.json" to shadow,
        )

        assertEquals(EngineConfigStore.JsoncDeleteResult.Deleted, store.deleteJsoncConfig())
        assertTrue(store.hasJsonConfig())

        assertEquals(shadow, File(dir, "opencode.json").readText())
    }

    @Test
    fun `hasJsonConfig is false when no opencode_json exists`() {
        val (store, _) = storeWith("opencode.jsonc" to "{}")

        assertFalse(store.hasJsonConfig())
    }
}
