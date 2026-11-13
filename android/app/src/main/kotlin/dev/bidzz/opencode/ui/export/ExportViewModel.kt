package dev.bidzz.opencode.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.ui.engineDownFailure
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.SessionApi
import dev.bidzz.opencode.core.api.SessionExport
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ExportUiState(
    val export: SessionExport? = null,
    val loading: Boolean = true,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
)

/** The session title from the export info, falling back to its id. */
internal fun SessionExport.displayTitle(): String? {
    val info = data.info
    return (info["title"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: (info["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

/** Number of messages in the exported transcript. */
internal fun SessionExport.messageCount(): Int = data.messages.size

/**
 * One session's complete transcript (`GET /api/experimental/session/{id}/export`).
 *
 * Read-only: the export is shown as a summary and copied as verbatim
 * JSON. Nothing is written back — there is no import UI in this batch,
 * and the raw bytes are never re-encoded, so the copy is exactly what
 * the engine sent.
 */
class ExportViewModel(
    private val api: EngineApi,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    private var sessionID: String? = null
    private var loadJob: Job? = null

    fun load(sessionID: String) {
        this.sessionID = sessionID
        val handle = api.handle() ?: run {
            _state.value = ExportUiState(loading = false, failure = engineDownFailure())
            return
        }
        _state.value = ExportUiState(loading = true)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when (val result = SessionApi(handle.client).export(sessionID)) {
                is ApiResult.Success -> _state.value = ExportUiState(
                    export = result.value,
                    loading = false,
                )
                is ApiResult.Failure -> _state.value = ExportUiState(
                    loading = false,
                    failure = result,
                )
            }
        }
    }

    fun refresh() {
        sessionID?.let { load(it) }
    }
}
