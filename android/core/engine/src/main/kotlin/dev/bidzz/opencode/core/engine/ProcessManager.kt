package dev.bidzz.opencode.core.engine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Credentials the host must present to the local server. */
data class EngineCredentials(
    /** Base URL, always loopback. */
    val baseUrl: String,
    /** Password printed by the engine on startup. */
    val password: String,
) {
    /** Value for `Authorization: Basic base64("opencode:<password>")`. */
    val basicAuthHeader: String
        get() {
            val raw = "opencode:$password"
            val encoded = android.util.Base64.encodeToString(raw.toByteArray(), android.util.Base64.NO_WRAP)
            return "Basic $encoded"
        }

    /**
     * Never prints the secret (audit-100 53-F1): the default data-class
     * `toString()` would put `password=<secret>` — and the base64 header
     * with it — into any log line that prints the object.
     */
    override fun toString(): String = "EngineCredentials(baseUrl=$baseUrl, password=[redacted])"
}

/** Immutable snapshot of the supervisor's observable state. */
data class EngineStatus(
    val state: EngineState = EngineState.STOPPED,
    val credentials: EngineCredentials? = null,
    val pid: Int? = null,
    /** Loopback port for the current or most recent attempt; null when stopped. */
    val port: Int? = null,
    val lastError: String? = null,
    /** Recent stdout/stderr lines, for the diagnostics screen. */
    val recentOutput: List<String> = emptyList(),
    /**
     * Last Antigravity plugin patch report (audit-100 31-F2): null before the
     * first run. When the patch lands after the engine started, the report's
     * [AntigravityPatchReport.restartRequired] says a restart is needed.
     */
    val antigravityPatch: AntigravityPatchReport? = null,
    /**
     * True once the post-start re-patch poll has given up waiting for the
     * plugin (audit-200 B7, M-209): 30 × 10 s with the plugin still
     * NOT_FOUND. While false the UI may promise "the patch will run once
     * it appears"; once true that promise is over and the UI must say so.
     * Cleared when a new poll starts.
     */
    val antigravityPatchPollEnded: Boolean = false,
) {
    /**
     * Never prints the credentials object (audit-100 53-F1): the default
     * data-class `toString()` would inline `EngineCredentials.toString()`
     * — masked since 6h, but the status must not depend on that. Only
     * whether credentials are present is shown.
     */
    override fun toString(): String =
        "EngineStatus(state=$state, credentials=${if (credentials == null) "null" else "present"}, " +
            "pid=$pid, port=$port, lastError=$lastError, recentOutput=$recentOutput, " +
            "antigravityPatch=$antigravityPatch)"
}

/**
 * Chooses the loopback port the engine listens on.
 *
 * Injected so the supervisor can be tested with a fixed port, and so a
 * deliberately fixed port stays available for `adb forward` debugging.
 */
fun interface PortProvider {
    fun allocate(): Int
}

/**
 * Asks the kernel for a free port, then releases it.
 *
 * The port is free at the moment it is returned but could in principle be taken
 * before the engine binds it. That window is milliseconds wide and the engine
 * only binds after Bun finishes booting, so [ProcessManager] treats a bind
 * failure as retryable rather than fatal.
 *
 * The socket binds to `127.0.0.1` specifically, not `InetAddress.loopbackAddress`:
 * the latter can resolve to `::1`, and a port free on IPv6 is not necessarily
 * free on IPv4, which is what the engine binds.
 */
class EphemeralPortProvider : PortProvider {
    override fun allocate(): Int =
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
}

/** Always returns the same port. Useful for `adb forward tcp:4096` debugging. */
class FixedPortProvider(private val port: Int) : PortProvider {
    override fun allocate(): Int = port
}

