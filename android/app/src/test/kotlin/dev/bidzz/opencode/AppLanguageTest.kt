package dev.bidzz.opencode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
