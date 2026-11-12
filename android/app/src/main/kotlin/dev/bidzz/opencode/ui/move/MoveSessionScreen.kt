package dev.bidzz.opencode.ui.move

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.ProjectInfo
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.message

/**
 * Moves one session to another project.
 *
 * The destination is picked from the engine's project list; the current
 * project is shown and cannot be picked. Moving keeps the session's
 * history — only its project changes — and answers 204 with no body, so
 * the screen reports success from the call itself, then hands back to
 * the caller to refresh the session list.
 */
@Composable
fun MoveSessionScreen(
    sessionID: String,
    state: MoveSessionUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onMove: () -> Unit,
    onMoved: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    LaunchedEffect(sessionID) { onRefresh() }

    ScreenScaffold(
        title = stringResource(R.string.move_title),
        onBack = onBack,
        contentMaxWidth = 720.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(R.string.move_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(
                    horizontal = spacing.lg,
                    vertical = spacing.sm,
                ),
            )
            when {
                state.loading -> EmptyState(stringResource(R.string.common_loading))
                state.moved -> {
                    EmptyState(stringResource(R.string.move_done))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextButton(onClick = onMoved) {
                            Text(
                                text = stringResource(R.string.common_done),
                                style = OpenCodeTypography.label,
                                color = colors.accentText,
                            )
                        }
                    }
                }
                state.failure != null -> EmptyState(state.failure.message(), isError = true)
                state.projects.isEmpty() -> EmptyState(stringResource(R.string.move_empty))
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(horizontal = spacing.lg),
                    ) {
                        itemsIndexed(
                            state.projects,
                            key = { index, project -> "$index:${project.id}" },
                        ) { index, project ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            ProjectRow(
                                project = project,
                                isCurrent = project.id == state.currentProjectID,
                                selected = project.id == state.selectedProjectID,
                                enabled = !state.moving,
                                onSelect = { onSelect(project.id) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.lg, vertical = spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (state.moving) stringResource(R.string.move_moving) else "",
                            style = OpenCodeTypography.bodySmall,
                            color = colors.onSurfaceMuted,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onMove,
                            enabled = state.selectedProjectID != null && !state.moving,
                        ) {
                            Text(
                                text = stringResource(R.string.move_confirm),
                                style = OpenCodeTypography.label,
                                color = if (state.selectedProjectID != null && !state.moving) {
                                    colors.accent
                                } else {
                                    colors.onSurfaceFaint
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One destination project: radio + name + path, or the current-project mark. */
@Composable
private fun ProjectRow(
    project: ProjectInfo,
    isCurrent: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(
                if (!isCurrent && enabled) {
                    Modifier.clickable(role = Role.RadioButton, onClick = onSelect)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = !isCurrent,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.accent,
                unselectedColor = colors.onSurfaceMuted,
            ),
        )
        Column(
            modifier = Modifier.padding(start = spacing.md),
        ) {
            Text(
                text = project.displayName(),
                style = OpenCodeTypography.body,
                color = if (isCurrent) colors.onSurfaceFaint else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isCurrent) {
                    stringResource(R.string.move_current)
                } else {
                    project.canonical
                },
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
