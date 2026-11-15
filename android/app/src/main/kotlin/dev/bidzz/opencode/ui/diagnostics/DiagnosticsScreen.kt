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
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
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
import dev.bidzz.opencode.CrashLog
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.core.engine.AntigravityPatchOutcome
import dev.bidzz.opencode.core.engine.EngineBinary
import dev.bidzz.opencode.core.engine.EngineCrashReport
import dev.bidzz.opencode.core.engine.EngineDiagnostics
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.ui.AppTag
import dev.bidzz.opencode.ui.CardGroup
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.SecureWindowEffect
import dev.bidzz.opencode.ui.browser.shareText
import dev.bidzz.opencode.ui.copyPlainText
import dev.bidzz.opencode.ui.IconBadge
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.StatusDot
import dev.bidzz.opencode.ui.TextAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * report exports at most 100 console lines + 200 disk lines (300), not
 * 200 — the old comment claimed 200 and the export silently proved it
 * wrong (audit-200 C9, M-136).
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
 * already used, widened with the failure wordings engines actually print
 * (audit-200 C9, M-133). The level is a guess from the text, never the
 * engine's own severity: the filter chips say so in the UI, and a line like
 * "no error" still classifies as ERROR, because no negation is detected.
 */
internal enum class LogLevel {
    ALL,
    INFO,
    WARNING,
    ERROR,
}

/** One retained log line: its 1-based position in the displayed list, text, and inferred level. */
private data class IndexedLogLine(val number: Int, val text: String, val level: LogLevel)

/**
 * Guesses a log line's severity from its text (audit-200 C9, M-133).
 *
 * The old set (error/fail/exception, warn) missed the wordings a failing
 * engine actually prints — "API key not valid", "Is port 41234 in use?",
 * "fatal", "panic", "timeout", "denied", "unauthorized" — so the Error
 * filter hid the very lines it exists to find. The widened set still
 * cannot prove a level: it only makes the guess less blind.
 */
internal fun classifyLogLevel(line: String): LogLevel {
    val lower = line.lowercase()
    return when {
        lower.contains("error") || lower.contains("fail") || lower.contains("exception") ||
            lower.contains("fatal") || lower.contains("panic") ||
            lower.contains("eaddrinuse") || lower.contains("timeout") || lower.contains("timed out") ||
            lower.contains("denied") || lower.contains("unauthorized") ||
            lower.contains("not valid") || lower.contains("refused") ||
            lower.contains("is port") || lower.contains("in use") ||
            lower.contains("cannot") || lower.contains("can't") || lower.contains("unable to") ||
            lower.contains("crash") || lower.contains("out of memory") -> LogLevel.ERROR
        lower.contains("warn") || lower.contains("deprecated") -> LogLevel.WARNING
        else -> LogLevel.INFO
    }
}

/**
 * Whether a restart has settled (audit-200 C9, M-135).
 *
 * A restart is settled once the supervisor cycled (a transitional state
 * was seen, or a new PID appeared) and the engine landed on a terminal
 * state. RUNNING and FAILED were already settled; DEGRADED and STOPPED
 * are terminal states too (EngineState: RUNNING -> DEGRADED,
 * STOPPING -> STOPPED), and the old predicate left the button stuck on
 * "Restarting…" for 30 seconds after a restart that had already finished
 * degraded or stopped.
 */
internal fun isRestartSettled(
    sawRestart: Boolean,
    state: EngineState,
    pidBefore: Int?,
    pid: Int?,
): Boolean {
    val terminal = state == EngineState.RUNNING || state == EngineState.FAILED ||
        state == EngineState.DEGRADED || state == EngineState.STOPPED
    val newPid = pidBefore != null && pid != null && pid != pidBefore
    return (sawRestart || newPid) && terminal
}

/** Most characters a logcat export may carry (audit-200 C15, M-222): the same 256 KB convention as the disk log. */
internal const val LOGCAT_EXPORT_MAX_CHARS = 256 * 1024

/**
 * The logcat command that reads only this app's own logs (audit-200 C15,
 * M-222): `logcat -d --pid=<own pid>` needs no READ_LOGS permission —
 * an app can always read its own lines. Pure, so the rule is
 * unit-tested here rather than through the export button.
 */
internal fun logcatCommand(pid: Int): List<String> = listOf("logcat", "-d", "--pid=$pid")

/** The tail of the logcat output, capped so a share/clipboard export never carries megabytes. */
internal fun trimLogcatTail(text: String, maxChars: Int = LOGCAT_EXPORT_MAX_CHARS): String =
    if (text.length <= maxChars) text else text.takeLast(maxChars)

