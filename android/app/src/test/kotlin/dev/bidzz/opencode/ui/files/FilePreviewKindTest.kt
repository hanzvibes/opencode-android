package dev.bidzz.opencode.ui.files

import org.junit.Assert.assertEquals
import org.junit.Test

class FilePreviewKindTest {

    @Test
    fun `image extensions preview as IMAGE`() {
        listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif").forEach { ext ->
            assertEquals(
                FilePreviewKind.IMAGE,
                previewKindForPath("photo.$ext"),
            )
        }
    }

    @Test
    fun `m-722 avif previews only on API 34 and up`() {
        assertEquals(FilePreviewKind.IMAGE, previewKindForPath("photo.avif", sdkInt = 34))
        assertEquals(FilePreviewKind.IMAGE, previewKindForPath("photo.avif", sdkInt = 35))
        assertEquals(FilePreviewKind.UNSUPPORTED, previewKindForPath("photo.avif", sdkInt = 33))
        assertEquals(FilePreviewKind.UNSUPPORTED, previewKindForPath("photo.avif", sdkInt = 29))
    }

    @Test
    fun `m-722 tiff and ico are unsupported, not text`() {
        listOf("scan.tif", "scan.tiff", "icon.ico").forEach { name ->
            assertEquals(FilePreviewKind.UNSUPPORTED, previewKindForPath(name))
        }
    }

    @Test
    fun `m-722 svg stays NONE because it is XML text`() {
        assertEquals(FilePreviewKind.NONE, previewKindForPath("logo.svg"))
    }

    @Test
    fun `pdf previews as PDF`() {
        assertEquals(FilePreviewKind.PDF, previewKindForPath("report.pdf"))
    }

    @Test
    fun `extension match is case-insensitive`() {
        assertEquals(FilePreviewKind.IMAGE, previewKindForPath("PHOTO.JPG"))
        assertEquals(FilePreviewKind.PDF, previewKindForPath("Report.PDF"))
    }

    @Test
    fun `code and text files do not preview`() {
        listOf("Main.kt", "notes.md", "data.json", "script.py", "notes.txt").forEach { name ->
            assertEquals(FilePreviewKind.NONE, previewKindForPath(name))
        }
    }

    @Test
    fun `missing extension does not preview`() {
        assertEquals(FilePreviewKind.NONE, previewKindForPath("README"))
        assertEquals(FilePreviewKind.NONE, previewKindForPath("src/Makefile"))
    }

    @Test
    fun `double extension uses the last one`() {
        assertEquals(FilePreviewKind.NONE, previewKindForPath("report.pdf.bak"))
        assertEquals(FilePreviewKind.PDF, previewKindForPath("archive.tar.pdf"))
    }

    @Test
    fun `unknown extension does not preview`() {
        assertEquals(FilePreviewKind.NONE, previewKindForPath("model.xyz"))
    }
}
