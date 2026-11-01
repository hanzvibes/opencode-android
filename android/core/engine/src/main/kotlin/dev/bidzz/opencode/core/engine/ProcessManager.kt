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
)

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
        private const val SHUTDOWN_GRACE_MS = 5_000L
        private const val MAX_OUTPUT_LINES = 200
        private const val PASSWORD_PREFIX = "server password"

        /** One retry covers a port stolen between allocation and bind. */
        private const val MAX_START_ATTEMPTS = 3
    }

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val process = AtomicReference<Process?>(null)
    private var monitorJob: Job? = null

    /** Serialises start/stop/restart against each other. */
    private val lifecycle = Mutex()

    /** Serialises every write to [_status]. Never held across a suspension point. */
    private val stateLock = Any()

    /** Brings the engine up. Returns once the health check passes. */
    suspend fun start(): Result<EngineCredentials> = lifecycle.withLock { startLocked() }

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

    private suspend fun startLocked(): Result<EngineCredentials> = withContext(Dispatchers.IO + NonCancellable) {
        val current = _status.value.state
        if (current == EngineState.RUNNING) {
            return@withContext _status.value.credentials?.let { Result.success(it) }
                ?: Result.failure(EngineStartupException("Running without credentials"))
        }
        if (!current.canStart) {
            return@withContext Result.failure(IllegalTransitionException(current, EngineState.INITIALIZING))
        }

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
            environment.clearTmp()

            val port = portProvider.allocate()
            val command = buildCommand(port)
            Log.i(TAG, "starting engine (attempt $attempt): ${command.joinToString(" ")}")

            val builder = ProcessBuilder(command)
                .directory(environment.workspace)
                .redirectErrorStream(true)
            builder.environment().clear()
            builder.environment().putAll(environment.variables(binary.toolBinDir))

            val proc = builder.start()
            started = proc
            process.set(proc)
            val pid = readPid(proc)
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
            process.getAndSet(null)?.let { terminate(it) }
            transition(EngineState.STOPPED)
            return@withContext Result.success(Unit)
        }

        transition(EngineState.STOPPING)
        monitorJob?.cancelAndJoin()
        monitorJob = null
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

        // Drain output on a background thread so the pipe never fills, and keep the
        // last lines for diagnostics. The password line is picked out here.
        val reader = Thread {
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        appendOutput(line)
                        if (password.get() == null && line.startsWith(PASSWORD_PREFIX)) {
                            password.compareAndSet(null, line.removePrefix(PASSWORD_PREFIX).trim())
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
                if (failures >= HEALTH_FAILURE_LIMIT && _status.value.state == EngineState.RUNNING) {
                    Log.w(TAG, "engine unhealthy $failures times, marking DEGRADED")
                    transition(EngineState.DEGRADED, error = "Health check failing")
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
     * Reads the child's PID.
     *
     * `Process.pid()` is a Java 9+ method and is not present in the Android API 29
     * java.lang.Process stub, so it is resolved reflectively. A missing PID only
     * affects diagnostics, never supervision, so failure returns null.
     */
    private fun readPid(proc: Process): Int? =
        runCatching {
            val method = proc.javaClass.getMethod("pid")
            (method.invoke(proc) as Long).toInt()
        }.getOrNull()

    /** True when the engine's own output says it could not bind the port. */
    private fun looksLikePortConflict(output: String): Boolean = isPortConflict(output)

    /**
     * Applies a state change, or refuses it.
     *
     * Credentials and the port survive into DEGRADED (the monitor needs them to
     * test recovery) and are cleared everywhere else, so a stopped supervisor
     * never reports a stale password or port.
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
            lastError = error,
        )
        true
    }

    private fun appendOutput(line: String) {
        synchronized(stateLock) {
            _status.value = _status.value.copy(
                recentOutput = (_status.value.recentOutput + line).takeLast(MAX_OUTPUT_LINES),
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
