package dev.bidzz.opencode.engine

import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Locale
import org.json.JSONObject

/**
 * Reads and writes the engine's own global config, `…/.config/opencode/opencode.json`.
 *
 * This lives in the app's private storage, so the app needs no permission to write it —
 * which is the whole point. Until now a provider with its own base URL could only be
 * configured by pushing the file with `run-as`, i.e. on a debug build, so a released
 * install had no way off opencode's free tier.
 *
 * Two rules this class exists to honour:
 *
 * - **Merge, never replace.** The file can hold keys the app knows nothing about;
 *   rewriting it from scratch would silently delete them.
 * - **Write atomically.** The engine parses this at startup, so a half-written file is
 *   a broken launch, not a recoverable glitch. Temp file plus rename, so a reader sees
 *   either the old content or the new.
 */
class EngineConfigStore(configHome: File) {

    private val dir = File(configHome, "opencode")
    private val file = File(dir, "opencode.json")
    private val jsoncFile = File(dir, "opencode.jsonc")
    private val antigravityAccountsFile = File(dir, "antigravity-accounts.json")

    /** Stored Google Antigravity OAuth account from `antigravity-accounts.json`. */
    data class AntigravityAccount(
        val id: String,
        val email: String,
        val enabled: Boolean = true,
        val projectId: String? = null,
        val hasRefreshToken: Boolean = false,
    )

    /** What reading `antigravity-accounts.json` found (audit-100 31-F10/38-F5). */
    sealed interface AntigravityAccountsResult {
        /** No accounts file at all — OAuth was never set up. */
        data object Missing : AntigravityAccountsResult

        /** The file parsed; the list may be empty (an account-less but valid file). */
        data class Ok(val accounts: List<AntigravityAccount>) : AntigravityAccountsResult

        /** The file exists but could not be read or parsed — not the same as "no accounts". */
        data class Corrupt(val reason: String) : AntigravityAccountsResult
    }

    /** Why the engine config could not be read, if the last read failed (audit-100 30-F3). */
    data class ConfigReadProblem(val fileName: String, val reason: String)

    /** Set by the last [readRoot] attempt: null when the last read succeeded (or nothing was read). */
    @Volatile
    var lastConfigReadProblem: ConfigReadProblem? = null
        private set

    /** One custom provider as stored in the config file. Never carries the key. */
    data class CustomProvider(
        val id: String,
        val name: String,
        val baseURL: String,
        val models: List<String>,
        /**
         * True when the stored entry's `settings` or `models` is present but
         * not an object (38-F9): a hand-edited config may hold e.g.
         * `"settings": "disabled"` or `"models": ["gpt-4"]`. The old code
         * silently showed an empty base URL and no models, as if the
         * provider were unconfigured; the row now shows a warning instead.
         */
        val malformed: Boolean = false,
        /**
         * True when the entry itself is not an object (audit-200 B4,
         * M-014): a hand-edited config may hold e.g.
         * `"old": "disabled"`. The old code skipped it entirely, so it
         * was invisible, could not be deleted from the UI, and blocked
         * creating a provider with that name (the write refused to
         * replace it). The row shows it as a broken entry with Delete
         * instead of Edit.
         */
        val nonObject: Boolean = false,
    )

    /**
     * A model the user listed: its id, and the name to show for it.
     *
     * [name] ends up in `models[id].name`, which the engine copies onto the model record
     * (`config/plugin/provider.ts`: `if (config.name) model.name = config.name`) and the
     * chat picker reads. Without it the picker falls back to the raw, namespaced id.
     */
    data class ModelSpec(val id: String, val name: String? = null)

    /** Outcome of a write. */
    sealed interface WriteResult {
        data class Saved(val providerID: String) : WriteResult
    }

    /**
     * The effective config lives in `opencode.jsonc`, which this class reads but
     * never writes (audit-100 30-F5/38-F10): writing the merged root into
     * `opencode.json` would leave a shadowed copy the engine ignores — and
     * silently drop the user's comments from that copy. Refusing is honest;
     * the UI offers deleting the `.jsonc` file instead.
     */
    class JsoncBlockedException : IOException(
        "Providers are stored in opencode.jsonc, which the app does not write. " +
            "Delete opencode.jsonc to let the app manage providers in opencode.json.",
    )

