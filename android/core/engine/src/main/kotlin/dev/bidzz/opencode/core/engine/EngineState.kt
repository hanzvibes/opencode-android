package dev.bidzz.opencode.core.engine

/**
 * Explicit lifecycle states for the embedded engine.
 *
 * Transitions are one-way except where noted. Anything that would be an illegal
 * transition is rejected rather than silently coerced, so a bug in the supervisor
 * surfaces as a failed transition instead of a wrong UI state.
 *
 * ```
 * STOPPED ──start()──> INITIALIZING ──> STARTING ──> RUNNING
 *    ^                      │               │           │
 *    │                      v               v           v
 *    │                   FAILED <───────────┴────── DEGRADED
 *    │                      │                           │
 *    └──────stop()────── STOPPING <─────────────────────┘
 * ```
 */
enum class EngineState {
    /** No process, no server, nothing allocated. */
    STOPPED,

    /** Verifying the binary, preparing directories and environment. */
    INITIALIZING,

    /** Process launched, waiting for the server to accept connections. */
    STARTING,

    /** Health check passed; the API is usable. */
    RUNNING,

    /** Process alive but failing health checks; may recover or be restarted. */
    DEGRADED,

    /** Shutdown requested; waiting for the process to exit. */
    STOPPING,

    /** Terminal failure. Requires an explicit [ProcessManager.start] to retry. */
    FAILED,
    ;

    val isAlive: Boolean
        get() = this == STARTING || this == RUNNING || this == DEGRADED

    /** Whether a `start` call is meaningful from this state. */
    val canStart: Boolean
        get() = this == STOPPED || this == FAILED

    /** Whether a `stop` call is meaningful from this state. */
    val canStop: Boolean
        get() = this == RUNNING || this == DEGRADED || this == STARTING

    fun canTransitionTo(next: EngineState): Boolean =
        when (this) {
            STOPPED -> next == INITIALIZING
            INITIALIZING -> next == STARTING || next == FAILED || next == STOPPED
            STARTING -> next == RUNNING || next == FAILED || next == STOPPING
            RUNNING -> next == DEGRADED || next == STOPPING || next == FAILED
            DEGRADED -> next == RUNNING || next == STOPPING || next == FAILED
            STOPPING -> next == STOPPED || next == FAILED
            FAILED -> next == INITIALIZING || next == STOPPED
        }
}

/** Raised when the supervisor attempts a transition the state machine forbids. */
class IllegalTransitionException(from: EngineState, to: EngineState) :
    IllegalStateException("Illegal engine transition: $from -> $to")
