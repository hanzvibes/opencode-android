package dev.bidzz.opencode.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.StringRes
import dev.bidzz.opencode.MainActivity
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.EngineClient
import dev.bidzz.opencode.core.api.EventStream
import dev.bidzz.opencode.core.api.EventStreamAuthException
import dev.bidzz.opencode.core.api.SessionApi
import dev.bidzz.opencode.core.api.dto.EngineEvent
import dev.bidzz.opencode.core.engine.EngineCredentials
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.core.engine.EngineStatus
import dev.bidzz.opencode.core.engine.ProcessManager
import dev.bidzz.opencode.ui.isAgentRunEndEvent
import dev.bidzz.opencode.ui.notifyAgentDone
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Keeps the embedded engine alive while the app is not in the foreground.
 *
 * This is the whole reason the engine survives a task swipe: an app process with
 * a running foreground service is not a candidate for the cached-process killer.
 *
 * ## Why `specialUse`
 *
 * The engine is a local HTTP server that runs for as long as a coding session
 * lasts, with no upper bound. `dataSync` — the obvious fit — is capped at six
 * hours per 24 hours while the app is in the background, after which the system
 * calls `onTimeout` and refuses further `dataSync` services until the app is
 * reopened. That cap makes it unusable for this workload. `specialUse` has no
 * timeout and no runtime prerequisite; the trade-off is that Google Play reviews
 * the justification, which is moot because this app ships through GitHub
 * Releases (see the storage decision in `handoff.md` §3).
 *
 * ## Ordering constraint
 *
 * `startForeground` runs *before* the engine starts. The engine needs up to 60
 * seconds to become healthy, and the system only gives the service 5 seconds
 * after `startForegroundService` before it raises
 * `ForegroundServiceDidNotStartInTimeException`. The notification is therefore
 * published in its "starting" form first and updated as state arrives.
 */
