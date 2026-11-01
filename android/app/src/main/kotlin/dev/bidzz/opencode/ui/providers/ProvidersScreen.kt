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
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    var keyFor by remember { mutableStateOf<IntegrationInfo?>(null) }

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
            if (state.integrations.none { it.connected }) {
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

            state.integrations.forEach { integration ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(radius.md))
                        .background(colors.surfaceRaised)
                        .border(0.5.dp, colors.outline, RoundedCornerShape(radius.md))
                        .clickable(enabled = integration.acceptsKey) { keyFor = integration }
                        .padding(horizontal = spacing.md, vertical = spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(integration.name, style = OpenCodeTypography.body, color = colors.onSurface)
                        Text(
                            text = when {
                                integration.connected -> "connected"
                                integration.acceptsKey -> "tap to add an API key"
                                else -> "no API-key method"
                            },
                            style = OpenCodeTypography.bodySmall,
                            color = if (integration.connected) colors.success else colors.onSurfaceFaint,
                        )
                    }
                    if (integration.connected) {
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
