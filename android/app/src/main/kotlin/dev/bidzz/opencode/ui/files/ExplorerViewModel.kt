package dev.bidzz.opencode.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.api.describe
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

data class ExplorerUiState(
    val loading: Boolean = true,
    val rows: List<FileRow> = emptyList(),
    /** Relative path of the directory being shown; empty for the project root. */
    val currentPath: String = "",
    /** Breadcrumb segments, each with the path it navigates to. */
    val breadcrumbs: List<Crumb> = emptyList(),
    val error: String? = null,
    /** Files changed in the working tree, by relative path. */
    val changed: Map<String, VcsFileStatus> = emptyMap(),
    /** True when the project is a git repository the engine can read. */
    val isRepository: Boolean = false,
    /** Set when git is unavailable, so the UI can explain rather than show "clean". */
    val vcsNote: String? = null,
) {
    data class Crumb(val label: String, val path: String)
}

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

    fun open(path: String = "") {
        currentPath = path
        load()
    }

    fun load() {
        val handle = api.handle() ?: run {
            _state.value = _state.value.copy(loading = false, error = "Engine is not running")
            return
        }
        val directory = workspace()
        val files = FileApi(handle.client)
        val vcs = VcsApi(handle.client)

        _state.value = _state.value.copy(
            loading = true,
            error = null,
            currentPath = currentPath,
            breadcrumbs = buildCrumbs(currentPath),
        )

        viewModelScope.launch {
            val entries = when (val result = files.list(directory, currentPath)) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(loading = false, error = result.describe())
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
                    !isRepo -> "Not a git repository — changes are not tracked."
                    changed.isEmpty() -> "Git repository with no uncommitted changes."
                    else -> null
                },
                error = null,
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
        // Status paths are relative to the repository root, while entries are
        // relative to the directory being shown.
        val prefix = if (currentPath.isEmpty()) "" else "$currentPath/"
        return entries
            .map { entry ->
                val relative = if (currentPath.isEmpty()) entry.path.trimEnd('/')
                else "$prefix${entry.path.trimEnd('/')}"
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
        val handle = api.handle() ?: run {
            _state.value = FileViewState(loading = false, path = path, error = "Engine is not running")
            return
        }
        _state.value = FileViewState(loading = true, path = path)

        viewModelScope.launch {
            when (val result = FileApi(handle.client).readText(workspace(), path)) {
                is ApiResult.Success -> _state.value = FileViewState(
                    loading = false,
                    path = path,
                    content = result.value,
                )
                is ApiResult.Failure -> _state.value = FileViewState(
                    loading = false,
                    path = path,
                    error = result.describe(),
                )
            }
        }
    }

    fun startEditing() {
        val s = _state.value
        if (s.loading || s.error != null || s.editing) return
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
            _state.value = s.copy(error = "Engine is not running")
            return
        }
        val directory = workspace()
        _state.value = s.copy(saving = true, error = null, conflict = false)

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
                    error = "Could not re-read the file to check whether it changed, so nothing was " +
                        "written. Your text is still here.",
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
                        error = result.describe(),
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
    val error: String? = null,
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
            _state.value = _state.value.copy(loading = false, error = "Engine is not running")
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
                            error = if (isRepo) "No changes in $file" else null,
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
                        error = null,
                    )
                }
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = result.describe(),
                        gitUnavailable = result.code == 404,
                    )
                }
            }
        }
    }
}
