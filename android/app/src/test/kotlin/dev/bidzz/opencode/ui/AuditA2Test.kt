package dev.bidzz.opencode.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.bidzz.opencode.ui.chat.ToolFilter
import dev.bidzz.opencode.ui.chat.toolFilterForName
import dev.bidzz.opencode.ui.diff.DiffViewMode
import dev.bidzz.opencode.ui.diff.diffViewModeForName
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Audit-200 Fase A2: the rememberSaveable sweep.
 *
 * C-006 (PROVEN) was [HatchRenameDialog] holding a [TextFieldValue]
 * in `rememberSaveable` without a saver — backgrounding the app with
 * the dialog open crashed in `onSaveInstanceState`. The one-time
 * sweep of all 41 `rememberSaveable` call sites found two more of the
 * same class: the [ToolFilter] enum in the tool timeline and the
 * [DiffViewMode] enum in the diff viewer — enums have no built-in
 * saver either, so both now persist by NAME, the same pattern the
 * codebase already used for `diffDecisionName`.
 *
 * Pure JVM: the saver round-trip and the name mappings — no Android
 * framework, no Robolectric. The screens themselves are verified by
 * compile + the fast-verify run; what is pinned here is that a save
 * restores exactly what was typed, selected, or filtered, and that
 * an unknown saved name falls back instead of throwing.
 */
class AuditA2Test {

    // Saver.save is a member with a SaverScope receiver, so it can
    // only be called with the saver as the (implicit) dispatch receiver
    // and a scope in scope — the same shape Compose's own saver tests
    // use. The scope only answers whether a value may be saved, and
    // TextFieldValue's saver only ever hands it saveable values.
    private val saverScope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private fun Saver<TextFieldValue, Any>.roundTrip(value: TextFieldValue): TextFieldValue? {
        val saved = with(saverScope) { save(value) } ?: return null
        return restore(saved)
    }

    @Test
    fun `textfieldvalue saver round-trips text and selection`() {
        val original = TextFieldValue(text = "hello", selection = TextRange(1, 4))
        assertEquals(original, TextFieldValue.Saver.roundTrip(original))
    }

    @Test
    fun `textfieldvalue saver round-trips an empty value`() {
        val original = TextFieldValue(text = "", selection = TextRange.Zero)
        assertEquals(original, TextFieldValue.Saver.roundTrip(original))
    }

    @Test
    fun `tool filter name maps every filter and falls back to all`() {
        assertEquals(ToolFilter.ALL, toolFilterForName("ALL"))
        assertEquals(ToolFilter.FAILED, toolFilterForName("FAILED"))
        assertEquals(ToolFilter.EDIT, toolFilterForName("EDIT"))
        assertEquals(ToolFilter.READ, toolFilterForName("READ"))
        assertEquals(ToolFilter.ALL, toolFilterForName("NOPE"))
        assertEquals(ToolFilter.ALL, toolFilterForName(""))
    }

    @Test
    fun `diff view mode name maps both modes and falls back to unified`() {
        assertEquals(DiffViewMode.Unified, diffViewModeForName("Unified"))
        assertEquals(DiffViewMode.Split, diffViewModeForName("Split"))
        assertEquals(DiffViewMode.Unified, diffViewModeForName("NOPE"))
        assertEquals(DiffViewMode.Unified, diffViewModeForName(""))
    }

    @Test
    fun `enum names round-trip through the saved string`() {
        for (filter in ToolFilter.entries) {
            assertEquals(filter, toolFilterForName(filter.name))
        }
        for (mode in DiffViewMode.entries) {
            assertEquals(mode, diffViewModeForName(mode.name))
        }
    }
}
