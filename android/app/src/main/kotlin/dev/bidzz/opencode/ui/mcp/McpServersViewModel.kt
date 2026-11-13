package dev.bidzz.opencode.ui.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.ui.engineDownFailure
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.McpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class McpServersUiState(
    val servers: List<McpServer> = emptyList(),
    val loading: Boolean = true,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
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
 * List-only: adding, removing, connecting and disconnecting are
 * experimental endpoints the app does not call.
 */
class McpServersViewModel(
    private val api: EngineApi,
) : ViewModel() {

    private val _state = MutableStateFlow(McpServersUiState())
    val state: StateFlow<McpServersUiState> = _state.asStateFlow()

    fun refresh() {
        val handle = api.handle()
        if (handle == null) {
            _state.value = _state.value.copy(loading = false, failure = engineDownFailure())
            return
        }
        _state.value = _state.value.copy(loading = true, failure = null)
        viewModelScope.launch {
            when (val result = handle.mcp.list()) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    servers = result.value.sortedBy { it.name.lowercase() },
                    loading = false,
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    failure = result,
                )
            }
        }
    }
}
