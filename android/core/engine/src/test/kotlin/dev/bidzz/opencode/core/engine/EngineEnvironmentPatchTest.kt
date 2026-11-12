package dev.bidzz.opencode.core.engine

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Antigravity plugin patch, audit-100 Fase 3 (31-F2 patch timing + status,
 * 31-F11 atomic write + backup + verify).
 *
 * The old behaviour silently no-op'd on a fresh install (no plugin in the
 * cache yet, so `prepare()` patched nothing and nobody ever retried) and
 * overwrote plugin files in place with `writeText`, so a write cut short
 * mid-way left a permanently broken plugin the patch then ignored.
 */
class EngineEnvironmentPatchTest {

    private fun fixture(): EngineEnvironment {
        val root = Files.createTempDirectory("patch").toFile()
        val filesDir = File(root, "files").apply { mkdirs() }
        val cacheDir = File(root, "cache").apply { mkdirs() }
        val workspace = File(root, "workspace").apply { mkdirs() }
        return EngineEnvironment(filesDir, cacheDir, workspace)
    }

    private fun pluginFile(env: EngineEnvironment, name: String, text: String): File {
        val dir = File(env.cacheHome, "opencode/npm/opencode-antigravity-auth/dist").apply { mkdirs() }
        return File(dir, name).apply { writeText(text) }
    }

    private val requestUnpatched = """
        if (typeof input === "string" && input.includes("generativelanguage.googleapis.com")) {
          const match = input.match(/\/models\/([^:]+):(\w+)/);
        }
    """.trimIndent()

    private val v2Unpatched = """
        const url = input instanceof URL ? input.toString() : input;
    """.trimIndent()

    @Test
    fun `missing plugin cache reports NOT_FOUND instead of silently doing nothing`() {
        val report = fixture().patchAntigravityPlugin()
        assertEquals(AntigravityPatchOutcome.NOT_FOUND, report.outcome)
        assertTrue(report.patchedFiles.isEmpty())
    }

    @Test
    fun `fresh plugin is patched and the report names both files`() {
        val env = fixture()
        pluginFile(env, "request.js", requestUnpatched)
        pluginFile(env, "v2.js", v2Unpatched)

        val report = env.patchAntigravityPlugin()
        assertEquals(AntigravityPatchOutcome.APPLIED, report.outcome)
        assertEquals(listOf("request.js", "v2.js").sorted(), report.patchedFiles.sorted())
        // The new code landed in both files…
        val request = File(env.cacheHome, "opencode/npm/opencode-antigravity-auth/dist/request.js").readText()
        assertTrue(request.contains("input.url"))
        assertTrue(request.contains("_inUrl"))
        // …and the old code is gone.
        assertTrue(!request.contains("typeof input === \"string\" && input.includes"))
        val v2 = File(env.cacheHome, "opencode/npm/opencode-antigravity-auth/dist/v2.js").readText()
        assertTrue(!v2.contains("input instanceof URL ? input.toString() : input"))
    }

    @Test
    fun `re-running the patch on patched files reports ALREADY_PATCHED and rewrites nothing`() {
        val env = fixture()
        val request = pluginFile(env, "request.js", requestUnpatched)
        pluginFile(env, "v2.js", v2Unpatched)
        env.patchAntigravityPlugin()
        val bytesAfterFirst = request.readBytes()

        val report = env.patchAntigravityPlugin()
        assertEquals(AntigravityPatchOutcome.ALREADY_PATCHED, report.outcome)
        assertTrue(report.patchedFiles.isEmpty())
        assertTrue(request.readBytes().contentEquals(bytesAfterFirst))
    }

    @Test
    fun `a plugin file from an unknown version reports VERSION_MISMATCH`() {
        val env = fixture()
        pluginFile(env, "request.js", "// rewritten by plugin v9, no known markers here\n")
        val report = env.patchAntigravityPlugin()
        assertEquals(AntigravityPatchOutcome.VERSION_MISMATCH, report.outcome)
        assertTrue(report.detail!!.contains("request.js"))
    }

    @Test
    fun `a backup of the pre-patch bytes is kept beside each patched file`() {
        val env = fixture()
        val request = pluginFile(env, "request.js", requestUnpatched)
        pluginFile(env, "v2.js", v2Unpatched)
        env.patchAntigravityPlugin()
        val backup = File(request.parentFile, "request.js.bak")
        assertTrue(backup.isFile)
        assertEquals(requestUnpatched, backup.readText())
        assertTrue(File(request.parentFile, "v2.js.bak").isFile)
    }

    @Test
    fun `decidePluginPatch is pure: unpatched, patched and unknown texts`() {
        val patch = decidePluginPatch("request.js", requestUnpatched)
        assertTrue(patch is PluginPatchDecision.Patch)
        val patchedText = (patch as PluginPatchDecision.Patch).patchedText
        assertEquals(PluginPatchDecision.AlreadyPatched, decidePluginPatch("request.js", patchedText))
        assertEquals(PluginPatchDecision.VersionMismatch, decidePluginPatch("request.js", "nothing known here"))
        assertEquals(PluginPatchDecision.AlreadyPatched, decidePluginPatch("v2.js", (decidePluginPatch("v2.js", v2Unpatched) as PluginPatchDecision.Patch).patchedText))
        assertEquals(PluginPatchDecision.VersionMismatch, decidePluginPatch("other.js", requestUnpatched))
    }

    @Test
    fun `files outside the antigravity plugin are never touched`() {
        val env = fixture()
        val other = File(env.cacheHome, "opencode/npm/some-other-plugin/dist").apply { mkdirs() }
        val victim = File(other, "request.js").apply { writeText(requestUnpatched) }
        val report = env.patchAntigravityPlugin()
        assertEquals(AntigravityPatchOutcome.NOT_FOUND, report.outcome)
        assertEquals(requestUnpatched, victim.readText())
        assertTrue(!File(other, "request.js.bak").exists())
    }

    @Test
    fun `prepare stores the patch report for diagnostics`() {
        val env = fixture()
        pluginFile(env, "request.js", requestUnpatched)
        env.prepare()
        val report = env.lastPatchReport
        assertEquals(AntigravityPatchOutcome.APPLIED, report?.outcome)
        assertTrue(!report!!.restartRequired)
    }

    @Test
    fun `post-start patch marks restartRequired because the running engine loaded old code`() {
        val env = fixture()
        pluginFile(env, "request.js", requestUnpatched)
        val report = env.patchAntigravityPlugin(restartRequired = true)
        assertEquals(AntigravityPatchOutcome.APPLIED, report.outcome)
        assertTrue(report.restartRequired)
    }

    @Test
    fun `a half-patched file is not mistaken for patched on the next run`() {
        val env = fixture()
        // Only the guard replacement applied (simulates a write cut short after
        // the first replace, back when writes were in place).
        val half = requestUnpatched.replace(
            "typeof input === \"string\" && input.includes(\"generativelanguage.googleapis.com\")",
            "((typeof input === \"string\" ? input : (input && typeof input === \"object\" && \"url\" in input ? input.url : (input ? String(input) : \"\"))).includes(\"generativelanguage.googleapis.com\"))",
        )
        assertNotEquals(requestUnpatched, half)
        pluginFile(env, "request.js", half)
        val report = env.patchAntigravityPlugin()
        assertEquals(AntigravityPatchOutcome.APPLIED, report.outcome)
        val final = File(env.cacheHome, "opencode/npm/opencode-antigravity-auth/dist/request.js").readText()
        assertTrue(final.contains("_inUrl"))
    }
}
