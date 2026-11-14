package dev.bidzz.opencode.ui.mcp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.McpServer
import dev.bidzz.opencode.core.api.dto.McpServerConfig
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.HatchBannerKind
import dev.bidzz.opencode.ui.HatchInlineBanner
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.LocalizedAlertDialog
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.SelectorChip
import dev.bidzz.opencode.ui.StatusDot
import dev.bidzz.opencode.ui.TextAction
import dev.bidzz.opencode.ui.message

/**
 * Audit-200 Fase A1 (C-001, C-003): LazyColumn keys for the server
 * list and the resource/template catalog.
 *
 * The engine is allowed to return the same name twice — the same
 * server in the global and the project config, or one server exposing
 * the same resource URI twice — and a key of just the name/URI then
 * crashes the screen with a duplicate key. The index qualifies the
 * key, so every row stays visible (deduping would silently hide a
 * real server or resource). Stable for the common case: the same row
 * at the same position keeps the same key.
 */
fun mcpServerListKey(index: Int, name: String): String = "$index:$name"

/** Catalog resource key: index-qualified, `res:`-prefixed. */
fun mcpResourceListKey(index: Int, server: String, uri: String): String =
    "res:$index:$server:$uri"

/** Catalog template key: index-qualified, `tpl:`-prefixed. */
fun mcpTemplateListKey(index: Int, server: String, uriTemplate: String): String =
    "tpl:$index:$server:$uriTemplate"

/**
 * MCP servers configured on the engine, with their connection status.
 *
 * The list comes from `GET /api/mcp`, the resources from
 * `GET /api/mcp/resource`. Each row offers Connect / Disconnect and
 * Remove (behind a confirm — removing is runtime-only, the server
 * returns with the next engine restart); the header offers Add, a form
 * for a local (command) or remote (URL) server. An added server is
 * runtime-only too: it is gone after the next engine restart, and the
 * form says so.
 *
 * A failed action never blanks the list: it arrives as
 * [McpServersUiState.actionFailure] and is shown once as a snackbar.
 */
