package dev.bidzz.opencode.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.UsageApi
import dev.bidzz.opencode.ui.engineDownFailure
import dev.bidzz.opencode.core.api.dto.UsageBudget
import dev.bidzz.opencode.core.api.dto.UsageLimitsInfo
import dev.bidzz.opencode.core.api.dto.UsageMeter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class UsageLimitsUiState(
    val info: UsageLimitsInfo? = null,
    val loading: Boolean = true,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
)

/** A Go meter's used share, 0–1, clamped: the engine promises 0–100. */
internal fun UsageMeter.fraction(): Float =
    (percent / 100.0).toFloat().coerceIn(0f, 1f)

/** A budget's spent share, 0–1; a zero limit means nothing is spent against it. */
internal fun UsageBudget.spentFraction(): Float =
    if (limit > 0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f

/** A reset instant as a local calendar date (2026-12-01); null hides the row. */
internal fun formatResetDate(millis: Long?): String? {
    if (millis == null) return null
    return runCatching {
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }.getOrNull()
}

/**
 * Usage limits (`GET /api/experimental/usage/limits`).
 *
 * Read-only. A failure and a null answer are DIFFERENT states: the endpoint
 * is experimental, and a workspace that is not signed in to OpenCode Console
 * has no limits to show — but a failed load must say so instead of accusing
 * the user of not being signed in (audit-100 64-F14; the old fail-silent
 * design hid real engine failures behind the "not signed in" text).
 */
class UsageLimitsViewModel(
    private val api: EngineApi,
) : ViewModel() {

    private val _state = MutableStateFlow(UsageLimitsUiState())
    val state: StateFlow<UsageLimitsUiState> = _state.asStateFlow()

    fun refresh() {
        val handle = api.handle() ?: run {
            _state.value = UsageLimitsUiState(loading = false, failure = engineDownFailure())
            return
        }
        _state.value = UsageLimitsUiState(loading = true)
        viewModelScope.launch {
            when (val result = UsageApi(handle.client).limits()) {
                is ApiResult.Success -> _state.value = UsageLimitsUiState(
                    info = result.value,
                    loading = false,
                )
                is ApiResult.Failure -> _state.value = UsageLimitsUiState(
                    info = null,
                    loading = false,
                    failure = result,
                )
            }
        }
    }
}
