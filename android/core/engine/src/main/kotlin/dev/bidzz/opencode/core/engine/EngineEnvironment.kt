package dev.bidzz.opencode.core.engine

import java.io.File

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
        patchAntigravityPlugin()
    }

    /**
     * Patches `opencode-antigravity-auth` plugin files if present in the cache,
     * resolving the leaked Generative Language API call bug where non-string Request/URL
     * objects bypass request interception and cause "API key not valid" errors.
     */
    fun patchAntigravityPlugin() {
        val npmDir = File(cacheHome, "opencode/npm")
        if (!npmDir.exists()) return
        npmDir.walkTopDown().forEach { file ->
            if (file.name == "request.js" && file.path.contains("opencode-antigravity-auth")) {
                runCatching {
                    var text = file.readText()
                    var changed = false
                    val oldFn = "typeof input === \"string\" && input.includes(\"generativelanguage.googleapis.com\")"
                    val newFn = "((typeof input === \"string\" ? input : (input && typeof input === \"object\" && \"url\" in input ? input.url : (input ? String(input) : \"\"))).includes(\"generativelanguage.googleapis.com\"))"
                    if (text.contains(oldFn)) {
                        text = text.replace(oldFn, newFn)
                        changed = true
                    }
                    val oldMatch = "const match = input.match(/\\/models\\/([^:]+):(\\w+)/);"
                    val newMatch = "const _inUrl = typeof input === \"string\" ? input : (input && typeof input === \"object\" && \"url\" in input ? input.url : (input ? String(input) : \"\")); const match = _inUrl.match(/\\/models\\/([^:]+):(\\w+)/);"
                    if (text.contains(oldMatch)) {
                        text = text.replace(oldMatch, newMatch)
                        changed = true
                    }
                    if (changed) {
                        file.writeText(text)
                    }
                }
            } else if (file.name == "v2.js" && file.path.contains("opencode-antigravity-auth")) {
                runCatching {
                    var text = file.readText()
                    val oldFetch = "input instanceof URL ? input.toString() : input"
                    val newFetch = "typeof input === \"string\" ? input : (input && typeof input === \"object\" && \"url\" in input ? input.url : (input ? String(input) : input))"
                    if (text.contains(oldFetch)) {
                        text = text.replace(oldFetch, newFetch)
                        file.writeText(text)
                    }
                }
            }
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
