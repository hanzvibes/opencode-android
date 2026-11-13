package dev.bidzz.opencode.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.ui.engineDownFailure
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.InboxApi
import dev.bidzz.opencode.core.api.dto.InboxItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class InboxUiState(
    val items: List<InboxItem> = emptyList(),
    val loading: Boolean = true,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
)

/**
 * The one-line summary of an inbox item, from its variant payload.
 *
 * The payload is a [JsonObject] because the four variants carry different
 * fields (see `InboxItem`): a user item shows its prompt text, a synthetic
 * item its text or description, a move item its destination project or
 * directory, and a compaction item has no payload to show — null there is
 * a fact, not a missing string, and the screen falls back to the type word.
 * An attached-files count is appended for user items that carry files.
 */
internal fun InboxItem.summaryText(): String? = when (type.lowercase()) {
    "user" -> payload.stringField("text")
    "synthetic" -> payload.stringField("text") ?: payload.stringField("description")
    "move" -> payload.stringField("projectID")
        ?: (payload["location"] as? JsonObject)?.stringField("directory")
    else -> null
}?.takeIf { it.isNotBlank() }

/** Number of files attached to a user inbox item, 0 for other variants. */
internal fun InboxItem.fileCount(): Int =
    if (type.lowercase() == "user") (payload["files"] as? JsonArray)?.size ?: 0 else 0

private fun JsonObject.stringField(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

/**
 * Queued work for one session (`GET /api/session/{id}/inbox`).
 *
 * List-only: cancelling an item and changing its delivery are writes the
 * app does not call. The inbox is ordered by enqueue sequence by the
 * engine; the app keeps that order.
 */
class InboxViewModel(
    private val api: EngineApi,
) : ViewModel() {

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    private var sessionID: String? = null
    private var loadJob: Job? = null

    fun load(sessionID: String) {
        this.sessionID = sessionID
        val handle = api.handle() ?: run {
            _state.value = InboxUiState(loading = false, failure = engineDownFailure())
            return
        }
        _state.value = InboxUiState(loading = true)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when (val result = InboxApi(handle.client).list(sessionID)) {
                is ApiResult.Success -> _state.value = InboxUiState(
                    items = result.value,
                    loading = false,
                )
                is ApiResult.Failure -> _state.value = InboxUiState(
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