/**
 * Supervises the embedded OpenCode server process.
 *
 * Responsibilities, per the project plan:
 * resolve the binary, prepare the environment, start the process, capture
 * output, detect startup failure, health check, monitor exit, terminate
 * gracefully, recover from crashes, and refuse duplicate instances.
 *
 * Deliberately not a Service. It owns no lifecycle of its own; a foreground
 * service drives it. That keeps this class testable on the JVM.
 *
 * ## Concurrency contract
 *
 * Lifecycle operations ([start], [stop], [restart]) are serialised by a [Mutex],
 * so two callers can never drive the process at the same time. The critical
 * sections run under [NonCancellable]: the engine takes up to a minute to become
 * healthy, and a caller whose scope dies mid-start (an Activity being destroyed,
 * for instance) must not leave a half-started child process behind.
 *
 * State mutation is serialised by [stateLock] and validated against
 * [EngineState.canTransitionTo]. A refused transition is logged as an error and
 * dropped rather than thrown: this object runs inside a foreground service where
 * an exception on a background coroutine would take down the whole app, and the
 * state machine is already covered by tests.
 */
class ProcessManager(
    private val binary: EngineBinary,
    private val environment: EngineEnvironment,
    private val portProvider: PortProvider = EphemeralPortProvider(),
    private val healthCheck: HealthCheck = HttpHealthCheck(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    companion object {
        private const val TAG = "EngineProcess"

        /** Historical fixed port. Only used when a [FixedPortProvider] asks for it. */
        const val DEFAULT_PORT = 4096

        private const val STARTUP_TIMEOUT_MS = 60_000L
        private const val HEALTH_INTERVAL_MS = 2_000L
        private const val HEALTH_FAILURE_LIMIT = 5
        /** Extra consecutive failures while DEGRADED before escalating to FAILED (item 21). */
        private const val DEGRADED_ESCALATION_LIMIT = 5
        private const val SHUTDOWN_GRACE_MS = 5_000L
        private const val MAX_OUTPUT_LINES = 200
        /** The stdout line the engine prints its password on. Shared with [redactEnginePassword]. */
        internal const val PASSWORD_PREFIX = "server password"

        /** Covers the gap between fork and the child's exec when looking up its pid. */
        private const val PID_LOOKUP_ATTEMPTS = 20

        /** How long a reaped orphan gets to vanish from /proc before start proceeds (item 29). */
        private const val ZOMBIE_REAP_WAIT_MS = 2_000L

        /** One retry covers a port stolen between allocation and bind. */
        private const val MAX_START_ATTEMPTS = 3

        /** Post-start Antigravity re-patch: how often to check for the downloaded plugin. */
        private const val PATCH_POLL_INTERVAL_MS = 10_000L
        /** …and how long to keep checking (30 × 10s = 5 minutes after boot). */
        private const val PATCH_POLL_ATTEMPTS = 30
        /** Failed patch runs retried this many times before giving up for this boot. */
        private const val PATCH_FAILURE_LIMIT = 3
    }

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val process = AtomicReference<Process?>(null)
    private var monitorJob: Job? = null
    private var patchJob: Job? = null

    /**
     * The password the running engine printed (audit-100 53-F1): the
     * format-anchored redactor cannot mask a second echo site — an
     * auth-failure line, a JSON log line — that prints the value without
     * the `server password` prefix. The value is captured in [awaitReady]
     * and used for value-anchored masking in [appendOutput] and the
     * `lastError` path. Reset on every start: a restarted engine prints a
     * new password, and the old one is dead with it.
     */
    private val capturedPassword = AtomicReference<String?>(null)

    /** Serialises start/stop/restart against each other. */
    private val lifecycle = Mutex()

    /** Serialises every write to [_status]. Never held across a suspension point. */
    private val stateLock = Any()

    /**
     * Brings the engine up. Returns once the health check passes.
     *
     * With [preserveBusyOrphan] an engine left over from a previous life
     * is not reaped (audit-200 A9-3, DL-029): it may still be running a
     * turn, and killing it would destroy that work mid-flight. The new
     * engine takes a fresh port, so the orphan holds nothing it needs;
     * the orphan is reaped on the next start, once the flag that asked
     * for this has been cleared.
     */
    suspend fun start(preserveBusyOrphan: Boolean = false): Result<EngineCredentials> =
        lifecycle.withLock { startLocked(preserveBusyOrphan) }

    /** Stops the engine, waiting for the process to exit. */
    suspend fun stop(): Result<Unit> = lifecycle.withLock { stopLocked() }

    /** Restarts after a failure, preserving the workspace. */
    suspend fun restart(): Result<EngineCredentials> = lifecycle.withLock {
        stopLocked()
        startLocked()
    }

    /**
     * Last-resort synchronous shutdown, for `Service.onDestroy`.
     *
     * [stop] is the correct path, but `onDestroy` cannot suspend and blocking the
     * main thread for the full five-second grace period risks an ANR. This method
     * therefore asks politely for a short while and then kills the child outright.
     *
     * Skipping the graceful path risks nothing that matters here: the engine keeps
     * its state in SQLite, which is crash-safe, and the alternative is an orphaned
     * 200 MB process that keeps holding a port after the UI is gone.
     */
    fun shutdownBlocking(graceMs: Long = 1_500L) {
        monitorJob?.cancel()
        monitorJob = null
        patchJob?.cancel()
        patchJob = null
        val proc = process.getAndSet(null)
        if (proc != null) {
            runCatching {
                proc.destroy()
                if (!proc.waitFor(graceMs, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "engine ignored SIGTERM during shutdown, killing")
                    proc.destroyForcibly()
                    proc.waitFor(1, TimeUnit.SECONDS)
                }
            }.onFailure { Log.w(TAG, "shutdown failed: ${it.message}") }
        }
        val current = _status.value.state
        if (current != EngineState.STOPPED) {
            if (current.canTransitionTo(EngineState.STOPPING)) {
                transition(EngineState.STOPPING)
            }
            transition(EngineState.STOPPED)
        }
    }

    private suspend fun startLocked(preserveBusyOrphan: Boolean = false): Result<EngineCredentials> = withContext(Dispatchers.IO + NonCancellable) {
        val current = _status.value.state
        if (current == EngineState.RUNNING) {
            return@withContext _status.value.credentials?.let { Result.success(it) }
                ?: Result.failure(EngineStartupException("Running without credentials"))
        }
        if (!current.canStart) {
            return@withContext Result.failure(IllegalTransitionException(current, EngineState.INITIALIZING))
        }

        // A previous life of this app may have left its engine behind
        // (item 29): the child survives the parent's death, and the
        // service's onDestroy is not guaranteed to run. Reap orphans
        // before the first attempt, so the new engine never contends
        // with a ghost for its port or workspace — unless the flag says
        // the orphan may still be working (DL-029, see [start]).
        reapZombies(preserveBusy = preserveBusyOrphan)

        patchJob?.cancel()
        patchJob = null
        var lastFailure: Throwable? = null
        for (attempt in 1..MAX_START_ATTEMPTS) {
            when (val outcome = attemptStart(attempt)) {
                is StartOutcome.Ready -> return@withContext Result.success(outcome.credentials)
                is StartOutcome.Failed -> {
                    lastFailure = outcome.cause
                    if (!outcome.portConflict) break
                    if (attempt < MAX_START_ATTEMPTS) {
                        Log.w(TAG, "port conflict on attempt $attempt, retrying with a new port")
                    }
                }
            }
        }
        Result.failure(lastFailure ?: EngineStartupException("Engine failed to start"))
    }

    private suspend fun attemptStart(attempt: Int): StartOutcome {
        var started: Process? = null
        try {
            transition(EngineState.INITIALIZING)
            environment.prepare()
            environment.lastPatchReport?.let { updatePatchReport(it) }
            environment.clearTmp()

            val port = portProvider.allocate()
            val command = buildCommand(port)
            Log.i(TAG, "starting engine (attempt $attempt): ${command.joinToString(" ")}")

            val builder = ProcessBuilder(command)
                .directory(environment.workspace)
                .redirectErrorStream(true)
            builder.environment().clear()
            builder.environment().putAll(environment.variables(binary.toolBinDir, binary.nativeLibraryDir))

            val proc = builder.start()
            started = proc
            process.set(proc)
            val pid = readPid()
            transition(EngineState.STARTING, pid = pid, port = port)

            val credentials = awaitReady(proc, port)
            if (credentials == null) {
                // Startup failed: capture why, then clean up so we do not leak a
                // half-started process.
                val reason = _status.value.recentOutput.takeLast(5).joinToString(" | ")
                terminate(proc)
                process.compareAndSet(proc, null)
                transition(EngineState.FAILED, error = "Engine did not become healthy. $reason")
                return StartOutcome.Failed(
                    cause = EngineStartupException("Engine did not become healthy within ${STARTUP_TIMEOUT_MS}ms"),
                    portConflict = looksLikePortConflict(reason),
                )
            }

            transition(EngineState.RUNNING, credentials = credentials, pid = pid, port = port)
            monitorJob = scope.launch { monitor(proc) }
            // Fresh install: prepare() ran before the plugin was downloaded, so its
            // patch reported NOT_FOUND. The engine downloads the plugin after boot;
            // keep re-trying the patch until it lands (or the plugin proves to be a
            // version the patch does not know). A patch applied here still needs a
            // restart — the running engine already loaded the unpatched code.
            if (environment.lastPatchReport?.outcome == AntigravityPatchOutcome.NOT_FOUND ||
                environment.lastPatchReport?.outcome == AntigravityPatchOutcome.FAILED
            ) {
                patchJob = scope.launch { repatchAntigravityAfterDownload(proc) }
            }
            return StartOutcome.Ready(credentials)
        } catch (t: Throwable) {
            Log.e(TAG, "engine start failed", t)
            // Never leave a child running behind a failure.
            process.getAndSet(null)?.let { terminate(it) } ?: started?.let { terminate(it) }
            transition(EngineState.FAILED, error = t.message ?: t::class.java.simpleName)
            return StartOutcome.Failed(t, portConflict = false)
        }
    }

    private suspend fun stopLocked(): Result<Unit> = withContext(Dispatchers.IO + NonCancellable) {
        val current = _status.value.state
        if (current == EngineState.STOPPED) return@withContext Result.success(Unit)

        // STOPPING is only reachable from a state that owns a live process. From
        // FAILED or INITIALIZING there is nothing to wait for, and asking for
        // STOPPING would be an illegal transition — which is exactly the bug that
        // made `restart()` throw after a crash.
        if (!current.canTransitionTo(EngineState.STOPPING)) {
            monitorJob?.cancelAndJoin()
            monitorJob = null
            patchJob?.cancel()
            patchJob = null
            process.getAndSet(null)?.let { terminate(it) }
            transition(EngineState.STOPPED)
            return@withContext Result.success(Unit)
        }

        transition(EngineState.STOPPING)
        monitorJob?.cancelAndJoin()
        monitorJob = null
        patchJob?.cancel()
        patchJob = null
        process.getAndSet(null)?.let { terminate(it) }
        transition(EngineState.STOPPED)
        Result.success(Unit)
    }

    private fun buildCommand(port: Int): List<String> = listOf(
        binary.executable.absolutePath,
        "serve",
        "--hostname", "127.0.0.1",
        "--port", port.toString(),
    )

    /**
     * Reads stdout until the server prints its password, then waits for the health
     * check. Returns null on timeout or early process exit.
     */
    private suspend fun awaitReady(proc: Process, port: Int): EngineCredentials? {
        val deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS
        // Written by the stdout thread, read by this one.
        val password = AtomicReference<String?>(null)
        capturedPassword.set(null)

        // Drain output on a background thread so the pipe never fills, and keep the
        // last lines for diagnostics. The password line is picked out here.
        val reader = Thread {
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        appendOutput(line)
                        if (password.get() == null && line.startsWith(PASSWORD_PREFIX)) {
                            if (password.compareAndSet(null, line.removePrefix(PASSWORD_PREFIX).trim())) {
                                // 53-F1: the value itself is now known — later
                                // lines that echo it without the prefix can be
                                // masked too (see redactEnginePassword).
                                capturedPassword.set(password.get())
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // Stream closed during shutdown; not an error worth surfacing.
            }
        }.apply {
            isDaemon = true
            name = "engine-stdout"
            start()
        }

        val baseUrl = "http://127.0.0.1:$port"
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) {
                reader.join(500)
                return null
            }
            val candidate = password.get()
            if (candidate != null) {
                val creds = EngineCredentials(baseUrl, candidate)
                if (healthCheck.isHealthy(creds)) return creds
            }
            delay(250)
        }
        return null
    }

    /** Publishes a patch report without changing the engine state. */
    private fun updatePatchReport(report: AntigravityPatchReport) {
        synchronized(stateLock) {
            // Audit-200 B7 (M-209): a fresh report restarts the wait — the
            // "poll ended" flag belongs to the previous wait only. The
            // flag is set after the loop ends, when no more reports come,
            // so clearing it here can never fight the exhausted state.
            _status.value = _status.value.copy(
                antigravityPatch = report,
                antigravityPatchPollEnded = false,
            )
        }
    }

    /**
     * Re-runs the Antigravity plugin patch while the engine runs, for the fresh-
     * install case where the plugin was not downloaded yet when [EngineEnvironment.prepare]
     * ran (audit-100 31-F2). Polls until the patch lands, the plugin proves to be
     * already patched or an unknown version, or the window runs out; stops early
     * when the process dies. A FAILED run is retried a few times (a transient
     * disk error can clear) and then left for the next start to retry.
     */
    private suspend fun repatchAntigravityAfterDownload(proc: Process) {
        // Audit-200 B7 (M-209): a new poll is waiting again — the "stopped
        // waiting" flag from a previous boot must not linger into it.
        synchronized(stateLock) {
            _status.value = _status.value.copy(antigravityPatchPollEnded = false)
        }
        var failures = 0
        var lastOutcome: AntigravityPatchOutcome? = null
        repeat(PATCH_POLL_ATTEMPTS) {
            delay(PATCH_POLL_INTERVAL_MS)
            if (!proc.isAlive) return
            val report = runCatching {
                environment.patchAntigravityPlugin(restartRequired = true)
            }.getOrNull() ?: return
            updatePatchReport(report)
            lastOutcome = report.outcome
            when (report.outcome) {
                AntigravityPatchOutcome.APPLIED,
                AntigravityPatchOutcome.ALREADY_PATCHED,
                AntigravityPatchOutcome.VERSION_MISMATCH -> return
                AntigravityPatchOutcome.FAILED -> {
                    failures++
                    if (failures >= PATCH_FAILURE_LIMIT) return
                }
                AntigravityPatchOutcome.NOT_FOUND -> Unit // plugin not downloaded yet; keep polling
            }
        }
        // Audit-200 B7 (M-209): the 5-minute window ran out with the plugin
        // still missing — the UI must stop promising the patch will run
        // once it appears. (FAILED gives up earlier with its own report,
        // so only NOT_FOUND ends here.)
        if (lastOutcome == AntigravityPatchOutcome.NOT_FOUND) {
            synchronized(stateLock) {
                _status.value = _status.value.copy(antigravityPatchPollEnded = true)
            }
        }
    }

    /** Polls health while the process lives; degrades after repeated failures. */
    private suspend fun monitor(proc: Process) {
        var failures = 0
        while (proc.isAlive) {
            delay(HEALTH_INTERVAL_MS)
            val creds = _status.value.credentials ?: continue
            if (healthCheck.isHealthy(creds)) {
                failures = 0
                if (_status.value.state == EngineState.DEGRADED) {
                    transition(EngineState.RUNNING, credentials = creds)
                }
            } else {
                failures++
                when (_status.value.state) {
                    EngineState.RUNNING -> {
                        if (failures >= HEALTH_FAILURE_LIMIT) {
                            Log.w(TAG, "engine unhealthy $failures times, marking DEGRADED")
                            transition(EngineState.DEGRADED, error = "Health check failing")
                        }
                    }
                    EngineState.DEGRADED -> {
                        // A server that stays unhealthy does not heal (item 21):
                        // after a second full failure window the supervisor
                        // stops waiting and escalates to FAILED, so the
                        // resurrect path brings a fresh engine and the
                        // composer stops accepting sends that would hang
                        // until the 120s timeout. The process is terminated
                        // first — FAILED with a live process would resurrect
                        // a second engine next to it.
                        if (failures >= HEALTH_FAILURE_LIMIT + DEGRADED_ESCALATION_LIMIT) {
                            Log.w(TAG, "engine unhealthy $failures times while DEGRADED, escalating to FAILED")
                            terminate(proc)
                            process.compareAndSet(proc, null)
                            transition(EngineState.FAILED, error = "Engine stayed unhealthy")
                            return
                        }
                    }
                    else -> Unit
                }
            }
        }
        // The process exited. If we did not ask for it, this is a crash. `stop()`
        // may have won the race and already moved to STOPPED, in which case there
        // is nothing to report and the transition is refused.
        if (_status.value.state != EngineState.STOPPING) {
            val code = runCatching { proc.exitValue() }.getOrNull()
            Log.w(TAG, "engine exited unexpectedly, code=$code")
            process.compareAndSet(proc, null)
            transition(EngineState.FAILED, error = "Engine exited unexpectedly (code $code)")
        }
    }

    /** SIGTERM, then SIGKILL if it does not exit within the grace period. */
    private fun terminate(proc: Process) {
        runCatching {
            proc.destroy()
            if (!proc.waitFor(SHUTDOWN_GRACE_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "engine ignored SIGTERM, killing")
                proc.destroyForcibly()
                proc.waitFor(2, TimeUnit.SECONDS)
            }
        }
    }

    /**
     * The engine's pid, located by reading `/proc`.
     *
     * Android's `java.lang.Process` has no `pid()`: the method is desktop Java 9 and
     * is not part of the platform, so the reflective lookup that used to live here
     * found nothing and the diagnostics screen printed a dash while the engine ran.
     * The child shares this app's uid, so its `/proc` entry is readable, and `argv[0]`
     * is the exact binary path `buildCommand` execed.
     */
    private suspend fun readPid(): Int? {
        val executable = binary.executable.absolutePath
        // start() returns on fork, before the child's exec lands, so its cmdline still
        // reads as this process's name for a few milliseconds. Poll instead of giving
        // up and reporting a dash next to a running engine.
        repeat(PID_LOOKUP_ATTEMPTS) {
            val found = scanForEnginePid(executable)
            if (found != null) return found
            delay(50)
        }
        return null
    }

    /**
     * Kills engine processes left over from a previous life of this app
     * (item 29), and waits briefly for them to vanish.
     *
     * The engine is a child of the app process, and Android does not kill
     * children when the parent dies — a force-stop or a low-memory kill
     * leaves the engine holding its port and ~200 MB with nothing left to
     * talk to it. The service's onDestroy is the only other place the
     * process is killed, and Android does not guarantee it runs, so every
     * start reaps orphans first. A candidate is any process whose argv[0]
     * is the engine binary and whose argv[1] is "serve": the exact command
     * this class execs, and no other process can exec that path (it lives
     * in this app's private native library dir). The currently tracked
     * engine, if one exists, is never a candidate — start only runs from
     * a state with no live process, and its pid is skipped anyway.
     *
     * The kill is a parameter so tests can substitute it: the platform
     * `killProcess` is an Android stub under JVM unit tests.
     *
     * With [preserveBusy] the orphans are counted but left alive
     * (audit-200 A9-3, DL-029): one of them may still be running a turn
     * from before the process died. The caller clears the flag that asked
     * for this once the new engine is up, so the next start reaps them.
     *
     * Returns the number of orphans found.
     */
    internal suspend fun reapZombies(
        kill: (Int) -> Unit = { pid -> android.os.Process.killProcess(pid) },
        ownPid: Int = android.os.Process.myPid(),
        preserveBusy: Boolean = false,
    ): Int {
        val executable = binary.executable.absolutePath
        val currentPid = _status.value.pid
        val zombies = scanForEnginePids(executable, ownPid = ownPid).filter { it != currentPid }
        if (preserveBusy) {
            if (zombies.isNotEmpty()) {
                Log.i(TAG, "leaving ${zombies.size} orphaned engine(s) alive: a run may still be in flight")
            }
            return zombies.size
        }
        for (pid in zombies) {
            Log.w(TAG, "reaping orphaned engine pid=$pid")
            runCatching { kill(pid) }
                .onFailure { Log.w(TAG, "could not kill orphaned engine pid=$pid: ${it.message}") }
        }
        // A SIGKILLed process usually vanishes at once; give it a moment
        // before the new engine binds, so the old one cannot still hold
        // the port when the new one starts.
        val deadline = System.currentTimeMillis() + ZOMBIE_REAP_WAIT_MS
        for (pid in zombies) {
            while (System.currentTimeMillis() < deadline && File("/proc/$pid").exists()) {
                delay(50)
            }
            if (File("/proc/$pid").exists()) {
                Log.w(TAG, "orphaned engine pid=$pid still present after reap")
            }
        }
        return zombies.size
    }

    private fun scanForEnginePid(executable: String): Int? =
        scanForEnginePids(executable).firstOrNull()

    /** True when the engine's own output says it could not bind the port. */
    private fun looksLikePortConflict(output: String): Boolean = isPortConflict(output)

    /**
     * Applies a state change, or refuses it.
     *
     * Credentials and the port survive into DEGRADED (the monitor needs them to
     * test recovery) and are cleared everywhere else, so a stopped supervisor
     * never reports a stale password or port. The console output is cleared
     * the same way on terminal states (item 18): it belongs to the dead
     * process, and the failure reason already survives in lastError.
     */
    private fun transition(
        next: EngineState,
        credentials: EngineCredentials? = null,
        pid: Int? = null,
        port: Int? = null,
        error: String? = null,
    ): Boolean = synchronized(stateLock) {
        val current = _status.value
        if (current.state == next) return@synchronized false
        if (!current.state.canTransitionTo(next)) {
            Log.e(TAG, "refusing illegal transition ${current.state} -> $next")
            return@synchronized false
        }
        Log.i(TAG, "state ${current.state} -> $next")
        _status.value = current.copy(
            state = next,
            credentials = when (next) {
                EngineState.RUNNING -> credentials ?: current.credentials
                EngineState.DEGRADED -> current.credentials
                else -> null
            },
            pid = when (next) {
                EngineState.STARTING, EngineState.RUNNING -> pid
                EngineState.DEGRADED -> current.pid
                else -> null
            },
            port = when (next) {
                EngineState.STARTING, EngineState.RUNNING -> port
                EngineState.DEGRADED -> current.port
                else -> null
            },
            lastError = error?.let { redactEnginePassword(it, capturedPassword.get()) },
            recentOutput = when (next) {
                EngineState.FAILED, EngineState.STOPPED -> emptyList()
                else -> current.recentOutput
            },
        )
        true
    }

    private fun appendOutput(line: String) {
        synchronized(stateLock) {
            _status.value = _status.value.copy(
                recentOutput = (_status.value.recentOutput + redactEnginePassword(line, capturedPassword.get())).takeLast(MAX_OUTPUT_LINES),
            )
        }
    }

    /** Result of one start attempt, carrying whether a retry could help. */
    private sealed interface StartOutcome {
        data class Ready(val credentials: EngineCredentials) : StartOutcome
        data class Failed(val cause: Throwable, val portConflict: Boolean) : StartOutcome
    }
}

