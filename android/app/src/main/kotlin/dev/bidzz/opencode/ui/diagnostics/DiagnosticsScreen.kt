package dev.bidzz.opencode.ui.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.StatFs
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.core.engine.AntigravityPatchOutcome
import dev.bidzz.opencode.core.engine.EngineDiagnostics
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.ui.AppTag
import dev.bidzz.opencode.ui.CardGroup
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.SecureWindowEffect
import dev.bidzz.opencode.ui.copyPlainText
import dev.bidzz.opencode.ui.IconBadge
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.StatusDot
import dev.bidzz.opencode.ui.TextAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The probe's own timeout, so the message and the run cannot disagree about it. */
private const val PROBE_TIMEOUT_SECONDS = 8

/** Longest a restart may take before the button gives up waiting for the engine. */
private const val RESTART_TIMEOUT_MS = 30_000L

/** How long the engine password stays visible after the eye is tapped. */
private const val PASSWORD_VISIBLE_MS = 10_000L

/**
 * Most log lines retained in memory for the viewer. The disk log grows
 * without bound, so the viewer keeps only the newest slice; the copy-all
 * report still exports at most 200 lines.
 */
private const val MAX_LOG_LINES = 2000

/**
 * First log line's index in the screen LazyColumn: the four sections, the
 * log tab/copy header item, and the sticky log-controls header. Jump-to-
 * latest math depends on this — update it if an item is added above the
 * log lines.
 */
private const val LOG_FIRST_ITEM_INDEX = 6

private enum class LogTab {
    ALL,
    DISK,
    CONSOLE,
}

/**
 * Severity inferred from the log text. The log sources are plain strings —
 * [EngineDiagnostics.engineLogTail] and the engine's recent output carry no
 * structured level — so the filter reuses the same keywords the row colour
 * already used (error/fail/exception, warn, everything else is Info).
 */
private enum class LogLevel {
    ALL,
    INFO,
    WARNING,
    ERROR,
}

/** One retained log line: its real 1-based number, text, and inferred level. */
private data class IndexedLogLine(val number: Int, val text: String, val level: LogLevel)

private fun classifyLogLevel(line: String): LogLevel {
    val lower = line.lowercase()
    return when {
        lower.contains("error") || lower.contains("fail") || lower.contains("exception") -> LogLevel.ERROR
        lower.contains("warn") -> LogLevel.WARNING
        else -> LogLevel.INFO
    }
}