    /** Providers already declared in the config file, in document order. */
    fun customProviders(): List<CustomProvider> {
        val root = readRoot() ?: return emptyList()
        val providers = root.optJSONObject("providers") ?: return emptyList()
        return providers.keys().asSequence().map { id ->
            val entry = providers.optJSONObject(id)
            if (entry == null) {
                // Audit-200 B4 (M-014): a non-object entry is still a
                // provider row — with no URL or models to edit, and a
                // Delete instead of an edit form. Skipping it hid it
                // from the list while it kept blocking its name.
                Log.w(TAG, "custom provider '$id' is not an object; showing as a broken entry")
                return@map CustomProvider(
                    id = id,
                    name = id,
                    baseURL = "",
                    models = emptyList(),
                    malformed = true,
                    nonObject = true,
                )
            }
            val settings = entry.optJSONObject("settings")
            val modelsObj = entry.optJSONObject("models")
            // A hand-edited config may hold settings/models in a non-object
            // shape (38-F9): the old code silently showed an empty base URL
            // and no models, as if the provider were unconfigured. Flag it
            // instead — the row shows a warning and the log names the entry.
            val malformed = (entry.has("settings") && settings == null) ||
                (entry.has("models") && modelsObj == null)
            if (malformed) {
                Log.w(TAG, "custom provider '$id' has non-object settings/models; showing as malformed")
            }
            CustomProvider(
                id = id,
                name = entry.optString("name").ifEmpty { id },
                baseURL = settings?.optString("baseURL").orEmpty(),
                models = modelsObj?.keys()?.asSequence()?.toList() ?: emptyList(),
                malformed = malformed,
            )
        }.toList()
    }

    /** Raw read of the accounts file: one slot per array entry, in file order. */
    private sealed interface AntigravityRead {
        data object Missing : AntigravityRead
        data class Corrupt(val reason: String) : AntigravityRead
        data class Parsed(val slots: List<AntigravityAccount?>, val activeIndex: Int?) : AntigravityRead
    }

    private fun readAntigravity(): AntigravityRead {
        if (!antigravityAccountsFile.isFile) return AntigravityRead.Missing
        val text = runCatching { antigravityAccountsFile.readText() }.getOrNull()
            ?: return AntigravityRead.Corrupt("could not read the file")
        if (text.isBlank()) return AntigravityRead.Parsed(emptyList(), null)
        return try {
            val root = JSONObject(text)
            val accounts = root.optJSONArray("accounts")
            val activeIndex = if (root.has("activeIndex")) {
                root.optInt("activeIndex", -1).takeIf { it >= 0 }
            } else {
                null
            }
            if (accounts == null) return AntigravityRead.Parsed(emptyList(), activeIndex)
            val slots = (0 until accounts.length()).map { i ->
                val obj = accounts.optJSONObject(i) ?: return@map null
                val email = obj.optString("email").ifEmpty { return@map null }
                AntigravityAccount(
                    id = obj.optString("id").ifEmpty { email },
                    email = email,
                    enabled = obj.optBoolean("enabled", true),
                    projectId = obj.optString("projectId").takeIf { it.isNotBlank() },
                    hasRefreshToken = obj.optString("refreshToken").isNotBlank(),
                )
            }
            AntigravityRead.Parsed(slots, activeIndex)
        } catch (e: Exception) {
            Log.w(TAG, "failed reading antigravity accounts", e)
            AntigravityRead.Corrupt(e.message ?: e.javaClass.simpleName)
        }
    }

    /** List of active/saved Google Antigravity OAuth accounts. */
    fun antigravityAccounts(): List<AntigravityAccount> =
        (antigravityAccountsResult() as? AntigravityAccountsResult.Ok)?.accounts ?: emptyList()

