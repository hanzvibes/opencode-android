package dev.bidzz.opencode.ui.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.ui.engineDownFailure
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.McpResourceCatalog
import dev.bidzz.opencode.core.api.dto.McpServer
import dev.bidzz.opencode.core.api.dto.McpServerConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class McpServersUiState(
    val servers: List<McpServer> = emptyList(),
    val loading: Boolean = true,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
    /** Resources and templates from every connected server; null until loaded. */
    val resources: McpResourceCatalog? = null,
    /**
     * What the catalog load got wrong (audit-200 D10, m-1010): a failed
     * catalog used to leave [resources] null — the same value as "no
     * resources exist" — so the section vanished without a word. Kept
     * separate from [failure] on purpose: the server list is fine, only
     * the secondary catalog failed, so the screen shows a banner over
     * the list instead of a full-screen error.
     */
    val resourcesFailure: ApiResult.Failure? = null,
    /** Names of servers with a connect / disconnect / remove in flight. */
    val busyServers: Set<String> = emptySet(),
    /** True while an add is in flight. */
    val adding: Boolean = false,
    /**
     * What the last action (add / connect / disconnect / remove) got
     * wrong; the screen shows it once as a snackbar, then clears it.
     * A failed list load goes to [failure] instead — the two are never
     * confused, because an action failure must not blank the list.
     */
    val actionFailure: ApiResult.Failure? = null,
    /**
     * Audit-200 D7 (m-629): the last action failure per server, kept
     * until that server's next successful action or a dismiss — a
     * snackbar vanishes, but the row keeps saying what went wrong.
     * Without this, a failed Connect/Disconnect/Remove left the row
     * looking exactly as before the tap once the snackbar was gone.
     */
    val failedActions: Map<String, ApiResult.Failure> = emptyMap(),
    /**
     * What the last action got right (audit-200 B2, M-293): a success
     * used to be silent — the quiet reload often showed the same status,
     * so the tap looked like it did nothing. The screen shows this once
     * as a snackbar, then clears it, like [actionFailure].
     */
    val actionSuccess: McpActionSuccess? = null,
    /**
     * True when this session changed a server at runtime (audit-200 B2,
     * M-292): an add, connect, disconnect or remove that the next engine
     * restart undoes or deletes. The screen shows the pending-restart
     * banner while this is true. It clears when the engine handle
     * changes — a restart already happened, so there is nothing pending
     * anymore.
     */
    val hasRuntimeChanges: Boolean = false,
    /**
     * An OAuth re-auth attempt in flight for one NEEDS_AUTH server
     * (audit-200 C17, M-262); null when no re-auth is running.
     */
    val reauth: ReauthAttempt? = null,
    /** True while a re-auth attempt is being started, completed, or cancelled. */
    val reauthBusy: Boolean = false,
    /**
     * A client-side re-auth problem — the integration has no OAuth method
     * to repeat. API failures go to [actionFailure] instead. The screen
     * shows this once as a snackbar, then clears it.
     */
    val reauthError: ReauthError? = null,
)

/** One successful MCP action, for the one-shot success snackbar (M-293). */
enum class McpAction { ADD, CONNECT, DISCONNECT, REMOVE, REAUTH }

/** A successful MCP action on [name] (M-293). */
data class McpActionSuccess(val action: McpAction, val name: String)

/**
 * An OAuth re-auth attempt in flight for one NEEDS_AUTH server
 * (audit-200 C17, M-262).
 *
 * The user authorizes in the browser, then either the redirect lands on
 * the engine by itself ("auto") or they paste a code back ([needsCode]).
 * [status] is the last polled attempt status: "pending" | "complete" |
 * "failed" | "expired".
 */
data class ReauthAttempt(
    val serverName: String,
    val integrationID: String,
    val attemptID: String,
    val url: String,
    val instructions: String,
    val needsCode: Boolean,
    val status: String = "pending",
    val statusMessage: String? = null,
)

/** A re-auth problem the engine call never reached. */
enum class ReauthError { NO_OAUTH_METHOD }

/** The engine's `Mcp.Status.*` variants, as a word the UI can switch on. */
enum class McpStatusKind { CONNECTED, DISABLED, FAILED, NEEDS_AUTH, PENDING, UNKNOWN }

