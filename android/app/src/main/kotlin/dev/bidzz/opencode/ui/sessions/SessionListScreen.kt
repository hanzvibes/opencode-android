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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.TextAction
import dev.bidzz.opencode.ui.message

/**
 * Conversation history.
 *
 * The rows sit on one card, like the file listing, so the list reads as one group.
 * Each row has the title the engine generated, and a quiet meta line with how long
 * ago it ran, its directory (only when the list is not already scoped to one project),
 * and its cost when non-zero.
 */
@Composable
fun SessionListScreen(
    state: SessionListUiState,
    onBack: () -> Unit,
    onOpen: (SessionRow) -> Unit,
    onToggleScope: () -> Unit,
    onNewSession: () -> Unit,
) {
    val spacing = OpenCodeTheme.tokens.spacing

    ScreenScaffold(
        title = stringResource(R.string.history_title),
        onBack = onBack,
        actions = { TextAction(stringResource(R.string.history_new), onNewSession) },
    ) {
        // Scope toggle: the two useful views are "this project" and "everything".
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            ScopeChip(
                label = stringResource(R.string.history_this_project),
                selected = state.filterDirectory != null,
                onClick = onToggleScope,
            )
            ScopeChip(
                label = stringResource(R.string.history_all_projects),
                selected = state.filterDirectory == null,
                onClick = onToggleScope,
            )
        }

        when {
            state.loading -> CenteredMessage(stringResource(R.string.history_loading))
            state.failure != null -> CenteredMessage(state.failure.message(), isError = true)
            state.failureKey != null -> CenteredMessage(stringResource(state.failureKey), isError = true)
            state.rows.isEmpty() -> CenteredMessage(stringResource(R.string.history_empty))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = spacing.lg,
                    end = spacing.lg,
                    top = spacing.xs,
                    bottom = spacing.xxl,
                ),
            ) {
                itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                    SessionRowItem(
                        row = row,
                        showDirectory = state.filterDirectory == null,
                        isFirst = index == 0,
                        isLast = index == state.rows.lastIndex,
                        onClick = { onOpen(row) },
                    )
                }
            }
        }
    }
}

/** "5m ago" / "5 mnt lalu", worded in the app's current language. */
@Composable
fun SessionAge.label(): String = when (unit) {
    AgeUnit.NOW -> stringResource(R.string.age_now)
    AgeUnit.MINUTES -> stringResource(R.string.age_minutes, value)
    AgeUnit.HOURS -> stringResource(R.string.age_hours, value)
    AgeUnit.DAYS -> stringResource(R.string.age_days, value)
    AgeUnit.MONTHS -> stringResource(R.string.age_months, value)
}

/** One session. First and last round the card's corners; the rest are square. */
@Composable
private fun SessionRowItem(
    row: SessionRow,
    showDirectory: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val r = OpenCodeTheme.tokens.radius.lg
    val shape = RoundedCornerShape(
        topStart = if (isFirst) r else 0.dp,
        topEnd = if (isFirst) r else 0.dp,
        bottomStart = if (isLast) r else 0.dp,
        bottomEnd = if (isLast) r else 0.dp,
    )

    val age = if (row.age != null) row.age.label() else null
    val directory = row.directory
        ?.takeIf { showDirectory }
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotEmpty() }
    val meta = listOfNotNull(age, directory, row.cost).joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface),
    ) {
        if (!isFirst) RowDivider(inset = spacing.md)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title.ifBlank { stringResource(R.string.drawer_untitled) },
                    style = OpenCodeTypography.body,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = OpenCodeTypography.label,
                        color = colors.onSurfaceFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }
            GlyphIcon(
                Glyph.ChevronRight,
                tint = colors.onSurfaceFaint,
                size = 18.dp,
                modifier = Modifier.padding(start = spacing.sm),
            )
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
            .background(if (selected) colors.accent.copy(alpha = 0.16f) else colors.surfaceSunken)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.xs + spacing.xxs),
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
