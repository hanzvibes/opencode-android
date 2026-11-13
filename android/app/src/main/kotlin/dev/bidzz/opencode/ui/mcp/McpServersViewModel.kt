package dev.bidzz.opencode.ui.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.ui.engineDownFailure
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.McpResourceCatalog
import dev.bidzz.opencode.core.api.dto.McpServer
import dev.bidzz.opencode.core.api.dto.McpServerConfig
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
)

/** The engine's `Mcp.Status.*` variants, as a word the UI can switch on. */
enum class McpStatusKind { CONNECTED, DISABLED, FAILED, NEEDS_AUTH, PENDING, UNKNOWN }

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
) : ViewModel() {

    private val _state = MutableStateFlow(McpServersUiState())
    val state: StateFlow<McpServersUiState> = _state.asStateFlow()

    fun refresh() {
        load(showLoading = true)
    }

    /**
     * Reloads the list and the catalog.
     *
     * `showLoading` is false after a successful mutation: the list stays
     * visible and is replaced in place instead of flashing the loading
     * state. A failed catalog load leaves [McpServersUiState.resources]
     * null and hides the resources section — the catalog is secondary to
     * the server list, and a failed list already reports the engine
     * problem through [McpServersUiState.failure].
     */
    private fun load(showLoading: Boolean) {
        val handle = api.handle()
        if (handle == null) {
            _state.value = _state.value.copy(loading = false, failure = engineDownFailure())
            return
        }
        if (showLoading) {
            _state.value = _state.value.copy(loading = true, failure = null)
        }
        viewModelScope.launch {
            when (val result = handle.mcp.list()) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        servers = result.value.sortedBy { it.name.lowercase() },
                        loading = false,
                    )
                    when (val catalog = handle.mcp.resourceCatalog()) {
                        is ApiResult.Success -> _state.value =
                            _state.value.copy(resources = catalog.value)
                        is ApiResult.Failure -> _state.value =
                            _state.value.copy(resources = null)
                    }
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    failure = result,
                )
            }
        }
    }

    /** Adds a server at runtime and connects it immediately. */
    fun addServer(name: String, config: McpServerConfig) {
        val handle = api.handle() ?: return
        if (_state.value.adding) return
        _state.value = _state.value.copy(adding = true, actionFailure = null)
        viewModelScope.launch {
            when (val result = handle.mcp.add(name, config)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(adding = false)
                    load(showLoading = false)
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    adding = false,
                    actionFailure = result,
                )
            }
        }
    }

    /** Connects a server at runtime, overriding a disabled configuration until restart. */
    fun connect(name: String) {
        mutate(name) { handle -> handle.mcp.connect(name) }
    }

    /** Disconnects a server at runtime, removing its tools until reconnected. */
    fun disconnect(name: String) {
        mutate(name) { handle -> handle.mcp.disconnect(name) }
    }

    /** Stops a server and removes it from the runtime set until restart. */
    fun remove(name: String) {
        mutate(name) { handle -> handle.mcp.remove(name) }
    }

    /**
     * One per-server mutation: guards double taps through [McpServersUiState.busyServers],
     * reloads quietly on success, reports through `actionFailure` on failure.
     */
    private fun mutate(
        name: String,
        call: suspend (EngineApi.Handle) -> ApiResult<Unit>,
    ) {
        val handle = api.handle() ?: return
        if (name in _state.value.busyServers) return
        _state.value = _state.value.copy(
            busyServers = _state.value.busyServers + name,
            actionFailure = null,
        )
        viewModelScope.launch {
            when (val result = call(handle)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        busyServers = _state.value.busyServers - name,
                    )
                    load(showLoading = false)
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    busyServers = _state.value.busyServers - name,
                    actionFailure = result,
                )
            }
        }
    }

    /** The screen calls this after showing [McpServersUiState.actionFailure] once. */
    fun clearActionFailure() {
        _state.value = _state.value.copy(actionFailure = null)
    }
}
