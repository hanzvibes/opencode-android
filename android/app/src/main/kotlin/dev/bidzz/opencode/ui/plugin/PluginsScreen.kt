package dev.bidzz.opencode.ui.plugin

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.PluginInfo
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.StatusDot
import dev.bidzz.opencode.ui.TextAction
import dev.bidzz.opencode.ui.message

/**
 * Server plugins enabled on the engine, with their status.
 *
 * The list comes from `GET /api/plugin`. The header offers Check for
 * updates (`POST /api/plugin/check`, which answers the full inventory
 * with `outdated: true` stamped onto the package plugins that have one);
 * an outdated package plugin shows an Update button
 * (`POST /api/plugin/update`), and the list is checked again afterwards
 * so the flags refresh. Built-in, local and sdk plugins never carry
 * `outdated`, so they never get an Update button.
 *
 * A failed action never blanks the list: it arrives as
 * [PluginsUiState.actionFailure] and is shown once as a snackbar.
 */
@Composable
fun PluginsScreen(
    state: PluginsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCheckUpdates: () -> Unit,
    onUpdate: (String) -> Unit,
    onClearActionFailure: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val context = LocalContext.current

    LaunchedEffect(Unit) { onRefresh() }

    // Action failures arrive as values; show once, then clear.
    LaunchedEffect(state.actionFailure) {
        val failure = state.actionFailure ?: return@LaunchedEffect
        (context.applicationContext as OpenCodeApplication).showSnackbar(failure.message())
        onClearActionFailure()
    }

    ScreenScaffold(
        title = stringResource(R.string.plugin_title),
        onBack = onBack,
        contentMaxWidth = 720.dp,
        actions = {
            TextAction(
                label = stringResource(
                    if (state.checking) R.string.plugin_checking else R.string.plugin_check_updates,
                ),
                onClick = onCheckUpdates,
                enabled = !state.checking && !state.loading,
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
                text = stringResource(R.string.plugin_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(
                    horizontal = spacing.lg,
                    vertical = spacing.sm,
                ),
            )
            when {
                state.loading -> LoadingState(stringResource(R.string.common_loading), onRetry = onRefresh)
                state.failure != null -> EmptyState(state.failure.message(), isError = true)
                state.plugins.isEmpty() -> EmptyState(stringResource(R.string.plugin_empty))
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.lg),
                    ) {
                        itemsIndexed(
                            state.plugins,
                            // The display name is not a key: two built-in
                            // plugins can both read "Built-in" (no id, same
                            // source), and duplicate LazyColumn keys crash.
                            key = { index, plugin -> "$index:${plugin.displayName()}" },
                        ) { index, plugin ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            PluginRow(
                                plugin = plugin,
                                updating = plugin.packageTarget()?.let { it in state.updatingTargets } == true,
                                onUpdate = {
                                    plugin.packageTarget()?.let { onUpdate(it) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginRow(
    plugin: PluginInfo,
    updating: Boolean,
    onUpdate: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val kind = plugin.stateKind()
    val statusText = pluginStatusWord(kind)
    val dotColor = when (kind) {
        PluginStateKind.ACTIVE -> colors.success
        PluginStateKind.FAILED -> colors.danger
        PluginStateKind.UNKNOWN -> colors.onSurfaceMuted
    }
    // The id is the key the config uses; the source says where the plugin
    // came from, so it stays visible underneath.
    val title = plugin.displayName()
    val sourceLabel = pluginSourceLabel(plugin.source)
    val features = plugin.featureWords().joinToString(" · ")
    val error = plugin.state.error?.takeIf { it.isNotBlank() }
    // Only a package plugin with an update gets an Update button; the
    // target is what the update endpoint takes, and it is also the
    // busy key while the update runs.
    val outdated = plugin.isOutdated()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = spacing.xs),
            ) {
                Text(
                    text = title,
                    style = OpenCodeTypography.body,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (features.isEmpty()) sourceLabel else "$sourceLabel · $features",
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (outdated) {
                    Text(
                        text = stringResource(R.string.plugin_update_available),
                        style = OpenCodeTypography.bodySmall,
                        color = colors.warning,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
                if (error != null && kind == PluginStateKind.FAILED) {
                    Text(
                        text = error,
                        style = OpenCodeTypography.bodySmall,
                        color = colors.danger,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = spacing.xs),
            ) {
                StatusDot(color = dotColor, size = 7.dp, modifier = Modifier.padding(end = spacing.xs))
                Text(
                    text = statusText,
                    style = OpenCodeTypography.label,
                    color = dotColor,
                )
            }
        }
        if (outdated) {
            Row(
                horizontalArrangement = Arrangement.Start,
            ) {
                TextButton(
                    onClick = onUpdate,
                    enabled = !updating,
                ) {
                    Text(
                        text = stringResource(
                            if (updating) R.string.plugin_updating else R.string.plugin_update,
                        ),
                        color = colors.accentText,
                    )
                }
            }
        }
    }
}

@Composable
private fun pluginStatusWord(kind: PluginStateKind): String = stringResource(
    when (kind) {
        PluginStateKind.ACTIVE -> R.string.plugin_status_active
        PluginStateKind.FAILED -> R.string.plugin_status_failed
        PluginStateKind.UNKNOWN -> R.string.plugin_status_unknown
    },
)
