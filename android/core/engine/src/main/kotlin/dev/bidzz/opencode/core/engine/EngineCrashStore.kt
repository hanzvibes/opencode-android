package dev.bidzz.opencode.core.engine

import java.io.File

/**
 * The last engine crash, persisted across restarts (audit-200 C13, M-180).
 *
 * Before this, an unexpected engine exit lived only in logcat and in the
 * in-memory `lastError`: restart the app and the exit code, the time and
 * the last output were gone. The supervisor now writes one small file
 * (`last-engine-crash.txt` under the engine root) on every transition to
 * FAILED, and Diagnostics reads it back.
 *
 * The report is built from already-redacted values — `lastError` is
 * redacted in `transition()` and console lines in `appendOutput()` — and
 * [buildReport] redacts once more with the password, so a writer that
 * forgets the first redaction cannot leak the credential into the file.
 * Only the latest crash is kept: one file, overwritten on each crash.
 */
data class EngineCrashReport(
    val timestampMs: Long,
    val exitCode: Int?,
    val error: String?,
    val outputLines: List<String>,
)

object EngineCrashStore {

    const val FILE_NAME = "last-engine-crash.txt"
    private const val OUTPUT_MARKER = "--- output ---"

    fun file(root: File): File = File(root, FILE_NAME)

    /** Assembles the report from the transition's facts; redacts with the password when known. */
    fun buildReport(
        timestampMs: Long = System.currentTimeMillis(),
        exitCode: Int?,
        error: String?,
        outputLines: List<String>,
        password: String?,
    ): EngineCrashReport = EngineCrashReport(
        timestampMs = timestampMs,
        exitCode = exitCode,
        error = error?.let { redactEnginePassword(it, password) },
        outputLines = outputLines.map { redactEnginePassword(it, password) },
    )

    fun save(root: File, report: EngineCrashReport) {
        file(root).writeText(
            buildString {
                appendLine("timestamp=${report.timestampMs}")
                appendLine("exitCode=${report.exitCode ?: ""}")
                appendLine("error=${report.error?.replace('\n', ' ') ?: ""}")
                appendLine(OUTPUT_MARKER)
                report.outputLines.forEach { appendLine(it) }
            },
        )
    }

    fun read(root: File): EngineCrashReport? {
        val file = file(root)
        if (!file.isFile) return null
        val lines = runCatching { file.readLines() }.getOrNull() ?: return null
        val timestamp = lines.firstOrNull { it.startsWith("timestamp=") }
            ?.removePrefix("timestamp=")?.toLongOrNull() ?: return null
        val exitCode = lines.firstOrNull { it.startsWith("exitCode=") }
            ?.removePrefix("exitCode=")?.toIntOrNull()
        val error = lines.firstOrNull { it.startsWith("error=") }
            ?.removePrefix("error=")?.ifEmpty { null }
        val output = lines.dropWhile { it != OUTPUT_MARKER }.drop(1)
        return EngineCrashReport(timestamp, exitCode, error, output)
    }
}
