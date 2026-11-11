package dev.bidzz.opencode.engine

import android.util.Log
import java.io.File
import java.io.IOException
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

    /** Outcome of a write, so the UI can distinguish success from a shadowed file. */
    sealed interface WriteResult {
        data class Saved(val providerID: String) : WriteResult

        /**
         * The entry was written, but the engine will ignore it: config discovery picks
         * the *last* existing candidate, so a sibling `opencode.jsonc` wins over ours.
         */
        data class Shadowed(val providerID: String) : WriteResult
    }

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

    /** List of active/saved Google Antigravity OAuth accounts. */
    fun antigravityAccounts(): List<AntigravityAccount> {
        if (!antigravityAccountsFile.isFile) return emptyList()
        val text = runCatching { antigravityAccountsFile.readText() }.getOrNull() ?: return emptyList()
        if (text.isBlank()) return emptyList()
        return try {
            val root = JSONObject(text)
            val accounts = root.optJSONArray("accounts") ?: return emptyList()
            (0 until accounts.length()).mapNotNull { i ->
                val obj = accounts.optJSONObject(i) ?: return@mapNotNull null
                val email = obj.optString("email").ifEmpty { return@mapNotNull null }
                AntigravityAccount(
                    id = obj.optString("id").ifEmpty { email },
                    email = email,
                    enabled = obj.optBoolean("enabled", true),
                    projectId = obj.optString("projectId").takeIf { it.isNotBlank() },
                    hasRefreshToken = obj.optString("refreshToken").isNotBlank(),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed reading antigravity accounts", e)
            emptyList()
        }
    }

    /** Active Google Antigravity OAuth account if one is configured and enabled. */
    fun activeAntigravityAccount(): AntigravityAccount? =
        antigravityAccounts().firstOrNull { it.enabled && it.hasRefreshToken }

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
        val root = readRoot() ?: JSONObject()
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
        return if (File(dir, "opencode.jsonc").exists()) WriteResult.Shadowed(id) else WriteResult.Saved(id)
    }

    private fun readRoot(): JSONObject? {
        val target = when {
            file.isFile -> file
            jsoncFile.isFile -> jsoncFile
            else -> return null
        }
        val text = target.readText()
        if (text.isBlank()) return JSONObject()
        return try {
            // Strip single-line comments in case it is jsonc
            val cleaned = text.lines().joinToString("\n") { line ->
                val trimmed = line.trimStart()
                if (trimmed.startsWith("//")) "" else line
            }
            JSONObject(cleaned)
        } catch (e: org.json.JSONException) {
            // A malformed file the app did not write is a real problem, but overwriting
            // it silently would destroy something the user made by hand.
            Log.e(TAG, "config unreadable, refusing to merge into it", e)
            throw IOException("Existing config file is not valid JSON: ${e.message}")
        }
    }

    private fun write(root: JSONObject) {
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create ${dir.absolutePath}")
        val tmp = File(dir, "opencode.json.tmp")
        // The file holds provider API keys, so keep it owner-only before any content lands.
        // ponytail: plaintext at rest in app-private storage; the engine reads `settings.apiKey`
        // from this file, so encrypting needs engine support (env/file substitution) first.
        tmp.createNewFile()
        tmp.setReadable(false, false); tmp.setReadable(true, true)
        tmp.setWritable(false, false); tmp.setWritable(true, true)
        tmp.writeText(root.toString(INDENT) + "\n")
        try {
            // Same directory, so the atomic replace holds; a failure leaves the old file intact
            // instead of truncating it mid-write.
            java.nio.file.Files.move(
                tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: java.io.IOException) {
            tmp.delete()
            throw e
        }
    }

    companion object {
        private const val TAG = "EngineConfig"
        private const val PACKAGE = "@opencode/ai/providers/openai-compatible"
        private const val INDENT = 2

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
