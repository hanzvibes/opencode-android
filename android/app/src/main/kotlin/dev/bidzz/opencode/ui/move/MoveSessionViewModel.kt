package dev.bidzz.opencode.ui.move

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.ui.engineDownFailure
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.SessionApi
import dev.bidzz.opencode.core.api.dto.ProjectInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MoveSessionUiState(
    val projects: List<ProjectInfo> = emptyList(),
    val currentProjectID: String? = null,
    val selectedProjectID: String? = null,
    val loading: Boolean = true,
    val moving: Boolean = false,
    val moved: Boolean = false,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
)

/** The name shown for a project: its name when the engine gives one, else its path. */
internal fun ProjectInfo.displayName(): String =
    name?.takeIf { it.isNotBlank() } ?: canonical

/**
 * Moves one session to another project (`POST /api/session/{id}/move`).
 *
 * The destination is picked from the engine's project list
 * (`GET /api/project`, bare array); the move itself only sends the
 * chosen project's directory — the engine resolves the project from it
 * (see `MoveSessionRequest`). The session's own project is shown and
 * cannot be picked: moving there would be a no-op the engine would
 * still accept. After a successful move the caller must refresh the
 * session list: the session now belongs to another project.
 */
class MoveSessionViewModel(
    private val api: EngineApi,
) : ViewModel() {

    private val _state = MutableStateFlow(MoveSessionUiState())
    val state: StateFlow<MoveSessionUiState> = _state.asStateFlow()

    private var sessionID: String? = null
    private var loadJob: Job? = null

    fun load(sessionID: String) {
        this.sessionID = sessionID
        val handle = api.handle() ?: run {
            // Same keep-the-list rule as the fetch below (M-264): an
            // engine-down retry reports the failure without blanking
            // the projects and the selection the operator already made.
            _state.value = _state.value.copy(loading = false, failure = engineDownFailure())
            return
        }
        // Audit-200 C17 (M-264): a reload keeps the list it already has —
        // the old full reset blanked the projects (and the selection) on
        // every retry, so the operator re-picked the target after each
        // failure. Only the loading flag and the failure are replaced.
        _state.value = _state.value.copy(loading = true, failure = null)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val session = when (val result = SessionApi(handle.client).get(sessionID)) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(loading = false, failure = result)
                    return@launch
                }
            }
            when (val result = handle.projects.list()) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    projects = result.value.sortedBy { it.displayName().lowercase() },
                    currentProjectID = session.projectID,
                    loading = false,
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    // The session was already read, so its project id
                    // survives a failed project list — the current
                    // project stays marked instead of vanishing.
                    currentProjectID = session.projectID,
                    loading = false,
                    failure = result,
                )
            }
        }
    }

    fun select(projectID: String) {
        if (projectID == _state.value.currentProjectID) return
        _state.value = _state.value.copy(selectedProjectID = projectID, failure = null)
    }

    fun move() {
        val sessionID = sessionID ?: return
        val target = _state.value.projects.firstOrNull { it.id == _state.value.selectedProjectID }
            ?: return
        // Audit-200 B2 (M-184): a dead engine used to swallow the tap
        // silently — the screen already renders [failure] as its error
        // state, so reporting engine-down here is enough to show it.
        val handle = api.handle()
        if (handle == null) {
            _state.value = _state.value.copy(failure = engineDownFailure())
            return
        }
        _state.value = _state.value.copy(moving = true, failure = null)
        viewModelScope.launch {
            when (val result = SessionApi(handle.client).move(sessionID, target.canonical)) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    moving = false,
                    moved = true,
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    moving = false,
                    failure = result,
                )
            }
        }
    }

    fun refresh() {
        sessionID?.let { load(it) }
    }
}
