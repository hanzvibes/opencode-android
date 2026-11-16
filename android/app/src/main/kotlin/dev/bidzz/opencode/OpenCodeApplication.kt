package dev.bidzz.opencode

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.engine.EngineBinary
import dev.bidzz.opencode.core.engine.EngineEnvironment
import dev.bidzz.opencode.core.engine.EngineStartupException
import dev.bidzz.opencode.core.engine.EngineStatus
import dev.bidzz.opencode.core.engine.ProcessManager
import dev.bidzz.opencode.core.engine.StartupTimings
import dev.bidzz.opencode.service.EngineService
import dev.bidzz.opencode.ui.chat.CodeSaveQueue
import dev.bidzz.opencode.ui.chat.CodeSaveRequest
import dev.bidzz.opencode.ui.chat.PendingOpenStore
import dev.bidzz.opencode.ui.chat.SharedPrefsCodeSaveQueueStore
import dev.bidzz.opencode.ui.chat.SharedPrefsPendingOpenStore
import dev.bidzz.opencode.ui.chat.sanitizeFallbackName
import dev.bidzz.opencode.workspace.WorkspaceStore
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Owns the process supervisor for the app's lifetime.
 *
 * The supervisor lives here rather than in the service so that the UI can read
 * engine state without binding, and so that state survives the service being
 * torn down and recreated. [EngineService] is what keeps the process alive
 * across a task swipe; this class only holds the handle.
 */
/**
 * What offering a deep-link id to the app came to (audit-200 D13,
 * m-525/m-532): the old `Boolean` could not tell "not a session id"
 * from "the in-memory flow was full", so a valid link dropped by a
 * full buffer got the same "not valid" snackbar as a broken one.
 */
enum class SessionOpenDelivery {
    /** The id reached the collector (and the disk queue) and will open. */
    DELIVERED,
    /** The id is not shaped like a session id — rejected, never queued. */
    INVALID,
    /**
     * The in-memory flow was full, but the id is safely on disk and
     * will open at attach — no snackbar: there is nothing wrong to
     * report, the link is queued, not lost.
     */
    BUFFER_FULL,
}

/**
 * An external intent that arrived while App Lock was showing (audit-200
 * D14, m-707): held for one confirmation dialog after unlock instead of
 * executing silently behind the lock. One value per intent kind — the
 * dialog names what will happen, and the confirm path re-runs the same
 * delivery the intent would have taken unlocked.
 */
sealed interface PendingExternalIntent {
    /** An opencode://session/{id} deep link, widget tap or shortcut. */
    data class OpenSession(val sessionID: String) : PendingExternalIntent

    /** Text shared from another app (ACTION_SEND). */
    data class SharedText(val text: String) : PendingExternalIntent

    /** A fresh session from the widget or launcher shortcut. */
    data object NewSession : PendingExternalIntent
}

class OpenCodeApplication : Application() {
    companion object {
        private const val TAG = "OpenCodeApp"
        /**
         * Delay before a dropped priority snackbar is retried once
         * (audit-200 D6, m-593): a Short snackbar shows ~4s, so 6s gives
         * the drain loop room to free a buffer slot.
         */
        private const val SNACKBAR_RETRY_DELAY_MS = 6_000L
        /**
         * Pending SAF picks the chat can hold (audit-200 D7, m-1164): the
         * reader is serial, so a slow provider leaves only the buffer for
         * the next picks — 8 was too small for a burst, and the 9th pick
         * vanished. Same size as the snackbar queue (D6 m-593).
         */
        internal const val SAF_PICK_BUFFER_CAPACITY = 16
        /**
         * Photos the chat can hold (audit-200 D9, m-488): same size as
         * the SAF picks above — the camera flow used to be a buffer-1
         * SharedFlow whose tryEmit silently dropped the third photo of
         * a burst while the first was still being copied.
         */
        internal const val CAMERA_PHOTO_BUFFER_CAPACITY = 16
        /** SharedPreferences file the app restores from (also read by the widget). */
        const val PREFS_NAME = "app"
        /** Last opened session id (also read by the widget). */
        const val KEY_LAST_SESSION = "last_session_id"
        /** Session with a run in flight when the process died (audit-200 A9-3, DL-029). */
        const val KEY_LAST_BUSY_SESSION = "last_busy_session_id"
    }