    /**
     * What reading `antigravity-accounts.json` found. Unlike [antigravityAccounts],
     * this distinguishes "no file" from "file exists but is corrupt" (audit-100
     * 31-F10/38-F5): a corrupt file must not silently look like "never set up".
     */
    fun antigravityAccountsResult(): AntigravityAccountsResult = when (val read = readAntigravity()) {
        is AntigravityRead.Missing -> AntigravityAccountsResult.Missing
        is AntigravityRead.Corrupt -> AntigravityAccountsResult.Corrupt(read.reason)
        is AntigravityRead.Parsed -> AntigravityAccountsResult.Ok(read.slots.filterNotNull())
    }

    /**
     * Active Google Antigravity OAuth account if one is configured and enabled.
     *
     * The file's own `activeIndex` wins when it points at a usable account
     * (audit-100 31-F5/38-F4); otherwise this falls back to the first enabled
     * account with a refresh token, as before. `activeIndex` indexes the file's
     * `accounts` array, so an entry without an email still occupies its slot.
     */
    fun activeAntigravityAccount(): AntigravityAccount? {
        val read = readAntigravity() as? AntigravityRead.Parsed ?: return null
        val indexed = read.activeIndex?.let { read.slots.getOrNull(it) }
        if (indexed != null && indexed.enabled && indexed.hasRefreshToken) return indexed
        return read.slots.filterNotNull().firstOrNull { it.enabled && it.hasRefreshToken }
    }

    /**
     * Points the accounts file's `activeIndex` at the account with this id
     * (audit-100 31-F5). Returns false when the id is unknown or the file
     * cannot be read or written. The caller restarts the engine so the plugin
     * picks the account up.
     */
    fun setAntigravityActiveAccount(id: String): Boolean = updateAntigravityFile { root ->
        val accounts = root.optJSONArray("accounts") ?: return@updateAntigravityFile false
        val index = (0 until accounts.length()).indexOfFirst { i ->
            val obj = accounts.optJSONObject(i)
            obj != null && obj.optString("id").ifEmpty { obj.optString("email") } == id
        }
        if (index < 0) return@updateAntigravityFile false
        root.put("activeIndex", index)
        true
    }

    /** Enables or disables one Antigravity account in the file (audit-100 31-F5). */
    fun setAntigravityAccountEnabled(id: String, enabled: Boolean): Boolean = updateAntigravityFile { root ->
        val accounts = root.optJSONArray("accounts") ?: return@updateAntigravityFile false
        for (i in 0 until accounts.length()) {
            val obj = accounts.optJSONObject(i) ?: continue
            if (obj.optString("id").ifEmpty { obj.optString("email") } == id) {
                obj.put("enabled", enabled)
                return@updateAntigravityFile true
            }
        }
        false
    }

    /** Result of deleting `opencode.jsonc` (audit-200 A7, DL-002). */
    sealed interface JsoncDeleteResult {
        /** The file is gone; a backup was written to `opencode.jsonc.bak` first. */
        data object Deleted : JsoncDeleteResult

        /** There was no `opencode.jsonc` to delete — nothing changed. */
        data object Missing : JsoncDeleteResult

        /** The backup or the delete failed — the file is still there. */
        data object Failed : JsoncDeleteResult
    }

    /** Whether `opencode.json` exists — it becomes the effective config once the `.jsonc` is gone. */
    fun hasJsonConfig(): Boolean = file.isFile

    /**
     * Provider ids declared in `opencode.json` while the effective config
     * lives in `opencode.jsonc` (audit-200 B4, M-248): the engine reads
     * only the `.jsonc`, so these providers are shadowed — invisible in
     * the list and never loaded. Empty when there is no `.jsonc` (the
     * `.json` IS the effective file then) or when the `.json` cannot be
     * read. The UI names them and offers the delete-`.jsonc` flow, which
     * is the only way to bring them back.
     */
    fun shadowedJsonProviderIds(): List<String> {
        if (!jsoncFile.isFile || !file.isFile) return emptyList()
        val root = runCatching { JSONObject(stripJsonc(file.readText())) }.getOrNull() ?: return emptyList()
        val providers = root.optJSONObject("providers") ?: return emptyList()
        return providers.keys().asSequence().toList()
    }

