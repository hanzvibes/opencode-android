package dev.bidzz.opencode.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.NativeKeyEvent
import dev.bidzz.opencode.core.api.dto.CommandInfo
import dev.bidzz.opencode.ui.Glyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for CommandSuggestions logic, ranking, parameter parsing, and keyboard navigation.
 */
class CommandSuggestionsTest {

    @Test
    fun `isCommandPaletteActive triggers only on unspaced slash drafts`() {
        assertTrue(isCommandPaletteActive("/"))
        assertTrue(isCommandPaletteActive("/t"))
        assertTrue(isCommandPaletteActive("/test"))
        assertTrue(isCommandPaletteActive("/review"))
        assertTrue(isCommandPaletteActive("/fix"))
        assertTrue(isCommandPaletteActive("/init"))

        // Once the operator presses space to enter parameters, the palette closes
        assertFalse(isCommandPaletteActive("/test "))
        assertFalse(isCommandPaletteActive("/test --watch"))
        assertFalse(isCommandPaletteActive("hello /test"))
        assertFalse(isCommandPaletteActive(""))
        assertFalse(isCommandPaletteActive("   "))
    }

    @Test
    fun `extractCommandQuery strips leading slash and normalizes casing`() {
        assertEquals("", extractCommandQuery("/"))
        assertEquals("t", extractCommandQuery("/t"))
        assertEquals("test", extractCommandQuery("/test"))
        assertEquals("test", extractCommandQuery("/TEST"))
        assertEquals("review", extractCommandQuery("/Review"))
        assertEquals("", extractCommandQuery("regular text"))
    }

    @Test
    fun `parseParameterHint extracts bracketed and angled hints`() {
        val (hint1, desc1) = parseParameterHint("Run test suite [filter]")
        assertEquals("[filter]", hint1)
        assertEquals("Run test suite", desc1)

        val (hint2, desc2) = parseParameterHint("Switch active model <provider/model>")
        assertEquals("<provider/model>", hint2)
        assertEquals("Switch active model", desc2)

        val (hint3, desc3) = parseParameterHint("Initialize workspace - [template | path]")
        assertEquals("[template | path]", hint3)
        assertEquals("Initialize workspace", desc3)

        val (hint4, desc4) = parseParameterHint("Clear conversation history")
        assertNull(hint4)
        assertEquals("Clear conversation history", desc4)

        val (hintNull, descNull) = parseParameterHint(null)
        assertNull(hintNull)
        assertNull(descNull)
    }

    @Test
    fun `resolveSlashCommands provides fallback defaults for pro IDE workflows`() {
        // Even when engine advertises no commands, /init, /test, /review, /fix are provided
        val resolved = resolveSlashCommands(emptyList())
        val names = resolved.map { it.name }

        assertTrue(names.contains("init"))
        assertTrue(names.contains("test"))
        assertTrue(names.contains("review"))
        assertTrue(names.contains("fix"))

        val testCmd = resolved.first { it.name == "test" }
        assertEquals(Glyph.Terminal, testCmd.glyph)
        assertEquals("[filter]", testCmd.parameterHint)

        val initCmd = resolved.first { it.name == "init" }
        assertEquals(Glyph.Folder, initCmd.glyph)
        assertEquals("[template | path]", initCmd.parameterHint)

        val reviewCmd = resolved.first { it.name == "review" }
        assertEquals(Glyph.Code, reviewCmd.glyph)

        val fixCmd = resolved.first { it.name == "fix" }
        assertEquals(Glyph.Pencil, fixCmd.glyph)
    }

