package dev.bidzz.opencode.ui

import dev.bidzz.opencode.core.design.AuraPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Batch 5 (Aura full, Opsi B): tab layout.
 *
 * Pure JVM: the badge text, the indicator fractions, the
 * selected-index fallback, and the label colours — no Android
 * framework, no Robolectric, the same pattern as [HatchBatch4Test].
 * The composables themselves are verified by compile + the
 * fast-verify run; what is pinned here is the logic a call site
 * cannot see.
 */
class HatchBatch5Test {

    @Test
    fun `tab badge text is empty without a count`() {
        assertNull(hatchTabBadgeText(null))
        assertNull(hatchTabBadgeText(0))
        assertNull(hatchTabBadgeText(-3))
    }

    @Test
    fun `tab badge text shows the count up to 99`() {
        assertEquals("1", hatchTabBadgeText(1))
        assertEquals("42", hatchTabBadgeText(42))
        assertEquals("99", hatchTabBadgeText(99))
    }

    @Test
    fun `tab badge text caps past 99`() {
        assertEquals("99+", hatchTabBadgeText(100))
        assertEquals("99+", hatchTabBadgeText(10_000))
    }

    @Test
    fun `tab selected index finds the id or falls back to the first tab`() {
        val tabs = listOf(
            HatchTab("a", "Alpha"),
            HatchTab("b", "Beta"),
            HatchTab("c", "Gamma"),
        )
        assertEquals(1, hatchTabSelectedIndex(tabs, "b"))
        assertEquals(0, hatchTabSelectedIndex(tabs, "missing"))
        assertEquals(0, hatchTabSelectedIndex(tabs, null))
    }

    @Test
    fun `tab selected index is minus one without tabs`() {
        assertEquals(-1, hatchTabSelectedIndex(emptyList(), "a"))
    }

    @Test
    fun `tab indicator geometry spans one tab width`() {
        assertEquals(
            HatchTabIndicatorGeometry(0f, 1f / 3f),
            hatchTabIndicatorGeometry(0, 3),
        )
        assertEquals(
            HatchTabIndicatorGeometry(1f / 3f, 1f / 3f),
            hatchTabIndicatorGeometry(1, 3),
        )
        assertEquals(
            HatchTabIndicatorGeometry(2f / 3f, 1f / 3f),
            hatchTabIndicatorGeometry(2, 3),
        )
    }

    @Test
    fun `tab indicator geometry is empty without a selection`() {
        assertEquals(
            HatchTabIndicatorGeometry(0f, 0f),
            hatchTabIndicatorGeometry(-1, 3),
        )
        assertEquals(
            HatchTabIndicatorGeometry(0f, 0f),
            hatchTabIndicatorGeometry(3, 3),
        )
        assertEquals(
            HatchTabIndicatorGeometry(0f, 0f),
            hatchTabIndicatorGeometry(0, 0),
        )
    }

    @Test
    fun `tab label colour is text-safe blue selected and muted otherwise`() {
        assertEquals(AuraPalette.auraBlueText, hatchTabLabelColor(true))
        assertEquals(AuraPalette.auraMuted, hatchTabLabelColor(false))
    }
}
