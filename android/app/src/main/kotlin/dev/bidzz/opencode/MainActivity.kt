package dev.bidzz.opencode

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import dev.bidzz.opencode.core.engine.EngineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Temporary diagnostics screen for phase 2.
 *
 * Its only job is to show whether the engine starts from a real app process and
 * what the supervisor reports. It is not the product UI and will be replaced.
 */
class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var output: TextView
    private val log = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(32, 32, 32, 32)
        }
        setContentView(ScrollView(this).apply { addView(output) })

        val app = application as OpenCodeApplication
        app.startupFailure?.let {
            line("ENGINE UNAVAILABLE: $it")
            return
        }
        val manager = app.processManager
        if (manager == null) {
            line("ENGINE UNAVAILABLE: process manager not initialised")
            return
        }

        line("nativeLibraryDir: ${applicationInfo.nativeLibraryDir}")
        line("filesDir: ${filesDir.absolutePath}")
        line("")

        scope.launch {
            manager.status.collectLatest { status ->
                render(status.state, status.pid, status.credentials?.password, status.lastError)
            }
        }

        scope.launch {
            line("starting engine...")
            val result = manager.start()
            result.onSuccess { creds ->
                line("")
                line("=== ENGINE RUNNING ===")
                line("baseUrl:  ${creds.baseUrl}")
                line("password: ${creds.password}")
                line("auth:     ${creds.basicAuthHeader}")
            }.onFailure { e ->
                line("")
                line("=== START FAILED ===")
                line("${e::class.java.simpleName}: ${e.message}")
                line("")
                line("--- recent engine output ---")
                manager.status.value.recentOutput.takeLast(25).forEach { line("  $it") }
            }
        }
    }

    private fun render(state: EngineState, pid: Int?, password: String?, error: String?) {
        line("state: $state  pid: ${pid ?: "-"}  ${error ?: ""}")
    }

    private fun line(text: String) {
        log.append(text).append('\n')
        runOnUiThread { output.text = log.toString() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
