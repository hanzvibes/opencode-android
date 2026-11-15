package dev.bidzz.opencode.ui.diagnostics

import dev.bidzz.opencode.core.engine.EngineState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 C9: the diagnostics log classifier (M-133) and the restart
 * settle predicate (M-135).
 *
 * Both are pure functions over plain text and engine state — the screen
 * itself needs a running engine, these do not.
 */
class DiagnosticsC9Test {

    @Test
    fun `error wordings the old classifier missed classify as error`() {
        // The old set (error/fail/exception, warn) hid the lines a failing
        // engine actually prints — the Error filter could not find them.
        assertEquals(LogLevel.ERROR, classifyLogLevel("API key not valid"))
        assertEquals(LogLevel.ERROR, classifyLogLevel("Is port 41234 in use?"))
        assertEquals(LogLevel.ERROR, classifyLogLevel("fatal: unable to start engine"))
        assertEquals(LogLevel.ERROR, classifyLogLevel("panic: runtime error"))
        assertEquals(LogLevel.ERROR, classifyLogLevel("request timed out after 30s"))
        assertEquals(LogLevel.ERROR, classifyLogLevel("permission denied"))
        assertEquals(LogLevel.ERROR, classifyLogLevel("connection refused"))
        assertEquals(LogLevel.ERROR, classifyLogLevel("out of memory while loading model"))
    }

    @Test
    fun `M-222 the logcat export reads only this app own logs and caps the text`() {
        assertEquals(listOf("logcat", "-d", "--pid=1234"), logcatCommand(1234))
        assertEquals("short", trimLogcatTail("short"))
        val long = "x".repeat(LOGCAT_EXPORT_MAX_CHARS + 100)
        assertEquals(LOGCAT_EXPORT_MAX_CHARS, trimLogcatTail(long).length)
        assertEquals("y".repeat(LOGCAT_EXPORT_MAX_CHARS), trimLogcatTail(long.replace('x', 'y')))
        assertEquals("OpenCode logcat (pid 1234)\n\nline one", logcatExportText("line one", 1234))
        assertEquals(null, logcatExportText("", 1234))
        assertEquals(null, logcatExportText(null, 1234))
        assertEquals(null, logcatExportText("   \n  ", 1234))
    }

    @Test
    fun `warn and deprecated classify as warning`() {
        assertEquals(LogLevel.WARNING, classifyLogLevel("warning: low memory"))
        assertEquals(LogLevel.WARNING, classifyLogLevel("deprecated: use --new-flag"))
    }

    @Test
    fun `plain lines stay info`() {
        assertEquals(LogLevel.INFO, classifyLogLevel("engine started on port 41234"))
        assertEquals(LogLevel.INFO, classifyLogLevel("session opened"))
    }

    @Test
    fun `degraded and stopped settle a restart`() {
        // RUNNING and FAILED already settled; DEGRADED and STOPPED are
        // terminal states too, and the old predicate left the button stuck
        // on "Restarting…" for 30 seconds after a restart that had
        // already finished degraded or stopped.
        assertTrue(isRestartSettled(sawRestart = true, state = EngineState.DEGRADED, pidBefore = 100, pid = 100))
        assertTrue(isRestartSettled(sawRestart = true, state = EngineState.STOPPED, pidBefore = 100, pid = 100))
        assertTrue(isRestartSettled(sawRestart = true, state = EngineState.RUNNING, pidBefore = 100, pid = 100))
        assertTrue(isRestartSettled(sawRestart = true, state = EngineState.FAILED, pidBefore = 100, pid = 100))
    }

    @Test
    fun `transitional states never settle`() {
        assertFalse(isRestartSettled(sawRestart = true, state = EngineState.STARTING, pidBefore = 100, pid = 100))
        assertFalse(isRestartSettled(sawRestart = true, state = EngineState.INITIALIZING, pidBefore = 100, pid = 100))
        assertFalse(isRestartSettled(sawRestart = true, state = EngineState.STOPPING, pidBefore = 100, pid = 100))
    }

    @Test
    fun `a new pid settles even without a seen restart`() {
        // StateFlow conflates rapid transitions, so a restart too fast to
        // observe still shows up as a PID change.
        assertTrue(isRestartSettled(sawRestart = false, state = EngineState.RUNNING, pidBefore = 100, pid = 101))
        assertTrue(isRestartSettled(sawRestart = false, state = EngineState.DEGRADED, pidBefore = 100, pid = 101))
    }

    @Test
    fun `same pid without a seen restart does not settle`() {
        assertFalse(isRestartSettled(sawRestart = false, state = EngineState.RUNNING, pidBefore = 100, pid = 100))
        assertFalse(isRestartSettled(sawRestart = false, state = EngineState.RUNNING, pidBefore = null, pid = 100))
    }
}
