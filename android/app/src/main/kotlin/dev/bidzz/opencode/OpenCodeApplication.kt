package dev.bidzz.opencode

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.engine.EngineBinary
import dev.bidzz.opencode.core.engine.EngineEnvironment
import dev.bidzz.opencode.core.engine.EngineStartupException
import dev.bidzz.opencode.core.engine.EngineStatus
import dev.bidzz.opencode.core.engine.ProcessManager
import dev.bidzz.opencode.service.EngineService
import dev.bidzz.opencode.ui.cancelAgentDone
import dev.bidzz.opencode.ui.chat.CodeSaveQueue
import dev.bidzz.opencode.ui.chat.CodeSaveRequest
import dev.bidzz.opencode.ui.chat.SharedPrefsCodeSaveQueueStore
import dev.bidzz.opencode.workspace.WorkspaceStore
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
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
        /** SharedPreferences file the app restores from (also read by the widget). */
        const val PREFS_NAME = "app"
        /** Last opened session id (also read by the widget). */
        const val KEY_LAST_SESSION = "last_session_id"
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
            // Node's npm tree lives in assets/ and must be extracted before
            // the engine first spawns a child that calls npm. Non-fatal by
            // design: a build without the staging step still runs the engine
            // and the node binary, just without npm (NodeAssets logs it).
            runCatching { NodeAssets.sync(assets, filesDir) }
                .onFailure { Log.w(TAG, "node assets sync failed: ${it.message}") }
            // Same pattern for the Python stdlib tree (PYTHONHOME) and the
            // bundled language servers: extracted before the engine first
            // spawns a child that calls python3 or an LSP server. Non-fatal
            // by design, like NodeAssets above.
            runCatching { PythonAssets.sync(assets, filesDir) }
                .onFailure { Log.w(TAG, "python assets sync failed: ${it.message}") }
            runCatching { LspAssets.sync(assets, filesDir) }
                .onFailure { Log.w(TAG, "lsp assets sync failed: ${it.message}") }
            processManager = ProcessManager(binary, environment)
            Log.i(TAG, "engine resolved at ${binary.executable.absolutePath}")
            Log.i(TAG, "workspace: ${environment.workspace.absolutePath}")
        } catch (e: Exception) {
            // Keep the app usable so a diagnostics screen can explain the problem
            // instead of the process dying on launch. This catches
            // EngineStartupException and anything else resolve() can throw
            // (item 1): a disk error staging git/rg used to escape as a bare
            // IOException and crash the process on every launch.
            startupFailure = e.message
            unavailableStatus.value = EngineStatus(lastError = e.message)
            Log.e(TAG, "engine unavailable: ${e.message}")
        }

        // Seed the agent's runtime guide into the workspace (Opsi A): the
        // engine reads AGENTS.md from the project directory, and the file is
        // how the agent learns the bundled runtime. Runs even when the engine
        // failed to resolve — the file is ready for the next successful start.
        // Non-fatal by design, same as NodeAssets above.
        runCatching { WorkspaceAgentsMd.ensure(assets, workspaceStore.resolve()) }
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
        val previous = workspaceStore.selectedPath
        if (previous == dir.absolutePath) {
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
     */
    fun restartEngine() {
        Log.i(TAG, "restarting engine for config change")
        sendToService(EngineService.ACTION_RESTART)
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
            showSnackbar(getString(R.string.saf_picker_failed))
        }
    }

    /**
     * Delivers the URI picked in the system file picker.
     *
     * 14-F5: the picked flow holds 8 pending URIs (the reader is serial,
     * so a slow provider leaves only the buffer for the next picks), and a
     * pick that still cannot be queued tells the user instead of vanishing.
     */
    fun deliverSafFile(uri: Uri) {
        if (!safFilePicked.tryEmit(uri)) {
            showSnackbar(getString(R.string.saf_file_dropped))
        }
    }

    val safFileRequest = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 8)
    /** URIs picked in the system file picker; the chat view model reads them in as text. */
    val safFilePicked = MutableSharedFlow<Uri>(extraBufferCapacity = 8)

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
     * [isValidSessionId] is dropped silently: it came from outside the
     * app and never reaches the engine (Fase 5, 53-F6).
     *
     * Returns false when the id could not be queued (56-F4): the old
     * code ignored the `tryEmit` result, so a second deep link arriving
     * before the first was consumed vanished without a trace. Opening
     * the session also cancels its agent-done notification (55-F7) —
     * the user is looking at the answer now.
     */
    fun deliverSessionOpen(sessionID: String): Boolean {
        if (!isValidSessionId(sessionID)) return false
        cancelAgentDone(this, sessionID)
        val delivered = sessionOpenRequested.tryEmit(sessionID)
        if (!delivered) {
            Log.w(TAG, "session open request dropped (buffer full): $sessionID")
        }
        return delivered
    }

    /** Session ids from deep links; the chat screen opens them. */
    val sessionOpenRequested = MutableSharedFlow<String>(extraBufferCapacity = 16)

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
     * Camera photo (Native C): asks MainActivity to open the system
     * camera, and the URI of the photo it took. Same bridge shape as
     * the SAF attach flow above.
     */
    fun requestCameraPhoto() {
        cameraPhotoRequest.tryEmit(System.currentTimeMillis())
    }

    /** Delivers the URI of a photo just taken. */
    fun deliverCameraPhoto(uri: Uri) {
        cameraPhotoPicked.tryEmit(uri)
    }

    val cameraPhotoRequest = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 1)
    /** URIs of photos just taken; the chat screen copies them in. */
    val cameraPhotoPicked = MutableSharedFlow<Uri>(extraBufferCapacity = 1)

    /**
     * In-app snackbar requests (Opsi B #4d): the one replacement for
     * `Toast.makeText` across the app. Same bridge shape as the flows
     * above — a SharedFlow with no replay, so each request shows once.
     * The request carries the already-resolved string (like Toast did:
     * `context.getString` / `stringResource` at the call site), so no
     * @StringRes plumbing is needed.
     *
     * The buffer holds up to 16 pending requests: while one snackbar is
     * on screen the collector is suspended in `showSnackbar`, and a
     * burst (two quick copies, say) must not silently drop the second
     * one the way a capacity-1 buffer would. 16, not 8 (65-F8): a burst
     * of 9 feedbacks inside ~30s was enough to overflow the old buffer,
     * and the drop was silent. A drop past 16 is still possible — it is
     * now logged instead of vanishing (see [showSnackbar]).
     */
    data class SnackbarRequest(
        val message: String,
        val actionLabel: String? = null,
        val long: Boolean = false,
        val onAction: (() -> Unit)? = null,
    )

    /** Snackbar requests; the root composable shows them one at a time. */
    val snackbarRequests = MutableSharedFlow<SnackbarRequest>(replay = 0, extraBufferCapacity = 16)

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
        val delivered = snackbarRequests.tryEmit(SnackbarRequest(message, actionLabel, long, onAction))
        if (!delivered) {
            Log.w(TAG, "snackbar request dropped (buffer full): $message")
        }
        return delivered
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
     * The agent-done watcher in [EngineService] needs to know if the user
     * is looking at the app: a run that ends while the app is in the
     * foreground needs no notification. A counter, not a boolean: two
     * activities can overlap during a transition, and a single flag would
     * flicker false between onStop and onStart.
     */
    val isAppForeground: Boolean get() = ForegroundTracker.startedCount > 0
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
    if (!id.first().isLetterOrDigit()) return false
    return id.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }
}
