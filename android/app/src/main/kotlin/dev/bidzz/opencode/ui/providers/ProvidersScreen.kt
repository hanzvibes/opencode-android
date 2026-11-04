package dev.bidzz.opencode.ui.providers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.IntegrationInfo
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.LocalizedAlertDialog
import dev.bidzz.opencode.ui.ScreenScaffold

/**
 * Provider catalog and API-key entry.
 *
 * Without this the app can only use opencode's free tier, which rate-limits after a
 * single agent run — so this is the screen that makes sustained work possible.
 */
@Composable
fun ProvidersScreen(
    state: ProvidersUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSaveKey: (String, String) -> Unit,
    onSaveCustom: (String, String, String, String) -> Unit,
    onDeleteCredential: (String, String) -> Unit,
    onReplaceCredential: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    var keyFor by remember { mutableStateOf<IntegrationInfo?>(null) }
    var manageFor by remember { mutableStateOf<IntegrationInfo?>(null) }
    var replaceFor by remember { mutableStateOf<Pair<IntegrationInfo, CredentialRow>?>(null) }
    var confirmDelete by remember { mutableStateOf<Pair<IntegrationInfo, CredentialRow>?>(null) }
    var showCustomForm by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { onRefresh() }

    ScreenScaffold(title = stringResource(R.string.providers_title), onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            if (state.integrations.none { it.connected } && state.customProviders.isEmpty()) {
                Text(
                    text = stringResource(R.string.providers_none_connected),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
            }

            state.error?.let {
                Text(
                    text = it,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.danger,
                )
            }

            state.notice?.let {
                Text(
                    text = it,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
            }

            state.connected?.let {
                Text(
                    text = stringResource(R.string.providers_connected_to, it),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.success,
                )
            }

            Text(
                stringResource(R.string.providers_custom_section).uppercase(),
                style = OpenCodeTypography.label,
                color = colors.onSurfaceFaint,
            )
            state.customProviders.forEach { provider ->
                ProviderRow(
                    name = provider.name,
                    detail = provider.baseURL.ifEmpty { provider.id } +
                        if (provider.models.isEmpty()) "" else stringResource(R.string.providers_models_suffix, provider.models.size),
                    connected = true,
                    onClick = null,
                )
            }
            PrimaryButton(stringResource(R.string.providers_add_custom)) { showCustomForm = true }

            Text(
                stringResource(R.string.providers_catalog).uppercase(),
                style = OpenCodeTypography.label,
                color = colors.onSurfaceFaint,
            )
            if (state.integrations.isEmpty() && state.loading) {
                Text(
                    stringResource(R.string.providers_loading),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
            }
            // The catalog is a few hundred providers; without a filter the one you want is a long scroll away.
            if (state.integrations.isNotEmpty()) {
                SearchField(query = query, onChange = { query = it })
            }
            val shown = if (query.isBlank()) {
                state.integrations
            } else {
                state.integrations.filter { it.name.contains(query.trim(), ignoreCase = true) }
            }
            if (state.integrations.isNotEmpty() && shown.isEmpty()) {
                Text(
                    text = stringResource(R.string.providers_no_match, query.trim()),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
            }
            shown.forEach { integration ->
                val stored = rowsFor(state.credentials, integration.id)
                ProviderRow(
                    name = integration.name,
                    detail = when {
                        integration.connected && stored.isNotEmpty() ->
                            stringResource(
                                if (stored.size == 1) R.string.providers_keys_one else R.string.providers_keys_other,
                                stored.size,
                            )
                        integration.connected -> stringResource(R.string.providers_state_connected)
                        integration.acceptsKey -> stringResource(R.string.providers_state_add_key)
                        else -> stringResource(R.string.providers_state_no_method)
                    },
                    connected = integration.connected,
                    onClick = when {
                        // A provider with keys listed is managed, not re-keyed: adding a
                        // second key without showing the first hides which one the engine uses.
                        integration.connected && stored.isNotEmpty() -> ({ manageFor = integration })
                        integration.acceptsKey -> ({ keyFor = integration })
                        else -> null
                    },
                )
            }
        }
    }

    keyFor?.let { integration ->
        KeyPrompt(
            title = stringResource(R.string.providers_key_title, integration.name),
            body = stringResource(R.string.providers_key_body),
            submitLabel = stringResource(R.string.common_save),
            onDismiss = { keyFor = null },
            onSubmit = { key ->
                onSaveKey(integration.id, key)
                keyFor = null
            },
        )
    }

    manageFor?.let { integration ->
        ManageCredentialsDialog(
            integration = integration,
            rows = rowsFor(state.credentials, integration.id),
            onDismiss = { manageFor = null },
            onAddKey = {
                manageFor = null
                keyFor = integration
            },
            onReplace = { row -> replaceFor = integration to row },
            onDelete = { row -> confirmDelete = integration to row },
        )
    }

    replaceFor?.let { (integration, row) ->
        KeyPrompt(
            title = stringResource(R.string.providers_new_key_title, integration.name),
            body = stringResource(R.string.providers_new_key_body),
            submitLabel = stringResource(R.string.providers_replace),
            onDismiss = { replaceFor = null },
            onSubmit = { key ->
                onReplaceCredential(integration.id, row.id, key)
                replaceFor = null
                manageFor = null
            },
        )
    }

    confirmDelete?.let { (integration, row) ->
        val last = rowsFor(state.credentials, integration.id).size == 1
        LocalizedAlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = {
                Text(
                    stringResource(R.string.providers_delete_title),
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                )
            },
            text = {
                Text(
                    text = buildString {
                        append(stringResource(R.string.providers_delete_body, row.title, row.detail))
                        if (last) {
                            append(' ')
                            append(stringResource(R.string.providers_delete_last, integration.name))
                        }
                    },
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCredential(integration.id, row.id)
                        confirmDelete = null
                        manageFor = null
                    },
                ) { Text(stringResource(R.string.common_delete), color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.common_cancel), color = colors.onSurfaceMuted)
                }
            },
        )
    }

    if (showCustomForm) {
        CustomProviderDialog(
            onDismiss = { showCustomForm = false },
            onSubmit = { name, baseURL, key, models ->
                onSaveCustom(name, baseURL, key, models)
                showCustomForm = false
            },
        )
    }
}

