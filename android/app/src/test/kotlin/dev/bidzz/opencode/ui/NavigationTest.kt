package dev.bidzz.opencode.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTest {

    @Test
    fun `back from a nested screen goes to the screen it was opened from`() {
        assertEquals(Screen.Files, Screen.FileView("a.kt").parent())
        assertEquals(Screen.Files, Screen.DiffList.parent())
        assertEquals(Screen.DiffList, Screen.Diff("a.kt").parent())
        assertEquals(Screen.Settings, Screen.Providers.parent())
        assertEquals(Screen.Settings, Screen.Plugins.parent())
    }

    @Test
    fun `back from a top level screen goes to the conversation`() {
        listOf(Screen.Files, Screen.Sessions, Screen.Projects, Screen.Settings, Screen.Diagnostics, Screen.About)
            .forEach { assertEquals(Screen.Chat, it.parent()) }
    }

    @Test
    fun `screen depth orders chat below its children`() {
        // Animation C: the screen-transition slide direction. A deeper
        // screen slides up on entry; going back slides down.
        assertEquals(0, Screen.Chat.depth())
        assertEquals(1, Screen.Sessions.depth())
        assertEquals(2, Screen.Files.depth())
        assertEquals(3, Screen.FileView("a.kt").depth())
        assertEquals(3, Screen.DiffList.depth())
        assertEquals(4, Screen.Diff("a.kt").depth())
        assertEquals(3, Screen.SessionDiff.depth())
    }

    @Test
    fun `screen depth orders settings below its children`() {
        assertEquals(2, Screen.Settings.depth())
        assertEquals(3, Screen.Providers.depth())
        assertEquals(3, Screen.McpServers.depth())
        assertEquals(3, Screen.Plugins.depth())
        assertEquals(3, Screen.About.depth())
    }

    @Test
    fun `forward navigation always goes to an equal or deeper screen`() {
        // Every parent() target is shallower than the screen it is
        // reached from, so back always reads as the reverse direction.
        listOf(
            Screen.FileView("a.kt") to Screen.Files,
            Screen.DiffList to Screen.Files,
            Screen.Diff("a.kt") to Screen.DiffList,
            Screen.Providers to Screen.Settings,
            Screen.McpServers to Screen.Settings,
            Screen.Plugins to Screen.Settings,
        ).forEach { (from, to) ->
            assertEquals(true, from.depth() > to.depth())
        }
    }
}
