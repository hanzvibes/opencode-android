package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPhotoTest {

    @Test
    fun `photo name is IMG_yyyymmdd_hhmmss_jpg`() {
        val name = cameraPhotoFileName(1_762_886_400_000L)
        assertTrue(name.matches(Regex("IMG_\\d{8}_\\d{6}\\.jpg")))
    }
}
