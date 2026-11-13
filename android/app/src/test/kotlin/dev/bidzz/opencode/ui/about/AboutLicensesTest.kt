package dev.bidzz.opencode.ui.about

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The About screen's license list must match the license texts actually
 * bundled in the APK (audit-100 Fase 6 batch 6l): the texts were bundled
 * but unreachable from the UI, and a list that names a file the APK does
 * not carry — or omits one it does — is a lie either way.
 *
 * Unit tests run with the module dir as the working directory, so the asset
 * files are readable here; the repo-root copy of the notices file is two
 * levels up.
 */
class AboutLicensesTest {

    @Test
    fun `license list matches the bundled asset files`() {
        val dir = File("src/main/assets/licenses")
        assertTrue("assets/licenses is missing", dir.isDirectory)
        val onDisk = dir.listFiles().orEmpty().map { it.name }.sorted()
        val listed = LICENSE_DOCS.map { it.assetPath.removePrefix("licenses/") }.sorted()
        assertEquals(
            "LICENSE_DOCS does not match the files in assets/licenses — " +
                "add the license to the list and the APK together, or neither",
            onDisk,
            listed,
        )
        assertTrue("THIRD_PARTY_NOTICES.md is not bundled", File("src/main/assets/THIRD_PARTY_NOTICES.md").isFile)
    }

    @Test
    fun `bundled notices copy matches the repo-root file`() {
        val root = File("../../THIRD_PARTY_NOTICES.md")
        val bundled = File("src/main/assets/THIRD_PARTY_NOTICES.md")
        assertTrue("repo-root THIRD_PARTY_NOTICES.md is missing", root.isFile)
        assertTrue("bundled THIRD_PARTY_NOTICES.md is missing", bundled.isFile)
        assertEquals(
            "bundled notices drifted from the repo-root file — edit the root file, then copy it over",
            root.readText(),
            bundled.readText(),
        )
    }
}
