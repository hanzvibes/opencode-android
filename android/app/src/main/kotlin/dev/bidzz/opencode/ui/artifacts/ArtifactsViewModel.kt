package dev.bidzz.opencode.ui.artifacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.ArtifactInfo
import dev.bidzz.opencode.core.api.dto.ArtifactProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArtifactsUiState(
    val artifacts: List<ArtifactInfo> = emptyList(),
    val providers: List<ArtifactProvider> = emptyList(),
    val loading: Boolean = true,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
    /** IDs with a delete in flight. */
    val deletingIds: Set<String> = emptySet(),
    /**
     * What the last delete got wrong; the screen shows it once as a
     * snackbar, then clears it. A failed list load goes to [failure]
     * instead — the two are never confused, because an action failure
     * must not blank the list.
     */
    val actionFailure: ApiResult.Failure? = null,
)

/** True when the list failed because artifacts are disabled on the engine (HTTP 403). */
internal fun ApiResult.Failure.isArtifactsDisabled(): Boolean = code == 403

/**
 * Published artifacts on the engine (mockup group 3).
 *
 * Listing comes from `GET /api/experimental/artifacts` (newest first)
 * and providers from `GET /api/experimental/artifacts/providers`.
 * Delete is `DELETE /api/experimental/artifacts/{id}` (204). The
 * view URL (`Artifact.Info.url`, a relative path) is opened in the
 * in-app browser by the screen, with the engine credentials.
 *
 * Artifacts are experimental and need `experimental.artifacts` in the
 * Location config (default off in releases). A 403 load failure is
 * therefore a first-class state the screen explains, not a bare error:
 * the app cannot enable the flag itself (`ConfigApi` is list-only).
 */
class ArtifactsViewModel(private val api: EngineApi) : ViewModel() {

    private val _state = MutableStateFlow(ArtifactsUiState())
    val state: StateFlow<ArtifactsUiState> = _state.asStateFlow()

    fun refresh() {
        load(showLoading = true)
    }

    private fun load(showLoading: Boolean) {
        if (showLoading) {
            _state.value = _state.value.copy(loading = true, failure = null)
        }
        viewModelScope.launch {
            val handle = api.handle()
            if (handle == null) {
                _state.value = _state.value.copy(
                    loading = false,
                    failure = ApiResult.Failure(code = 0, message = "Engine not running"),
                )
                return@launch
            }
            when (val providers = handle.artifacts.providers()) {
                is ApiResult.Success -> _state.value = _state.value.copy(providers = providers.value)
                is ApiResult.Failure -> Unit // Providers are context only; the list is the screen.
            }
            when (val result = handle.artifacts.list()) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    artifacts = result.value,
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

    fun delete(id: String) {
        if (id in _state.value.deletingIds) return
        _state.value = _state.value.copy(deletingIds = _state.value.deletingIds + id)
        viewModelScope.launch {
            val handle = api.handle()
            val result = handle?.artifacts?.delete(id)
            if (result is ApiResult.Failure) {
                _state.value = _state.value.copy(
                    deletingIds = _state.value.deletingIds - id,
                    actionFailure = result,
                )
            } else {
                _state.value = _state.value.copy(deletingIds = _state.value.deletingIds - id)
                load(showLoading = false)
            }
        }
    }

    fun clearActionFailure() {
        _state.value = _state.value.copy(actionFailure = null)
    }
}
