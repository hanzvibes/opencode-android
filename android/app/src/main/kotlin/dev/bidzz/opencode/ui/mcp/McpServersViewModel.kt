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
) : ViewModel() {

    private val _state = MutableStateFlow(McpServersUiState())
    val state: StateFlow<McpServersUiState> = _state.asStateFlow()

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
                    runtimeAdded.retainListed(result.value.map { it.name })
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
                    runtimeAdded.added(name)
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
