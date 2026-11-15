package dev.bidzz.opencode.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.ScheduleInfo
import dev.bidzz.opencode.core.api.dto.ScheduleWhenInput
import dev.bidzz.opencode.ui.engineDownFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScheduleUiState(
    val schedules: List<ScheduleInfo> = emptyList(),
    val loading: Boolean = true,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
    /**
     * What the last action (create / pause / resume / delete) got wrong;
     * the screen shows it once as a snackbar, then clears it. A failed
     * list load goes to [failure] instead — the two are never confused,
     * because an action failure must not blank the list.
     */
    val actionFailure: ApiResult.Failure? = null,
    /** Schedule ids with a pause / resume / delete in flight. */
    val busyIds: Set<String> = emptySet(),
    /** True while a create is in flight. */
    val creating: Boolean = false,
)

class ScheduleViewModel(
    private val api: EngineApi,
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduleUiState())
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    private var sessionID: String? = null
    private var loadJob: Job? = null

    fun load(sessionID: String) {
        this.sessionID = sessionID
        val handle = api.handle() ?: run {
            _state.value = ScheduleUiState(loading = false, failure = engineDownFailure())
            return
        }
        _state.value = ScheduleUiState(loading = true)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when (val result = handle.schedules.list(sessionID)) {
                is ApiResult.Success -> _state.value = ScheduleUiState(
                    schedules = result.value,
                    loading = false,
                )
                is ApiResult.Failure -> _state.value = ScheduleUiState(
                    loading = false,
                    failure = result,
                )
            }
        }
    }

    fun refresh() {
        sessionID?.let { load(it) }
    }

    /**
     * Creates a schedule that wakes the session with [prompt].
     *
     * [whenKind] is one of "in" / "at" / "every" / "cron" and [whenValue]
     * its value ("20 minutes", "2026-10-27T09:00", "1 hour",
     * "0 9 * * 1-5"). The list reloads after a successful create.
     */
    fun create(name: String, whenKind: String, whenValue: String, prompt: String) {
        val id = sessionID ?: return
        if (_state.value.creating) return
        val whenInput = when (whenKind) {
            "at" -> ScheduleWhenInput(at = whenValue)
            "every" -> ScheduleWhenInput(every = whenValue)
            "cron" -> ScheduleWhenInput(cron = whenValue)
            else -> ScheduleWhenInput(`in` = whenValue)
        }
        _state.value = _state.value.copy(creating = true, actionFailure = null)
        viewModelScope.launch {
            val handle = api.handle()
            val result = if (handle == null) {
                engineDownFailure()
            } else {
                handle.schedules.create(id, name, whenInput, prompt)
            }
            when (result) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(creating = false)
                    load(id)
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    creating = false,
                    actionFailure = result,
                )
            }
        }
    }

    /** Stops a schedule from firing until it is resumed. */
    fun pause(scheduleID: String) {
        mutate(scheduleID) { handle -> handle.schedules.pause(scheduleID) }
    }

    /** Resumes a paused schedule. */
    fun resume(scheduleID: String) {
        mutate(scheduleID) { handle -> handle.schedules.resume(scheduleID) }
    }

    /** Deletes a schedule. */
    fun delete(scheduleID: String) {
        mutate(scheduleID) { handle -> handle.schedules.delete(scheduleID) }
    }

    /**
     * One per-schedule mutation: guards double taps through
     * [ScheduleUiState.busyIds], reloads quietly on success, reports
     * through `actionFailure` on failure and never blanks the list.
     */
    private fun mutate(
        scheduleID: String,
        call: suspend (EngineApi.Handle) -> ApiResult<Unit>,
    ) {
        val id = sessionID ?: return
        val handle = api.handle()
        if (handle == null) {
            _state.value = _state.value.copy(actionFailure = engineDownFailure())
            return
        }
        if (scheduleID in _state.value.busyIds) return
        _state.value = _state.value.copy(
            busyIds = _state.value.busyIds + scheduleID,
            actionFailure = null,
        )
        viewModelScope.launch {
            when (val result = call(handle)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        busyIds = _state.value.busyIds - scheduleID,
                    )
                    load(id)
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    busyIds = _state.value.busyIds - scheduleID,
                    actionFailure = result,
                )
            }
        }
    }

    /** The screen calls this after showing [ScheduleUiState.actionFailure] once. */
    fun clearActionFailure() {
        _state.value = _state.value.copy(actionFailure = null)
    }
}
