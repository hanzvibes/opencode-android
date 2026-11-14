package dev.bidzz.opencode.ui.providers

import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.engine.AntigravityPatchOutcome
import dev.bidzz.opencode.core.engine.AntigravityPatchReport
import org.junit.Assert.assertEquals
import org.junit.Test

class PatchDisplayTest {

    @Test
    fun `M-206 a patch that landed after start offers a restart`() {
        val display = patchDisplayState(
            AntigravityPatchReport(
                outcome = AntigravityPatchOutcome.APPLIED,
                restartRequired = true,
            ),
            pollEnded = false,
        )

        assertEquals(
            PatchDisplay.WithRestart(R.string.providers_patch_applied_restart),
            display,
        )
    }

    @Test
    fun `M-209 a patch whose wait ran out offers a restart instead of promising`() {
        val display = patchDisplayState(
            AntigravityPatchReport(outcome = AntigravityPatchOutcome.NOT_FOUND),
            pollEnded = true,
        )

        assertEquals(
            PatchDisplay.WithRestart(R.string.providers_patch_not_found_stopped),
            display,
        )
    }

    @Test
    fun `M-209 while the poll still runs the wait promise holds`() {
        val display = patchDisplayState(
            AntigravityPatchReport(outcome = AntigravityPatchOutcome.NOT_FOUND),
            pollEnded = false,
        )

        assertEquals(
            PatchDisplay.Message(R.string.providers_patch_not_found),
            display,
        )
    }

    @Test
    fun `an already-patched plugin stays quiet`() {
        assertEquals(
            PatchDisplay.Quiet,
            patchDisplayState(
                AntigravityPatchReport(outcome = AntigravityPatchOutcome.ALREADY_PATCHED),
                pollEnded = false,
            ),
        )
    }
}
