package dev.bidzz.opencode

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import java.io.File
import dev.bidzz.opencode.ui.OpenCodeApp
import kotlinx.coroutines.launch

/**
 * Hosts the Compose UI and owns the two permission flows.
 *
 * Everything else lives in the composables. The engine is started by the foreground
 * service, not here, so destroying this activity never interrupts it.
 */
class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    private val requestLegacyStorage =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val pickDirectory =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@registerForActivityResult
            val path = treeUriToPath(uri)
            if (path != null) {
                // A refused pick is told, not swallowed (item 30): the
                // picker happily offers the whole storage, and the guard
                // below it must say why that is not a project.
                if (!(application as OpenCodeApplication).switchWorkspace(path)) {
                    Toast.makeText(
                        this,
                        R.string.workspace_too_broad,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }

    /**
     * System file picker for files outside the workspace (SAF).
     *
     * The launcher lives here, not in a composable: a Compose launcher in
     * ChatScreen crashed on launch because the composition has no
     * ActivityResultRegistryOwner. The picked URI is handed to the
     * application, which the chat screen reads in as text.
     */
    private val pickSafFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { (application as OpenCodeApplication).deliverSafFile(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermission()
        (application as OpenCodeApplication).startEngine()
        observeDialogRequests()

        setContent {
            OpenCodeApp(app = application as OpenCodeApplication)
        }
    }

    /**
     * Bridges UI requests to system dialogs.
     *
     * The composables cannot launch activities themselves without holding an
     * Activity reference, which would leak on rotation. They bump a counter on the
     * Application instead, and this collector turns it into a real dialog.
     */
    private fun observeDialogRequests() {
        val app = application as OpenCodeApplication
        lifecycleScope.launch {
            app.workspaceFolderRequest.collect {
                // hasAllFilesAccess() already answers for both API bands, so this must not
                // add its own SDK gate: doing so skipped the legacy runtime request on
                // Android 10 and left the picker with only `Downloads` to show.
                if (!app.workspaceStore.hasAllFilesAccess()) {
                    requestStorageIfNeeded()
                } else {
                    // Best-effort like the SAF launch below (item 5): a
                    // missing DocumentsUI or a finishing activity must not
                    // crash the app; the folder simply never opens.
                    runCatching { pickDirectory.launch(null) }
                        .onFailure { Log.w("MainActivity", "Directory picker launch failed", it) }
                }
            }
        }
        lifecycleScope.launch {
            app.allFilesAccessRequest.collect {
                requestStorageIfNeeded()
            }
        }
        lifecycleScope.launch {
            app.safFileRequest.collect {
                // Best-effort: a missing DocumentsUI or a finishing
                // activity must not crash the app; the chat screen simply
                // never receives a file.
                runCatching { pickSafFile.launch(arrayOf("*/*")) }
                    .onFailure { Log.w("MainActivity", "SAF picker launch failed", it) }
            }
        }
    }

    /**
     * POST_NOTIFICATIONS on API 33+.
     *
     * Denial is not fatal: the foreground service still runs, its notification is
     * simply hidden. Asked once, no gating logic.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Unused for now; the workspace picker lives in Settings. */
    @Suppress("unused")
    private fun requestStorageIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) return
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            runCatching { startActivity(intent) }
        } else {
            requestLegacyStorage.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
            )
        }
    }

    /**
     * Maps a document-tree URI to a filesystem path.
     *
     * The engine needs a POSIX path; a `content://` URI is useless to it. The
     * document id root is the volume (`primary`, or a UUID for removable media),
     * which is the same assumption every file manager on Android makes. Fails
     * closed: an unexpected shape returns null rather than guessing.
     */
    private fun treeUriToPath(uri: Uri): File? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val parts = docId.split(":", limit = 2)
        if (parts.size != 2) return null
        val root = when {
            parts[0].equals("primary", ignoreCase = true) -> Environment.getExternalStorageDirectory()
            parts[0].isEmpty() -> return null
            else -> File("/storage", parts[0])
        }
        return if (parts[1].isEmpty()) root else File(root, parts[1])
    }
}
