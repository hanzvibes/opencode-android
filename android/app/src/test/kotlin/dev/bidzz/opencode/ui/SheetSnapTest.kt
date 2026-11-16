package dev.bidzz.opencode.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `m-692 a locked sheet snaps back to peek instead of dismissing`() {
        // Audit-200 D15 (m-692): while a publish is in flight the sheet
        // cannot be dragged away — both dismiss paths land on Peek.
        assertEquals(SheetSnap.Stop(0), resolveSheetSnap(191f, 0f, stops, dismissable = false))
        assertEquals(SheetSnap.Stop(0), resolveSheetSnap(100f, 0f, stops, dismissable = false))
        assertEquals(SheetSnap.Stop(0), resolveSheetSnap(320f, 800f, stops, dismissable = false))
    }

    @Test
    fun `m-692 a locked sheet still moves between stops`() {
        // Only the dismiss is blocked: an upward fling still climbs,
        // and a downward fling from Half still lands on Peek.
        assertEquals(SheetSnap.Stop(1), resolveSheetSnap(320f, -800f, stops, dismissable = false))
        assertEquals(SheetSnap.Stop(0), resolveSheetSnap(580f, 800f, stops, dismissable = false))
        assertEquals(SheetSnap.Stop(0), resolveSheetSnap(300f, 0f, stops, dismissable = false))
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

    // Audit-200 D8 (m-950): Peek has an absolute floor (220dp at the
    // call site) — without it a short landscape container opens the
    // sheet below its own chrome, hiding the delete message.

    @Test
    fun `m-950 peek never drops below its floor on a short screen`() {
        // 0.32 * 400 is 128, below the 220 floor.
        val short = sheetStopHeights(400f, peekMinPx = 220f)
        assertEquals(220f, short[0], 0.01f)
        assertEquals(232f, short[1], 0.01f)
        assertEquals(376f, short[2], 0.01f)
    }

    @Test
    fun `m-950 the floor never changes a tall screen`() {
        // 0.32 * 1000 is 320, above the 220 floor: the pinned stops stay.
        val tall = sheetStopHeights(1000f, peekMinPx = 220f)
        assertEquals(320f, tall[0], 0.01f)
        assertEquals(580f, tall[1], 0.01f)
        assertEquals(940f, tall[2], 0.01f)
    }

    @Test
    fun `m-950 the floor is capped at half so the stops stay ordered`() {
        // 0.58 * 360 is 208.8, below the 220 floor: Peek == Half rather
        // than overtaking it.
        val tiny = sheetStopHeights(360f, peekMinPx = 220f)
        assertEquals(208.8f, tiny[0], 0.01f)
        assertEquals(208.8f, tiny[1], 0.01f)
        assertEquals(338.4f, tiny[2], 0.01f)
        assertTrue(tiny[0] <= tiny[1] && tiny[1] <= tiny[2])
    }
}
