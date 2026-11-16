package dev.bidzz.opencode

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `saved codes map back to their language`() {
        assertEquals(AppLanguage.English, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.Indonesian, AppLanguage.fromCode("id"))
        assertEquals(AppLanguage.System, AppLanguage.fromCode("system"))
    }

    @Test
    fun `an unknown or missing code follows the system`() {
        assertEquals(AppLanguage.System, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.System, AppLanguage.fromCode("fr"))
    }

    @Test
    fun `only the system option has no pinned tag`() {
        assertNull(AppLanguage.System.tag)
        assertEquals("id", AppLanguage.Indonesian.tag)
    }

    @Test
    fun `m-502 Indonesian resolves to the id locale never legacy in`() {
        // The stored pin is "id" and wrap() must resolve the same tag —
        // a legacy "in" locale would look up the wrong resources.
        assertEquals("id", localeFor(AppLanguage.Indonesian)?.language)
        assertNotEquals("in", localeFor(AppLanguage.Indonesian)?.language)
        assertEquals("en", localeFor(AppLanguage.English)?.language)
    }

    @Test
    fun `m-502 System has no pinned locale`() {
        assertNull(localeFor(AppLanguage.System))
    }

    @Test
    fun `m-502 wrap resolves its locales through localeFor`() {
        // The tested helper is the path wrap() actually takes — a
        // second locale computation in wrap() would silently untest it.
        val src = File("src/main/kotlin/dev/bidzz/opencode/AppLanguage.kt").readText()
        assertTrue(src.contains("localeFor(language)?.let"))
    }
}
