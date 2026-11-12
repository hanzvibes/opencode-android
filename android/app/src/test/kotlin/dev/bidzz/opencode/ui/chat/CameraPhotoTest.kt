package dev.bidzz.opencode.ui.chat

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPhotoTest {

    @Test
    fun `photo name is IMG_yyyymmdd_hhmmss_jpg`() {
        val name = cameraPhotoFileName(1_762_886_400_000L)
        assertTrue(name.matches(Regex("IMG_\\d{8}_\\d{6}\\.jpg")))
    }

    private fun tempDir(): File {
        val dir = Files.createTempDirectory("camera-photo-test").toFile()
        File(dir, CAMERA_PHOTOS_DIR).mkdirs()
        return File(dir, CAMERA_PHOTOS_DIR)
    }

    @Test
    fun `14-F7 a taken name gets a numbered suffix instead of overwriting`() {
        val dir = tempDir()
        try {
            File(dir, "IMG_20261113_013000.jpg").writeText("first")
            assertEquals(
                File(dir, "IMG_20261113_013000_1.jpg"),
                uniquePhotoTarget(dir, "IMG_20261113_013000.jpg"),
            )
            File(dir, "IMG_20261113_013000_1.jpg").writeText("second")
            assertEquals(
                File(dir, "IMG_20261113_013000_2.jpg"),
                uniquePhotoTarget(dir, "IMG_20261113_013000.jpg"),
            )
            assertEquals("first", File(dir, "IMG_20261113_013000.jpg").readText())
        } finally {
            dir.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `15-F7 a successful copy lands under the relative path with no temp left`() {
        val dir = tempDir()
        try {
            val bytes = "fake-jpeg-bytes".toByteArray()
            val saved = copyPhotoToWorkspace(ByteArrayInputStream(bytes), dir, "IMG_20261113_013000.jpg")

            assertEquals(".opencode-photos/IMG_20261113_013000.jpg", saved)
            assertEquals("fake-jpeg-bytes", File(dir, "IMG_20261113_013000.jpg").readText())
            assertFalse(dir.listFiles()?.any { it.name.endsWith(".tmp") } ?: false)
        } finally {
            dir.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `15-F7 a 0-byte copy reports failure and leaves no file`() {
        val dir = tempDir()
        try {
            val saved = copyPhotoToWorkspace(ByteArrayInputStream(ByteArray(0)), dir, "IMG_20261113_013000.jpg")

            assertNull(saved)
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally {
            dir.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `15-F7 a copy that fails mid-stream leaves no partial file`() {
        val dir = tempDir()
        try {
            val failing = object : InputStream() {
                override fun read(): Int = throw java.io.IOException("provider died")
            }
            val saved = copyPhotoToWorkspace(failing, dir, "IMG_20261113_013000.jpg")

            assertNull(saved)
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally {
            dir.parentFile.deleteRecursively()
        }
    }
}
