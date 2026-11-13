package dev.bidzz.opencode.ui

import androidx.compose.ui.graphics.toArgb
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.AuraPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 1 (Aura full, Opsi B): Hatch dialogs + publish sheet.
 *
 * Pure JVM: the state mapping and the rename rule, no Android
 * framework, no Robolectric — the same pattern as [AuraThemeTest].
 * The composables themselves are verified by compile + the
 * fast-verify run; what is pinned here is the logic a call site
 * cannot see: which dot belongs to which publish state, which
 * label string each state resolves to, and that a rename only
 * confirms a non-blank title.
 */
class HatchBatch1Test {

    @Test
    fun `rename confirms a non-blank title only`() {
        assertFalse(isValidRenameTitle(""))
        assertFalse(isValidRenameTitle("   "))
        assertTrue(isValidRenameTitle("a"))
        assertTrue(isValidRenameTitle("  New title  "))
    }

    @Test
    fun `publish states are exactly the five Hatch variants`() {
        assertEquals(5, HatchPublishState.entries.size)
        assertEquals(
            setOf(
                HatchPublishState.PUBLISH,
                HatchPublishState.SUCCESS,
                HatchPublishState.FAILED,
                HatchPublishState.UP_TO_DATE,
                HatchPublishState.UNPUBLISHED,
            ),
            HatchPublishState.entries.toSet(),
        )
    }

    @Test
    fun `publish dot colors are the Aura fills for each state`() {
        assertEquals(
            AuraPalette.auraBlueFill.toArgb(),
            HatchPublishState.PUBLISH.dotColor().toArgb(),
        )
        assertEquals(
            AuraPalette.auraSuccess.toArgb(),
            HatchPublishState.SUCCESS.dotColor().toArgb(),
        )
        assertEquals(
            AuraPalette.auraDanger.toArgb(),
            HatchPublishState.FAILED.dotColor().toArgb(),
        )
        assertEquals(
            AuraPalette.auraMuted.toArgb(),
            HatchPublishState.UP_TO_DATE.dotColor().toArgb(),
        )
        assertEquals(
            AuraPalette.auraFaint.toArgb(),
            HatchPublishState.UNPUBLISHED.dotColor().toArgb(),
        )
    }

    @Test
    fun `publish labels resolve to five distinct strings`() {
        val labels = HatchPublishState.entries.map { hatchPublishStateLabelRes(it) }
        assertEquals(5, labels.toSet().size)
        assertEquals(R.string.publish_state_publish, labels[0])
        assertEquals(R.string.publish_state_success, labels[1])
        assertEquals(R.string.publish_state_failed, labels[2])
        assertEquals(R.string.publish_state_up_to_date, labels[3])
        assertEquals(R.string.publish_state_unpublished, labels[4])
    }

    @Test
    fun `failed and success dots differ, unpublished is the faint dot`() {
        assertTrue(
            HatchPublishState.FAILED.dotColor().toArgb() !=
                HatchPublishState.SUCCESS.dotColor().toArgb(),
        )
        assertTrue(
            HatchPublishState.UNPUBLISHED.dotColor().toArgb() !=
                HatchPublishState.UP_TO_DATE.dotColor().toArgb(),
        )
    }
}
