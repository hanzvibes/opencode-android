package dev.bidzz.opencode.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D17 (transcript / scroll / anchor, part 2): scanner guards
 * for the fixes whose behaviour lives in a composable or a ViewModel
 * effect that a JVM test cannot drive — the source must keep the shape
 * the fix gave it. Pure-function behaviour is tested in
 * RateLimitErrorD17Test and ChatRepositoryD17Test.
 */
class AuditD17Test {

    private fun read(path: String): String = File(path).readText()

    /** Body of the function whose signature starts at [marker], brace-matched (D3 pattern). */
    private fun funBody(src: String, marker: String): String {
        val start = src.indexOf(marker)
        require(start >= 0) { "missing $marker" }
        // The body's opening brace follows the signature's closing paren:
        // a bare '{' search would stop at a `{}` default value inside the
        // signature (InlineDiffBlock's onBeforeHeightChange).
        val open = src.indexOf(") {", start)
        require(open >= 0) { "no body after $marker" }
        var i = open + 1
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

    /** Body of a LaunchedEffect call starting at [marker], brace-matched. */
    private fun effectBody(src: String, marker: String): String {
        val start = src.indexOf(marker)
        require(start >= 0) { "missing $marker" }
        val open = src.indexOf("{", start)
        require(open >= 0) { "no body after $marker" }
        var i = open
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

    private val chatScreen = "src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt"
    private val viewModel = "src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatViewModel.kt"
    private val messageBubble = "src/main/kotlin/dev/bidzz/opencode/ui/chat/MessageBubble.kt"
    private val onboarding = "src/main/kotlin/dev/bidzz/opencode/ui/onboarding/OnboardingScreen.kt"
    private val mainActivity = "src/main/kotlin/dev/bidzz/opencode/MainActivity.kt"
    private val explorer = "src/main/kotlin/dev/bidzz/opencode/ui/files/ExplorerScreen.kt"
    private val chatRepository = "../core/api/src/main/kotlin/dev/bidzz/opencode/core/api/ChatRepository.kt"

    // ---- m-266: grant access is its own explained step before Choose ----

    @Test
    fun `m-266 step 0 shows the grant button before Choose a folder`() {
        val src = read(onboarding)
        // Signature facts live outside the body (D5): assert them on the
        // whole file, body facts on the body.
        assertTrue(src.contains("hasAllFilesAccess: Boolean,"))
        val body = funBody(src, "private fun ProjectStep(")
        assertTrue(body.contains("if (!hasAllFilesAccess)"))
        assertTrue(body.contains("R.string.onboarding_permissions_body"))
        assertTrue(body.contains("R.string.onboarding_grant_access"))
        assertTrue(body.contains("onGrantAllFilesAccess"))
    }

    // ---- m-268: the stale "Unused" comment is gone -----------------------

    @Test
    fun `m-268 requestStorageIfNeeded is documented as a live path`() {
        val src = read(mainActivity)
        assertFalse(src.contains("Unused for now"))
        val at = src.indexOf("private fun requestStorageIfNeeded()")
        require(at >= 0)
        val head = src.substring(maxOf(0, at - 400), at)
        assertFalse(head.contains("@Suppress(\"unused\")"))
        assertTrue(head.contains("all-files-access"))
    }

    // ---- m-352: the footer rides in the drawer LazyColumn ----------------

    @Test
    fun `m-352 the drawer footer is a trailing item of the same list`() {
        val src = read(chatScreen)
        assertTrue(src.contains("item(key = \"footer_block\""))
        assertTrue(src.contains("item(key = \"footer_view_all\""))
        assertTrue(src.contains("!it.startsWith(\"footer_\")"))
        // No second scrollable for the footer: the drawer is one LazyColumn.
        val drawerBody = funBody(src, "fun ChatDrawerContent(")
        assertFalse(drawerBody.contains("verticalScroll(rememberScrollState())"))
    }

    // ---- m-421: the open gate resets when the engine dies ----------------

    @Test
    fun `m-421 scrolledSessionID resets while the session is gone`() {
        val src = read(chatScreen)
        assertTrue(src.contains("if (state.sessionID == null) scrolledSessionID = null"))
    }

    // ---- m-426: anchors are saveable, the window is remembered -----------

    @Test
    fun `m-426 load and expand anchors survive recreation`() {
        val src = read(chatScreen)
        assertTrue(src.contains("rememberSaveable(stateSaver = ScrollAnchorSaver)"))
        assertTrue(count(src, "rememberSaveable(stateSaver = ScrollAnchorSaver)") == 2)
        val repo = read(chatRepository)
        assertTrue(repo.contains("class TranscriptWindowMemory"))
        assertTrue(repo.contains("windowMemory.widthFor(sessionID)"))
        assertTrue(repo.contains("windowMemory.remember(id, messageLimit)"))
        assertTrue(repo.contains("windowMemory.remember(id, combined.size)"))
    }

    // ---- m-430/m-448: ThinkingDisclosure keeps a manual open -------------

    @Test
    fun `m-430 a manually opened reasoning panel stays open`() {
        val src = read(messageBubble)
        val body = funBody(src, "fun ThinkingDisclosure(")
        assertTrue(body.contains("var userExpanded by rememberSaveable"))
        assertTrue(body.contains("!userExpanded && isExpanded"))
        assertTrue(body.contains("if (isExpanded) userExpanded = true"))
    }

    @Test
    fun `m-448 the disclosure reports height changes to the screen`() {
        val src = read(messageBubble)
        // The param lives in the signature (D5): assert it on the file.
        assertTrue(src.contains("onBeforeHeightChange: () -> Unit = {}"))
        val body = funBody(src, "fun ThinkingDisclosure(")
        assertTrue(body.contains("onBeforeHeightChange()"))
        assertTrue(src.contains("onBeforeHeightChange = onBeforeToolTraceHeightChange"))
        // Both disclosures (reasoning + tool trace) report to the same anchor.
        assertTrue(count(src, "onBeforeHeightChange = onBeforeToolTraceHeightChange") == 2)
    }

    // ---- m-449: the divider keeps its space when the watermark moves -----

    @Test
    fun `m-449 the divider leaves an invisible placeholder behind`() {
        val src = read(chatScreen)
        assertTrue(src.contains("dividerHostId = state.messages.getOrNull(firstUnreadIndex)?.id"))
        assertTrue(src.contains("message.id == dividerHostId"))
        assertTrue(src.contains(".alpha(0f)"))
    }

    // ---- m-450: no restore after the reader scrolled away ----------------

    @Test
    fun `m-450 the load-earlier restore checks the reader stayed put`() {
        val src = read(chatScreen)
        val body = effectBody(src, "LaunchedEffect(state.loadingEarlier, state.messages.size)")
        assertTrue(body.contains("firstVisible.index == anchor.index"))
        assertTrue(body.contains("firstVisible.offset == anchor.offset"))
        assertTrue(body.indexOf("firstVisible.index == anchor.index") < body.indexOf("scrollToItem(restored.index"))
    }

    // ---- m-453: the slow bottom-pin path never top-pins a frame ----------

    @Test
    fun `m-453 scrollToItemBottom never top-pins on the slow path`() {
        val src = read(chatScreen)
        val body = funBody(src, "private suspend fun LazyListState.scrollToItemBottom(")
        assertFalse(body.contains("scrollToItem(index)"))
        assertTrue(body.contains("BOTTOM_PIN_CLAMP_OFFSET"))
        val twin = funBody(src, "private suspend fun LazyListState.animateScrollToItemBottom(")
        assertFalse(twin.contains("animateScrollToItem(index)"))
        assertTrue(twin.contains("BOTTOM_PIN_CLAMP_OFFSET"))
    }

    // ---- m-455: history does not animate in -------------------------------

    @Test
    fun `m-455 entry animation is suppressed until the open lands`() {
        val src = read(chatScreen)
        assertTrue(src.contains("var suppressEntryAnimation by remember(state.sessionID)"))
        assertTrue(src.contains("EnterTransition.None"))
        assertTrue(src.contains("suppressEntryAnimation = false"))
    }

    // ---- m-459: the open gate opens after the scroll lands ---------------

    @Test
    fun `m-459 scrolledSessionID is set after the open scroll`() {
        val src = read(chatScreen)
        val body = effectBody(src, "LaunchedEffect(state.sessionID, state.messages.size)")
        val scrollAt = body.lastIndexOf("scrollToItemBottom(targetLastIndex)")
        val gateAt = body.indexOf("scrolledSessionID = state.sessionID")
        require(scrollAt >= 0 && gateAt >= 0)
        assertTrue(gateAt > scrollAt)
    }

    // ---- m-538: the Explorer filter reset is locked (regression guard) ----

    @Test
    fun `m-538 the Explorer filter resets on folder change`() {
        val src = read(explorer)
        assertTrue(src.contains("LaunchedEffect(state.currentPath) { searchFilter = \"\" }"))
        assertTrue(src.contains("state.rows.size > 5 || searchFilter.isNotEmpty()"))
    }
}
