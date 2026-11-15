package dev.bidzz.opencode.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun coldStart() = baselineProfileRule.collect(
        packageName = APP_PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun chatScrolling() = baselineProfileRule.collect(
        packageName = APP_PACKAGE_NAME,
    ) {
        startActivityAndWait()

        // Run generation on a device with a populated chat transcript, if available,
        // so these swipes exercise the actual scrolling conversation UI.
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val width = device.displayWidth
        val height = device.displayHeight
        repeat(5) {
            device.swipe(width / 2, height * 3 / 4, width / 2, height / 4, 12)
        }
    }

    private companion object {
        const val APP_PACKAGE_NAME = "dev.bidzz.opencode"
    }
}