/** Filters the catalog as you type. */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val shape = RoundedCornerShape(OpenCodeTheme.tokens.radius.lg)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(0.5.dp, colors.outline, shape)
            .padding(horizontal = spacing.md, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Search, tint = colors.onSurfaceMuted, size = 18.dp)
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = OpenCodeTypography.input.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.sm),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.providers_search),
                            style = OpenCodeTypography.input,
                            color = colors.onSurfaceFaint,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/** One provider row, shared by the custom and catalog sections. */
@Composable
private fun ProviderRow(
    name: String,
    detail: String,
    connected: Boolean,
    onClick: (() -> Unit)?,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.md))
            .background(colors.surfaceRaised)
            .border(0.5.dp, colors.outline, RoundedCornerShape(radius.md))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = spacing.md, vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = OpenCodeTypography.body, color = colors.onSurface)
            Text(
                text = detail,
                style = OpenCodeTypography.bodySmall,
                color = if (connected) colors.success else colors.onSurfaceFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (connected) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(radius.full))
                    .background(colors.surfaceSunken)
                    .padding(horizontal = spacing.sm, vertical = spacing.xxs),
            ) {
                Text(stringResource(R.string.providers_badge_on), style = OpenCodeTypography.label, color = colors.success)
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(radius.sm))
            .background(colors.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Text(text, style = OpenCodeTypography.label, color = colors.onAccent)
    }
}

/**
 * Single-field API key prompt, used both to add a key and to replace one.
 *
 * The submit button stays disabled while the field is blank: a credential with an
 * empty key would be stored and then fail every request with a provider-side error
 * that does not point back here.
 */
@Composable
private fun KeyPrompt(
    title: String,
    body: String,
    submitLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val radius = OpenCodeTheme.tokens.radius
    val spacing = OpenCodeTheme.tokens.spacing
    var value by remember { mutableStateOf("") }

    LocalizedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = OpenCodeTypography.title, color = colors.onSurface) },
        text = {
            Column {
                Text(
                    text = body,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = OpenCodeTypography.input.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.accent),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.md)
                        .clip(RoundedCornerShape(radius.sm))
                        .background(colors.surfaceSunken)
                        .padding(spacing.md),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(
                                stringResource(R.string.providers_paste_key),
                                style = OpenCodeTypography.input,
                                color = colors.onSurfaceFaint,
                            )
                        }
                        inner()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(value) }, enabled = value.isNotBlank()) {
                Text(submitLabel, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = colors.onSurfaceMuted) }
        },
    )
}

/**
 * The keys stored for one provider, with the actions that change them.
 *
 * The secret itself is not shown and cannot be: `Credential.Entry.value` is left off
 * the DTO on purpose, so the app never holds the key after storing it. Rows are
 * therefore labelled by their engine id — enough to tell two keys apart without
 * putting either on screen.
 */
