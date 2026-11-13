package dev.bidzz.opencode.ui.worktree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.ui.engineDownFailure
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.ProjectInfo
import dev.bidzz.opencode.core.api.dto.WorktreeDirectory
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
    val loading: Boolean = true,
    /** What went wrong, as a value; the screen words it in the app's language. */
    val failure: ApiResult.Failure? = null,
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
 * The list needs one call per project, so a failure on any call fails the
 * load instead of showing a partial list: a missing project section would
 * read as "no worktrees there", which is not what a failed request means.
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
            val byProject = mutableMapOf<String, List<WorktreeDirectory>>()
            for (project in projects) {
                when (val result = handle.worktrees.list(project.id)) {
                    is ApiResult.Success -> byProject[project.id] = result.value
                    is ApiResult.Failure -> {
                        _state.value = _state.value.copy(loading = false, failure = result)
                        return@launch
                    }
                }
            }
            _state.value = _state.value.copy(
                sections = buildWorktreeSections(projects, byProject),
                loading = false,
            )
        }
    }
}
