package dev.bidzz.opencode.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Batch 3 (Aura full, Opsi B): interactive mesh gradient, highlight
 * glow, tilt tracker.
 *
 * Pure JVM: the pointer math, no Android framework, no Robolectric —
 * the same pattern as [HatchBatch1Test]. The composables themselves
 * are verified by compile + the fast-verify run; what is pinned here
 * is the logic a call site cannot see: where the highlight sits for a
 * given pointer, how far a card tilts, and that out-of-bounds pointers
 * clamp instead of overshooting.
 */
class HatchBatch3Test {

    @Test
    fun `pointer fraction is zero without a pointer or size`() {
        assertEquals(Offset(0f, 0f), hatchPointerFraction(null, IntSize(100, 50)))
        assertEquals(Offset(0f, 0f), hatchPointerFraction(Offset(50f, 25f), IntSize.Zero))
    }

    @Test
    fun `pointer fraction maps the centre to half`() {
        assertEquals(Offset(0.5f, 0.5f), hatchPointerFraction(Offset(50f, 25f), IntSize(100, 50)))
        assertEquals(Offset(1f, 1f), hatchPointerFraction(Offset(100f, 50f), IntSize(100, 50)))
    }

    @Test
    fun `pointer fraction clamps out-of-bounds pointers`() {
        assertEquals(Offset(1f, 0f), hatchPointerFraction(Offset(150f, -10f), IntSize(100, 50)))
    }

    @Test
    fun `glow centre converts a fraction back to pixels`() {
        assertEquals(
            Offset(100f, 50f),
            hatchGlowCenterPx(Offset(0.5f, 0.5f), IntSize(200, 100)),
        )
        assertEquals(Offset(0f, 0f), hatchGlowCenterPx(Offset(0f, 0f), IntSize(200, 100)))
    }

    @Test
    fun `glow radius is three quarters of the long side`() {
        assertEquals(150f, hatchGlowRadius(IntSize(100, 200)))
        assertEquals(0f, hatchGlowRadius(IntSize.Zero))
    }

    @Test
    fun `tilt is flat at the centre`() {
        assertEquals(
            HatchTiltAngles(0f, 0f),
            hatchTiltAngles(Offset(50f, 50f), IntSize(100, 100)),
        )
    }

    @Test
    fun `tilt reaches the max at the corners`() {
        assertEquals(
            HatchTiltAngles(8f, -8f),
            hatchTiltAngles(Offset(0f, 0f), IntSize(100, 100)),
        )
        assertEquals(
            HatchTiltAngles(-8f, 8f),
            hatchTiltAngles(Offset(100f, 100f), IntSize(100, 100)),
        )
    }

    @Test
    fun `tilt clamps out-of-bounds pointers to the max`() {
        assertEquals(
            HatchTiltAngles(8f, -8f),
            hatchTiltAngles(Offset(-50f, -50f), IntSize(100, 100)),
        )
    }

    @Test
    fun `tilt is flat for an empty size and honours a custom max`() {
        assertEquals(
            HatchTiltAngles(0f, 0f),
            hatchTiltAngles(Offset(0f, 0f), IntSize.Zero),
        )
        assertEquals(
            HatchTiltAngles(4f, -4f),
            hatchTiltAngles(Offset(0f, 0f), IntSize(100, 100), maxDegrees = 4f),
        )
        assertEquals(8f, HatchTiltMaxDegrees)
    }

    @Test
    fun `tilt tracker tilts on pointer and flattens on reset`() {
        val tracker = HatchTiltTracker()
        assertEquals(HatchTiltAngles(0f, 0f), tracker.angles)
        tracker.onPointer(Offset(0f, 0f), IntSize(100, 100))
        assertEquals(HatchTiltAngles(8f, -8f), tracker.angles)
        tracker.reset()
        assertEquals(HatchTiltAngles(0f, 0f), tracker.angles)
    }
}
