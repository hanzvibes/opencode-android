package dev.bidzz.opencode.ui.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.R
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.FileApi
import dev.bidzz.opencode.core.api.VcsApi
import dev.bidzz.opencode.core.api.dto.FileDiffInfo
import dev.bidzz.opencode.core.api.dto.FsEntry
import dev.bidzz.opencode.core.api.dto.VcsFileStatus
import java.security.MessageDigest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One row in the explorer. */
data class FileRow(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    /** Git status for this file, when it changed: "added", "modified", "deleted". */
    val gitStatus: String? = null,
    val additions: Int = 0,
    val deletions: Int = 0,
)

/** Why the listing carries no change markers; the screen words it in the user's language. */
enum class VcsNote { NOT_A_REPOSITORY, NO_CHANGES }

data class ExplorerUiState(
    val loading: Boolean = true,
    val rows: List<FileRow> = emptyList(),
    /** Relative path of the directory being shown; empty for the project root. */
    val currentPath: String = "",
    /** Breadcrumb segments, each with the path it navigates to. */
    val breadcrumbs: List<Crumb> = emptyList(),
    /** What went wrong, as a value; the screen words it in the app’s language. */
    val failure: ApiResult.Failure? = null,
    /** A message with no failure behind it, e.g. the engine never started. */
    val failureKey: Int? = null,
    /** Files changed in the working tree, by relative path. */
    val changed: Map<String, VcsFileStatus> = emptyMap(),
    /** True when the project is a git repository the engine can read. */
    val isRepository: Boolean = false,
    /** Set when git says nothing useful, so the UI can explain rather than show "clean". */
    val vcsNote: VcsNote? = null,
    /** Workspace-wide file index for quick-open; empty until the palette is opened. */
    val quickOpenIndex: List<FileRow> = emptyList(),
    /** True while the quick-open index is being built. */
    val quickOpenLoading: Boolean = false,
    /**
     * True when the last index walk hit a failure: the published index is
     * partial, so the palette must say so and offer a retry instead of
     * serving it as complete (item 27).
     */
    val quickOpenFailed: Boolean = false,
) {
    data class Crumb(val label: String, val path: String)
}

/** Bounds for the quick-open index walk — see [ExplorerViewModel.ensureQuickOpenIndex]. */
private const val QuickOpenMaxFiles: Int = 2_000
private const val QuickOpenMaxDirs: Int = 200
private val QuickOpenSkippedDirs: Set<String> = setOf(
    ".git", "node_modules", ".gradle", "build", ".idea", ".dart_tool",
    "target", "dist", "out", "vendor", "Pods", ".cxx",
)

/**
 * File browser over the engine's filesystem API.
 *
 * Changed files are overlaid on the listing so the user sees what the agent touched
 * without opening a separate view. Git data is fetched once per directory load
 * rather than per row: one request instead of N.
 */
