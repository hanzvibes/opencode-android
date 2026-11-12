package dev.bidzz.opencode.core.engine

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Outcome of one Antigravity plugin patch run (audit-100 31-F2). */
enum class AntigravityPatchOutcome {
    /** The plugin cache directory — or no plugin file in it — does not exist yet. */
    NOT_FOUND,
    /** At least one plugin file was rewritten on disk by this run. */
    APPLIED,
    /** Every plugin file found already contains the patched code. */
    ALREADY_PATCHED,
    /** A plugin file exists but matches neither the old nor the new code (plugin version changed). */
    VERSION_MISMATCH,
    /** Patching was attempted but the write/verify step failed; the original file was kept. */
    FAILED,
}

/**
 * Result of [EngineEnvironment.patchAntigravityPlugin], for diagnostics.
 *
 * [restartRequired] is true only when the patch landed while the engine was already
 * running: Node loads the plugin once at startup, so the running engine still has the
 * old code in memory and a restart is needed before Antigravity auth works.
 */
data class AntigravityPatchReport(
    val outcome: AntigravityPatchOutcome,
    /** Plugin files actually rewritten by this run (empty unless [outcome] is APPLIED). */
    val patchedFiles: List<String> = emptyList(),
    val restartRequired: Boolean = false,
    val detail: String? = null,
)

/** Decision for one plugin file's text, decided without touching the disk. */
internal sealed interface PluginPatchDecision {
    data object AlreadyPatched : PluginPatchDecision
    data object VersionMismatch : PluginPatchDecision
    data class Patch(val patchedText: String) : PluginPatchDecision
}

/**
 * Decides what to do with one plugin file's text, pure and unit-testable.
 *
 * `request.js` carries two replacements (the generativelanguage guard and the
 * `.match` line); `v2.js` carries one (the fetch input normalisation). A file
 * is ALREADY_PATCHED only when every new string is present and no old string
 * remains; VERSION_MISMATCH when a file has neither the old nor the new code
 * for a replacement it should carry (the plugin changed version under us).
 */
internal fun decidePluginPatch(fileName: String, text: String): PluginPatchDecision {
    val replacements = when (fileName) {
        "request.js" -> listOf(REQUEST_GUARD_OLD to REQUEST_GUARD_NEW, REQUEST_MATCH_OLD to REQUEST_MATCH_NEW)
        "v2.js" -> listOf(FETCH_INPUT_OLD to FETCH_INPUT_NEW)
        else -> return PluginPatchDecision.VersionMismatch
    }
    var patched = text
    var needsPatch = false
    for ((old, new) in replacements) {
        when {
            patched.contains(old) -> {
                patched = patched.replace(old, new)
                needsPatch = true
            }
            patched.contains(new) -> Unit // this replacement already applied
            else -> return PluginPatchDecision.VersionMismatch
        }
    }
    return if (needsPatch) PluginPatchDecision.Patch(patched) else PluginPatchDecision.AlreadyPatched
}

private const val REQUEST_GUARD_OLD =
    "typeof input === \"string\" && input.includes(\"generativelanguage.googleapis.com\")"
private const val REQUEST_GUARD_NEW =
    "((typeof input === \"string\" ? input : (input && typeof input === \"object\" && \"url\" in input ? input.url : (input ? String(input) : \"\"))).includes(\"generativelanguage.googleapis.com\"))"
private const val REQUEST_MATCH_OLD = "const match = input.match(/\\/models\\/([^:]+):(\\w+)/);"
private const val REQUEST_MATCH_NEW =
    "const _inUrl = typeof input === \"string\" ? input : (input && typeof input === \"object\" && \"url\" in input ? input.url : (input ? String(input) : \"\")); const match = _inUrl.match(/\\/models\\/([^:]+):(\\w+)/);"
private const val FETCH_INPUT_OLD = "input instanceof URL ? input.toString() : input"
private const val FETCH_INPUT_NEW =
    "typeof input === \"string\" ? input : (input && typeof input === \"object\" && \"url\" in input ? input.url : (input ? String(input) : input))"

