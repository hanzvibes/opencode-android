package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.OpenCodeTheme

/**
 * A quiet vertical scrollbar thumb for the chat transcript (Fondasi Opsi B
 * #6d). LazyColumn ships no scrollbar, so the thumb is drawn from
 * [LazyListState.layoutInfo]: its height tracks the visible fraction of
 * the transcript, its offset tracks the scroll position. Read-only in this
 * PR — dragging the thumb is a later phase. Nothing is drawn while the
 * whole transcript fits on screen.
 */
@Composable
fun TranscriptScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val density = LocalDensity.current
    val geometry by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo
            if (total <= 0 || visible.isEmpty()) return@derivedStateOf null
            val viewportHeight = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
            val avgItemHeight = visible.sumOf { it.size }.toFloat() / visible.size
            if (avgItemHeight <= 0f) return@derivedStateOf null
            val totalHeight = avgItemHeight * total
            if (totalHeight <= viewportHeight) return@derivedStateOf null
            val minThumbPx = with(density) { 24.dp.toPx() }
            val thumbHeight = (viewportHeight.toFloat() / totalHeight * viewportHeight)
                .coerceIn(minThumbPx, viewportHeight.toFloat())
            val first = visible.first()
            val scrolled = first.index * avgItemHeight + first.offset
            val thumbOffset = (scrolled / totalHeight * (viewportHeight - thumbHeight))
                .coerceIn(0f, (viewportHeight - thumbHeight).coerceAtLeast(0f))
            ThumbGeometry(heightPx = thumbHeight, offsetPx = thumbOffset)
        }
    }
    val geometryNow = geometry ?: return
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(with(density) { geometryNow.heightPx.toDp() })
                .offset { IntOffset(0, geometryNow.offsetPx.toInt()) }
                .clip(RoundedCornerShape(2.dp))
                .background(colors.outline.copy(alpha = 0.5f)),
        )
    }
}

private data class ThumbGeometry(val heightPx: Float, val offsetPx: Float)
