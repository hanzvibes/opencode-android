package dev.bidzz.opencode.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.api.describe
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.IntegrationInfo
import dev.bidzz.opencode.engine.EngineConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProvidersUiState(
    val integrations: List<IntegrationInfo> = emptyList(),
    /** Providers the app wrote to the engine's config file, not from its catalog. */
    val customProviders: List<EngineConfigStore.CustomProvider> = emptyList(),
    val loading: Boolean = false,
    /** Provider currently being saved, so only its row shows progress. */
    val busyFor: String? = null,
    val error: String? = null,
    /** Name of the provider most recently connected, for a transient confirmation. */
    val connected: String? = null,
)

/**
 * Lists the engine's providers and stores credentials.
 *
 * Catalog providers go over `POST /api/credential`, which persists them in the engine's
 * own database. The app never writes `auth.json` for this: that file is legacy and the
 * migration importing it has already run on any existing install.
 *
 * Providers outside the catalog — an OpenAI-compatible endpoint with its own base URL —
 * have no credential endpoint at all, so those are written into the engine's config file
 * and need a restart before the engine sees them.
 */
class ProvidersViewModel(
    private val api: EngineApi,
    private val configStore: EngineConfigStore,
    private val restartEngine: () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(ProvidersUiState())
    val state: StateFlow<ProvidersUiState> = _state.asStateFlow()

    fun refresh() {
        // Read the config first and independently: it is on disk, so it is worth showing
        // even while the engine is down.
        _state.value = _state.value.copy(
            customProviders = runCatching { configStore.customProviders() }.getOrDefault(emptyList()),
        )
        val h = api.handle() ?: return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = h.integrations.list()) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    integrations = result.value.sortedWith(
                        compareBy<IntegrationInfo> { !it.connected }.thenBy { it.name.lowercase() },
                    ),
                    loading = false,
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    error = result.describe(),
                )
            }
        }
    }

    /** Stores [key] for a catalog provider and re-reads so its row updates. */
    fun saveKey(integrationID: String, key: String) {
        if (key.isBlank()) return
        val h = api.handle() ?: return
        _state.value = _state.value.copy(busyFor = integrationID, error = null, connected = null)
        viewModelScope.launch {
            when (val result = h.credentialStore.createKey(integrationID, key.trim())) {
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    busyFor = null,
                    error = result.describe(),
                )
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        busyFor = null,
                        connected = _state.value.integrations.firstOrNull { it.id == integrationID }?.name,
                    )
                    // Re-read so the row reflects the connection the engine just created.
                    h.integrations.list().let { r ->
                        if (r is ApiResult.Success) {
                            _state.value = _state.value.copy(integrations = r.value)
                        }
                    }
                }
            }
        }
    }

    /**
     * Writes a custom OpenAI-compatible provider, then restarts the engine.
     *
     * The restart is not optional: providers are resolved at startup, so without it the
     * form appears to succeed while the model never reaches the picker.
     */
    fun saveCustomProvider(name: String, baseURL: String, apiKey: String, modelsText: String) {
        val trimmedName = name.trim()
        val id = EngineConfigStore.slug(trimmedName)
        val models = EngineConfigStore.parseModels(modelsText)
        _state.value = _state.value.copy(busyFor = id, error = null, connected = null)

        val outcome = runCatching {
            configStore.putOpenAICompatible(id, trimmedName, baseURL.trim(), apiKey.trim(), models)
        }
        val written = outcome.getOrNull()
        if (written == null) {
            _state.value = _state.value.copy(
                busyFor = null,
                error = outcome.exceptionOrNull()?.message ?: "Could not write the config file",
            )
            return
        }
        _state.value = _state.value.copy(
            busyFor = null,
            connected = trimmedName,
            error = if (written is EngineConfigStore.WriteResult.Shadowed) {
                "Saved, but an `opencode.jsonc` beside it takes precedence — the engine " +
                    "will read that file instead of this one."
            } else {
                null
            },
        )
        refresh()
        restartEngine()
    }
}
