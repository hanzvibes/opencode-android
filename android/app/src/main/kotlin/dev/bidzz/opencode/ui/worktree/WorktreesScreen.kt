package dev.bidzz.opencode.ui.worktree

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.WorktreeDirectory
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.TextAction
import dev.bidzz.opencode.ui.message

/**
 * Worktrees saved on the engine, grouped by project.
 *
 * Read-only: the list comes from `GET /api/project` + one
 * `GET /api/worktree?projectID=…` per project. Creating, removing and
 * refreshing worktrees are not available from the app yet.
 */
@Composable
fun WorktreesScreen(
    state: WorktreesUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    LaunchedEffect(Unit) { onRefresh() }

    ScreenScaffold(
        title = stringResource(R.string.worktree_title),
        onBack = onBack,
        contentMaxWidth = 720.dp,
        actions = {
            // Audit-200 C17 (M-261): the list used to refresh only on
            // entry — a worktree added outside the app stayed invisible
            // until the screen was left and re-entered.
            TextAction(
                label = stringResource(R.string.common_refresh),
                onClick = onRefresh,
                enabled = !state.loading,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(R.string.worktree_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(
                    horizontal = spacing.lg,
                    vertical = spacing.sm,
                ),
            )
            when {
                state.loading -> LoadingState(stringResource(R.string.common_loading), onRetry = onRefresh)
                state.failure != null -> EmptyState(state.failure.message(), isError = true, onRetry = onRefresh)
                state.sections.isEmpty() && state.failedProjects.isEmpty() -> EmptyState(stringResource(R.string.worktree_empty))
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.lg),
                    ) {
                        state.sections.forEachIndexed { sectionIndex, section ->
                            if (sectionIndex > 0) {
                                item(key = "divider-$sectionIndex") {
                                    RowDivider(inset = spacing.md)
                                }
                            }
                            item(key = "header-${section.projectPath}") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = spacing.sm),
                                ) {
                                    Text(
                                        text = section.projectName,
                                        style = OpenCodeTypography.body,
                                        color = colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = section.projectPath,
                                        style = OpenCodeTypography.bodySmall,
                                        color = colors.onSurfaceMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            itemsIndexed(
                                section.worktrees,
                                key = { index, worktree -> "$sectionIndex-$index:${worktree.directory}" },
                            ) { _, worktree ->
                                WorktreeRow(worktree = worktree)
                            }
                        }
                        // Audit-200 C17 (M-255): a project whose worktree
                        // list failed to load is shown, not hidden — a
                        // missing section would read as "no worktrees
                        // there", which is not what a failed request means.
                        state.failedProjects.forEachIndexed { failedIndex, failed ->
                            if (state.sections.isNotEmpty() || failedIndex > 0) {
                                item(key = "failed-divider-$failedIndex") {
                                    RowDivider(inset = spacing.md)
                                }
                            }
                            item(key = "failed-${failed.projectPath}") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = spacing.sm),
                                ) {
                                    Text(
                                        text = failed.projectName,
                                        style = OpenCodeTypography.body,
                                        color = colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = failed.projectPath,
                                        style = OpenCodeTypography.bodySmall,
                                        color = colors.onSurfaceMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = failed.failure.message(),
                                        style = OpenCodeTypography.bodySmall,
                                        color = colors.danger,
                                        modifier = Modifier.padding(top = spacing.xxs),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorktreeRow(worktree: WorktreeDirectory) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = worktree.directory,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val strategy = worktree.strategy?.takeIf { it.isNotBlank() }
            if (strategy != null) {
                Text(
                    text = strategy,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
