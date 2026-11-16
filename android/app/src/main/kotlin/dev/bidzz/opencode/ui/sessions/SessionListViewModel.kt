package dev.bidzz.opencode.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.SessionInfo
import dev.bidzz.opencode.core.api.dto.TokenUsage
import dev.bidzz.opencode.ui.MAX_SESSION_TITLE_LENGTH
import dev.bidzz.opencode.ui.chat.ReadPositionStore
import dev.bidzz.opencode.ui.engineDownFailure
import dev.bidzz.opencode.ui.formatCompactTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One page of sessions (19-F3). */
internal const val SESSION_PAGE_SIZE = 50

/**
 * One page of messages while walking toward a session's first user
 * message (19-F2). Audit-200 C15 (M-214): the engine's messages
 * endpoint accepts limit 1..200 (SessionMessagesQuery, verified against
 * the pinned engine source) — 200 is the widest page the server allows,
 * so a 1,000-message session costs 5 requests instead of 20, with the
 * same result: the walk still finds the true first user message.
 */
internal const val PREVIEW_PAGE_SIZE = 200

/**
 * Most pages one preview walk may fetch (audit-200 D11, m-355): at
 * [PREVIEW_PAGE_SIZE] messages a page, a walk reads at most 5,000
 * messages — enough that every ordinary session still finds its true
 * first user message, while a 10,000-message session stops after 25
 * requests instead of walking its whole history before its row shows
 * a preview. A capped walk reports incomplete (see
 * [fetchFirstUserPreview]), so the D10 note says so and the next load
 * retries it — bounded again by this same cap.
 */
internal const val MAX_PREVIEW_WALK_PAGES = 25

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
    /** IDs with a delete in flight (audit-200 D7, m-686) — a second tap
     * while one is running must not send a second DELETE, which the
     * engine answers with a failure for a session that is already gone
     * and the banner then reports as a failed delete.
     */
    val deletingIds: Set<String> = emptySet(),
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
    /**
     * True while a preview walk is in flight (audit-200 C5, M-089): the
     * screen says previews are still arriving instead of letting a
     * search look complete while rows are still unknown.
     */
    val detailsLoading: Boolean = false,
    /**
     * True when a preview walk finished with at least one lookup failed
     * (audit-200 D10, m-173/m-1115): the walk is over, so [detailsLoading]
     * is false, but some session's preview is still unknown — the search
     * note must say the search may be incomplete instead of vanishing
     * with the loading flag. Reset when a new walk starts; a retry that
     * lands clears it the same way.
     */
    val previewsIncomplete: Boolean = false,
)

/**
 * What the session list says about its previews (audit-200 D10,
 * m-173/m-1115): [LOADING] while a walk is in flight, [INCOMPLETE]
 * when a walk finished with at least one lookup failed, [NONE] when
 * every listed session's preview is known (or there is nothing to say
 * because no row is unknown). The History screen and the drawer share
 * this one derivation, so the note can never disagree between them.
 */
enum class SessionPreviewNote {
    NONE,
    LOADING,
    INCOMPLETE,
}

/**
 * Derives the preview note from one state snapshot. Pure so the rule is
 * testable without a live engine: a note only ever appears while some
 * listed session's preview is still unknown — rows below the fold that
 * were never walked do not trigger it on their own, and a walk that
 * finished clean hides it even though [detailsLoading] just turned off.
 */
fun resolveSessionPreviewNote(state: SessionListUiState): SessionPreviewNote {
    if (!state.rows.any { state.details[it.id]?.previewKnown != true }) return SessionPreviewNote.NONE
    return when {
        state.detailsLoading -> SessionPreviewNote.LOADING
        state.previewsIncomplete -> SessionPreviewNote.INCOMPLETE
        else -> SessionPreviewNote.NONE
    }
}

/**
 * The sessions a search that found nothing still owes a preview walk
 * (audit-200 D11, m-485): rows below the fold have no search text
 * until their preview walk runs, and the walk only seeds the first 20
 * — so a word that lives only in an unwalked session's preview matches
 * nothing, silently. A non-blank query with zero matches and at least
 * one listed session still unwalked owes those ids a walk; anything
 * else owes nothing. Pure so the rule is testable without a live
 * engine; the screen fires it once per query (see SessionListPane),
 * so a walk that fails cannot re-trigger itself forever.
 */
