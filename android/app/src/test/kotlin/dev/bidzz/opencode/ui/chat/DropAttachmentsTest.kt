package dev.bidzz.opencode.ui.chat

import android.content.ClipDescription
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DropAttachmentsTest {

    @Test
    fun `uri list is accepted`() {
        assertTrue(
            acceptsDropMimeTypes(setOf(ClipDescription.MIMETYPE_TEXT_URILIST)),
        )
    }

    @Test
    fun `workspace path mime is accepted`() {
        assertTrue(acceptsDropMimeTypes(setOf(MIME_WORKSPACE_PATH)))
    }

    @Test
    fun `plain text alone is not accepted`() {
        // Plain text from another app could be any sentence, not a path.
        assertFalse(
            acceptsDropMimeTypes(setOf(ClipDescription.MIMETYPE_TEXT_PLAIN)),
        )
    }

    @Test
    fun `image mime alone is not accepted`() {
        assertFalse(acceptsDropMimeTypes(setOf("image/png")))
    }

    @Test
    fun `empty mime set is not accepted`() {
        assertFalse(acceptsDropMimeTypes(emptySet()))
    }

    @Test
    fun `either acceptable mime in a set is enough`() {
        assertTrue(
            acceptsDropMimeTypes(
                setOf(ClipDescription.MIMETYPE_TEXT_PLAIN, MIME_WORKSPACE_PATH),
            ),
        )
    }
}
