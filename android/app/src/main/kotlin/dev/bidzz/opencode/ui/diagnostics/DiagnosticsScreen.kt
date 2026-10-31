package dev.bidzz.opencode.ui.diagnostics

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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import java.io.File

/**
 * Runtime diagnostics.
 *
 * This is where the port, password and engine log live now. They used to be the
 * whole app; keeping them available matters because a failed engine start is
 * otherwise unexplainable, but they do not belong on the chat screen.
 *
 * Everything is selectable so a value can be copied into a bug report.
 */
@Composable
fun DiagnosticsScreen(
    app: OpenCodeApplication,
    onBack: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val status by app.engineStatus.collectAsState()

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
            Text("Diagnostics", style = OpenCodeTypography.title, color = colors.onSurface)
        }

        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.lg),
            ) {
                Line("state", status.state.name)
                Line("pid", status.pid?.toString() ?: "—")
                Line("port", status.port?.toString() ?: "—")
                Line("baseUrl", status.credentials?.baseUrl ?: "—")
                Line("password", status.credentials?.password ?: "—")
                Line("error", status.lastError ?: "—")
                Line("nativeLibraryDir", app.applicationInfo.nativeLibraryDir)
                Line("filesDir", app.filesDir.absolutePath)
                Line("workspace", app.workspaceStore.resolve().absolutePath)
                Line("all-files", app.workspaceStore.hasAllFilesAccess().toString())
                app.startupFailure?.let { Line("startupFailure", it) }

                Text(
                    text = "ENGINE OUTPUT",
                    style = OpenCodeTypography.label,
                    color = colors.onSurfaceFaint,
                    modifier = Modifier.padding(top = spacing.xl, bottom = spacing.sm),
                )
                if (status.recentOutput.isEmpty()) {
                    Text("(no output yet)", style = OpenCodeTypography.bodySmall, color = colors.onSurfaceMuted)
                } else {
                    status.recentOutput.takeLast(80).forEach { line ->
                        Text(
                            text = line,
                            style = OpenCodeTypography.code,
                            color = colors.onSurfaceMuted,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xxs)) {
        Text(
            text = label,
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurfaceFaint,
            modifier = Modifier.padding(end = spacing.md),
        )
        Text(
            text = value,
            style = OpenCodeTypography.code,
            color = colors.onSurface,
        )
    }
}
