package dev.bidzz.opencode.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.ConfigEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ConfigUiState(
    val entries: List<ConfigEntry> = emptyList(),
    val loading: Boolean = true,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
)

/**
 * The model a config document selects, as one line of text.
 *
 * `ConfigModel.Selection` may be a plain string (`"anthropic/claude-sonnet-4"`)
 * or an object (`{ modelID, providerID, ... }`); a string reads as-is, an
 * object reads as its compact JSON, and anything else hides — a guessed
 * rendering of a shape the app does not know would be a lie.
 */
internal fun configModelLabel(model: JsonElement?): String? = when (model) {
    null -> null
    is JsonPrimitive -> model.contentOrNull?.takeIf { it.isNotBlank() }
    else -> model.toString().takeIf { it.isNotBlank() }
}

/** The path shown for an entry: its file, or its searched directory. */
internal fun ConfigEntry.displayPath(): String? = path?.takeIf { it.isNotBlank() }

/**
 * The engine's configuration documents, lowest to highest priority.
 *
 * List-only: editing is `PATCH /api/experimental/config`, an experimental
 * endpoint the app does not call.
 */
class ConfigViewModel(
    private val api: EngineApi,
) : ViewModel() {

    private val _state = MutableStateFlow(ConfigUiState())
    val state: StateFlow<ConfigUiState> = _state.asStateFlow()

    fun refresh() {
        val handle = api.handle()
        if (handle == null) {
            _state.value = _state.value.copy(loading = false)
            return
        }
        _state.value = _state.value.copy(loading = true, failure = null)
        viewModelScope.launch {
            when (val result = handle.config.list()) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    entries = result.value,
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