@Composable
private fun ManageCredentialsDialog(
    integration: IntegrationInfo,
    rows: List<CredentialRow>,
    onDismiss: () -> Unit,
    onAddKey: () -> Unit,
    onReplace: (CredentialRow) -> Unit,
    onDelete: (CredentialRow) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val radius = OpenCodeTheme.tokens.radius
    val spacing = OpenCodeTheme.tokens.spacing

    LocalizedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.providers_keys_for, integration.name),
                style = OpenCodeTypography.title,
                color = colors.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.providers_manage_body),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
                rows.forEach { row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(radius.sm))
                            .background(colors.surfaceSunken)
                            .padding(spacing.sm),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = row.title,
                                style = OpenCodeTypography.body,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (row.inUse) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(radius.full))
                                        .background(colors.surfaceRaised)
                                        .padding(horizontal = spacing.sm, vertical = spacing.xxs),
                                ) {
                                    Text(
                                        stringResource(R.string.providers_in_use),
                                        style = OpenCodeTypography.label,
                                        color = colors.success,
                                    )
                                }
                            }
                        }
                        Text(row.detail, style = OpenCodeTypography.bodySmall, color = colors.onSurfaceFaint)
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                            Text(
                                text = stringResource(R.string.providers_replace),
                                style = OpenCodeTypography.label,
                                color = colors.accent,
                                modifier = Modifier
                                    .clickable { onReplace(row) }
                                    .padding(top = spacing.xs, bottom = spacing.xs),
                            )
                            Text(
                                text = stringResource(R.string.common_delete),
                                style = OpenCodeTypography.label,
                                color = colors.danger,
                                modifier = Modifier
                                    .clickable { onDelete(row) }
                                    .padding(top = spacing.xs, bottom = spacing.xs),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.providers_add_another),
                    style = OpenCodeTypography.label,
                    color = colors.accent,
                    modifier = Modifier
                        .clickable(onClick = onAddKey)
                        .padding(vertical = spacing.xs),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done), color = colors.accent) }
        },
    )
}

/**
 * Form for an OpenAI-compatible endpoint the engine's catalog does not know about.
 *
 * The engine resolves providers at startup, so the confirm button says what it
 * actually does — saving without restarting looks like a silent failure.
 */
@Composable
private fun CustomProviderDialog(
    onDismiss: () -> Unit,
    onSubmit: (name: String, baseURL: String, apiKey: String, models: String) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    var name by remember { mutableStateOf("") }
    var baseURL by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var models by remember { mutableStateOf("") }

    val ready = name.isNotBlank() && baseURL.isNotBlank() && apiKey.isNotBlank() && models.isNotBlank()

    LocalizedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.providers_custom_title),
                style = OpenCodeTypography.title,
                color = colors.onSurface,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    text = stringResource(R.string.providers_custom_body),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
                // The label names the field and the placeholder shows an example, so the two
                // never repeat each other.
                FormField(
                    label = stringResource(R.string.providers_field_name),
                    placeholder = stringResource(R.string.providers_field_name_hint),
                    value = name,
                    singleLine = true,
                    keyboard = KeyboardType.Text,
                ) { name = it }
                FormField(
                    label = stringResource(R.string.providers_field_url),
                    placeholder = "https://api.example.com/v1",
                    value = baseURL,
                    singleLine = true,
                    keyboard = KeyboardType.Uri,
                ) { baseURL = it }
                FormField(
                    label = stringResource(R.string.providers_field_key),
                    placeholder = stringResource(R.string.providers_field_key_hint),
                    value = apiKey,
                    singleLine = true,
                    secret = true,
                ) { apiKey = it }
                FormField(
                    label = stringResource(R.string.providers_field_models),
                    placeholder = stringResource(R.string.providers_field_models_hint),
                    value = models,
                    singleLine = false,
                    keyboard = KeyboardType.Text,
                ) { models = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(name, baseURL, apiKey, models) }, enabled = ready) {
                Text(stringResource(R.string.providers_save_restart), color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = colors.onSurfaceMuted) }
        },
    )
}

@Composable
private fun FormField(
    label: String,
    value: String,
    singleLine: Boolean,
    placeholder: String,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Column {
        Text(label, style = OpenCodeTypography.label, color = colors.onSurfaceFaint)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = OpenCodeTypography.input.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xxs)
                .clip(RoundedCornerShape(radius.sm))
                .background(colors.surfaceSunken)
                .padding(spacing.sm),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, style = OpenCodeTypography.input, color = colors.onSurfaceFaint)
                }
                inner()
            },
        )
    }
}
