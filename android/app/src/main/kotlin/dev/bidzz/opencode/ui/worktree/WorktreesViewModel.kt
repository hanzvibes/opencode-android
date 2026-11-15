package dev.bidzz.opencode.ui.worktree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.ui.engineDownFailure
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.ProjectInfo
import dev.bidzz.opencode.core.api.dto.WorktreeDirectory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One project and the worktrees saved under it. */
data class WorktreeSection(
    val projectName: String,
    val projectPath: String,
    val worktrees: List<WorktreeDirectory>,
)

data class WorktreesUiState(
    val sections: List<WorktreeSection> = emptyList(),
    /** Projects whose worktree list could not be read, with why. */
    val failedProjects: List<FailedWorktreeProject> = emptyList(),
    val loading: Boolean = true,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
)

/** One project whose worktree list could not be read. */
data class FailedWorktreeProject(
    val projectName: String,
    val projectPath: String,
    val failure: ApiResult.Failure,
)

/**
 * Groups loaded worktrees under their projects, for the screen.
 *
 * Projects with no saved worktrees are left out: the screen's empty state
 * then means "no worktrees anywhere", not "one project has none". Sections
 * and rows are sorted case-insensitively, so the list reads the same on
 * every load.
 */
internal fun buildWorktreeSections(
    projects: List<ProjectInfo>,
    worktreesByProject: Map<String, List<WorktreeDirectory>>,
): List<WorktreeSection> = projects.mapNotNull { project ->
    val worktrees = worktreesByProject[project.id].orEmpty()
    if (worktrees.isEmpty()) return@mapNotNull null
    WorktreeSection(
        projectName = project.name?.takeIf { it.isNotBlank() } ?: project.canonical,
        projectPath = project.canonical,
        worktrees = worktrees.sortedBy { it.directory.lowercase() },
    )
}.sortedBy { it.projectName.lowercase() }

/**
 * Worktrees saved on the engine, grouped by project.
 *
 * List-only: creating, removing and refreshing are writes
 * (`POST /api/worktree`, `DELETE /api/worktree`,
 * `POST /api/worktree/refresh`) the app does not call.
 *
 * The list needs one call per project. A failed project used to fail
 * the whole load instead of showing a partial list — a missing section
 * would read as "no worktrees there", which is not what a failed request
 * means. Since audit-200 C17 (M-255) the calls run in parallel and a
 * failed project is reported per project ([FailedWorktreeProject]): the
 * projects that loaded still show, and the failed one says why instead
 * of silently vanishing.
 */
class WorktreesViewModel(
    private val api: EngineApi,
) : ViewModel() {

    private val _state = MutableStateFlow(WorktreesUiState())
    val state: StateFlow<WorktreesUiState> = _state.asStateFlow()

    fun refresh() {
        val handle = api.handle()
        if (handle == null) {
            _state.value = _state.value.copy(loading = false, failure = engineDownFailure())
            return
        }
        _state.value = _state.value.copy(loading = true, failure = null)
        viewModelScope.launch {
            val projects = when (val result = handle.projects.list()) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(loading = false, failure = result)
                    return@launch
                }
            }
            // Audit-200 C17 (M-255): one request per project, fetched in
            // parallel — the old loop paid N round trips one after another,
            // and a single failure aborted the whole screen. A failed
            // project is reported per project now; the projects that
            // loaded still show.
            val results = coroutineScope {
                projects.map { project ->
                    async { project to handle.worktrees.list(project.id) }
                }.awaitAll()
            }
            val byProject = mutableMapOf<String, List<WorktreeDirectory>>()
            val failed = mutableListOf<FailedWorktreeProject>()
            for ((project, result) in results) {
                when (result) {
                    is ApiResult.Success -> byProject[project.id] = result.value
                    is ApiResult.Failure -> failed += FailedWorktreeProject(
                        projectName = project.name?.takeIf { it.isNotBlank() } ?: project.canonical,
                        projectPath = project.canonical,
                        failure = result,
                    )
                }
            }
            _state.value = _state.value.copy(
                sections = buildWorktreeSections(projects, byProject),
                failedProjects = failed.sortedBy { it.projectName.lowercase() },
                loading = false,
            )
        }
    }
}
