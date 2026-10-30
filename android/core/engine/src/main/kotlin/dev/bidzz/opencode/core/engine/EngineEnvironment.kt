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
 */
class EngineEnvironment(
    filesDir: File,
    cacheDir: File,
    val workspace: File,
) {
    val root: File = File(filesDir, "engine")
    val home: File = File(root, "home")
    val tmp: File = File(cacheDir, "engine-tmp")

    val dataHome: File = File(home, ".local/share")
    val cacheHome: File = File(home, ".cache")
    val configHome: File = File(home, ".config")

    /**
     * Creates every directory and fails loudly if one cannot be created.
     *
     * Silently continuing here produces a confusing failure much later, inside the
     * engine, with no indication of which directory was missing.
     */
    fun prepare() {
        val required = listOf(root, home, tmp, dataHome, cacheHome, configHome, workspace)
        for (dir in required) {
            if (dir.exists() && !dir.isDirectory) {
                throw EngineStartupException("Expected a directory but found a file: ${dir.absolutePath}")
            }
            if (!dir.exists() && !dir.mkdirs()) {
                throw EngineStartupException("Cannot create directory: ${dir.absolutePath}")
            }
        }
        if (!workspace.canWrite()) {
            throw EngineStartupException("Workspace is not writable: ${workspace.absolutePath}")
        }
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