@Composable
fun McpServersScreen(
    state: McpServersUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String, McpServerConfig) -> Unit,
    onClearActionFailure: () -> Unit,
    /** True when the server was added at runtime and lives in no config file (DL-015). */
    isRuntimeOnly: (String) -> Boolean = { false },
    onClearActionSuccess: () -> Unit = {},
    onRestartEngine: () -> Unit = {},
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val context = LocalContext.current

    LaunchedEffect(Unit) { onRefresh() }

    // Action failures arrive as values; show once, then clear.
    // message() is @Composable, so resolve it here — not inside LaunchedEffect.
    val actionFailureMessage = state.actionFailure?.message()
    // Audit-200 B2 (M-293): same one-shot channel for successes — a
    // quiet reload often showed the same status, so the tap looked like
    // it did nothing.
    val actionSuccessMessage = state.actionSuccess?.let { success ->
        stringResource(
            when (success.action) {
                McpAction.ADD -> R.string.mcp_added
                McpAction.CONNECT -> R.string.mcp_connected
                McpAction.DISCONNECT -> R.string.mcp_disconnected
                McpAction.REMOVE -> R.string.mcp_removed
            },
            success.name,
        )
    }
    var showAddForm by remember { mutableStateOf(false) }
    var showRestartConfirm by remember { mutableStateOf(false) }
    // While the add form is open, the failure is shown inside it instead:
    // clearing it for the snackbar would erase the form's error before
    // the user reads it (audit-200 A9-1, DL-016). The snackbar fires when
    // the form closes instead — the effect keys on both values.
    LaunchedEffect(state.actionFailure, showAddForm) {
        if (showAddForm) return@LaunchedEffect
        val message = actionFailureMessage ?: return@LaunchedEffect
        (context.applicationContext as OpenCodeApplication).showSnackbar(message)
        onClearActionFailure()
    }
    LaunchedEffect(state.actionSuccess) {
        val message = actionSuccessMessage ?: return@LaunchedEffect
        (context.applicationContext as OpenCodeApplication).showSnackbar(message)
        onClearActionSuccess()
    }

    // An add is in flight until the view model says otherwise (DL-016):
    // the form stays open while adding and closes only on success, so a
    // failed add keeps its values instead of losing them with the dialog.
    var addInFlight by remember { mutableStateOf(false) }
    LaunchedEffect(state.adding) {
        if (addInFlight && !state.adding) {
            addInFlight = false
            if (state.actionFailure == null) showAddForm = false
        }
    }

    var removeTarget by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(
        title = stringResource(R.string.mcp_title),
        onBack = onBack,
        contentMaxWidth = 720.dp,
        actions = {
            TextAction(
                label = stringResource(R.string.mcp_add),
                onClick = { showAddForm = true },
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
                text = stringResource(R.string.mcp_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(
                    horizontal = spacing.lg,
                    vertical = spacing.sm,
                ),
            )
            // Audit-200 B2 (M-292): runtime changes used to sit silently
            // until a restart — from another screen, or a provider change
            // — undid them with no trace. The banner names the pending
            // restart and offers it; the confirm says what it discards.
            if (state.hasRuntimeChanges) {
                HatchInlineBanner(
                    message = stringResource(R.string.mcp_runtime_changes),
                    kind = HatchBannerKind.WARNING,
                    modifier = Modifier.padding(
                        horizontal = spacing.lg,
                        vertical = spacing.xxs,
                    ),
                    onRetry = { showRestartConfirm = true },
                    actionLabel = stringResource(R.string.mcp_restart),
                )
            }
            when {
                state.loading -> LoadingState(stringResource(R.string.common_loading), onRetry = onRefresh)
                state.failure != null -> EmptyState(state.failure.message(), isError = true, onRetry = onRefresh)
                state.servers.isEmpty() -> EmptyState(stringResource(R.string.mcp_empty))
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.lg),
                    ) {
                        itemsIndexed(
                            state.servers,
                            key = { index, server -> mcpServerListKey(index, server.name) },
                        ) { index, server ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            McpServerRow(
                                server = server,
                                busy = server.name in state.busyServers,
                                onConnect = { onConnect(server.name) },
                                onDisconnect = { onDisconnect(server.name) },
                                onRemove = { removeTarget = server.name },
                            )
                        }
                        // Resources from every connected server, after the
                        // server list: one scrollable surface, no nested
                        // scrolling. The section only appears when the
                        // catalog loaded and is non-empty.
                        val catalog = state.resources
                        if (catalog != null &&
                            (catalog.resources.isNotEmpty() || catalog.templates.isNotEmpty())
                        ) {
                            item {
                                Text(
                                    text = stringResource(R.string.mcp_resources_title),
                                    style = OpenCodeTypography.label,
                                    color = colors.onSurfaceMuted,
                                    modifier = Modifier.padding(
                                        top = spacing.lg,
                                        bottom = spacing.xs,
                                    ),
                                )
                            }
                            itemsIndexed(
                                catalog.resources,
                                key = { index, resource ->
                                    mcpResourceListKey(index, resource.server, resource.uri)
                                },
                            ) { _, resource ->
                                McpResourceRow(
                                    name = resource.name,
                                    server = resource.server,
                                    uri = resource.uri,
                                    description = resource.description,
                                    isTemplate = false,
                                )
                            }
                            itemsIndexed(
                                catalog.templates,
                                key = { index, template ->
                                    mcpTemplateListKey(index, template.server, template.uriTemplate)
                                },
                            ) { _, template ->
                                McpResourceRow(
                                    name = template.name,
                                    server = template.server,
                                    uri = template.uriTemplate,
                                    description = template.description,
                                    isTemplate = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddForm) {
        AddMcpServerDialog(
            adding = state.adding,
            error = actionFailureMessage,
            existingServer = { name -> mcpServerNamed(state.servers, name) },
            onDismiss = { showAddForm = false },
            onAdd = { name, config ->
                addInFlight = true
                onAdd(name, config)
            },
        )
    }

    if (showRestartConfirm) {
        LocalizedAlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestartConfirm = false
                        onRestartEngine()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.mcp_restart),
                        color = colors.danger,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        color = colors.onSurfaceMuted,
                    )
                }
            },
            title = {
                Text(text = stringResource(R.string.mcp_restart_title))
            },
            text = {
                Text(text = stringResource(R.string.mcp_restart_body))
            },
        )
    }

    removeTarget?.let { name ->
        LocalizedAlertDialog(
            onDismissRequest = { removeTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeTarget = null
                        onRemove(name)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.mcp_remove_confirm),
                        color = colors.danger,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        color = colors.onSurfaceMuted,
                    )
                }
            },
            title = {
                Text(text = stringResource(R.string.mcp_remove_title, name))
            },
            text = {
                // Audit-200 A9-1 (DL-015): the old body promised every
                // server "comes back from the config file" after a
                // restart — false for a server added at runtime, which
                // lives in no file and is gone for good once removed.
                Text(
                    text = stringResource(
                        if (isRuntimeOnly(name)) R.string.mcp_remove_body_runtime
                        else R.string.mcp_remove_body,
                    ),
                )
            },
        )
    }
}

