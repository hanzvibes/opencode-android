package dev.bidzz.opencode.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 4 (Aura full, Opsi B): reorderable lists, composer/floating
 * chrome/window geometry, file actions.
 *
 * Pure JVM: the list moves, the centre-index mapping, the inset and
 * elevation math, and the edit-button visibility — no Android
 * framework, no Robolectric, the same pattern as [HatchBatch3Test].
 * The composables themselves are verified by compile + the
 * fast-verify run; what is pinned here is the logic a call site
 * cannot see.
 */
class HatchBatch4Test {

    @Test
    fun `reordered list moves forward to the landing index`() {
        assertEquals(
            listOf("b", "c", "a", "d"),
            hatchReorderedList(listOf("a", "b", "c", "d"), 0, 2),
        )
    }

    @Test
    fun `reordered list moves backward to the landing index`() {
        assertEquals(
            listOf("a", "d", "b", "c"),
            hatchReorderedList(listOf("a", "b", "c", "d"), 3, 1),
        )
    }

    @Test
    fun `reordered list is unchanged for a move onto itself or out of range`() {
        val items = listOf("a", "b", "c")
        assertEquals(items, hatchReorderedList(items, 1, 1))
        assertEquals(items, hatchReorderedList(items, -1, 1))
        assertEquals(items, hatchReorderedList(items, 0, 3))
        assertEquals(emptyList<String>(), hatchReorderedList(emptyList<String>(), 0, 0))
    }

    @Test
    fun `reorder target index follows the item centre`() {
        assertEquals(0, hatchReorderTargetIndex(-10f, 100f, 3))
        assertEquals(0, hatchReorderTargetIndex(0f, 100f, 3))
        assertEquals(1, hatchReorderTargetIndex(60f, 100f, 3))
        assertEquals(2, hatchReorderTargetIndex(250f, 100f, 3))
    }

    @Test
    fun `reorder target index clamps and rejects empty input`() {
        assertEquals(2, hatchReorderTargetIndex(10_000f, 100f, 3))
        assertEquals(-1, hatchReorderTargetIndex(0f, 100f, 0))
        assertEquals(-1, hatchReorderTargetIndex(0f, 0f, 3))
        assertEquals(-1, hatchReorderTargetIndex(0f, -50f, 3))
    }

    @Test
    fun `reorder target from sizes follows the centre across spans`() {
        val sizes = listOf(100f, 50f, 200f)
        assertEquals(0, hatchReorderTargetIndexFromSizes(40f, sizes))
        assertEquals(1, hatchReorderTargetIndexFromSizes(120f, sizes))
        assertEquals(2, hatchReorderTargetIndexFromSizes(200f, sizes))
        assertEquals(2, hatchReorderTargetIndexFromSizes(10_000f, sizes))
        assertEquals(0, hatchReorderTargetIndexFromSizes(-5f, sizes))
    }

    @Test
    fun `reorder target from sizes rejects an empty list`() {
        assertEquals(-1, hatchReorderTargetIndexFromSizes(0f, emptyList()))
    }

    @Test
    fun `reorder elevation lifts only while dragging`() {
        assertEquals(8.dp, hatchReorderElevation(true))
        assertEquals(0.dp, hatchReorderElevation(false))
    }

    @Test
    fun `composer bottom inset stacks keyboard and nav bar`() {
        assertEquals(800, hatchComposerBottomInset(600, 200))
        assertEquals(200, hatchComposerBottomInset(0, 200))
        assertEquals(0, hatchComposerBottomInset(0, 0))
    }

    @Test
    fun `composer bottom inset reads negatives as zero`() {
        assertEquals(200, hatchComposerBottomInset(-50, 200))
        assertEquals(0, hatchComposerBottomInset(-50, -20))
    }

    @Test
    fun `floating chrome offset follows the drag within its max`() {
        assertEquals(120f, hatchFloatingChromeOffsetY(120f, 300f))
        assertEquals(-120f, hatchFloatingChromeOffsetY(-120f, 300f))
        assertEquals(300f, hatchFloatingChromeOffsetY(999f, 300f))
        assertEquals(-300f, hatchFloatingChromeOffsetY(-999f, 300f))
        assertEquals(0f, hatchFloatingChromeOffsetY(120f, 0f))
    }

    @Test
    fun `floating chrome elevation prefers dragging over focused over idle`() {
        assertEquals(8.dp, hatchFloatingChromeElevation(true, true, 8.dp, 4.dp, 2.dp))
        assertEquals(4.dp, hatchFloatingChromeElevation(false, true, 8.dp, 4.dp, 2.dp))
        assertEquals(2.dp, hatchFloatingChromeElevation(false, false, 8.dp, 4.dp, 2.dp))
    }

    @Test
    fun `window geometry converts pixels and reports the keyboard`() {
        val geometry = hatchWindowGeometry(1080, 2400, 800, Density(2f))
        assertEquals(540.dp, geometry.widthDp)
        assertEquals(1200.dp, geometry.heightDp)
        assertEquals(400.dp, geometry.imeHeightDp)
        assertTrue(geometry.isKeyboardVisible)
    }

    @Test
    fun `window geometry hides the keyboard when the ime reports zero`() {
        val geometry = hatchWindowGeometry(1080, 2400, 0, Density(2f))
        assertEquals(0.dp, geometry.imeHeightDp)
        assertFalse(geometry.isKeyboardVisible)
    }

    @Test
    fun `edit file button shows only for a loaded non-truncated file`() {
        assertTrue(hatchEditFileButtonVisible(false, false, false, false, false))
        assertFalse(hatchEditFileButtonVisible(true, false, false, false, false))
        assertFalse(hatchEditFileButtonVisible(false, true, false, false, false))
        assertFalse(hatchEditFileButtonVisible(false, false, true, false, false))
        assertFalse(hatchEditFileButtonVisible(false, false, false, true, false))
        assertFalse(hatchEditFileButtonVisible(false, false, false, false, true))
    }
}
