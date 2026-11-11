package dev.bidzz.opencode.ui

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The width at which the app switches to its expanded layout: the
 * navigation drawer becomes a persistent sidebar and list screens gain a
 * detail pane beside the list. This is the Material 3 WindowSizeClass
 * Expanded breakpoint (840dp); [isExpandedWidth] is the single check, so
 * no screen re-implements the threshold.
 *
 * Below it (Compact <600dp, Medium 600–839dp) every screen keeps its
 * phone behaviour exactly: modal drawer, full-screen navigation, current
 * content caps. Landscape phones land in Medium, so they keep the phone
 * layout while still getting the 720dp content caps.
 */
val ExpandedWidthBreakpoint: Dp = 840.dp

/** Width of the persistent navigation sidebar (Opsi 2 redesign): 285dp, slimmer than the old 320dp. */
val PersistentSidebarWidth: Dp = 285.dp

/** Width of the collapsed sidebar rail: logomark + New session icon, both on 48dp targets. */
val CollapsedSidebarWidth: Dp = 64.dp

/** Width of the list pane in a list-detail layout (sessions, files). */
val ListPaneWidth: Dp = 360.dp

/** Window size class for the current window, recomputed when its size changes. */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val config = LocalConfiguration.current
    return remember(config.screenWidthDp, config.screenHeightDp) {
        WindowSizeClass.calculateFromSize(
            DpSize(config.screenWidthDp.dp, config.screenHeightDp.dp),
        )
    }
}

/** True at [ExpandedWidthBreakpoint] and above. */
val WindowSizeClass.isExpandedWidth: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Expanded
