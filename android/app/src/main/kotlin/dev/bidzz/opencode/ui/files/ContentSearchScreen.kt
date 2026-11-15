package dev.bidzz.opencode.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.SearchField
import dev.bidzz.opencode.ui.TextAction

/**
 * Content search mode for the explorer (audit-200 C16, M-238).
 *
 * Quick-open searches file NAMES; this searches file CONTENTS (see
 * [runContentSearch]). Results are grouped by file, each row showing the
 * line number and the matching line. Tapping a row opens the file with
 * the query pre-filled in its find bar, scrolled to that line.
 */
@Composable
fun ContentSearchContent(
    state: ExplorerUiState,
    onSearch: (String) -> Unit,
    onOpenResult: (path: String, line: Int, query: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    var query by remember(state.contentSearchQuery) { mutableStateOf(state.contentSearchQuery) }
    val groups = remember(state.contentSearchResults) {
        state.contentSearchResults.groupBy { it.path }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.files_search_hint),
                modifier = Modifier.weight(1f),
                imeAction = ImeAction.Search,
            )
            TextAction(
                label = stringResource(R.string.common_search),
                onClick = { if (query.isNotBlank()) onSearch(query) },
                enabled = query.isNotBlank(),
            )
            TextAction(
                label = stringResource(R.string.common_close),
                onClick = onClose,
            )
        }
        when {
            state.contentSearchLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = spacing.xl),
                        color = colors.accent,
                    )
                }
            }
            state.contentSearchFailed -> {
                EmptyState(
                    text = stringResource(R.string.files_search_failed),
                    isError = true,
                    onRetry = if (state.contentSearchQuery.isNotEmpty()) {
                        { onSearch(state.contentSearchQuery) }
                    } else {
                        null
                    },
                )
            }
            state.contentSearchQuery.isEmpty() -> {
                EmptyState(text = stringResource(R.string.files_search_empty))
            }
            state.contentSearchResults.isEmpty() -> {
                EmptyState(text = stringResource(R.string.files_search_none, state.contentSearchQuery))
            }
            else -> {
                if (state.contentSearchTruncated) {
                    Text(
                        text = stringResource(R.string.files_search_truncated, MaxContentMatches),
                        style = OpenCodeTypography.caption,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    groups.forEach { (path, matches) ->
                        item(key = "header:$path") {
                            Text(
                                text = stringResource(
                                    R.string.files_search_group,
                                    path,
                                    matches.size,
                                ),
                                style = OpenCodeTypography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = spacing.md,
                                        vertical = spacing.sm,
                                    ),
                            )
                        }
                        items(matches, key = { "${it.path}:${it.lineNumber}" }) { match ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onOpenResult(match.path, match.lineNumber, state.contentSearchQuery)
                                    }
                                    .padding(horizontal = spacing.md, vertical = spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = match.lineNumber.toString(),
                                    style = OpenCodeTypography.caption.copy(
                                        fontFamily = OpenCodeTypography.mono,
                                    ),
                                    color = colors.onSurfaceMuted,
                                    modifier = Modifier.width(spacing.xxl),
                                )
                                Text(
                                    text = match.lineText,
                                    style = OpenCodeTypography.bodySmall.copy(
                                        fontFamily = OpenCodeTypography.mono,
                                    ),
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
