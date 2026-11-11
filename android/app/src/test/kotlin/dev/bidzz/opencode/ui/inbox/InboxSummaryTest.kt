package dev.bidzz.opencode.ui.inbox

import dev.bidzz.opencode.core.api.dto.InboxItem
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InboxSummaryTest {

    private fun item(type: String, payload: JsonObject = JsonObject(emptyMap())) =
        InboxItem(id = "msg_1", type = type, payload = payload)

    @Test
    fun `user item shows its prompt text`() {
        val payload = buildJsonObject { put("text", "Fix the scroll bug") }
        assertEquals("Fix the scroll bug", item("user", payload).summaryText())
    }

    @Test
    fun `user item counts attached files`() {
        val payload = buildJsonObject {
            put("text", "Review this")
            putJsonArray("files") {
                addJsonObject { put("mime", "text/plain") }
                addJsonObject { put("mime", "image/png") }
            }
        }
        val inbox = item("user", payload)
        assertEquals("Review this", inbox.summaryText())
        assertEquals(2, inbox.fileCount())
    }

    @Test
    fun `synthetic item prefers text over description`() {
        val withText = buildJsonObject {
            put("text", "Reminder")
            put("description", "Check the build")
        }
        assertEquals("Reminder", item("synthetic", withText).summaryText())
        val descriptionOnly = buildJsonObject { put("description", "Check the build") }
        assertEquals("Check the build", item("synthetic", descriptionOnly).summaryText())
    }

    @Test
    fun `compaction item has no summary`() {
        assertNull(item("compaction").summaryText())
        assertEquals(0, item("compaction").fileCount())
    }

    @Test
    fun `move item shows project id, falling back to directory`() {
        val withProject = buildJsonObject { put("projectID", "81d8a9e2") }
        assertEquals("81d8a9e2", item("move", withProject).summaryText())
        val withDirectory = buildJsonObject {
            putJsonObject("location") { put("directory", "/storage/emulated/0/Projects/other") }
        }
        assertEquals(
            "/storage/emulated/0/Projects/other",
            item("move", withDirectory).summaryText(),
        )
    }

    @Test
    fun `unknown variant has no summary`() {
        val payload = buildJsonObject { put("text", "Hello") }
        assertNull(item("future-kind", payload).summaryText())
    }
}