/** Seam so the supervisor can be tested without a real HTTP server. */
interface HealthCheck {
    suspend fun isHealthy(credentials: EngineCredentials): Boolean
}

/**
 * Masks the engine password in a log line (item 18, audit-100 53-F1).
 *
 * The engine prints `server password <secret>` to stdout on start; the line
 * must never be stored verbatim — Diagnostics shows recentOutput and copies
 * it to the clipboard, and lastError is built from it. The fact that a
 * password was printed stays visible; the secret does not. The same mask is
 * applied when the engine's own log file is read for display.
 *
 * Format-anchored masking alone is not enough: a second echo site (an
 * auth-failure line, a JSON log line) can print the value without the
 * prefix. When [password] is known — the supervisor captures it in
 * `awaitReady` — every occurrence of the value is masked too, including
 * its `Basic base64("opencode:<password>")` form. The base64 form is masked
 * first: it never contains the raw value as a substring, but masking the
 * raw value first could leave a recognisable fragment of the token.
 *
 * Public so the failure notification (EngineService, `:app`) can redact the
 * same way instead of posting exception text verbatim (55-F4).
 */
fun redactEnginePassword(line: String, password: String? = null): String {
    var masked = line
    if (!password.isNullOrEmpty()) {
        val basic = java.util.Base64.getEncoder()
            .encodeToString("opencode:$password".toByteArray(Charsets.UTF_8))
        masked = masked.replace(basic, "[redacted]")
        masked = masked.replace(password, "[redacted]")
    }
    val prefix = ProcessManager.PASSWORD_PREFIX
    val idx = masked.indexOf(prefix)
    if (idx < 0) return masked
    return masked.substring(0, idx + prefix.length) + " [redacted]"
}

