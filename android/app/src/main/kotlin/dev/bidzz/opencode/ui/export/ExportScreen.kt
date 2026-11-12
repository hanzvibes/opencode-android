package dev.bidzz.opencode.ui.export

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.message

/**
 * One session's export, as a summary plus a copy action.
 *
 * The transcript itself is not rendered — it can be thousands of
 * messages — and it is never re-encoded: the Copy button puts the
 * verbatim JSON the engine sent onto the clipboard, so what the user
 * shares or saves elsewhere is exactly the export. Import is not part
 * of this batch.
 */
@Composable
fun ExportScreen(
    sessionID: String,
    state: ExportUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(sessionID) {
        copied = false
        onRefresh()
    }

    ScreenScaffold(
        title = stringResource(R.string.export_title),
        onBack = onBack,
        contentMaxWidth = 720.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.export_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
            )
            when {
                state.loading -> LoadingState(stringResource(R.string.common_loading), onRetry = onRefresh)
                state.failure != null -> EmptyState(state.failure.message(), isError = true)
                state.export == null -> EmptyState(stringResource(R.string.export_empty))
                else -> {
                    val export = state.export
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.lg),
                    ) {
                        export.displayTitle()?.let { title ->
                            Text(
                                text = title,
                                style = OpenCodeTypography.body,
                                color = colors.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = stringResource(R.string.export_messages, export.messageCount()),
                            style = OpenCodeTypography.bodySmall,
                            color = colors.onSurfaceMuted,
                            modifier = Modifier.padding(top = spacing.xxs),
                        )
                        Text(
                            text = stringResource(
                                R.string.export_size,
                                export.rawJson.length,
                            ),
                            style = OpenCodeTypography.bodySmall,
                            color = colors.onSurfaceMuted,
                            modifier = Modifier.padding(top = spacing.xxs),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(export.rawJson))
                                copied = true
                            },
                        ) {
                            Text(
                                text = stringResource(
                                    if (copied) R.string.export_copied else R.string.export_copy,
                                ),
                                style = OpenCodeTypography.label,
                                color = colors.accentText,
                            )
                        }
                    }
                }
            }
        }
    }
}
