package dev.bidzz.opencode.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D14 (notifikasi): scanner guards for the fixes whose
 * behaviour lives in a service, a view model or a composable that a JVM
 * test cannot drive — the source must keep the shape the fix gave it.
 */
class AuditD14Test {

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

    private val app = "src/main/kotlin/dev/bidzz/opencode/ui/OpenCodeApp.kt"
    private val chatScreen = "src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt"
    private val application = "src/main/kotlin/dev/bidzz/opencode/OpenCodeApplication.kt"
    private val mainActivity = "src/main/kotlin/dev/bidzz/opencode/MainActivity.kt"
    private val notifier = "src/main/kotlin/dev/bidzz/opencode/ui/AgentDoneNotifier.kt"
    private val idStore = "src/main/kotlin/dev/bidzz/opencode/ui/AgentDoneIdStore.kt"
    private val service = "src/main/kotlin/dev/bidzz/opencode/service/EngineService.kt"
    private val inbox = "src/main/kotlin/dev/bidzz/opencode/ui/inbox/InboxScreen.kt"
    private val widget = "src/main/kotlin/dev/bidzz/opencode/ui/widget/SessionWidgetProvider.kt"
    private val strings = "src/main/res/values/strings.xml"
    private val stringsId = "src/main/res/values-in/strings.xml"

    // ---- m-785: one alert per run -----------------------------------

    @Test
    fun `m-785 the agent-done notification alerts only once`() {
        val src = read(notifier)
        val body = funBody(src, "fun notifyAgentDone(")
        assertTrue(body.contains(".setOnlyAlertOnce(true)"))
    }

    // ---- m-324: no double-load on entry ------------------------------

    @Test
    fun `m-324 Inbox Move and Instructions do not load before navigating`() {
        val src = read(app)
        assertFalse(src.contains("inboxViewModel.load(row.id)"))
        assertFalse(src.contains("moveSessionViewModel.load(row.id)"))
        assertFalse(src.contains("instructionsViewModel.load(row.id)"))
        // The screens load once on entry; the navigation itself stays.
        assertTrue(src.contains("screen = Screen.Inbox(row.id)"))
        assertTrue(src.contains("screen = Screen.MoveSession(row.id)"))
        assertTrue(src.contains("screen = Screen.Instructions(row.id)"))
    }

    // ---- m-1169: drawer menu reaches the four screens ---------------

    @Test
    fun `m-1169 the drawer threads all four screen callbacks`() {
        val screen = read(chatScreen)
        assertTrue(screen.contains("onInboxSession: (String) -> Unit"))
        assertTrue(screen.contains("onMoveSession: (String) -> Unit"))
        assertTrue(screen.contains("onExportSession: (String) -> Unit"))
        assertTrue(screen.contains("onInstructionsSession: (String) -> Unit"))
        assertTrue(screen.contains("onInboxClick = { onInboxSession(row.id) }"))
        assertTrue(screen.contains("onMoveClick = { onMoveSession(row.id) }"))
        assertTrue(screen.contains("onExportClick = { onExportSession(row.id) }"))
        assertTrue(screen.contains("onInstructionsClick = { onInstructionsSession(row.id) }"))
        assertTrue(screen.contains("onInboxClick = onInboxClick,"))
        val open = read(app)
        assertTrue(open.contains("onInboxSession = { id -> onNavigate(Screen.Inbox(id)) }"))
        assertTrue(open.contains("onMoveSession = { id -> onNavigate(Screen.MoveSession(id)) }"))
        assertTrue(open.contains("onExportSession = { id -> onNavigate(Screen.SessionExport(id)) }"))
        assertTrue(open.contains("onInstructionsSession = { id -> onNavigate(Screen.Instructions(id)) }"))
    }

    // ---- m-472: queue age in the inbox row ---------------------------

    @Test
    fun `m-472 the inbox row shows how long the prompt has waited`() {
        val src = read(inbox)
        assertTrue(src.contains("item.time?.created?.let { formatAge(it) }"))
        assertTrue(src.contains("val ageText = "))
        assertTrue(src.contains("if (ageText != null) append(ageText)"))
    }

    // ---- m-501/m-551: fresh language, no restart ---------------------

    @Test
    fun `m-501 the four application snackbar sites read the stored language fresh`() {
        val src = read(application)
        assertTrue(src.contains("appLanguageContext(this).getString(R.string.saf_picker_failed)"))
        assertTrue(src.contains("appLanguageContext(this).getString(R.string.saf_file_dropped_named, name)"))
        assertTrue(src.contains("appLanguageContext(this).getString(R.string.camera_open_failed)"))
        assertTrue(src.contains("appLanguageContext(this).getString(R.string.camera_photo_dropped_named, name)"))
        val open = read(app)
        assertTrue(open.contains("localized.getString(R.string.workspace_too_broad)"))
    }