    /**
     * Deletes `opencode.jsonc` so the app can manage providers in `opencode.json`
     * again (30-F5/38-F10).
     *
     * Audit-200 A7 (DL-002): the file may hold settings written by hand —
     * providers and MCP blocks the app never sees — so a backup is written
     * to `opencode.jsonc.bak` FIRST, and the file is only deleted when the
     * backup landed. A failed backup refuses the delete instead of
     * destroying the only copy.
     */
    fun deleteJsoncConfig(): JsoncDeleteResult {
        if (!jsoncFile.isFile) return JsoncDeleteResult.Missing
        val backup = File(dir, "opencode.jsonc.bak")
        val backedUp = runCatching { jsoncFile.copyTo(backup, overwrite = true) }.isSuccess
        if (!backedUp) {
            Log.w(TAG, "Refusing to delete opencode.jsonc: the .bak backup failed")
            return JsoncDeleteResult.Failed
        }
        return if (runCatching { jsoncFile.delete() }.getOrDefault(false)) {
            JsoncDeleteResult.Deleted
        } else {
            Log.w(TAG, "Could not delete opencode.jsonc after writing the .bak backup")
            JsoncDeleteResult.Failed
        }
    }

    private fun updateAntigravityFile(mutator: (JSONObject) -> Boolean): Boolean {
        if (!antigravityAccountsFile.isFile) return false
        val root = runCatching { JSONObject(antigravityAccountsFile.readText()) }.getOrNull() ?: return false
        if (!mutator(root)) return false
        return runCatching {
            writeAtomically(antigravityAccountsFile, root.toString(INDENT) + "\n")
        }.isSuccess
    }

    /** Whether Google Antigravity OAuth is configured with at least one active account. */
    fun hasActiveAntigravity(): Boolean = activeAntigravityAccount() != null

    /** List of plugins declared in opencode.json or opencode.jsonc. */
    fun plugins(): List<String> {
        val root = readRoot() ?: return emptyList()
        val arr = root.optJSONArray("plugins") ?: return emptyList()
        return (0 until arr.length()).mapNotNull {
            val p = arr.optString(it)
            if (p.isNullOrBlank()) null else p
        }
    }

