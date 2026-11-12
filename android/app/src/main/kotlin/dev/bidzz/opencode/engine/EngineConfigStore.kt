package dev.bidzz.opencode.engine

import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
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
    data class CustomProvider(val id: String, val name: String, val baseURL: String, val models: List<String>)

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
        return providers.keys().asSequence().mapNotNull { id ->
            val entry = providers.optJSONObject(id) ?: return@mapNotNull null
            val settings = entry.optJSONObject("settings")
            CustomProvider(
                id = id,
                name = entry.optString("name").ifEmpty { id },
                baseURL = settings?.optString("baseURL").orEmpty(),
                models = entry.optJSONObject("models")?.keys()?.asSequence()?.toList() ?: emptyList(),
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

    /** Deletes `opencode.jsonc` so the app can manage providers in `opencode.json` again (30-F5/38-F10). */
    fun deleteJsoncConfig(): Boolean = runCatching { jsoncFile.delete() }.getOrDefault(false)

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
     * reads it — `Config.Provider.Info` has no `apiKey` field of its own.
     */
    @Throws(IOException::class)
    fun putOpenAICompatible(
        id: String,
        name: String,
        baseURL: String,
        apiKey: String,
        models: List<ModelSpec>,
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
        // not an object (e.g. "old": "disabled") is invisible to
        // customProviders(), so the duplicate-slug check cannot see it, and
        // overwriting it would delete a hand-made entry without warning.
        if (root.has("providers") && root.optJSONObject("providers") == null) {
            Log.w(TAG, "refusing to replace a non-object 'providers' value")
            throw IOException("Existing config has a non-object 'providers' value; refusing to replace it")
        }
        val providers = root.optJSONObject("providers") ?: JSONObject().also { root.put("providers", it) }
        if (providers.has(id) && providers.optJSONObject(id) == null) {
            Log.w(TAG, "refusing to replace non-object provider entry '$id'")
            throw IOException("Existing provider '$id' is not an object; refusing to replace it")
        }

        val entry = JSONObject()
        entry.put("name", name)
        entry.put("package", PACKAGE)
        entry.put(
            "settings",
            JSONObject().put("baseURL", baseURL.trimEnd('/')).put("apiKey", apiKey),
        )
        val modelMap = JSONObject()
        models.forEach { spec ->
            // A bare `{}` means "inherit whatever the provider knows about this model";
            // `name` is only written when the user gave one, so an unnamed model keeps
            // the engine's own answer instead of being pinned to an empty string.
            val model = JSONObject()
            spec.name?.let { model.put("name", it) }
            modelMap.put(spec.id, model)
        }
        entry.put("models", modelMap)
        providers.put(id, entry)

        write(root)
        Log.i(TAG, "configured custom provider '$id' -> $baseURL (${models.size} models)")
        return WriteResult.Saved(id)
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

    private fun write(root: JSONObject) {
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create ${dir.absolutePath}")
        writeAtomically(file, root.toString(INDENT) + "\n")
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
            val base = name.lowercase()
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
         */
        fun parseModels(text: String): List<ModelSpec> {
            val names = LinkedHashMap<String, String?>()
            for (item in text.split(',', '\n')) {
                val piece = item.trim()
                if (piece.isEmpty()) continue
                val id = piece.substringBefore('=', piece).trim()
                if (id.isEmpty()) continue
                val given = piece.substringAfter('=', "").trim()
                names[id] = if (given.isNotEmpty()) given else names[id]
            }
            return names.map { (id, name) -> ModelSpec(id, name) }
        }
    }
}
