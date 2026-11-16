package dev.bidzz.opencode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D8 (m-267): the notification prompt may only fire once
 * onboarding is out of the way. The gate is pure
 * ([mayAskNotificationPermission]), so its truth table is tested
 * directly; the Activity path and the system launcher cannot be.
 */
class NotificationGateD8Test {

    @Test
    fun `m-267 no prompt while onboarding is still to come`() {
        assertFalse(mayAskNotificationPermission(onboardingCompleted = false, onboardingWillShow = true))
    }

    @Test
    fun `m-267 prompt once onboarding is completed`() {
        assertTrue(mayAskNotificationPermission(onboardingCompleted = true, onboardingWillShow = false))
        assertTrue(mayAskNotificationPermission(onboardingCompleted = true, onboardingWillShow = true))
    }

    @Test
    fun `m-267 prompt when onboarding will never show`() {
        // A real workspace from before onboarding existed: the flow never
        // appears, so waiting for it would block the prompt forever.
        assertTrue(mayAskNotificationPermission(onboardingCompleted = false, onboardingWillShow = false))
    }
}