    /**
     * Adds or replaces an OpenAI-compatible provider entry.
     *
     * [id] becomes the provider key the model IDs are namespaced under, so it must be a
     * slug; [apiKey] is written into `settings` because that is where this engine version
     * reads it — `Config.Provider.Info` has no `apiKey` field of its own. A `null`
     * [apiKey] keeps the stored key (30-F2): editing a provider must not force the
     * user to retype a key the app never shows them. Saving a *new* provider with a
     * `null` key is refused — the caller validates first, so this is a guard, not a path.
     *
     * Audit-200 B4 (M-250/M-507): the entry is MERGED into the stored one, not
     * rebuilt from scratch. The old code wrote a fresh object with only the
     * fields the form owns, so hand-written keys inside the entry — an extra
     * `settings` key, a per-model `limit`, a top-level `disabled` — vanished
     * on every edit. Only the form's own fields are set now (name, package,
     * settings.baseURL, settings.apiKey, and the listed models); a model
     * removed from the form still leaves the map, and a model whose name the
     * form clears loses its `name` key.
     *
     * [previousId] renames in the same write (M-126): when the provider
     * being edited got a new slug, the old entry is removed with the new one
     * — two writes could leave both entries behind if the app died between
     * them. Null (or equal to [id]) means no rename.
     */
    @Throws(IOException::class)
    fun putOpenAICompatible(
        id: String,
        name: String,
        baseURL: String,
        apiKey: String?,
        models: List<ModelSpec>,
        previousId: String? = null,
    ): WriteResult {
        val (rootOrNull, source) = readRootAndSource()
        val root = rootOrNull ?: JSONObject()
        // The app only writes opencode.json. When the effective config lives in
        // opencode.jsonc, writing would copy its content into a shadowed .json
        // (dropping the user's comments) while the engine keeps reading .jsonc.
        if (source == jsoncFile) throw JsoncBlockedException()
        // "providers" must be an object to merge into: a hand-made config
        // may hold a string, an array or null there, and replacing it with
        // a fresh object would silently delete it (audit-70 38-F4). Refuse
        // instead — the caller reports the write as failed and nothing is
        // lost. The same holds for the entry itself: an id whose value is
        // not an object (e.g. "old": "disabled") is listed as a broken
        // entry now (M-014), so the user can delete it first — overwriting
        // it here would still delete a hand-made entry without warning.
        if (root.has("providers") && root.optJSONObject("providers") == null) {
            Log.w(TAG, "refusing to replace a non-object 'providers' value")
            throw IOException("Existing config has a non-object 'providers' value; refusing to replace it")
        }
        val providers = root.optJSONObject("providers") ?: JSONObject().also { root.put("providers", it) }
        if (providers.has(id) && providers.optJSONObject(id) == null) {
            Log.w(TAG, "refusing to replace non-object provider entry '$id'")
            throw IOException("Existing provider '$id' is not an object; refusing to replace it")
        }

        // Merge into the stored entry (M-250/M-507): unknown keys survive
        // the edit. A fresh object is used only for a brand-new provider.
        // A rename (M-126) merges from the OLD entry, not the new id:
        // looking the key up under the new id would find nothing and the
        // edit would be refused for "no API key" even though the old entry
        // has one — and the old entry's hand-written keys would be lost
        // with it.
        val entry = (if (previousId != null) providers.optJSONObject(previousId) else null)
            ?: providers.optJSONObject(id)
            ?: JSONObject()
        entry.put("name", name)
        entry.put("package", PACKAGE)
        val settings = entry.optJSONObject("settings") ?: JSONObject()
        settings.put("baseURL", baseURL.trimEnd('/'))
        if (apiKey != null) {
            settings.put("apiKey", apiKey)
        } else {
            // Editing without retyping the key (30-F2): keep the stored one.
            val kept = settings.optString("apiKey")
            if (kept.isNullOrEmpty()) throw IOException("Cannot save provider '$id' without an API key")
            settings.put("apiKey", kept)
        }
        entry.put("settings", settings)
        // The model list is the form's: a model removed from the form
        // leaves the map, but a model that stays keeps the keys the form
        // never shows (M-250) — only its `name` is set or cleared.
        val modelMap = JSONObject()
        models.forEach { spec ->
            val model = entry.optJSONObject("models")?.optJSONObject(spec.id) ?: JSONObject()
            // A bare `{}` means "inherit whatever the provider knows about this model";
            // `name` is only written when the user gave one, so an unnamed model keeps
            // the engine's own answer instead of being pinned to an empty string.
            // Clearing a name the entry already had removes the key, so the
            // old name does not stick after the user deleted it (M-250).
            if (spec.name != null) model.put("name", spec.name) else model.remove("name")
            modelMap.put(spec.id, model)
        }
        entry.put("models", modelMap)
        providers.put(id, entry)
        if (previousId != null && previousId != id) {
            providers.remove(previousId)
        }

        write(root, source)
        Log.i(TAG, "configured custom provider '$id' -> $baseURL (${models.size} models)")
        return WriteResult.Saved(id)
    }

    /**
     * Removes a custom provider entry (30-F2). Returns false when the id is
     * not present. Refuses when the effective config lives in `.jsonc`, like
     * [putOpenAICompatible] — deleting from the shadowed `.json` would change
     * nothing the engine reads, so the caller reports it as a blocked write.
     */
    @Throws(IOException::class)
    fun deleteCustomProvider(id: String): Boolean {
        val (rootOrNull, source) = readRootAndSource()
        val root = rootOrNull ?: return false
        if (source == jsoncFile) throw JsoncBlockedException()
        val providers = root.optJSONObject("providers") ?: return false
        if (!providers.has(id)) return false
        providers.remove(id)
        write(root, source)
        Log.i(TAG, "removed custom provider '$id'")
        return true
    }

