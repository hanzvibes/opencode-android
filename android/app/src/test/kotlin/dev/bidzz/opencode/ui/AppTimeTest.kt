package dev.bidzz.opencode.ui

import dev.bidzz.opencode.ui.chat.formatClock
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Audit-200 D1 (m-021/m-435/m-554): date/time formatting follows the pinned
 * app language, and digit-only patterns stay ASCII under non-Latin locales.
 */
class AppTimeTest {

    private lateinit var previousDefault: Locale

    @Before
    fun saveDefaultLocale() {
        previousDefault = Locale.getDefault()
    }

    @After
    fun restoreDefaultLocale() {
        Locale.setDefault(previousDefault)
    }

    // 2026-08-15 12:00 UTC: the month stays August in every timezone, while
    // the abbreviation differs between Indonesian ("Agu") and English ("Aug").
    private val augustMillis = 1_786_795_200_000L

    @Test
    fun `formatAppDateTime uses Indonesian month abbreviations`() {
        val text = formatAppDateTime(augustMillis, Locale("id"))
        assertTrue("expected Indonesian 'Agu' in '$text'", text.contains("Agu"))
        assertTrue("expected year 2026 in '$text'", text.contains("2026"))
    }

    @Test
    fun `formatAppDateTime uses English month abbreviations`() {
        val text = formatAppDateTime(augustMillis, Locale.ENGLISH)
        assertTrue("expected English 'Aug' in '$text'", text.contains("Aug"))
    }

    @Test
    fun `formatClock stays ASCII digits under an Arabic default locale`() {
        Locale.setDefault(Locale("ar", "EG"))
        val clock = formatClock(1_791_800_400_000L)
        assertTrue("expected ASCII HH:mm, got '$clock'", clock.matches(Regex("\\d{2}:\\d{2}")))
    }

    @Test
    fun `formatAppDateShort uses the given locale`() {
        val id = formatAppDateShort(augustMillis, Locale("id"))
        val en = formatAppDateShort(augustMillis, Locale.ENGLISH)
        assertTrue("expected Indonesian 'Agu' in '$id'", id.contains("Agu"))
        assertTrue("expected English 'Aug' in '$en'", en.contains("Aug"))
        assertEquals(id.length, en.length)
    }
}
