package dev.bidzz.opencode.ui.export

import dev.bidzz.opencode.core.api.SessionExport
import dev.bidzz.opencode.core.api.dto.SessionTransferData
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportSummaryTest {

    private fun export(title: String? = "Demo", id: String = "ses_1", messages: Int = 2): SessionExport {
        val info = buildJsonObject {
            put("id", id)
            if (title != null) put("title", title)
        }
        val data = SessionTransferData(
            info = info,
            messages = List(messages) { JsonObject(emptyMap()) },
        )
        return SessionExport(data = data, rawJson = "{}")
    }

    @Test
    fun `display title prefers the session title`() {
        assertEquals("Demo", export(title = "Demo").displayTitle())
    }

    @Test
    fun `display title falls back to the session id`() {
        assertEquals("ses_1", export(title = null).displayTitle())
        assertEquals("ses_1", export(title = "   ").displayTitle())
    }

    @Test
    fun `display title is null without title or id`() {
        val data = SessionTransferData(info = JsonObject(emptyMap()), messages = emptyList())
        assertNull(SessionExport(data = data, rawJson = "{}").displayTitle())
    }

    @Test
    fun `message count counts the exported messages`() {
        assertEquals(2, export(messages = 2).messageCount())
        assertEquals(0, export(messages = 0).messageCount())
    }
}