    private fun readRoot(): JSONObject? = readRootAndSource().first

    /**
     * Reads the effective config and says which file it came from. The engine's
     * config discovery picks the *last* existing candidate, so a sibling
     * `opencode.jsonc` wins over `opencode.json` (audit-100 30-F5/38-F3) — the
     * app must read the same file the engine reads, or it shows providers the
     * engine will never load. A failed read records [lastConfigReadProblem] and
     * throws, so the UI can name the file instead of showing an empty list.
     */
    private fun readRootAndSource(): Pair<JSONObject?, File?> {
        val target = when {
            jsoncFile.isFile -> jsoncFile
            file.isFile -> file
            else -> return null to null
        }
        val text = target.readText()
        if (text.isBlank()) {
            lastConfigReadProblem = null
            return JSONObject() to target
        }
        return try {
            lastConfigReadProblem = null
            JSONObject(stripJsonc(text)) to target
        } catch (e: org.json.JSONException) {
            // A malformed file the app did not write is a real problem, but overwriting
            // it silently would destroy something the user made by hand.
            Log.e(TAG, "config unreadable, refusing to merge into it", e)
            lastConfigReadProblem = ConfigReadProblem(target.name, e.message ?: e.javaClass.simpleName)
            throw IOException("Existing config file ${target.name} is not valid JSON: ${e.message}")
        }
    }

