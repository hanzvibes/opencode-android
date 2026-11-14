package dev.bidzz.opencode.ui.files

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class FilePreviewPathTest {

    @Test
    fun `M-022 a workspace-relative path resolves under the workspace root`() {
        assertEquals(
            File("/data/project", "images/photo.png"),
            resolveWorkspaceFile("/data/project", "images/photo.png"),
        )
    }

    @Test
    fun `M-022 an absolute path passes through untouched`() {
        assertEquals(
            File("/elsewhere/report.pdf"),
            resolveWorkspaceFile("/data/project", "/elsewhere/report.pdf"),
        )
    }

    @Test
    fun `M-022 a bare filename resolves under the workspace root`() {
        assertEquals(
            File("/data/project", "photo.png"),
            resolveWorkspaceFile("/data/project", "photo.png"),
        )
    }
}