    /**
     * The app language (92-F2): the whole process reads resources in the
     * language the user picked — the service's notifications, the widget,
     * and shortcuts all derive their context from here, so they no longer
     * fall back to the phone's locale while the UI speaks Indonesian.
     * The wrap below runs once per process, so it only covers cold starts
     * and receivers that hold no fresher context. A language change while
     * the app is open reaches the live surfaces through fresh wraps
     * instead: the Compose tree re-wraps on change and updates at once,
     * notifications wrap the stored language fresh on every post (D1
     * m-530), the widget and shortcut re-push from the wrapped `localized`
     * context (D7 m-860), and the activity's snackbars read fresh on every
     * call (D7 m-860) — so nothing waits for the next process start.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageStore.wrap(base, LanguageStore.get(base)))
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
     * The engine's directory layout. Exposed because the config file the app writes
     * lives inside it, and duplicating those paths here would let the two drift.
     */
    lateinit var engineEnvironment: EngineEnvironment
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
     * Persisted in `SharedPreferences` (item 7) so a force-stop or process
     * death restores the conversation, not just the draft. A stale id is
     * harmless: [ChatViewModel.onResume] verifies the session belongs to
     * the active workspace before opening it, and skips one the engine no
     * longer has — so a reboot never resurrects a dead session.
     */
    var lastSessionID: String?
        get() = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_LAST_SESSION, null)
        set(value) {
            // 69-F10: commit(), not apply() — the id must be on disk before
            // this returns. A force-stop in the window between a session
            // switch and the async flush used to lose the remembered
            // session. One small string write; the setter already runs on
            // the main thread.
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().apply {
                if (value == null) remove(KEY_LAST_SESSION) else putString(KEY_LAST_SESSION, value)
            }.commit()
        }

    /**
     * The session with a run in flight, if any (audit-200 A9-3, DL-029).
     * The chat view model reports it on every busy change; the value is
     * still set when the process dies mid-run, so the next engine start
     * knows an orphan may still be working and must not be reaped. The
     * start clears it once the new engine is up (one-shot: the orphan's
     * run is preserved across one restart, not forever). Same commit()
     * rule as [lastSessionID]: the flag must be on disk before the run
     * can outlive the process.
     */
    var lastBusySessionID: String?
        get() = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_LAST_BUSY_SESSION, null)
        set(value) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().apply {
                if (value == null) remove(KEY_LAST_BUSY_SESSION) else putString(KEY_LAST_BUSY_SESSION, value)
            }.commit()
        }

    /** Fallback status published when the binary never resolved. */
    private val unavailableStatus = MutableStateFlow(EngineStatus())

    /**
     * Single source of truth for engine state, read by both the service and the
     * UI. Never null: a broken install reports STOPPED with the reason attached
     * (audit-200 C10, M-156) instead of forcing every caller to handle absence.
     */
    val engineStatus: StateFlow<EngineStatus>
        get() = processManager?.status ?: unavailableStatus

    /** Runtime archives are copied on IO, but the service must await them before spawning the engine. */
    private val runtimeAssetSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtimeAssetsReady = CompletableDeferred<Unit>()

    suspend fun awaitRuntimeAssetsReady() {
        runtimeAssetsReady.await()
    }

    override fun onCreate() {
        super.onCreate()
        // Audit-200 C13 (M-179): record uncaught exceptions before any
        // other work, so an early crash is captured too. The handler
        // chains to the previous one — the process still dies normally.
        @Suppress("DEPRECATION")
        val version = runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        }.getOrDefault("unknown")
        CrashLog.install(filesDir, version)
        registerActivityLifecycleCallbacks(ForegroundTracker)

        val nativeLibraryDir = File(applicationInfo.nativeLibraryDir)
        workspaceStore = WorkspaceStore(this)
        engineApi = dev.bidzz.opencode.api.EngineApi { workspaceStore.resolve().absolutePath }

        // The environment reads the workspace through this lambda on every access,
        // so changing the project does not require a new supervisor or a new
        // engine home. The engine's own state must not live inside the project.
        engineEnvironment = EngineEnvironment(filesDir, cacheDir) { workspaceStore.resolve() }
        val environment = engineEnvironment

        try {
            val binary = EngineBinary.resolve(nativeLibraryDir, filesDir)
            processManager = ProcessManager(binary, environment)
            // Asset extraction is disk-heavy on a fresh install. Schedule it
            // off the main thread so the first activity can draw; EngineService
            // awaits completion before every engine start/restart.
            syncRuntimeAssetsInBackground()
            Log.i(TAG, "engine resolved at ${binary.executable.absolutePath}")
            Log.i(TAG, "workspace: ${environment.workspace.absolutePath}")
        } catch (e: Exception) {
            // Keep the app usable so a diagnostics screen can explain the problem
            // instead of the process dying on launch. This catches
            // EngineStartupException and anything else resolve() can throw
            // (item 1): a disk error staging git/rg used to escape as a bare
            // IOException and crash the process on every launch.
            // Audit-200 D14 (m-419): the screen, the clipboard and the chat
            // banner show only a generic failure — the exception's own
            // words can carry a path (and, on other paths, a password),
            // and they are English either way. The raw message stays in
            // logcat only.
            val genericFailure = appLanguageContext(this).getString(R.string.engine_notif_failed_title)
            startupFailure = genericFailure
            unavailableStatus.value = EngineStatus(lastError = genericFailure)
            Log.e(TAG, "engine unavailable: ${e.message}")
        }

        // Seed the agent's runtime guide into the workspace (Opsi A): the
        // engine reads AGENTS.md from the project directory, and the file is
        // how the agent learns the bundled runtime. Runs even when the engine
        // failed to resolve — the file is ready for the next successful start.
        // Non-fatal by design, same as NodeAssets above.
        runCatching { WorkspaceAgentsMd.ensure(assets, workspaceStore.resolve()) }
    }

    private fun syncRuntimeAssetsInBackground() {
        runtimeAssetSyncScope.launch {
            // Audit-200 D5 (m-875): cold-start timing for the bundled
            // runtime staging (Node / Python / LSP), which runs on
            // Dispatchers.IO while the engine starts.
            StartupTimings.mark("assetsSyncStart")
            try {
                // Keep the synchronizers' version-marker behavior unchanged:
                // full copies happen only on install or runtime-version bumps.
                runCatching { NodeAssets.sync(assets, filesDir) }
                    .onFailure { Log.w(TAG, "node assets sync failed: ${it.message}") }
                StartupTimings.mark("nodeAssetsSynced")
                runCatching { PythonAssets.sync(assets, filesDir) }
                    .onFailure { Log.w(TAG, "python assets sync failed: ${it.message}") }
                StartupTimings.mark("pythonAssetsSynced")
                runCatching { LspAssets.sync(assets, filesDir) }
                    .onFailure { Log.w(TAG, "lsp assets sync failed: ${it.message}") }
                StartupTimings.mark("lspAssetsSynced")
            } finally {
                // Asset sync is non-fatal, as before; allow the engine to
                // start with whatever bundled runtime files are available.
                StartupTimings.mark("assetsSyncDone")
                runtimeAssetsReady.complete(Unit)
            }
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
     * Points the engine at a different project directory and restarts it.
     *
     * The engine takes its working directory at process start, so storing the new
     * path is not enough: without the restart the agent keeps editing the previous
     * project, which is the worst kind of bug because nothing looks wrong.
     *
     * The restart goes through the foreground service, so the notification stays
     * up and the process is never left without a supervisor.
     */
    fun switchWorkspace(dir: File): Boolean {
        if (!workspaceStore.isAcceptableWorkspace(dir)) {
            Log.w(TAG, "refusing workspace: ${dir.absolutePath}")
            return false
        }
        // Audit-200 D1 (m-311): compare canonical paths, not raw strings —
        // an alias spelling of the same directory used to slip past this
        // guard and trigger a pointless engine restart.
        val previous = workspaceStore.selectedPath
        val samePath = previous != null && runCatching {
            File(previous).canonicalPath == dir.canonicalPath
        }.getOrDefault(previous == dir.absolutePath)
        if (samePath) {
            Log.i(TAG, "workspace already ${dir.absolutePath}")
            return true
        }
        workspaceStore.selectedPath = dir.absolutePath
        // The new project gets the runtime guide too, before the engine
        // restarts into it — otherwise only the first workspace ever has one.
        runCatching { WorkspaceAgentsMd.ensure(assets, dir) }
        Log.i(TAG, "workspace switched to ${dir.absolutePath}, restarting engine")
        sendToService(EngineService.ACTION_RESTART)
        return true
    }

    /**
     * Restarts the engine so it re-reads its own config.
     *
     * Provider entries are resolved at startup, so writing the config file does
     * nothing until the process comes back — the same class of trap as a workspace
     * change that skips its restart.
     *
     * Returns false when the restart never started (audit-200 B4, M-125):
     * the supervisor was never initialised, so the service intent goes
     * nowhere. Callers that report the restart's outcome need the result
     * instead of assuming the engine is coming back.
     */
    fun restartEngine(): Boolean {
        Log.i(TAG, "restarting engine for config change")
        return sendToService(EngineService.ACTION_RESTART)
    }

    /** Opens the system folder picker. The result arrives in the activity. */
    fun requestWorkspaceFolder() {
        workspaceFolderRequest.tryEmit(System.currentTimeMillis())
    }

    /** Opens the all-files-access settings screen. */
    fun requestAllFilesAccess() {
        allFilesAccessRequest.tryEmit(System.currentTimeMillis())
    }

    /**
     * Signals that the UI wants a system dialog.
     *
     * One-shot events, not state: a StateFlow replays its last value to
     * every new collector, so a request fired once re-opened its dialog on
     * every later MainActivity recreation — a cold start with a live process
     * popped the file picker instead of the app UI. A SharedFlow with no
     * replay delivers each tap once; the timestamp value still lets repeated
     * taps re-trigger.
     */
    val workspaceFolderRequest = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 1)
    val allFilesAccessRequest = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 1)

    /**
     * Opens the system file picker for a file outside the workspace (SAF). The result arrives in the activity.
     *
     * 14-F5: the request flow holds 8 pending taps, and a tap that still
     * cannot be queued tells the user instead of vanishing — the old
     * buffer-1 `tryEmit` dropped the third pick of a burst silently.
     */
    fun requestSafFile() {
        if (!safFileRequest.tryEmit(System.currentTimeMillis())) {
            // Audit-200 D14 (m-501): the stored language, read fresh — the
            // Application's base context was wrapped once per process, so
            // a language change used to reach this snackbar only after a
            // restart.
            showSnackbar(appLanguageContext(this).getString(R.string.saf_picker_failed))
        }
    }

    /**
     * Delivers the URI picked in the system file picker.
     *
     * Audit-200 D7 (m-1164): the picks now flow through a Channel, not a
     * SharedFlow with replay 0 — the SharedFlow forgot offers with no
     * collector while tryEmit still reported success (the same trap D6
     * m-593 found in the snackbar queue), so a pick could vanish before
     * the reader subscribed. A pick that still cannot be queued names
     * the file it lost, and the message itself is retried once — the
     * generic message used to drop silently too when the snackbar queue
     * was full.
     */
    fun deliverSafFile(uri: Uri) {
        if (!safFilePickedChannel.trySend(uri).isSuccess) {
            val name = safDisplayName(uri)
            showSnackbarWithRetry(
                applicationScope,
                appLanguageContext(this).getString(R.string.saf_file_dropped_named, name),
                long = true,
            )
        }
    }

    /**
     * The picked file's display name for the drop message: the provider's
     * DISPLAY_NAME when it answers, else the sanitized last path segment
     * (the same fallback [readSafFile] uses) — never a raw opaque id.
     */
    private fun safDisplayName(uri: Uri): String {
        val fromProvider: String? = try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
        return fromProvider
            ?: uri.lastPathSegment?.let { sanitizeFallbackName(it) }
            ?: "file"
    }

    /** Process-lifetime scope for fire-and-forget work that outlives any screen (audit-200 D7, m-1164). */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val safFileRequest = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 8)

    private val safFilePickedChannel = Channel<Uri>(capacity = SAF_PICK_BUFFER_CAPACITY)

    /** URIs picked in the system file picker; the chat view model reads them in as text. */
    val safFilePicked: Flow<Uri> = safFilePickedChannel.receiveAsFlow()

    /**
     * Voice input (Composer C): asks MainActivity to request the
     * microphone permission, and the granted/denied answer for it. Same
     * bridge shape as the SAF attach flow above: the composable only
     * names the request, MainActivity owns the permission launcher (a
     * Compose launcher in chat crashed for want of an
     * ActivityResultRegistryOwner). One-shot events, not state, for the
     * same reason as the flows above — a replayed request would re-open
     * the permission dialog on every MainActivity recreation.
     */
    fun requestVoicePermission() {
        voicePermissionRequest.tryEmit(System.currentTimeMillis())
    }

    /** Delivers the microphone permission answer from MainActivity. */
    fun deliverVoicePermissionResult(granted: Boolean) {
        voicePermissionResult.tryEmit(granted)
    }

    val voicePermissionRequest = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 1)
    val voicePermissionResult = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    /**
     * A code block the user asked to save through the system picker (SAF).
     * Same bridge shape as the SAF attach flow above: the composable only
     * names the file and its text, MainActivity owns the `CreateDocument`
     * launcher (a Compose launcher in chat crashed for want of an
     * ActivityResultRegistryOwner) and the write.
     *
     * The pending requests live in a persisted queue ([CodeSaveQueue]),
     * not a single in-memory slot (05-F2): a second Save used to
     * overwrite the first without feedback, and a process death while
     * the picker was open meant nothing was ever written. The queue is
     * restored on first use, so the picker callback that arrives after
     * a recreation still finds the request it belongs to.
     */
    private val codeSaveQueue by lazy {
        CodeSaveQueue(SharedPrefsCodeSaveQueueStore(this))
    }

    /** Asks MainActivity to open the system save picker for a code block. */
    fun requestCodeSave(filename: String, text: String) {
        codeSaveQueue.enqueue(CodeSaveRequest(filename, text))
        codeSaveRequest.tryEmit(System.currentTimeMillis())
    }

    /**
     * Re-queues a code save whose write just failed (audit-200 A6, DL-024,
     * DL-025): the request survives the snackbar — it is persisted again
     * at once, so a dismissed snackbar, a rotation or a process death no
     * longer loses it — and it goes ahead of every request whose picker
     * has not opened yet. When no picker is open it goes first; while one
     * is open it goes right behind the head, so that picker's result still
     * takes the request it was opened for. The snackbar's Retry only
     * re-opens the picker; it must not enqueue again.
     */
    fun requeueFailedCodeSave(filename: String, text: String, pickerOpen: Boolean) {
        val request = CodeSaveRequest(filename, text)
        if (pickerOpen) codeSaveQueue.enqueueAfterHead(request) else codeSaveQueue.enqueueFirst(request)
    }

    /** Takes the oldest pending code-save request, if any. */
    fun takePendingCodeSave(): CodeSaveRequest? = codeSaveQueue.take()

    /** Peeks at the oldest pending request without consuming it (for the launch). */
    fun peekPendingCodeSave(): CodeSaveRequest? = codeSaveQueue.peek()

    /**
     * Re-emits the save-picker request when requests are still queued —
     * called by MainActivity after a picker returns, so the next Save in
     * line opens its picker instead of waiting for another tap.
     */
    fun emitCodeSaveRequestIfPending() {
        if (!codeSaveQueue.isEmpty()) codeSaveRequest.tryEmit(System.currentTimeMillis())
    }

    val codeSaveRequest = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 1)

    /**
     * Text shared into the app from another app (ACTION_SEND, Native C).
     * Same bridge shape as the SAF attach flow above: the activity owns
     * the intent, the UI owns what happens with the text. One-shot, so a
     * recreated activity never re-shares the same text.
     */
    fun deliverSharedText(text: String) {
        sharedTextReceived.tryEmit(text)
    }

    /** Text shared from another app; the chat screen drafts it. */
    val sharedTextReceived = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /**
     * Session id from an opencode://session/{id} deep link (Native C).
     * Same bridge shape as the share above. An id that fails
     * [isValidSessionId] is rejected, not queued: it came from outside
     * the app and never reaches the engine (Fase 5, 53-F6). The caller
     * tells the user the link is not valid (audit-200 D12, m-377) —
     * a rejected link used to die silently and the tap did nothing.
     *
     * Audit-200 D13 (m-525/m-948): the id is persisted to the pending
     * store BEFORE emitting it. The flow is in-memory (replay 0), so a
     * process death between here and the first collector used to lose
     * the link silently — the store is the second copy, and the
     * collector's openSession dedups against it (D12 m-380), so the
     * link opens exactly once. A full buffer no longer loses the link
     * either: it stays on disk and opens at attach (BUFFER_FULL).
     *
     * The agent-done notification is cancelled when the session actually
     * opens (OpenCodeApp, 55-F7), not here — cancelling it before the
     * id was even verified deleted the notification for links that
     * were then refused or deferred (audit-200 D12, m-382).
     */
    fun deliverSessionOpen(sessionID: String): SessionOpenDelivery {
        if (!isValidSessionId(sessionID)) return SessionOpenDelivery.INVALID
        pendingOpenStore.addPendingOpen(sessionID)
        val delivered = sessionOpenRequested.tryEmit(sessionID)
        if (!delivered) {
            Log.w(TAG, "session open request dropped (buffer full, kept on disk): $sessionID")
        }
        return if (delivered) SessionOpenDelivery.DELIVERED else SessionOpenDelivery.BUFFER_FULL
    }

    /** Session ids from deep links; the chat screen opens them. */
    val sessionOpenRequested = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** The disk half of the pending-open queue (audit-200 D13, m-525). */
    private val pendingOpenStore: PendingOpenStore by lazy { SharedPrefsPendingOpenStore(this) }

    /**
     * Whether MainActivity has run onCreate once in this process
     * (audit-200 D13, m-525/m-948, 125-F3): a process death hands the
     * launch intent back with a non-null savedInstanceState, so the old
     * "first launch only" gate threw a deep link away on a restore from
     * recents. The first onCreate in a process is still a first launch
     * even with saved state; a config-change recreate is not.
     */
    internal var activityCreatedOnce: Boolean = false

    /**
     * A launcher shortcut asked for a fresh session (Native C). Same
     * bridge shape as the share above.
     */
    fun requestNewSession() {
        newSessionRequested.tryEmit(System.currentTimeMillis())
    }

    /** Fresh-session requests from launcher shortcuts. */
    val newSessionRequested = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 1)

    /**
     * An external intent that arrived while App Lock was showing
     * (audit-200 D14, m-707): held for one confirmation dialog after
     * unlock instead of executing silently behind the lock. In-memory
     * only — killing the app while locked drops it, and the user just
     * taps the link again. Only the latest held intent is kept; the
     * share/new-session flows are buffer-1 too, so a second intent
     * while locked was already lost before this hold existed.
     */
    val pendingExternalIntent = MutableStateFlow<PendingExternalIntent?>(null)

    /** Holds [pending] for the post-unlock confirmation dialog. */
    fun holdExternalIntent(pending: PendingExternalIntent) {
        pendingExternalIntent.value = pending
    }

    /**
     * Runs a held external intent after the user confirms it (D14,
     * m-707): the same deliveries the intent would have taken unlocked.
     * Clears the hold first, so a recomposition can never run it twice.
     */
    fun executePendingExternalIntent(pending: PendingExternalIntent) {
        pendingExternalIntent.value = null
        when (pending) {
            is PendingExternalIntent.OpenSession -> {
                if (deliverSessionOpen(pending.sessionID) == SessionOpenDelivery.INVALID) {
                    showSnackbar(appLanguageContext(this).getString(R.string.session_link_invalid))
                }
            }
            is PendingExternalIntent.SharedText -> deliverSharedText(pending.text)
            PendingExternalIntent.NewSession -> requestNewSession()
        }
    }

    /**
     * Camera photo (Native C): asks MainActivity to open the system
     * camera, and the URI of the photo it took. Same bridge shape as
     * the SAF attach flow above.
     *
     * Audit-200 D9 (m-488): both flows used to be buffer-1 SharedFlows
     * whose tryEmit was never checked — a photo taken while the previous
     * one was still being copied vanished without a word. They are
     * Channels now, like the SAF picks (D7 m-1164), and a photo that
     * still cannot be queued names the file it lost.
     */
    fun requestCameraPhoto() {
        if (!cameraPhotoRequestChannel.trySend(System.currentTimeMillis()).isSuccess) {
            // Audit-200 D14 (m-501): same fresh wrap as the SAF sites above.
            showSnackbar(appLanguageContext(this).getString(R.string.camera_open_failed))
        }
    }

    /** Delivers the URI of a photo just taken. */
    fun deliverCameraPhoto(uri: Uri) {
        if (!cameraPhotoPickedChannel.trySend(uri).isSuccess) {
            val name = safDisplayName(uri)
            showSnackbarWithRetry(
                applicationScope,
                appLanguageContext(this).getString(R.string.camera_photo_dropped_named, name),
                long = true,
            )
        }
    }

    /**
     * Confirms a photo was copied into the workspace (audit-200 A6,
     * DL-027): MainActivity clears the pending trace on this, not in the
     * camera callback — the trace must survive until the copy completes.
     */
    fun acknowledgeCameraPhoto(uri: Uri) {
        cameraPhotoCopied.tryEmit(uri)
    }

    private val cameraPhotoRequestChannel = Channel<Long>(capacity = CAMERA_PHOTO_BUFFER_CAPACITY)
    val cameraPhotoRequest: Flow<Long> = cameraPhotoRequestChannel.receiveAsFlow()
    private val cameraPhotoPickedChannel = Channel<Uri>(capacity = CAMERA_PHOTO_BUFFER_CAPACITY)
    /** URIs of photos just taken; the chat screen copies them in. */
    val cameraPhotoPicked: Flow<Uri> = cameraPhotoPickedChannel.receiveAsFlow()
    /** URIs the chat screen finished copying (success or failure). */
    val cameraPhotoCopied = MutableSharedFlow<Uri>(extraBufferCapacity = 1)

    /**
     * In-app snackbar requests (Opsi B #4d): the one replacement for
     * `Toast.makeText` across the app. Same bridge shape as the flows
     * above — a SharedFlow with no replay, so each request shows once.
     * The request carries the already-resolved string (like Toast did:
     * `context.getString` / `stringResource` at the call site), so no
     * @StringRes plumbing is needed. The queue itself lives in
     * [SnackbarQueue] so the buffer contract is unit-testable.
     *
     * Which call sites check the return (audit-200 D6, m-593): a dropped
     * "Copied" is harmless, so the 30+ plain-feedback sites keep the bare
     * call and ignore the Boolean. A dropped failure or action-bearing
     * message loses the user's only path — the notification-permission
     * hints show once, the restart failure has no other surface — so
     * those sites use [showSnackbarWithRetry], which retries once after
     * a short delay. A full buffer holds 16 requests for 64–160s of
     * queue, so a retry after 6s usually finds room.
     */
    internal val snackbarQueue = SnackbarQueue()

    /**
     * Asks the UI to show a snackbar. Returns false when the request was
     * dropped (65-F8): the buffer is full, and the drop is logged so a
     * lost error message leaves a trace instead of silence.
     */
    fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        long: Boolean = false,
        onAction: (() -> Unit)? = null,
    ): Boolean {
        val delivered = snackbarQueue.tryOffer(SnackbarQueue.Request(message, actionLabel, long, onAction))
        if (!delivered) {
            Log.w(TAG, "snackbar request dropped (buffer full): $message")
        }
        return delivered
    }

    /**
     * Shows a snackbar, retrying once after a short delay when the buffer
     * was full (audit-200 D6, m-593). Only for failure and action-bearing
     * requests — see [snackbarQueue]. The retry re-uses the same request;
     * if the second attempt also drops, [showSnackbar] logs it as usual.
     */
    fun showSnackbarWithRetry(
        scope: CoroutineScope,
        message: String,
        actionLabel: String? = null,
        long: Boolean = false,
        onAction: (() -> Unit)? = null,
    ) {
        if (!showSnackbar(message, actionLabel, long, onAction)) {
            scope.launch {
                delay(SNACKBAR_RETRY_DELAY_MS)
                showSnackbar(message, actionLabel, long, onAction)
            }
        }
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

    /**
     * Whether any activity is currently started (Fase 5, 55-F6).
     *
     * The agent-done watcher in [EngineService] reads this together with
     * [isLockScreenShowing] and [visibleChatSessionID] (audit-200 D14,
     * m-531/m-789/m-979): a run that ends while the user is watching that
     * session's chat needs no notification; any other foreground case
     * does. A counter, not a boolean: two activities can overlap during
     * a transition, and a single flag would flicker false between onStop
     * and onStart.
     */
    val isAppForeground: Boolean get() = ForegroundTracker.startedCount > 0

    /**
     * Whether the App Lock screen is currently showing (audit-200 D14,
     * m-531/m-789): written by MainActivity's lock gate, read by the
     * service watcher — a run that ends while the lock is up DOES get its
     * agent-done notification, it is not swallowed.
     */
    @Volatile
    var isLockScreenShowing: Boolean = false

    /**
     * The session whose chat the user is currently looking at (audit-200
     * D14, m-531/m-789/m-979): written from OpenCodeApp's composition,
     * read by the service watcher — the agent-done notification is
     * suppressed only while this is the session that just finished.
     * Null on any other screen, while the lock is showing, or when the
     * app is backgrounded (the last value then goes stale, so the
     * watcher also requires the app to be in the foreground).
     */
    @Volatile
    var visibleChatSessionID: String? = null
}

