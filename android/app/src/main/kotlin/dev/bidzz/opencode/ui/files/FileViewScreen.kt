package dev.bidzz.opencode.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/** State of one open file. */
data class FileViewState(
    val loading: Boolean = true,
    val path: String = "",
    val content: String = "",
    val error: String? = null,
)

/**
 * Read-only file view.
 *
 * Read-only on purpose: editing needs a diff against the agent's changes and a
 * save path that cannot race the agent, which is a separate piece of work. Showing
 * content is still useful — it answers "what is in this file" while reviewing.
 */
@Composable
fun FileViewScreen(
    state: FileViewState,
    onBack: () -> Unit,
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
                    .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.sm))
                    .clickable(onClick = onBack)
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
            ) {
                Text("Back", style = OpenCodeTypography.label, color = colors.accent)
            }
            Column(Modifier.padding(start = spacing.md)) {
                Text(
                    text = state.path.substringAfterLast('/'),
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                )
                if (state.content.isNotEmpty()) {
                    Text(
                        text = "${state.content.lines().size} lines",
                        style = OpenCodeTypography.label,
                        color = colors.onSurfaceFaint,
                    )
                }
            }
        }

        when {
            state.loading -> Centered("Reading file…")
            state.error != null -> Centered(state.error)
            else -> SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(spacing.md),
                ) {
                    Text(
                        text = state.content,
                        style = OpenCodeTypography.code,
                        color = colors.onSurface,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun Centered(text: String) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = OpenCodeTypography.body,
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(spacing.xxl),
        )
    }
}