/**
 * Directories the engine needs, kept separate from the app's own storage.
 *
 * The engine writes a SQLite database, logs, session data and caches. It must not
 * share the app's databases, and it must not be pointed at a directory the app
 * also writes to, or a future schema change in one breaks the other.
 *
 * Layout (all app-private, no external permission required):
 *
 * ```
 * filesDir/engine/
 *   home/           HOME for the process; engine derives its own paths from this
 *     .local/share/opencode/   data, sessions, logs
 *     .cache/                  caches
 *     .config/                 user config
 *   tmp/            TMPDIR
 * ```
 *
 * The workspace is deliberately *not* part of this layout and is supplied by a
 * provider: it is the user's project directory and can be anywhere on shared
 * storage, whereas the engine's own state must stay app-private and must survive
 * a workspace switch. Keeping them in different trees is what makes switching
 * projects safe — the sessions database is not inside the project.
 */
class EngineEnvironment(
    filesDir: File,
    cacheDir: File,
    private val workspaceProvider: () -> File,
) {
    constructor(filesDir: File, cacheDir: File, workspace: File) :
        this(filesDir, cacheDir, { workspace })

    val root: File = File(filesDir, "engine")
    val home: File = File(root, "home")
    val tmp: File = File(cacheDir, "engine-tmp")

    val dataHome: File = File(home, ".local/share")
    val cacheHome: File = File(home, ".cache")
    val configHome: File = File(home, ".config")

    /**
     * Node.js runtime assets, extracted from `assets/node/` at first launch
     * (NodeAssets) into this directory:
     *
     * ```
     * filesDir/engine/node/
     *   lib/node_modules/npm/bin/npm-cli.js   (invoked as `node <this path>`)
     *   lib/node_modules/corepack/
     *   etc/tls/cert.pem                     (bundled CA bundle, see SSL_CERT_FILE)
     * ```
     */
    val nodeDir: File = File(root, "node")
    val nodeCertFile: File = File(nodeDir, "etc/tls/cert.pem")
    val npmCliJs: File = File(nodeDir, "lib/node_modules/npm/bin/npm-cli.js")

    /**
     * Python runtime assets, extracted from `assets/python/` at first launch
     * (PythonAssets) into this directory:
     *
     * ```
     * filesDir/engine/python/
     *   lib/python3.14/os.py              (stdlib sentinel; PYTHONHOME root)
     *   lib/python3.14/site-packages/pip/ (invoked as `python3 -m pip`)
     *   lib/python3.14/lib-dynload/       (extension modules: _ssl, _sqlite3, ...)
     * ```
     *
     * CPython computes its stdlib location from PYTHONHOME; without it the
     * interpreter looks under the dead Termux prefix
     * (`/data/data/com.termux/files/usr`) and fails to start.
     */
    val pythonDir: File = File(root, "python")

    /**
     * Language servers, extracted from `assets/lsp/` at first launch
     * (LspAssets) into this directory:
     *
     * ```
     * filesDir/engine/lsp/
     *   lib/node_modules/typescript-language-server/lib/cli.mjs
     *   lib/node_modules/typescript/lib/typescript.js
     *   lib/node_modules/pyright/langserver.index.js
     * ```
     *
     * The engine's `lsp` config points at these paths (see engine/README.md
     * for the opencode.json snippet). The servers run under the bundled Node.
     */
    val lspDir: File = File(root, "lsp")

    /** Resolved on every read, so a workspace switch needs no new environment. */
    val workspace: File get() = workspaceProvider()

    /**
     * Result of the most recent [patchAntigravityPlugin] run, or null before any
     * run. Read by diagnostics; updated by [prepare] and by the post-start
     * re-patch loop in [ProcessManager].
     */
    @Volatile
    var lastPatchReport: AntigravityPatchReport? = null
        private set

    /**
     * Creates every directory and fails loudly if one cannot be created.
     *
     * Silently continuing here produces a confusing failure much later, inside the
     * engine, with no indication of which directory was missing.
     */
    fun prepare() {
        val projectDir = workspace
        val required = listOf(root, home, tmp, dataHome, cacheHome, configHome, projectDir)
        for (dir in required) {
            if (dir.exists() && !dir.isDirectory) {
                throw EngineStartupException("Expected a directory but found a file: ${dir.absolutePath}")
            }
            // An existing workspace the user picked must not be recreated; only
            // the engine's own directories are ours to create.
            if (!dir.exists() && !dir.mkdirs()) {
                throw EngineStartupException("Cannot create directory: ${dir.absolutePath}")
            }
        }
        if (!projectDir.canWrite()) {
            throw EngineStartupException("Workspace is not writable: ${projectDir.absolutePath}")
        }
        writeGitConfig()
        lastPatchReport = patchAntigravityPlugin()
    }

    /**
     * Patches `opencode-antigravity-auth` plugin files if present in the cache,
     * resolving the leaked Generative Language API call bug where non-string Request/URL
     * objects bypass request interception and cause "API key not valid" errors.
     *
     * Idempotent and reporting (audit-100 31-F2/31-F11): a fresh install has no
     * plugin yet, so the run reports NOT_FOUND instead of silently doing nothing
     * — the caller re-runs it after the engine downloads the plugin. A patched
     * file is written to a temp file and atomically renamed over the original,
     * with a `.bak` copy of the pre-patch bytes kept beside it; after the rename
     * the file is re-read and the patched code must be present, or the backup is
     * restored and the run reports FAILED. A half-written plugin can no longer
     * strand Antigravity auth with no diagnosis.
     *
     * @param restartRequired set by the post-start re-patch loop: the patch landed
     * while the engine was already running, so the running engine still has the
     * old code loaded.
     */
    fun patchAntigravityPlugin(restartRequired: Boolean = false): AntigravityPatchReport {
        val npmDir = File(cacheHome, "opencode/npm")
        if (!npmDir.exists()) return AntigravityPatchReport(AntigravityPatchOutcome.NOT_FOUND)
        val patched = mutableListOf<String>()
        var sawPatched = false
        var sawMismatch: String? = null
        var sawFailure: String? = null
        npmDir.walkTopDown().forEach { file ->
            if ((file.name != "request.js" && file.name != "v2.js") ||
                !file.path.contains("opencode-antigravity-auth")
            ) return@forEach
            val text = runCatching { file.readText() }.getOrNull()
            if (text == null) {
                sawFailure = file.name
                return@forEach
            }
            when (val decision = decidePluginPatch(file.name, text)) {
                is PluginPatchDecision.AlreadyPatched -> sawPatched = true
                is PluginPatchDecision.VersionMismatch -> sawMismatch = file.name
                is PluginPatchDecision.Patch -> {
                    if (writePatchedPluginFile(file, decision.patchedText)) {
                        patched += file.name
                    } else {
                        sawFailure = file.name
                    }
                }
            }
        }
        val report = when {
            sawFailure != null -> AntigravityPatchReport(
                AntigravityPatchOutcome.FAILED,
                patchedFiles = patched,
                restartRequired = restartRequired && patched.isNotEmpty(),
                detail = "could not patch $sawFailure",
            )
            patched.isNotEmpty() -> AntigravityPatchReport(
                AntigravityPatchOutcome.APPLIED,
                patchedFiles = patched,
                restartRequired = restartRequired,
            )
            sawMismatch != null -> AntigravityPatchReport(
                AntigravityPatchOutcome.VERSION_MISMATCH,
                detail = "$sawMismatch matches neither the old nor the patched code (plugin version changed?)",
            )
            sawPatched -> AntigravityPatchReport(AntigravityPatchOutcome.ALREADY_PATCHED)
            else -> AntigravityPatchReport(AntigravityPatchOutcome.NOT_FOUND)
        }
        lastPatchReport = report
        return report
    }

    /**
     * Writes patched plugin bytes without ever leaving a half-written file behind
     * (audit-100 31-F11): backup first (no backup, no patch), temp file + atomic
     * rename, then re-read and verify the patched code landed. Any failure
     * restores the backup and returns false.
     */
    private fun writePatchedPluginFile(file: File, patchedText: String): Boolean {
        val backup = File(file.parentFile, "${file.name}.bak")
        val tmp = File(file.parentFile, "${file.name}.tmp")
        if (runCatching { file.copyTo(backup, overwrite = true) }.isFailure) return false
        return try {
            tmp.writeText(patchedText)
            try {
                Files.move(tmp.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), REPLACE_EXISTING)
            }
            if (runCatching { file.readText() }.getOrNull() == patchedText) {
                true
            } else {
                runCatching { backup.copyTo(file, overwrite = true) }
                false
            }
        } catch (e: Exception) {
            tmp.delete()
            false
        }
    }

    /**
     * Disables git's file-mode tracking for the engine's git invocations.
     *
     * Shared storage is FUSE, which ignores `chmod` (verified in `handoff.md`
     * §2.5). With `core.fileMode` on, git reports every file as mode-changed the
     * moment a checkout touches permissions, so the agent's diff view fills with
     * noise it cannot fix. Written to the engine's own HOME, so it never touches
     * the user's global git config.
     */
    private fun writeGitConfig() {
        val config = File(home, ".gitconfig")
        val desired = """
            [core]
            	fileMode = false
            [safe]
            	directory = *
        """.trimIndent() + "\n"
        if (config.exists() && config.readText() == desired) return
        runCatching { config.writeText(desired) }
            .onFailure { /* Git is optional at this stage; the engine reports its own failure. */ }
    }

    /**
     * Environment variables for the child process.
     *
     * PATH deliberately starts with the directory holding the bundled tool
     * binaries (git, rg, node) so the engine's shell tool finds them, and
     * includes the system bin directories for `/system/bin/sh`.
     *
     * LD_LIBRARY_PATH must point at the native library directory: a child
     * process exec'd through the linker does not inherit the app's library
     * search path, and Node's RUNPATH points at the dead Termux prefix
     * (`/data/data/com.termux/files/usr/lib`). git/rg never needed this —
     * their DT_NEEDED entries are system libs only.
     *
     * SSL_CERT_FILE must point at the bundled CA bundle: libcrypto hardcodes
     * the Termux path `/data/data/com.termux/files/usr/etc/tls/cert.pem`,
     * which does not exist on the device, so npm's TLS verification would
     * fail without it.
     *
     * SHELL is `/system/bin/sh`: the Node binary contains hardcoded Termux
     * paths (`.../usr/bin/bash`), and there is no bash bundled.
     *
     * PYTHONHOME must point at the extracted Python assets: CPython
     * computes its stdlib location from it, and the Termux prefix it was
     * built with does not exist on the device. The interpreter library
     * itself (libpython3.14.so) resolves via LD_LIBRARY_PATH, and the
     * extension modules in lib-dynload resolve their own libs the same way.
     */
    fun variables(toolBinDir: File?, nativeLibraryDir: File? = null): Map<String, String> = buildMap {
        put("HOME", home.absolutePath)
        put("TMPDIR", tmp.absolutePath)
        put("XDG_DATA_HOME", dataHome.absolutePath)
        put("XDG_CACHE_HOME", cacheHome.absolutePath)
        put("XDG_CONFIG_HOME", configHome.absolutePath)
        put("PATH", buildPath(toolBinDir))
        // The engine must not inherit the app's locale assumptions.
        put("LANG", "C.UTF-8")
        // Signals to the engine that it is running under the Android host.
        put("OPENCODE_ANDROID", "1")
        put("SHELL", "/system/bin/sh")
        put("SSL_CERT_FILE", nodeCertFile.absolutePath)
        put("PYTHONHOME", pythonDir.absolutePath)
        if (nativeLibraryDir != null) {
            put("LD_LIBRARY_PATH", nativeLibraryDir.absolutePath)
        }
    }

    private fun buildPath(toolBinDir: File?): String =
        listOfNotNull(
            toolBinDir?.absolutePath,
            "/system/bin",
            "/system/xbin",
            "/vendor/bin",
        ).joinToString(":")

    fun clearTmp() {
        tmp.listFiles()?.forEach { it.deleteRecursively() }
    }
}

/** Raised when the engine cannot be brought up. Carries a user-facing reason. */
class EngineStartupException(message: String, cause: Throwable? = null) : Exception(message, cause)
