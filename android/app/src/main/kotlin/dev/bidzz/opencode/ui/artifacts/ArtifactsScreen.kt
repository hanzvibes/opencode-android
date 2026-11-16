package dev.bidzz.opencode.ui.artifacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.ArtifactInfo
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.HatchDeleteConfirmationDialog
import dev.bidzz.opencode.ui.appLocale
import dev.bidzz.opencode.ui.formatAppDateTime
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.message

/**
 * Published artifacts on the engine.
 *
 * The list comes from `GET /api/experimental/artifacts` (newest
 * first). Tapping a row opens its view URL in the in-app browser
 * (with the engine credentials — the view endpoint needs Basic auth
 * like every engine call). Delete removes the artifact and its stored
 * files after a confirm.
 *
 * When artifacts are disabled on the engine (the flag defaults off in
 * releases) the list answers 403: the screen explains how to enable
 * them instead of showing a bare error, because the app cannot flip
 * the flag itself.
 */
@Composable
fun ArtifactsScreen(
    state: ArtifactsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (ArtifactInfo) -> Unit,
    onDelete: (String) -> Unit,
    onClearActionFailure: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val context = LocalContext.current
    // Audit-200 D8 (m-425/m-946): the pending delete survives a process
    // death — only the id is saved; the message names no artifact, so no
    // lookup is needed to show the dialog again.
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { onRefresh() }

    // message() is @Composable, so resolve it here — not inside LaunchedEffect.
    val actionFailureMessage = state.actionFailure?.message()
    LaunchedEffect(state.actionFailure) {
        val message = actionFailureMessage ?: return@LaunchedEffect
        (context.applicationContext as OpenCodeApplication).showSnackbar(message)
        onClearActionFailure()
    }

    ScreenScaffold(
        title = stringResource(R.string.artifact_title),
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
                text = stringResource(R.string.artifact_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(
                    horizontal = spacing.lg,
                    vertical = spacing.sm,
                ),
            )
            if (state.providers.isNotEmpty()) {
                // stringResource is @Composable: resolve it here, not inside joinToString.
                val defaultLabel = stringResource(R.string.artifact_provider_default)
                Text(
                    text = stringResource(
                        R.string.artifact_providers,
                        state.providers.joinToString(", ") { provider ->
                            if (provider.default) "${provider.name} ($defaultLabel)" else provider.name
                        },
                    ),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(
                        horizontal = spacing.lg,
                        vertical = spacing.xxs,
                    ),
                )
            }
            when {
                state.loading -> LoadingState(stringResource(R.string.common_loading), onRetry = onRefresh)
                state.failure != null && state.failure.isArtifactsDisabled() ->
                    EmptyState(stringResource(R.string.artifact_disabled))
                // Audit-200 B1 (M-194-class): same as JobsScreen — the
                // failure state had no retry even though onRefresh is in
                // scope. (The 403-disabled branch above stays retry-less:
                // the app cannot enable the flag itself.)
                state.failure != null -> EmptyState(
                    state.failure.message(),
                    isError = true,
                    onRetry = onRefresh,
                )
                state.artifacts.isEmpty() -> EmptyState(stringResource(R.string.artifact_empty))
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.lg),
                    ) {
                        itemsIndexed(state.artifacts, key = { _, artifact -> artifact.id }, contentType = { _, _ -> "data_row" }) { index, artifact ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            ArtifactRow(
                                artifact = artifact,
                                deleting = artifact.id in state.deletingIds,
                                onOpen = { onOpen(artifact) },
                                onDelete = { pendingDeleteId = artifact.id },
                            )
                        }
                    }
                }
            }
        }
    }

    val toDeleteId = pendingDeleteId
    if (toDeleteId != null) {
        // Batch 1 (Aura full, Opsi B): the Hatch delete sheet instead of
        // the generic AlertDialog. Same strings and behaviour — dismiss
        // never deletes, confirm deletes this artifact's stored files.
        HatchDeleteConfirmationDialog(
            title = stringResource(R.string.artifact_remove_title),
            message = stringResource(R.string.artifact_remove_body),
            confirmLabel = stringResource(R.string.artifact_remove_confirm),
            onDismiss = { pendingDeleteId = null },
            onConfirm = {
                pendingDeleteId = null
                onDelete(toDeleteId)
            },
        )
    }
}

@Composable
private fun ArtifactRow(
    artifact: ArtifactInfo,
    deleting: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    // Audit-200 D3 (m-340/m-556/m-612): the row is tappable but TalkBack
    // announced only its text, with no role — and the Delete button read
    // just "Delete" with no artifact name. Resolved here, not inside
    // .semantics {}: the semantics lambda is not a composable scope.
    val openDescription = stringResource(R.string.artifact_open, artifact.title)
    val deleteDescription = stringResource(R.string.artifact_delete_item, artifact.title)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics {
                role = Role.Button
                contentDescription = openDescription
            }
            .clickable(onClick = onOpen)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = spacing.xs),
        ) {
            Text(
                text = artifact.title,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.artifact_row_meta,
                    artifact.provider,
                    formatAppDateTime(artifact.time.updated, appLocale()),
                ),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artifact.entry,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            modifier = Modifier.sizeIn(minHeight = 48.dp).semantics { contentDescription = deleteDescription },
            onClick = onDelete,
            enabled = !deleting,
        ) {
            Text(
                text = stringResource(R.string.artifact_delete),
                color = colors.danger,
            )
        }
    }
}
