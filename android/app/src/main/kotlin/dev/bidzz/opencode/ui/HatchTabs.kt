package dev.bidzz.opencode.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.AuraPalette
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Batch 5 (Aura full, Opsi B): tab layout, clean-room re-type of
 * Aura's `AuraTabLayout` + `FixedTab` (`aura/tabs` — tab dock + badge).
 *
 * Aura's tabs are fixed-width, equal-weight, with a 2dp indicator bar
 * under the selected tab and a count badge on tabs that have one. The
 * selected label is Aura's text-safe blue; unselected labels are muted;
 * the badge is a danger-red pill with white text (a fill under white
 * text is AA on any surface, per the palette rules).
 *
 * No call site yet: the app has no tab UI today — navigation is the
 * drawer + the [Screen] stack, and no screen switches between sibling
 * views — so the layout ships as a tested component, ready for the
 * first screen that wants sibling tabs. The same honest pattern as
 * Batch 1's [HatchTaskDeleteConfirmationDialog]. What is pinned by
 * tests is the logic a call site cannot see: the badge text, the
 * indicator fractions, and the selected-index fallback.
 */

/** One tab: a stable id, its label, and an optional count badge. */
data class HatchTab(
    val id: String,
    val label: String,
    val badgeCount: Int? = null,
)

/**
 * Pure: the badge's text — nothing when there is no count, "99+"
 * past 99 so the pill never grows past two characters.
 */
fun hatchTabBadgeText(count: Int?): String? = when {
    count == null || count <= 0 -> null
    count > 99 -> "99+"
    else -> count.toString()
}

/**
 * Pure: the selected tab's index — the tab whose id matches, the
 * first tab when nothing matches, -1 when there are no tabs at all.
 */
fun hatchTabSelectedIndex(tabs: List<HatchTab>, selectedId: String?): Int {
    if (tabs.isEmpty()) return -1
    val found = tabs.indexOfFirst { it.id == selectedId }
    return if (found >= 0) found else 0
}

/** Where the indicator bar sits, as fractions of the layout width. */
data class HatchTabIndicatorGeometry(
    val leftFraction: Float,
    val widthFraction: Float,
)

/**
 * Pure: the indicator geometry for [selectedIndex] of [tabCount]
 * equal tabs — the bar starts at selectedIndex/tabCount and spans
 * one tab width. No tabs, or no selection, means no bar.
 */
fun hatchTabIndicatorGeometry(selectedIndex: Int, tabCount: Int): HatchTabIndicatorGeometry {
    if (tabCount <= 0 || selectedIndex < 0 || selectedIndex >= tabCount) {
        return HatchTabIndicatorGeometry(0f, 0f)
    }
    return HatchTabIndicatorGeometry(
        leftFraction = selectedIndex.toFloat() / tabCount,
        widthFraction = 1f / tabCount,
    )
}

/** Pure: a tab's label colour — text-safe blue when selected, muted otherwise. */
fun hatchTabLabelColor(selected: Boolean): Color =
    if (selected) AuraPalette.auraBlueText else AuraPalette.auraMuted

/**
 * One fixed tab: label + optional badge, with a 2dp indicator bar at
 * its bottom when selected. The whole cell is the touch target.
 * [AuraTabLayout] passes [showIndicator] = false and draws the one
 * sliding bar itself, so the layout never shows two bars.
 */
@Composable
fun FixedTab(
    tab: HatchTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showIndicator: Boolean = true,
) {
    val spacing = OpenCodeTheme.tokens.spacing
    Column(
        modifier = modifier
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tab.label,
                style = OpenCodeTypography.label,
                color = hatchTabLabelColor(selected),
            )
            hatchTabBadgeText(tab.badgeCount)?.let { badge ->
                Box(
                    modifier = Modifier
                        .padding(start = spacing.xs)
                        .background(
                            color = AuraPalette.auraDanger,
                            shape = RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = spacing.xs, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badge,
                        style = OpenCodeTypography.caption,
                        color = Color.White,
                    )
                }
            }
        }
        // Indicator bar: Aura blue as a fill (never as text), 2dp tall.
        if (showIndicator) {
            Box(
                modifier = Modifier
                    .padding(top = spacing.xs)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        color = if (selected) AuraPalette.auraBlue else Color.Transparent,
                        shape = RoundedCornerShape(1.dp),
                    ),
            )
        }
    }
}

/**
 * Aura's tab dock: [tabs] laid out fixed-width and equal-weight, with
 * one 2dp indicator bar that slides under the selected tab, eased
 * across 150ms. The bar's geometry is [hatchTabIndicatorGeometry];
 * the layout reads it through the animated left fraction, so the bar
 * glides instead of jumping.
 */
@Composable
fun AuraTabLayout(
    tabs: List<HatchTab>,
    selectedId: String?,
    onSelect: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = hatchTabSelectedIndex(tabs, selectedId)
    val geometry = hatchTabIndicatorGeometry(selectedIndex, tabs.size)
    val indicatorLeft by animateFloatAsState(
        targetValue = geometry.leftFraction,
        animationSpec = tween(durationMillis = 150),
        label = "hatch_tab_indicator_left",
    )
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Capture the width first: inside the Column below, the
        // BoxWithConstraintsScope receiver is shadowed.
        val layoutWidth = maxWidth
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, tab ->
                    FixedTab(
                        tab = tab,
                        selected = index == selectedIndex,
                        onClick = { onSelect(tab.id) },
                        modifier = Modifier.weight(1f),
                        showIndicator = false,
                    )
                }
            }
            // The sliding bar: one tab wide, offset by the animated
            // left fraction. Hidden when there is no selection.
            if (geometry.widthFraction > 0f) {
                Box(
                    modifier = Modifier
                        .offset(x = layoutWidth * indicatorLeft)
                        .width(layoutWidth * geometry.widthFraction)
                        .height(2.dp)
                        .background(
                            color = AuraPalette.auraBlue,
                            shape = RoundedCornerShape(1.dp),
                        ),
                )
            }
        }
    }
}
