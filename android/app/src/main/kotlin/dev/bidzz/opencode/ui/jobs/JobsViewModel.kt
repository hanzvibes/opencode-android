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
class JobsViewModel(private val api: EngineApi) : ViewModel() {

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
        // Audit-200 B1 (M-289): the filters are captured now, not read
        // inside the coroutine — a slow load for the filter the user
        // already left must not land after the load for the new one.
        val kind = _state.value.kindFilter
        val status = _state.value.statusFilter
        viewModelScope.launch {
            val handle = api.handle()
            if (handle == null) {
                if (isCurrentJobsLoad(kind, status, _state.value)) {
                    _state.value = _state.value.copy(
                        loading = false,
                        failure = ApiResult.Failure(code = 0, message = "Engine not running"),
                    )
                }
                return@launch
            }
            when (val result = handle.jobs.list(kind = kind.apiValue, status = status.apiValue)) {
                is ApiResult.Success -> {
                    if (isCurrentJobsLoad(kind, status, _state.value)) {
                        _state.value = _state.value.copy(
                            jobs = result.value,
                            loading = false,
                            failure = null,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    if (isCurrentJobsLoad(kind, status, _state.value)) {
                        _state.value = _state.value.copy(
                            loading = false,
                            failure = result,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A load started for [kind]/[status] still owns the list when the
 * current filters match (audit-200 B1, M-289). Pure, so the rule is
 * unit-tested here rather than through the view model, whose scope
 * needs a Main dispatcher the JVM tests do not have.
 */
internal fun isCurrentJobsLoad(
    kind: JobKindFilter,
    status: JobStatusFilter,
    current: JobsUiState,
): Boolean = current.kindFilter == kind && current.statusFilter == status
