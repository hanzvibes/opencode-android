package dev.bidzz.opencode.core.engine

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads what the engine leaves on disk, and runs its tools.
 *
 * Everything here exists because `run-as` refuses a release build: on a debug APK
 * a developer can shell into app-private storage and read the engine log or run
 * `git --version` by hand, and on a released install nobody can. The app itself has
 * always been able to read those paths — it owns them — so this moves the same
 * capability inside the app.
 *
 * [probe] is the part worth having most: it launches a tool through the symlink in
 * `engine/tools/bin`, which is the exact path the engine's `shell` tool uses, in the
 * same SELinux domain. If `git --version` answers here, git works on that device; if
 * it returns exit 126, that is the `app_data_file` label trap and nothing else.
 */
class EngineDiagnostics(
    private val environment: EngineEnvironment,
    private val nativeLibraryDir: File,
) {

    /** One entry in the tool bin directory, as the agent will see it. */
    data class ToolStatus(
        val name: String,
        val linkPath: String,
        val target: String,
        /** False when the symlink is missing or points at something absent. */
        val resolves: Boolean,
        val sourcePresent: Boolean,
    )

    /** Result of actually executing a tool. */
    data class ProbeResult(
        val command: String,
        val exitCode: Int?,
        val output: String,
        val timedOut: Boolean,
    ) {
        val ok: Boolean get() = exitCode == 0 && !timedOut
    }

    val toolBin: File = File(environment.root, "tools/bin")

    /** The engine's log file. Public so a screen can say whether it exists yet, in its own language. */
    val engineLogFile: File = logFileFor(environment)

    /**
     * Trims the engine log to its newest bytes (audit-200 C13, M-181):
     * the engine never rotates `opencode.log`, so without this it grows
     * without bound. Only rewrites when the file is over the cap; the
     * viewer already reads only the tail, so trimming never hides a line
     * the viewer could have shown. Called at engine start (after orphans
     * are reaped, before the new engine spawns) and when Diagnostics
     * opens. The engine's logger appends, so truncating the file does not
     * disturb its writes.
     */
    fun trimEngineLog(maxBytes: Long = ENGINE_LOG_MAX_BYTES): Boolean =
        trimLogFile(engineLogFile, maxBytes)

    /** The last engine crash the supervisor persisted, if any (audit-200 C13, M-180). */
    fun lastEngineCrash(): EngineCrashReport? = EngineCrashStore.read(environment.root)

    /** Which tools the APK was supposed to ship, and what is actually linked. */
    fun tools(): List<ToolStatus> =
        EngineBinary.TOOL_LIBRARIES.map { (library, toolName) ->
            val link = File(toolBin, toolName)
            val source = File(nativeLibraryDir, library)
            val target = runCatching { link.toPath().toRealPath().toString() }.getOrDefault("—")
            ToolStatus(
                name = toolName,
                linkPath = link.absolutePath,
                target = target,
                resolves = link.exists(),
                sourcePresent = source.isFile,
            )
        }

    /**
     * Runs [toolName] from the tool bin and captures its output.
     *
     * The child inherits this process's environment, whose `PATH` is the platform's
     * (`/system/bin` and friends, verified against `/proc/<pid>/environ`) — nothing
     * that would find the bundled tools. They are addressed by absolute path, which
     * is also what makes a failure mean one thing: a bad SELinux label, exit 126.
     */
    suspend fun probe(toolName: String, args: List<String>, timeoutMs: Long = 8_000): ProbeResult =
        withContext(Dispatchers.IO) {
            val file = File(toolBin, toolName)
            val command = (listOf(file.absolutePath) + args).joinToString(" ")
            if (!file.exists()) {
                return@withContext ProbeResult(command, null, "not linked: ${file.absolutePath}", false)
            }
            val outcome = runCatching {
                val process = ProcessBuilder(file.absolutePath, *args.toTypedArray())
                    .redirectErrorStream(true)
                    .directory(environment.workspace)
                    .start()
                val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    process.waitFor(1, TimeUnit.SECONDS)
                    return@runCatching ProbeResult(command, null, read(process), true)
                }
                ProbeResult(command, process.exitValue(), read(process), false)
            }
            outcome.getOrElse { error ->
                Log.w(TAG, "probe $command failed", error)
                ProbeResult(command, null, "${error::class.java.simpleName}: ${error.message}", false)
            }
        }

    /** The tail of the engine's log file, with the facts the viewer needs to be honest about it. */
    data class LogTail(
        /** The lines read, newest last. */
        val lines: List<String>,
        /** True when the file is larger than the 256 KB read window, so older lines exist but are not shown. */
        val wasTruncated: Boolean,
        /** The file's full size in bytes at read time. */
        val fileBytes: Long,
    )

    /**
     * The engine's own log, newest last.
     *
     * Read backwards from the end because the file grows without bound over a
     * session; pulling the whole thing into memory to show eighty lines is how a
     * diagnostics screen becomes the thing that fails.
     *
     * Audit-200 C9 (M-136): the result says whether the file was cut — the
     * viewer used to show the tail as if it were the whole log, and a crash's
     * cause before the 256 KB window silently vanished from the diagnosis.
     */
    suspend fun engineLogTail(lines: Int = 120, password: String? = null): LogTail = withContext(Dispatchers.IO) {
        if (!engineLogFile.isFile) return@withContext LogTail(emptyList(), wasTruncated = false, fileBytes = 0)
        runCatching {
            RandomAccessFile(engineLogFile, "r").use { raf ->
                val length = raf.length()
                val window = minOf(length, MAX_TAIL_BYTES)
                raf.seek(length - window)
                val bytes = ByteArray(window.toInt())
                raf.readFully(bytes)
                val read = String(bytes, Charsets.UTF_8)
                    .lines()
                    .let { if (length > window) it.drop(1) else it } // first line is likely truncated
                    .filter { it.isNotBlank() }
                    .takeLast(lines)
                    // The engine's own log is read for display and copying:
                    // mask a password line the same way as stdout (item 18).
                    // Audit-200 C13 (M-003): the value-based redaction only
                    // runs when the password is passed — a bare function
                    // reference meant `password = null` here, so a password
                    // echoed without its "server password" prefix reached
                    // the viewer and the clipboard verbatim.
                    .map { redactEnginePassword(it, password) }
                LogTail(read, wasTruncated = length > window, fileBytes = length)
            }
        }.getOrElse {
            Log.w(TAG, "could not read engine log", it)
            LogTail(emptyList(), wasTruncated = false, fileBytes = 0)
        }
    }

    /** Paths worth quoting in a bug report. */
    fun paths(): List<Pair<String, String>> = listOf(
        "engineRoot" to environment.root.absolutePath,
        "engineHome" to environment.home.absolutePath,
        "configDir" to File(environment.configHome, "opencode").absolutePath,
        "dataDir" to File(environment.dataHome, "opencode").absolutePath,
        "engineLog" to engineLogFile.absolutePath,
        "toolBin" to toolBin.absolutePath,
        "workspace" to environment.workspace.absolutePath,
        "tmp" to environment.tmp.absolutePath,
    )

    private fun read(process: Process): String =
        process.inputStream.bufferedReader().readText().take(MAX_OUTPUT)

    companion object {
        private const val TAG = "EngineDiagnostics"
        private const val MAX_TAIL_BYTES = 256L * 1024
        private const val MAX_OUTPUT = 4000
        /** Cap for [trimEngineLog] (audit-200 C13, M-181): 8 MB of newest log is far more than any diagnosis needs. */
        const val ENGINE_LOG_MAX_BYTES = 8L * 1024 * 1024

        /** Where the engine writes its log, for a given environment. */
        fun logFileFor(environment: EngineEnvironment): File =
            File(File(File(environment.dataHome, "opencode"), "log"), "opencode.log")

        /** Keeps only the newest [maxBytes] of [file]; false when nothing needed trimming. */
        fun trimLogFile(file: File, maxBytes: Long = ENGINE_LOG_MAX_BYTES): Boolean {
            if (!file.isFile) return false
            val length = file.length()
            if (length <= maxBytes) return false
            return runCatching {
                val kept = RandomAccessFile(file, "r").use { raf ->
                    raf.seek(length - maxBytes)
                    val bytes = ByteArray(maxBytes.toInt())
                    raf.readFully(bytes)
                    // Drop the first partial line, like the tail reader does.
                    String(bytes, Charsets.UTF_8).substringAfter('\n', "")
                }
                file.writeText(kept)
                true
            }.getOrElse {
                Log.w(TAG, "could not trim engine log", it)
                false
            }
        }
    }
}
