package dev.bidzz.opencode.ui.diagnostics

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.StatFs
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import dev.bidzz.opencode.core.engine.EngineDiagnostics
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.ui.AppTag
import dev.bidzz.opencode.ui.CardGroup
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.IconBadge
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.StatusDot
import dev.bidzz.opencode.ui.TextAction
import kotlinx.coroutines.delay
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

private enum class LogTab {
    ALL,
    DISK,
    CONSOLE,
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

    // A revealed password hides itself again after 10s, so a glance does
    // not leave the credential on screen indefinitely.
    LaunchedEffect(showPassword) {
        if (showPassword) {
            delay(PASSWORD_VISIBLE_MS)
            showPassword = false
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

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("OpenCode Diagnostics", report))
        Toast.makeText(context, context.getString(R.string.diag_copied_toast), Toast.LENGTH_SHORT).show()
    }

    val copyLogs = { lines: List<String> ->
        val text = lines.joinToString("\n")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("OpenCode Logs", text))
        Toast.makeText(context, context.getString(R.string.diag_copied_toast), Toast.LENGTH_SHORT).show()
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
                onClick = copyAllDiagnostics,
            )
            TextAction(
                label = stringResource(if (running) R.string.diag_running else R.string.common_refresh),
                onClick = refresh,
                enabled = !running,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        ) {
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
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.diag_restart_timeout),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                        refresh()
                                        isRestarting = false
                                    }
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.diag_restarting),
                                            Toast.LENGTH_SHORT,
                                        )
                                        .show()
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
                                color = colors.accent,
                            )
                        }
                    }
                }
            }

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

            // Rich Diagnostics Log Viewer
            SectionTitle(stringResource(R.string.diag_section_log))

            val activeLogs: List<String> = when (selectedLogTab) {
                LogTab.ALL -> status.recentOutput + logLines
                LogTab.DISK -> logLines
                LogTab.CONSOLE -> status.recentOutput
            }

            // Log Viewer Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radius.lg))
                    .background(colors.surfaceSunken)
                    .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.lg)),
            ) {
                // Viewer Header: Tabs + Window dots + Copy button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
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

                    // Quick Copy Logs Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(radius.sm))
                            .background(colors.fill)
                            .clickable(onClick = { copyLogs(activeLogs) })
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

                RowDivider(inset = 0.dp)

                // Log Content Area
                if (activeLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.diag_nothing_read),
                            style = OpenCodeTypography.bodySmall,
                            color = colors.onSurfaceFaint,
                        )
                    }
                } else {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(spacing.sm),
                        ) {
                            activeLogs.takeLast(200).forEachIndexed { idx, line ->
                                val lineTrimmed = line.trim()
                                val lineLower = lineTrimmed.lowercase()
                                val lineTextColor = when {
                                    lineLower.contains("error") || lineLower.contains("fail") || lineLower.contains("exception") -> colors.danger
                                    lineLower.contains("warn") -> colors.warning
                                    lineLower.contains("info") || lineLower.contains("ready") || lineLower.contains("listening") -> colors.neutral
                                    else -> colors.onSurfaceMuted
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(vertical = 1.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = (idx + 1).toString().padStart(3, ' '),
                                        style = OpenCodeTypography.code.copy(fontSize = 11.sp),
                                        color = colors.onSurfaceFaint.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(end = spacing.sm),
                                    )
                                    Text(
                                        text = line,
                                        style = OpenCodeTypography.code.copy(fontSize = 11.sp),
                                        color = lineTextColor,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(spacing.xxl))
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