/**
 * Detects a bind failure in the engine's output.
 *
 * Kept as a free function so it can be tested directly: the retry decision is the
 * one piece of start-up logic that cannot be exercised without a real port clash.
 *
 * The strings below are the ones actually observed, not guesses. A second engine
 * instance started on an occupied port prints:
 *
 * ```
 * ServeError: ... [cause]: Error: Failed to start server. Is port 41234 in use?
 * ```
 *
 * Note that it says neither `EADDRINUSE` nor `address already in use`, so matching
 * only the errno forms would silently fail to retry. Both families are covered
 * because Bun's wording is not a stable contract.
 */
internal fun isPortConflict(output: String): Boolean {
    val lower = output.lowercase()
    if (lower.contains("eaddrinuse") || lower.contains("address already in use")) return true
    return lower.contains("failed to start server") && lower.contains("in use")
}

/**
 * Every engine pid in /proc: the reap needs all orphans, not just the
 * first (item 29). A match is a process whose argv[0] is the engine
 * binary and whose argv[1] is "serve" — the exact command
 * [ProcessManager] execs, and no other process can exec that path (it
 * lives in this app's private native library dir).
 *
 * Kept as a free function so it can be tested directly: the proc root and
 * own pid are parameters so tests can drive the scan against a fake tree
 * without the Android stubs.
 */