/**
 * The server in [servers] with exactly this name, or null (audit-200
 * A9-1, DL-014): adding replaces an existing server with the same name,
 * so the form must warn first. The match is exact — the engine takes
 * the name as a path segment, and a differently-cased name is a
 * different server.
 */
internal fun mcpServerNamed(servers: List<McpServer>, name: String): McpServer? =
    servers.firstOrNull { it.name == name }

/**
 * Names added at runtime in this session (audit-200 A9-1, DL-015).
 *
 * The engine's list carries no source flag, so the app tracks what it
 * added itself: those servers live in no config file, and removing one
 * deletes it for good — unlike a config server, which comes back after
 * a restart. [retainListed] drops names the engine no longer lists (a
 * restart already took them), so a later config server with the same
 * name is not misread as runtime-only.
 */
internal class RuntimeAddedServers {
    private val names = mutableSetOf<String>()

    fun added(name: String) {
        names += name
    }

    fun isRuntimeOnly(name: String): Boolean = name in names

    fun retainListed(listed: List<String>) {
        names.retainAll(listed.toSet())
    }
}

/**
 * Maps the engine's status string to a kind.
 *
 * Compared case-insensitively, so both `connected` and `Connected` read
 * the same. Anything unrecognised is UNKNOWN, never a crash: the engine
 * gains statuses without notice.
 */
internal fun McpServer.statusKind(): McpStatusKind = when (status.status.lowercase()) {
    "connected" -> McpStatusKind.CONNECTED
    "disabled" -> McpStatusKind.DISABLED
    "failed" -> McpStatusKind.FAILED
    "needs-auth", "needs_auth", "needsauth" -> McpStatusKind.NEEDS_AUTH
    "pending" -> McpStatusKind.PENDING
    else -> McpStatusKind.UNKNOWN
}

/**
 * The engine's configured MCP servers and their connection status.
 *
 * Listing comes from `GET /api/mcp`, resources from
 * `GET /api/mcp/resource`. Add / remove / connect / disconnect are the
 * experimental endpoints — all runtime-only (see [McpServerConfig]):
 * a server added here is gone after the next engine restart, and a
 * removed one comes back with it.
 *
 * One action per server at a time ([busyServers] guards double taps);
 * a failed action reports through [McpServersUiState.actionFailure]
 * and never blanks the list.
 */
