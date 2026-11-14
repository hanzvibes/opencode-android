package dev.bidzz.opencode.ui

import dev.bidzz.opencode.core.engine.EngineCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationTest {

    @Test
    fun `back from a nested screen goes to the screen it was opened from`() {
        assertEquals(Screen.Files, Screen.FileView("a.kt").parent())
        assertEquals(Screen.Files, Screen.DiffList.parent())
        assertEquals(Screen.DiffList, Screen.Diff("a.kt").parent())
        assertEquals(Screen.Settings, Screen.Providers.parent())
        assertEquals(Screen.Settings, Screen.Plugins.parent())
        assertEquals(Screen.Settings, Screen.Worktrees.parent())
        assertEquals(Screen.Settings, Screen.EngineConfig.parent())
        assertEquals(Screen.Chat, Screen.InAppBrowser("https://example.com").parent())
    }

    @Test
    fun `back from a top level screen goes to the conversation`() {
        listOf(Screen.Files, Screen.Sessions, Screen.Projects, Screen.Settings, Screen.Diagnostics(), Screen.About())
            .forEach { assertEquals(Screen.Chat, it.parent()) }
    }

    @Test
    fun `M-109 back from About returns to Settings when opened there`() {
        assertEquals(Screen.Settings, Screen.About(Screen.Settings).parent())
    }

    @Test
    fun `M-109 back from About defaults to Chat when opened from the drawer`() {
        assertEquals(Screen.Chat, Screen.About().parent())
        assertEquals(Screen.Chat, Screen.About(Screen.Chat).parent())
    }

    @Test
    fun `M-157 back from Diagnostics returns to Settings when opened there`() {
        assertEquals(Screen.Settings, Screen.Diagnostics(Screen.Settings).parent())
    }

    @Test
    fun `M-157 back from Diagnostics defaults to Chat when opened from chat`() {
        assertEquals(Screen.Chat, Screen.Diagnostics().parent())
        assertEquals(Screen.Chat, Screen.Diagnostics(Screen.Chat).parent())
    }

    @Test
    fun `M-160 back from FileView returns to Chat when opened from a chat tool call`() {
        assertEquals(Screen.Chat, Screen.FileView("a.kt", Screen.Chat).parent())
    }

    @Test
    fun `M-160 back from FileView defaults to Files when opened from the file list`() {
        assertEquals(Screen.Files, Screen.FileView("a.kt").parent())
        assertEquals(Screen.Files, Screen.FileView("a.kt", Screen.Files).parent())
    }

    @Test
    fun `M-158 back from the browser returns to Artifacts when opened there`() {
        assertEquals(
            Screen.Artifacts,
            Screen.InAppBrowser("http://127.0.0.1:1/view", returnTo = Screen.Artifacts).parent(),
        )
    }

    @Test
    fun `M-158 back from the browser returns to WebSearch when opened there`() {
        assertEquals(
            Screen.WebSearch,
            Screen.InAppBrowser("https://example.com", returnTo = Screen.WebSearch).parent(),
        )
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
        assertEquals(3, Screen.InAppBrowser("https://example.com").depth())
    }

    @Test
    fun `screen depth orders settings below its children`() {
        assertEquals(2, Screen.Settings.depth())
        assertEquals(3, Screen.Providers.depth())
        assertEquals(3, Screen.McpServers.depth())
        assertEquals(3, Screen.Plugins.depth())
        assertEquals(3, Screen.Worktrees.depth())
        assertEquals(3, Screen.EngineConfig.depth())
        assertEquals(3, Screen.About().depth())
    }

    @Test
    fun `M-002 restored engine URL re-supplies the current engine credentials`() {
        val creds = EngineCredentials(baseUrl = "http://127.0.0.1:12345", password = "secret")
        assertEquals("opencode" to "secret", engineBasicAuthFor("http://127.0.0.1:12345/view/x", creds))
        assertEquals("opencode" to "secret", engineBasicAuthFor("http://127.0.0.1:12345", creds))
    }

    @Test
    fun `M-002 public links and missing credentials get no auth`() {
        val creds = EngineCredentials(baseUrl = "http://127.0.0.1:12345", password = "secret")
        assertNull(engineBasicAuthFor("https://example.com/page", creds))
        // A longer port that merely shares the prefix is a different host.
        assertNull(engineBasicAuthFor("http://127.0.0.1:123456/view", creds))
        assertNull(engineBasicAuthFor("http://127.0.0.1:12345/view", null))
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
            Screen.Worktrees to Screen.Settings,
            Screen.EngineConfig to Screen.Settings,
        ).forEach { (from, to) ->
            assertEquals(true, from.depth() > to.depth())
        }
    }
}
