package dev.bidzz.opencode.ui

import android.app.Activity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
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

/**
 * Window size class for the current window, recomputed when its size changes.
 *
 * Audit-200 B7 (M-235): this used to compute the class from
 * `LocalConfiguration.screenWidthDp` — the SCREEN width, not the window.
 * In split-screen the window can be far narrower than the screen, so the
 * app still believed it was Expanded: Files wired its detail opener while
 * the explorer (which measures its real content width) rendered no pane,
 * swallowing file taps, and the Sessions list-detail + persistent sidebar
 * squeezed the chat column into the narrow window. The official
 * [calculateWindowSizeClass] reads the WindowMetrics — the real window
 * bounds — so the class follows the window, not the screen.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val activity = LocalContext.current as Activity
    return calculateWindowSizeClass(activity)
}

/** True at [ExpandedWidthBreakpoint] and above. */
val WindowSizeClass.isExpandedWidth: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Expanded
