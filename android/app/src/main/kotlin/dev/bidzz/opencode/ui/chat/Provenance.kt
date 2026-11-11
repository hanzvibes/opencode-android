package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.AgentInfo
import dev.bidzz.opencode.core.api.dto.ModelInfo
import dev.bidzz.opencode.core.api.dto.ModelRef
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Known AI model providers for branded badge representation.
 *
 * Identity only — no colours live here. The brand colour comes from
 * OpenCodeColors.providerColor (Theme.kt), the single source of truth,
 * and is used only on the 8dp dot in [ProvenanceBadge].
 */
enum class ModelProvider(val displayName: String) {
    ANTHROPIC("Anthropic"),
    OPENAI("OpenAI"),
    GOOGLE("Google"),
    DEEPSEEK("DeepSeek"),
    META("Meta"),
    MISTRAL("Mistral"),
    OPENCODE("OpenCode"),
    OTHER("AI"),
}

/**
 * Structured provenance breakdown for UI badge presentation.
 */
data class ProvenanceTag(
    val fullLabel: String,
    val mode: String?,
    val model: String?,
    val provider: ModelProvider,
)

/**
 * Detects the model provider from provider ID, model ID, or full label text.
 */
fun detectProvider(
    providerID: String? = null,
    modelID: String? = null,
    rawLabel: String? = null,
): ModelProvider {
    val combined = listOfNotNull(providerID, modelID, rawLabel)
        .joinToString(" ")
        .lowercase()

    return when {
        combined.contains("anthropic") || combined.contains("claude") -> ModelProvider.ANTHROPIC
        combined.contains("openai") || combined.contains("chatgpt") || combined.contains("gpt") ||
            Regex("""\b(o1|o3|o4)(-[a-z0-9]+)?\b""").containsMatchIn(combined) -> ModelProvider.OPENAI
        combined.contains("google") || combined.contains("gemini") -> ModelProvider.GOOGLE
        combined.contains("deepseek") -> ModelProvider.DEEPSEEK
        combined.contains("meta") || combined.contains("llama") -> ModelProvider.META
        combined.contains("mistral") || combined.contains("codestral") -> ModelProvider.MISTRAL
        combined.contains("opencode") -> ModelProvider.OPENCODE
        else -> ModelProvider.OTHER
    }
}

/**
 * Cleans namespaced model IDs (e.g. "anthropic/claude-3-7-sonnet" -> "claude-3-7-sonnet").
 */
fun cleanModelTag(model: String): String {
    val trimmed = model.trim()
    return if (trimmed.contains('/')) {
        trimmed.substringAfterLast('/')
    } else {
        trimmed
    }
}

/**
 * Parses raw provenance string and optional [ModelRef] into structured [ProvenanceTag].
 */
fun parseProvenanceTag(
    label: String?,
    modelRef: ModelRef? = null,
): ProvenanceTag? {
    if (label.isNullOrBlank()) return null
    val provider = detectProvider(
        providerID = modelRef?.providerID,
        modelID = modelRef?.id,
        rawLabel = label,
    )
    val mode: String?
    val modelRaw: String?
    if (label.contains(" · ")) {
        mode = label.substringBefore(" · ").trim().takeIf { it.isNotEmpty() }
        modelRaw = label.substringAfter(" · ").trim().takeIf { it.isNotEmpty() }
    } else {
        mode = null
        modelRaw = label.trim()
    }
    val modelClean = modelRaw?.let { cleanModelTag(it) }
    return ProvenanceTag(
        fullLabel = label,
        mode = mode,
        model = modelClean ?: modelRaw,
        provider = provider,
    )
}

/**
 * Who produced one assistant message: the mode it ran in, and the model that answered.
 *
 * The engine records the model on the assistant message itself, so changing the model
 * halfway through a conversation does not relabel the earlier replies — the transcript
 * says what actually produced each one. That per-message identity is the whole point;
 * a header chip only shows what the *next* reply will use.
 *
 * Returns null when neither is known. That is the normal state while a message is still
 * streaming: the text deltas carry no model, and the identity arrives with the history
 * re-read once the run finishes.
 */