/**
 * The text the logcat export shares: a header naming the app and pid,
 * then the capped log. Null when logcat produced nothing usable — the
 * caller reports the failure instead of sharing an empty file.
 */
internal fun logcatExportText(raw: String?, pid: Int): String? {
    val trimmed = raw?.trim().orEmpty().let { trimLogcatTail(it) }
    if (trimmed.isEmpty()) return null
    return "OpenCode logcat (pid $pid)\n\n$trimmed"
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
    val status by app.engineStatus.collectAsStateWithLifecycle()

    val diagnostics = remember {
        EngineDiagnostics(
            app.engineEnvironment,
            File(app.applicationInfo.nativeLibraryDir),
        )
    }

    var tools by remember { mutableStateOf(diagnostics.tools()) }
    var probes by remember { mutableStateOf<List<EngineDiagnostics.ProbeResult>>(emptyList()) }
    // Audit-200 C9 (M-136): the tail carries its own truncation fact —
    // a plain list of lines could not say older lines exist beyond it.
    var logTail by remember { mutableStateOf(EngineDiagnostics.LogTail(emptyList(), wasTruncated = false, fileBytes = 0)) }
    // Audit-200 C13 (M-179/M-180): the latest crash records, read on
    // refresh — null when the app / engine never crashed (or the file is
    // gone). Loaded with the log tail so the section never shows a stale
    // record next to a fresh log.
    var lastCrash by remember { mutableStateOf<CrashLog.LastCrash?>(null) }
    var lastEngineCrash by remember { mutableStateOf<EngineCrashReport?>(null) }
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
                // Audit-200 C13 (M-181): trim the disk log before reading
                // it — the engine never rotates it. (M-179/M-180): read
                // the crash records on the same pass.
                val crashReads = withContext(Dispatchers.IO) {
                    diagnostics.trimEngineLog()
                    CrashLog.readLastCrash(app.filesDir) to diagnostics.lastEngineCrash()
                }
                lastCrash = crashReads.first
                lastEngineCrash = crashReads.second
                // Audit-200 C13 (M-003): the disk log is redacted with the
                // engine password the supervisor captured, so a password
                // echoed without its prefix no longer reaches the viewer
                // or the clipboard verbatim.
                logTail = diagnostics.engineLogTail(
                    lines = MAX_LOG_LINES,
                    password = status.credentials?.password,
                )
                // Audit-200 C9 (M-137): probe every tool the Tools card
                // lists, not just git and rg — the card only proved the
                // symlink exists, and node is the riskiest binary the
                // agent actually runs. Concurrent, so one hung tool
                // cannot hold the refresh hostage for 8 seconds each;
                // awaitAll keeps the card's own order.
                probes = EngineBinary.TOOL_LIBRARIES.values.map { tool ->
                    async { diagnostics.probe(tool, listOf("--version")) }
                }.awaitAll()
                running = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // Audit-200 C13 (M-179/M-180): one-line summaries for the crash
    // section and the copied report — date plus the first trace line
    // (app) or the exit code and error (engine).
    val crashDateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    val lastCrashSummary = remember(lastCrash) {
        lastCrash?.let {
            "${crashDateFormat.format(Date(it.timestampMs))} · ${it.trace.lineSequence().firstOrNull().orEmpty()}"
        }
    }
    val lastEngineCrashSummary = remember(lastEngineCrash) {
        lastEngineCrash?.let {
            "${crashDateFormat.format(Date(it.timestampMs))} · exit ${it.exitCode ?: "?"}${it.error?.let { err -> " · $err" } ?: ""}"
        }
    }

    // The log viewer keeps only the newest slice in memory: the disk tail is
    // already capped at MAX_LOG_LINES and console output at 200 lines.
    // Audit-200 C9 (M-134): ALL used to put the live console tail BEFORE
    // the older disk tail, so startup → error → crash read backwards and
    // the same line had a different number on every tab. Disk first, then
    // console: the disk history is older, the console is the newest output,
    // so this order is approximately chronological. The two sources can
    // overlap and neither carries timestamps, so the merge cannot be exact
    // — and the numbers are positions in this displayed list, not line
    // numbers in any file.
    val activeLogs: List<String> = remember(status.recentOutput, logTail.lines, selectedLogTab) {
        when (selectedLogTab) {
            LogTab.ALL -> logTail.lines + status.recentOutput
            LogTab.DISK -> logTail.lines
            LogTab.CONSOLE -> status.recentOutput
        }.takeLast(MAX_LOG_LINES)
    }

    // Line numbers are positions in the displayed list, 1-based, captured
    // before filtering so a search never renumbers the lines it shows.
    // They are NOT line numbers in the log file: the ALL tab merges two
    // sources, and a refresh shifts every number as new lines arrive
    // (audit-200 C9, M-134).
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

    // Copy entire diagnostics report to clipboard.
    // Audit-200 D2 (m-940): the report body stays in English by design —
    // it is pasted into bug reports, where the log lines and headers are
    // English anyway. Only the on-screen Health rows are localised.
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
            // Audit-200 C13 (M-179/M-180): the crash records travel with
            // the report, so a pasted bug report carries them.
            appendLine("## Last Crashes")
            appendLine("- Last app crash: ${lastCrashSummary ?: "None"}")
            appendLine("- Last engine crash: ${lastEngineCrashSummary ?: "None"}")
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
            // Audit-200 C9 (M-136): the headers say how much was kept,
            // not as if the export were the whole log — the console
            // section is the last 100 lines, the disk section the last
            // 200 lines of a file that may be far larger.
            val consoleTaken = status.recentOutput.takeLast(100)
            appendLine("## Console Output (last ${consoleTaken.size} of ${status.recentOutput.size} lines)")
            consoleTaken.forEach { appendLine(it) }
            appendLine()
            val diskTaken = logTail.lines.takeLast(200)
            appendLine(
                "## Disk Log (last ${diskTaken.size} of ${logTail.lines.size} lines" +
                    if (logTail.wasTruncated) {
                        "; file is ${formatBytes(logTail.fileBytes)}, only the last 256 KB were read"
                    } else {
                        ""
                    } + ")",
            )
            diskTaken.forEach { appendLine(it) }
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

    // Exports this app's own logcat (audit-200 C15, M-222): `logcat -d
    // --pid=<own pid>` on IO, capped at 256 KB, then out through the
    // system share chooser. When the chooser cannot open, the clipboard
    // is the fallback — and when logcat itself fails, the snackbar says
    // so instead of sharing an empty file.
    val exportLogcat = {
        scope.launch(Dispatchers.IO) {
            val raw = runCatching {
                Runtime.getRuntime().exec(logcatCommand(Process.myPid()).toTypedArray())
                    .inputStream.bufferedReader().use { it.readText() }
            }.getOrNull()
            val text = logcatExportText(raw, Process.myPid())
            withContext(Dispatchers.Main) {
                when {
                    text == null -> {
                        (context.applicationContext as OpenCodeApplication)
                            .showSnackbar(context.getString(R.string.diag_logcat_failed))
                    }
                    shareText(context, text) -> Unit
                    copyPlainText(context, "OpenCode Logcat", text) -> {
                        (context.applicationContext as OpenCodeApplication)
                            .showSnackbar(context.getString(R.string.diag_copied_toast))
                    }
                    else -> {
                        (context.applicationContext as OpenCodeApplication)
                            .showSnackbar(context.getString(R.string.copy_failed))
                    }
                }
            }
        }
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
                label = stringResource(R.string.diag_export_logcat),
                onClick = { exportLogcat() },
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
                            // Audit-200 C8 (M-132): no explicit tint —
                            // IconBadge's default picks the foreground
                            // from the background's luminance (47-F5).
                            // White on this orange was only 2.20:1; the
                            // default near-black is 8.95:1.
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
                                        // and the engine lands on a terminal
                                        // state (RUNNING, FAILED, DEGRADED or
                                        // STOPPED — audit-200 C9, M-135), or
                                        // once a new PID is running. StateFlow
                                        // conflates rapid transitions, so the PID
                                        // change is a second signal for a restart
                                        // too fast to observe.
                                        val pidBefore = status.pid
                                        // Audit-200 C9 (M-135): restartEngine()
                                        // returns whether the restart was actually
                                        // sent — the old code ignored it, so a
                                        // restart that never left the phone waited
                                        // the full 30 seconds and then blamed the
                                        // engine with a timeout. Say so at once.
                                        if (!app.restartEngine()) {
                                            (context.applicationContext as OpenCodeApplication).showSnackbar(
                                                context.getString(R.string.diag_restart_not_sent),
                                                long = true,
                                            )
                                            isRestarting = false
                                        } else {
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
                                                        isRestartSettled(sawRestart, engine.state, pidBefore, engine.pid)
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
                                        }
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
                // Audit-200 C13 (M-179/M-180): the latest crash records —
                // the app's own uncaught exception, and the engine's last
                // unexpected exit. Both survive a restart; "none" when
                // there is no record yet.
                SectionTitle(stringResource(R.string.diag_section_crashes))
                CardGroup {
                    DiagnosticRow(
                        label = stringResource(R.string.diag_last_app_crash),
                        value = lastCrashSummary ?: stringResource(R.string.diag_none),
                        isCode = true,
                    )
                    RowDivider(inset = spacing.md)
                    DiagnosticRow(
                        label = stringResource(R.string.diag_last_engine_crash),
                        value = lastEngineCrashSummary ?: stringResource(R.string.diag_none),
                        isCode = true,
                    )
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
                        primaryText = stringResource(R.string.diag_cores, cpuCores),
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
                        primaryText = stringResource(R.string.diag_free, ramFreeStr),
                        secondaryText = stringResource(R.string.diag_total, ramTotalStr),
                        tagLabel = stringResource(if (memInfo.lowMemory) R.string.diag_health_low_mem else R.string.diag_health_good),
                        tagColor = if (memInfo.lowMemory) colors.warning else colors.success,
                    )
                    RowDivider(inset = spacing.md + 30.dp + spacing.md)

                    // Storage
                    // Audit-200 D1 (m-231): the storage row used to reuse the
                    // RAM tag ("Low Memory"). It has its own tag now. A
                    // failed StatFs reads as 0/0 — that is "unknown", not
                    // "0 B free" with a low tag.
                    val storageUnknown = storageTotal == 0L
                    val storageFreeStr = if (storageUnknown) stringResource(R.string.diag_storage_unknown) else formatBytes(storageFree)
                    val storageTotalStr = if (storageUnknown) stringResource(R.string.diag_storage_unknown) else formatBytes(storageTotal)
                    val isLowStorage = !storageUnknown && storageFree < 500L * 1024 * 1024
                    HealthRow(
                        glyph = Glyph.Folder,
                        badgeColor = Color(0xFF34C759),
                        title = stringResource(R.string.diag_storage),
                        primaryText = if (storageUnknown) storageFreeStr else stringResource(R.string.diag_free, storageFreeStr),
                        secondaryText = stringResource(R.string.diag_total, storageTotalStr),
                        tagLabel = stringResource(
                            if (storageUnknown) R.string.diag_storage_unknown
                            else if (isLowStorage) R.string.diag_health_low_storage
                            else R.string.diag_health_normal,
                        ),
                        tagColor = if (storageUnknown) colors.onSurfaceMuted
                        else if (isLowStorage) colors.warning else colors.success,
                    )
                    RowDivider(inset = spacing.md + 30.dp + spacing.md)

                    // Process PID
                    HealthRow(
                        glyph = Glyph.Sliders,
                        badgeColor = Color(0xFFFF9500),
                        title = stringResource(R.string.diag_process_pid),
                        primaryText = stringResource(R.string.diag_engine_label, status.pid?.toString() ?: "—"),
                        secondaryText = stringResource(R.string.diag_app_pid, Process.myPid()),
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
                                modifier = Modifier.weight(1f),
                            )
                            LogTabItem(
                                title = stringResource(R.string.diag_tab_engine_log),
                                selected = selectedLogTab == LogTab.DISK,
                                onClick = { selectedLogTab = LogTab.DISK },
                                modifier = Modifier.weight(1f),
                            )
                            LogTabItem(
                                title = stringResource(R.string.diag_tab_output),
                                selected = selectedLogTab == LogTab.CONSOLE,
                                onClick = { selectedLogTab = LogTab.CONSOLE },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(Modifier.width(spacing.sm))

                        // Quick Copy Logs Button (copies the filtered lines)
                        // Audit-200 D1 (m-234): disabled when nothing matches —
                        // the old button copied an empty string and still
                        // showed "Copied to clipboard".
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(radius.sm))
                                .background(colors.fill)
                                // A11y (#8b): the "Copy logs" text inside is the name.
                                .clickable(
                                    enabled = filteredLogs.isNotEmpty(),
                                    role = Role.Button,
                                    onClick = { copyLogs(filteredLogs.map { it.text }) },
                                )
                                .alpha(if (filteredLogs.isEmpty()) 0.4f else 1f)
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
                    // Audit-200 C9 (M-136): the note only applies to a
                    // disk tail — the console tab carries live output,
                    // not a cut file.
                    showTruncationNote = selectedLogTab != LogTab.CONSOLE && logTail.wasTruncated,
                )
            }

            if (filteredLogs.isEmpty()) {
                item(key = "log-empty-state", contentType = "empty_state") {
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
                items(filteredLogs, key = { it.number }, contentType = { _ -> "data_row" }) { entry ->
                    LogLineRow(
                        number = entry.number,
                        text = entry.text,
                        level = entry.level,
                        numberWidth = numberWidth,
                    )
                }
                item(key = "log-footer", contentType = "footer") {
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

            item(key = "log-bottom-spacer", contentType = "spacer") {
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
    // Audit-200 C9 (M-136): true when the disk log was cut at the 256 KB
    // window, so the viewer shows only its newest lines.
    showTruncationNote: Boolean = false,
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
            // Audit-200 D3 (m-911): the log search field was silent for
            // TalkBack when empty — label it with its hint. Resolved
            // here, not inside .semantics {}.
            val searchDescription = stringResource(R.string.diag_log_search_hint)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = OpenCodeTypography.bodySmall.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.sm)
                    .semantics { contentDescription = searchDescription },
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
                modifier = Modifier.weight(1f),
            )
            LogTabItem(
                title = stringResource(R.string.diag_log_level_info),
                selected = selectedLevel == LogLevel.INFO,
                onClick = { onLevelChange(LogLevel.INFO) },
                modifier = Modifier.weight(1f),
            )
            LogTabItem(
                title = stringResource(R.string.diag_log_level_warning),
                selected = selectedLevel == LogLevel.WARNING,
                onClick = { onLevelChange(LogLevel.WARNING) },
                modifier = Modifier.weight(1f),
            )
            LogTabItem(
                title = stringResource(R.string.diag_log_level_error),
                selected = selectedLevel == LogLevel.ERROR,
                onClick = { onLevelChange(LogLevel.ERROR) },
                modifier = Modifier.weight(1f),
            )
        }

        // Audit-200 C9 (M-133): the levels are guessed from the log text,
        // not reported by the engine — the filter must not pretend
        // otherwise. Audit-200 C9 (M-136): the disk log is read through a
        // 256 KB window, so when it is cut, say the viewer shows only the
        // newest lines instead of letting the tail pass for the whole log.
        Text(
            text = stringResource(R.string.diag_log_level_inferred),
            style = OpenCodeTypography.caption,
            color = colors.onSurfaceFaint,
            modifier = Modifier.padding(top = spacing.xs),
        )
        if (showTruncationNote) {
            Text(
                text = stringResource(R.string.diag_log_truncated_note),
                style = OpenCodeTypography.caption,
                color = colors.warning,
                modifier = Modifier.padding(top = spacing.xxs),
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
                            // Audit-200 D3 (m-339, neighbour): same 48dp
                            // floor as the Follow pill below — this pill
                            // was ~22dp too.
                            .defaultMinSize(minHeight = 48.dp)
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
                        // Audit-200 C8 (M-131): white text on `accent`
                        // was only 4.02:1 (light) / 3.65:1 (dark) — below
                        // AA. The pill now uses the button fill + its
                        // on-colour (5.98:1 / 5.57:1), the same pair
                        // PrimaryButton uses.
                        .background(if (follow) colors.accentFill else colors.fill)
                        // Audit-200 D3 (m-339): the pill was ~22dp tall —
                        // below even the 24dp WCAG floor. The colour pair
                        // above already passes AA (C8 M-131); only the
                        // height was still wrong.
                        .defaultMinSize(minHeight = 48.dp)
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
                            tint = if (follow) colors.onAccent else colors.onSurfaceMuted,
                            size = 14.dp,
                        )
                        Spacer(Modifier.width(spacing.xxs))
                        Text(
                            text = stringResource(R.string.diag_log_follow),
                            style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = if (follow) colors.onAccent else colors.onSurfaceMuted,
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
                // Audit-200 D4 (m-015): faint at 0.5 alpha is only 2.07:1 /
                // 2.56:1 on the sunken background — the full token reads
                // 5.48:1 / 6.89:1.
                color = colors.onSurfaceFaint,
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
            // Audit-200 C8 (M-132): no explicit tint — see the engine
            // control badge above. White on the cyan/green/orange fills
            // was 2.57:1 / 2.22:1 / 2.20:1; the luminance default gives
            // a near-black glyph (7.65:1 / 8.86:1 / 8.95:1).
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
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val radius = OpenCodeTheme.tokens.radius
    val spacing = OpenCodeTheme.tokens.spacing

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.full))
            .background(if (selected) colors.surfaceRaised else Color.Transparent)
            // 47-F2: a log tab announces as selected, not a plain button.
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = OpenCodeTypography.caption.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) colors.onSurface else colors.onSurfaceMuted,
            // Audit-200 C11 (M-167): on a ≤390dp phone the third tab
            // was measured with ~0dp left and clipped away entirely,
            // so the Console log could not be opened at all. One line
            // with an ellipsis keeps every tab tappable; the call sites
            // give each item an equal weight.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
