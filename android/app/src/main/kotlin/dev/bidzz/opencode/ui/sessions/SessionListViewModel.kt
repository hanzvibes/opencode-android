package dev.bidzz.opencode.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.api.describe
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.SessionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A session as the history list presents it. */
data class SessionRow(
    val id: String,
    val title: String,
    /** Directory the session belongs to. */
    val directory: String?,
    /** Human-readable age, e.g. "2h ago". */
    val age: String,
    val cost: String?,
)

data class SessionListUiState(
    val loading: Boolean = true,
    val rows: List<SessionRow> = emptyList(),
    val error: String? = null,
    /** Only sessions for this directory, when filtering. */
    val filterDirectory: String? = null,
)

/**
 * Session history.
 *
 * Loads on demand rather than caching: the engine owns the list, sessions can be
 * created from other clients, and a stale cache would show conversations that no
 * longer exist. A refresh is one cheap local request.
 */
class SessionListViewModel(
    private val api: EngineApi,
    private val workspace: () -> String,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionListUiState())
    val state: StateFlow<SessionListUiState> = _state.asStateFlow()

    /** @param scopedToWorkspace when true, only the current project's sessions. */
    fun load(scopedToWorkspace: Boolean) {
        val handle = api.handle() ?: run {
            _state.value = _state.value.copy(loading = false, error = "Engine is not running")
            return
        }
        val directory = if (scopedToWorkspace) workspace() else null
        _state.value = _state.value.copy(loading = true, error = null, filterDirectory = directory)

        viewModelScope.launch {
            when (val result = handle.sessions.list(directory = directory, limit = 50)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        rows = result.value.map { it.toRow() },
                        error = null,
                    )
                }
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(loading = false, error = result.describe())
                }
            }
        }
    }
}

private fun SessionInfo.toRow(): SessionRow = SessionRow(
    id = id,
    title = title?.takeIf { it.isNotBlank() } ?: "Untitled session",
    directory = location?.directory,
    age = formatAge(time?.updated ?: time?.created),
    cost = cost?.takeIf { it > 0 }?.let { formatCost(it) },
)

/** Relative age from an epoch-millis timestamp. Coarse on purpose: exact seconds do not help. */
internal fun formatAge(epochMillis: Long?, now: Long = System.currentTimeMillis()): String {
    if (epochMillis == null || epochMillis <= 0) return ""
    val delta = (now - epochMillis).coerceAtLeast(0)
    val minutes = delta / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 30 -> "${days}d ago"
        else -> "${days / 30}mo ago"
    }
}

private fun formatCost(value: Double): String =
    if (value < 0.01) "<$0.01" else String.format(java.util.Locale.US, "$%.2f", value)