fun provenanceLabel(
    agent: String?,
    model: ModelRef?,
    agents: List<AgentInfo>,
    models: List<ModelInfo>,
): String? {
    val mode = agent?.trim()?.takeIf { it.isNotEmpty() }?.let { id ->
        agents.firstOrNull { it.id == id }?.name?.trim()?.takeIf { it.isNotEmpty() } ?: id
    }
    // Falls back to the id the message carries, not to the catalog's: after a provider
    // is removed, the transcript should still name the model that answered.
    val modelName = model?.id?.trim()?.takeIf { it.isNotEmpty() }?.let { id ->
        models.firstOrNull { it.ref() == model }?.label() ?: id
    }
    return listOfNotNull(mode, modelName)
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
}

/**
 * Vector provider badge icon drawn on a 24-unit coordinate grid.
 *
 * The glyph is drawn in the caller's [tint] — a neutral tone in the badge.
 * Provider brand colour lives only on the 8dp dot next to it, resolved
 * through OpenCodeColors.providerColor, never on the glyph itself.
 */
@Composable
fun ProviderIcon(
    provider: ModelProvider,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    tint: Color,
) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val strokeWidth = 2f * u

        when (provider) {
            ModelProvider.ANTHROPIC -> {
                // Claude radiating 8-spoke spark / asterisk
                val c = 12f
                val r = 8.5f
                val d = 6.0f
                drawLine(tint, Offset(c * u, (c - r) * u), Offset(c * u, (c + r) * u), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, Offset((c - r) * u, c * u), Offset((c + r) * u, c * u), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, Offset((c - d) * u, (c - d) * u), Offset((c + d) * u, (c + d) * u), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, Offset((c - d) * u, (c + d) * u), Offset((c + d) * u, (c - d) * u), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            }
            ModelProvider.OPENAI -> {
                // OpenAI swirling rosette
                val cx = 12f * u
                val cy = 12f * u
                val radius = 7.5f * u
                for (i in 0 until 6) {
                    val angle = (i * Math.PI / 3.0).toFloat()
                    val x1 = cx + (radius * 0.45f * kotlin.math.cos(angle))
                    val y1 = cy + (radius * 0.45f * kotlin.math.sin(angle))
                    val x2 = cx + (radius * kotlin.math.cos(angle + 0.55f))
                    val y2 = cy + (radius * kotlin.math.sin(angle + 0.55f))
                    drawLine(tint, Offset(x1, y1), Offset(x2, y2), strokeWidth = strokeWidth * 0.85f, cap = StrokeCap.Round)
                }
                drawCircle(tint, radius = 2.2f * u, center = Offset(cx, cy), style = Stroke(width = strokeWidth * 0.85f))
            }
            ModelProvider.GOOGLE -> {
                // Gemini diamond sparkle star
                val path = Path().apply {
                    moveTo(12f * u, 2.5f * u)
                    quadraticBezierTo(12f * u, 12f * u, 21.5f * u, 12f * u)
                    quadraticBezierTo(12f * u, 12f * u, 12f * u, 21.5f * u)
                    quadraticBezierTo(12f * u, 12f * u, 2.5f * u, 12f * u)
                    quadraticBezierTo(12f * u, 12f * u, 12f * u, 2.5f * u)
                    close()
                }
                drawPath(path, tint)
            }
            ModelProvider.DEEPSEEK -> {
                // DeepSeek stylized whale fin / D
                val path = Path().apply {
                    moveTo(6f * u, 5f * u)
                    lineTo(13f * u, 5f * u)
                    cubicTo(19f * u, 5f * u, 20f * u, 9.5f * u, 18f * u, 13.5f * u)
                    cubicTo(16f * u, 18f * u, 11f * u, 19f * u, 6f * u, 19f * u)
                    close()
                }
                drawPath(path, tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(tint, Offset(6f * u, 5f * u), Offset(6f * u, 19f * u), strokeWidth = strokeWidth * 1.2f, cap = StrokeCap.Round)
            }
            ModelProvider.META -> {
                // Infinity loop
                val path = Path().apply {
                    moveTo(8f * u, 12f * u)
                    cubicTo(4f * u, 6.5f * u, 4f * u, 17.5f * u, 8f * u, 12f * u)
                    cubicTo(12f * u, 6.5f * u, 16f * u, 17.5f * u, 20f * u, 12f * u)
                    cubicTo(20f * u, 6.5f * u, 16f * u, 17.5f * u, 12f * u, 12f * u)
                    close()
                }
                drawPath(path, tint, style = Stroke(width = strokeWidth * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            ModelProvider.MISTRAL -> {
                // Stepped M
                val path = Path().apply {
                    moveTo(4.5f * u, 18f * u)
                    lineTo(4.5f * u, 7f * u)
                    lineTo(9.5f * u, 13.5f * u)
                    lineTo(14.5f * u, 13.5f * u)
                    lineTo(19.5f * u, 7f * u)
                    lineTo(19.5f * u, 18f * u)
                }
                drawPath(path, tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            ModelProvider.OPENCODE, ModelProvider.OTHER -> {
                // Clean 4-point AI sparkle
                val path = Path().apply {
                    moveTo(12f * u, 4f * u)
                    quadraticBezierTo(12f * u, 12f * u, 20f * u, 12f * u)
                    quadraticBezierTo(12f * u, 12f * u, 12f * u, 20f * u)
                    quadraticBezierTo(12f * u, 12f * u, 4f * u, 12f * u)
                    quadraticBezierTo(12f * u, 12f * u, 12f * u, 4f * u)
                    close()
                }
                drawPath(path, tint)
            }
        }
    }
}

/**
 * Polished provenance tag badge with provider dot, mode, and model tag.
 *
 * The provider brand colour (from OpenCodeColors.providerColor) appears
 * only on the 8dp dot — never on text, background, or border. The provider
 * glyph is a neutral shape next to it. When the identity is the "OpenCode"
 * fallback (no model known yet), the badge is the 20dp logomark instead
 * of the word "OpenCode".
 */
@Composable
fun ProvenanceBadge(
    provenance: String,
    modifier: Modifier = Modifier,
    modelRef: ModelRef? = null,
    streaming: Boolean = false,
) {
    val tag = remember(provenance, modelRef) {
        parseProvenanceTag(provenance, modelRef)
    } ?: return

    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions

    val badgeShape = RoundedCornerShape(radius.full)

    // The "OpenCode" fallback identity (no model known): the logomark
    // carries the brand, so no dot, glyph, or word is needed beside it.
    val isOpenCodeFallback = tag.provider == ModelProvider.OPENCODE &&
        tag.model.equals("OpenCode", ignoreCase = true) && tag.mode.isNullOrBlank()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(badgeShape)
            .background(colors.surfaceVariant.copy(alpha = 0.55f))
            .border(0.5.dp, colors.hairline, badgeShape)
            .padding(horizontal = spacing.sm, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        if (isOpenCodeFallback) {
            Image(
                painter = painterResource(R.drawable.ic_logomark),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(radius.sm)),
            )
        } else {
            // Provider brand colour, on the 8dp dot only.
            val providerColor = colors.providerColor(tag.provider.displayName)
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(providerColor, CircleShape),
            )
            ProviderIcon(
                provider = tag.provider,
                size = 12.dp,
                tint = colors.onSurfaceMuted,
            )
        }

        // Mode if present (e.g. "Build", "Plan")
        if (!tag.mode.isNullOrBlank()) {
            Text(
                text = tag.mode,
                style = OpenCodeTypography.caption,
                color = colors.onSurfaceFaint,
            )
            Text(
                text = "·",
                style = OpenCodeTypography.caption,
                color = colors.onSurfaceFaint,
            )
        }

        // Model name tag, through the same friendly-name mapping the
        // model selector uses — the transcript used to show the raw id
        // ("anthropic/claude-3-7-sonnet") while the selector said
        // "Claude 3.7 Sonnet". Skipped for the OpenCode fallback above:
        // the logomark already says it.
        if (!isOpenCodeFallback) {
            Text(
                text = tag.model?.let { cleanModelName(it, it) }.orEmpty(),
                style = OpenCodeTypography.caption,
                color = colors.onSurfaceMuted,
            )
        }

        // Streaming spinner if generation is ongoing
        if (streaming) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(start = spacing.xxs)
                    .size(dims.statusDotSize),
                strokeWidth = 1.5.dp,
                color = colors.accent,
            )
        }
    }
}
