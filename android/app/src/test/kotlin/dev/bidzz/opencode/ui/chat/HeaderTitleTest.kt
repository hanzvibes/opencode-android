package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The project name shown in the chat header.
 *
 * The header replaced four navigation labels with the folder the agent works in, so
 * this is the only text telling the user which project is open — it has to be right
 * for the paths that actually occur, including the annoying ones.
 */
class HeaderTitleTest {

    @Test
    fun `the last segment names the project`() {
        assertEquals("demo", headerTitle("/storage/emulated/0/Projects/demo"))
    }

    @Test
    fun `a trailing slash does not empty the name`() {
        // The classic mistake: trimming after the split instead of before it yields "",
        // and the header goes blank exactly when the user picked a shared-storage folder.
        assertEquals("demo", headerTitle("/storage/emulated/0/Projects/demo/"))
        assertEquals("demo", headerTitle("/storage/emulated/0/Projects/demo///"))
    }

    @Test
    fun `a folder with spaces keeps its whole name`() {
        // Verified path shape: this project was tested against a spaced directory, and
        // a name cut at the space would look like a truncation bug.
        assertEquals("space test", headerTitle("/storage/emulated/0/Projects/space test"))
    }

    @Test
    fun `the internal workspace is named after its folder`() {
        assertEquals("workspace", headerTitle("/data/user/0/dev.bidzz.opencode/files/workspace"))
    }

    @Test
    fun `nothing usable falls back to the product name`() {
        assertEquals("OpenCode", headerTitle(null))
        assertEquals("OpenCode", headerTitle(""))
        assertEquals("OpenCode", headerTitle("   "))
        assertEquals("OpenCode", headerTitle("/"))
        assertEquals("OpenCode", headerTitle("//"))
    }

    @Test
    fun `a bare folder name with no path is still a name`() {
        assertEquals("myapp", headerTitle("myapp"))
    }

    // Audit-200 D16 (m-199): same folder name in two places -----------

    @Test
    fun `m-199 a colliding name shows its parent segment`() {
        val known = setOf("/storage/emulated/0/Projects/client-a/app", "/storage/emulated/0/Projects/client-b/app")
        assertEquals("client-a/app", headerTitle("/storage/emulated/0/Projects/client-a/app", known))
        assertEquals("client-b/app", headerTitle("/storage/emulated/0/Projects/client-b/app", known))
    }

    @Test
    fun `m-199 a unique name stays a single segment even with known workspaces`() {
        val known = setOf("/storage/emulated/0/Projects/demo", "/storage/emulated/0/Projects/other")
        assertEquals("demo", headerTitle("/storage/emulated/0/Projects/demo", known))
    }

    @Test
    fun `m-199 the workspace itself does not count as a collision`() {
        // Only one workspace with this name, even if it is in the known set.
        val known = setOf("/storage/emulated/0/Projects/demo")
        assertEquals("demo", headerTitle("/storage/emulated/0/Projects/demo", known))
    }

    @Test
    fun `m-199 a colliding name with no parent falls back to the name`() {
        assertEquals("app", headerTitle("app", setOf("app", "other/app")))
    }
}
