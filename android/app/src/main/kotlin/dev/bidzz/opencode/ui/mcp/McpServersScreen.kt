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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.LocalizedAlertDialog
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.SelectorChip
import dev.bidzz.opencode.ui.StatusDot
import dev.bidzz.opencode.ui.TextAction
import dev.bidzz.opencode.ui.message

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

    var showAddForm by remember { mutableStateOf(false) }
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
                        itemsIndexed(state.servers, key = { _, server -> server.name }) { index, server ->
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
                            items(
                                catalog.resources,
                                key = { resource -> "res:${resource.server}:${resource.uri}" },
                            ) { resource ->
                                McpResourceRow(
                                    name = resource.name,
                                    server = resource.server,
                                    uri = resource.uri,
                                    description = resource.description,
                                    isTemplate = false,
                                )
                            }
                            items(
                                catalog.templates,
                                key = { template -> "tpl:${template.server}:${template.uriTemplate}" },
                            ) { template ->
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
            onDismiss = { showAddForm = false },
            onAdd = { name, config ->
                showAddForm = false
                onAdd(name, config)
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
                Text(text = stringResource(R.string.mcp_remove_body))
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
 * working directory for local servers.
 *
 * Validation lives here, not in the ViewModel: the name must be one word
 * of letters, digits, `-` or `_` (the engine takes the name as a path
 * segment, and the client-MCP names use the same alphabet), and the
 * command / URL must be non-blank. The command is split on whitespace,
 * so quoted arguments are not supported — the hint says so. Environment
 * variables, headers, OAuth, timeouts and the protocol are not in this
 * form; the engine defaults apply, and the form's note says the server
 * is runtime-only.
 */
@Composable
private fun AddMcpServerDialog(
    adding: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, McpServerConfig) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    var name by remember { mutableStateOf("") }
    var isLocal by remember { mutableStateOf(true) }
    var command by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var cwd by remember { mutableStateOf("") }

    val nameValid = name.matches(Regex("^[A-Za-z0-9_-]+$"))
    val detailValid = if (isLocal) command.isNotBlank() else url.isNotBlank()
    val valid = nameValid && detailValid

    LocalizedAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val config = if (isLocal) {
                        McpServerConfig.local(
                            command = command.trim().split(Regex("\\s+")),
                            cwd = cwd.trim().takeIf { it.isNotEmpty() },
                        )
                    } else {
                        McpServerConfig.remote(url.trim())
                    }
                    onAdd(name.trim(), config)
                },
                enabled = valid && !adding,
            ) {
                Text(
                    text = stringResource(R.string.mcp_add_confirm),
                    color = if (valid && !adding) colors.accentText else colors.onSurfaceFaint,
                )
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
                } else {
                    FormField(
                        label = stringResource(R.string.mcp_add_url),
                        value = url,
                        onValueChange = { url = it },
                        placeholder = stringResource(R.string.mcp_add_url_hint),
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
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
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
