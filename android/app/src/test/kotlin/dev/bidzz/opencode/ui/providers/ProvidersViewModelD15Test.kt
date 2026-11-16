package dev.bidzz.opencode.ui.providers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D15 (m-496): the stale-key warning is true exactly while the
 * credential a replace failed to remove is still in the freshly listed
 * credentials. The decision is pure ([staleKeyStillRemains]); the view
 * model recomputes it in [ProvidersViewModel.reload] on every refresh.
 */
class ProvidersViewModelD15Test {

    @Test
    fun `m-496 the warning stays while the stale credential is still listed`() {
        assertTrue(staleKeyStillRemains("cred-1", listOf("cred-1", "cred-2")))
    }

    @Test
    fun `m-496 the warning clears once the stale credential is gone`() {
        assertFalse(staleKeyStillRemains("cred-1", listOf("cred-2", "cred-3")))
        assertFalse(staleKeyStillRemains("cred-1", emptyList()))
    }

    @Test
    fun `m-496 no stale credential means no warning`() {
        assertFalse(staleKeyStillRemains(null, listOf("cred-1")))
        assertFalse(staleKeyStillRemains(null, emptyList()))
    }

    @Test
    fun `m-496 reload recomputes the flag from the fresh list`() {
        val src = File(
            "src/main/kotlin/dev/bidzz/opencode/ui/providers/ProvidersViewModel.kt",
        ).readText()
        assertTrue(src.contains("staleKeyStillRemains(staleCredentialId, result.value.map { it.id })"))
        assertTrue(src.contains("staleCredentialId = if (stale != null) credentialID else null"))
    }
}
