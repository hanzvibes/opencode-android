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
    }

    @Test
    fun `back from a top level screen goes to the conversation`() {
        listOf(Screen.Files, Screen.Sessions, Screen.Projects, Screen.Settings, Screen.Diagnostics)
            .forEach { assertEquals(Screen.Chat, it.parent()) }
    }
}
