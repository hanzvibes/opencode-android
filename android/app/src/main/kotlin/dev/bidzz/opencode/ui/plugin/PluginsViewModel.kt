package dev.bidzz.opencode.ui.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.ui.engineDownFailure
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.PluginInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class PluginsUiState(
    val plugins: List<PluginInfo> = emptyList(),
    val loading: Boolean = true,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
)

/** The engine's `Plugin.State.status` variants, as a word the UI can switch on. */
enum class PluginStateKind { ACTIVE, FAILED, UNKNOWN }

/**
 * Maps the engine's plugin status string to a kind.
 *
 * Compared case-insensitively, so both `active` and `Active` read the
 * same. Anything unrecognised is UNKNOWN, never a crash: the engine gains
 * statuses without notice.
 */
internal fun PluginInfo.stateKind(): PluginStateKind = when (state.status.lowercase()) {
    "active" -> PluginStateKind.ACTIVE
    "failed" -> PluginStateKind.FAILED
    else -> PluginStateKind.UNKNOWN
}

/** The name shown for a plugin: its id when the engine gives one, else its source. */
internal fun PluginInfo.displayName(): String =
    id?.takeIf { it.isNotBlank() } ?: pluginSourceLabel(source)

/**
 * A human-readable source for a plugin, from its tagged-union `source` object.
 *
 * Verified against the engine fork source at pin 92b2ca0
 * (`packages/schema/src/plugin.ts`): builtin has no target, a package names
 * its `target` (plus `version` when present), a local plugin names its
 * `path`, an sdk plugin has neither. An unknown `type` is shown as-is
 * rather than hidden — a new source kind is information, not an error.
 */
internal fun pluginSourceLabel(source: JsonObject): String {
    val type = (source["type"] as? JsonPrimitive)?.contentOrNull ?: return "?"
    return when (type) {
        "builtin" -> "Built-in"
        "package" -> {
            val target = (source["target"] as? JsonPrimitive)?.contentOrNull
                ?: return "Package"
            val version = (source["version"] as? JsonPrimitive)?.contentOrNull
            if (version.isNullOrBlank()) target else "$target@$version"
        }
        "local" -> (source["path"] as? JsonPrimitive)?.contentOrNull ?: "Local"
        "sdk" -> "SDK"
        else -> type
    }
}

/** Feature flags a plugin declares, as short words for the row. */
internal fun PluginInfo.featureWords(): List<String> = buildList {
    if (features.server == true) add("server")
    if (features.tui == true) add("tui")
    if (features.rpc == true) add("rpc")
    if (features.gui == true) add("gui")
}

/**
 * The engine's server plugins and their status.
 *
 * List-only: checking for updates and updating are writes
 * (`POST /api/plugin/check`, `/api/plugin/update`) the app does not call.
 */
class PluginsViewModel(
    private val api: EngineApi,
) : ViewModel() {

    private val _state = MutableStateFlow(PluginsUiState())
    val state: StateFlow<PluginsUiState> = _state.asStateFlow()

    fun refresh() {
        val handle = api.handle()
        if (handle == null) {
            _state.value = _state.value.copy(loading = false, failure = engineDownFailure())
            return
        }
        _state.value = _state.value.copy(loading = true, failure = null)
        viewModelScope.launch {
            when (val result = handle.plugins.list()) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    plugins = result.value.sortedBy { it.displayName().lowercase() },
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
