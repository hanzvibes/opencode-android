package dev.bidzz.opencode.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.SessionInfo
import dev.bidzz.opencode.core.api.dto.TokenUsage
import dev.bidzz.opencode.ui.formatCompactTokens
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
    /**
     * A failed delete (Fase 5, 19-F7/65-F1/80-F2), kept separate from
     * [failure] on purpose: the old code put it in `failure`, and the
     * screen's full-screen error branch hid the list — including the
     * row the delete had just restored. A per-row action failure is a
     * banner over the visible list, never a full-screen error.
     */
    val deleteFailure: ApiResult.Failure? = null,
    /** The session the failed delete was for, so the banner can retry it. */
    val deleteFailedSessionID: String? = null,
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
        _state.value = _state.value.copy(loading = true, failure = null, failureKey = null, deleteFailure = null, deleteFailedSessionID = null, filterDirectory = directory)

        loadJob = viewModelScope.launch {
            when (val result = handle.sessions.list(directory = directory, limit = 50)) {
                is ApiResult.Success -> {
                    if (generation != loadGeneration) return@launch
                    val pinned = pinnedStore?.getPinnedIds().orEmpty()
                    // Details survive a reload (Fase 2, 19-F1): wiping them
                    // made every drawer open refetch every session's full
                    // history. Entries for sessions no longer listed are
                    // pruned; the rest are refreshed by loadDetails, which
                    // only fetches sessions without a preview.
                    val listedIds = result.value.map { it.id }.toSet()
                    _state.value = _state.value.copy(
                        loading = false,
                        rows = result.value.map { it.toRow(isPinned = pinned.contains(it.id)) },
                        failure = null, failureKey = null,
                        details = _state.value.details.filterKeys { it in listedIds },
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
     * There is no cheaper way to reach the oldest message: the endpoint
     * takes only a newest-first limit, and a small limit returns the wrong
     * end. What is cheap is not fetching twice (Fase 2, 19-F1): a preview
     * already loaded is immutable — the first message never changes — so
     * it is kept across reloads and only its model/status refresh from
     * the fresh list info, with no network call. Sessions without a
     * preview (new, empty, or failed before) are still looked up.
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
            val filled = _state.value.details.toMutableMap()
            for (info in infos) {
                val modelLabel = info.model?.let { "${it.providerID}/${it.id}" }
                val existing = filled[info.id]
                if (existing?.preview != null) {
                    filled[info.id] = existing.copy(model = modelLabel, status = info.outcome)
                    _state.value = _state.value.copy(details = filled.toMap())
                    continue
                }
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
        // The engine must be reachable before anything is removed locally:
        // with no handle the delete cannot run, and removing the pin or the
        // row first would lose state for a session that is still there.
        val handle = api.handle() ?: return
        val rows = _state.value.rows
        val index = rows.indexOfFirst { it.id == sessionID }
        val deleted = rows.getOrNull(index)
        _state.value = _state.value.copy(
            rows = rows.filter { it.id != sessionID }
        )
        viewModelScope.launch {
            when (val result = handle.sessions.delete(sessionID)) {
                is ApiResult.Success -> {
                    // The pin is removed only now that the session is really
                    // gone: removing it first meant a failed delete brought
                    // the session back without its pin (audit-70 19-F3).
                    pinnedStore?.remove(sessionID)
                    load(scopedToWorkspace)
                }
                is ApiResult.Failure -> {
                    // The session is still on the engine: put the row back
                    // where it was, keep its pin, and show the failure
                    // instead of pretending the delete worked.
                    _state.value = resolveDeleteFailure(_state.value, deleted, index, sessionID, result)
                }
            }
        }
    }
    /** Dismisses the failed-delete banner; the row stays in the list. */
    fun clearDeleteFailure() {
        _state.value = _state.value.copy(deleteFailure = null, deleteFailedSessionID = null)
    }

    /** Dismisses a load-failure banner shown over a non-empty list. */
    fun clearLoadFailure() {
        _state.value = _state.value.copy(failure = null, failureKey = null)
    }
}

/**
 * State after a delete the engine refused (19-F7/65-F1/80-F2): the row
 * goes back where it was with its pin intact, and the failure lands in
 * [SessionListUiState.deleteFailure] — never in `failure`, which is the
 * screen's full-screen error. Pure so the branch is testable without a
 * live engine: [SessionListViewModel.delete] runs it inside a coroutine
 * the JVM unit tests cannot drive (no Main dispatcher on the test
 * classpath).
 */
internal fun resolveDeleteFailure(
    state: SessionListUiState,
    deleted: SessionRow?,
    index: Int,
    sessionID: String,
    result: ApiResult.Failure,
): SessionListUiState {
    val current = state.rows.toMutableList()
    if (deleted != null && current.none { it.id == sessionID }) {
        current.add(index.coerceIn(0, current.size), deleted)
    }
    return state.copy(rows = current, deleteFailure = result, deleteFailedSessionID = sessionID)
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
 * double-count. Null only when the engine reported no usage at all; a
 * reported zero stays zero (audit-100 Fase 6a, 67-F5) — the list, the turn
 * footer and Stats now share one rule: absence hides, zero shows.
 */
internal fun totalTokens(tokens: TokenUsage?): Long? {
    if (tokens == null) return null
    return (tokens.input ?: 0) + (tokens.output ?: 0) + (tokens.reasoning ?: 0)
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
