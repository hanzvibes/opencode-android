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

/** One page of sessions (19-F3). */
internal const val SESSION_PAGE_SIZE = 50

/** One page of messages while walking toward a session's first user message (19-F2). */
internal const val PREVIEW_PAGE_SIZE = 50

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
    /** A message with no failure behind it for a failed delete, e.g. the engine never started (65-F3). */
    val deleteFailureKey: Int? = null,
    /** A failed rename (65-F2/80-F3), kept separate like [deleteFailure]: the row is reverted, the banner explains why. */
    val renameFailure: ApiResult.Failure? = null,
    /** The session the failed rename was for, so the banner can retry it. */
    val renameFailedSessionID: String? = null,
    /** The title the failed rename tried to set, so the banner can retry it. */
    val renameFailedTitle: String? = null,
    /** A message with no failure behind it for a failed rename, e.g. the engine never started. */
    val renameFailureKey: Int? = null,
    /** More pages exist past the first 50 (19-F3); the screen offers "Load more". */
    val hasMoreSessions: Boolean = false,
    /** A next page is being fetched (19-F3). */
    val loadingMore: Boolean = false,
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

    /** Cursor for the next page of sessions (19-F3); null when the list is complete. */
    private var sessionCursor: String? = null

    /**
     * The scope the last load() used (80-F11): rename/delete reload after
     * their network call returns, and the scope chip may have moved since
     * they were tapped — the reload must use the scope that is current
     * when it runs, not the one captured at tap time. Defaults to the
     * app's initial scope (this project).
     */
    private var lastScopedToWorkspace: Boolean = true

    /** @param scopedToWorkspace when true, only the current project's sessions. */
    fun load(scopedToWorkspace: Boolean) {
        val handle = api.handle() ?: run {
            _state.value = _state.value.copy(loading = false, failureKey = R.string.error_engine_not_running)
            return
        }
        val directory = if (scopedToWorkspace) workspace() else null
        val generation = ++loadGeneration
        lastScopedToWorkspace = scopedToWorkspace
        sessionCursor = null
        detailsJob?.cancel()
        loadJob?.cancel()
        _state.value = _state.value.copy(loading = true, failure = null, failureKey = null, deleteFailure = null, deleteFailedSessionID = null, deleteFailureKey = null, renameFailure = null, renameFailedSessionID = null, renameFailedTitle = null, renameFailureKey = null, hasMoreSessions = false, loadingMore = false, filterDirectory = directory)

        loadJob = viewModelScope.launch {
            when (val result = handle.sessions.listPage(directory = directory, limit = SESSION_PAGE_SIZE)) {
                is ApiResult.Success -> {
                    if (generation != loadGeneration) return@launch
                    val page = result.value
                    val listedIds = page.data.map { it.id }.toSet()
                    // Ghost pins (80-F6): a pin for a session that is gone
                    // can never be unpinned from the UI, because the session
                    // never appears. Prune them only when the list is
                    // complete — while the list is scoped to a project, or
                    // capped at 50, an unlisted pin may belong to a session
                    // that is real but out of view.
                    val pinned = pinnedStore?.getPinnedIds().orEmpty()
                    val keptPins = pruneGhostPins(pinned, listedIds, directory != null || page.data.size >= SESSION_PAGE_SIZE)
                    (pinned - keptPins).forEach { pinnedStore?.remove(it) }
                    // Details survive a reload (Fase 2, 19-F1): wiping them
                    // made every drawer open refetch every session's full
                    // history. Entries for sessions no longer listed are
                    // pruned; the rest are refreshed by loadDetails, which
                    // only fetches sessions without a preview.
                    _state.value = _state.value.copy(
                        loading = false,
                        rows = page.data.map { it.toRow(isPinned = keptPins.contains(it.id)) },
                        failure = null, failureKey = null,
                        details = _state.value.details.filterKeys { it in listedIds },
                        hasMoreSessions = page.cursor?.next != null,
                    )
                    sessionCursor = page.cursor?.next
                    loadDetails(page.data, generation)
                }
                is ApiResult.Failure -> {
                    if (generation != loadGeneration) return@launch
                    _state.value = _state.value.copy(loading = false, failure = result)
                }
            }
        }
    }

    /**
     * Appends the next page of sessions (19-F3). A no-op when the list is
     * complete or a page is already loading.
     */
    fun loadMore() {
        val cursor = sessionCursor ?: return
        if (_state.value.loading || _state.value.loadingMore) return
        val handle = api.handle() ?: return
        val generation = loadGeneration
        val directory = _state.value.filterDirectory
        _state.value = _state.value.copy(loadingMore = true)
        loadJob = viewModelScope.launch {
            when (val result = handle.sessions.listPage(directory = directory, limit = SESSION_PAGE_SIZE, cursor = cursor)) {
                is ApiResult.Success -> {
                    if (generation != loadGeneration) return@launch
                    val page = result.value
                    val listedIds = (_state.value.rows.map { it.id } + page.data.map { it.id }).toSet()
                    val pinned = pinnedStore?.getPinnedIds().orEmpty()
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        rows = mergeSessionPage(_state.value.rows, page.data.map { it.toRow(isPinned = pinned.contains(it.id)) }),
                        failure = null, failureKey = null,
                        details = _state.value.details.filterKeys { it in listedIds },
                        hasMoreSessions = page.cursor?.next != null,
                    )
                    sessionCursor = page.cursor?.next
                    loadDetails(page.data, generation)
                }
                is ApiResult.Failure -> {
                    if (generation != loadGeneration) return@launch
                    // Rows are already on screen, so this is a banner over
                    // the list, never a full-screen error (Fase 5 pattern).
                    _state.value = _state.value.copy(loadingMore = false, failure = result)
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
     * first user message; that is the oldest entry, so the walk follows
     * the page cursor toward the oldest page (19-F2): the old code fetched
     * the whole history in one unbounded call and assumed "no limit" meant
     * "everything", which breaks the moment the engine paginates. Each
     * page is bounded (50 messages) and only the candidate preview is
     * kept, so a long session no longer loads its whole transcript into
     * memory to find one line. What is cheap is not fetching twice
     * (Fase 2, 19-F1): a preview already loaded is immutable — the first
     * message never changes — so it is kept across reloads and only its
     * model/status refresh from the fresh list info, with no network call.
     * Sessions without a preview (new, empty, or failed before) are still
     * looked up. Lookups run sequentially in the background and publish
     * incrementally, so rows fill in as their data lands. A failed lookup
     * leaves that session's model/status in place with no preview rather
     * than failing the whole list — and a session whose history is
     * genuinely empty has no preview at all, which the row already handles.
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
                val preview = fetchFirstUserPreview(handle, info.id, generation)
                filled[info.id] = SessionDetail(
                    preview = preview,
                    model = modelLabel,
                    status = info.outcome,
                )
                // A newer load replaced the rows while this lookup was in
                // flight: its previews belong to the old list (item 26).
                if (generation != loadGeneration) return@launch
                _state.value = _state.value.copy(details = filled.toMap())
            }
        }
    }

    /**
     * The session's first user message, found by walking the message
     * pages newest-first (19-F2/80-F7).
     *
     * Each page's oldest user message overwrites the candidate, so after
     * the last page the candidate is the first user message of the whole
     * history — the page walk never holds more than one page in memory.
     * A repeated cursor (an engine that loops) ends the walk instead of
     * spinning; a failed page keeps whatever was found so far.
     */
    private suspend fun fetchFirstUserPreview(
        handle: EngineApi.Handle,
        sessionID: String,
        generation: Int,
    ): String? {
        var cursor: String? = null
        var candidate: String? = null
        val seenCursors = mutableSetOf<String>()
        while (true) {
            if (generation != loadGeneration) return candidate
            when (val page = handle.sessions.messagesPage(sessionID, limit = PREVIEW_PAGE_SIZE, cursor = cursor)) {
                is ApiResult.Success -> {
                    candidate = updatePreviewCandidate(candidate, page.value.data)
                    val next = page.value.cursor?.next
                    if (next == null || next in seenCursors) return candidate
                    seenCursors += next
                    cursor = next
                }
                is ApiResult.Failure -> return candidate
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
    fun rename(sessionID: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        val oldTitle = _state.value.rows.find { it.id == sessionID }?.title
        _state.value = _state.value.copy(
            rows = _state.value.rows.map {
                if (it.id == sessionID) it.copy(title = trimmed) else it
            }
        )
        val handle = api.handle() ?: run {
            // The engine is down: nothing was renamed. Revert the
            // optimistic title and say so (65-F3 pattern from delete) —
            // the old code returned silently and the user kept waiting.
            _state.value = resolveRenameFailure(_state.value, sessionID, oldTitle, trimmed, null, R.string.error_engine_not_running)
            return
        }
        viewModelScope.launch {
            when (val result = handle.sessions.rename(sessionID, trimmed)) {
                is ApiResult.Success -> load(lastScopedToWorkspace)
                is ApiResult.Failure -> {
                    // The old code dropped the result and reloaded: the
                    // title quietly snapped back with no word about why
                    // (65-F2/80-F3). Revert it here and keep the failure,
                    // so the banner can name it and retry with the same
                    // title instead of reopening the dialog.
                    _state.value = resolveRenameFailure(_state.value, sessionID, oldTitle, trimmed, result)
                }
            }
        }
    }

    /** Deletes a session. */
    fun delete(sessionID: String, onDeleted: ((String) -> Unit)? = null) {
        // The engine must be reachable before anything is removed locally:
        // with no handle the delete cannot run, and removing the pin or the
        // row first would lose state for a session that is still there.
        // The old code returned silently here (65-F3/80-F4): the row stayed
        // and the user got no word at all. The banner below names it.
        val handle = api.handle() ?: run {
            _state.value = _state.value.copy(
                deleteFailure = null,
                deleteFailedSessionID = sessionID,
                deleteFailureKey = R.string.error_engine_not_running,
            )
            return
        }
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
                    // The chat must not move away from the session before
                    // the engine confirms the delete (80-F1): the old call
                    // sites called newSession() first, so a failed delete
                    // left the session alive with its live view destroyed.
                    // The caller switches away and prunes the parked
                    // composer (80-F6) only on this callback.
                    onDeleted?.invoke(sessionID)
                    load(lastScopedToWorkspace)
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
        _state.value = _state.value.copy(deleteFailure = null, deleteFailedSessionID = null, deleteFailureKey = null)
    }

    /** Dismisses the failed-rename banner; the reverted title stays. */
    fun clearRenameFailure() {
        _state.value = _state.value.copy(renameFailure = null, renameFailedSessionID = null, renameFailedTitle = null, renameFailureKey = null)
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

/**
 * State after a rename the engine refused (65-F2/80-F3): the title goes
 * back to what it was, and the failure lands in [SessionListUiState.renameFailure]
 * with the title that failed, so the banner can retry it — never in
 * `failure`, which is the screen's full-screen error. Pure so the branch
 * is testable without a live engine: [SessionListViewModel.rename] runs it
 * inside a coroutine the JVM unit tests cannot drive (no Main dispatcher
 * on the test classpath).
 */
internal fun resolveRenameFailure(
    state: SessionListUiState,
    sessionID: String,
    oldTitle: String?,
    newTitle: String,
    result: ApiResult.Failure?,
    failureKey: Int? = null,
): SessionListUiState {
    val rows = if (oldTitle != null) {
        state.rows.map { if (it.id == sessionID) it.copy(title = oldTitle) else it }
    } else {
        state.rows
    }
    return state.copy(
        rows = rows,
        renameFailure = result,
        renameFailedSessionID = sessionID,
        renameFailedTitle = newTitle,
        renameFailureKey = failureKey,
    )
}

/**
 * Pins that may stay after a list load (80-F6).
 *
 * A pin for a session that is gone can never be unpinned from the UI,
 * because the session never appears — but only when the list is known
 * complete. While the list is scoped to a project, or capped at a full
 * page, an unlisted pin may belong to a session that is real but out of
 * view, so every pin is kept. Pure so the rule is testable.
 */
internal fun pruneGhostPins(
    pinned: Set<String>,
    listedIds: Set<String>,
    incomplete: Boolean,
): Set<String> =
    if (incomplete) pinned else pinned.filter { it in listedIds }.toSet()

/**
 * Appends one more page of session rows (19-F3), dropping rows the list
 * already shows: a page boundary must never duplicate a session the
 * user already sees.
 */
internal fun mergeSessionPage(
    existing: List<SessionRow>,
    page: List<SessionRow>,
): List<SessionRow> {
    val ids = existing.map { it.id }.toSet()
    return existing + page.filter { it.id !in ids }
}

/**
 * The preview candidate after one page of a newest-first walk (19-F2).
 *
 * The page's oldest user message overwrites the candidate, so after the
 * last (oldest) page the candidate is the session's first user message.
 * A page with no user message keeps the candidate from the newer pages:
 * the first user message is the earliest one the walk has seen.
 */
internal fun updatePreviewCandidate(
    candidate: String?,
    page: List<dev.bidzz.opencode.core.api.dto.SessionMessage>,
): String? =
    page.reversed().firstOrNull { it.isUser }
        ?.text?.trim()?.takeIf { it.isNotEmpty() }
        ?: candidate

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