class ExplorerViewModel(
    private val api: EngineApi,
    private val workspace: () -> String,
) : ViewModel() {

    private val _state = MutableStateFlow(ExplorerUiState())
    val state: StateFlow<ExplorerUiState> = _state.asStateFlow()

    private var currentPath: String = ""

    /** In-flight directory load, cancelled when a fresh load starts. */
    private var loadJob: Job? = null

    /**
     * Increments on every load(): a load whose calls were already in flight
     * when a newer load started must drop its results instead of
     * overwriting the newer listing (item 26). Cancellation alone does not
     * cover it — a blocked HTTP call returns after its job was cancelled,
     * and the code after it still runs.
     */
    private var loadGeneration = 0

    /** Quick-open index state: built lazily on first palette open, not on every listing. */
    private var quickOpenLoaded: Boolean = false
    private var quickOpenLoading: Boolean = false

    /** Workspace the cached index was built for; an index from another workspace is stale. */
    private var quickOpenDirectory: String? = null

    /** In-flight index walk, cancelled when the index is invalidated. */
    private var quickOpenJob: Job? = null

    fun open(path: String = "") {
        currentPath = path
        load()
    }

    /**
     * Builds the workspace-wide file index quick-open searches, by walking the tree
     * breadth-first through the same list endpoint the explorer uses.
     *
     * Bounded on purpose: at most [QuickOpenMaxDirs] directories and
     * [QuickOpenMaxFiles] files, generated/dependency directories skipped, so a
     * large monorepo cannot turn opening the palette into hundreds of requests.
     * The index is a snapshot — files created after it is built appear once the
     * root listing reloads (which clears it) and the palette is opened again.
     */
    fun ensureQuickOpenIndex() {
        val directory = workspace()
        if (quickOpenLoading) return
        // An index built for another workspace is stale, not loaded: the
        // workspace can change without this view model being told (the
        // folder picker switches it from the activity), so the cached
        // index is only trusted for the directory it was built for.
        if (quickOpenLoaded && quickOpenDirectory == directory) return
        if (quickOpenLoaded) invalidateQuickOpenIndex()
        val handle = api.handle() ?: return
        quickOpenLoading = true
        _state.value = _state.value.copy(quickOpenLoading = true, quickOpenFailed = false)

        quickOpenJob = viewModelScope.launch {
            val files = FileApi(handle.client)
            val found = mutableListOf<FileRow>()
            val queue = ArrayDeque<String>()
            queue.add("")
            var dirsVisited = 0
            // One unreadable directory used to poison the whole index
            // silently: its files were missing and the walk still counted
            // as loaded, so no retry ever happened (item 27).
            var hadFailure = false
            while (queue.isNotEmpty() && found.size < QuickOpenMaxFiles && dirsVisited < QuickOpenMaxDirs) {
                val dir = queue.removeFirst()
                dirsVisited++
                val entries = when (val result = files.list(directory, dir)) {
                    is ApiResult.Success -> result.value
                    is ApiResult.Failure -> {
                        hadFailure = true
                        continue
                    }
                }
                for (entry in entries) {
                    val relative = entry.path.trimEnd('/')
                    if (entry.isDirectory) {
                        if (entry.name !in QuickOpenSkippedDirs && !entry.name.startsWith(".")) {
                            queue.add(relative)
                        }
                    } else if (found.size < QuickOpenMaxFiles) {
                        found.add(FileRow(path = relative, name = entry.name, isDirectory = false))
                    }
                }
            }
            // The workspace may have changed during the walk: an index built
            // for the old directory must not land on the new one (item 26).
            // The loading flag is still cleared, so the next palette open
            // starts a fresh walk for the current workspace.
            quickOpenLoading = false
            if (directory != workspace()) {
                _state.value = _state.value.copy(quickOpenLoading = false)
                return@launch
            }
            // The partial index is still published — what was found is
            // searchable — but it does not count as loaded, so the next
            // palette open retries the walk instead of serving a partial
            // index as complete.
            quickOpenLoaded = !hadFailure
            quickOpenDirectory = directory
            _state.value = _state.value.copy(
                quickOpenIndex = found.sortedBy { it.path.lowercase() },
                quickOpenLoading = false,
                quickOpenFailed = hadFailure,
            )
        }
    }

    /**
     * Drops the cached quick-open index so the next palette open rebuilds
     * it — after a save (the file may be new) or a workspace change
     * (item 27). Cancels a walk in flight, so it cannot publish a
     * snapshot for the workspace that just went away.
     */
    fun invalidateQuickOpenIndex() {
        quickOpenJob?.cancel()
        quickOpenJob = null
        quickOpenLoaded = false
        quickOpenLoading = false
        quickOpenDirectory = null
        _state.value = _state.value.copy(
            quickOpenIndex = emptyList(),
            quickOpenLoading = false,
            quickOpenFailed = false,
        )
    }

    fun load() {
        val handle = api.handle() ?: run {
            _state.value = _state.value.copy(loading = false, failureKey = R.string.error_engine_not_running)
            return
        }
        val directory = workspace()
        // Captured now: the launch below runs later, and navigating in the
        // meantime must not make this load list the new directory while the
        // state still describes the old one (item 26).
        val path = currentPath
        val files = FileApi(handle.client)
        val vcs = VcsApi(handle.client)
        val generation = ++loadGeneration
        loadJob?.cancel()

        // A root reload means the tree may have changed (the agent writes files),
        // so the quick-open snapshot is dropped and rebuilt on next palette open.
        if (path.isEmpty()) invalidateQuickOpenIndex()
        _state.value = _state.value.copy(
            loading = true,
            failure = null, failureKey = null,
            currentPath = path,
            breadcrumbs = buildCrumbs(path),
            quickOpenIndex = if (path.isEmpty()) emptyList() else _state.value.quickOpenIndex,
        )

        loadJob = viewModelScope.launch {
            val entries = when (val result = files.list(directory, path)) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> {
                    if (generation != loadGeneration) return@launch
                    _state.value = _state.value.copy(loading = false, failure = result)
                    return@launch
                }
            }

            // One status call per directory, not per file.
            val statusResult = vcs.status(directory)
            val changed = when (statusResult) {
                is ApiResult.Success -> statusResult.value.associateBy { it.file }
                is ApiResult.Failure -> emptyMap()
            }

            // Status returns 200 with an empty array whether the tree is clean or the
            // directory is not a repository, so the repository check is separate.
            val isRepo = files.hasRepository(directory)
            val gitAvailable = isRepo && changed.isNotEmpty()

            if (generation != loadGeneration) return@launch
            _state.value = _state.value.copy(
                loading = false,
                rows = sortEntries(entries, changed),
                changed = changed,
                isRepository = isRepo,
                vcsNote = when {
                    !isRepo -> VcsNote.NOT_A_REPOSITORY
                    changed.isEmpty() -> VcsNote.NO_CHANGES
                    else -> null
                },
                failure = null, failureKey = null,
            )
        }
    }

    /**
     * Directories first, then files, each alphabetical.
     *
     * This is the ordering every file manager uses; mixing them makes a project
     * with many files hard to scan.
     */
    private fun sortEntries(entries: List<FsEntry>, changed: Map<String, VcsFileStatus>): List<FileRow> {
        // `fs/list` returns each entry's `path` relative to the project root even when a
        // `path=` subdirectory was requested — `?path=src` answers with `src/index.js`,
        // not `index.js`. Prepending `currentPath` here therefore produced
        // `src/src/index.js` and every file below the root failed to open (§2.66).
        //
        // Status paths are relative to the repository root, which is the same frame the
        // entries are already in, so the lookup needs no adjustment either.
        return entries
            .map { entry ->
                val relative = entry.path.trimEnd('/')
                val status = changed[relative]
                FileRow(
                    path = relative,
                    name = entry.name,
                    isDirectory = entry.isDirectory,
                    gitStatus = status?.status,
                    additions = status?.additions ?: 0,
                    deletions = status?.deletions ?: 0,
                )
            }
            .sortedWith(compareByDescending<FileRow> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun buildCrumbs(path: String): List<ExplorerUiState.Crumb> {
        if (path.isEmpty()) return emptyList()
        val parts = path.split('/').filter { it.isNotEmpty() }
        return parts.indices.map { index ->
            ExplorerUiState.Crumb(
                label = parts[index],
                path = parts.take(index + 1).joinToString("/"),
            )
        }
    }

    /** Navigates up one level; returns false when already at the root. */
    fun up(): Boolean {
        if (currentPath.isEmpty()) return false
        currentPath = currentPath.substringBeforeLast('/', "")
        load()
        return true
    }
}

/** Reads one file for the viewer. */
class FileViewViewModel(
    private val api: EngineApi,
    private val workspace: () -> String,
    /** Called after a successful write: the file may be new to the index. */
    private val onSaved: () -> Unit = {},
    /**
     * Optional handle that owns the unsaved draft across process death.
     * Null when the caller constructs the view model without one (the draft
     * then lives only in [FileViewState], as before).
     */
    private val savedStateHandle: SavedStateHandle? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(FileViewState())
    val state: StateFlow<FileViewState> = _state.asStateFlow()

    /**
     * Draft left behind by the last process (audit-70 69-F3): the file, the
     * unsaved text, and whether edit mode was on. Applied when the file is
     * next loaded successfully — the content is re-read from the engine,
     * only the draft is restored — and dropped when another file is opened.
     */
    private var restoredPath: String? = savedStateHandle?.get<String>(FILE_DRAFT_PATH_KEY)
    private var restoredDraft: String? = savedStateHandle?.get<String>(FILE_DRAFT_TEXT_KEY)
    private var restoredEditing: Boolean =
        savedStateHandle?.get<Boolean>(FILE_DRAFT_EDITING_KEY) ?: false
    /** SHA-256 of the content the restored draft was based on (89-F3). */
    private var restoredBasisHash: String? = savedStateHandle?.get<String>(FILE_DRAFT_BASE_KEY)

    /**
     * Increments on every save start and every discard: a save whose generation
     * moved must not write or apply results afterwards (35-F8). Internal so a
     * test can pin down that discarding really cancels an in-flight save.
     */
    internal var saveGeneration = 0

    /** Writes the live draft into the handle, or clears it when not editing. */
    private fun persistDraft() {
        val handle = savedStateHandle ?: return
        val s = _state.value
        if (s.editing) {
            handle[FILE_DRAFT_PATH_KEY] = s.path
            handle[FILE_DRAFT_TEXT_KEY] = s.draft
            handle[FILE_DRAFT_EDITING_KEY] = true
            // The content the draft was made against (89-F3). A draft restored
            // on top of newer disk content keeps its original basis:
            // recomputing it from the new content would launder the draft
            // as fresh, and a later restore would apply it silently.
            if (!s.draftStale) handle[FILE_DRAFT_BASE_KEY] = sha256Hex(s.content)
        } else {
            clearPersistedDraft()
        }
    }

    /** Drops the persisted draft and any unrestored one with it. */
    private fun clearPersistedDraft() {
        savedStateHandle?.remove<String>(FILE_DRAFT_PATH_KEY)
        savedStateHandle?.remove<String>(FILE_DRAFT_TEXT_KEY)
        savedStateHandle?.remove<Boolean>(FILE_DRAFT_EDITING_KEY)
        savedStateHandle?.remove<String>(FILE_DRAFT_BASE_KEY)
        restoredPath = null
        restoredDraft = null
        restoredEditing = false
        restoredBasisHash = null
    }

    companion object {
        internal const val FILE_DRAFT_PATH_KEY = "fileview_path"
        internal const val FILE_DRAFT_TEXT_KEY = "fileview_draft"
        internal const val FILE_DRAFT_EDITING_KEY = "fileview_editing"
        /** SHA-256 of the content the persisted draft was based on (89-F3). */
        internal const val FILE_DRAFT_BASE_KEY = "fileview_basis"
    }

    /** In-flight file read, cancelled when another file is opened. */
    private var loadJob: Job? = null

    fun load(path: String) {
        load(path, allowFull = false)
    }

    /**
     * Loads [path] unless unsaved edits would be lost by doing so (item 10).
     * Returns false when the switch is held for the user's decision: the
     * viewer keeps showing the current file and [FileViewState.confirmSwitchTo]
     * names the file that was asked for.
     */
    fun requestLoad(path: String): Boolean {
        val s = _state.value
        if (path == s.path) return true
        if (s.dirty) {
            _state.value = s.copy(confirmSwitchTo = path)
            return false
        }
        load(path)
        return true
    }

    /** Discards the unsaved draft and opens the file held by [requestLoad]. */
    fun confirmSwitchDiscard() {
        val pending = _state.value.confirmSwitchTo ?: return
        stopEditing()
        load(pending)
    }

    /** Keeps the current file and drops the held switch request. */
    fun cancelSwitch() {
        _state.value = _state.value.copy(confirmSwitchTo = null)
    }

    /**
     * Loads the full file after the large-file guard truncated it. This re-reads
     * from the engine: the preview deliberately dropped the rest, so there is
     * nothing to restore from. Only ever called from the banner's explicit action.
     */
    fun loadFull() {
        val s = _state.value
        if (!s.truncated || s.loading) return
        load(s.path, allowFull = true)
    }

    private fun load(path: String, allowFull: Boolean) {
        // A draft restored after process death belongs to its file: opening
        // another file abandons it (audit-70 69-F3).
        if (restoredPath != null && restoredPath != path) clearPersistedDraft()
        // Never overwrite an unsaved draft with another file's content
        // (item 10): every load path goes through requestLoad, and this
        // guard is the backstop for any caller that forgot.
        if (_state.value.dirty) return
        val handle = api.handle() ?: run {
            _state.value = FileViewState(loading = false, path = path, failureKey = R.string.error_engine_not_running)
            return
        }
        _state.value = FileViewState(loading = true, path = path)

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = FileApi(handle.client).readText(workspace(), path)
            // Another file was opened while this read was in flight: its
            // content must not land in the viewer afterwards (item 26).
            // The state path is set synchronously by every load(), so a
            // newer load is visible here even if this job was never
            // cancelled in time.
            if (_state.value.path != path) return@launch
            when (result) {
                is ApiResult.Success -> {
                    val file = result.value
                    // A capped read is a preview only (59-F5): loading it
                    // whole for editing would save the kept prefix over
                    // the real file. Refuse with a plain-language failure
                    // instead of silently truncating on save.
                    if (allowFull && file.truncated) {
                        _state.value = FileViewState(
                            loading = false,
                            path = path,
                            failureKey = R.string.fileview_too_large_to_edit,
                        )
                        return@launch
                    }
                    // Large files are shown as a preview, never held in full by
                    // default — see LargeFileGuard. The full line count is kept so
                    // the banner can say how much was held back.
                    val (shown, totalLines) = LargeFileGuard.preview(file.text, file.totalLines)
                    // Line-based preview: only actually cut when there are more
                    // lines than the preview keeps (see LargeFileGuard).
                    val truncated = !allowFull &&
                        LargeFileGuard.isLarge(file.text) &&
                        totalLines > LargeFileGuard.PreviewLines
                    // A draft restored after process death comes back with the
                    // file it belongs to; a truncated preview must never
                    // become a draft, so a restored draft is only applied to
                    // a whole file (loadFull applies it later).
                    //
                    // The draft is only fresh when the file still holds the
                    // content it was made against (89-F3). When the agent
                    // changed the file while the app was dead, the draft is
                    // restored as stale instead: it stays editable, but it is
                    // flagged and only an explicit Overwrite may write it —
                    // a plain Save would silently replace the agent's newer
                    // work with a draft based on an older version.
                    val restore = resolveRestoreDecision(
                        restoredPath = restoredPath,
                        restoredEditing = restoredEditing,
                        truncated = truncated,
                        path = path,
                        diskContent = file.text,
                        basisHash = restoredBasisHash,
                    )
                    val draftToRestore =
                        if (restore == RestoreDecision.Apply) restoredDraft else null
                    val staleDraft =
                        if (restore == RestoreDecision.StaleConflict) restoredDraft else null
                    _state.value = FileViewState(
                        loading = false,
                        path = path,
                        content = if (truncated) shown else file.text,
                        truncated = truncated,
                        totalLines = if (truncated) totalLines else 0,
                        editing = draftToRestore != null || staleDraft != null,
                        draft = draftToRestore ?: staleDraft ?: "",
                        conflict = staleDraft != null,
                        draftStale = staleDraft != null,
                    )
                    if (draftToRestore != null || staleDraft != null) {
                        restoredPath = null
                        restoredDraft = null
                        restoredEditing = false
                        restoredBasisHash = null
                    }
                }
                is ApiResult.Failure -> _state.value = FileViewState(
                    loading = false,
                    path = path,
                    failure = result,
                )
            }
        }
    }

    fun startEditing() {
        val s = _state.value
        if (s.loading || s.failure != null || s.failureKey != null || s.editing) return
        // A truncated preview must never become a draft: saving it would write
        // the preview back over the real file. Load the full file first.
        if (s.truncated) return
        // A fresh edit starts from what is on screen, so its basis is current.
        _state.value = s.copy(editing = true, draft = s.content, saved = false, conflict = false, draftStale = false)
        persistDraft()
    }

    fun editDraft(text: String) {
        _state.value = _state.value.copy(draft = text, saved = false)
        persistDraft()
    }

    /** Leaves the editor without writing. The draft goes with it. */
    fun stopEditing() {
        // Discarding cancels a save in flight (35-F8): the save coroutine
        // checks the generation before it writes and before it applies
        // anything, so a discarded draft can no longer reach the disk.
        saveGeneration++
        _state.value = _state.value.copy(editing = false, draft = "", conflict = false, saving = false, draftStale = false)
        clearPersistedDraft()
    }

    /**
     * Writes the draft, unless something has changed the file first.
     *
     * [overwrite] is only ever set from the conflict dialog. A save always re-reads
     * first: without that there is no way to know whether the draft is being written
     * over the agent's work, and the engine's write endpoint replaces the whole file.
     *
     * Honest limit (audit-100 35-F2/89-F1): the check and the write are two separate
     * requests and the engine offers no conditional write, so an agent edit that
     * lands between the re-read and the write is still silently overwritten — the
     * post-write re-read below only catches writes that landed AFTER our write.
     * What this save does guarantee: typing that lands while it runs is never
     * discarded (35-F1), its result never lands in another file's viewer (35-F3),
     * and discarding the draft cancels it before it writes (35-F8).
     */
    fun save(overwrite: Boolean = false) {
        val s = _state.value
        if (!s.editing || s.saving) return
        // A draft restored on top of newer disk content (89-F3) may only be
        // written by an explicit Overwrite: a plain Save would silently
        // replace the agent's newer work with a draft based on an older
        // version. Show the conflict dialog instead.
        if (resolveSaveStart(s, overwrite) == SaveStart.StaleConflict) {
            _state.value = s.copy(conflict = true)
            return
        }
        val handle = api.handle() ?: run {
            _state.value = s.copy(failureKey = R.string.error_engine_not_running)
            return
        }
        val directory = workspace()
        val generation = ++saveGeneration
        _state.value = s.copy(saving = true, failure = null, failureKey = null, conflict = false)

        viewModelScope.launch {
            val files = FileApi(handle.client)
            val onDisk = (files.readText(directory, s.path) as? ApiResult.Success)?.value?.text
            // Discarded while the re-read was in flight (35-F8): never write.
            if (generation != saveGeneration) return@launch
            when (saveAction(s.content, s.draft, onDisk, overwrite)) {
                // Nothing to write, but the view is stale: reload so the user sees what
                // is actually in the file rather than what was loaded when they opened it.
                SaveAction.Unchanged -> {
                    // The viewer moved on while the re-read ran (35-F3):
                    // reloading the saved file here would yank it back.
                    if (_state.value.path != s.path) return@launch
                    _state.value = _state.value.copy(saving = false)
                    // A file switch was held for this save (35-F4): the draft
                    // equals what is on disk, so open the file asked for
                    // instead of reloading this one and dropping the request.
                    val pending = _state.value.confirmSwitchTo
                    if (pending != null) load(pending) else load(s.path)
                }

                SaveAction.Unverifiable -> {
                    if (_state.value.path == s.path) {
                        _state.value = _state.value.copy(
                            saving = false,
                            failureKey = R.string.error_reread_failed,
                        )
                    }
                }

                SaveAction.Conflict -> {
                    if (_state.value.path == s.path) {
                        _state.value = _state.value.copy(
                            saving = false,
                            conflict = true,
                        )
                    }
                }

                SaveAction.Write -> when (val result = files.writeText(directory, s.path, s.draft)) {
                    // The draft survives a failed write: losing typed work on a network
                    // error would be worse than the error itself.
                    is ApiResult.Failure -> {
                        if (_state.value.path == s.path && generation == saveGeneration) {
                            _state.value = _state.value.copy(
                                saving = false,
                                failure = result,
                            )
                        }
                    }
                    is ApiResult.Success -> {
                        // Discarded while the write was in flight (35-F8): the
                        // write already happened, so there is nothing to
                        // undo — but its result must not touch the viewer.
                        if (generation != saveGeneration) return@launch
                        // Read the file back: a mismatch means the file moved
                        // under this save after our write, so report a
                        // conflict instead of marking it saved — the draft is
                        // kept, the agent's version stands on disk, and the
                        // user decides. A failed re-read cannot prove a move,
                        // so the successful write stands (the draft is on
                        // disk either way). This does NOT cover a write
                        // between the pre-write re-read and this write (see
                        // the KDoc above): that edit was already replaced
                        // before this read ran.
                        val after = (files.readText(directory, s.path) as? ApiResult.Success)?.value?.text
                        if (after != null && after != s.draft) {
                            if (_state.value.path == s.path) {
                                _state.value = _state.value.copy(saving = false, conflict = true)
                            }
                        } else {
                            val resolved = resolveSaveSuccess(_state.value, s)
                            if (resolved != null) {
                                _state.value = resolved
                                if (!resolved.editing) {
                                    // The draft is on disk now: a later process death
                                    // must not resurrect it (audit-70 69-F3).
                                    clearPersistedDraft()
                                    // A file switch was held for this save (item 10): the
                                    // draft is written now, so open the file asked for.
                                    _state.value.confirmSwitchTo?.let { load(it) }
                                }
                                // When typing landed during the save (35-F1),
                                // the new draft stays persisted and editing
                                // continues on top of what is now on disk.
                            }
                            // The file was written either way — it may be new to the
                            // quick-open index, which only lists paths (item 27).
                            onSaved()
                        }
                    }
                }
            }
        }
    }

    /** Drops the draft and shows what the file holds now. */
    fun reloadFromDisk() {
        // Discarding cancels a save in flight, like stopEditing (35-F8).
        saveGeneration++
        val s = _state.value
        _state.value = s.copy(editing = false, draft = "", conflict = false, saving = false, draftStale = false)
        clearPersistedDraft()
        load(s.path)
    }

    /**
     * Retries a load that has not finished yet (35-F10/89-F2).
     *
     * Unlike [reloadFromDisk] this keeps the persisted draft: a draft restored
     * after process death has not been applied yet while the file is still
     * loading, and clearing it here would delete work the user never saw again.
     * The retry only re-runs the read — [load] preserves a restored draft for
     * the same path and applies it when the read succeeds.
     */
    fun retryLoad() {
        val path = _state.value.path
        if (path.isEmpty()) return
        load(path)
    }

    /** Keeps editing after a conflict, so the difference can be looked at by hand. */
    fun dismissConflict() {
        _state.value = _state.value.copy(conflict = false)
    }
}