class McpServersViewModel(
    private val api: EngineApi,
    /**
     * Shared with the Providers screen (audit-200 B4, M-457): a provider
     * restart must ask first when runtime MCP changes are pending. Defaults
     * to a private instance so existing callers need no wiring.
     */
    private val runtimeChangesSignal: McpRuntimeChangesSignal = McpRuntimeChangesSignal(),
) : ViewModel() {

    private val _state = MutableStateFlow(McpServersUiState())
    val state: StateFlow<McpServersUiState> = _state.asStateFlow()

    /**
     * In-flight list load, cancelled when a fresh load starts, and its
     * generation (audit-200 D4, m-392): the same guard SessionListViewModel
     * uses. Cancellation alone does not cover it — a blocked HTTP call
     * returns after its job was cancelled, and the code after it still
     * runs — so a stale load drops its result instead of overwriting the
     * newer list.
     */
    private var loadJob: Job? = null
    private var loadGeneration = 0

    /** Servers this session added at runtime — see [RuntimeAddedServers]. */
    private val runtimeAdded = RuntimeAddedServers()

    /** True when [name] was added at runtime and lives in no config file (DL-015). */
    fun isRuntimeOnly(name: String): Boolean = runtimeAdded.isRuntimeOnly(name)

    fun refresh() {
        load(showLoading = true)
    }

    /**
     * Reloads the list and the catalog.
     *
     * `showLoading` is false after a successful mutation: the list stays
     * visible and is replaced in place instead of flashing the loading
     * state. A failed catalog load leaves [McpServersUiState.resources]
     * null and records [McpServersUiState.resourcesFailure] — the
     * catalog is secondary to the server list, so the screen shows a
     * banner over the list instead of a full-screen error; a failed list
     * already reports the engine problem through
     * [McpServersUiState.failure].
     */
    /**
     * The engine handle this VM last loaded from (M-292): a restart
     * attaches a new handle, which is the only signal the runtime
     * changes are already gone — so the pending-restart banner clears
     * then, not on a guess.
     */
    private var lastHandle: EngineApi.Handle? = null

    private fun load(showLoading: Boolean) {
        val handle = api.handle()
        if (handle == null) {
            _state.value = _state.value.copy(loading = false, failure = engineDownFailure())
            return
        }
        // Audit-200 B2 (M-292): a new handle means the engine restarted
        // since the last load — the runtime changes it carried are gone,
        // so the banner clears instead of promising a restart that
        // already happened. The shared signal clears with it (B4, M-457):
        // a provider restart after this one has nothing to discard.
        if (handle !== lastHandle) {
            lastHandle = handle
            runtimeChangesSignal.clear()
            _state.value = _state.value.copy(hasRuntimeChanges = false)
        }
        if (showLoading) {
            _state.value = _state.value.copy(loading = true, failure = null)
        }
        // Audit-200 D4 (m-392): refresh() is callable while a load is
        // in flight (the Retry button stays visible during loading) and
        // every successful mutation fires load(false) — without a guard
        // the last load to FINISH wins, not the last to start, so a stale
        // list or a stale error can overwrite the fresh one.
        val generation = ++loadGeneration
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when (val result = handle.mcp.list()) {
                is ApiResult.Success -> {
                    if (generation != loadGeneration) return@launch
                    runtimeAdded.retainListed(result.value.map { it.name })
                    // Audit-200 B6 (M-017): a quiet reload after a mutation
                    // (add/connect/remove call load(false)) must clear a
                    // previous list failure — a successful Add used to leave
                    // the screen showing the old error state.
                    _state.value = _state.value.copy(
                        servers = result.value.sortedBy { it.name.lowercase() },
                        loading = false,
                        failure = null,
                    )
                    when (val catalog = handle.mcp.resourceCatalog()) {
                        is ApiResult.Success -> {
                            if (generation != loadGeneration) return@launch
                            _state.value = _state.value.copy(resources = catalog.value, resourcesFailure = null)
                        }
                        is ApiResult.Failure -> {
                            if (generation != loadGeneration) return@launch
                            _state.value = _state.value.copy(resources = null, resourcesFailure = catalog)
                        }
                    }
                }
                is ApiResult.Failure -> {
                    if (generation != loadGeneration) return@launch
                    _state.value = _state.value.copy(
                        loading = false,
                        failure = result,
                    )
                }
            }
        }
    }

    /** Adds a server at runtime and connects it immediately. */
    fun addServer(name: String, config: McpServerConfig) {
        // Audit-200 B2 (M-178/M-184): a dead engine used to swallow the
        // tap silently — the form closed and nothing happened. The
        // failure is reported the same way a failed add is.
        val handle = api.handle()
        if (handle == null) {
            _state.value = _state.value.copy(actionFailure = engineDownFailure())
            return
        }
        if (_state.value.adding) return
        _state.value = _state.value.copy(adding = true, actionFailure = null)
        viewModelScope.launch {
            when (val result = handle.mcp.add(name, config)) {
                is ApiResult.Success -> {
                    runtimeAdded.added(name)
                    runtimeChangesSignal.markChanged()
                    _state.value = _state.value.copy(
                        adding = false,
                        actionSuccess = McpActionSuccess(McpAction.ADD, name),
                        hasRuntimeChanges = true,
                        // Audit-200 D7 (m-629): a success clears this
                        // server's row trace.
                        failedActions = _state.value.failedActions - name,
                    )
                    load(showLoading = false)
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    adding = false,
                    actionFailure = result,
                    // Audit-200 D7 (m-629): the row keeps the failure
                    // after the snackbar is gone.
                    failedActions = _state.value.failedActions + (name to result),
                )
            }
        }
    }

    /** Connects a server at runtime, overriding a disabled configuration until restart. */
    fun connect(name: String) {
        mutate(name, McpAction.CONNECT) { handle -> handle.mcp.connect(name) }
    }

    /** Disconnects a server at runtime, removing its tools until reconnected. */
    fun disconnect(name: String) {
        mutate(name, McpAction.DISCONNECT) { handle -> handle.mcp.disconnect(name) }
    }

    /** Stops a server and removes it from the runtime set until restart. */
    fun remove(name: String) {
        mutate(name, McpAction.REMOVE) { handle -> handle.mcp.remove(name) }
    }

    /**
     * One per-server mutation: guards double taps through [McpServersUiState.busyServers],
     * reloads quietly on success, reports through `actionFailure` on failure
     * and through [McpServersUiState.actionSuccess] on success (M-293).
     */
    private fun mutate(
        name: String,
        action: McpAction,
        call: suspend (EngineApi.Handle) -> ApiResult<Unit>,
    ) {
        // Audit-200 B2 (M-178/M-184): same as addServer — a dead engine
        // used to swallow the tap silently.
        val handle = api.handle()
        if (handle == null) {
            // Audit-200 D7 (m-629): the row keeps the engine-down failure
            // too — the snackbar alone would vanish without a trace.
            val failure = engineDownFailure()
            _state.value = _state.value.copy(
                actionFailure = failure,
                failedActions = _state.value.failedActions + (name to failure),
            )
            return
        }
        if (name in _state.value.busyServers) return
        _state.value = _state.value.copy(
            busyServers = _state.value.busyServers + name,
            actionFailure = null,
        )
        viewModelScope.launch {
            when (val result = call(handle)) {
                is ApiResult.Success -> {
                    runtimeChangesSignal.markChanged()
                    _state.value = _state.value.copy(
                        busyServers = _state.value.busyServers - name,
                        actionSuccess = McpActionSuccess(action, name),
                        hasRuntimeChanges = true,
                        // Audit-200 D7 (m-629): a success clears this
                        // server's row trace.
                        failedActions = _state.value.failedActions - name,
                    )
                    load(showLoading = false)
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    busyServers = _state.value.busyServers - name,
                    actionFailure = result,
                    // Audit-200 D7 (m-629): the row keeps the failure
                    // after the snackbar is gone.
                    failedActions = _state.value.failedActions + (name to result),
                )
            }
        }
    }

    /** The screen calls this after showing [McpServersUiState.actionFailure] once. */
    fun clearActionFailure() {
        _state.value = _state.value.copy(actionFailure = null)
    }

    /** Dismisses the per-server failure line under a row (audit-200 D7, m-629). */
    fun clearFailedAction(name: String) {
        _state.value = _state.value.copy(failedActions = _state.value.failedActions - name)
    }

    /** The screen calls this after showing [McpServersUiState.actionSuccess] once. */
    fun clearActionSuccess() {
        _state.value = _state.value.copy(actionSuccess = null)
    }

    /** The screen calls this after showing [McpServersUiState.reauthError] once. */
    fun clearReauthError() {
        _state.value = _state.value.copy(reauthError = null)
    }

    /**
     * Starts an OAuth re-auth for a NEEDS_AUTH server (audit-200 C17,
     * M-262): the server's auth expired, and "Connect" only retries the
     * same handshake — it cannot re-authorize.
     *
     * The OAuth method id comes from the integration list (the integration
     * itself is found by the server's `integrationID`, which the app used
     * to ignore). The attempt opens in the browser; the screen polls the
     * status until it completes, fails, or expires.
     */
    fun beginReauth(server: McpServer) {
        if (_state.value.reauth != null || _state.value.reauthBusy) return
        val integrationID = server.integrationID
        if (integrationID.isNullOrBlank()) {
            _state.value = _state.value.copy(reauthError = ReauthError.NO_OAUTH_METHOD)
            return
        }
        val handle = api.handle()
        if (handle == null) {
            _state.value = _state.value.copy(actionFailure = engineDownFailure())
            return
        }
        _state.value = _state.value.copy(reauthBusy = true, reauthError = null)
        viewModelScope.launch {
            when (val infos = handle.integrations.list()) {
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    reauthBusy = false,
                    actionFailure = infos,
                )
                is ApiResult.Success -> {
                    val method = infos.value
                        .firstOrNull { it.id == integrationID }
                        ?.methods
                        ?.firstOrNull { it.type == "oauth" }
                    if (method == null) {
                        _state.value = _state.value.copy(
                            reauthBusy = false,
                            reauthError = ReauthError.NO_OAUTH_METHOD,
                        )
                        return@launch
                    }
                    when (val attempt = handle.integrations.beginOAuth(integrationID, method.id)) {
                        is ApiResult.Failure -> _state.value = _state.value.copy(
                            reauthBusy = false,
                            actionFailure = attempt,
                        )
                        is ApiResult.Success -> {
                            val value = attempt.value
                            _state.value = _state.value.copy(
                                reauthBusy = false,
                                reauth = ReauthAttempt(
                                    serverName = server.name,
                                    integrationID = integrationID,
                                    attemptID = value.attemptID,
                                    url = value.url,
                                    instructions = value.instructions,
                                    // "code" mode shows a code to paste back;
                                    // an "auto" attempt with paste accepts the
                                    // redirect URL pasted from the browser.
                                    needsCode = value.mode == "code" || value.paste,
                                ),
                            )
                            pollReauth()
                        }
                    }
                }
            }
        }
    }

    /**
     * Submits the code (or pasted redirect URL) for the in-flight re-auth
     * attempt. The poll loop picks the result up within seconds; a failed
     * submit is reported through [McpServersUiState.actionFailure].
     */
    fun submitReauthCode(code: String) {
        val current = _state.value.reauth ?: return
        if (_state.value.reauthBusy) return
        val handle = api.handle()
        if (handle == null) {
            _state.value = _state.value.copy(actionFailure = engineDownFailure())
            return
        }
        _state.value = _state.value.copy(reauthBusy = true)
        viewModelScope.launch {
            when (
                val result = handle.integrations.completeOAuth(
                    current.integrationID,
                    current.attemptID,
                    code.ifBlank { null },
                )
            ) {
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    reauthBusy = false,
                    actionFailure = result,
                )
                is ApiResult.Success -> _state.value = _state.value.copy(reauthBusy = false)
            }
        }
    }

    /**
     * Cancels the in-flight re-auth attempt (best effort DELETE) and
     * closes the dialog. A failed cancel leaves the attempt to expire
     * on its own — nothing to report to the user.
     */
    fun cancelReauth() {
        val current = _state.value.reauth ?: return
        reauthPollJob?.cancel()
        reauthPollJob = null
        _state.value = _state.value.copy(reauth = null, reauthError = null, reauthBusy = false)
        viewModelScope.launch {
            api.handle()?.integrations?.cancelOAuth(current.integrationID, current.attemptID)
        }
    }

    /** Polls the in-flight attempt's status every 2 s until it settles. */
    private var reauthPollJob: Job? = null

    private fun pollReauth() {
        reauthPollJob?.cancel()
        reauthPollJob = viewModelScope.launch {
            while (true) {
                delay(REAUTH_POLL_MS)
                val current = _state.value.reauth ?: break
                if (current.status != "pending") break
                val handle = api.handle() ?: break
                when (
                    val status = handle.integrations.getOAuthStatus(
                        current.integrationID,
                        current.attemptID,
                    )
                ) {
                    is ApiResult.Success -> {
                        val settled = status.value.status.lowercase()
                        _state.value = _state.value.copy(
                            reauth = current.copy(
                                status = settled,
                                statusMessage = status.value.message,
                            ),
                        )
                        if (settled == "complete") {
                            reauthPollJob?.cancel()
                            reauthPollJob = null
                            _state.value = _state.value.copy(
                                reauth = null,
                                actionSuccess = McpActionSuccess(
                                    McpAction.REAUTH,
                                    current.serverName,
                                ),
                            )
                            load(showLoading = false)
                        }
                    }
                    // A transient poll failure must not kill the dialog —
                    // the user may still be mid-authorization in the
                    // browser. Keep polling; the attempt expires on its own.
                    is ApiResult.Failure -> Unit
                }
            }
        }
    }
}

/** Delay between OAuth attempt status polls (M-262). */
private const val REAUTH_POLL_MS: Long = 2_000

/**
 * Parses `KEY=value` / `KEY: value` lines (one per line) into a map
 * (audit-200 B2, M-291): the Add form's environment variables and
 * headers. The split is on the FIRST `=` or `:` — values may contain
 * either (a URL, a base64 token). Blank lines and lines with no
 * separator are skipped, not errors: a stray line must not eat the
 * whole server. A later line wins on a duplicate key.
 */
internal fun parseKeyValueLines(text: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue
        val eq = line.indexOf('=')
        val colon = line.indexOf(':')
        val split = when {
            eq < 0 && colon < 0 -> continue
            eq < 0 -> colon
            colon < 0 -> eq
            else -> minOf(eq, colon)
        }
        val key = line.substring(0, split).trim()
        val value = line.substring(split + 1).trim()
        if (key.isEmpty() || value.isEmpty()) continue
        out[key] = value
    }
    return out
}
