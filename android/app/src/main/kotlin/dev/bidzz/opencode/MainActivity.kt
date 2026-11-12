package dev.bidzz.opencode

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.bidzz.opencode.ui.ACTION_NEW_SESSION
import dev.bidzz.opencode.ui.ACTION_OPEN_SESSION
import dev.bidzz.opencode.ui.EXTRA_OPENCODE_ACTION
import dev.bidzz.opencode.ui.EXTRA_SESSION_ID
import dev.bidzz.opencode.ui.OpenCodeApp
import dev.bidzz.opencode.ui.security.AppLockPrefs
import dev.bidzz.opencode.ui.security.LockScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hosts the Compose UI and owns the two permission flows.
 *
 * Everything else lives in the composables. The engine is started by the foreground
 * service, not here, so destroying this activity never interrupts it.
 *
 * A FragmentActivity (not ComponentActivity) because the app lock
 * (Batch 7) uses androidx BiometricPrompt, which requires one.
 */
class MainActivity : FragmentActivity() {

    /**
     * App lock (Batch 7): whether the lock screen has been passed since
     * this foreground session began. Re-armed whenever the activity
     * returns from the background ([onResume] after [onStop]), so leaving
     * the app and coming back asks again. Held as snapshot state so
     * flipping it recomposes the setContent gate below.
     */
    private var unlocked by mutableStateOf(false)
    private var wentBackground = false

    override fun onStop() {
        super.onStop()
        wentBackground = true
    }

    override fun onResume() {
        super.onResume()
        if (wentBackground) {
            wentBackground = false
            unlocked = false
        }
    }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    /**
     * Microphone permission for voice input (Composer C). The answer goes
     * back through the application so the chat screen — which owns the
     * recognizer — can start listening on a grant or show the denial hint
     * without re-asking. A denial is not fatal: the mic just stays off.
     */
    private val requestVoicePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            (application as OpenCodeApplication).deliverVoicePermissionResult(granted)
        }

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
                    (application as OpenCodeApplication).showSnackbar(
                        getString(R.string.workspace_too_broad),
                        long = true,
                    )
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

    /**
     * System save picker for a code block (SAF `CreateDocument`).
     *
     * Same reason as [pickSafFile] for living here. The pending request —
     * filename plus the code verbatim — is taken here, so a cancelled pick
     * writes nothing and a pick that arrives with no request is ignored.
     * The write runs on IO; the result is a snackbar, using the same
     * `code_saved` string the file viewer already uses.
     */
    private val saveCodeDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            val pending = (application as OpenCodeApplication).takePendingCodeSave()
                ?: return@registerForActivityResult
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch(Dispatchers.IO) {
                val saved = runCatching {
                    contentResolver.openOutputStream(uri)?.use { it.write(pending.text.toByteArray()) } != null
                }.getOrDefault(false)
                withContext(Dispatchers.Main) {
                    // A failed save offers one retry: re-open the picker
                    // with the same filename and text, so the user can
                    // pick a different location instead of losing the code.
                    (application as OpenCodeApplication).showSnackbar(
                        getString(if (saved) R.string.code_saved else R.string.code_save_failed),
                        actionLabel = if (saved) null else getString(R.string.chat_retry),
                        onAction = if (saved) null else {
                            { (application as OpenCodeApplication).requestCodeSave(pending.filename, pending.text) }
                        },
                    )
                }
            }
        }

    /**
     * System camera (Native C): the photo is written to the gallery
     * through MediaStore, so no CAMERA permission and no FileProvider
     * are needed — the camera app owns the capture. The URI is handed
     * to the application; the chat screen copies it into the workspace.
     */
    private var pendingPhotoUri: Uri? = null

    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingPhotoUri
            pendingPhotoUri = null
            if (success && uri != null) {
                (application as OpenCodeApplication).deliverCameraPhoto(uri)
            }
        }

    private fun launchCamera() {
        val name = "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenCode")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        pendingPhotoUri = uri
        runCatching { takePhoto.launch(uri) }
            .onFailure { Log.w("MainActivity", "Camera launch failed", it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermission()
        (application as OpenCodeApplication).startEngine()
        observeDialogRequests()

        setContent {
            // App lock (Batch 7): read once per launch. Enabling the lock
            // in Settings does not lock the running session — it takes
            // effect on the next launch or background return.
            val lockEnabled = remember { AppLockPrefs.isEnabled(this) }
            if (lockEnabled && !unlocked) {
                LockScreen(onUnlocked = { unlocked = true })
            } else {
                OpenCodeApp(app = application as OpenCodeApplication)
            }
        }

        handleExternalIntent(intent)
    }

    /**
     * A share or deep link arriving while the app is already open.
     * singleTop in the manifest routes it here instead of recreating
     * the activity (and the engine survives either way).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalIntent(intent)
    }

    /**
     * External entries (Native C): a text share becomes a fresh draft,
     * an opencode://session/{id} link opens that session. Both travel
     * through the application flows, so the UI picks them up whenever
     * it is composed. Anything unrecognised is ignored, never crashed on.
     */
    private fun handleExternalIntent(intent: Intent) {
        val app = application as OpenCodeApplication
        when (intent.action) {
            Intent.ACTION_MAIN -> {
                // Launcher shortcut (Native C): a home-screen tap has no
                // extras, so this only fires for the shortcut intents.
                when (intent.getStringExtra(EXTRA_OPENCODE_ACTION)) {
                    ACTION_NEW_SESSION -> app.requestNewSession()
                    ACTION_OPEN_SESSION -> {
                        val id = intent.getStringExtra(EXTRA_SESSION_ID)
                            ?.takeIf { it.isNotBlank() } ?: return
                        app.deliverSessionOpen(id)
                    }
                }
            }
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/") != true) return
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?.takeIf { it.isNotBlank() } ?: return
                app.deliverSharedText(text)
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                if (uri.scheme != "opencode" || uri.host != "session") return
                val sessionID = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: return
                app.deliverSessionOpen(sessionID)
            }
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
        lifecycleScope.launch {
            app.voicePermissionRequest.collect {
                // Best-effort like the SAF launch above: a finishing
                // activity must not crash the app; the chat screen simply
                // never starts listening.
                runCatching { requestVoicePermission.launch(Manifest.permission.RECORD_AUDIO) }
                    .onFailure { Log.w("MainActivity", "Voice permission request failed", it) }
            }
        }
        lifecycleScope.launch {
            app.codeSaveRequest.collect {
                // Peek, do not take: the result callback takes the request
                // when the picker returns, so a cancelled pick loses
                // nothing and a second tap replaces the pending one.
                val pending = app.peekPendingCodeSave() ?: return@collect
                runCatching { saveCodeDocument.launch(pending.filename) }
                    .onFailure { Log.w("MainActivity", "Code save picker launch failed", it) }
            }
        }
        lifecycleScope.launch {
            app.cameraPhotoRequest.collect {
                // Best-effort like the SAF launch above: a missing camera
                // app or a finishing activity must not crash the app; the
                // composer simply never receives a photo.
                runCatching { launchCamera() }
                    .onFailure { Log.w("MainActivity", "Camera launch failed", it) }
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