/**
 * Counts started activities for [OpenCodeApplication.isAppForeground].
 * A plain object on purpose: the service reads the same flag from the
 * application instance, and the count must not depend on which activity
 * happens to be alive.
 */
private object ForegroundTracker : Application.ActivityLifecycleCallbacks {
    @Volatile var startedCount: Int = 0
        private set

    override fun onActivityStarted(activity: android.app.Activity) {
        startedCount++
    }

    override fun onActivityStopped(activity: android.app.Activity) {
        startedCount = (startedCount - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
    override fun onActivityResumed(activity: android.app.Activity) = Unit
    override fun onActivityPaused(activity: android.app.Activity) = Unit
    override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
    override fun onActivityDestroyed(activity: android.app.Activity) = Unit
}

/**
 * Whether a deep-link session id is shaped like a real session id
 * (Fase 5, 53-F6). Session ids are single path segments of letters,
 * digits, `_`, `-` and `.` (UUIDs and the engine's own ids); anything
 * else — a `/` smuggled in through `%2F`, `..`, `?`, `#`, a blank —
 * is rejected before it reaches the engine. `Uri.getLastPathSegment`
 * returns the segment percent-decoded, so the charset check is what
 * catches an encoded slash, not the segment count.
 */
internal fun isValidSessionId(id: String): Boolean {
    if (id.isEmpty()) return false
    // Audit-200 D13 (m-780): engine ids are ~26 chars (ses_*) and UUIDs
    // are 36 — a valid-looking id of tens of kilobytes used to pass the
    // charset check, get persisted to disk (D12 m-379) and build a giant
    // GET path, then stall the pending queue when the GET failed. 128
    // leaves ample room for any real id.
    if (id.length > 128) return false
    if (!id.first().isLetterOrDigit()) return false
    return id.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }
}
