package dev.bidzz.opencode.ui.onboarding

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Seeding reports WHY it failed instead of returning null (audit-100
 * 64-F6): the onboarding button used to stay stuck on "Create sample"
 * with no explanation.
 */
class SampleProjectTest {

    @Test
    fun `seeding into an empty directory creates the three files`() {
        val dir = File.createTempFile("sample-ok", "").apply { delete(); mkdirs() }
        try {
            val result = SampleProject.seed(dir)
            assertTrue(result is SampleProject.SeedResult.Created)
            assertEquals(dir, (result as SampleProject.SeedResult.Created).dir)
            assertTrue(File(dir, "README.md").exists())
            assertTrue(File(dir, "main.py").exists())
            assertTrue(File(dir, "TODO.md").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `seeding into an existing file fails with a reason`() {
        val file = File.createTempFile("sample-file", ".txt")
        try {
            val result = SampleProject.seed(file)
            assertTrue(result is SampleProject.SeedResult.Failed)
            val reason = (result as SampleProject.SeedResult.Failed).reason
            assertTrue(reason.isNotBlank())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `seeding never overwrites an existing README`() {
        val dir = File.createTempFile("sample-keep", "").apply { delete(); mkdirs() }
        try {
            File(dir, "README.md").writeText("mine")
            val result = SampleProject.seed(dir)
            assertTrue(result is SampleProject.SeedResult.Created)
            assertEquals("mine", File(dir, "README.md").readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}