internal fun scanForEnginePids(
    executable: String,
    procRoot: File = File("/proc"),
    ownPid: Int = android.os.Process.myPid(),
): List<Int> {
    val found = mutableListOf<Int>()
    val entries = procRoot.listFiles() ?: return found
    for (entry in entries) {
        val pid = entry.name.toIntOrNull() ?: continue
        if (pid == ownPid) continue
        val argv = runCatching {
            File(entry, "cmdline").readBytes()
                .toString(Charsets.UTF_8)
                .split('\u0000')
        }.getOrNull() ?: continue
        if (argv.getOrNull(0) == executable && argv.getOrNull(1) == "serve") found += pid
    }
    return found
}

/**
 * Calls `GET /api/info` with Basic auth, matching the engine's actual contract.
 *
 * The timeout is deliberately long: while an agent run is streaming from the provider the
 * engine stops answering this request for tens of seconds. A dead process is detected by
 * `isAlive` in [ProcessManager.monitor], so the probe does not need to be impatient.
 */
class HttpHealthCheck(
    private val timeoutMs: Int = 10_000,
) : HealthCheck {
    override suspend fun isHealthy(credentials: EngineCredentials): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs.toLong()) {
            runCatching {
                val url = java.net.URL("${credentials.baseUrl}/api/info")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", credentials.basicAuthHeader)
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                try {
                    val code = connection.responseCode
                    if (code != 200) {
                        // Surface the status: a 401 here means the auth scheme is
                        // wrong, which is a different bug from "not listening yet".
                        Log.w("EngineHealth", "GET /api/info -> $code")
                    }
                    code == 200
                } finally {
                    connection.disconnect()
                }
            }.onFailure {
                Log.w("EngineHealth", "GET /api/info failed: ${it::class.java.simpleName}: ${it.message}")
            }.getOrDefault(false)
        } ?: run {
            Log.w("EngineHealth", "GET /api/info timed out after ${timeoutMs}ms")
            false
        }
    }
}
