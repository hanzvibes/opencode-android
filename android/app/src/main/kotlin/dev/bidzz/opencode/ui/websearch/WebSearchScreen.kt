package dev.bidzz.opencode.ui.websearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.WebSearchProvider
import dev.bidzz.opencode.core.api.dto.WebSearchResult
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.SearchField
import dev.bidzz.opencode.ui.TextAction
import dev.bidzz.opencode.ui.message
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip

/**
 * Audit-200 Fase A1 (C-002): LazyColumn key for the result list.
 *
 * Two results may share a URL (the engine can return the same link
 * from two sources), and a key of just the URL then crashes the
 * screen with a duplicate key. The index qualifies the key, so both
 * results stay visible. Stable for the common case: the same result
 * at the same position keeps the same key.
 */
fun webSearchResultKey(index: Int, url: String): String = "$index:$url"

/**
 * Direct web search (mockup group 5).
 *
 * One query at a time through the selected provider (or the engine's
 * configured default). A result opens in the in-app browser; its
 * content snippet is what the engine returned, not a fresh fetch.
 */
@Composable
fun WebSearchScreen(
    state: WebSearchUiState,
    onBack: () -> Unit,
    onRefreshProviders: () -> Unit,
    onQueryChange: (String) -> Unit,
    onProviderChange: (String?) -> Unit,
    onSearch: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onClearFailure: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val context = LocalContext.current

    LaunchedEffect(Unit) { onRefreshProviders() }

    // message() is @Composable, so resolve it here — not inside LaunchedEffect.
    val failureMessage = state.failure?.message()
    LaunchedEffect(state.failure) {
        val message = failureMessage ?: return@LaunchedEffect
        (context.applicationContext as OpenCodeApplication).showSnackbar(message)
        onClearFailure()
    }

    ScreenScaffold(
        title = stringResource(R.string.websearch_title),
        onBack = onBack,
        contentMaxWidth = 720.dp,
        actions = {
            TextAction(
                label = stringResource(R.string.websearch_action),
                onClick = onSearch,
                enabled = state.query.isNotBlank() && !state.searching,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.websearch_query_hint),
                modifier = Modifier.padding(
                    horizontal = spacing.lg,
                    vertical = spacing.sm,
                ),
            )
            if (state.providers.isNotEmpty()) {
                ProviderPicker(
                    providers = state.providers,
                    selectedProviderId = state.selectedProviderId,
                    onProviderChange = onProviderChange,
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
            }
            val response = state.response
            when {
                state.searching -> LoadingState(stringResource(R.string.websearch_searching))
                response == null -> EmptyState(stringResource(R.string.websearch_empty))
                response.results.isEmpty() -> EmptyState(stringResource(R.string.websearch_no_results))
                else -> {
                    Text(
                        text = stringResource(
                            R.string.websearch_provider_line,
                            response.providerID,
                        ),
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.padding(
                            horizontal = spacing.lg,
                            vertical = spacing.xxs,
                        ),
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.lg),
                    ) {
                        itemsIndexed(
                            response.results,
                            key = { index, result -> webSearchResultKey(index, result.url) },
                        ) { index, result ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            WebSearchRow(
                                result = result,
                                onOpen = { onOpenUrl(result.url) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderPicker(
    providers: List<WebSearchProvider>,
    selectedProviderId: String?,
    onProviderChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    var expanded by remember { mutableStateOf(false) }
    val selected = selectedProviderId?.let { id -> providers.firstOrNull { it.id == id } }
    val label = selected?.name ?: stringResource(R.string.websearch_provider_default)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(radius.md))
                .background(colors.fill)
                .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.md))
                .clickable { expanded = true }
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = label,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            GlyphIcon(
                glyph = Glyph.ChevronDown,
                tint = if (expanded) colors.accent else colors.onSurfaceMuted,
                size = 11.dp,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset.Zero,
            shape = RoundedCornerShape(radius.lg),
            containerColor = colors.surfaceRaised,
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.websearch_provider_default)) },
                onClick = {
                    expanded = false
                    onProviderChange(null)
                },
            )
            for (provider in providers) {
                DropdownMenuItem(
                    text = { Text(text = provider.name) },
                    onClick = {
                        expanded = false
                        onProviderChange(provider.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun WebSearchRow(
    result: WebSearchResult,
    onOpen: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onOpen)
            .padding(vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = result.title ?: result.url,
            style = OpenCodeTypography.body,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = result.url,
            style = OpenCodeTypography.bodySmall,
            color = colors.accentText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        result.content?.takeIf { it.isNotBlank() }?.let { content ->
            Text(
                text = content,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
