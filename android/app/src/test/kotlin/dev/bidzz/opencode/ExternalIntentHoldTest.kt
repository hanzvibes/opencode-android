package dev.bidzz.opencode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D14 (m-707): an external intent is held for the post-unlock
 * confirmation only when App Lock is enabled and not yet passed.
 */
class ExternalIntentHoldTest {

    @Test
    fun `m-707 a locked app holds the intent`() {
        assertTrue(shouldHoldExternalIntent(lockEnabled = true, unlocked = false))
    }

    @Test
    fun `m-707 an unlocked app executes at once`() {
        assertFalse(shouldHoldExternalIntent(lockEnabled = true, unlocked = true))
    }

    @Test
    fun `m-707 without App Lock nothing is ever held`() {
        assertFalse(shouldHoldExternalIntent(lockEnabled = false, unlocked = false))
        assertFalse(shouldHoldExternalIntent(lockEnabled = false, unlocked = true))
    }
}
