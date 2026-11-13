package dev.bidzz.opencode.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.AuraPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 2 (Aura full, Opsi B): Hatch inline banner, pagination footer,
 * row spinner, error state.
 *
 * Pure JVM: the kind mappings and the pagination state, no Android
 * framework, no Robolectric — the same pattern as [HatchBatch1Test].
 * The composables themselves are verified by compile + the fast-verify
 * run; what is pinned here is the logic a call site cannot see: which
 * dot and glyph belong to which banner kind, that a page already in
 * flight is never offered again, and that the row ring is smaller than
 * the full-screen ring.
 */
class HatchBatch2Test {

    @Test
    fun `banner kinds are exactly the four Hatch variants`() {
        assertEquals(4, HatchBannerKind.entries.size)
        assertEquals(
            setOf(
                HatchBannerKind.ERROR,
                HatchBannerKind.WARNING,
                HatchBannerKind.INFO,
                HatchBannerKind.SUCCESS,
            ),
            HatchBannerKind.entries.toSet(),
        )
    }

    @Test
    fun `banner dot colors are the Aura fills for each kind`() {
        val themeWarning = Color(0xFFFF9500)
        assertEquals(
            AuraPalette.auraDanger.toArgb(),
            HatchBannerKind.ERROR.dotColor(themeWarning).toArgb(),
        )
        // Aura has no warning token: the kind passes the theme warning
        // through untouched.
        assertEquals(
            themeWarning.toArgb(),
            HatchBannerKind.WARNING.dotColor(themeWarning).toArgb(),
        )
        assertEquals(
            AuraPalette.auraBlueFill.toArgb(),
            HatchBannerKind.INFO.dotColor(themeWarning).toArgb(),
        )
        assertEquals(
            AuraPalette.auraSuccess.toArgb(),
            HatchBannerKind.SUCCESS.dotColor(themeWarning).toArgb(),
        )
    }

    @Test
    fun `banner glyphs are warning for error and warning, info and check otherwise`() {
        assertEquals(Glyph.Warning, HatchBannerKind.ERROR.glyph())
        assertEquals(Glyph.Warning, HatchBannerKind.WARNING.glyph())
        assertEquals(Glyph.Info, HatchBannerKind.INFO.glyph())
        assertEquals(Glyph.Check, HatchBannerKind.SUCCESS.glyph())
    }

    @Test
    fun `banner error and success dots differ`() {
        val themeWarning = Color(0xFFFF9500)
        assertTrue(
            HatchBannerKind.ERROR.dotColor(themeWarning).toArgb() !=
                HatchBannerKind.SUCCESS.dotColor(themeWarning).toArgb(),
        )
    }

    @Test
    fun `pagination states are exactly the three footer variants`() {
        assertEquals(3, HatchPaginationState.entries.size)
        assertEquals(
            setOf(
                HatchPaginationState.HAS_MORE,
                HatchPaginationState.LOADING,
                HatchPaginationState.END,
            ),
            HatchPaginationState.entries.toSet(),
        )
    }

    @Test
    fun `pagination state offers the next page only when idle and one exists`() {
        assertEquals(
            HatchPaginationState.HAS_MORE,
            hatchPaginationState(hasMore = true, loadingMore = false),
        )
        assertEquals(
            HatchPaginationState.END,
            hatchPaginationState(hasMore = false, loadingMore = false),
        )
    }

    @Test
    fun `pagination loading wins over has-more`() {
        assertEquals(
            HatchPaginationState.LOADING,
            hatchPaginationState(hasMore = true, loadingMore = true),
        )
        assertEquals(
            HatchPaginationState.LOADING,
            hatchPaginationState(hasMore = false, loadingMore = true),
        )
    }

    @Test
    fun `row spinner ring is smaller than the full-screen ring`() {
        assertEquals(16.dp, HatchRowSpinnerSize)
        assertTrue(HatchRowSpinnerSize < 24.dp)
    }
}
