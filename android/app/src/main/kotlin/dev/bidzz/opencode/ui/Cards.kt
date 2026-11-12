package dev.bidzz.opencode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Floating white card treatment (White B, light theme only).
 *
 * Light: 16dp radius (`tokens.radius.lg`) + 1dp #EBEBEB border
 * (`colors.cardBorder`) + the card shadow from `tokens.shadow.card`
 * (8dp elevation, ambient `rgba(0,0,0,0.06)`, spot `rgba(0,0,0,0.05)`)
 * on the white card surface, so the card lifts off the #F9F9FA page.
 * Dark: the flat grouped treatment instead — raised surface, hairline
 * edge, no shadow — so the same call site renders correctly in both
 * themes without branching on `colors.isDark` itself.
 *
 * Later phases apply this by name to the composer, session rows and
 * other surfaces; [CardGroup] already wires it through its `whiteCard`
 * parameter for grouped settings-style cards.
 *
 * [borderColor] overrides the 1dp edge (the connected-provider green
 * edge, for example); null keeps the default [OpenCodeColors.cardBorder].
 */
@Composable
fun Modifier.whiteCard(borderColor: Color? = null): Modifier {
    val colors = OpenCodeTheme.colors
    val tokens = OpenCodeTheme.tokens
    val shape = RoundedCornerShape(tokens.radius.lg)
    val edge = borderColor ?: colors.cardBorder
    return if (colors.isDark) {
        clip(shape)
            .background(colors.surface)
            .border(tokens.dimensions.hairline, edge, shape)
    } else {
        val shadow = tokens.shadow.card
        shadow(
            elevation = shadow.elevation,
            shape = shape,
            ambientColor = shadow.ambientColor,
            spotColor = shadow.spotColor,
        )
            .clip(shape)
            .background(colors.surface)
            .border(tokens.shadow.cardBorderWidth, edge, shape)
    }
}

/** Rows grouped on one rounded surface with a hairline edge (matching AppGroup in cross-platform-llm-client). */
@Composable
fun CardGroup(
    modifier: Modifier = Modifier,
    /**
     * When true (the default since Opsi B #41), the group renders as a
     * floating white card ([whiteCard]): 16dp radius, 1dp #EBEBEB border,
     * shadow tokens — instead of the flat surface + hairline. Light theme
     * only; in dark the treatment falls back to the flat surface, so the
     * parameter is safe to set unconditionally. Set false only for a
     * deliberately flat group.
     */
    whiteCard: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val shape = RoundedCornerShape(OpenCodeTheme.tokens.radius.lg)
    val baseModifier = if (whiteCard) {
        modifier.fillMaxWidth().whiteCard()
    } else {
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(0.5.dp, colors.hairline, shape)
    }
    Column(
        modifier = baseModifier,
        content = content,
    )
}

/** Rounded neutral surface card. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    outlined: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val shape = RoundedCornerShape(OpenCodeTheme.tokens.radius.lg)
    val baseModifier = modifier
        .clip(shape)
        .background(if (outlined) Color.Transparent else colors.surface)
        .border(
            width = if (outlined) 1.dp else 0.5.dp,
            color = if (outlined) colors.outline else colors.hairline,
            shape = shape,
        )
    val clickableModifier = if (onClick != null) {
        baseModifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        baseModifier
    }
    Box(modifier = clickableModifier.padding(16.dp)) {
        content()
    }
}

/** Icon badge with customizable shape and background (Apple-style squircle or neutral circle). */
@Composable
fun IconBadge(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    background: Color? = null,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    size: Dp = 32.dp,
) {
    val colors = OpenCodeTheme.colors
    val fg = tint ?: if (background != null) Color.White else colors.onSurface.copy(alpha = 0.85f)
    val bg = background ?: colors.fill
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, tint = fg, size = size * 0.53f)
    }
}

/** Hairline between two rows of a card; [inset] lines it up under the text, not the icon. */
@Composable
fun RowDivider(inset: Dp = 0.dp) {
    Box(
        Modifier
            .padding(start = inset)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(OpenCodeTheme.colors.hairline),
    )
}

/**
 * The one primary action button (Opsi B #42): an accent-fill button
 * with white label (+ optional glyph), 48dp minimum height, 12dp
 * radius — the same radius as [TonalButton], so the two button styles
 * align. Replaces the drifted private primary buttons:
 * SidebarPrimaryButton (ChatScreen), PrimaryButton (ProvidersScreen),
 * ChooseFolderButton (ProjectPickerScreen), and the inline accent-fill
 * boxes in ToolRow (Accept) and the status dialog (Diagnostics).
 * Drift it fixes: Providers had no 48dp minimum and no Button role;
 * ChooseFolder was 52dp with a 16dp radius; Accept was an 8dp radius;
 * the sidebar label was 15sp while the other four were 13sp.
 *
 * [enabled] dims the fill instead of hiding the action: a disabled
 * primary button keeps its place in the layout. None of the migrated
 * call sites passes disabled yet — the parameter exists so the next
 * form that needs it does not invent another button.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    enabled: Boolean = true,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dims.minTouchTarget)
            .clip(RoundedCornerShape(radius.md))
            .background(if (enabled) colors.accentFill else colors.fill)
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .padding(horizontal = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) {
            GlyphIcon(
                glyph,
                tint = if (enabled) colors.onAccent else colors.onSurfaceFaint,
                size = dims.iconMedium,
            )
        }
        Text(
            text = label,
            style = OpenCodeTypography.label,
            color = if (enabled) colors.onAccent else colors.onSurfaceFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (glyph != null) Modifier.padding(start = spacing.sm) else Modifier,
        )
    }
}

/** Tonal button: clean modern touch button matching cross-platform-llm-client. */
@Composable
fun TonalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = OpenCodeTheme.colors.onSurface,
    fill: Color = OpenCodeTheme.colors.fill,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.md))
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.label.copy(fontSize = 15.sp),
            color = color,
        )
    }
}

/** Status indicator dot. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 6.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/** Compact label badge / tag. */
@Composable
fun AppTag(
    label: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    glyph: Glyph? = null,
) {
    val colors = OpenCodeTheme.colors
    val fg = color ?: colors.onSurfaceMuted
    val bg = if (color == null) colors.fill else color.copy(alpha = 0.12f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, tint = fg, size = 11.dp)
        }
        Text(
            text = label,
            style = OpenCodeTypography.label.copy(fontSize = 11.sp),
            color = fg,
        )
    }
}
