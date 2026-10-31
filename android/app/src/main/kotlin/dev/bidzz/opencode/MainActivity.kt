package dev.bidzz.opencode

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.core.engine.EngineStatus
import java.io.File
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
        const val REQUEST_LEGACY_STORAGE = 2
        const val REQUEST_PICK_DIRECTORY = 3
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
            addView(
                Button(this@MainActivity).apply {
                    text = "Workspace"
                    setOnClickListener { pickWorkspace() }
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
        line("workspace: ${app.workspaceStore.resolve().absolutePath}")
        line("all-files access: ${app.workspaceStore.hasAllFilesAccess()}")
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

    /**
     * Opens the system screen for all-files access, then the directory picker.
     *
     * Two separate steps because they are two separate grants: all-files access is
     * a special app access, not a runtime permission, so it cannot be requested
     * with `requestPermissions`. The directory itself is chosen afterwards so the
     * user sees the effect of the grant.
     */
    private fun pickWorkspace() {
        val app = application as OpenCodeApplication
        if (!app.workspaceStore.hasAllFilesAccess()) {
            line("")
            line("all-files access is required to use a folder on shared storage")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                runCatching { startActivity(intent) }.onFailure {
                    // Some OEM builds do not expose the per-app screen.
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } else {
                // API 29: legacy read/write permissions are runtime grants.
                requestPermissions(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ),
                    REQUEST_LEGACY_STORAGE,
                )
            }
            return
        }
        launchDirectoryPicker()
    }

    private fun launchDirectoryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
        runCatching { startActivityForResult(intent, REQUEST_PICK_DIRECTORY) }.onFailure {
            line("no document picker available: ${it.message}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        when (requestCode) {
            REQUEST_LEGACY_STORAGE -> if (granted) launchDirectoryPicker() else line("storage permission denied")
            else -> Unit
        }
    }

    @Deprecated("startActivityForResult is adequate for this temporary diagnostics screen")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_DIRECTORY) return
        if (resultCode != RESULT_OK || data?.data == null) {
            line("workspace selection cancelled")
            return
        }
        val treeUri = data.data!!
        val path = treeUriToPath(treeUri)
        if (path == null) {
            // The engine cannot use a content:// URI, and this provider does not
            // expose a real path. Saying so is better than starting the engine on
            // a directory it cannot see.
            line("this folder cannot be used: no POSIX path available for $treeUri")
            return
        }
        val app = application as OpenCodeApplication
        if (!app.switchWorkspace(path)) {
            line("refused workspace: ${path.absolutePath} (must be a writable folder, not a storage root)")
            return
        }
        line("workspace -> ${path.absolutePath}")
        line("restart the engine for the change to take effect")
    }

    /**
     * Turns a document-tree URI into a filesystem path.
     *
     * `DocumentsContract` exposes the volume id as the URI authority's document id
     * root; on Android that maps to `/storage/<volumeId>` for the primary and
     * removable volumes. This is the same assumption every file manager on Android
     * makes, and it fails closed: if the shape is not what we expect, the caller
     * reports that the folder is unusable rather than guessing.
     */
    private fun treeUriToPath(uri: Uri): File? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val parts = docId.split(":", limit = 2)
        if (parts.size != 2) return null
        val volume = parts[0]
        val relative = parts[1]
        val root = when {
            volume.equals("primary", ignoreCase = true) -> Environment.getExternalStorageDirectory()
            volume.isEmpty() -> return null
            else -> File("/storage", volume)
        }
        return if (relative.isEmpty()) root else File(root, relative)
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
