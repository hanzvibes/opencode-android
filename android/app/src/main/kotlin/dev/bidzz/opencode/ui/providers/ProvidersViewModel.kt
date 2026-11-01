package dev.bidzz.opencode.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.api.describe
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.IntegrationInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProvidersUiState(
    val integrations: List<IntegrationInfo> = emptyList(),
    val loading: Boolean = false,
    /** Integration currently being saved, so only its row shows progress. */
    val busyFor: String? = null,
    val error: String? = null,
    /** Name of the provider most recently connected, for a transient confirmation. */
    val connected: String? = null,
)

/**
 * Lists the engine's providers and stores API keys.
 *
 * Credentials go to the engine over `POST /api/credential`, which persists them in its
 * own database. The app never writes a config file for this: `auth.json` is legacy and
 * the migration that reads it has already run on any existing install.
 */
class ProvidersViewModel(private val api: EngineApi) : ViewModel() {

    private val _state = MutableStateFlow(ProvidersUiState())
    val state: StateFlow<ProvidersUiState> = _state.asStateFlow()

    fun refresh() {
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

    /** Stores [key] for a provider and re-reads the catalog so its state updates. */
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
}
