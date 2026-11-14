package dev.bidzz.opencode.ui.generate

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.GenerateImageOutput
import dev.bidzz.opencode.core.api.dto.ModelInfo
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.SelectorChip
import dev.bidzz.opencode.ui.TextAction
import dev.bidzz.opencode.ui.message

private val ASPECTS = listOf("1:1", "16:9", "9:16")
private val RESOLUTIONS = listOf<String?>(null, "1K", "2K", "4K")

/**
 * One-shot generation (mockup group 5).
 *
 * Text is a stateless prompt to the default model (or a picked one);
 * image needs a picked image model and returns base64 pictures the
 * screen renders. Nothing is stored on the engine, and the result
 * carries its cost and token counts when the provider reports them.
 */
@Composable
fun GenerateScreen(
    state: GenerateUiState,
    onBack: () -> Unit,
    onRefreshModels: () -> Unit,
    onPromptChange: (String) -> Unit,
    onModeChange: (GenerateMode) -> Unit,
    onModelChange: (String?) -> Unit,
    onAspectChange: (String) -> Unit,
    onResolutionChange: (String?) -> Unit,
    onGenerate: () -> Unit,
    onClearFailure: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val context = LocalContext.current

    LaunchedEffect(Unit) { onRefreshModels() }

    // message() is @Composable, so resolve it here — not inside LaunchedEffect.
    val failureMessage = state.failure?.message()
    LaunchedEffect(state.failure) {
        val message = failureMessage ?: return@LaunchedEffect
        (context.applicationContext as OpenCodeApplication).showSnackbar(message)
        onClearFailure()
    }

    val canGenerate = state.prompt.isNotBlank() && !state.generating &&
        (state.mode == GenerateMode.TEXT || state.selectedModelId != null)
    val modelsForMode = when (state.mode) {
        GenerateMode.TEXT -> state.models
        GenerateMode.IMAGE -> state.models.imageModels()
    }

    ScreenScaffold(
        title = stringResource(R.string.generate_title),
        onBack = onBack,
        contentMaxWidth = 720.dp,
        actions = {
            TextAction(
                label = stringResource(R.string.generate_action),
                onClick = onGenerate,
                enabled = canGenerate,
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
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(R.string.generate_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                SelectorChip(
                    label = stringResource(R.string.generate_mode_text),
                    selected = state.mode == GenerateMode.TEXT,
                    onClick = { onModeChange(GenerateMode.TEXT) },
                )
                SelectorChip(
                    label = stringResource(R.string.generate_mode_image),
                    selected = state.mode == GenerateMode.IMAGE,
                    onClick = { onModeChange(GenerateMode.IMAGE) },
                )
            }
            when {
                state.modelsLoading -> LoadingState(stringResource(R.string.common_loading))
                state.modelsFailure != null -> EmptyState(state.modelsFailure.message(), isError = true)
                state.mode == GenerateMode.IMAGE && modelsForMode.isEmpty() ->
                    EmptyState(stringResource(R.string.generate_no_image_models))
                else -> ModelPicker(
                    models = modelsForMode,
                    selectedModelId = state.selectedModelId,
                    allowDefault = state.mode == GenerateMode.TEXT,
                    onModelChange = onModelChange,
                )
            }
            PromptField(
                value = state.prompt,
                onValueChange = onPromptChange,
                placeholder = stringResource(
                    if (state.mode == GenerateMode.TEXT) R.string.generate_prompt_hint_text
                    else R.string.generate_prompt_hint_image,
                ),
            )
            if (state.mode == GenerateMode.IMAGE) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    for (aspect in ASPECTS) {
                        SelectorChip(
                            label = aspect,
                            selected = state.aspect == aspect,
                            onClick = { onAspectChange(aspect) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    for (resolution in RESOLUTIONS) {
                        SelectorChip(
                            label = resolution ?: stringResource(R.string.generate_resolution_default),
                            selected = state.resolution == resolution,
                            onClick = { onResolutionChange(resolution) },
                        )
                    }
                }
            }
            if (state.generating) {
                LoadingState(stringResource(R.string.generate_generating))
            }
            // Audit-200 A9-3 (DL-011): the last generation died with the
            // process — the prompt and the previous result are back, but
            // nothing is running anymore.
            if (state.interrupted) {
                Text(
                    text = stringResource(R.string.generate_interrupted),
                    style = OpenCodeTypography.caption,
                    color = colors.danger,
                )
            }
            state.textResult?.let { text ->
                SelectionContainer {
                    Text(
                        text = text,
                        style = OpenCodeTypography.body,
                        color = colors.onSurface,
                    )
                }
            }
            for (image in state.imageResults) {
                GeneratedImage(image = image)
            }
            if (state.textResult != null || state.imageResults.isNotEmpty()) {
                val meta = buildList {
                    state.resultCost?.let { add(stringResource(R.string.generate_cost, it)) }
                    if (state.resultInputTokens != null || state.resultOutputTokens != null) {
                        add(
                            stringResource(
                                R.string.generate_tokens,
                                state.resultInputTokens ?: 0,
                                state.resultOutputTokens ?: 0,
                            ),
                        )
                    }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString(" · "),
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurfaceMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelPicker(
    models: List<ModelInfo>,
    selectedModelId: String?,
    allowDefault: Boolean,
    onModelChange: (String?) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    var expanded by remember { mutableStateOf(false) }
    val selected = selectedModelId?.let { id -> models.firstOrNull { it.id == id } }
    val label = selected?.label()
        ?: if (allowDefault) stringResource(R.string.generate_model_default) else ""

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(radius.md))
                .background(colors.fill)
                .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.md))
                .clickable { expanded = true }
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            GlyphIcon(
                glyph = Glyph.ChevronDown,
                tint = if (expanded) colors.accent else colors.onSurfaceMuted,
                size = 11.dp,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset.Zero,
            shape = RoundedCornerShape(radius.lg),
            containerColor = colors.surfaceRaised,
        ) {
            if (allowDefault) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.generate_model_default)) },
                    onClick = {
                        expanded = false
                        onModelChange(null)
                    },
                )
            }
            for (model in models) {
                DropdownMenuItem(
                    text = { Text(text = model.label()) },
                    onClick = {
                        expanded = false
                        onModelChange(model.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun PromptField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(radius.md))
            .background(colors.fill)
            .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.md))
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = OpenCodeTypography.body.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = OpenCodeTypography.body,
                        color = colors.onSurfaceMuted,
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun GeneratedImage(image: GenerateImageOutput) {
    val colors = OpenCodeTheme.colors
    val radius = OpenCodeTheme.tokens.radius
    val bitmap = remember(image.data) {
        runCatching {
            val bytes = Base64.decode(image.data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = image.name,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(radius.md)),
        )
    } else {
        Text(
            text = stringResource(R.string.generate_image_unreadable),
            style = OpenCodeTypography.bodySmall,
            color = colors.danger,
        )
    }
}
