package dev.bidzz.opencode.ui.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStoreTest {

    @Test
    fun `M-015 a workspace with only the seeded agents file counts as first run`() {
        assertTrue(workspaceLooksFirstRun(listOf("AGENTS.md")))
    }

    @Test
    fun `M-015 an empty workspace counts as first run`() {
        assertTrue(workspaceLooksFirstRun(emptyList()))
        assertTrue(workspaceLooksFirstRun(null))
    }

    @Test
    fun `M-015 a workspace with any other file is not a first run`() {
        assertFalse(workspaceLooksFirstRun(listOf("AGENTS.md", "README.md")))
        assertFalse(workspaceLooksFirstRun(listOf("main.kt")))
    }
}
