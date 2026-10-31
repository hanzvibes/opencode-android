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
     * binaries (git, rg) so the engine's shell tool finds them, and includes the
     * system bin directories for `/system/bin/sh`.
     */
    fun variables(toolBinDir: File?): Map<String, String> = buildMap {
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
