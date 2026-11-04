package dev.bidzz.opencode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.OpenCodeTheme

/** Rows grouped on one rounded surface with a hairline edge: the shared "card" of the app. */
@Composable
fun CardGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = OpenCodeTheme.colors
    val shape = RoundedCornerShape(OpenCodeTheme.tokens.radius.lg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(0.5.dp, colors.outline, shape),
        content = content,
    )
}

/** A glyph on a softly tinted rounded square; the leading mark of a list row. */
@Composable
fun IconBadge(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    tint: Color = OpenCodeTheme.colors.accent,
    size: Dp = 36.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.sm))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, tint = tint, size = size * 0.55f)
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
            .background(OpenCodeTheme.colors.outline),
    )
}
