package dev.bidzz.opencode.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.ui.engineDownFailure
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.StatsApi
import dev.bidzz.opencode.core.api.dto.SessionStatsInfo
import dev.bidzz.opencode.core.api.dto.TokenUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class StatsUiState(
    val stats: SessionStatsInfo? = null,
    val loading: Boolean = true,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
)

/**
 * Total tokens for a stats entry, on the same convention as the turn
 * footer and the session list: input + output + reasoning. Cache
 * read/write is not added — it would double-count tokens the model
 * already saw.
 *
 * Null when the engine reported no usage at all; a reported zero stays
 * zero (audit-100 Fase 6a, 67-F5) — the old non-null `Long` forced the
 * screen to print "0" for missing data, a third zero convention next to
 * the list (hide) and the footer (show 0).
 */
internal fun TokenUsage?.totalTokens(): Long? {
    if (this == null) return null
    return (input ?: 0L) + (output ?: 0L) + (reasoning ?: 0L)
}

/** Cost as a short dollar amount; zero reads as $0.00, not as under a cent. */
internal fun formatStatsCost(value: Double): String = when {
    value == 0.0 -> "$0.00"
    value < 0.01 -> "<$0.01"
    else -> "$" + String.format(java.util.Locale.US, "%.2f", value)
}

/**
 * The model a stats row covers, as one line of text.
 *
 * `SessionStats.ModelUsage.model` is a `Model.Ref`
 * (`{ providerID, modelID }`); a plain string reads as-is, an object
 * reads its `modelID` (falling back to `id`), and anything else hides —
 * a guessed rendering of a shape the app does not know would be a lie.
 */
internal fun statsModelLabel(model: JsonElement?): String? = when (model) {
    null -> null
    is JsonPrimitive -> model.contentOrNull?.takeIf { it.isNotBlank() }
    is JsonObject -> (model["modelID"] as? JsonPrimitive)?.contentOrNull
        ?: (model["id"] as? JsonPrimitive)?.contentOrNull
    else -> model.toString().takeIf { it.isNotBlank() }
}

/**
 * Aggregate session statistics (`GET /api/experimental/session/stats`).
 *
 * Read-only, for the engine's default range. The endpoint is
 * experimental: a failure leaves the screen on its empty state instead
 * of crashing, because an experimental endpoint may change shape.
 */
class StatsViewModel(
    private val api: EngineApi,
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    fun refresh() {
        val handle = api.handle() ?: run {
            _state.value = StatsUiState(loading = false, failure = engineDownFailure())
            return
        }
        _state.value = StatsUiState(loading = true)
        viewModelScope.launch {
            when (val result = StatsApi(handle.client).stats()) {
                is ApiResult.Success -> _state.value = StatsUiState(
                    stats = result.value,
                    loading = false,
                )
                is ApiResult.Failure -> _state.value = StatsUiState(
                    loading = false,
                    failure = result,
                )
            }
        }
    }
}
