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
            HatchPublishState.PUBLISH.dotColor(isDark = false).toArgb(),
        )
        assertEquals(
            AuraPalette.auraSuccess.toArgb(),
            HatchPublishState.SUCCESS.dotColor(isDark = false).toArgb(),
        )
        assertEquals(
            AuraPalette.auraDanger.toArgb(),
            HatchPublishState.FAILED.dotColor(isDark = false).toArgb(),
        )
        assertEquals(
            AuraPalette.auraMuted.toArgb(),
            HatchPublishState.UP_TO_DATE.dotColor(isDark = false).toArgb(),
        )
        assertEquals(
            AuraPalette.auraFaint.toArgb(),
            HatchPublishState.UNPUBLISHED.dotColor(isDark = false).toArgb(),
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
    fun `publish titles and messages default per state, five distinct each (D1 m-696)`() {
        // A sheet with no title/message must still say what happened.
        val titles = HatchPublishState.entries.map { hatchPublishTitleRes(it) }
        val messages = HatchPublishState.entries.map { hatchPublishMessageRes(it) }
        assertEquals(5, titles.toSet().size)
        assertEquals(5, messages.toSet().size)
        assertEquals(R.string.publish_title_publish, titles[0])
        assertEquals(R.string.publish_title_success, titles[1])
        assertEquals(R.string.publish_title_failed, titles[2])
        assertEquals(R.string.publish_title_up_to_date, titles[3])
        assertEquals(R.string.publish_title_unpublished, titles[4])
        assertEquals(R.string.publish_message_publish, messages[0])
        assertEquals(R.string.publish_message_success, messages[1])
        assertEquals(R.string.publish_message_failed, messages[2])
        assertEquals(R.string.publish_message_up_to_date, messages[3])
        assertEquals(R.string.publish_message_unpublished, messages[4])
    }

    @Test
    fun `failed and success dots differ, unpublished is the faint dot`() {
        assertTrue(
            HatchPublishState.FAILED.dotColor(isDark = false).toArgb() !=
                HatchPublishState.SUCCESS.dotColor(isDark = false).toArgb(),
        )
        assertTrue(
            HatchPublishState.UNPUBLISHED.dotColor(isDark = false).toArgb() !=
                HatchPublishState.UP_TO_DATE.dotColor(isDark = false).toArgb(),
        )
    }
}
