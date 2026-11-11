package dev.bidzz.opencode.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.SessionInfo
import dev.bidzz.opencode.core.api.dto.TokenUsage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How long ago something happened, as a unit and a count; the screen words it in the user's language. */
enum class AgeUnit { NOW, MINUTES, HOURS, DAYS, MONTHS }

data class SessionAge(val unit: AgeUnit, val value: Int = 0)

/** A session as the history list presents it. */
data class SessionRow(
    val id: String,
    /** Blank when the engine has not named the session yet; the screen shows a placeholder. */
    val title: String,
    /** Directory the session belongs to. */
    val directory: String?,
    /** Null when the engine has not stamped the session. */
    val age: SessionAge?,
    val cost: String?,
    val timestamp: Long? = null,
    val isPinned: Boolean = false,
    /** Total tokens the session used (input + output + reasoning), or null when unknown. */
    val tokenTotal: Long? = null,
)

data class SessionListUiState(
    val loading: Boolean = true,
    val rows: List<SessionRow> = emptyList(),
    /** What went wrong, as a value; the screen words it in the app’s language. */
    val failure: ApiResult.Failure? = null,
    /** A message with no failure behind it, e.g. the engine never started. */
    val failureKey: Int? = null,
    /** Only sessions for this directory, when filtering. */
    val filterDirectory: String? = null,
    /** Per-session preview/model/status (Phase 2, finding 21), filled by
     * [SessionListViewModel.loadDetails]. A session with no entry yet shows
     * title-only until its lookup lands. */
    val details: Map<String, SessionDetail> = emptyMap(),
)

/**
 * Session history.
 *
 * Loads on demand rather than caching: the engine owns the list, sessions can be
 * created from other clients, and a stale cache would show conversations that no
 * longer exist. A refresh is one cheap local request.
 */
class SessionListViewModel(
    private val api: EngineApi,
    private val workspace: () -> String,
    private val pinnedStore: PinnedSessionStore? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionListUiState())
    val state: StateFlow<SessionListUiState> = _state.asStateFlow()

    /** In-flight detail lookup, cancelled when a fresh list loads. */
    private var detailsJob: Job? = null

    /** In-flight list load, cancelled when a fresh list loads. */
    private var loadJob: Job? = null

    /**
     * Increments on every load(): a load whose network call was already in
     * flight when a newer load started must drop its result instead of
     * overwriting the newer list (item 26). Cancellation alone does not
     * cover it — a blocked HTTP call returns after its job was cancelled,
     * and the code after it still runs.
     */
    private var loadGeneration = 0

    /** @param scopedToWorkspace when true, only the current project's sessions. */
    fun load(scopedToWorkspace: Boolean) {
        val handle = api.handle() ?: run {
            _state.value = _state.value.copy(loading = false, failureKey = R.string.error_engine_not_running)
            return
        }
        val directory = if (scopedToWorkspace) workspace() else null
        val generation = ++loadGeneration
        detailsJob?.cancel()
        loadJob?.cancel()
        _state.value = _state.value.copy(loading = true, failure = null, failureKey = null, filterDirectory = directory, details = emptyMap())

        loadJob = viewModelScope.launch {
            when (val result = handle.sessions.list(directory = directory, limit = 50)) {
                is ApiResult.Success -> {
                    if (generation != loadGeneration) return@launch
                    val pinned = pinnedStore?.getPinnedIds().orEmpty()
                    _state.value = _state.value.copy(
                        loading = false,
                        rows = result.value.map { it.toRow(isPinned = pinned.contains(it.id)) },
                        failure = null, failureKey = null,
                    )
                    loadDetails(result.value, generation)
                }
                is ApiResult.Failure -> {
                    if (generation != loadGeneration) return@launch
                    _state.value = _state.value.copy(loading = false, failure = result)
                }
            }
        }
    }

    /**
     * Fills [SessionListUiState.details] (Phase 2, finding 21).
     *
     * The list endpoint returns no transcript, so the preview comes from the
     * messages endpoint and the model/outcome from the [SessionInfo] already
     * in hand — no extra call for those two. The preview is the session's
     * first user message; that is the oldest entry, so the full history is
     * fetched (no limit), newest-first from the engine and reversed here.
     * Lookups run sequentially in the background and publish incrementally,
     * so rows fill in as their data lands. A failed lookup leaves that
     * session's model/status in place with no preview rather than failing
     * the whole list — and a session whose history is genuinely empty has
     * no preview at all, which the row already handles.
     */
    private fun loadDetails(infos: List<SessionInfo>, generation: Int) {
        detailsJob?.cancel()
        detailsJob = viewModelScope.launch {
            val handle = api.handle() ?: return@launch
            val filled = mutableMapOf<String, SessionDetail>()
            for (info in infos) {
                val modelLabel = info.model?.let { "${it.providerID}/${it.id}" }
                when (val result = handle.sessions.messages(info.id)) {
                    is ApiResult.Success -> {
                        val preview = result.value.reversed()
                            .firstOrNull { it.isUser }
                            ?.text?.trim()
                            ?.takeIf { it.isNotEmpty() }
                        filled[info.id] = SessionDetail(
                            preview = preview,
                            model = modelLabel,
                            status = info.outcome,
                        )
                    }
                    is ApiResult.Failure -> {
                        filled[info.id] = SessionDetail(
                            preview = null,
                            model = modelLabel,
                            status = info.outcome,
                        )
                    }
                }
                // A newer load replaced the rows while this lookup was in
                // flight: its previews belong to the old list (item 26).
                if (generation != loadGeneration) return@launch
                _state.value = _state.value.copy(details = filled.toMap())
            }
        }
    }

    /** Toggles the pinned status of a session. */
    fun togglePin(sessionID: String) {
        val pinned = pinnedStore?.togglePin(sessionID) ?: false
        _state.value = _state.value.copy(
            rows = _state.value.rows.map {
                if (it.id == sessionID) it.copy(isPinned = pinned) else it
            }
        )
    }

    /** Renames a session. */
    fun rename(sessionID: String, newTitle: String, scopedToWorkspace: Boolean = true) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        _state.value = _state.value.copy(
            rows = _state.value.rows.map {
                if (it.id == sessionID) it.copy(title = trimmed) else it
            }
        )
        val handle = api.handle() ?: return
        viewModelScope.launch {
            handle.sessions.rename(sessionID, trimmed)
            load(scopedToWorkspace)
        }
    }

    /** Deletes a session. */
    fun delete(sessionID: String, scopedToWorkspace: Boolean = true) {
        pinnedStore?.remove(sessionID)
        _state.value = _state.value.copy(
            rows = _state.value.rows.filter { it.id != sessionID }
        )
        val handle = api.handle() ?: return
        viewModelScope.launch {
            handle.sessions.delete(sessionID)
            load(scopedToWorkspace)
        }
    }
}

