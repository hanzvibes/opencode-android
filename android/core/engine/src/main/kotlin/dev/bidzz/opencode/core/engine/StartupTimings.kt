package dev.bidzz.opencode.core.engine

import android.os.SystemClock

/**
 * Cold-start timings (audit-200 D5, m-875): the repo had zero startup
 * instrumentation, so no cold-start fix (139-F1–F6) could ever be proven
 * faster or slower. A process-lifetime list of (stage, ms since the
 * first mark) pairs, written from the app, the engine and the asset
 * sync, read in Diagnostics.
 *
 * Thread-safe; marks are cheap (one map put) and logcat-only — no
 * Firebase Performance, no analytics. Durations use
 * [SystemClock.elapsedRealtime], never wall time, so a clock change
 * mid-start cannot bend them. A stage marked twice keeps the latest
 * value (a retried engine start re-marks its stages).
 *
 * The clock is a seam for JVM tests only: production always reads
 * [SystemClock.elapsedRealtime].
 */
object StartupTimings {

    internal var clock: () -> Long = { SystemClock.elapsedRealtime() }

    private val lock = Any()
    private var baseMs: Long? = null
    private val marks = LinkedHashMap<String, Long>()

    /** Records [name] at the current elapsed time since the first mark. */
    fun mark(name: String) {
        synchronized(lock) {
            val now = clock()
            if (baseMs == null) baseMs = now
            marks[name] = now - (baseMs ?: now)
        }
    }

    /** Milliseconds since the first mark for [name], or null if unmarked. */
    fun elapsed(name: String): Long? = synchronized(lock) { marks[name] }

    /** A copy of all (stage, ms) pairs in mark order. */
    fun snapshot(): Map<String, Long> = synchronized(lock) { marks.toMap() }

    /** One line for Diagnostics and logcat: `stage=12ms, stage=340ms`. */
    fun report(): String = synchronized(lock) {
        if (marks.isEmpty()) "no marks yet"
        else marks.entries.joinToString(", ") { "${it.key}=${it.value}ms" }
    }

    internal fun reset() {
        synchronized(lock) {
            baseMs = null
            marks.clear()
        }
    }
}