/**
 * Runtime diagnostics & system health monitor.
 *
 * Features:
 * - Engine supervisor control card with state badge and Restart Engine trigger.
 * - Apple-style System Health check cards (CPU, RAM, Storage, Process PID).
 * - Rich diagnostics log viewer with syntax coloring, line numbers, and Copy Logs.
 * - Tool binary & runtime probe diagnostics.
 * - "Copy All" full Markdown report export.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiagnosticsScreen(
    app: OpenCodeApplication,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
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
    var isRestarting by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var selectedLogTab by remember { mutableStateOf(LogTab.ALL) }
    var logQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf(LogLevel.ALL) }
    var followLogs by remember { mutableStateOf(true) }
    val logListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // System Health Metrics
    val cpuCores = remember { Runtime.getRuntime().availableProcessors() }
    val cpuAbi = remember { Build.SUPPORTED_ABIS.firstOrNull() ?: Build.CPU_ABI }

    val memInfo = remember(running, status) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        info
    }

    val (storageFree, storageTotal) = remember(running, status) {
        runCatching {
            val stat = StatFs(app.filesDir.absolutePath)
            stat.availableBytes to stat.totalBytes
        }.getOrDefault(0L to 0L)
    }

    val refresh: () -> Unit = {
        if (!running) {
            running = true
            tools = diagnostics.tools()
            scope.launch {
                logLines = diagnostics.engineLogTail(lines = MAX_LOG_LINES)
                probes = listOf(
                    diagnostics.probe("git", listOf("--version")),
                    diagnostics.probe("rg", listOf("--version")),
                )
                running = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // The log viewer keeps only the newest slice in memory: the disk tail is
    // already capped at MAX_LOG_LINES and console output at 200 lines.
    val activeLogs: List<String> = remember(status.recentOutput, logLines, selectedLogTab) {
        when (selectedLogTab) {
            LogTab.ALL -> status.recentOutput + logLines
            LogTab.DISK -> logLines
            LogTab.CONSOLE -> status.recentOutput
        }.takeLast(MAX_LOG_LINES)
    }

    // Line numbers are the index in the retained list, 1-based, captured
    // before filtering so a search never renumbers the lines it shows.
    val indexedLogs: List<IndexedLogLine> = remember(activeLogs) {
        activeLogs.mapIndexed { index, line ->
            IndexedLogLine(number = index + 1, text = line, level = classifyLogLevel(line))
        }
    }
    val trimmedQuery = logQuery.trim()
    val logFilterActive = trimmedQuery.isNotEmpty() || selectedLevel != LogLevel.ALL
    val filteredLogs: List<IndexedLogLine> = remember(indexedLogs, trimmedQuery, selectedLevel) {
        indexedLogs.filter { entry ->
            (selectedLevel == LogLevel.ALL || entry.level == selectedLevel) &&
                (trimmedQuery.isEmpty() || entry.text.contains(trimmedQuery, ignoreCase = true))
        }
    }

    // A revealed password hides itself again after 10s, so a glance does
    // not leave the credential on screen indefinitely.
    LaunchedEffect(showPassword) {
        if (showPassword) {
            delay(PASSWORD_VISIBLE_MS)
            showPassword = false
        }
    }

    // 53-F3: while the password is visible, block screenshots and the
    // Recents thumbnail. The flag clears with the reveal.
    if (showPassword) SecureWindowEffect()

    // Following pauses the moment the user scrolls up by hand: a manual
    // drag that settles away from the last line turns follow off, so new
    // lines never yank the view out from under the reader. Programmatic
    // jumps use scrollToItem, which does not trip isScrollInProgress.
    LaunchedEffect(logListState) {
        var wasScrolling = false
        snapshotFlow { logListState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling && wasScrolling && followLogs) {
                    val layout = logListState.layoutInfo
                    val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index
                    if (lastVisible != null && lastVisible < layout.totalItemsCount - 1) {
                        followLogs = false
                    }
                }
                wasScrolling = scrolling
            }
    }

    // While following, jump to the newest line whenever the (filtered) log
    // grows, the tab or level changes, or follow is switched back on.
    // No jump while a text search is active: the reader is looking at
    // matches, not the tail.
    LaunchedEffect(filteredLogs.size, followLogs, selectedLogTab, selectedLevel, trimmedQuery) {
        if (followLogs && trimmedQuery.isEmpty() && filteredLogs.isNotEmpty()) {
            logListState.scrollToItem(LOG_FIRST_ITEM_INDEX + filteredLogs.size - 1)
        }
    }

    // Copy entire diagnostics report to clipboard
    val copyAllDiagnostics = {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val report = buildString {
            appendLine("# OpenCode Diagnostics Report")
            appendLine("Generated: ${sdf.format(Date())}")
            appendLine()
            appendLine("## System Health")
            appendLine("- CPU: $cpuCores Cores ($cpuAbi)")
            appendLine("- RAM: ${formatBytes(memInfo.availMem)} free / ${formatBytes(memInfo.totalMem)} (lowMemory: ${memInfo.lowMemory})")
            appendLine("- Storage: ${formatBytes(storageFree)} free / ${formatBytes(storageTotal)}")
            appendLine("- App PID: ${Process.myPid()}")
            appendLine("- Engine PID: ${status.pid ?: "None"}")
            appendLine()
            appendLine("## Engine Status")
            appendLine("- State: ${status.state.name}")
            appendLine("- Port: ${status.port ?: "None"}")
            appendLine("- Base URL: ${status.credentials?.baseUrl ?: "None"}")
            appendLine("- Last Error: ${status.lastError ?: "None"}")
            status.antigravityPatch?.let { patch ->
                append("- Antigravity Patch: ${patch.outcome.name}")
                if (patch.patchedFiles.isNotEmpty()) append(" (${patch.patchedFiles.joinToString()})")
                if (patch.restartRequired) append(" — restart required")
                patch.detail?.let { append(" — $it") }
                appendLine()
            }
            if (app.startupFailure != null) {
                appendLine("- Startup Failure: ${app.startupFailure}")
            }
            appendLine()
            appendLine("## Tools")
            tools.forEach { t ->
                appendLine("- ${t.name}: ${if (t.resolves) "linked" else "MISSING"} -> ${t.target}")
            }
            appendLine()
            appendLine("## Probes")
            probes.forEach { p ->
                appendLine("- ${p.command}: exit ${p.exitCode ?: "unknown"}, timedOut: ${p.timedOut}")
                if (p.output.isNotBlank()) {
                    appendLine("  Output: ${p.output.trim().replace("\n", "\n  ")}")
                }
            }
            appendLine()
            appendLine("## Paths")
            diagnostics.paths().forEach { (k, v) ->
                appendLine("- $k: $v")
            }
            appendLine()
            appendLine("## Console Output (${status.recentOutput.size} lines)")
            status.recentOutput.takeLast(100).forEach { appendLine(it) }
            appendLine()
            appendLine("## Disk Log (${logLines.size} lines)")
            logLines.takeLast(200).forEach { appendLine(it) }
        }

        // 65-F4: the "copied" snackbar only fires on a real copy.
        val copiedMessage = if (copyPlainText(context, "OpenCode Diagnostics", report)) {
            context.getString(R.string.diag_copied_toast)
        } else {
            context.getString(R.string.copy_failed)
        }
        (context.applicationContext as OpenCodeApplication).showSnackbar(copiedMessage)
    }

    val copyLogs = { lines: List<String> ->
        val text = lines.joinToString("\n")
        val copiedMessage = if (copyPlainText(context, "OpenCode Logs", text)) {
            context.getString(R.string.diag_copied_toast)
        } else {
            context.getString(R.string.copy_failed)
        }
        (context.applicationContext as OpenCodeApplication).showSnackbar(copiedMessage)
    }

    val stateColor = when (status.state) {
        EngineState.RUNNING -> colors.success
        EngineState.STARTING, EngineState.INITIALIZING -> colors.warning
        EngineState.DEGRADED, EngineState.FAILED -> colors.danger
        EngineState.STOPPED, EngineState.STOPPING -> colors.onSurfaceMuted
    }

    ScreenScaffold(
        title = stringResource(R.string.settings_diag_title),
        onBack = onBack,
        actions = {
            TextAction(
                label = stringResource(R.string.diag_copy_all),
                onClick = { copyAllDiagnostics() },
            )
            TextAction(
                label = stringResource(if (running) R.string.diag_running else R.string.common_refresh),
                onClick = refresh,
                enabled = !running,
            )
        },
    ) {
        // One LazyColumn is the screen's only scrollable surface: the four
        // sections above are items, the log controls stick, and every log
        // line is its own lazy item. No nested scrolling anywhere.
        LazyColumn(
                state = logListState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
            ) {
                item {
                // Engine Supervisor & Control Card
                SectionTitle(stringResource(R.string.diag_section_engine_control))
                CardGroup {
                    // Header row: State & Badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.md, vertical = spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(
                            glyph = Glyph.Terminal,
                            tint = Color.White,
                            background = Color(0xFFFF9500),
                            shape = RoundedCornerShape(radius.sm),
                            size = 34.dp,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = spacing.md),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_section_engine),
                                style = OpenCodeTypography.bodyStrong,
                                color = colors.onSurface,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(color = stateColor, size = 6.dp)
                                Spacer(Modifier.width(spacing.xs))
                                Text(
                                    text = status.state.name,
                                    style = OpenCodeTypography.caption,
                                    color = stateColor,
                                )
                            }
                        }
                        AppTag(
                            label = status.state.name,
                            color = stateColor,
                        )
                    }

                    RowDivider(inset = spacing.md)

                    // Info Rows
                    SelectionContainer {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            DiagnosticRow(
                                label = stringResource(R.string.diag_engine_pid),
                                value = status.pid?.toString() ?: stringResource(R.string.diag_none),
                                isCode = true,
                            )
                            RowDivider(inset = spacing.md)
                            DiagnosticRow(
                                label = stringResource(R.string.settings_engine_port),
                                value = status.port?.toString() ?: stringResource(R.string.diag_none),
                                isCode = true,
                                valueColor = if (status.port != null) colors.neutral else colors.onSurfaceMuted,
                            )
                            RowDivider(inset = spacing.md)
                            DiagnosticRow(
                                label = "baseUrl",
                                value = status.credentials?.baseUrl ?: stringResource(R.string.diag_none),
                                isCode = true,
                            )
                            RowDivider(inset = spacing.md)
                            DiagnosticRow(
                                label = "password",
                                value = status.credentials?.password?.let {
                                    if (showPassword) it else stringResource(R.string.diag_password_hidden)
                                } ?: stringResource(R.string.diag_none),
                                isCode = true,
                                // A revealed password must not be selectable:
                                // the row sits inside a SelectionContainer, and a
                                // long-press would otherwise copy the credential.
                                selectable = false,
                                trailingAction = {
                                    val toggleDescription = stringResource(
                                        if (showPassword) R.string.diag_password_hide else R.string.diag_password_show,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .clickable(role = Role.Button) { showPassword = !showPassword }
                                            .semantics { contentDescription = toggleDescription },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        GlyphIcon(
                                            glyph = if (showPassword) Glyph.EyeOff else Glyph.Eye,
                                            tint = colors.accent,
                                            size = 18.dp,
                                        )
                                    }
                                },
                            )

                            // Antigravity plugin patch status (audit-100 31-F2): only
                            // shown once a patch run has happened, so a fresh engine
                            // that never needed the patch stays quiet.
                            status.antigravityPatch?.let { patch ->
                                RowDivider(inset = spacing.md)
                                DiagnosticRow(
                                    label = "antigravityPatch",
                                    value = buildString {
                                        append(patch.outcome.name)
                                        if (patch.patchedFiles.isNotEmpty()) {
                                            append(" (")
                                            append(patch.patchedFiles.joinToString())
                                            append(")")
                                        }
                                        if (patch.restartRequired) append(" — restart required")
                                        patch.detail?.let { append(" — $it") }
                                    },
                                    isCode = true,
                                    valueColor = when (patch.outcome) {
                                        AntigravityPatchOutcome.APPLIED,
                                        AntigravityPatchOutcome.ALREADY_PATCHED -> colors.onSurfaceMuted
                                        else -> colors.warning
                                    },
                                )
                            }

                            if (!status.lastError.isNullOrEmpty()) {
                                RowDivider(inset = spacing.md)
                                DiagnosticRow(
                                    label = "error",
                                    value = status.lastError ?: "",
                                    isCode = true,
                                    valueColor = colors.danger,
                                )
                            }

                            if (!app.startupFailure.isNullOrEmpty()) {
                                RowDivider(inset = spacing.md)
                                DiagnosticRow(
                                    label = "startupFailure",
                                    value = app.startupFailure ?: "",
                                    isCode = true,
                                    valueColor = colors.danger,
                                )
                            }
                        }
                    }

                    RowDivider(inset = 0.dp)

                    // Restart Engine Trigger Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(radius.md))
                                .background(colors.fill)
                                .clickable(
                                    role = Role.Button,
                                    enabled = !isRestarting,
                                    onClick = {
                                        isRestarting = true
                                        // The button stays in "Restarting…" until
                                        // the engine itself reports a settled
                                        // state — no fixed delay. A restart is
                                        // settled once the supervisor has cycled
                                        // (STARTING/INITIALIZING/STOPPING seen)
                                        // and the engine lands on RUNNING or
                                        // FAILED, or once a new PID is running.
                                        // StateFlow conflates rapid transitions,
                                        // so the PID change is a second signal
                                        // for a restart too fast to observe.
                                        val pidBefore = status.pid
                                        app.restartEngine()
                                        scope.launch {
                                            val settled = withTimeoutOrNull(RESTART_TIMEOUT_MS) {
                                                var sawRestart = false
                                                app.engineStatus.first { engine ->
                                                    if (
                                                        engine.state == EngineState.STARTING
                                                        || engine.state == EngineState.INITIALIZING
                                                        || engine.state == EngineState.STOPPING
                                                    ) {
                                                        sawRestart = true
                                                    }
                                                    val newPidRunning =
                                                        pidBefore != null && engine.pid != null
                                                            && engine.pid != pidBefore
                                                            && engine.state == EngineState.RUNNING
                                                    (sawRestart && (engine.state == EngineState.RUNNING || engine.state == EngineState.FAILED))
                                                        || newPidRunning
                                                }
                                            }
                                            if (settled == null) {
                                                (context.applicationContext as OpenCodeApplication).showSnackbar(
                                                    context.getString(R.string.diag_restart_timeout),
                                                    long = true,
                                                )
                                            }
                                            refresh()
                                            isRestarting = false
                                        }
                                        (context.applicationContext as OpenCodeApplication).showSnackbar(
                                            context.getString(R.string.diag_restarting),
                                        )
                                    },
                                )
                                .padding(horizontal = spacing.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                GlyphIcon(
                                    glyph = Glyph.Refresh,
                                    tint = colors.accent,
                                    size = 16.dp,
                                )
                                Spacer(Modifier.width(spacing.sm))
                                Text(
                                    text = stringResource(if (isRestarting) R.string.diag_restarting else R.string.diag_restart_engine),
                                    style = OpenCodeTypography.bodyStrong.copy(fontSize = 14.sp),
                                    color = colors.accentText,
                                )
                            }
                        }
                    }
                }

            }

            item {
                // System Health Check Section (CPU, RAM, Storage, Process PID)
                SectionTitle(stringResource(R.string.diag_section_health))
                CardGroup {
                    // CPU
                    HealthRow(
                        glyph = Glyph.Terminal,
                        badgeColor = Color(0xFF007AFF),
                        title = stringResource(R.string.diag_cpu),
                        primaryText = "$cpuCores Cores",
                        secondaryText = cpuAbi,
                        tagLabel = stringResource(R.string.diag_health_good),
                        tagColor = colors.success,
                    )
                    RowDivider(inset = spacing.md + 30.dp + spacing.md)

                    // RAM
                    val ramFreeStr = formatBytes(memInfo.availMem)
                    val ramTotalStr = formatBytes(memInfo.totalMem)
                    HealthRow(
                        glyph = Glyph.Code,
                        badgeColor = Color(0xFF30B0C7),
                        title = stringResource(R.string.diag_ram),
                        primaryText = "$ramFreeStr free",
                        secondaryText = "Total: $ramTotalStr",
                        tagLabel = stringResource(if (memInfo.lowMemory) R.string.diag_health_low_mem else R.string.diag_health_good),
                        tagColor = if (memInfo.lowMemory) colors.warning else colors.success,
                    )
                    RowDivider(inset = spacing.md + 30.dp + spacing.md)

                    // Storage
                    val storageFreeStr = formatBytes(storageFree)
                    val storageTotalStr = formatBytes(storageTotal)
                    val isLowStorage = storageFree < 500L * 1024 * 1024
                    HealthRow(
                        glyph = Glyph.Folder,
                        badgeColor = Color(0xFF34C759),
                        title = stringResource(R.string.diag_storage),
                        primaryText = "$storageFreeStr free",
                        secondaryText = "Total: $storageTotalStr",
                        tagLabel = stringResource(if (isLowStorage) R.string.diag_health_low_mem else R.string.diag_health_normal),
                        tagColor = if (isLowStorage) colors.warning else colors.success,
                    )
                    RowDivider(inset = spacing.md + 30.dp + spacing.md)

                    // Process PID
                    HealthRow(
                        glyph = Glyph.Sliders,
                        badgeColor = Color(0xFFFF9500),
                        title = stringResource(R.string.diag_process_pid),
                        primaryText = "Engine: ${status.pid ?: "—"}",
                        secondaryText = "App PID: ${Process.myPid()}",
                        tagLabel = stringResource(if (status.pid != null) R.string.diag_health_active else R.string.diag_health_inactive),
                        tagColor = if (status.pid != null) colors.success else colors.onSurfaceMuted,
                    )
                }

            }

            item {
                // Tools & Runtime Probes Section
                SectionTitle(stringResource(R.string.diag_section_tools))
                CardGroup {
                    if (tools.isEmpty()) {
                        Box(Modifier.padding(spacing.md)) {
                            Text(
                                text = stringResource(R.string.diag_no_tools),
                                style = OpenCodeTypography.bodySmall,
                                color = colors.onSurfaceFaint,
                            )
                        }
                    } else {
                        tools.forEachIndexed { index, tool ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tool.name, style = OpenCodeTypography.bodyStrong, color = colors.onSurface)
                                    Text(
                                        text = "${tool.linkPath} -> ${tool.target}",
                                        style = OpenCodeTypography.code.copy(fontSize = 11.sp),
                                        color = colors.onSurfaceMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                AppTag(
                                    label = stringResource(if (tool.resolves) R.string.diag_linked else R.string.diag_missing),
                                    color = if (tool.resolves) colors.success else colors.danger,
                                )
                            }
                        }
                    }

                    if (probes.isNotEmpty()) {
                        RowDivider(inset = 0.dp)
                        probes.forEachIndexed { index, probe ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = probe.command.substringAfterLast('/').substringBefore(' '),
                                        style = OpenCodeTypography.bodyStrong,
                                        color = colors.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    AppTag(
                                        label = when {
                                            probe.timedOut -> "TIMEOUT"
                                            probe.exitCode == 0 -> "OK"
                                            else -> "EXIT ${probe.exitCode}"
                                        },
                                        color = if (probe.ok) colors.success else colors.danger,
                                    )
                                }
                                Text(
                                    text = probe.output.trim().ifEmpty { stringResource(R.string.diag_no_output) },
                                    style = OpenCodeTypography.code.copy(fontSize = 11.sp),
                                    color = colors.onSurfaceMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = spacing.xxs),
                                )
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.diag_probe_note),
                    style = OpenCodeTypography.caption,
                    color = colors.onSurfaceFaint,
                    modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.xs),
                )

            }

            item {
                // Runtime Paths Section
                SectionTitle(stringResource(R.string.diag_section_paths))
                CardGroup {
                    SelectionContainer {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            diagnostics.paths().forEachIndexed { index, (label, value) ->
                                if (index > 0) RowDivider(inset = spacing.md)
                                val isMissing = label == "engineLog" && !diagnostics.engineLogFile.isFile
                                DiagnosticRow(
                                    label = label,
                                    value = value + if (isMissing) " ${stringResource(R.string.diag_missing_suffix)}" else "",
                                    isCode = true,
                                    valueColor = if (isMissing) colors.warning else colors.onSurface,
                                )
                            }
                        }
                    }
                }

            }

            // Log viewer — the log list itself is this screen's scrollable
            // surface: one LazyColumn, no nested scroll. The tab/copy header
            // is a plain item, the search/filter/follow controls stick, and
            // every line is its own lazy item with a real line number.
            item {
                SectionTitle(stringResource(R.string.diag_section_log))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = radius.lg, topEnd = radius.lg))
                        .background(colors.surfaceSunken),
                ) {
                    // Viewer Header: Tabs + Window dots + Copy button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Terminal macOS-style window dots
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF27C93F)))
                        }

                        Spacer(Modifier.width(spacing.md))

                        // Tab pills
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(radius.full))
                                .background(colors.fill)
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            LogTabItem(
                                title = stringResource(R.string.diag_tab_all),
                                selected = selectedLogTab == LogTab.ALL,
                                onClick = { selectedLogTab = LogTab.ALL },
                            )
                            LogTabItem(
                                title = stringResource(R.string.diag_tab_engine_log),
                                selected = selectedLogTab == LogTab.DISK,
                                onClick = { selectedLogTab = LogTab.DISK },
                            )
                            LogTabItem(
                                title = stringResource(R.string.diag_tab_output),
                                selected = selectedLogTab == LogTab.CONSOLE,
                                onClick = { selectedLogTab = LogTab.CONSOLE },
                            )
                        }

                        Spacer(Modifier.width(spacing.sm))

                        // Quick Copy Logs Button (copies the filtered lines)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(radius.sm))
                                .background(colors.fill)
                                // A11y (#8b): the "Copy logs" text inside is the name.
                                .clickable(role = Role.Button, onClick = { copyLogs(filteredLogs.map { it.text }) })
                                .padding(horizontal = spacing.sm, vertical = spacing.xs),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlyphIcon(Glyph.Copy, tint = colors.onSurfaceMuted, size = 14.dp)
                                Spacer(Modifier.width(spacing.xxs))
                                Text(
                                    text = stringResource(R.string.diag_copy_logs),
                                    style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onSurfaceMuted,
                                )
                            }
                        }
                    }
                }
            }

            stickyHeader {
                LogViewerControls(
                    query = logQuery,
                    onQueryChange = { logQuery = it },
                    selectedLevel = selectedLevel,
                    onLevelChange = { selectedLevel = it },
                    follow = followLogs,
                    onFollowChange = { followLogs = it },
                    matchCount = filteredLogs.size,
                    totalCount = indexedLogs.size,
                    filterActive = logFilterActive,
                    onJumpToLatest = {
                        followLogs = true
                        scope.launch {
                            logListState.scrollToItem(LOG_FIRST_ITEM_INDEX + filteredLogs.size - 1)
                        }
                    },
                )
            }

            if (filteredLogs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = radius.lg, bottomEnd = radius.lg))
                            .background(colors.surfaceSunken)
                            .padding(spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(
                                if (activeLogs.isEmpty()) R.string.diag_nothing_read
                                else R.string.diag_log_no_match,
                            ),
                            style = OpenCodeTypography.bodySmall,
                            color = colors.onSurfaceFaint,
                        )
                    }
                }
            } else {
                val numberWidth = indexedLogs.size.toString().length
                items(filteredLogs, key = { it.number }) { entry ->
                    LogLineRow(
                        number = entry.number,
                        text = entry.text,
                        level = entry.level,
                        numberWidth = numberWidth,
                    )
                }
                item {
                    // Rounded bottom cap for the log panel.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(bottomStart = radius.lg, bottomEnd = radius.lg))
                            .background(colors.surfaceSunken),
                    )
                }
            }

            item {
                Spacer(Modifier.height(spacing.xxl))
            }
        }
    }
}

/**
 * Sticky log controls: search field, level filter, follow toggle, and the
 * match count. Sticks to the top of the log while the lines scroll under it.
 */
