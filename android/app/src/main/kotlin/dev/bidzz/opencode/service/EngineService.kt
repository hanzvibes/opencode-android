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
import dev.bidzz.opencode.MainActivity
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.core.engine.EngineStatus
import dev.bidzz.opencode.core.engine.ProcessManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

            val (title, text) = when (status.state) {
                EngineState.STOPPED -> "Engine stopped" to "Not running"
                EngineState.INITIALIZING -> "Engine starting" to "Preparing environment"
                EngineState.STARTING -> "Engine starting" to "Waiting for the server"
                EngineState.RUNNING -> "Engine running" to (status.credentials?.baseUrl ?: "Ready")
                EngineState.DEGRADED -> "Engine unhealthy" to (status.lastError ?: "Health check failing")
                EngineState.STOPPING -> "Engine stopping" to "Shutting down"
                EngineState.FAILED -> "Engine failed" to (status.lastError ?: "Stopped unexpectedly")
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
                .setOngoing(status.state.isAlive)
                // Silence comes from the channel's IMPORTANCE_LOW; the platform
                // Notification.Builder has no setSilent, that lives in
                // NotificationCompat. onlyAlertOnce stops the frequent state
                // updates from re-alerting.
                .setOnlyAlertOnce(true)
                .setShowWhen(false)

            if (status.state.isAlive || status.state == EngineState.STOPPED) {
                builder.addAction(
                    Notification.Action.Builder(
                        null,
                        "Stop",
                        stopPendingIntent(context),
                    ).build(),
                )
            }
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
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Engine", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Keeps the local OpenCode engine running"
                    setShowBadge(false)
                },
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var statusJob: Job? = null
    private val stopping = AtomicBoolean(false)
    private var consecutiveFailures = 0

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
            postFailureNotification("Engine unavailable")
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
                    postFailureNotification(e.message ?: "Engine failed to restart")
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
            val result = supervisor.start()
            result.onSuccess {
                consecutiveFailures = 0
                Log.i(TAG, "engine running at ${it.baseUrl}")
            }.onFailure { e ->
                consecutiveFailures++
                Log.e(TAG, "engine start failed ($consecutiveFailures/$RESTART_FAILURE_LIMIT)", e)
                postFailureNotification(e.message ?: "Engine failed to start")
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
                if (status.state == EngineState.RUNNING) consecutiveFailures = 0
                if (shouldResurrect(status.state, stopping.get(), resurrecting.get(), consecutiveFailures, RESTART_FAILURE_LIMIT)) {
                    resurrect(supervisor)
                }
            }
        }
    }

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
                        postFailureNotification(e.message ?: "Engine failed to restart")
                    }
                }
        }
    }

    private fun postFailureNotification(reason: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Engine could not start")
            .setContentText(reason)
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
            .build()
        manager.notify(FAILED_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        Log.i(TAG, "service destroyed")
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
