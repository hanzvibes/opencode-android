package dev.bidzz.opencode

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.core.engine.EngineStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Diagnostics screen.
 *
 * Its only job is to show what the supervisor reports and to drive the engine
 * service. It is not the product UI and will be replaced.
 *
 * It no longer starts the engine itself: the engine's lifetime is owned by
 * [dev.bidzz.opencode.service.EngineService], so that destroying this Activity
 * (rotation, back press) cannot cancel a start that is still in flight.
 */
class MainActivity : Activity() {
    private companion object {
        const val REQUEST_NOTIFICATIONS = 1
    }

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

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 16, 32, 0)
            addView(
                Button(this@MainActivity).apply {
                    text = "Start"
                    setOnClickListener { (application as OpenCodeApplication).startEngine() }
                },
            )
            addView(
                Button(this@MainActivity).apply {
                    text = "Stop"
                    setOnClickListener { (application as OpenCodeApplication).stopEngine() }
                },
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                controls,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                ScrollView(this@MainActivity).apply { addView(output) },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        // targetSdk 35 means edge-to-edge is enforced: the window is laid out
        // underneath the status and navigation bars, so without this the buttons
        // sit under the status bar and taps land on the system instead of the app.
        val basePadding = intArrayOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                basePadding[0] + insets.systemWindowInsetLeft,
                basePadding[1] + insets.systemWindowInsetTop,
                basePadding[2] + insets.systemWindowInsetRight,
                basePadding[3] + insets.systemWindowInsetBottom,
            )
            insets
        }
        setContentView(root)

        val app = application as OpenCodeApplication
        app.startupFailure?.let {
            line("ENGINE UNAVAILABLE: $it")
            return
        }
        if (app.processManager == null) {
            line("ENGINE UNAVAILABLE: process manager not initialised")
            return
        }

        line("nativeLibraryDir: ${applicationInfo.nativeLibraryDir}")
        line("filesDir: ${filesDir.absolutePath}")
        line("")

        requestNotificationPermissionIfNeeded()

        scope.launch {
            app.engineStatus.collectLatest { render(it) }
        }

        line("asking the engine service to start...")
        app.startEngine()
    }

    /**
     * Asks for POST_NOTIFICATIONS on API 33+.
     *
     * Denial is not fatal: a foreground service runs regardless, the notification
     * is simply hidden. So this is a one-shot request with no gating logic.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    private fun render(status: EngineStatus) {
        val pid = status.pid?.let { "pid: $it" } ?: "pid: -"
        val port = status.port?.let { "port: $it" } ?: "port: -"
        line("state: ${status.state}  $pid  $port  ${status.lastError ?: ""}")

        val credentials = status.credentials
        if (status.state == EngineState.RUNNING && credentials != null) {
            line("  baseUrl:  ${credentials.baseUrl}")
            line("  password: ${credentials.password}")
            line("  auth:     ${credentials.basicAuthHeader}")
        }
        if (status.state == EngineState.FAILED) {
            line("")
            line("--- recent engine output ---")
            status.recentOutput.takeLast(25).forEach { line("  $it") }
        }
    }

    private fun line(text: String) {
        log.append(text).append('\n')
        runOnUiThread { output.text = log.toString() }
    }

    override fun onDestroy() {
        // Only this screen's collector dies here. The engine keeps running under
        // the foreground service.
        scope.cancel()
        super.onDestroy()
    }
}