@Composable
private fun LogViewerControls(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedLevel: LogLevel,
    onLevelChange: (LogLevel) -> Unit,
    follow: Boolean,
    onFollowChange: (Boolean) -> Unit,
    matchCount: Int,
    totalCount: Int,
    filterActive: Boolean,
    onJumpToLatest: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    // Hoisted out of the .semantics {} lambdas below: stringResource is
    // @Composable and cannot be called from a SemanticsPropertyReceiver.
    val clearSearchDescription = stringResource(R.string.diag_log_search_clear)
    val followDescription = stringResource(
        if (follow) R.string.diag_log_follow_off else R.string.diag_log_follow_on,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceSunken)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        // Search field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(radius.full))
                .background(colors.fill)
                .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.full))
                .padding(start = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.Search, tint = colors.onSurfaceMuted, size = 16.dp)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = OpenCodeTypography.bodySmall.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.sm),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.diag_log_search_hint),
                            style = OpenCodeTypography.bodySmall,
                            color = colors.onSurfaceFaint,
                        )
                    }
                    innerTextField()
                },
            )
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onQueryChange("") }
                        .semantics { contentDescription = clearSearchDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.Close, tint = colors.onSurfaceMuted, size = 16.dp)
                }
            }
        }

        // Level filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogTabItem(
                title = stringResource(R.string.diag_log_level_all),
                selected = selectedLevel == LogLevel.ALL,
                onClick = { onLevelChange(LogLevel.ALL) },
            )
            LogTabItem(
                title = stringResource(R.string.diag_log_level_info),
                selected = selectedLevel == LogLevel.INFO,
                onClick = { onLevelChange(LogLevel.INFO) },
            )
            LogTabItem(
                title = stringResource(R.string.diag_log_level_warning),
                selected = selectedLevel == LogLevel.WARNING,
                onClick = { onLevelChange(LogLevel.WARNING) },
            )
            LogTabItem(
                title = stringResource(R.string.diag_log_level_error),
                selected = selectedLevel == LogLevel.ERROR,
                onClick = { onLevelChange(LogLevel.ERROR) },
            )
        }

        // Match count + follow toggle / jump to latest
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (filterActive) {
                    stringResource(R.string.diag_log_match_count, matchCount, totalCount)
                } else {
                    stringResource(R.string.diag_lines_newest_last, totalCount)
                },
                style = OpenCodeTypography.caption,
                color = colors.onSurfaceFaint,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!follow) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(radius.full))
                            .background(colors.fill)
                            .clickable(role = Role.Button, onClick = onJumpToLatest)
                            .padding(horizontal = spacing.md, vertical = spacing.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphIcon(Glyph.ArrowDown, tint = colors.accent, size = 14.dp)
                            Spacer(Modifier.width(spacing.xxs))
                            Text(
                                text = stringResource(R.string.diag_log_jump_latest),
                                style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.accentText,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(radius.full))
                        .background(if (follow) colors.accent else colors.fill)
                        .clickable(role = Role.Button) { onFollowChange(!follow) }
                        .semantics {
                            contentDescription = followDescription
                        }
                        .padding(horizontal = spacing.md, vertical = spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(
                            Glyph.ArrowDown,
                            tint = if (follow) Color.White else colors.onSurfaceMuted,
                            size = 14.dp,
                        )
                        Spacer(Modifier.width(spacing.xxs))
                        Text(
                            text = stringResource(R.string.diag_log_follow),
                            style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = if (follow) Color.White else colors.onSurfaceMuted,
                        )
                    }
                }
            }
        }
    }
    RowDivider(inset = 0.dp)
}

