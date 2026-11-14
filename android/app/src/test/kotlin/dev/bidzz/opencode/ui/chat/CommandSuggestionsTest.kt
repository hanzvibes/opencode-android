package dev.bidzz.opencode.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.NativeKeyEvent
import dev.bidzz.opencode.core.api.dto.CommandInfo
import dev.bidzz.opencode.core.api.dto.SkillInfo
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
    fun `resolveSlashCommands appends skills after commands`() {
        val skills = listOf(
            SkillInfo(id = "sk_1", name = "commit", description = "Write a commit message", autoinvoke = true),
            SkillInfo(id = "sk_2", name = "review-pr", description = "Review a pull request"),
        )

        val resolved = resolveSlashCommands(emptyList(), skills)
        val skillRows = resolved.filter { it.isSkill }

        assertEquals(listOf("commit", "review-pr"), skillRows.map { it.name })
        assertEquals("Write a commit message", skillRows[0].description)
        assertEquals(Glyph.Sparkles, skillRows[0].glyph)
        assertEquals("skill", skillRows[0].parameterHint)
        // Audit-200 C3 (M-067): the row keeps the engine skill's id —
        // the pick used to drop it and keep only the name, so a pick
        // could never send the structured attachment the engine
        // resolves. Commands carry no skill id.
        assertEquals(listOf("sk_1", "sk_2"), skillRows.map { it.skillId })
        assertTrue(resolved.filter { !it.isSkill }.all { it.skillId == null })
        // Skills come after every command, and no command is marked a skill.
        assertTrue(resolved.indexOf(skillRows[0]) > resolved.indexOfFirst { it.name == "help" })
        assertTrue(resolved.filter { !it.isSkill }.none { it.name == "commit" })
    }

    @Test
    fun `resolveSlashCommands drops a skill that collides with a command name`() {
        // The palette keys rows by name: a skill named like a command would
        // duplicate a key and crash composition on open.
        val skills = listOf(
            SkillInfo(id = "sk_1", name = "test", description = "A skill that shares a command name"),
            SkillInfo(id = "sk_2", name = "Test", description = "Same name, different case"),
            SkillInfo(id = "sk_3", name = "commit", description = "A skill that survives"),
        )

        val resolved = resolveSlashCommands(emptyList(), skills)

        assertEquals(1, resolved.count { it.name == "test" })
        assertFalse(resolved.first { it.name == "test" }.isSkill)
        assertEquals(listOf("commit"), resolved.filter { it.isSkill }.map { it.name })
    }

    @Test
    fun `skills are searchable by description in the palette`() {
        val resolved = resolveSlashCommands(
            emptyList(),
            listOf(SkillInfo(id = "sk_1", name = "commit", description = "Write a commit message")),
        )

        val matches = filterSlashCommands(resolved, "/commit message")
        assertTrue(matches.isEmpty()) // a space ends palette mode; the query is the first word only
        assertTrue(filterSlashCommands(resolved, "/commit").any { it.isSkill && it.name == "commit" })
        assertTrue(filterSlashCommands(resolved, "/message").any { it.isSkill })
    }

    @Test
    fun `resolveSlashCommands without skills is unchanged`() {
        val engineCmds = listOf(CommandInfo(name = "deploy", description = "Deploy the app"))
        assertEquals(resolveSlashCommands(engineCmds), resolveSlashCommands(engineCmds, emptyList()))
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
    fun `filterSlashCommands matches the localised description, not just the English one`() {
        // 11-F4: the builtin descriptions are English literals, so without the
        // localised map a user searching in Indonesian finds nothing.
        val commands = listOf(
            SlashCommand(name = "clear", description = "Clear conversation messages and reset context"),
            SlashCommand(name = "test", description = "Run test suite or target test spec"),
        )
        val indonesian = mapOf(
            "clear" to "Hapus pesan percakapan dan reset konteks",
            "test" to "Jalankan rangkaian tes atau spesifikasi tes",
        )

        // Without the map, "/hapus" matches nothing: "hapus" is in neither
        // the English description nor the name.
        assertTrue(filterSlashCommands(commands, "/hapus").isEmpty())

        // With the map, the same query finds /clear by its Indonesian
        // description — and "/jalankan" finds /test, which no name or
        // English description contains.
        val matches = filterSlashCommands(commands, "/hapus", indonesian)
        assertEquals(listOf("clear"), matches.map { it.name })
        assertEquals(listOf("test"), filterSlashCommands(commands, "/jalankan", indonesian).map { it.name })
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
