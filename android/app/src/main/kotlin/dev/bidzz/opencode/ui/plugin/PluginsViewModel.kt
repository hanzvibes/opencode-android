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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

data class PluginsUiState(
    val plugins: List<PluginInfo> = emptyList(),
    val loading: Boolean = true,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
    /** True while a check-for-updates is in flight. */
    val checking: Boolean = false,
    /** Package targets with an update in flight. */
    val updatingTargets: Set<String> = emptySet(),
    /**
     * What the last action (check / update) got wrong; the screen shows it
     * once as a snackbar, then clears it. A failed list load goes to
     * [failure] instead — the two are never confused, because an action
     * failure must not blank the list.
     */
    val actionFailure: ApiResult.Failure? = null,
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

/** Whether the engine stamped `outdated: true` on this plugin's source. */
internal fun PluginInfo.isOutdated(): Boolean =
    (source["outdated"] as? JsonPrimitive)?.booleanOrNull == true

/**
 * The package target for a package plugin (e.g. `opencode-anthropic-auth`),
 * or null for builtin / local / sdk plugins — only package plugins can be
 * checked or updated.
 */
internal fun PluginInfo.packageTarget(): String? {
    if ((source["type"] as? JsonPrimitive)?.contentOrNull != "package") return null
    return (source["target"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
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
 * Listing comes from `GET /api/plugin`. Checking for updates
 * (`POST /api/plugin/check`) and updating (`POST /api/plugin/update`) are
 * the writes: check answers the full inventory with `outdated: true`
 * stamped onto the package sources that have an update, so the list is
 * replaced with the answer; update answers 204, so the list is checked
 * again afterwards to refresh the flags. Only package plugins can be
 * checked or updated — builtin, local and sdk plugins never carry
 * `outdated`, so the UI never offers them an update.
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

    /**
     * Checks one package plugin for updates (`target`), or every package
     * plugin when `target` is null. The answer replaces the plugin list
     * with the `outdated` flags stamped in.
     */
    fun checkUpdates(target: String? = null) {
        // Audit-200 B2 (M-184/M-260): a dead engine used to swallow the
        // tap silently — the button spun down and nothing happened.
        // refresh() already reports engine-down through [failure]; an
        // action reports it through [actionFailure], like a failed check.
        val handle = api.handle()
        if (handle == null) {
            _state.value = _state.value.copy(actionFailure = engineDownFailure())
            return
        }
        if (_state.value.checking) return
        _state.value = _state.value.copy(checking = true, actionFailure = null)
        viewModelScope.launch {
            when (val result = handle.plugins.check(target)) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    plugins = result.value.sortedBy { it.displayName().lowercase() },
                    checking = false,
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    checking = false,
                    actionFailure = result,
                )
            }
        }
    }

    /**
     * Updates package plugins by target. On success the inventory is
     * checked again so the `outdated` flags refresh; on failure the
     * update error is reported and no re-check runs.
     */
    fun update(targets: List<String>) {
        // Audit-200 B2 (M-184/M-260): same as checkUpdates.
        val handle = api.handle()
        if (handle == null) {
            _state.value = _state.value.copy(actionFailure = engineDownFailure())
            return
        }
        if (targets.isEmpty() || targets.any { it in _state.value.updatingTargets }) return
        _state.value = _state.value.copy(
            updatingTargets = _state.value.updatingTargets + targets,
            actionFailure = null,
        )
        viewModelScope.launch {
            when (val result = handle.plugins.update(targets)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        updatingTargets = _state.value.updatingTargets - targets.toSet(),
                    )
                    checkUpdates()
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    updatingTargets = _state.value.updatingTargets - targets.toSet(),
                    actionFailure = result,
                )
            }
        }
    }

    /** The screen calls this after showing [PluginsUiState.actionFailure] once. */
    fun clearActionFailure() {
        _state.value = _state.value.copy(actionFailure = null)
    }
}
