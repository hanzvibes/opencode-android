package dev.bidzz.opencode

import android.Manifest
import android.content.ContentValues
import android.content.Context
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
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.bidzz.opencode.core.engine.StartupTimings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.bidzz.opencode.core.design.applyLaunchWindowTheme
import dev.bidzz.opencode.ui.ACTION_NEW_SESSION
import dev.bidzz.opencode.ui.ACTION_OPEN_SESSION
import dev.bidzz.opencode.ui.EXTRA_OPENCODE_ACTION
import dev.bidzz.opencode.ui.EXTRA_SESSION_ID
import dev.bidzz.opencode.ui.OpenCodeApp
import dev.bidzz.opencode.ui.chat.PendingPhotoStore
import dev.bidzz.opencode.ui.onboarding.OnboardingStore
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
     * The app language (92-F2): the activity's own resources read in the
     * language the user picked, not the phone's — but only as wrapped at
     * creation time. The activity is never recreated on a language change
     * (the manifest holds `locale` in `configChanges`), so a plain
     * `getString` below would keep speaking the old language until the
     * next process start. [localizedString] re-wraps fresh on every call
     * (audit-200 D7, m-860) — the same helper the service notifications
     * use (D1 m-530) — so the snackbars follow a language change at once.
     * The Compose tree wraps its context separately (see `OpenCodeApp`);
     * this covers everything outside it.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageStore.wrap(base, LanguageStore.get(base)))
    }

    /** A string in the language currently stored, not the activity's creation-time locale. */
    private fun localizedString(@StringRes resId: Int): String =
        appLanguageContext(this).getString(resId)

    /**
     * App lock (Batch 7): whether the lock screen has been passed since
     * this foreground session began. Re-armed whenever the activity
     * returns from the background ([onResume] after [onStop]), so leaving
     * the app and coming back asks again. Held as snapshot state so
     * flipping it recomposes the setContent gate below.
     */
    private var unlocked by mutableStateOf(false)
    private var wentBackground = false

    /**
     * Audit-200 D8 (m-1006): true while a system picker or the camera is
     * open. Launching one stops this activity, so without this the lock
     * would re-arm on the return — the user never left the app. The flag
     * is consumed by the next [onResume], which IS the return from the
     * picker: the result callbacks run around onResume, so they must not
     * clear it themselves (a callback that fires first would disarm the
     * guard before onResume reads it).
     */
    private var expectingActivityResult = false

    /**
     * Launches a system picker or the camera, marking that the lock must
     * not re-arm on the return. A failed launch clears the mark at once,
     * so the next real background return still locks.
     */
    private inline fun launchForResult(crossinline launch: () -> Unit) {
        expectingActivityResult = true
        try {
            launch()
        } catch (e: Exception) {
            expectingActivityResult = false
            throw e
        }
    }

    override fun onStop() {
        super.onStop()
        wentBackground = true
    }

    override fun onResume() {
        super.onResume()
        if (wentBackground) {
            wentBackground = false
            // Audit-200 D8 (m-1006): returning from a system picker or
            // the camera is not leaving the app — do not re-arm the lock.
            if (!expectingActivityResult) {
                unlocked = false
            }
            expectingActivityResult = false
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
                        localizedString(R.string.workspace_too_broad),
                        long = true,
                    )
                }
            } else {
                // Audit-200 C4 (M-075): a folder from a provider whose
                // tree document id is not `volume:path` (Drive, OneDrive
                // and friends) used to fail with no sign at all — the
                // picker closed, the workspace stayed put, and nothing
                // said why. A cancel is still silent (uri == null above);
                // an unmapped folder is told.
                Log.w("MainActivity", "Workspace folder not usable: ${uri}")
                (application as OpenCodeApplication).showSnackbar(
                    localizedString(R.string.workspace_folder_unsupported),
                    long = true,
                )
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
        registerForActivityResult(OpenDocumentPersistable()) { uri ->
            uri?.let {
                // Audit-200 D9 (m-488): take a persistable read grant so
                // the file is still readable if the process dies between
                // the pick and the read — the grant used to die with the
                // process and the pick came back unreadable. The grant is
                // deliberately kept afterwards: the file stays re-readable
                // and the grant is cheap metadata, released on uninstall.
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                (application as OpenCodeApplication).deliverSafFile(it)
            }
        }

    /**
     * Whether the system save picker is currently open. A second Save
     * while one is open must not launch a second picker on top of it:
     * the results would arrive out of order and the first callback
     * would take the wrong request. The queued requests open one by
     * one, each after the previous picker returns (05-F2).
     */
    private var codeSavePickerOpen = false

    /**
     * System save picker for a code block (SAF `CreateDocument`).
     *
     * Same reason as [pickSafFile] for living here. The pending request —
     * filename plus the code verbatim — is taken here, so a cancelled pick
     * writes nothing and a pick that arrives with no request is ignored.
     * The write runs on IO; the result is a snackbar, using the same
     * `code_saved` string the file viewer already uses. After the picker
     * returns, the next queued request (if any) opens its picker.
     */
    private val saveCodeDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            codeSavePickerOpen = false
            val app = application as OpenCodeApplication
            val pending = app.takePendingCodeSave()
                ?: run { app.emitCodeSaveRequestIfPending(); return@registerForActivityResult }
            if (uri == null) {
                app.emitCodeSaveRequestIfPending()
                return@registerForActivityResult
            }
            app.emitCodeSaveRequestIfPending()
            lifecycleScope.launch(Dispatchers.IO) {
                val saved = runCatching {
                    contentResolver.openOutputStream(uri)?.use { it.write(pending.text.toByteArray()) } != null
                }.getOrDefault(false)
                withContext(Dispatchers.Main) {
                    // A failed save offers one retry: re-open the picker
                    // with the same filename and text, so the user can
                    // pick a different location instead of losing the code.
                    // Audit-200 A6 (DL-024, DL-025): the failed request is
                    // re-queued at once — ahead of every request whose
                    // picker has not opened yet — so a dismissed snackbar,
                    // a rotation or a process death no longer loses it,
                    // and the retry does not jump behind newer saves.
                    // Retry only re-opens the picker; it must not enqueue
                    // the request a second time.
                    if (!saved) {
                        app.requeueFailedCodeSave(pending.filename, pending.text, codeSavePickerOpen)
                    }
                    app.showSnackbar(
                        localizedString(if (saved) R.string.code_saved else R.string.code_save_failed),
                        actionLabel = if (saved) null else localizedString(R.string.chat_retry),
                        onAction = if (saved) null else {
                            { app.emitCodeSaveRequestIfPending() }
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
     *
     * The pending URI is persisted ([PendingPhotoStore]) as well as held
     * in this field (15-F6): a rotation or process death while the
     * camera is open used to lose it, so the callback came back with
     * success and nowhere to deliver the photo. It is restored in
     * [onCreate], before the result can arrive.
     */
    private val pendingPhotoStore by lazy { PendingPhotoStore(this) }
    private var pendingPhotoUri: Uri? = null

    /** Whether the system camera is currently open (see [launchCamera]). */
    private var cameraOpen = false

    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            cameraOpen = false
            val uri = pendingPhotoUri
            pendingPhotoUri = null
            when {
                success && uri != null -> {
                    // Audit-200 A6 (DL-027): the pending trace is NOT
                    // cleared here — the chat screen is still copying the
                    // photo into the workspace, and a death mid-copy must
                    // leave a trace to retry from. Mark it delivered
                    // instead; the copy's acknowledgement clears it.
                    pendingPhotoStore.markDelivered()
                    (application as OpenCodeApplication).deliverCameraPhoto(uri)
                }
                success -> {
                    // The camera wrote the photo to the gallery, but the
                    // pending URI is gone (15-F6): say so, instead of
                    // silence — the user can attach it again from the
                    // gallery, and the photo itself is not lost.
                    pendingPhotoStore.clear()
                    (application as OpenCodeApplication).showSnackbar(
                        localizedString(R.string.photo_saved_to_gallery),
                    )
                }
                uri != null -> {
                    // Cancelled or failed: the MediaStore row the camera
                    // never wrote to must not linger in the gallery as
                    // an empty entry (73-F6).
                    pendingPhotoStore.clear()
                    runCatching { contentResolver.delete(uri, null, null) }
                }
            }
        }

    /**
     * Audit-200 D9 (m-569): the byte size of a pending photo's MediaStore
     * row, or -1 when the row is gone / unreadable. A row of size 0 is an
     * empty capture that never landed; a row with bytes is a real photo
     * whose callback never arrived. Never delete on size alone without
     * this check — the row must be proven empty first.
     */
    private fun pendingPhotoRowSize(uri: Uri): Long {
        return try {
            contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return -1L
                val index = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                if (index < 0) -1L else cursor.getLong(index)
            } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun launchCamera() {
        // A second tap while the camera is already open would create a
        // second MediaStore row and overwrite the pending URI, orphaning
        // the first row (same single-slot root as 05-F2).
        if (cameraOpen) return
        val name = "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenCode")
        }
        val uri = runCatching { contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }
            .onFailure {
                // Audit-200 D6 (m-148): an insert that THROWS (a dead
                // MediaStore) used to escape to the collector and end in
                // a log line only — the tap died silently.
                Log.w("MainActivity", "MediaStore insert failed", it)
                (application as OpenCodeApplication).showSnackbar(localizedString(R.string.camera_insert_failed))
                return
            }
            .getOrNull()
            ?: run {
                // The gallery refused the row: say so instead of a dead
                // camera tap (audit-100 14-F6).
                Log.w("MainActivity", "MediaStore insert returned null")
                (application as OpenCodeApplication).showSnackbar(localizedString(R.string.camera_insert_failed))
                return
            }
        // Audit-200 D9 (m-569): never overwrite a stale pending URI
        // blindly — its empty row would orphan in the gallery. Clear it
        // first, but only when it holds no photo: a delivered photo's
        // copy may still be running, and its row must survive.
        pendingPhotoStore.get()?.let { stale ->
            if (!pendingPhotoStore.isDelivered() && pendingPhotoRowSize(stale) <= 0) {
                runCatching { contentResolver.delete(stale, null, null) }
                pendingPhotoStore.clear()
            }
        }
        pendingPhotoUri = uri
        pendingPhotoStore.put(uri)
        cameraOpen = true
        runCatching { launchForResult { takePhoto.launch(uri) } }
            .onFailure {
                // The camera never opened: the row just created would sit
                // in the gallery empty (73-F6), and the pending URI would
                // point at it forever.
                cameraOpen = false
                pendingPhotoUri = null
                pendingPhotoStore.clear()
                runCatching { contentResolver.delete(uri, null, null) }
                Log.w("MainActivity", "Camera launch failed", it)
                // Audit-200 D6 (m-148): the failure used to end here, in
                // a log line — the user got no word at all. A new string:
                // the row was created fine, so camera_insert_failed
                // ("Could not create a photo in the gallery") would lie.
                (application as OpenCodeApplication).showSnackbar(localizedString(R.string.camera_open_failed))
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Audit-200 D5 (m-875): cold-start timing starts here — this is
        // the first mark, so every later stage is measured against it.
        StartupTimings.mark("appOnCreate")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Audit-200 D4 (m-014/m-342/m-437/m-500): the launch window follows
        // the SYSTEM theme until the first Compose frame reads ThemeStore —
        // apply the pin here, before setContent, so the window background
        // and the bar icons already match the app theme. The runtime
        // SideEffect in OpenCodeTheme re-asserts the same values.
        applyLaunchWindowTheme(this)

        // A photo the camera was writing when the process died or the
        // screen rotated (15-F6): the TakePicture result is re-delivered
        // to this recreated activity, and the callback reads this field.
        pendingPhotoUri = pendingPhotoStore.get()
        // Audit-200 D9 (m-569): a restored URI used to be trusted
        // blindly — an empty MediaStore row lingered in the gallery
        // forever, and the stale URI sat until the next launch overwrote
        // it. On a FRESH start only (savedInstanceState == null): a
        // recreation still has its camera result coming, and the row may
        // legitimately be empty while the camera writes. Validate the
        // row — empty or gone means the photo was never taken, so delete
        // the row and clear the trace; a row that holds a photo means
        // the shot landed but its callback never arrived, so deliver it
        // instead of deleting a real photo.
        if (savedInstanceState == null && pendingPhotoUri != null && !pendingPhotoStore.isDelivered()) {
            val restored = pendingPhotoUri!!
            if (pendingPhotoRowSize(restored) <= 0) {
                runCatching { contentResolver.delete(restored, null, null) }
                pendingPhotoStore.clear()
                pendingPhotoUri = null
            } else {
                (application as OpenCodeApplication).deliverCameraPhoto(restored)
            }
        }
        // Audit-200 A6 (DL-027): a photo whose result already arrived but
        // whose workspace copy never finished — the process died mid-copy
        // — is delivered again, so the copy runs and the pending trace
        // clears on its acknowledgement. A photo the camera is still
        // writing (not marked delivered) is left alone: its result is
        // still coming through the callback.
        if (pendingPhotoStore.isDelivered() && pendingPhotoUri != null) {
            (application as OpenCodeApplication).deliverCameraPhoto(pendingPhotoUri!!)
        }

        requestNotificationPermission()
        // Audit-200 C10 (M-156): a start that never started is not
        // silent anymore — the reason (usually a missing binary) reaches
        // the user instead of leaving them on a dead chat screen.
        val app = application as OpenCodeApplication
        StartupTimings.mark("engineStartRequested")
        if (!app.startEngine()) {
            app.showSnackbar(app.startupFailure ?: localizedString(R.string.error_engine_not_running))
        }
        observeDialogRequests()
        observeCameraPhotoCopied()

        setContent {
            // Audit-200 D5 (m-875): first-frame timing — this effect runs
            // after the first composition commits, the closest the app
            // gets to "the UI is drawn". reportFullyDrawn() tells the
            // platform the same thing, for TTID measurement.
            LaunchedEffect(Unit) {
                StartupTimings.mark("firstFrame")
                this@MainActivity.reportFullyDrawn()
            }
            // App lock (Batch 7): read once per launch. Enabling the lock
            // in Settings does not lock the running session — it takes
            // effect on the next launch or background return.
            val lockEnabled = remember { AppLockPrefs.isEnabled(this) }
            // Audit-200 D14 (m-531): publish whether the lock screen is
            // showing — the service watcher posts the agent-done
            // notification while it is, instead of swallowing the run.
            LaunchedEffect(lockEnabled, unlocked) {
                (application as OpenCodeApplication).isLockScreenShowing = lockEnabled && !unlocked
            }
            if (lockEnabled && !unlocked) {
                LockScreen(onUnlocked = { unlocked = true })
            } else {
                OpenCodeApp(app = application as OpenCodeApplication)
            }
        }

        // Audit-200 D13 (m-525/m-948, 125-F3): a process death hands the
        // launch intent back with a non-null savedInstanceState, so the
        // old "first launch only" gate threw a deep link away on a
        // restore from recents. The first onCreate in this process is
        // still a first launch even with saved state; a config-change
        // recreate (unfold, resize, font scale) is not.
        val isFirstCreationInProcess = !app.activityCreatedOnce
        app.activityCreatedOnce = true
        handleExternalIntentIfFirstLaunch(
            intent,
            savedInstanceState == null || isFirstCreationInProcess,
        )
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
     * External intents are handled on the FIRST launch only (Fase 5,
     * 69-F11). Android hands the launch intent back to `onCreate` on
     * every recreate — unfold the tablet, resize a multi-window, change
     * the font scale — and re-running it replayed the share/shortcut:
     * `newSession()` wiped the conversation in progress and replaced it
     * with the same shared draft. `onNewIntent` stays the only path for
     * intents that arrive while the activity is alive.
     */
    private fun handleExternalIntentIfFirstLaunch(intent: Intent, isFirstLaunch: Boolean) {
        if (isFirstLaunch) handleExternalIntent(intent)
    }

    /**
     * Holds [pending] when App Lock is showing and returns true; the
     * caller returns without executing (audit-200 D14, m-707). False
     * when the app is unlocked — the caller executes as before.
     */
    private fun holdIfLocked(pending: PendingExternalIntent): Boolean {
        if (!shouldHoldExternalIntent(AppLockPrefs.isEnabled(this), unlocked)) return false
        (application as OpenCodeApplication).holdExternalIntent(pending)
        return true
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
                    ACTION_NEW_SESSION -> {
                        if (holdIfLocked(PendingExternalIntent.NewSession)) return
                        app.requestNewSession()
                    }
                    ACTION_OPEN_SESSION -> {
                        val id = intent.getStringExtra(EXTRA_SESSION_ID)
                            ?.takeIf { it.isNotBlank() } ?: return
                        // Audit-200 D14 (m-707): a shortcut tap while
                        // locked waits for the post-unlock confirmation
                        // instead of opening the session silently.
                        if (holdIfLocked(PendingExternalIntent.OpenSession(id))) return
                        // Audit-200 D12 (m-377): an id that fails
                        // validation is no longer a silent drop — the
                        // shortcut pointed at a session id the app cannot
                        // open, so the tap says so. A full buffer
                        // (D13, m-532) needs no snackbar: the id is on
                        // disk and will open at attach.
                        if (app.deliverSessionOpen(id) == SessionOpenDelivery.INVALID) {
                            app.showSnackbar(localizedString(R.string.session_link_invalid))
                        }
                    }
                }
            }
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/") != true) return
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?.takeIf { it.isNotBlank() } ?: return
                // Audit-200 D14 (m-707): a share while locked waits for
                // the post-unlock confirmation instead of starting a new
                // session silently.
                if (holdIfLocked(PendingExternalIntent.SharedText(text))) return
                app.deliverSharedText(text)
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                if (uri.scheme != "opencode" || uri.host != "session") return
                // Exactly one path segment (53-F6): opencode://session/a/b
                // used to silently open "b", and a %2F-decoded slash in the
                // id reached the engine's API path unencoded. The charset
                // itself is checked in deliverSessionOpen.
                // Audit-200 D12 (m-377): the scheme and host matched, so
                // this IS a session link, just a broken one — a malformed
                // link used to die silently and the tap did nothing.
                if (uri.pathSegments.size != 1) {
                    app.showSnackbar(localizedString(R.string.session_link_invalid))
                    return
                }
                val sessionID = uri.lastPathSegment?.takeIf { it.isNotBlank() }
                if (sessionID == null) {
                    app.showSnackbar(localizedString(R.string.session_link_invalid))
                    return
                }
                // Audit-200 D14 (m-707): a deep link while locked waits
                // for the post-unlock confirmation instead of opening
                // the session silently.
                if (holdIfLocked(PendingExternalIntent.OpenSession(sessionID))) return
                if (app.deliverSessionOpen(sessionID) == SessionOpenDelivery.INVALID) {
                    app.showSnackbar(localizedString(R.string.session_link_invalid))
                }
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
    /**
     * Clears the pending photo trace once the chat screen finished copying
     * it into the workspace (audit-200 A6, DL-027). The acknowledgement
     * carries the URI it belongs to, so a stale one cannot clear a newer
     * photo's trace.
     */
    private fun observeCameraPhotoCopied() {
        val app = application as OpenCodeApplication
        lifecycleScope.launch {
            app.cameraPhotoCopied.collect { uri ->
                if (pendingPhotoStore.get() == uri) {
                    pendingPhotoStore.clear()
                    if (pendingPhotoUri == uri) pendingPhotoUri = null
                }
            }
        }
    }

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
                    // crash the app; the folder simply never opens — and
                    // the user is told so instead of a dead tap (15-F5).
                    runCatching { launchForResult { pickDirectory.launch(null) } }
                        .onFailure {
                            Log.w("MainActivity", "Directory picker launch failed", it)
                            app.showSnackbar(localizedString(R.string.directory_picker_failed))
                        }
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
                // never receives a file — and the user is told so instead
                // of a dead tap (15-F5).
                runCatching { launchForResult { pickSafFile.launch(arrayOf("*/*")) } }
                    .onFailure {
                        Log.w("MainActivity", "SAF picker launch failed", it)
                        app.showSnackbar(localizedString(R.string.saf_picker_failed))
                    }
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
                // nothing. Queued requests open one at a time — launching
                // while one is open would deliver the results out of
                // order (05-F2).
                if (codeSavePickerOpen) return@collect
                val pending = app.peekPendingCodeSave() ?: return@collect
                codeSavePickerOpen = true
                runCatching { launchForResult { saveCodeDocument.launch(pending.filename) } }
                    .onFailure {
                        codeSavePickerOpen = false
                        Log.w("MainActivity", "Code save picker launch failed", it)
                        app.showSnackbar(localizedString(R.string.code_save_picker_failed))
                    }
            }
        }
        lifecycleScope.launch {
            app.cameraPhotoRequest.collect {
                // Best-effort like the SAF launch above: a missing camera
                // app or a finishing activity must not crash the app; the
                // composer simply never receives a photo. Log-only here
                // on purpose (audit-200 D6, m-148): launchCamera() reports
                // its own failures with a snackbar, so a second one from
                // this collector would double-report.
                runCatching { launchCamera() }
                    .onFailure { Log.w("MainActivity", "Camera launch failed", it) }
            }
        }
    }

    /**
     * POST_NOTIFICATIONS on API 33+.
     *
     * Denial is not fatal: the foreground service still runs, its notification is
     * simply hidden. But the agent-done feature is worthless without it, and the
     * old code asked blindly on every launch with no explanation and no way back
     * (55-F9): after two denials the system stops showing the prompt at all, and
     * the user never learns why no notification ever arrives.
     *
     * First ask: the system prompt, directly. Denied once (rationale
     * available): a snackbar explains why, with an action to ask again.
     * Denied permanently (asked before, no rationale left): a snackbar
     * points at Settings. Each hint shows once, then the app stops
     * nagging — the permission can still be granted from system Settings.
     *
     * Audit-200 D8 (m-267): the first ask never fires before onboarding
     * is out of the way — see [mayAskNotificationPermission].
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val app = application as OpenCodeApplication
        // Audit-200 D8 (m-267): never ask before onboarding is out of
        // the way — the prompt used to stack on top of it with no
        // explanation. The next launch asks, once onboarding is done or
        // will never show.
        if (!mayAskNotificationPermission(OnboardingStore.isCompleted(this), OnboardingStore.shouldShow(this, app.workspaceStore))) return
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            if (!notificationRationaleShown()) {
                markNotificationRationaleShown()
                app.showSnackbarWithRetry(
                    lifecycleScope,
                    localizedString(R.string.notification_permission_rationale),
                    actionLabel = localizedString(R.string.notification_permission_allow),
                    long = true,
                    onAction = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }
            return
        }
        if (notificationPermissionAsked()) {
            if (!notificationSettingsHintShown()) {
                markNotificationSettingsHintShown()
                app.showSnackbarWithRetry(
                    lifecycleScope,
                    localizedString(R.string.notification_permission_settings),
                    actionLabel = localizedString(R.string.settings_title),
                    long = true,
                    onAction = { openAppNotificationSettings() },
                )
            }
            return
        }
        markNotificationPermissionAsked()
        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Opens this app's page in system Settings (55-F9). */
    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
    }

    private fun notificationPrefs() =
        getSharedPreferences("notification_permission", MODE_PRIVATE)

    private fun notificationPermissionAsked(): Boolean =
        notificationPrefs().getBoolean("asked", false)

    private fun markNotificationPermissionAsked() {
        notificationPrefs().edit().putBoolean("asked", true).apply()
    }

    private fun notificationRationaleShown(): Boolean =
        notificationPrefs().getBoolean("rationale_shown", false)

    private fun markNotificationRationaleShown() {
        notificationPrefs().edit().putBoolean("rationale_shown", true).apply()
    }

    private fun notificationSettingsHintShown(): Boolean =
        notificationPrefs().getBoolean("settings_hint_shown", false)

    private fun markNotificationSettingsHintShown() {
        notificationPrefs().edit().putBoolean("settings_hint_shown", true).apply()
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

/**
 * Audit-200 D8 (m-267): whether the notification permission may be asked
 * now. The prompt must not fire while onboarding is still to come — on a
 * fresh install it stacked on top of the onboarding flow with no
 * explanation. Onboarding is out of the way when it is completed
 * (skipping counts as completing), or when it will never show (a real
 * workspace from before onboarding existed). Pure so it is unit-testable;
 * the Activity path and the system launcher cannot be.
 */
internal fun mayAskNotificationPermission(onboardingCompleted: Boolean, onboardingWillShow: Boolean): Boolean =
    onboardingCompleted || !onboardingWillShow

/**
 * Audit-200 D14 (m-707): whether an external intent must be held for the
 * post-unlock confirmation instead of executing at once — only when App
 * Lock is enabled AND the lock screen has not been passed yet. Pure so
 * it is unit-testable; the Activity path cannot be.
 */
internal fun shouldHoldExternalIntent(lockEnabled: Boolean, unlocked: Boolean): Boolean =
    lockEnabled && !unlocked

/**
 * Audit-200 D9 (m-488): the stock `OpenDocument` contract's intent
 * carries no `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` — verified against
 * the androidx.activity 1.9.3 bytecode, which only sets the action, the
 * MIME-types extra and a wildcard type. Without the flag,
 * `takePersistableUriPermission` on the result throws
 * SecurityException, so the read grant died with the process. This
 * contract is the stock one plus the read + persistable flags; the
 * result parsing is the same (a URI on RESULT_OK, else null).
 */
internal class OpenDocumentPersistable : ActivityResultContract<Array<String>, Uri?>() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .putExtra(Intent.EXTRA_MIME_TYPES, input)
            .setType("*/*")
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