@Composable
private fun McpServerRow(
    server: McpServer,
    busy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val kind = server.statusKind()
    val statusText = mcpStatusWord(kind)
    val dotColor = when (kind) {
        McpStatusKind.CONNECTED -> colors.success
        McpStatusKind.PENDING, McpStatusKind.NEEDS_AUTH -> colors.warning
        McpStatusKind.FAILED -> colors.danger
        McpStatusKind.DISABLED, McpStatusKind.UNKNOWN -> colors.onSurfaceMuted
    }
    // The title is the human name when the engine has one; the server name
    // is the key the config uses, so it stays visible underneath.
    val title = server.title?.takeIf { it.isNotBlank() } ?: server.name
    val error = server.status.error?.takeIf { it.isNotBlank() }

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
                if (title != server.name) {
                    Text(
                        text = server.name,
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (error != null && (kind == McpStatusKind.FAILED || kind == McpStatusKind.NEEDS_AUTH)) {
                    Text(
                        text = error,
                        style = OpenCodeTypography.bodySmall,
                        color = colors.danger,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
                // Audit-200 B2 (M-294): "Needs auth" was a dead end —
                // Connect only retries the same handshake, and nothing
                // said the authentication itself can't happen here.
                if (kind == McpStatusKind.NEEDS_AUTH) {
                    Text(
                        text = stringResource(R.string.mcp_needs_auth_note),
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurfaceMuted,
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
        // Connect / Disconnect / Remove: a connected server disconnects,
        // anything else connects (a failed or disabled server included —
        // the engine answers the error, and the snackbar shows it).
        // Disabled while this server has an action in flight.
        Row(
            horizontalArrangement = Arrangement.Start,
        ) {
            if (kind == McpStatusKind.CONNECTED) {
                TextButton(onClick = onDisconnect, enabled = !busy) {
                    Text(
                        text = stringResource(R.string.mcp_disconnect),
                        color = colors.accentText,
                    )
                }
            } else {
                TextButton(onClick = onConnect, enabled = !busy) {
                    Text(
                        text = stringResource(R.string.mcp_connect),
                        color = colors.accentText,
                    )
                }
            }
            TextButton(onClick = onRemove, enabled = !busy) {
                Text(
                    text = stringResource(R.string.mcp_remove),
                    color = colors.danger,
                )
            }
        }
    }
}

/**
 * One resource (or template) from the catalog: its name, then the server
 * and URI underneath. A template shows its URI template and is marked as
 * one — the URI is a pattern, not an address to open.
 */
@Composable
private fun McpResourceRow(
    name: String,
    server: String,
    uri: String,
    description: String?,
    isTemplate: Boolean,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
    ) {
        Text(
            text = name,
            style = OpenCodeTypography.body,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (isTemplate) {
                stringResource(R.string.mcp_resource_template_line, server, uri)
            } else {
                stringResource(R.string.mcp_resource_line, server, uri)
            },
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
    }
}

/** Lower-case status word for use inside a sentence ("Server is connected"). */
@Composable
private fun mcpStatusWord(kind: McpStatusKind): String = stringResource(
    when (kind) {
        McpStatusKind.CONNECTED -> R.string.mcp_status_connected
        McpStatusKind.DISABLED -> R.string.mcp_status_disabled
        McpStatusKind.FAILED -> R.string.mcp_status_failed
        McpStatusKind.NEEDS_AUTH -> R.string.mcp_status_needs_auth
        McpStatusKind.PENDING -> R.string.mcp_status_pending
        McpStatusKind.UNKNOWN -> R.string.mcp_status_unknown
    },
)

/**
 * The add-server form: name, local (command) or remote (URL), optional
 * working directory for local servers, environment variables / headers
 * as `KEY=value` lines, and a start-disabled toggle.
 *
 * Validation lives here, not in the ViewModel: the name must be one word
 * of letters, digits, `-` or `_` (the engine takes the name as a path
 * segment, and the client-MCP names use the same alphabet), and the
 * command / URL must be non-blank. The command is split on whitespace,
 * so quoted arguments are not supported — the hint says so. OAuth,
 * timeouts, the protocol, the pool and Code Mode are not in this form
 * (audit-200 B2, M-291: they need structured editors, not text lines);
 * the engine defaults apply, and the form's note says the server is
 * runtime-only.
 */
@Composable
private fun AddMcpServerDialog(
    adding: Boolean,
    /** The last add failure, shown inside the dialog (DL-016). */
    error: String?,
    /** The server with this name, when one already exists (DL-014). */
    existingServer: (String) -> McpServer?,
    onDismiss: () -> Unit,
    onAdd: (String, McpServerConfig) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    // Audit-200 A9-1 (DL-016): saved across rotation, and kept — the
    // dialog stays open on a failed add — so a failure never wipes
    // what the user typed.
    var name by rememberSaveable { mutableStateOf("") }
    var isLocal by rememberSaveable { mutableStateOf(true) }
    var command by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var cwd by rememberSaveable { mutableStateOf("") }
    // Audit-200 B2 (M-291): environment (local) / headers (remote) as
    // KEY=value lines, plus a start-disabled toggle — config fields the
    // form could never set before.
    var environment by rememberSaveable { mutableStateOf("") }
    var headers by rememberSaveable { mutableStateOf("") }
    var disabled by rememberSaveable { mutableStateOf(false) }

    val nameValid = name.matches(Regex("^[A-Za-z0-9_-]+$"))
    val detailValid = if (isLocal) command.isNotBlank() else url.isNotBlank()
    val valid = nameValid && detailValid
    val existing = if (nameValid) existingServer(name.trim()) else null

    LocalizedAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val config = if (isLocal) {
                        McpServerConfig.local(
                            command = command.trim().split(Regex("\\s+")),
                            cwd = cwd.trim().takeIf { it.isNotEmpty() },
                            environment = parseKeyValueLines(environment)
                                .takeIf { it.isNotEmpty() },
                            disabled = disabled.takeIf { it },
                        )
                    } else {
                        McpServerConfig.remote(
                            url.trim(),
                            headers = parseKeyValueLines(headers)
                                .takeIf { it.isNotEmpty() },
                            disabled = disabled.takeIf { it },
                        )
                    }
                    onAdd(name.trim(), config)
                },
                enabled = valid && !adding,
            ) {
                if (adding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.onSurfaceFaint,
                    )
                } else {
                    Text(
                        // DL-014: the button says Replace when the name
                        // is taken, so the overwrite is a deliberate tap.
                        text = stringResource(
                            if (existing != null) R.string.mcp_add_replace
                            else R.string.mcp_add_confirm,
                        ),
                        color = if (valid) colors.accentText else colors.onSurfaceFaint,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    color = colors.onSurfaceMuted,
                )
            }
        },
        title = {
            Text(text = stringResource(R.string.mcp_add_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    SelectorChip(
                        label = stringResource(R.string.mcp_add_local),
                        selected = isLocal,
                        onClick = { isLocal = true },
                    )
                    SelectorChip(
                        label = stringResource(R.string.mcp_add_remote),
                        selected = !isLocal,
                        onClick = { isLocal = false },
                    )
                }
                FormField(
                    label = stringResource(R.string.mcp_add_name),
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.mcp_add_name_hint),
                )
                if (name.isNotEmpty() && !nameValid) {
                    Text(
                        text = stringResource(R.string.mcp_add_name_invalid),
                        style = OpenCodeTypography.caption,
                        color = colors.danger,
                    )
                }
                if (isLocal) {
                    FormField(
                        label = stringResource(R.string.mcp_add_command),
                        value = command,
                        onValueChange = { command = it },
                        placeholder = stringResource(R.string.mcp_add_command_hint),
                    )
                    FormField(
                        label = stringResource(R.string.mcp_add_cwd),
                        value = cwd,
                        onValueChange = { cwd = it },
                        placeholder = stringResource(R.string.mcp_add_cwd_hint),
                    )
                    FormField(
                        label = stringResource(R.string.mcp_add_environment),
                        value = environment,
                        onValueChange = { environment = it },
                        placeholder = stringResource(R.string.mcp_add_environment_hint),
                        singleLine = false,
                    )
                } else {
                    FormField(
                        label = stringResource(R.string.mcp_add_url),
                        value = url,
                        onValueChange = { url = it },
                        placeholder = stringResource(R.string.mcp_add_url_hint),
                    )
                    FormField(
                        label = stringResource(R.string.mcp_add_headers),
                        value = headers,
                        onValueChange = { headers = it },
                        placeholder = stringResource(R.string.mcp_add_headers_hint),
                        singleLine = false,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    SelectorChip(
                        label = stringResource(R.string.mcp_add_disabled),
                        selected = disabled,
                        onClick = { disabled = !disabled },
                    )
                }
                // Audit-200 A9-1 (DL-014): adding with a taken name
                // replaces the whole server — its command, headers and
                // OAuth included — so the warning names what is at stake
                // before the Replace tap.
                existing?.let {
                    Text(
                        text = stringResource(
                            R.string.mcp_add_overwrite_warning,
                            it.name,
                            mcpStatusWord(it.statusKind()),
                        ),
                        style = OpenCodeTypography.caption,
                        color = colors.danger,
                    )
                }
                error?.let {
                    Text(
                        text = it,
                        style = OpenCodeTypography.caption,
                        color = colors.danger,
                    )
                }
                Text(
                    text = stringResource(R.string.mcp_add_runtime_note),
                    style = OpenCodeTypography.caption,
                    color = colors.onSurfaceMuted,
                )
            }
        },
    )
}

/**
 * One labelled text field, styled like [SearchField] (fill background,
 * hairline border, accent focus ring) without the search glyph.
 * The label sits above the field; the placeholder shows while empty.
 */
@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    // Audit-200 B2 (M-291): environment / headers are one KEY=value
    // line each, so those fields are multi-line.
    singleLine: Boolean = true,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.caption,
            color = colors.onSurfaceMuted,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = OpenCodeTypography.body.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.accent),
            singleLine = singleLine,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (singleLine) 48.dp else 96.dp)
                .clip(RoundedCornerShape(radius.md))
                .background(colors.fill)
                .border(
                    if (isFocused) 1.dp else 0.5.dp,
                    if (isFocused) colors.accent.copy(alpha = 0.70f) else colors.hairline,
                    RoundedCornerShape(radius.md),
                )
                .padding(horizontal = spacing.md, vertical = spacing.xs)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = OpenCodeTypography.body,
                            color = colors.onSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
    }
}
