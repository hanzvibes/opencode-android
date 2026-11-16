package dev.bidzz.opencode.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D15 (pengaturan / settings / onboarding): scanner guards for
 * the fixes whose behaviour lives in a store, a view model or a
 * composable that a JVM test cannot drive — the source must keep the
 * shape the fix gave it.
 */
class AuditD15Test {

    private fun read(path: String): String = File(path).readText()

    /** Body of the function whose signature starts at [marker], brace-matched (D3 pattern). */
    private fun funBody(src: String, marker: String): String {
        val start = src.indexOf(marker)
        require(start >= 0) { "missing $marker" }
        var i = src.indexOf('{', start)
        require(i >= 0) { "no body after $marker" }
        var depth = 0
        val bodyStart = i
        while (i < src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(bodyStart, i + 1)
                }
            }
            i++
        }
        error("unbalanced braces after $marker")
    }

    private fun count(src: String, needle: String): Int =
        src.split(needle).size - 1

    private val onboardingStore = "src/main/kotlin/dev/bidzz/opencode/ui/onboarding/OnboardingStore.kt"
    private val settingsScreen = "src/main/kotlin/dev/bidzz/opencode/ui/settings/SettingsScreen.kt"
    private val settingsSearch = "src/main/kotlin/dev/bidzz/opencode/ui/settings/SettingsSearch.kt"
    private val app = "src/main/kotlin/dev/bidzz/opencode/ui/OpenCodeApp.kt"
    private val chatScreen = "src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt"
    private val dialogs = "src/main/kotlin/dev/bidzz/opencode/ui/sessions/SessionActionDialogs.kt"
    private val publishSheet = "src/main/kotlin/dev/bidzz/opencode/ui/HatchPublishSheet.kt"
    private val bottomSheet = "src/main/kotlin/dev/bidzz/opencode/ui/DraggableBottomSheet.kt"
    private val palette = "../core/design/src/main/kotlin/dev/bidzz/opencode/core/design/Palette.kt"
    private val auraTest = "src/test/kotlin/dev/bidzz/opencode/ui/AuraThemeTest.kt"

    // ---- m-265: markCompleted is synchronous -------------------------

    @Test
    fun `m-265 markCompleted writes with commit not apply`() {
        val src = read(onboardingStore)
        val body = funBody(src, "fun markCompleted(context: Context)")
        assertTrue(body.contains(".commit()"))
        assertFalse(body.contains(".apply()"))
        // No async write anywhere in the store: a kill right after
        // Skip/Finish must not lose the flag.
        assertFalse(src.contains(".apply()"))
    }

    // ---- m-1086: onboarding is replayable ----------------------------

    @Test
    fun `m-1086 reset clears the flag with commit`() {
        val src = read(onboardingStore)
        val body = funBody(src, "fun reset(context: Context)")
        assertTrue(body.contains("remove(KEY_COMPLETED).commit()"))
    }

    @Test
    fun `m-1086 Settings has a replay row wired to the reset`() {
        val src = read(settingsScreen)
        assertTrue(src.contains("settings_replay_onboarding_title"))
        assertTrue(src.contains("onReplayOnboarding"))
        val appSrc = read(app)
        assertTrue(appSrc.contains("onReplayOnboarding = {"))
        assertTrue(appSrc.contains("OnboardingStore.reset(hostContext)"))
        assertEquals(1, count(appSrc, "showOnboarding = true"))
    }

    // ---- m-545: bilingual settings search ----------------------------

    @Test
    fun `m-545 the search matches the same strings in both locales`() {
        val searchSrc = read(settingsSearch)
        assertTrue(searchSrc.contains("fun matchesSettingsQueryBilingual("))
        val screenSrc = read(settingsScreen)
        // The screen resolves each row's strings in English and
        // Indonesian and matches the query against all three versions.
        assertTrue(screenSrc.contains("matchesSettingsQueryBilingual"))
        assertTrue(screenSrc.contains("LanguageStore.wrap(lockContext, AppLanguage.English)"))
        assertTrue(screenSrc.contains("LanguageStore.wrap(lockContext, AppLanguage.Indonesian)"))
        assertTrue(screenSrc.contains("bilingual("))
    }

    // ---- m-683: untitled fallback in the delete dialog ---------------

    @Test
    fun `m-683 the delete dialog falls back to Untitled for a blank title`() {
        val src = read(dialogs)
        val body = funBody(src, "fun DeleteSessionDialog(")
        assertTrue(body.contains("sessionTitle.ifBlank { stringResource(R.string.drawer_untitled) }"))
    }

    // ---- m-672: stale contrast KDoc ----------------------------------

    @Test
    fun `m-672 no live KDoc still cites the gone Aura page`() {
        val paletteSrc = read(palette)
        // Exactly one mention survives: the D5 historical note that the
        // page is gone. The per-colour ratios cite the real page #F9F9FA.
        assertEquals(1, count(paletteSrc, "#F6F7F8"))
        assertTrue(paletteSrc.contains("7.50:1 on the app page #F9F9FA"))
        assertTrue(paletteSrc.contains("4.54:1"))
        val testSrc = read(auraTest)
        // Two mentions survive in the test file, both as D5 history.
        assertEquals(2, count(testSrc, "#F6F7F8"))
        assertTrue(testSrc.contains("#F9F9FA app page"))
    }

    // ---- m-705: enable-lock errors surface in Settings ---------------

    @Test
    fun `m-705 a failed enable-lock shows its error under the row`() {
        val src = read(settingsScreen)
        assertTrue(src.contains("onError = { lockError = it }"))
        assertTrue(src.contains("lockError?.let { error ->"))
        assertFalse(src.contains("onError = {},"))
    }

    // ---- m-706: lock availability is re-read on resume ---------------

    @Test
    fun `m-706 lock availability is re-read when the screen resumes`() {
        val src = read(settingsScreen)
        assertTrue(src.contains("LifecycleEventObserver"))
        assertTrue(src.contains("Lifecycle.Event.ON_RESUME"))
        assertTrue(src.contains("lockAvailability = lockAvailability(lockContext)"))
        // The wrong assumption that justified the read-once must be gone.
        assertFalse(src.contains("does not change while the screen is open"))
    }

    // ---- m-692: publish sheet cannot be dismissed mid-publish --------

    @Test
    fun `m-692 the publish sheet is locked while publishing`() {
        val src = read(publishSheet)
        assertTrue(src.contains("dismissable: Boolean = state != HatchPublishState.PUBLISH"))
        assertTrue(src.contains("if (dismissable) {"))
        val sheetSrc = read(bottomSheet)
        assertTrue(sheetSrc.contains("dismissable: Boolean = true"))
        assertTrue(sheetSrc.contains("if (dismissing || !dismissable) return"))
    }

    // ---- m-992: chat drawer reaches the four tool screens ------------

    @Test
    fun `m-992 the drawer footer links Artifacts Jobs Generate and Web search`() {
        val src = read(chatScreen)
        assertTrue(src.contains("label = stringResource(R.string.artifact_title)"))
        assertTrue(src.contains("label = stringResource(R.string.job_title)"))
        assertTrue(src.contains("label = stringResource(R.string.generate_title)"))
        assertTrue(src.contains("label = stringResource(R.string.websearch_title)"))
        assertTrue(src.contains("onArtifacts = onArtifactsClick"))
        assertTrue(src.contains("onArtifacts = { closeThen(onArtifactsClick) }"))
        val appSrc = read(app)
        assertTrue(appSrc.contains("onArtifactsClick = { onNavigate(Screen.Artifacts(Screen.Chat)) }"))
        assertTrue(appSrc.contains("onJobsClick = { onNavigate(Screen.Jobs(Screen.Chat)) }"))
        assertTrue(appSrc.contains("onGenerateClick = { onNavigate(Screen.Generate(Screen.Chat)) }"))
        assertTrue(appSrc.contains("onWebSearchClick = { onNavigate(Screen.WebSearch(Screen.Chat)) }"))
    }
}
