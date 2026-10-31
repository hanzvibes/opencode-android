package dev.bidzz.opencode.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Conversation history.
 *
 * Each row shows what distinguishes it: the title the engine generated, how long
 * ago it ran, and its cost when non-zero. The directory is only shown when the
 * list is not already scoped to one project, otherwise every row repeats the same
 * path.
 */
@Composable
fun SessionListScreen(
    state: SessionListUiState,
    onBack: () -> Unit,
    onOpen: (SessionRow) -> Unit,
    onToggleScope: () -> Unit,
    onNewSession: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(end = spacing.lg, top = spacing.xs, bottom = spacing.xs),
            ) {
                Text("Back", style = OpenCodeTypography.label, color = colors.accent)
            }
            Text("History", style = OpenCodeTypography.title, color = colors.onSurface)
            Box(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.sm))
                    .clickable(onClick = onNewSession)
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
            ) {
                Text("New", style = OpenCodeTypography.label, color = colors.accent)
            }
        }

        // Scope toggle: the two useful views are "this project" and "everything".
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            ScopeChip(
                label = "This project",
                selected = state.filterDirectory != null,
                onClick = onToggleScope,
            )
            ScopeChip(
                label = "All projects",
                selected = state.filterDirectory == null,
                onClick = onToggleScope,
            )
        }

        when {
            state.loading -> CenteredMessage("Loading conversations…")
            state.error != null -> CenteredMessage(state.error, isError = true)
            state.rows.isEmpty() -> CenteredMessage(
                "No conversations yet.\nSend a message to start one.",
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                items(state.rows, key = { it.id }) { row ->
                    SessionRowItem(
                        row = row,
                        showDirectory = state.filterDirectory == null,
                        onClick = { onOpen(row) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRowItem(row: SessionRow, showDirectory: Boolean, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = spacing.md),
    ) {
        Text(
            text = row.title,
            style = OpenCodeTypography.body,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.padding(top = spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            if (row.age.isNotEmpty()) {
                Text(row.age, style = OpenCodeTypography.label, color = colors.onSurfaceFaint)
            }
            if (showDirectory && row.directory != null) {
                Text(
                    text = row.directory.substringAfterLast('/').ifEmpty { row.directory },
                    style = OpenCodeTypography.label,
                    color = colors.onSurfaceFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (row.cost != null) {
                Text(row.cost, style = OpenCodeTypography.label, color = colors.onSurfaceFaint)
            }
        }
    }
}

@Composable
private fun ScopeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(radius.full))
            .background(if (selected) colors.userBubble else colors.surfaceSunken)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.xs),
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.label,
            color = if (selected) colors.accent else colors.onSurfaceMuted,
        )
    }
}

@Composable
private fun CenteredMessage(text: String, isError: Boolean = false) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = OpenCodeTypography.body,
            color = if (isError) colors.danger else colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(spacing.xxl),
        )
    }
}
