package dev.bidzz.opencode.core.engine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
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
    val lastError: String? = null,
    /** Recent stdout/stderr lines, for the diagnostics screen. */
    val recentOutput: List<String> = emptyList(),
)

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
 */
class ProcessManager(
    private val binary: EngineBinary,
    private val environment: EngineEnvironment,
    private val port: Int = DEFAULT_PORT,
    private val healthCheck: HealthCheck = HttpHealthCheck(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    companion object {
        private const val TAG = "EngineProcess"
        const val DEFAULT_PORT = 4096
        private const val STARTUP_TIMEOUT_MS = 60_000L
        private const val HEALTH_INTERVAL_MS = 2_000L
        private const val HEALTH_FAILURE_LIMIT = 3
        private const val SHUTDOWN_GRACE_MS = 5_000L
        private const val MAX_OUTPUT_LINES = 200
        private const val PASSWORD_PREFIX = "server password"
    }

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val process = AtomicReference<Process?>(null)
    private var monitorJob: Job? = null

    /** Guards start/stop against concurrent callers. */
    private val lifecycleLock = Any()

    val baseUrl: String get() = "http://127.0.0.1:$port"

    /**
     * Brings the engine up. Returns once the health check passes.
     *
     * Idempotent in the sense that calling it while RUNNING is a no-op; calling it
     * concurrently while starting waits for the in-flight attempt.
     */
    suspend fun start(): Result<EngineCredentials> = withContext(Dispatchers.IO) {
        synchronized(lifecycleLock) {
            val current = _status.value.state
            if (current == EngineState.RUNNING) {
                return@withContext _status.value.credentials?.let { Result.success(it) }
                    ?: Result.failure(EngineStartupException("Running without credentials"))
            }
            if (!current.canStart) {
                return@withContext Result.failure(
                    IllegalTransitionException(current, EngineState.INITIALIZING),
                )
            }
            transition(EngineState.INITIALIZING)
        }

        try {
            environment.prepare()
            environment.clearTmp()

            val command = buildCommand()
            Log.i(TAG, "starting engine: ${command.joinToString(" ")}")

            val builder = ProcessBuilder(command)
                .directory(environment.workspace)
                .redirectErrorStream(true)
            builder.environment().clear()
            builder.environment().putAll(environment.variables(binary.toolBinDir))

            val started = builder.start()
            process.set(started)
            transition(EngineState.STARTING, pid = readPid(started))

            val credentials = awaitReady(started)
            if (credentials == null) {
                // Startup failed: capture why, then clean up so we do not leak a
                // half-started process.
                val reason = _status.value.recentOutput.takeLast(5).joinToString(" | ")
                terminate(started)
                transition(EngineState.FAILED, error = "Engine did not become healthy. $reason")
                return@withContext Result.failure(
                    EngineStartupException("Engine did not become healthy within ${STARTUP_TIMEOUT_MS}ms"),
                )
            }

            transition(EngineState.RUNNING, credentials = credentials)
            monitorJob = scope.launch { monitor(started) }
            Result.success(credentials)
        } catch (t: Throwable) {
            Log.e(TAG, "engine start failed", t)
            transition(EngineState.FAILED, error = t.message ?: t::class.java.simpleName)
            Result.failure(t)
        }
    }

    /** Stops the engine, waiting for the process to exit. */
    suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(lifecycleLock) {
            if (_status.value.state == EngineState.STOPPED) return@withContext Result.success(Unit)
            transition(EngineState.STOPPING)
        }
        monitorJob?.cancel()
        monitorJob = null

        val current = process.getAndSet(null)
        if (current != null) terminate(current)
        transition(EngineState.STOPPED)
        Result.success(Unit)
    }

    /** Restarts after a failure, preserving the workspace. */
    suspend fun restart(): Result<EngineCredentials> {
        stop()
        return start()
    }

    private fun buildCommand(): List<String> = listOf(
        binary.executable.absolutePath,
        "serve",
        "--hostname", "127.0.0.1",
        "--port", port.toString(),
    )

    /**
     * Reads stdout until the server prints its password, then waits for the health
     * check. Returns null on timeout or early process exit.
     */
    private suspend fun awaitReady(proc: Process): EngineCredentials? {
        val deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS
        var password: String? = null

        // Drain output on a background thread so the pipe never fills, and keep the
        // last lines for diagnostics. The password line is picked out here.
        val reader = Thread {
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        appendOutput(line)
                        if (password == null && line.startsWith(PASSWORD_PREFIX)) {
                            password = line.removePrefix(PASSWORD_PREFIX).trim()
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

        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) {
                reader.join(500)
                return null
            }
            val candidate = password
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
                    transition(EngineState.RUNNING)
                }
            } else {
                failures++
                if (failures >= HEALTH_FAILURE_LIMIT && _status.value.state == EngineState.RUNNING) {
                    Log.w(TAG, "engine unhealthy $failures times, marking DEGRADED")
                    transition(EngineState.DEGRADED, error = "Health check failing")
                }
            }
        }
        // The process exited. If we did not ask for it, this is a crash.
        if (_status.value.state != EngineState.STOPPING) {
            val code = runCatching { proc.exitValue() }.getOrNull()
            Log.w(TAG, "engine exited unexpectedly, code=$code")
            process.set(null)
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

    private fun transition(
        next: EngineState,
        credentials: EngineCredentials? = _status.value.credentials,
        pid: Int? = _status.value.pid,
        error: String? = null,
    ) {
        val current = _status.value.state
        if (current == next) return
        if (!current.canTransitionTo(next)) {
            // A transition the machine forbids means the supervisor has a bug, not
            // that the caller did something wrong. Fail loudly rather than
            // publishing an impossible state.
            throw IllegalTransitionException(current, next)
        }
        Log.i(TAG, "state $current -> $next")
        _status.value = _status.value.copy(
            state = next,
            credentials = if (next == EngineState.RUNNING) credentials else _status.value.credentials,
            pid = pid,
            lastError = error,
        )
    }

    private fun appendOutput(line: String) {
        val updated = (_status.value.recentOutput + line).takeLast(MAX_OUTPUT_LINES)
        _status.value = _status.value.copy(recentOutput = updated)
    }
}

/** Seam so the supervisor can be tested without a real HTTP server. */
interface HealthCheck {
    suspend fun isHealthy(credentials: EngineCredentials): Boolean
}

/** Calls `GET /api/info` with Basic auth, matching the engine's actual contract. */
class HttpHealthCheck(
    private val timeoutMs: Int = 3_000,
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
