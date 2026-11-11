package dev.bidzz.opencode.ui.files

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

    /** Quick-open index state: built lazily on first palette open, not on every listing. */
    private var quickOpenLoaded: Boolean = false
    private var quickOpenLoading: Boolean = false

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
        if (quickOpenLoaded || quickOpenLoading) return
        val handle = api.handle() ?: return
        val directory = workspace()
        quickOpenLoading = true
        _state.value = _state.value.copy(quickOpenLoading = true)

        viewModelScope.launch {
            val files = FileApi(handle.client)
            val found = mutableListOf<FileRow>()
            val queue = ArrayDeque<String>()
            queue.add("")
            var dirsVisited = 0
            while (queue.isNotEmpty() && found.size < QuickOpenMaxFiles && dirsVisited < QuickOpenMaxDirs) {
                val dir = queue.removeFirst()
                dirsVisited++
                val entries = when (val result = files.list(directory, dir)) {
                    is ApiResult.Success -> result.value
                    is ApiResult.Failure -> continue
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
            quickOpenLoaded = true
            quickOpenLoading = false
            _state.value = _state.value.copy(
                quickOpenIndex = found.sortedBy { it.path.lowercase() },
                quickOpenLoading = false,
            )
        }
    }

    fun load() {
        val handle = api.handle() ?: run {
            _state.value = _state.value.copy(loading = false, failureKey = R.string.error_engine_not_running)
            return
        }
        val directory = workspace()
        val files = FileApi(handle.client)
        val vcs = VcsApi(handle.client)

        // A root reload means the tree may have changed (the agent writes files),
        // so the quick-open snapshot is dropped and rebuilt on next palette open.
        if (currentPath.isEmpty()) quickOpenLoaded = false
        _state.value = _state.value.copy(
            loading = true,
            failure = null, failureKey = null,
            currentPath = currentPath,
            breadcrumbs = buildCrumbs(currentPath),
            quickOpenIndex = if (currentPath.isEmpty()) emptyList() else _state.value.quickOpenIndex,
        )

        viewModelScope.launch {
            val entries = when (val result = files.list(directory, currentPath)) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> {
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
) : ViewModel() {

    private val _state = MutableStateFlow(FileViewState())
    val state: StateFlow<FileViewState> = _state.asStateFlow()

    fun load(path: String) {
        load(path, allowFull = false)
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
        val handle = api.handle() ?: run {
            _state.value = FileViewState(loading = false, path = path, failureKey = R.string.error_engine_not_running)
            return
        }
        _state.value = FileViewState(loading = true, path = path)

        viewModelScope.launch {
            when (val result = FileApi(handle.client).readText(workspace(), path)) {
                is ApiResult.Success -> {
                    // Large files are shown as a preview, never held in full by
                    // default — see LargeFileGuard. The full line count is kept so
                    // the banner can say how much was held back.
                    val (shown, totalLines) = LargeFileGuard.preview(result.value)
                    // Line-based preview: only actually cut when there are more
                    // lines than the preview keeps (see LargeFileGuard).
                    val truncated = !allowFull &&
                        LargeFileGuard.isLarge(result.value) &&
                        totalLines > LargeFileGuard.PreviewLines
                    _state.value = FileViewState(
                        loading = false,
                        path = path,
                        content = if (truncated) shown else result.value,
                        truncated = truncated,
                        totalLines = if (truncated) totalLines else 0,
                    )
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
        _state.value = s.copy(editing = true, draft = s.content, saved = false, conflict = false)
    }

    fun editDraft(text: String) {
        _state.value = _state.value.copy(draft = text, saved = false)
    }

    /** Leaves the editor without writing. The draft goes with it. */
    fun stopEditing() {
        _state.value = _state.value.copy(editing = false, draft = "", conflict = false, saving = false)
    }

    /**
     * Writes the draft, unless something has changed the file first.
     *
     * [overwrite] is only ever set from the conflict dialog. A save always re-reads
     * first: without that there is no way to know whether the draft is being written
     * over the agent's work, and the engine's write endpoint replaces the whole file.
     */
    fun save(overwrite: Boolean = false) {
        val s = _state.value
        if (!s.editing || s.saving) return
        val handle = api.handle() ?: run {
            _state.value = s.copy(failureKey = R.string.error_engine_not_running)
            return
        }
        val directory = workspace()
        _state.value = s.copy(saving = true, failure = null, failureKey = null, conflict = false)

        viewModelScope.launch {
            val files = FileApi(handle.client)
            val onDisk = (files.readText(directory, s.path) as? ApiResult.Success)?.value
            when (saveAction(s.content, s.draft, onDisk, overwrite)) {
                // Nothing to write, but the view is stale: reload so the user sees what
                // is actually in the file rather than what was loaded when they opened it.
                SaveAction.Unchanged -> {
                    _state.value = _state.value.copy(saving = false)
                    load(s.path)
                }

                SaveAction.Unverifiable -> _state.value = _state.value.copy(
                    saving = false,
                    failureKey = R.string.error_reread_failed,
                )

                SaveAction.Conflict -> _state.value = _state.value.copy(
                    saving = false,
                    conflict = true,
                )

                SaveAction.Write -> when (val result = files.writeText(directory, s.path, s.draft)) {
                    // The draft survives a failed write: losing typed work on a network
                    // error would be worse than the error itself.
                    is ApiResult.Failure -> _state.value = _state.value.copy(
                        saving = false,
                        failure = result,
                    )
                    is ApiResult.Success -> _state.value = _state.value.copy(
                        saving = false,
                        editing = false,
                        conflict = false,
                        content = s.draft,
                        draft = "",
                        saved = true,
                    )
                }
            }
        }
    }

    /** Drops the draft and shows what the file holds now. */
    fun reloadFromDisk() {
        val s = _state.value
        _state.value = s.copy(editing = false, draft = "", conflict = false, saving = false)
        load(s.path)
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

    fun load(file: String) {
        val handle = api.handle() ?: run {
            _state.value = _state.value.copy(loading = false, failureKey = R.string.error_engine_not_running)
            return
        }
        _state.value = DiffUiState(loading = true, file = file)

        viewModelScope.launch {
            val vcs = VcsApi(handle.client)
            when (val result = vcs.diff(workspace())) {
                is ApiResult.Success -> {
                    val info: FileDiffInfo? = result.value.firstOrNull { it.file == file }
                    if (info == null) {
                        // Either the file is unchanged, or git is not present. The
                        // engine returns an empty list for both, so ask directly
                        // rather than showing an empty diff and calling it "no changes".
                        val isRepo = vcs.hasRepository(workspace())
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