/**
 * One log line: its real 1-based number from the retained list (captured
 * before filtering, so search/filter never renumbers), JetBrains Mono text,
 * and the severity colour for its inferred level.
 */
@Composable
private fun LogLineRow(
    number: Int,
    text: String,
    level: LogLevel,
    numberWidth: Int,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    val lower = text.trim().lowercase()
    val textColor = when (level) {
        LogLevel.ALL -> colors.onSurfaceMuted
        LogLevel.ERROR -> colors.danger
        LogLevel.WARNING -> colors.warning
        LogLevel.INFO -> if (lower.contains("info") || lower.contains("ready") || lower.contains("listening")) {
            colors.neutral
        } else {
            colors.onSurfaceMuted
        }
    }

    SelectionContainer {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceSunken)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = spacing.sm, vertical = 1.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = number.toString().padStart(numberWidth, ' '),
                style = OpenCodeTypography.code.copy(fontSize = 11.sp),
                color = colors.onSurfaceFaint.copy(alpha = 0.5f),
                modifier = Modifier.padding(end = spacing.sm),
            )
            Text(
                text = text,
                style = OpenCodeTypography.code.copy(fontSize = 11.sp),
                color = textColor,
            )
        }
    }
}

/** Formats byte count into human-readable B, KB, MB, GB string. */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}

