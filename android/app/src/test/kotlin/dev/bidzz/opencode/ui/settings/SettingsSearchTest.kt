package dev.bidzz.opencode.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings search field filters rows by title, subtitle or value.
 * A blank query matches everything, so the screen is unchanged before
 * the user starts typing — the filter must never hide a row by accident.
 */
class SettingsSearchTest {

    private val portRow = SettingsSearchEntry(
        title = "Engine port",
        subtitle = "http://127.0.0.1:4096",
        value = "4096",
    )
    private val languageRow = SettingsSearchEntry(
        title = "App language",
        value = "System",
    )

    @Test
    fun `a blank query matches every row`() {
        assertTrue(matchesSettingsQuery("", portRow))
        assertTrue(matchesSettingsQuery("   ", portRow))
        assertEquals(
            listOf(portRow, languageRow),
            filterSettingsEntries("", listOf(portRow, languageRow)),
        )
    }

    @Test
    fun `the title matches case-insensitively`() {
        assertTrue(matchesSettingsQuery("ENGINE", portRow))
        assertTrue(matchesSettingsQuery("engine", portRow))
        assertTrue(matchesSettingsQuery(" port ", portRow))
    }

    @Test
    fun `the subtitle and the value match too`() {
        assertTrue(matchesSettingsQuery("127.0.0.1", portRow))
        assertTrue(matchesSettingsQuery("4096", portRow))
        assertTrue(matchesSettingsQuery("system", languageRow))
    }

    @Test
    fun `a query that matches nothing hides the row`() {
        assertFalse(matchesSettingsQuery("theme", portRow))
        assertFalse(matchesSettingsQuery("theme", languageRow))
        assertTrue(
            filterSettingsEntries("theme", listOf(portRow, languageRow)).isEmpty(),
        )
    }

    @Test
    fun `filtering keeps order and only the matching rows`() {
        assertEquals(
            listOf(languageRow),
            filterSettingsEntries("language", listOf(portRow, languageRow)),
        )
    }

    @Test
    fun `a null subtitle or value never matches`() {
        val titleOnly = SettingsSearchEntry(title = "About")
        assertFalse(matchesSettingsQuery("missing", titleOnly))
        assertTrue(matchesSettingsQuery("about", titleOnly))
    }
}
