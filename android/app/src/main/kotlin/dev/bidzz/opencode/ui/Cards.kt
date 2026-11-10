package dev.bidzz.opencode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/** Rows grouped on one rounded surface with a hairline edge (matching AppGroup in cross-platform-llm-client). */
@Composable
fun CardGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = OpenCodeTheme.colors
    val shape = RoundedCornerShape(OpenCodeTheme.tokens.radius.lg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(0.5.dp, colors.hairline, shape),
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
