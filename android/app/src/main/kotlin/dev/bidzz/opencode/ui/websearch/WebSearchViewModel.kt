package dev.bidzz.opencode.ui.websearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.WebSearchProvider
import dev.bidzz.opencode.core.api.dto.WebSearchResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WebSearchUiState(
    val providers: List<WebSearchProvider> = emptyList(),
    /** Null means the engine's configured default provider. */
    val selectedProviderId: String? = null,
    val query: String = "",
    val searching: Boolean = false,
    val response: WebSearchResponse? = null,
    /**
     * What the last search got wrong; the screen shows it as a
     * persistent error with a retry (audit-200 B1, M-183/M-310: a
     * snackbar that clears itself left the old results on screen as if
     * they answered the query that just failed, and once it dismissed
     * the screen claimed it had never searched).
     */
    val failure: ApiResult.Failure? = null,
)

/**
 * Direct web search (mockup group 5).
 *
 * One query at a time through the selected provider (or the engine's
 * configured default). Results are transient — a search is not stored
 * on the engine — and open in the in-app browser.
 */
class WebSearchViewModel(private val api: EngineApi) : ViewModel() {

    private val _state = MutableStateFlow(WebSearchUiState())
    val state: StateFlow<WebSearchUiState> = _state.asStateFlow()

    fun refreshProviders() {
        viewModelScope.launch {
            val handle = api.handle() ?: return@launch
            when (val result = handle.websearch.providers()) {
                is ApiResult.Success -> _state.value = _state.value.copy(providers = result.value)
                is ApiResult.Failure -> Unit // Providers are context only; a failed list hides the picker.
            }
        }
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    fun setProvider(providerId: String?) {
        _state.value = _state.value.copy(selectedProviderId = providerId)
    }

    fun search() {
        val state = _state.value
        if (state.query.isBlank() || state.searching) return
        val handle = api.handle()
        if (handle == null) {
            _state.value = state.copy(failure = ApiResult.Failure(code = 0, message = "Engine not running"))
            return
        }
        _state.value = state.copy(searching = true, failure = null)
        viewModelScope.launch {
            when (val result = handle.websearch.query(state.query, state.selectedProviderId)) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    searching = false,
                    response = result.value,
                )
                // Audit-200 B1 (M-310): a failed search clears the
                // previous response — leaving it on screen made it look
                // like the answer to the query that just failed.
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    searching = false,
                    response = null,
                    failure = result,
                )
            }
        }
    }

}