fun idsToWalkForEmptySearch(
    query: String,
    hasResults: Boolean,
    rows: List<SessionRow>,
    details: Map<String, SessionDetail>,
): List<String> {
    if (query.isBlank() || hasResults) return emptyList()
    return rows.filter { details[it.id]?.previewKnown != true }.map { it.id }
}

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
    private val readPositionStore: ReadPositionStore? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionListUiState())
    val state: StateFlow<SessionListUiState> = _state.asStateFlow()

    /** In-flight preview job, cancelled when a fresh list loads. */
    private var previewJob: Job? = null

    /**
     * Sessions whose preview walk is still wanted, in request order
     * (audit-200 C5, M-089): the first load seeds the first 20, and the
     * screens add the rows they actually show (see [requestPreviews]).
     * A walk removes its id when it lands, so a session is never walked
     * twice for one list.
     */
    private val previewWantedIds = LinkedHashSet<String>()

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
        // Audit-200 D9 (m-1116): the scope chip moves to the requested
        // scope at once, but the rows stay the old scope's until the
        // network answers — and on failure nothing restored them, so the
        // chip showed the new scope over the old list, with Load more
        // gone (hasMoreSessions was reset too). Capture the previous
        // scope/cursor/page state and restore it in the Failure branch.
        val previousDirectory = _state.value.filterDirectory
        val previousCursor = sessionCursor
        val previousHasMore = _state.value.hasMoreSessions
        sessionCursor = null
        previewJob?.cancel()
        previewWantedIds.clear()
        loadJob?.cancel()
        // Audit-200 D6 (m-481): a load must NOT wipe the operation
        // failures — they belong to the delete/rename, not to the load,
        // and the drawer opens by calling load() (so a banner wiped
        // itself exactly when the user went to look at it, 80-F19).
        // They clear on Dismiss, or when a new failure of the same kind
        // supersedes them. Trade-off, accepted: a stale banner can
        // survive a successful reload — e.g. the session was meanwhile
        // deleted from another client, so the Retry finds no row and
        // offers no button; the user dismisses it.
        _state.value = _state.value.copy(loading = true, failure = null, failureKey = null, hasMoreSessions = false, loadingMore = false, filterDirectory = directory)

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
                    // the engine says there is another page, an unlisted pin
                    // may belong to a session that is real but out of view.
                    val pinned = pinnedStore?.getPinnedIds().orEmpty()
                    val keptPins = pruneGhostPins(pinned, listedIds, isSessionListIncomplete(directory, page.cursor?.next != null))
                    (pinned - keptPins).forEach { pinnedStore?.remove(it) }
                    // Read watermarks (audit-200 B5, M-025): same
                    // completeness rule as the pins — a session deleted
                    // on the engine leaves its `read_positions` key
                    // behind forever. Prune only when the list is
                    // complete, or a session in another project or on
                    // the next page would look deleted.
                    if (!isSessionListIncomplete(directory, page.cursor?.next != null)) {
                        readPositionStore?.prune(listedIds)
                    }
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
                    // Audit-200 D9 (m-1116): the load failed, so the chip
                    // and the list go back to the scope they showed
                    // before — a failed scope switch must not leave the
                    // new chip over the old rows with no Load more.
                    // (The generation check above already drops stale
                    // results, so these are this load's own values.)
                    sessionCursor = previousCursor
                    _state.value = resolveLoadFailure(
                        _state.value, result,
                        previousDirectory = previousDirectory,
                        previousHasMore = previousHasMore,
                    )
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
        // Audit-200 D10 (m-817): a dead engine used to swallow the tap
        // silently — the same B2 pattern as addServer/mutate, where the
        // failure is reported the same way a failed page is.
        val handle = api.handle() ?: run {
            _state.value = _state.value.copy(failure = engineDownFailure())
            return
        }
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
     * Refreshes [SessionListUiState.details] (Phase 2, finding 21).
     *
     * The list endpoint returns no transcript, so the preview comes from the
     * messages endpoint and the model/outcome from the [SessionInfo] already
     * in hand — no extra call for those two. The preview is the session's
     * first user message; that is the oldest entry, so the walk follows
     * the page cursor toward the oldest page (19-F2).
     *
     * Audit-200 C5 (M-089): the old code walked every session's whole
     * history eagerly, one after another, before the list was usable — a
     * long session stalled every row behind it, and a null preview
     * (empty or failed) was re-walked on every load. Now the refresh
     * itself is free (model/status from the list info, no network), and
     * the history walk is lazy: the first load seeds the first 20 ids,
     * the screens add the rows they actually show (see
     * [requestPreviews]), and one job walks them in small concurrent
     * batches. A walk that completed is never repeated — a null preview
     * then means the session is genuinely empty, not "not looked up
     * yet" (see [SessionDetail.previewKnown]) — and a walk that failed
     * mid-way stays wanted, so the next load retries it.
     */
    private fun loadDetails(infos: List<SessionInfo>, generation: Int) {
        val filled = _state.value.details.toMutableMap()
        for (info in infos) {
            val modelLabel = info.model?.let { "${it.providerID}/${it.id}" }
            val existing = filled[info.id]
            filled[info.id] = SessionDetail(
                preview = existing?.preview,
                model = modelLabel,
                status = info.outcome,
                searchText = existing?.searchText,
                previewKnown = existing?.previewKnown == true,
            )
        }
        _state.value = _state.value.copy(details = filled.toMap())
        // Seed the first screenful: the drawer shows at most 20 rows,
        // and History's first rows are the same ids — visible reporting
        // (requestPreviews) covers everything below the fold.
        val wanted = infos.take(PREVIEW_SEED_COUNT)
            .map { it.id }
            .filter { filled[it]?.previewKnown != true }
        previewWantedIds.addAll(wanted)
        startPreviewJob()
    }

    /**
     * Asks for previews for [ids] (audit-200 C5, M-089): the screens
     * report the rows actually visible, so a session below the fold is
     * never walked until the user scrolls to it. Ids already known are
     * skipped; the rest join the queue and the preview job starts when
     * it is not already running.
     */
    fun requestPreviews(ids: List<String>) {
        val details = _state.value.details
        val wanted = ids.filter { details[it]?.previewKnown != true }
        if (wanted.isEmpty()) return
        if (previewWantedIds.addAll(wanted)) startPreviewJob()
    }

    /**
     * Walks the wanted previews in batches (audit-200 C5, M-089): up to
     * [PREVIEW_BATCH_SIZE] walks at once, publishing each batch as it
     * lands, until the queue is empty or a newer load replaces the list
     * (the generation check). A single job owns the queue — a second
     * start while it runs is a no-op — and the loading flag belongs to
     * that job: the finally block only clears it when no newer job took
     * over, so a cancelled job cannot hide a running one.
     */
    private fun startPreviewJob() {
        if (previewWantedIds.isEmpty()) {
            _state.value = _state.value.copy(detailsLoading = false)
            return
        }
        if (previewJob?.isActive == true) return
        val generation = loadGeneration
        previewJob = viewModelScope.launch {
            val self = coroutineContext[Job]!!
            // Audit-200 D10 (m-173/m-1115): a new walk re-decides the
            // incomplete flag — a stale failure from the last walk must
            // not survive into this one.
            _state.value = _state.value.copy(detailsLoading = true, previewsIncomplete = false)
            try {
                while (previewWantedIds.isNotEmpty()) {
                    if (generation != loadGeneration) return@launch
                    val handle = api.handle() ?: return@launch
                    val batch = previewWantedIds.take(PREVIEW_BATCH_SIZE)
                    val results = batch.map { id ->
                        async { id to fetchFirstUserPreview(handle, id, generation) }
                    }.awaitAll()
                    if (generation != loadGeneration) return@launch
                    val filled = _state.value.details.toMutableMap()
                    // A lookup that lands with complete = false failed
                    // (a superseded walk returns before this point, so a
                    // false here is a genuine fetch failure, not a
                    // generation change) — remember it, so the search note
                    // survives the walk instead of vanishing with the
                    // loading flag.
                    val batchFailed = results.any { (_, lookup) -> !lookup.complete }
                    for ((id, lookup) in results) {
                        previewWantedIds.remove(id)
                        val existing = filled[id]
                        filled[id] = SessionDetail(
                            preview = lookup.preview,
                            model = existing?.model,
                            status = existing?.status,
                            searchText = lookup.searchText,
                            previewKnown = lookup.complete,
                        )
                    }
                    _state.value = _state.value.copy(
                        details = filled.toMap(),
                        previewsIncomplete = _state.value.previewsIncomplete || batchFailed,
                    )
                }
            } finally {
                if (previewJob === self) {
                    _state.value = _state.value.copy(detailsLoading = false)
                }
            }
        }
    }

    /**
     * The session's first user message, found by walking the message
     * pages newest-first (19-F2/80-F7), plus the searchable text the
     * walk passed and whether the walk completed (audit-200 C5,
     * M-089/M-097).
     *
     * Each page's oldest user message overwrites the candidate, so after
     * the last page the candidate is the first user message of the whole
     * history — the page walk never holds more than one page in memory.
     * A repeated cursor (an engine that loops) ends the walk instead of
     * spinning; a failed page keeps whatever was found so far and
     * reports the walk incomplete, so the next load retries it. A walk
     * that hits [MAX_PREVIEW_WALK_PAGES] pages stops the same way
     * (audit-200 D11, m-355): the candidate is the oldest user message
     * found so far and the walk reports incomplete, so the D10 note says
     * the previews are not all loaded — before this, one very long
     * session walked its whole history when it became visible.
     */
    private suspend fun fetchFirstUserPreview(
        handle: EngineApi.Handle,
        sessionID: String,
        generation: Int,
    ): PreviewLookup {
        var cursor: String? = null
        var candidate: String? = null
        var searchText: String? = null
        var pagesWalked = 0
        val seenCursors = mutableSetOf<String>()
        while (true) {
            if (generation != loadGeneration) return PreviewLookup(candidate, searchText, complete = false)
            when (val page = handle.sessions.messagesPage(sessionID, limit = PREVIEW_PAGE_SIZE, cursor = cursor)) {
                is ApiResult.Success -> {
                    pagesWalked++
                    candidate = updatePreviewCandidate(candidate, page.value.data)
                    searchText = accumulateSearchText(searchText, page.value.data)
                    val next = page.value.cursor?.next
                    if (next == null || next in seenCursors) return PreviewLookup(candidate, searchText, complete = true)
                    if (pagesWalked >= MAX_PREVIEW_WALK_PAGES) return PreviewLookup(candidate, searchText, complete = false)
                    seenCursors += next
                    cursor = next
                }
                is ApiResult.Failure -> return PreviewLookup(candidate, searchText, complete = false)
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
        // Audit-200 D8 (m-1228): the title is capped — an old over-long
        // title is cut on its next rename.
        val trimmed = newTitle.trim().take(MAX_SESSION_TITLE_LENGTH)
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
        // Audit-200 D7 (m-686): a delete already in flight for this id
        // owns the outcome — a second tap (the dialog only vanishes on
        // the next recomposition, and the Retry banner calls onDelete
        // directly) must not send a second DELETE.
        if (sessionID in _state.value.deletingIds) return
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
        // Audit-200 D7 (m-686): no row, no delete — a Retry for a session
        // deleted elsewhere would otherwise send a DELETE for a gone
        // session and report its 404 as a failed delete.
        if (index == -1) return
        val deleted = rows.getOrNull(index)
        _state.value = _state.value.copy(
            rows = rows.filter { it.id != sessionID },
            deletingIds = _state.value.deletingIds + sessionID,
        )
        viewModelScope.launch {
            when (val result = handle.sessions.delete(sessionID)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(deletingIds = _state.value.deletingIds - sessionID)
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
    // Audit-200 D7 (m-686): the in-flight marker is released on failure
    // too, so a Retry can send a fresh DELETE.
    return state.copy(rows = current, deleteFailure = result, deleteFailedSessionID = sessionID, deletingIds = state.deletingIds - sessionID)
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
 * Audit-200 D9 (m-1116): a failed load hands the scope back — the
 * directory chip, the "Load more" flag and the failure land together, so
 * a failed scope switch never leaves the new chip over the old rows with
 * no Load more. The rows themselves are untouched (they still belong to
 * the old scope). Pure so the branch is testable without a live engine:
 * [SessionListViewModel.load] runs it inside a coroutine the JVM unit
 * tests cannot drive (no Main dispatcher on the test classpath). The
 * [sessionCursor] restore is not part of the state and stays in load().
 */
internal fun resolveLoadFailure(
    state: SessionListUiState,
    result: ApiResult.Failure,
    previousDirectory: String?,
    previousHasMore: Boolean,
): SessionListUiState =
    state.copy(
        loading = false,
        failure = result,
        filterDirectory = previousDirectory,
        hasMoreSessions = previousHasMore,
    )

/**
 * True when a just-loaded first page cannot prove a pin is a ghost
 * (audit-200 A9-7, DL-028): the list is scoped to one project, or the
 * engine says there is another page. The old check read the page's
 * size — a short page with a `next` cursor looked complete, so pins
 * for sessions on the later pages were pruned as ghosts. The cursor is
 * the only signal that says whether more sessions exist: a full page
 * with no `next` cursor IS complete (exactly 50 sessions), and a short
 * page with one is not. Pure so the rule is testable.
 */
internal fun isSessionListIncomplete(
    scopedDirectory: String?,
    hasNextPage: Boolean,
): Boolean = scopedDirectory != null || hasNextPage

/**
 * Pins that may stay after a list load (80-F6).
 *
 * A pin for a session that is gone can never be unpinned from the UI,
 * because the session never appears — but only when the list is known
 * complete. While the list is scoped to a project, or the engine
 * reports another page, an unlisted pin may belong to a session that
 * is real but out of view, so every pin is kept. Pure so the rule is
 * testable.
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

/** How much user-message text a session's search entry keeps (audit-200 C5, M-097): enough to match on, never the whole transcript. */
internal const val SEARCH_TEXT_MAX_CHARS = 8192

/** How many preview walks run at once (audit-200 C5, M-089). */
internal const val PREVIEW_BATCH_SIZE = 4

/** How many sessions the first load seeds previews for (audit-200 C5, M-089): the drawer shows at most 20 rows. */
internal const val PREVIEW_SEED_COUNT = 20

/**
 * What one preview walk found (audit-200 C5, M-089/M-097): the first
 * user message, the capped searchable text of every user message the
 * walk passed, and whether the walk reached the oldest page.
 */
internal data class PreviewLookup(
    val preview: String?,
    val searchText: String?,
    val complete: Boolean,
)

/**
 * Appends a page's user messages to the searchable text, in visit
 * order (audit-200 C5, M-097). Blank messages add nothing; once the
 * text reaches [SEARCH_TEXT_MAX_CHARS] it stops growing — the walk
 * still continues for the preview candidate. Pure, so the cap and the
 * order are unit-tested here rather than through a walk.
 */
internal fun accumulateSearchText(
    current: String?,
    page: List<dev.bidzz.opencode.core.api.dto.SessionMessage>,
): String? {
    if (current != null && current.length >= SEARCH_TEXT_MAX_CHARS) return current
    val joined = page.filter { it.isUser }
        .mapNotNull { it.text?.trim()?.takeIf { text -> text.isNotEmpty() } }
        .joinToString("\n")
    if (joined.isEmpty()) return current
    val combined = if (current.isNullOrEmpty()) joined else "$current\n$joined"
    return combined.take(SEARCH_TEXT_MAX_CHARS)
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
 * Total tokens a session used: input + output + reasoning + cache.read + cache.write.
 *
 * Audit-200 C13 (M-185): cache read/write are separate buckets, NOT
 * subsets of input — opencode's `getUsage` reports `input` as
 * `inputTokens` − cache.read − cache.write, so leaving them out
 * undercounted every cached turn. Null only when the engine reported no
 * usage at all; a reported zero stays zero (audit-100 Fase 6a, 67-F5) —
 * the list, the turn footer and Stats now share one rule: absence hides,
 * zero shows.
 */
internal fun totalTokens(tokens: TokenUsage?): Long? {
    if (tokens == null) return null
    return (tokens.input ?: 0) + (tokens.output ?: 0) + (tokens.reasoning ?: 0) +
        (tokens.cache?.read ?: 0) + (tokens.cache?.write ?: 0)
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