    /**
     * Writes the merged config back to `opencode.json`.
     *
     * Audit-200 B4 (M-248/M-249): a hand-edited `.json` may hold comments —
     * the reader strips them to parse (30-F3), but the old write then
     * re-serialized the parsed object and the first save wiped every
     * comment in the file. When [source] still has comments, only the
     * `providers` value is replaced inside the raw text, so comments
     * anywhere else in the file survive byte for byte. Comments *inside*
     * the `providers` object are still lost — they sit in the replaced
     * span — and a file whose `providers` span cannot be located is
     * refused rather than rewritten without its comments.
     */
    private fun write(root: JSONObject, source: File?) {
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create ${dir.absolutePath}")
        val raw = source?.takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }
        val text = if (raw != null && stripJsonc(raw) != raw) {
            replaceProvidersValue(raw, root.optJSONObject("providers")?.toString(INDENT) ?: "{}")
                ?: throw IOException(
                    "Existing config ${source.name} has comments the app cannot preserve; " +
                        "refusing to rewrite it",
                )
        } else {
            root.toString(INDENT) + "\n"
        }
        writeAtomically(file, text)
    }

    /**
     * Replaces the top-level `"providers"` value inside [raw] with
     * [providersJson], leaving every other byte untouched. Returns null
     * when the key cannot be located confidently — the caller refuses
     * the write instead of guessing.
     *
     * Comment-aware: the raw text is JSONC, so a `"` inside a `//` or
     * `/* */` comment must not toggle string tracking, or the scan
     * desyncs and replaces the wrong span.
     */
    internal fun replaceProvidersValue(raw: String, providersJson: String): String? {
        // Find the root object, then the "providers" key at depth 1.
        var i = 0
        while (i < raw.length && raw[i].isWhitespace()) i++
        if (i >= raw.length || raw[i] != '{') return null
        var depth = 0
        var inString = false
        var keyStart = -1
        var j = i
        while (j < raw.length) {
            val c = raw[j]
            if (inString) {
                if (c == '\\') {
                    j += 2
                    continue
                }
                if (c == '"') inString = false
                j++
                continue
            }
            // A comment outside a string: skip it whole, quotes and all.
            if (c == '/' && j + 1 < raw.length && raw[j + 1] == '/') {
                while (j < raw.length && raw[j] != '\n') j++
                continue
            }
            if (c == '/' && j + 1 < raw.length && raw[j + 1] == '*') {
                val end = raw.indexOf("*/", j + 2)
                j = if (end < 0) raw.length else end + 2
                continue
            }
            when (c) {
                '"' -> {
                    if (depth == 1 && keyStart < 0) keyStart = j
                    inString = true
                    j++
                }
                '{', '[' -> {
                    depth++
                    j++
                }
                '}', ']' -> {
                    depth--
                    j++
                }
                ':' -> {
                    if (depth == 1 && keyStart >= 0) {
                        val key = raw.substring(keyStart, j).trim()
                        // The key text still carries its quotes; compare
                        // with them on so an escaped name cannot match.
                        if (key == "\"providers\"") {
                            var k = j + 1
                            while (k < raw.length && raw[k].isWhitespace()) k++
                            if (k >= raw.length || raw[k] != '{') return null
                            val end = findValueEnd(raw, k) ?: return null
                            return raw.substring(0, k) + providersJson + raw.substring(end)
                        }
                        keyStart = -1
                    }
                    j++
                }
                ',' -> {
                    if (depth == 1) keyStart = -1
                    j++
                }
                else -> j++
            }
        }
        // No "providers" key: insert one after the root's opening brace.
        val insertAt = i + 1
        val rest = raw.substring(insertAt).trimStart()
        val prefix = if (rest.startsWith("}")) {
            "\"providers\": $providersJson"
        } else {
            "\"providers\": $providersJson, "
        }
        return raw.substring(0, insertAt) + prefix + raw.substring(insertAt)
    }

    /**
     * End index (exclusive) of the JSON value starting at [start],
     * string- and comment-aware (a `{` inside a comment inside the
     * providers object must not move the depth, or the replaced span
     * would swallow the rest of the file). Returns null on unbalanced input.
     */
    private fun findValueEnd(raw: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var j = start
        while (j < raw.length) {
            val c = raw[j]
            if (inString) {
                if (c == '\\') {
                    j += 2
                    continue
                }
                if (c == '"') inString = false
                j++
                continue
            }
            if (c == '/' && j + 1 < raw.length && raw[j + 1] == '/') {
                while (j < raw.length && raw[j] != '\n') j++
                continue
            }
            if (c == '/' && j + 1 < raw.length && raw[j + 1] == '*') {
                val end = raw.indexOf("*/", j + 2)
                j = if (end < 0) raw.length else end + 2
                continue
            }
            when (c) {
                '"' -> {
                    inString = true
                    j++
                }
                '{', '[' -> {
                    depth++
                    j++
                }
                '}', ']' -> {
                    depth--
                    j++
                    if (depth == 0) return j
                }
                else -> j++
            }
        }
        return null
    }

    /**
     * Temp file + rename in the same directory, so a reader sees the old or the
     * new content, never a half-written file. Falls back to a plain rename where
     * the filesystem refuses an atomic move (audit-100 38-F8).
     */
    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        // The file holds provider API keys, so keep it owner-only before any content lands.
        // ponytail: plaintext at rest in app-private storage; the engine reads `settings.apiKey`
        // from this file, so encrypting needs engine support (env/file substitution) first.
        tmp.createNewFile()
        tmp.setReadable(false, false); tmp.setReadable(true, true)
        tmp.setWritable(false, false); tmp.setWritable(true, true)
        tmp.writeText(text)
        try {
            // Same directory, so the atomic replace holds; a failure leaves the old file intact
            // instead of truncating it mid-write.
            try {
                Files.move(tmp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), target.toPath(), REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            tmp.delete()
            throw e
        }
    }

    companion object {
        private const val TAG = "EngineConfig"
        private const val PACKAGE = "@opencode/ai/providers/openai-compatible"
        private const val INDENT = 2

        /**
         * Strips JSONC syntax — `//` line comments, `/* */` block comments,
         * trailing commas, and a leading UTF-8 BOM — so `org.json` can parse a
         * hand-edited config (audit-100 30-F3/38-F2). Comment markers inside
         * string literals are left alone, as are escaped quotes. Comments are
         * stripped first, then trailing commas, so a comma before a comment
         * that precedes `}` or `]` is still dropped.
         */
        internal fun stripJsonc(text: String): String =
            stripTrailingCommas(stripJsoncComments(text.removePrefix("\uFEFF")))

        private fun stripJsoncComments(text: String): String {
            val out = StringBuilder(text.length)
            var i = 0
            var inString = false
            while (i < text.length) {
                val c = text[i]
                if (inString) {
                    out.append(c)
                    if (c == '\\' && i + 1 < text.length) {
                        out.append(text[i + 1])
                        i += 2
                    } else {
                        if (c == '"') inString = false
                        i++
                    }
                    continue
                }
                when {
                    c == '"' -> {
                        inString = true
                        out.append(c)
                        i++
                    }
                    c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                        while (i < text.length && text[i] != '\n') i++
                    }
                    c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                        val end = text.indexOf("*/", i + 2)
                        i = if (end < 0) text.length else end + 2
                    }
                    else -> {
                        out.append(c)
                        i++
                    }
                }
            }
            return out.toString()
        }

        private fun stripTrailingCommas(text: String): String {
            val out = StringBuilder(text.length)
            var i = 0
            var inString = false
            while (i < text.length) {
                val c = text[i]
                if (inString) {
                    out.append(c)
                    if (c == '\\' && i + 1 < text.length) {
                        out.append(text[i + 1])
                        i += 2
                    } else {
                        if (c == '"') inString = false
                        i++
                    }
                    continue
                }
                when {
                    c == '"' -> {
                        inString = true
                        out.append(c)
                        i++
                    }
                    c == ',' -> {
                        var j = i + 1
                        while (j < text.length && text[j].isWhitespace()) j++
                        if (j < text.length && (text[j] == '}' || text[j] == ']')) {
                            i++ // trailing comma: drop it
                        } else {
                            out.append(c)
                            i++
                        }
                    }
                    else -> {
                        out.append(c)
                        i++
                    }
                }
            }
            return out.toString()
        }

        /** Turns a display name into the provider key models get namespaced under. */
        fun slug(name: String): String {
            // Locale.ROOT (38-F6): under tr-TR, "Iron".lowercase() is "ıron"
            // and the regex below turns it into "ron", not "iron".
            val base = name.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
            return base.ifEmpty { "custom" }
        }

        /**
         * Parses a user-supplied model list: one per line or comma-separated, each item
         * either `id` or `id = Name`.
         *
         * Only the first `=` splits, so a name may contain one. Repeating an id keeps its
         * first position and the last name given for it — and a bare repeat does not erase
         * a name already supplied, because typing the same model twice again is a likelier
         * typo than a deliberate way to un-name it.
         *
         * A comma inside a name is escaped as `\,` (38-F7): `gpt-4 = GPT\, 4 Turbo`
         * is one model, not a ghost model `4 Turbo`. `\\` is a literal backslash;
         * any other backslash is kept as-is.
         */
        fun parseModels(text: String): List<ModelSpec> {
            val names = LinkedHashMap<String, String?>()
            for (piece in splitModelList(text)) {
                val trimmed = piece.trim()
                if (trimmed.isEmpty()) continue
                val id = trimmed.substringBefore('=', trimmed).trim()
                if (id.isEmpty()) continue
                val given = trimmed.substringAfter('=', "").trim()
                names[id] = if (given.isNotEmpty()) given else names[id]
            }
            return names.map { (id, name) -> ModelSpec(id, name) }
        }

        /**
         * Splits a model list on commas and newlines, honouring backslash escapes
         * (38-F7): `\,` is a literal comma inside an id or a name, `\\` a literal
         * backslash. The escape is consumed, so the caller never sees it.
         */
        private fun splitModelList(text: String): List<String> {
            val pieces = ArrayList<String>()
            val current = StringBuilder()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    c == '\\' && i + 1 < text.length && (text[i + 1] == ',' || text[i + 1] == '\\') -> {
                        current.append(text[i + 1])
                        i += 2
                    }
                    c == ',' || c == '\n' -> {
                        pieces += current.toString()
                        current.clear()
                        i++
                    }
                    else -> {
                        current.append(c)
                        i++
                    }
                }
            }
            pieces += current.toString()
            return pieces
        }
    }
}
