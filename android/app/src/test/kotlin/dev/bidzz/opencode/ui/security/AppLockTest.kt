package dev.bidzz.opencode.ui.security

import androidx.biometric.BiometricManager
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLockTest {

    @Test
    fun `success maps to Available`() {
        assertEquals(
            LockAvailability.Available,
            lockAvailabilityFrom(BiometricManager.BIOMETRIC_SUCCESS),
        )
    }

    @Test
    fun `no hardware maps to NoHardware`() {
        assertEquals(
            LockAvailability.NoHardware,
            lockAvailabilityFrom(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE),
        )
    }

    @Test
    fun `hardware unavailable maps to HardwareUnavailable`() {
        assertEquals(
            LockAvailability.HardwareUnavailable,
            lockAvailabilityFrom(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE),
        )
    }

    @Test
    fun `nothing enrolled maps to NoneEnrolled`() {
        assertEquals(
            LockAvailability.NoneEnrolled,
            lockAvailabilityFrom(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED),
        )
    }

    @Test
    fun `security update required maps to SecurityUpdateRequired`() {
        assertEquals(
            LockAvailability.SecurityUpdateRequired,
            lockAvailabilityFrom(BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED),
        )
    }

    @Test
    fun `unknown code degrades to HardwareUnavailable`() {
        assertEquals(
            LockAvailability.HardwareUnavailable,
            lockAvailabilityFrom(-999),
        )
    }
}