    @Test
    fun `m-551 the cold-start widget wraps the stored language fresh`() {
        val src = read(widget)
        assertTrue(src.contains("pushWidget(appLanguageContext(context), lastSessionID, null)"))
    }

    // ---- m-370: channel names follow the language --------------------

    @Test
    fun `m-370 channel ids carry the display language`() {
        val notify = read(notifier)
        assertTrue(notify.contains("notificationChannelId(\"agent_done\", currentChannelLanguage(context))"))
        assertFalse(notify.contains("CHANNEL_ID"))
        val svc = read(service)
        assertTrue(svc.contains("notificationChannelId(CHANNEL_BASE, currentChannelLanguage(context))"))
        assertTrue(svc.contains("notificationChannelId(FAILED_CHANNEL_BASE, currentChannelLanguage(this))"))
        assertFalse(svc.contains("CHANNEL_ID"))
        assertTrue(svc.contains("pruneStaleNotificationChannels(context, setOf(channelId, failureChannelId))"))
    }

    // ---- m-790/m-839: legacy ids --------------------------------------

    @Test
    fun `m-790 every post cancels the legacy hash id when it is safe`() {
        val src = read(notifier)
        val body = funBody(src, "fun notifyAgentDone(")
        assertTrue(body.contains("manager.cancel(sessionID.hashCode())"))
        assertTrue(body.contains("shouldCancelLegacyAgentDoneId(sessionID.hashCode(), notificationId, store.assignedIdValues())"))
    }

    @Test
    fun `m-839 the clear-data id reuse is documented as a limitation`() {
        val src = read(idStore)
        assertTrue(src.contains("per-install epoch"))
        assertTrue(src.contains("only clear data"))
    }

    // ---- m-419: no raw exception text on screen ----------------------

    @Test
    fun `m-419 the engine-start failure shows a generic message`() {
        val src = read(application)
        val body = funBody(src, "catch (e: Exception)")
        assertTrue(body.contains("startupFailure = genericFailure"))
        assertTrue(body.contains("lastError = genericFailure"))
        assertFalse(body.contains("startupFailure = e.message"))
        assertFalse(body.contains("lastError = e.message"))
        // The raw message stays in logcat only.
        assertTrue(body.contains("Log.e(TAG, \"engine unavailable: \${e.message}\")"))
    }

    // ---- m-531/m-789/m-979: the watcher gate -------------------------

    @Test
    fun `m-531 the watcher posts unless that exact chat is visible`() {
        val svc = read(service)
        val body = funBody(svc, "private fun updateAgentDoneWatcher(")
        assertTrue(body.contains("shouldPostAgentDone("))
        assertTrue(body.contains("app.isLockScreenShowing"))
        assertTrue(body.contains("app.visibleChatSessionID"))
        assertFalse(body.contains("if ((application as OpenCodeApplication).isAppForeground) return@collect"))
        val appSrc = read(application)
        assertTrue(appSrc.contains("var isLockScreenShowing: Boolean = false"))
        assertTrue(appSrc.contains("var visibleChatSessionID: String? = null"))
        val open = read(app)
        assertTrue(open.contains("app.visibleChatSessionID = if (screen is Screen.Chat) chatState.sessionID else null"))
    }

    // ---- m-707: post-unlock confirmation -----------------------------

    @Test
    fun `m-707 intents arriving while locked are held for confirmation`() {
        val activity = read(mainActivity)
        assertEquals(4, count(activity, "holdIfLocked(PendingExternalIntent"))
        assertTrue(activity.contains("fun holdIfLocked(pending: PendingExternalIntent): Boolean"))
        assertTrue(activity.contains("shouldHoldExternalIntent(AppLockPrefs.isEnabled(this), unlocked)"))
        val appSrc = read(application)
        assertTrue(appSrc.contains("val pendingExternalIntent = MutableStateFlow<PendingExternalIntent?>(null)"))
        assertTrue(appSrc.contains("fun executePendingExternalIntent(pending: PendingExternalIntent)"))
        val open = read(app)
        assertTrue(open.contains("app.pendingExternalIntent.collectAsStateWithLifecycle()"))
        assertTrue(open.contains("app.executePendingExternalIntent(pending)"))
        assertTrue(open.contains("stringResource(R.string.external_intent_open_session, pending.sessionID)"))
        assertTrue(read(strings).contains("name=\"external_intent_open_session\""))
        assertTrue(read(strings).contains("name=\"external_intent_new_session\""))
        assertTrue(read(strings).contains("name=\"external_intent_new_session_shared\""))
        assertTrue(read(stringsId).contains("name=\"external_intent_open_session\""))
        assertTrue(read(stringsId).contains("name=\"external_intent_new_session\""))
        assertTrue(read(stringsId).contains("name=\"external_intent_new_session_shared\""))
    }
}