/** Diff for one file. */
data class DiffUiState(
    val loading: Boolean = true,
    val file: String = "",
    val patch: String = "",
    val hunks: List<dev.bidzz.opencode.ui.diff.DiffHunk> = emptyList(),
    val additions: Int = 0,
    val deletions: Int = 0,
    val truncated: Boolean = false,
    /** What went wrong, as a value; the screen words it in the app’s language. */
    val failure: ApiResult.Failure? = null,
    /** A message with no failure behind it, e.g. the engine never started. */
    val failureKey: Int? = null,
    /** True when the engine has no git, so no diff can be produced. */
    val gitUnavailable: Boolean = false,
)

class DiffViewModel(
    private val api: EngineApi,
    private val workspace: () -> String,
) : ViewModel() {

    private val _state = MutableStateFlow(DiffUiState())
    val state: StateFlow<DiffUiState> = _state.asStateFlow()

    /** In-flight diff load, cancelled when another file's diff is asked for. */
    private var loadJob: Job? = null

    fun load(file: String) {
        val handle = api.handle() ?: run {
            _state.value = _state.value.copy(loading = false, failureKey = R.string.error_engine_not_running)
            return
        }
        _state.value = DiffUiState(loading = true, file = file)

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val vcs = VcsApi(handle.client)
            val result = vcs.diff(workspace())
            // Another file's diff was asked for while this one was in
            // flight: it must not overwrite the newer file's diff (item 26).
            if (_state.value.file != file) return@launch
            when (result) {
                is ApiResult.Success -> {
                    val info: FileDiffInfo? = result.value.firstOrNull { it.file == file }
                    if (info == null) {
                        // Either the file is unchanged, or git is not present. The
                        // engine returns an empty list for both, so ask directly
                        // rather than showing an empty diff and calling it "no changes".
                        val isRepo = vcs.hasRepository(workspace())
                        if (_state.value.file != file) return@launch
                        _state.value = _state.value.copy(
                            loading = false,
                            gitUnavailable = !isRepo,
                            failureKey = if (isRepo) R.string.diff_none else null,
                        )
                        return@launch
                    }
                    val hunks = dev.bidzz.opencode.ui.diff.DiffParser.parse(info.patch)
                    _state.value = _state.value.copy(
                        loading = false,
                        patch = info.patch,
                        hunks = hunks,
                        additions = info.additions,
                        deletions = info.deletions,
                        truncated = info.more == true,
                        failure = null, failureKey = null,
                    )
                }
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        failure = result,
                        gitUnavailable = result.code == 404,
                    )
                }
            }
        }
    }
}

