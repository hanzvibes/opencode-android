package dev.bidzz.opencode.ui.websearch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.ui.engineDownFailure
import dev.bidzz.opencode.core.api.dto.WebSearchProvider
import dev.bidzz.opencode.core.api.dto.WebSearchResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How many recent queries the search screen remembers (audit-200 D10, m-658). */
internal const val MAX_RECENT_QUERIES = 8

private const val RECENT_QUERIES_KEY = "websearch_recent_queries"

data class WebSearchUiState(
    val providers: List<WebSearchProvider> = emptyList(),
    /** Null means the engine's configured default provider. */
    val selectedProviderId: String? = null,
    val query: String = "",
    val searching: Boolean = false,
    val response: WebSearchResponse? = null,
    /**
     * The last queries, most recent first (audit-200 D10, m-658): a
     * search used to leave nothing behind, so a repeated query had to
     * be retyped every time. Kept on this device only — in the saved
     * state handle, so it survives process death — and never sent
     * anywhere; the engine stores nothing about a search.
     */
    val recentQueries: List<String> = emptyList(),
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
 * Audit-200 D9 (m-666): clearing the query (the X button) left the old
 * results on screen with no way out — the Search button is disabled for
 * a blank query, so the stale results could not even be replaced. A
 * blank query clears the response (and the failure, for the same
 * reason); any non-blank keystroke keeps the results until the next
 * search replaces them. Pure so the rule is testable without a live
 * engine.
 */
internal fun WebSearchUiState.withQuery(query: String): WebSearchUiState =
    if (query.isBlank()) {
        copy(query = query, response = null, failure = null)
    } else {
        copy(query = query)
    }

/**
 * Audit-200 D10 (m-658): records a finished query as the most recent
 * one — a repeat moves to the front instead of duplicating, and the
 * list is capped so the saved state stays small. Pure so the rule is
 * testable without a live engine.
 */
internal fun WebSearchUiState.withRecentQuery(query: String): WebSearchUiState {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this
    val recent = (listOf(trimmed) + recentQueries.filter { it != trimmed })
        .take(MAX_RECENT_QUERIES)
    return copy(recentQueries = recent)
}

/**
 * Direct web search (mockup group 5).
 *
 * One query at a time through the selected provider (or the engine's
 * configured default). Results are transient — a search is not stored
 * on the engine — and open in the in-app browser. The queries
 * themselves are remembered on this device only (audit-200 D10,
 * m-658), in the saved state handle so they survive process death.
 */
class WebSearchViewModel(
    private val api: EngineApi,
    private val savedStateHandle: SavedStateHandle? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        WebSearchUiState(
            recentQueries = savedStateHandle
                ?.get<ArrayList<String>>(RECENT_QUERIES_KEY)
                ?.take(MAX_RECENT_QUERIES)
                ?: emptyList(),
        ),
    )
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
        _state.value = _state.value.withQuery(query)
    }

    fun setProvider(providerId: String?) {
        _state.value = _state.value.copy(selectedProviderId = providerId)
    }

    fun search() {
        val state = _state.value
        if (state.query.isBlank() || state.searching) return
        val handle = api.handle()
        if (handle == null) {
            _state.value = state.copy(failure = engineDownFailure())
            return
        }
        _state.value = state.copy(searching = true, failure = null)
        viewModelScope.launch {
            when (val result = handle.websearch.query(state.query, state.selectedProviderId)) {
                is ApiResult.Success -> {
                    val updated = _state.value
                        .copy(searching = false, response = result.value)
                        .withRecentQuery(state.query)
                    _state.value = updated
                    savedStateHandle?.set(RECENT_QUERIES_KEY, ArrayList(updated.recentQueries))
                }
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
