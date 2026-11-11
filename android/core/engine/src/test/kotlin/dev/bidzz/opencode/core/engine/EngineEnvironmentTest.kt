package dev.bidzz.opencode.core.engine

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the Node.js runtime wiring (fix list items 7-10, 16): the tool map
 * must list `node`, and the child-process environment must carry the library
 * path, the bundled CA bundle, and a shell that exists on the device.
 */
class EngineEnvironmentTest {

    private class Fixture(root: File) {
        val environment: EngineEnvironment
        val toolBinDir: File
        val nativeLibDir: File

        init {
            val filesDir = File(root, "files").apply { mkdirs() }
            val cacheDir = File(root, "cache").apply { mkdirs() }
            val workspace = File(root, "workspace").apply { mkdirs() }
            toolBinDir = File(root, "tools-bin").apply { mkdirs() }
            nativeLibDir = File(root, "lib").apply { mkdirs() }
            environment = EngineEnvironment(filesDir, cacheDir, workspace)
        }
    }

    private fun fixture(): Fixture = Fixture(Files.createTempDirectory("env").toFile())

    @Test
    fun toolLibrariesContainNode() {
        assertEquals("node", EngineBinary.TOOL_LIBRARIES["libnode.so"])
        assertEquals("rg", EngineBinary.TOOL_LIBRARIES["librg.so"])
        assertEquals("git", EngineBinary.TOOL_LIBRARIES["libgit.so"])
    }

    @Test
    fun variablesSetsNodeEnv() {
        val fixture = fixture()
        val vars = fixture.environment.variables(fixture.toolBinDir, fixture.nativeLibDir)

        assertEquals(fixture.nativeLibDir.absolutePath, vars["LD_LIBRARY_PATH"])
        assertEquals(
            File(fixture.environment.nodeDir, "etc/tls/cert.pem").absolutePath,
            vars["SSL_CERT_FILE"],
        )
        assertEquals("/system/bin/sh", vars["SHELL"])
        assertTrue(
            "PATH must start with the tool bin dir",
            vars["PATH"]!!.startsWith(fixture.toolBinDir.absolutePath + ":"),
        )
    }

    @Test
    fun variablesWithoutNativeLibOmitsLdLibraryPath() {
        val fixture = fixture()
        val vars = fixture.environment.variables(fixture.toolBinDir)

        assertFalse("no native lib dir, no LD_LIBRARY_PATH", vars.containsKey("LD_LIBRARY_PATH"))
        assertEquals("/system/bin/sh", vars["SHELL"])
    }

    @Test
    fun nodePathsLiveUnderEngineRoot() {
        val fixture = fixture()
        val environment = fixture.environment

        assertEquals(File(File(environment.root, "node"), "lib/node_modules/npm/bin/npm-cli.js"), environment.npmCliJs)
        assertEquals(File(environment.nodeDir, "etc/tls/cert.pem"), environment.nodeCertFile)
    }
}
