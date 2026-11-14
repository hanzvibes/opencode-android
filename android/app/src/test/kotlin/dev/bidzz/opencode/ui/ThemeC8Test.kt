package dev.bidzz.opencode.ui

import android.content.res.Configuration
import dev.bidzz.opencode.core.design.isNightModeActive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C8 (M-130): System mode follows the phone while the
 * app stays open. The decision itself is pure `uiMode` bit arithmetic
 * — the Compose wiring (ComponentCallbacks in OpenCodeApp, the
 * `systemDark` parameter of OpenCodeTheme) is covered by inspection,
 * because the repo has no Compose UI test harness.
 *
 * A bare `Configuration()` constructs fine on JVM unit tests: the
 * android.jar stub constructor is a no-op and `uiMode` is a plain
 * public field; only method bodies throw.
 */
class ThemeC8Test {

    @Test
    fun `M-130 night mode reads the night bit`() {
        val night = Configuration().apply { uiMode = Configuration.UI_MODE_NIGHT_YES }
        assertTrue(isNightModeActive(night))

        val day = Configuration().apply { uiMode = Configuration.UI_MODE_NIGHT_NO }
        assertFalse(isNightModeActive(day))
    }

    @Test
    fun `M-130 an undefined night mode is not night`() {
        val undefined = Configuration().apply { uiMode = Configuration.UI_MODE_NIGHT_UNDEFINED }
        assertFalse(isNightModeActive(undefined))
    }

    @Test
    fun `M-130 other uiMode bits do not affect the night decision`() {
        val nightWithType = Configuration().apply {
            uiMode = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES
        }
        assertTrue(isNightModeActive(nightWithType))

        val dayWithType = Configuration().apply {
            uiMode = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO
        }
        assertFalse(isNightModeActive(dayWithType))
    }
}