@Composable
private fun SectionTitle(title: String) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Text(
        text = title.uppercase(),
        style = OpenCodeTypography.caption.copy(letterSpacing = 0.6.sp),
        color = colors.onSurfaceFaint,
        modifier = Modifier.padding(start = spacing.sm, top = spacing.xl, bottom = spacing.xs),
    )
}

/** One System Health check row with icon badge, title, metrics, and status tag. */
@Composable
private fun HealthRow(
    glyph: Glyph,
    badgeColor: Color,
    title: String,
    primaryText: String,
    secondaryText: String,
    tagLabel: String,
    tagColor: Color,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            glyph = glyph,
            tint = Color.White,
            background = badgeColor,
            shape = RoundedCornerShape(radius.sm),
            size = 30.dp,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.md, end = spacing.xs),
        ) {
            Text(
                text = title,
                style = OpenCodeTypography.bodyStrong,
                color = colors.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = primaryText,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurface,
                )
                Text(
                    text = "  ·  $secondaryText",
                    style = OpenCodeTypography.caption,
                    color = colors.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AppTag(label = tagLabel, color = tagColor)
    }
}

/** Diagnostic key-value row with optional action. */
@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    isCode: Boolean = false,
    valueColor: Color = OpenCodeTheme.colors.onSurface,
    trailingAction: (@Composable () -> Unit)? = null,
    /** False keeps the value out of text selection (e.g. a revealed password). */
    selectable: Boolean = true,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurfaceFaint,
            modifier = Modifier.width(110.dp),
        )
        val valueText = @Composable {
            Text(
                text = value,
                style = if (isCode) OpenCodeTypography.code else OpenCodeTypography.body,
                color = valueColor,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selectable) valueText() else DisableSelection { valueText() }
        if (trailingAction != null) {
            trailingAction()
        }
    }
}

/** Tab item inside the rich log viewer. */
@Composable
private fun LogTabItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val radius = OpenCodeTheme.tokens.radius
    val spacing = OpenCodeTheme.tokens.spacing

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(radius.full))
            .background(if (selected) colors.surfaceRaised else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = OpenCodeTypography.caption.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) colors.onSurface else colors.onSurfaceMuted,
        )
    }
}
