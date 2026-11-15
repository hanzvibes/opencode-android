package dev.bidzz.opencode

import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * The app's own crash record (audit-200 C13, M-179).
 *
 * Before this there was no uncaught-exception handler and no crash
 * reporting at all: a crash left no trace — no file, no "last crash" in
 * Diagnostics, nothing to correlate a screenshot report with. The
 * handler writes one small file (`last-crash.txt` in app-private
 * storage) and then hands the exception to the previous handler, so the
 * process still dies the way the system expects instead of limping on in
 * a half-dead state. The file is read back on the Diagnostics screen.
 *
 * Only the latest crash is kept: one file, overwritten on each crash.
 * The stack trace is the app's own code — no user text is added.
 */
object CrashLog {

    const val FILE_NAME = "last-crash.txt"
    private const val TAG = "CrashLog"

    data class LastCrash(
        val timestampMs: Long,
        val version: String,
        val thread: String,
        val trace: String,
    )

    /**
     * Installs the handler. Call first in `Application.onCreate()`, before
     * any other work, so an early crash is recorded too. The previous
     * handler is chained, never swallowed.
     */
    fun install(filesDir: File, version: String) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeReport(filesDir, thread, throwable, version) }
                .onFailure { Log.w(TAG, "could not write crash report", it) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Writes the report; returns the file. Pure enough to unit-test. */
    fun writeReport(filesDir: File, thread: Thread, throwable: Throwable, version: String): File {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val file = File(filesDir, FILE_NAME)
        file.writeText(
            buildString {
                appendLine("timestamp=${System.currentTimeMillis()}")
                appendLine("version=$version")
                appendLine("thread=${thread.name}")
                append(trace)
            },
        )
        return file
    }

    /** Reads the latest crash back, or null when there is none (or the file is unreadable). */
    fun readLastCrash(filesDir: File): LastCrash? {
        val file = File(filesDir, FILE_NAME)
        if (!file.isFile) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val lines = text.lines()
        val timestamp = lines.firstOrNull { it.startsWith("timestamp=") }
            ?.removePrefix("timestamp=")?.toLongOrNull() ?: return null
        val version = lines.firstOrNull { it.startsWith("version=") }
            ?.removePrefix("version=") ?: ""
        val thread = lines.firstOrNull { it.startsWith("thread=") }
            ?.removePrefix("thread=") ?: ""
        val trace = lines.dropWhile { !it.startsWith("thread=") }.drop(1)
            .joinToString("\n").trim()
        if (trace.isEmpty()) return null
        return LastCrash(timestamp, version, thread, trace)
    }
}
