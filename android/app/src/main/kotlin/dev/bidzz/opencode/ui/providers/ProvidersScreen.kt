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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.api.dto.IntegrationInfo
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

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
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    var keyFor by remember { mutableStateOf<IntegrationInfo?>(null) }
    var showCustomForm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { onRefresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = spacing.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Back",
                style = OpenCodeTypography.label,
                color = colors.accent,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(end = spacing.lg, top = spacing.xs, bottom = spacing.xs),
            )
            Text("Providers", style = OpenCodeTypography.title, color = colors.onSurface)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            if (state.integrations.none { it.connected } && state.customProviders.isEmpty()) {
                Text(
                    text = "No provider connected. Until one is, the agent runs on opencode's " +
                        "free tier, which stops after a short while. Add an API key below to " +
                        "use your own account.",
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

            Text("CUSTOM ENDPOINT", style = OpenCodeTypography.label, color = colors.onSurfaceFaint)
            state.customProviders.forEach { provider ->
                ProviderRow(
                    name = provider.name,
                    detail = provider.baseURL.ifEmpty { provider.id } +
                        if (provider.models.isEmpty()) "" else " · ${provider.models.size} models",
                    connected = true,
                    onClick = null,
                )
            }
            PrimaryButton("Add a custom endpoint…") { showCustomForm = true }

            Text("CATALOG", style = OpenCodeTypography.label, color = colors.onSurfaceFaint)
            state.integrations.forEach { integration ->
                ProviderRow(
                    name = integration.name,
                    detail = when {
                        integration.connected -> "connected"
                        integration.acceptsKey -> "tap to add an API key"
                        else -> "no API-key method"
                    },
                    connected = integration.connected,
                    onClick = if (integration.acceptsKey) ({ keyFor = integration }) else null,
                )
            }
        }
    }

    keyFor?.let { integration ->
        KeyDialog(
            name = integration.name,
            onDismiss = { keyFor = null },
            onSubmit = { key ->
                onSaveKey(integration.id, key)
                keyFor = null
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
                Text("ON", style = OpenCodeTypography.label, color = colors.success)
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

@Composable
private fun KeyDialog(name: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val colors = OpenCodeTheme.colors
    val radius = OpenCodeTheme.tokens.radius
    val spacing = OpenCodeTheme.tokens.spacing
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API key for $name", style = OpenCodeTypography.title, color = colors.onSurface) },
        text = {
            Column {
                Text(
                    text = "Stored by the engine in its own database, inside the app's private " +
                        "storage. It is not written to any config file.",
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
                            Text("paste key", style = OpenCodeTypography.input, color = colors.onSurfaceFaint)
                        }
                        inner()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(value) }, enabled = value.isNotBlank()) {
                Text("Save", color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceMuted) }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom endpoint", style = OpenCodeTypography.title, color = colors.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    text = "For any OpenAI-compatible API. The engine reads this at startup, " +
                        "so it restarts when you save.",
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
                FormField("Display name", name, singleLine = true) { name = it }
                FormField("Base URL, e.g. https://api.example.com/v1", baseURL, singleLine = true) { baseURL = it }
                FormField("API key", apiKey, singleLine = true, secret = true) { apiKey = it }
                FormField("Model ids, comma or line separated", models, singleLine = false) { models = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(name, baseURL, apiKey, models) }, enabled = ready) {
                Text("Save & restart", color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceMuted) }
        },
    )
}

@Composable
private fun FormField(
    label: String,
    value: String,
    singleLine: Boolean,
    secret: Boolean = false,
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
            keyboardOptions = if (secret) {
                KeyboardOptions(keyboardType = KeyboardType.Password)
            } else {
                KeyboardOptions(keyboardType = KeyboardType.Uri)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xxs)
                .clip(RoundedCornerShape(radius.sm))
                .background(colors.surfaceSunken)
                .padding(spacing.sm),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(label, style = OpenCodeTypography.input, color = colors.onSurfaceFaint)
                }
                inner()
            },
        )
    }
}
