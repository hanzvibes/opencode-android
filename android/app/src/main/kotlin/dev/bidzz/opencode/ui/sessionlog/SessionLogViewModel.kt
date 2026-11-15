package dev.bidzz.opencode.ui.sessionlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.SessionLogApi
import dev.bidzz.opencode.core.api.SessionLogEvent
import dev.bidzz.opencode.ui.engineDownFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SessionLogUiState(
    val events: List<SessionLogEvent> = emptyList(),
    /** The `seq` of the `log.synced` marker; null while the read never synced. */
    val syncedSeq: Long? = null,
    val loading: Boolean = true,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
)

class SessionLogViewModel(
    private val api: EngineApi,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionLogUiState())
    val state: StateFlow<SessionLogUiState> = _state.asStateFlow()

    private var sessionID: String? = null
    private var loadJob: Job? = null

    fun load(sessionID: String) {
        this.sessionID = sessionID
        val handle = api.handle() ?: run {
            _state.value = SessionLogUiState(loading = false, failure = engineDownFailure())
            return
        }
        _state.value = SessionLogUiState(loading = true)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when (val result = handle.sessionLog.read(sessionID)) {
                is ApiResult.Success -> _state.value = SessionLogUiState(
                    events = result.value.events,
                    syncedSeq = result.value.syncedSeq,
                    loading = false,
                )
                is ApiResult.Failure -> _state.value = SessionLogUiState(
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

/**
 * One readable line for a log event (audit-200 C17, M-259).
 *
 * The payload shapes vary per event, so the summary reads the most
 * human field it can find: a text field, the first text part of a
 * message-content update, or a usage record's cost and tokens. Anything
 * else falls back to the payload's compact JSON — a log viewer that
 * hides what it cannot name would be worse than a raw line.
 */
internal fun sessionLogSummary(event: SessionLogEvent): String {
    val data = event.data
    (data["text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
        return it.take(SUMMARY_CHARS)
    }
    val content = data["content"] as? JsonArray
    content?.firstNotNullOfOrNull { part ->
        val obj = part as? JsonObject ?: return@firstNotNullOfOrNull null
        if ((obj["type"] as? JsonPrimitive)?.contentOrNull != "text") return@firstNotNullOfOrNull null
        (obj["text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }?.let { return it.take(SUMMARY_CHARS) }
    val cost = (data["cost"] as? JsonPrimitive)?.contentOrNull
    if (cost != null) {
        val tokens = data["tokens"] as? JsonObject
        val total = (tokens?.get("total") as? JsonPrimitive)?.longOrNull
            ?: listOf("input", "output")
                .mapNotNull { key -> (tokens?.get(key) as? JsonPrimitive)?.longOrNull }
                .takeIf { it.isNotEmpty() }
                ?.sum()
        return buildString {
            append("Cost $cost")
            if (total != null) append(" · $total tokens")
        }
    }
    return data.toString().take(SUMMARY_CHARS)
}

/** Wall-clock of a log event as "14:32:05"; null when unknown.
 * Audit-200 D1 (m-554): `Locale.ROOT` — the pattern is digits only, so it
 * must stay ASCII digits whatever the device locale. */
internal fun formatLogTime(createdMs: Long?): String? {
    if (createdMs == null) return null
    val ms = if (createdMs < 1_000_000_000_000L) createdMs * 1_000 else createdMs
    return SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(ms))
}

private const val SUMMARY_CHARS: Int = 160
