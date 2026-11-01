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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.core.engine.EngineDiagnostics
import kotlinx.coroutines.launch
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

    val diagnostics = remember {
        EngineDiagnostics(
            app.engineEnvironment,
            File(app.applicationInfo.nativeLibraryDir),
        )
    }
    var tools by remember { mutableStateOf(diagnostics.tools()) }
    var probes by remember { mutableStateOf<List<EngineDiagnostics.ProbeResult>>(emptyList()) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val refresh: () -> Unit = {
        if (!running) {
            running = true
            tools = diagnostics.tools()
            scope.launch {
                logLines = diagnostics.engineLogTail()
                probes = listOf(
                    diagnostics.probe("git", listOf("--version")),
                    diagnostics.probe("rg", listOf("--version")),
                )
                running = false
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }

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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = refresh)
                    .padding(vertical = spacing.xs),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = if (running) "running…" else "Refresh",
                    style = OpenCodeTypography.label,
                    color = if (running) colors.onSurfaceFaint else colors.accent,
                )
            }
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

                Section("TOOLS")
                if (tools.isEmpty()) {
                    Body("(no tools declared)", colors.onSurfaceFaint)
                } else {
                    tools.forEach { tool ->
                        Line(
                            tool.name,
                            buildString {
                                append(if (tool.resolves) "linked" else "MISSING")
                                append(" -> ")
                                append(tool.target)
                                if (!tool.sourcePresent) append("  (no .so in APK)")
                            },
                            ok = tool.resolves,
                        )
                    }
                }
                if (probes.isEmpty() && !running) {
                    Body("tap Refresh to run git --version and rg --version", colors.onSurfaceFaint)
                }
                probes.forEach { probe ->
                    Line(
                        probe.command.substringAfterLast('/').substringBefore(' '),
                        when {
                            probe.timedOut -> "TIMEOUT after 8s"
                            probe.exitCode == 0 ->
                                // --version prints a block; the first line is the version, and
                                // the rest only matters when something is missing.
                                probe.output.trim().lineSequence().first().let { head ->
                                    val extra = probe.output.trim()
                                        .lines()
                                        .drop(1)
                                        .firstOrNull { it.isNotBlank() }
                                        ?.trim()
                                    if (extra == null) head else "$head  ·  $extra"
                                }
                            else -> "exit ${probe.exitCode}: " +
                                probe.output.trim().ifEmpty { "no output" }.replace('\n', ' ')
                        },
                        ok = probe.ok,
                    )
                }
                Body(
                    text = "A probe failing with exit 126 / Permission denied means the " +
                        "SELinux label on the linked binary rejects exec — the tool must be " +
                        "reached through the symlink in toolBin, never copied into filesDir.",
                    color = colors.onSurfaceFaint,
                )

                Section("PATHS")
                diagnostics.paths().forEach { (label, value) -> Line(label, value) }

                Section("ENGINE OUTPUT (this process)")
                if (status.recentOutput.isEmpty()) {
                    Body("(no output yet)", colors.onSurfaceMuted)
                } else {
                    status.recentOutput.takeLast(40).forEach { line -> Mono(line) }
                }

                Section("ENGINE LOG (on disk)")
                if (logLines.isEmpty()) {
                    Body("(nothing read — the file may not exist yet)", colors.onSurfaceMuted)
                } else {
                    Body("${logLines.size} lines, newest last", colors.onSurfaceFaint)
                    logLines.takeLast(120).forEach { line -> Mono(line) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = OpenCodeTypography.label,
        color = OpenCodeTheme.colors.onSurfaceFaint,
        modifier = Modifier.padding(top = OpenCodeTheme.tokens.spacing.xl, bottom = OpenCodeTheme.tokens.spacing.sm),
    )
}

@Composable
private fun Body(text: String, color: Color) {
    Text(text = text, style = OpenCodeTypography.bodySmall, color = color)
}

@Composable
private fun Mono(text: String) {
    val colors = OpenCodeTheme.colors
    Text(
        text = text,
        style = OpenCodeTypography.code,
        color = colors.onSurfaceMuted,
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    )
}

@Composable
private fun Line(label: String, value: String, ok: Boolean? = null) {
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
            color = when (ok) {
                true -> colors.success
                false -> colors.danger
                else -> colors.onSurface
            },
        )
    }
}