/** SHA-256 of [text] as lowercase hex, for draft-basis checks (89-F3). */
internal fun sha256Hex(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

/** Whether a save may start, or must show the conflict dialog instead. */
internal enum class SaveStart { Proceed, StaleConflict }

/**
 * A draft restored on top of newer disk content (89-F3) may only be written by
 * an explicit Overwrite: a plain Save would silently replace the agent's newer
 * work with a draft based on an older version.
 */
internal fun resolveSaveStart(state: FileViewState, overwrite: Boolean): SaveStart =
    if (state.draftStale && !overwrite) SaveStart.StaleConflict else SaveStart.Proceed

/**
 * Applies a finished write to the live viewer state (35-F1, 35-F3).
 *
 * Returns null when the viewer has moved on to another file: the write stands,
 * but its result must not land in that file's viewer. Otherwise returns the new
 * state: when the user typed while the save ran, the fresh typing is kept and
 * editing continues on top of what is now on disk; only when the draft is
 * untouched is it cleared and the file marked saved.
 */
internal fun resolveSaveSuccess(now: FileViewState, snapshot: FileViewState): FileViewState? {
    if (now.path != snapshot.path) return null
    return if (now.draft != snapshot.draft) {
        now.copy(saving = false, content = snapshot.draft, saved = false)
    } else {
        now.copy(
            saving = false,
            editing = false,
            conflict = false,
            content = snapshot.draft,
            draft = "",
            saved = true,
            draftStale = false,
        )
    }
}

/** What loading a file should do with a draft restored after process death. */
internal enum class RestoreDecision {
    /** The file still holds the content the draft was made against: apply it. */
    Apply,
    /** The file moved on since the draft was made: restore it as stale (89-F3). */
    StaleConflict,
    /** No draft to restore for this file (wrong file, not editing, truncated). */
    Skip,
}

/**
 * Decides a draft restore from its basis (89-F3). A handle written before basis
 * tracking has no hash, so it restores as before rather than being discarded.
 */
internal fun resolveRestoreDecision(
    restoredPath: String?,
    restoredEditing: Boolean,
    truncated: Boolean,
    path: String,
    diskContent: String,
    basisHash: String?,
): RestoreDecision {
    if (restoredPath != path || !restoredEditing || truncated) return RestoreDecision.Skip
    if (basisHash == null) return RestoreDecision.Apply
    return if (basisHash == sha256Hex(diskContent)) RestoreDecision.Apply else RestoreDecision.StaleConflict
}
