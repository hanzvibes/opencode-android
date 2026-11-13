package dev.bidzz.opencode.ui.diff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.R
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.SessionApi
import dev.bidzz.opencode.core.api.dto.FileDiffInfo
import dev.bidzz.opencode.core.api.dto.VcsFileStatus
import dev.bidzz.opencode.ui.files.DiffUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the turn-diff screen renders. */
data class SessionDiffUiState(
    val loading: Boolean = true,
    val files: List<FileDiffInfo> = emptyList(),
    val failure: ApiResult.Failure? = null,
    /** A message with no failure behind it, e.g. the engine never started. */
    val failureKey: Int? = null,
    /** Single-file view, built from [files]; null while the list shows. */
    val selected: DiffUiState? = null,
) {
    /** The list screen's input: the same rows the working-tree list shows. */
    val changed: Map<String, VcsFileStatus>
        get() = files.associateBy({ it.file }, { it.toVcsFileStatus() })
}

/** One session-diff file as a working-tree list row. */
internal fun FileDiffInfo.toVcsFileStatus(): VcsFileStatus =
    VcsFileStatus(file = file, additions = additions, deletions = deletions, status = status)

/**
 * Latest turn's file diffs for one session (`GET /api/session/{id}/diff`).
 *
 * The list and the single-file view share one load: the file's patch comes
 * from the loaded list, never from a second fetch, so the patch the viewer
 * shows is the one the list counted.
 */
class SessionDiffViewModel(
    private val api: EngineApi,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionDiffUiState())
    val state: StateFlow<SessionDiffUiState> = _state.asStateFlow()

    /** In-flight diff load, cancelled when another session's diff is asked for. */
    private var loadJob: Job? = null

    /**
     * Increments on every load(): a load whose network call was already in
     * flight when a newer load started must drop its result instead of
     * overwriting the newer session's diff. Cancellation alone does not
     * cover it — a blocked HTTP call returns after its job was cancelled,
     * and the code after it still runs (80-F10; the same generation guard
     * SessionListViewModel.load uses for the list).
     */
    private var loadGeneration = 0

    fun load(sessionID: String) {
        val handle = api.handle() ?: run {
            _state.value = SessionDiffUiState(loading = false, failureKey = R.string.error_engine_not_running)
            return
        }
        val generation = ++loadGeneration
        _state.value = SessionDiffUiState(loading = true)

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when (val result = SessionApi(handle.client).diff(sessionID)) {
                is ApiResult.Success -> {
                    if (generation != loadGeneration) return@launch
                    _state.value = SessionDiffUiState(loading = false, files = result.value)
                }
                is ApiResult.Failure -> {
                    if (generation != loadGeneration) return@launch
                    _state.value = SessionDiffUiState(loading = false, failure = result)
                }
            }
        }
    }

    /** Opens one file's patch from the loaded list — no second fetch. */
    fun openFile(file: String) {
        val info = _state.value.files.firstOrNull { it.file == file } ?: return
        val hunks = DiffParser.parse(info.patch)
        _state.value = _state.value.copy(
            selected = DiffUiState(
                loading = false,
                file = info.file,
                patch = info.patch,
                hunks = hunks,
                additions = info.additions,
                deletions = info.deletions,
                truncated = info.more == true,
            ),
        )
    }

    fun closeFile() {
        _state.value = _state.value.copy(selected = null)
    }
}
