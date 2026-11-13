package dev.bidzz.opencode.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The current screen survives a recreate through a short string key
 * (audit-100 69-F4): every screen — including the `data class` ones
 * carrying a String payload — round-trips, and an unknown key falls
 * back to Chat instead of crashing.
 */
class ScreenSaverTest {

    @Test
    fun `every object screen round-trips`() {
        val screens = listOf(
            Screen.Chat,
            Screen.Sessions,
            Screen.Projects,
            Screen.Files,
            Screen.DiffList,
            Screen.SessionDiff,
            Screen.SessionStats,
            Screen.UsageLimits,
            Screen.Settings,
            Screen.Providers,
            Screen.Diagnostics,
            Screen.McpServers,
            Screen.Plugins,
            Screen.Worktrees,
            Screen.EngineConfig,
            Screen.About,
        )
        for (screen in screens) {
            assertEquals(screen, screenFromSavedKey(screen.toSavedKey()))
        }
    }

    @Test
    fun `data class screens keep their payload`() {
        assertEquals(
            Screen.FileView("/a/b.kt"),
            screenFromSavedKey(Screen.FileView("/a/b.kt").toSavedKey()),
        )
        assertEquals(
            Screen.Diff("src/Main.kt"),
            screenFromSavedKey(Screen.Diff("src/Main.kt").toSavedKey()),
        )
        assertEquals(
            Screen.Inbox("ses-1"),
            screenFromSavedKey(Screen.Inbox("ses-1").toSavedKey()),
        )
        assertEquals(
            Screen.MoveSession("ses-2"),
            screenFromSavedKey(Screen.MoveSession("ses-2").toSavedKey()),
        )
        assertEquals(
            Screen.SessionExport("ses-3"),
            screenFromSavedKey(Screen.SessionExport("ses-3").toSavedKey()),
        )
        assertEquals(
            Screen.Instructions("ses-4"),
            screenFromSavedKey(Screen.Instructions("ses-4").toSavedKey()),
        )
        assertEquals(
            Screen.InAppBrowser("https://example.com/a?b=c"),
            screenFromSavedKey(Screen.InAppBrowser("https://example.com/a?b=c").toSavedKey()),
        )
    }

    @Test
    fun `an unknown key falls back to Chat`() {
        assertEquals(Screen.Chat, screenFromSavedKey("nope"))
        assertEquals(Screen.Chat, screenFromSavedKey("fileview:"))
        assertEquals(Screen.Chat, screenFromSavedKey(""))
    }
}
