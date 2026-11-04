package dev.bidzz.opencode.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.SessionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How long ago something happened, as a unit and a count; the screen words it in the user's language. */
enum class AgeUnit { NOW, MINUTES, HOURS, DAYS, MONTHS }

data class SessionAge(val unit: AgeUnit, val value: Int = 0)

/** A session as the history list presents it. */
data class SessionRow(
    val id: String,
    /** Blank when the engine has not named the session yet; the screen shows a placeholder. */
    val title: String,
    /** Directory the session belongs to. */
    val directory: String?,
    /** Null when the engine has not stamped the session. */
    val age: SessionAge?,
    val cost: String?,
)

data class SessionListUiState(
    val loading: Boolean = true,
    val rows: List<SessionRow> = emptyList(),
    /** What went wrong, as a value; the screen words it in the app’s language. */
    val failure: ApiResult.Failure? = null,
    /** A message with no failure behind it, e.g. the engine never started. */
    val failureKey: Int? = null,
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
            _state.value = _state.value.copy(loading = false, failureKey = R.string.error_engine_not_running)
            return
        }
        val directory = if (scopedToWorkspace) workspace() else null
        _state.value = _state.value.copy(loading = true, failure = null, failureKey = null, filterDirectory = directory)

        viewModelScope.launch {
            when (val result = handle.sessions.list(directory = directory, limit = 50)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        rows = result.value.map { it.toRow() },
                        failure = null, failureKey = null,
                    )
                }
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(loading = false, failure = result)
                }
            }
        }
    }
}

private fun SessionInfo.toRow(): SessionRow = SessionRow(
    id = id,
    title = title.orEmpty().trim(),
    directory = location?.directory,
    age = formatAge(time?.updated ?: time?.created),
    cost = cost?.takeIf { it > 0 }?.let { formatCost(it) },
)

/** Relative age from an epoch-millis timestamp. Coarse on purpose: exact seconds do not help. */
internal fun formatAge(epochMillis: Long?, now: Long = System.currentTimeMillis()): SessionAge? {
    if (epochMillis == null || epochMillis <= 0) return null
    val delta = (now - epochMillis).coerceAtLeast(0)
    val minutes = delta / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> SessionAge(AgeUnit.NOW)
        minutes < 60 -> SessionAge(AgeUnit.MINUTES, minutes.toInt())
        hours < 24 -> SessionAge(AgeUnit.HOURS, hours.toInt())
        days < 30 -> SessionAge(AgeUnit.DAYS, days.toInt())
        else -> SessionAge(AgeUnit.MONTHS, (days / 30).toInt())
    }
}

private fun formatCost(value: Double): String =
    if (value < 0.01) "<$0.01" else String.format(java.util.Locale.US, "$%.2f", value)
