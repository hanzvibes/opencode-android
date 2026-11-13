package dev.bidzz.opencode.ui.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.JobInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Kind filter for the job list; null means all kinds. */
enum class JobKindFilter(val apiValue: String?) {
    ALL(null),
    SHELL("shell"),
    SUBAGENT("subagent"),
    IMAGE("image"),
}

/** Status filter for the job list; null means all statuses. */
enum class JobStatusFilter(val apiValue: String?) {
    ALL(null),
    RUNNING("running"),
    COMPLETED("completed"),
    ERROR("error"),
    CANCELLED("cancelled"),
}

data class JobsUiState(
    val jobs: List<JobInfo> = emptyList(),
    val loading: Boolean = true,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
    val kindFilter: JobKindFilter = JobKindFilter.ALL,
    val statusFilter: JobStatusFilter = JobStatusFilter.ALL,
)

/**
 * Background jobs on the engine (mockup group 3).
 *
 * Listing comes from `GET /api/experimental/job`, filtered server-side
 * by kind and status. A job is a shell command, a subagent or an image
 * generation running behind a session; the list shows what is active
 * and what finished, so the user can see work that outlives the turn
 * that started it.
 */
class JobsViewModel : ViewModel() {

    private val _state = MutableStateFlow(JobsUiState())
    val state: StateFlow<JobsUiState> = _state.asStateFlow()

    fun refresh() {
        load(showLoading = true)
    }

    fun setKindFilter(filter: JobKindFilter) {
        if (filter == _state.value.kindFilter) return
        _state.value = _state.value.copy(kindFilter = filter)
        load(showLoading = false)
    }

    fun setStatusFilter(filter: JobStatusFilter) {
        if (filter == _state.value.statusFilter) return
        _state.value = _state.value.copy(statusFilter = filter)
        load(showLoading = false)
    }

    private fun load(showLoading: Boolean) {
        if (showLoading) {
            _state.value = _state.value.copy(loading = true, failure = null)
        }
        viewModelScope.launch {
            val handle = EngineApi.handle()
            if (handle == null) {
                _state.value = _state.value.copy(
                    loading = false,
                    failure = ApiResult.Failure(code = 0, message = "Engine not running"),
                )
                return@launch
            }
            val state = _state.value
            when (val result = handle.jobs.list(kind = state.kindFilter.apiValue, status = state.statusFilter.apiValue)) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    jobs = result.value,
                    loading = false,
                    failure = null,
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    failure = result,
                )
            }
        }
    }
}
