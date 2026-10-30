package dev.bidzz.opencode

import android.app.Application
import android.util.Log
import dev.bidzz.opencode.core.engine.EngineBinary
import dev.bidzz.opencode.core.engine.EngineEnvironment
import dev.bidzz.opencode.core.engine.EngineStartupException
import dev.bidzz.opencode.core.engine.ProcessManager
import java.io.File

/**
 * Owns the process supervisor for the app's lifetime.
 *
 * Deliberately not a Service yet: this phase proves the engine can be started and
 * supervised from a real app process, which is a different claim from "the engine
 * runs when started from adb". Foreground service integration is the next step.
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

    override fun onCreate() {
        super.onCreate()

        val nativeLibraryDir = File(applicationInfo.nativeLibraryDir)
        val workspace = File(filesDir, "workspace").apply { mkdirs() }
        val environment = EngineEnvironment(filesDir, cacheDir, workspace)

        try {
            val binary = EngineBinary.resolve(nativeLibraryDir, filesDir)
            processManager = ProcessManager(binary, environment)
            Log.i(TAG, "engine resolved at ${binary.executable.absolutePath}")
        } catch (e: EngineStartupException) {
            // Keep the app usable so a diagnostics screen can explain the problem
            // instead of the process dying on launch.
            startupFailure = e.message
            Log.e(TAG, "engine unavailable: ${e.message}")
        }
    }
}
