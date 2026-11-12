package dev.bidzz.opencode.ui.move

import dev.bidzz.opencode.core.api.dto.ProjectInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveSessionTest {

    @Test
    fun `display name prefers the project name`() {
        val project = ProjectInfo(
            id = "81d8a9e2",
            canonical = "/storage/emulated/0/Projects/demo",
            name = "demo",
        )
        assertEquals("demo", project.displayName())
    }

    @Test
    fun `display name falls back to the canonical path`() {
        val project = ProjectInfo(
            id = "81d8a9e2",
            canonical = "/storage/emulated/0/Projects/demo",
            name = null,
        )
        assertEquals("/storage/emulated/0/Projects/demo", project.displayName())
        val blankName = project.copy(name = "   ")
        assertEquals("/storage/emulated/0/Projects/demo", blankName.displayName())
    }
}