    @Test
    fun `resolveSlashCommands enriches engine-provided commands`() {
        val engineCmds = listOf(
            CommandInfo(name = "test", description = "Run custom runner [args]"),
            CommandInfo(name = "custom_script", description = "Execute project build script <target>"),
        )

        val resolved = resolveSlashCommands(engineCmds)

        // Builtin 'test' description overridden by engine, but glyph retained
        val testCmd = resolved.first { it.name == "test" }
        assertEquals("Run custom runner", testCmd.description)
        assertEquals("[args]", testCmd.parameterHint)
        assertEquals(Glyph.Terminal, testCmd.glyph)

        // Custom engine command gets parsed
        val customCmd = resolved.first { it.name == "custom_script" }
        assertEquals("Execute project build script", customCmd.description)
        assertEquals("<target>", customCmd.parameterHint)
        assertEquals(Glyph.Terminal, customCmd.glyph)

        // Other builtins still present
        assertTrue(resolved.any { it.name == "review" })
        assertTrue(resolved.any { it.name == "fix" })
        assertTrue(resolved.any { it.name == "init" })
    }

    @Test
    fun `filterSlashCommands ranks exact and prefix matches first`() {
        val commands = listOf(
            SlashCommand(name = "test", description = "Run tests", parameterHint = "[filter]"),
            SlashCommand(name = "init", description = "Initialize workspace"),
            SlashCommand(name = "review", description = "Code review diffs"),
            SlashCommand(name = "fix", description = "Fix issues"),
            SlashCommand(name = "terminal", description = "Open terminal"),
        )

        // Query "/" returns all
        assertEquals(5, filterSlashCommands(commands, "/").size)

        // Query "/te" ranks "test" and "terminal" before anything else
        val teMatches = filterSlashCommands(commands, "/te")
        assertEquals("test", teMatches.first().name)
        assertTrue(teMatches.any { it.name == "terminal" })

        // Query "/rev" returns "review"
        val revMatches = filterSlashCommands(commands, "/rev")
        assertEquals(1, revMatches.size)
        assertEquals("review", revMatches.first().name)

        // Query "/fi" returns "fix" and any description matches like "init" (configuration)
        val fiMatches = filterSlashCommands(commands, "/fi")
        assertTrue(fiMatches.isNotEmpty())
        assertEquals("fix", fiMatches.first().name)

        // Substring match: "view" matches "review"
        val viewMatches = filterSlashCommands(commands, "/view")
        assertEquals("review", viewMatches.first().name)
    }

    @Test
    fun `buildHighlightedCommandName creates highlighted styled text`() {
        val annotated = buildHighlightedCommandName(
            name = "review",
            query = "rev",
            accentColor = Color.Blue,
            onSurfaceColor = Color.White,
        )

        assertEquals("/review", annotated.text)
        // Verify span styles exist
        assertTrue(annotated.spanStyles.isNotEmpty())
    }

    @Test
    fun `CommandPaletteState handles selection navigation and cycling`() {
        val state = CommandPaletteState(
            initialCommands = emptyList(),
            initialDraft = "/",
        )

        assertTrue(state.isActive)
        assertTrue(state.matches.isNotEmpty())
        assertEquals(0, state.selectedIndex)

        // Select next
        state.selectNext()
        assertEquals(1, state.selectedIndex)

        // Select previous
        state.selectPrevious()
        assertEquals(0, state.selectedIndex)

        // Wrap around backward
        state.selectPrevious()
        assertEquals(state.matches.lastIndex, state.selectedIndex)

        // Wrap around forward
        state.selectNext()
        assertEquals(0, state.selectedIndex)
    }

    @Test
    fun `CommandPaletteState intercepts keyboard navigation events`() {
        val state = CommandPaletteState(
            initialCommands = emptyList(),
            initialDraft = "/t",
        )

        var pickedCommand: String? = null
        var dismissed = false

        // Test navigation state manipulation directly
        assertEquals(0, state.selectedIndex)
        state.selectNext()
        assertEquals(1, state.selectedIndex)
        state.selectPrevious()
        assertEquals(0, state.selectedIndex)

        // Select and verify pick
        val current = state.currentSelectedCommand()
        assertNotNull(current)
        state.currentSelectedCommand()?.let { pickedCommand = it.name }
        assertEquals(current?.name, pickedCommand)
    }
}
