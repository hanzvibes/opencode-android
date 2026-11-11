package dev.bidzz.opencode.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Opsi C — the sheet's snap rules (SheetSnap.kt). Pure Kotlin, no Compose:
 * a fling moves one stop, a slow release snaps to the nearest stop, and
 * dragging below the Peek stop dismisses. A dismiss is never a confirm —
 * the composable turns it into onDismiss only.
 */
class SheetSnapTest {

    private val stops = sheetStopHeights(1000f) // Peek 320, Half 580, Full 940

    @Test
    fun `stops are peek half full fractions of the available height`() {
        // Delta, not exact equality: 0.32f * 1000f is 320f only up to float
        // rounding, and the snap rules only need the stops to be close.
        assertEquals(320f, stops[0], 0.01f)
        assertEquals(580f, stops[1], 0.01f)
        assertEquals(940f, stops[2], 0.01f)
    }

    @Test
    fun `slow release near peek snaps to peek`() {
        assertEquals(SheetSnap.Stop(0), resolveSheetSnap(300f, 0f, stops))
        assertEquals(SheetSnap.Stop(0), resolveSheetSnap(340f, 0f, stops))
    }

    @Test
    fun `slow release near half snaps to half`() {
        assertEquals(SheetSnap.Stop(1), resolveSheetSnap(500f, 0f, stops))
        assertEquals(SheetSnap.Stop(1), resolveSheetSnap(650f, 0f, stops))
    }

    @Test
    fun `slow release near full snaps to full`() {
        assertEquals(SheetSnap.Stop(2), resolveSheetSnap(900f, 0f, stops))
    }

    @Test
    fun `slow release dragged below sixty percent of peek dismisses`() {
        // Peek is 320, so 60% is 192: below it the drag clearly means close.
        assertEquals(SheetSnap.Dismiss, resolveSheetSnap(191f, 0f, stops))
        assertEquals(SheetSnap.Dismiss, resolveSheetSnap(100f, 0f, stops))
    }

    @Test
    fun `slow release exactly at the dismiss threshold snaps back to peek`() {
        assertEquals(SheetSnap.Stop(0), resolveSheetSnap(192f, 0f, stops))
        assertEquals(SheetSnap.Stop(0), resolveSheetSnap(200f, 0f, stops))
    }

    @Test
    fun `fast upward fling moves one stop up`() {
        assertEquals(SheetSnap.Stop(1), resolveSheetSnap(320f, -500f, stops))
        assertEquals(SheetSnap.Stop(2), resolveSheetSnap(580f, -500f, stops))
    }

    @Test
    fun `fast upward fling at full stays at full`() {
        assertEquals(SheetSnap.Stop(2), resolveSheetSnap(940f, -500f, stops))
    }

    @Test
    fun `fast downward fling moves one stop down`() {
        assertEquals(SheetSnap.Stop(1), resolveSheetSnap(940f, 500f, stops))
        assertEquals(SheetSnap.Stop(0), resolveSheetSnap(580f, 500f, stops))
    }

    @Test
    fun `fast downward fling from peek dismisses`() {
        assertEquals(SheetSnap.Dismiss, resolveSheetSnap(320f, 500f, stops))
    }

    @Test
    fun `velocity exactly at the fling threshold is a slow release`() {
        // 400 px/s is the threshold, not a fling: nearest stop wins.
        assertEquals(SheetSnap.Stop(1), resolveSheetSnap(580f, 400f, stops))
        assertEquals(SheetSnap.Stop(1), resolveSheetSnap(580f, -400f, stops))
    }
}