private fun SessionInfo.toRow(isPinned: Boolean = false): SessionRow {
    val ts = time?.updated ?: time?.created
    return SessionRow(
        id = id,
        title = title.orEmpty().trim(),
        directory = location?.directory,
        age = formatAge(ts),
        cost = cost?.takeIf { it > 0 }?.let { formatCost(it) },
        timestamp = ts,
        isPinned = isPinned,
        tokenTotal = totalTokens(tokens),
    )
}

/**
 * Total tokens a session used: input + output + reasoning.
 *
 * Cache read/write are subsets of input, not additions — adding them would
 * double-count. Null when the engine reported no usage at all, or a total
 * of zero (a session that never ran a model has nothing to show).
 */
internal fun totalTokens(tokens: TokenUsage?): Long? {
    if (tokens == null) return null
    val total = (tokens.input ?: 0) + (tokens.output ?: 0) + (tokens.reasoning ?: 0)
    return total.takeIf { it > 0 }
}

/** 842 → "842", 12_400 → "12.4k", 1_250_000 → "1.2M". Same compact form as the turn footer. */
internal fun formatCompactTokens(tokens: Long): String = when {
    tokens < 1_000 -> tokens.toString()
    tokens < 1_000_000 -> "${tokens / 1000}.${(tokens % 1000) / 100}k"
    else -> "${tokens / 1_000_000}.${(tokens % 1_000_000) / 100_000}M"
}

/** Relative age from an epoch-millis timestamp. Coarse on purpose: exact seconds do not help. */
internal fun formatAge(epochMillis: Long?, now: Long = System.currentTimeMillis()): SessionAge? {
    if (epochMillis == null || epochMillis <= 0) return null
    val delta = (now - epochMillis).coerceAtLeast(0)
    val minutes = delta / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> SessionAge(AgeUnit.NOW)
        minutes < 60 -> SessionAge(AgeUnit.MINUTES, minutes.toInt())
        hours < 24 -> SessionAge(AgeUnit.HOURS, hours.toInt())
        days < 30 -> SessionAge(AgeUnit.DAYS, days.toInt())
        else -> SessionAge(AgeUnit.MONTHS, (days / 30).toInt())
    }
}

private fun formatCost(value: Double): String =
    if (value < 0.01) "<$0.01" else String.format(java.util.Locale.US, "$%.2f", value)