class EngineService : Service() {
    companion object {
        private const val TAG = "EngineService"

        const val ACTION_START = "dev.bidzz.opencode.action.START"
        const val ACTION_STOP = "dev.bidzz.opencode.action.STOP"

        /**
         * Stops the engine and starts it again on the current workspace.
         *
         * Needed because the engine takes its working directory at process start:
         * changing the project directory only takes effect on a restart. Doing it
         * through the service keeps the foreground notification continuous, so the
         * app never drops out of the foreground during the swap.
         */
        const val ACTION_RESTART = "dev.bidzz.opencode.action.RESTART"

        private const val CHANNEL_ID = "engine"
        private const val NOTIFICATION_ID = 1001
        private const val FAILED_NOTIFICATION_ID = 1002

        /**
         * The failure channel (55-F3): failures must alert, so they get
         * their own `IMPORTANCE_DEFAULT` channel instead of sharing the
         * foreground channel's `IMPORTANCE_LOW`. A new id on purpose — a
         * channel's importance cannot be changed once created.
         */
        private const val FAILED_CHANNEL_ID = "engine_failure"

        /** Consecutive failures after which the service stops resurrecting itself. */
        private const val RESTART_FAILURE_LIMIT = 3

        /**
         * Publishes a low-importance, silent, ongoing notification.
         *
         * Not a status-bar nuisance: `IMPORTANCE_LOW` keeps it out of the heads-up
         * path while still satisfying the requirement that a foreground service be
         * user-visible and dismissible-by-stopping.
         */
        fun buildNotification(context: Context, status: EngineStatus): Notification {
            ensureChannel(context)

            // The baseUrl and lastError are engine data, never translated;
            // only the fixed words around them come from strings. "Ready"
            // and "Stop" reuse the existing keys with the same meaning.
            val (title, text) = when (status.state) {
                EngineState.STOPPED -> context.getString(R.string.engine_notif_stopped_title) to
                    context.getString(R.string.engine_notif_stopped_text)
                EngineState.INITIALIZING -> context.getString(R.string.engine_notif_starting_title) to
                    context.getString(R.string.engine_notif_preparing_text)
                EngineState.STARTING -> context.getString(R.string.engine_notif_starting_title) to
                    context.getString(R.string.engine_notif_waiting_text)
                EngineState.RUNNING -> context.getString(R.string.engine_notif_running_title) to
                    (status.credentials?.baseUrl ?: context.getString(R.string.chat_status_ready))
                EngineState.DEGRADED -> context.getString(R.string.engine_notif_unhealthy_title) to
                    (status.lastError ?: context.getString(R.string.engine_notif_health_failing_text))
                EngineState.STOPPING -> context.getString(R.string.engine_notif_stopping_title) to
                    context.getString(R.string.engine_notif_stopping_text)
                EngineState.FAILED -> context.getString(R.string.engine_notif_failed_title) to
                    (status.lastError ?: context.getString(R.string.engine_notif_failed_text))
            }

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val builder = Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(open)
                // 55-F5: the service is foreground whenever this notification
                // exists, in every state — INITIALIZING, FAILED and STOPPING
                // included. `isAlive` (STARTING/RUNNING/DEGRADED) let the
                // notification be swiped away mid-startup or mid-failure,
                // leaving a foreground service with no indicator and no
                // Stop action until the next status transition.
                .setOngoing(true)
                // Silence comes from the channel's IMPORTANCE_LOW; the platform
                // Notification.Builder has no setSilent, that lives in
                // NotificationCompat. onlyAlertOnce stops the frequent state
                // updates from re-alerting.
                .setOnlyAlertOnce(true)
                .setShowWhen(false)

            // 55-F5: the Stop action belongs to every state — a FAILED or
            // STOPPING service is exactly when the user wants to stop it.
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.composer_stop),
                    stopPendingIntent(context),
                ).build(),
            )
            return builder.build()
        }

        private fun stopPendingIntent(context: Context): PendingIntent =
            PendingIntent.getService(
                context,
                1,
                Intent(context, EngineService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.engine_notif_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = context.getString(R.string.engine_notif_channel_desc)
                        setShowBadge(false)
                    },
                )
            }
            // 55-F3: the failure channel lives apart from the foreground
            // one, so a failed start actually alerts instead of whispering
            // through the low-importance channel.
            if (manager.getNotificationChannel(FAILED_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        FAILED_CHANNEL_ID,
                        context.getString(R.string.engine_notif_failure_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = context.getString(R.string.engine_notif_failure_channel_desc)
                        setShowBadge(false)
                    },
                )
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var statusJob: Job? = null
    private val stopping = AtomicBoolean(false)
    private var consecutiveFailures = 0

    /** The agent-done stream watcher (55-F6), if one is running. */
    private var agentDoneJob: Job? = null

    /** Credentials the running watcher was built with; a restart rotates them. */
    private var agentDoneCredentials: EngineCredentials? = null

    /** Guards against two resurrection attempts overlapping while one is in flight. */
    private val resurrecting = AtomicBoolean(false)

    private val manager: ProcessManager?
        get() = (application as OpenCodeApplication).processManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Create the channel before the first startForeground, so the notification
        // is not dropped for a missing channel on a cold start.
        ensureChannel(this)
        observeStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        // Must happen before any slow work: the 5 second window starts here.
        promoteToForeground(manager?.status?.value ?: EngineStatus())

        if (action == ACTION_STOP) {
            requestStop()
            return START_NOT_STICKY
        }

        val supervisor = manager
        if (supervisor == null) {
            Log.e(TAG, "no supervisor; stopping service")
            postFailureNotification(R.string.engine_notif_unavailable_text)
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_RESTART) {
            scope.launch {
                Log.i(TAG, "restarting engine for workspace change")
                // `restart()` holds the supervisor's lifecycle lock, so a start that
                // is still in flight finishes or aborts cleanly before this runs.
                val result = supervisor.restart()
                result.onSuccess {
                    consecutiveFailures = 0
                    Log.i(TAG, "engine running at ${it.baseUrl}")
                }.onFailure { e ->
                    consecutiveFailures++
                    Log.e(TAG, "engine restart failed", e)
                    postFailureNotification(R.string.engine_notif_restart_failed_text)
                }
            }
            return START_STICKY
        }

        // A null intent means the system restarted us after a kill. Treating it as
        // START keeps the engine up without user intervention.
        scope.launch {
            val state = supervisor.status.value.state
            if (state.isAlive) {
                Log.i(TAG, "engine already ${state.name.lowercase()}, nothing to do")
                return@launch
            }
            // Audit-200 A9-3 (DL-029): the app died mid-run once — the
            // orphan may still be working, so this start does not reap
            // it. One-shot: the flag is cleared when the new engine is
            // up, so the next start reaps the orphan (by then idle, its
            // run finished and its results in the session store).
            val app = application as OpenCodeApplication
            val preserveBusyOrphan = app.lastBusySessionID != null
            val result = supervisor.start(preserveBusyOrphan = preserveBusyOrphan)
            result.onSuccess {
                consecutiveFailures = 0
                if (preserveBusyOrphan) app.lastBusySessionID = null
                Log.i(TAG, "engine running at ${it.baseUrl}")
            }.onFailure { e ->
                consecutiveFailures++
                Log.e(TAG, "engine start failed ($consecutiveFailures/$RESTART_FAILURE_LIMIT)", e)
                postFailureNotification(R.string.engine_notif_start_failed_text)
                if (consecutiveFailures >= RESTART_FAILURE_LIMIT) {
                    // Returning START_STICKY with a permanently failing start is a
                    // battery-draining crash loop. Give up and let the user retry.
                    Log.w(TAG, "giving up after $consecutiveFailures failed starts")
                    stopSelf()
                }
            }
        }

        return START_STICKY
    }

    private fun requestStop() {
        if (!stopping.compareAndSet(false, true)) return
        Log.i(TAG, "stop requested")
        // 55-F1: a stopped service leaves no failure behind either.
        getSystemService(NotificationManager::class.java)?.cancel(FAILED_NOTIFICATION_ID)
        scope.launch {
            manager?.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun promoteToForeground(status: EngineStatus) {
        val notification = buildNotification(this, status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // The type constant is only known from API 34; passing it on an older
            // platform throws. SPECIAL_USE exists exactly because no built-in type
            // describes a long-running local server.
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** Keeps the notification text in step with the supervisor. */
    private fun observeStatus() {
        statusJob?.cancel()
        val supervisor = manager ?: return
        statusJob = scope.launch {
            supervisor.status.collectLatest { status ->
                if (stopping.get()) return@collectLatest
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, buildNotification(this@EngineService, status))
                // A run that got this far earns a fresh retry budget: the limit counts
                // consecutive failed *resurrections*, not lifetime failures.
                if (status.state == EngineState.RUNNING) {
                    consecutiveFailures = 0
                    // 55-F1: the engine is up — a failure notification from
                    // an earlier attempt is stale now and must not linger
                    // next to the running one.
                    notificationManager?.cancel(FAILED_NOTIFICATION_ID)
                }
                updateAgentDoneWatcher(status)
                if (shouldResurrect(status.state, stopping.get(), resurrecting.get(), consecutiveFailures, RESTART_FAILURE_LIMIT)) {
                    resurrect(supervisor)
                }
            }
        }
    }

    /**
     * Watches the engine's global event stream for finished agent runs
     * (Fase 5, 55-F6).
     *
     * The old trigger lived in the chat composition (`LaunchedEffect`
     * on `busy`): swiping the app from recents destroyed the activity
     * and the ViewModel with it, so a run that finished afterwards had
     * no observer at all — the headline "agent done" notification never
     * fired in the most common background case. The service survives
     * the swipe (that is its job), so the watcher lives here, on the
     * engine's own `/api/event` stream: `execution.succeeded/failed`
     * is the run-end signal, no per-session polling.
     *
     * The watcher runs only while the engine is RUNNING and restarts
     * whenever the credentials rotate (every engine restart prints a
     * new password). It notifies only while the app is not in the
     * foreground — a run that ends while the user watches needs no
     * notification, and the UI trigger still covers the
     * backgrounded-but-alive case. Posting twice for the same run is
     * harmless: both paths use the same notification id, so the second
     * post replaces the first. A title fetch that fails falls back to
     * the generic body text.
     */
    private fun updateAgentDoneWatcher(status: EngineStatus) {
        val credentials = if (status.state == EngineState.RUNNING) status.credentials else null
        if (credentials == agentDoneCredentials && agentDoneJob?.isActive == true) return
        agentDoneJob?.cancel()
        agentDoneCredentials = credentials
        if (credentials == null) return
        val client = EngineClient(credentials.baseUrl, credentials.basicAuthHeader)
        val sessions = SessionApi(client)
        agentDoneJob = scope.launch {
            try {
                EventStream(client).events().collect { event ->
                    if (!isAgentRunEndEvent(event.type)) return@collect
                    val sessionID = event.sessionIDOf() ?: return@collect
                    if ((application as OpenCodeApplication).isAppForeground) return@collect
                    val title = runCatching { sessions.get(sessionID) }
                        .getOrNull()
                        ?.let { (it as? ApiResult.Success)?.value?.title }
                    notifyAgentDone(this@EngineService, sessionID, title)
                }
            } catch (e: EventStreamAuthException) {
                // The password rotated: the next RUNNING status restarts
                // the watcher with fresh credentials. Cancellation is
                // deliberately not caught: it must propagate when the job
                // is cancelled (engine stopped, credentials rotated).
                Log.i(TAG, "agent-done stream rejected (${e.message}); waiting for fresh credentials")
            }
        }
    }

    /** The session an engine event belongs to, if its payload names one. */
    private fun EngineEvent.sessionIDOf(): String? =
        data?.jsonObject?.get("sessionID")?.jsonPrimitive?.contentOrNull

    /**
     * Starts the engine again after it exited on its own.
     *
     * Deliberately does not `stopSelf()` when the limit is reached, unlike the
     * start-at-launch path: the service being alive is what keeps the user's next
     * action — switching project, sending a message — able to reach a supervisor that
     * can try again. Stopping here would trade a visible failure for a dead end.
     */
    private fun resurrect(supervisor: ProcessManager) {
        if (!resurrecting.compareAndSet(false, true)) return
        consecutiveFailures++
        Log.w(TAG, "engine failed; resurrecting (attempt $consecutiveFailures/$RESTART_FAILURE_LIMIT)")
        scope.launch {
            val result = supervisor.start()
            resurrecting.set(false)
            result.onSuccess { Log.i(TAG, "engine resurrected at ${it.baseUrl}") }
                .onFailure { e ->
                    Log.e(TAG, "resurrect failed: ${e.message}")
                    if (consecutiveFailures >= RESTART_FAILURE_LIMIT) {
                        postFailureNotification(R.string.engine_notif_restart_failed_text)
                    }
                }
        }
    }

    /**
     * The failure notification (92-F3): the title and the text are string
     * resources in the app language (the service context is wrapped by the
     * Application's `attachBaseContext`, 92-F2). The exception's own words
     * are NOT shown — they stay in the log at the call site, where they are
     * already recorded with the exception — because exception text is
     * English, and it can also carry a path or a password value (55-F4).
     */
    private fun postFailureNotification(@StringRes textRes: Int) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        // 55-F2: the notification dismisses itself when tapped.
        val notification = Notification.Builder(this, FAILED_CHANNEL_ID)
            .setContentTitle(getString(R.string.engine_notif_failed_title))
            .setContentText(getString(textRes))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setAutoCancel(true)
            .build()
        manager.notify(FAILED_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        Log.i(TAG, "service destroyed")
        // 55-F1: no failure notification outlives the service.
        getSystemService(NotificationManager::class.java)?.cancel(FAILED_NOTIFICATION_ID)
        statusJob?.cancel()
        scope.cancel()
        // onDestroy cannot suspend, and the engine must not outlive the service:
        // an orphan holds a port and 200 MB of memory with nothing left to talk to
        // it. Graceful SIGTERM first, SIGKILL shortly after.
        manager?.shutdownBlocking()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Deliberately does nothing. The engine must keep running when the task is
        // swiped away; that is the entire purpose of this service.
        Log.i(TAG, "task removed; keeping engine alive")
        super.onTaskRemoved(rootIntent)
    }
}
