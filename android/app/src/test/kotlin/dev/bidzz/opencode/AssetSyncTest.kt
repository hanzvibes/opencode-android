package dev.bidzz.opencode

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Audit-200 Fase A9-9 (DL-038): a runtime asset sync deletes only the
 * paths the bundle ships, never the whole tree. Pure JVM (`java.io.File`
 * only — the AssetManager listing is exercised through compile + the
 * fast-verify run), so the deletion rule is tested here against a temp
 * directory standing in for `files/engine/<runtime>/`.
 */
class AssetSyncTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("asset-sync-test").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun file(rel: String, text: String = "x"): File =
        File(root, rel).also {
            it.parentFile?.mkdirs()
            it.writeText(text)
        }

    @Test
    fun `DL-038 deleting owned paths removes bundled files and the marker but keeps user files`() {
        file("lib/node_modules/npm/bin/npm-cli.js")
        file("lib/node_modules/npm/package.json")
        file(".node-assets-version", "old-version")
        // What the user installed: a global package next to the bundled
        // npm, and a global bin shim — both must survive the sync.
        file("lib/node_modules/my-pkg/package.json")
        file("bin/my-cmd")

        val refused = deleteOwnedPaths(
            root,
            listOf(
                "lib/node_modules/npm/bin/npm-cli.js",
                "lib/node_modules/npm/package.json",
                ".node-assets-version",
            ),
        )

        assertTrue(refused.isEmpty())
        assertFalse(File(root, "lib/node_modules/npm/bin/npm-cli.js").exists())
        assertFalse(File(root, "lib/node_modules/npm/package.json").exists())
        assertFalse(File(root, ".node-assets-version").exists())
        assertTrue(File(root, "lib/node_modules/my-pkg/package.json").isFile)
        assertTrue(File(root, "bin/my-cmd").isFile)
    }

    @Test
    fun `DL-038 a non-empty directory at a bundled path is left in place and reported`() {
        file("lib/node_modules/npm/old-file.js")

        val refused = deleteOwnedPaths(root, listOf("lib/node_modules/npm"))

        assertEquals(listOf(File(root, "lib/node_modules/npm")), refused)
        assertTrue(File(root, "lib/node_modules/npm/old-file.js").isFile)
    }

    @Test
    fun `DL-038 deleting a missing path is a no-op`() {
        assertTrue(deleteOwnedPaths(root, listOf("no/such/file.js")).isEmpty())
    }
}
