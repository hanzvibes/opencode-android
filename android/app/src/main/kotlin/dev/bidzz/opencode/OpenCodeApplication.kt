package dev.bidzz.opencode

import android.app.Application
import android.content.Intent
import android.util.Log
import dev.bidzz.opencode.core.engine.EngineBinary
import dev.bidzz.opencode.core.engine.EngineEnvironment
import dev.bidzz.opencode.core.engine.EngineStartupException
import dev.bidzz.opencode.core.engine.EngineStatus
import dev.bidzz.opencode.core.engine.ProcessManager
import dev.bidzz.opencode.service.EngineService
import dev.bidzz.opencode.workspace.WorkspaceStore
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the process supervisor for the app's lifetime.
 *
 * The supervisor lives here rather than in the service so that the UI can read
 * engine state without binding, and so that state survives the service being
 * torn down and recreated. [EngineService] is what keeps the process alive
 * across a task swipe; this class only holds the handle.
 */
class OpenCodeApplication : Application() {
    companion object {
        private const val TAG = "OpenCodeApp"
    }

    /** Null until [onCreate] finishes, or when the engine binary is unavailable. */
    var processManager: ProcessManager? = null
        private set

    /** User-facing reason the engine is unavailable, or null when it resolved. */
    var startupFailure: String? = null
        private set

    lateinit var workspaceStore: WorkspaceStore
        private set

    /**
     * HTTP/SSE layer for the running engine.
     *
     * Separate from [processManager] because the credentials change on every engine
     * restart: the port is dynamic and the password is re-printed to stdout, so the
     * API handle is rebuilt each time rather than cached across restarts.
     */
    lateinit var engineApi: dev.bidzz.opencode.api.EngineApi
        private set

    /**
     * Session the user was last looking at.
     *
     * Held in memory rather than on disk: the engine owns session persistence, and
     * this is only a hint for restoring the screen after the activity is recreated.
     * Writing it to `SharedPreferences` would survive a reboot but point at a
     * session the engine may no longer have.
     */
    var lastSessionID: String? = null

    /** Fallback status published when the binary never resolved. */
    private val unavailableStatus = MutableStateFlow(EngineStatus())

    /**
     * Single source of truth for engine state, read by both the service and the
     * UI. Never null: a broken install reports FAILED with the reason attached
     * instead of forcing every caller to handle absence.
     */
    val engineStatus: StateFlow<EngineStatus>
        get() = processManager?.status ?: unavailableStatus

    override fun onCreate() {
        super.onCreate()

        val nativeLibraryDir = File(applicationInfo.nativeLibraryDir)
        workspaceStore = WorkspaceStore(this)
        engineApi = dev.bidzz.opencode.api.EngineApi { workspaceStore.resolve().absolutePath }

        // The environment reads the workspace through this lambda on every access,
        // so changing the project does not require a new supervisor or a new
        // engine home. The engine's own state must not live inside the project.
        val environment = EngineEnvironment(filesDir, cacheDir) { workspaceStore.resolve() }

        try {
            val binary = EngineBinary.resolve(nativeLibraryDir, filesDir)
            processManager = ProcessManager(binary, environment)
            Log.i(TAG, "engine resolved at ${binary.executable.absolutePath}")
            Log.i(TAG, "workspace: ${environment.workspace.absolutePath}")
        } catch (e: EngineStartupException) {
            // Keep the app usable so a diagnostics screen can explain the problem
            // instead of the process dying on launch.
            startupFailure = e.message
            unavailableStatus.value = EngineStatus(lastError = e.message)
            Log.e(TAG, "engine unavailable: ${e.message}")
        }
    }

    /**
     * Asks the foreground service to bring the engine up.
     *
     * Returns false when the binary is missing, so the caller can show the reason
     * instead of starting a service that can only fail.
     */
    fun startEngine(): Boolean = sendToService(EngineService.ACTION_START)

    /** Asks the foreground service to stop the engine and itself. */
    fun stopEngine(): Boolean = sendToService(EngineService.ACTION_STOP)

    /**
     * Points the engine at a different project directory.
     *
     * The engine takes its working directory at process start, so a switch is only
     * real after a restart. The caller is responsible for stopping first: doing it
     * here would race with whatever else is driving the service.
     */
    fun switchWorkspace(dir: File): Boolean {
        if (!workspaceStore.isAcceptableWorkspace(dir)) {
            Log.w(TAG, "refusing workspace: ${dir.absolutePath}")
            return false
        }
        workspaceStore.selectedPath = dir.absolutePath
        Log.i(TAG, "workspace switched to ${dir.absolutePath}")
        return true
    }

    /**
     * Delivers a command to [EngineService].
     *
     * `startForegroundService` is used for both actions, not just start: the
     * service must call `startForeground` within five seconds of being started,
     * and going through the same entry point keeps that obligation in one place
     * instead of depending on which action arrived. `minSdk` is 29, so the API
     * needs no version branch.
     */
    private fun sendToService(action: String): Boolean {
        if (processManager == null) {
            Log.w(TAG, "engine command $action ignored: ${startupFailure ?: "supervisor not initialised"}")
            return false
        }
        startForegroundService(Intent(this, EngineService::class.java).setAction(action))
        return true
    }
}
