package dev.bidzz.opencode.service

import dev.bidzz.opencode.core.engine.EngineState

/**
 * Whether a status means the service has to bring the engine back.
 *
 * A crash does not fix itself, and the usual Android answer does not apply here:
 * `START_STICKY` restarts the *service* after the system kills it, but on an
 * unexpected engine exit the service is still alive and running its foreground
 * notification, so nothing calls `onStartCommand` again. Without a nudge from the
 * status stream the app stays attached to a process that no longer exists until the
 * user force-quits it.
 *
 * Only `FAILED` qualifies. `DEGRADED` keeps a live process and its credentials, and
 * restarting it would throw away a server that may recover on the next health check.
 * `STOPPED` and `STOPPING` mean somebody asked for this — a user stop must not be
 * undone by an auto-resurrect that races the shutdown transition.
 */
fun shouldResurrect(
    state: EngineState,
    stopping: Boolean,
    inFlight: Boolean,
    attempts: Int,
    limit: Int,
): Boolean = state == EngineState.FAILED && !stopping && !inFlight && attempts < limit
