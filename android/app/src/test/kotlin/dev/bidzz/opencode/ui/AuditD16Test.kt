package dev.bidzz.opencode.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D16 (transcript / scroll / anchor): scanner guards for the
 * fixes whose behaviour lives in a composable or a ViewModel effect that
 * a JVM test cannot drive — the source must keep the shape the fix gave
 * it. Pure-function behaviour is tested in ChatScrollAnchorTest,
 * ReadPositionTest, ChatFollowTest, SessionGroupTest and HeaderTitleTest.
 */
class AuditD16Test {

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

    private fun count(src: String, needle: String): Int =
        src.split(needle).size - 1

    private val chatScreen = "src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt"
    private val viewModel = "src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatViewModel.kt"
    private val toolRow = "src/main/kotlin/dev/bidzz/opencode/ui/chat/ToolRow.kt"
    private val sessionGroup = "src/main/kotlin/dev/bidzz/opencode/ui/sessions/SessionGroup.kt"
    private val headerTitle = "src/main/kotlin/dev/bidzz/opencode/ui/chat/HeaderTitle.kt"
    private val diffScreen = "src/main/kotlin/dev/bidzz/opencode/ui/diff/DiffScreen.kt"

    // ---- m-038/m-048: id-based anchor restore --------------------------

    @Test
    fun `m-038 the load-earlier restore is by message id`() {
        val src = read(chatScreen)
        assertTrue(src.contains("restoreScrollAnchorById("))
        assertTrue(src.contains("anchorMessageId = anchorMessageId("))
        // The count-only restore must not be the one the effect calls.
        assertFalse(src.contains("val restored = restoreScrollAnchor("))
    }

    // ---- m-044: the expand anchor waits out the gesture ----------------

    @Test
    fun `m-044 the expand anchor is retried after the gesture ends`() {
        val src = read(chatScreen)
        val body = funBody(src, "LaunchedEffect(pendingExpandAnchor)")
        assertTrue(body.contains("snapshotFlow { listState.isScrollInProgress }.first { !it }"))
        assertTrue(body.contains("listState.scrollToItem(anchor.index, anchor.offset)"))
        // The anchor is cleared only after a successful restore, never
        // before the gesture check.
        assertTrue(body.indexOf("pendingExpandAnchor = null") > body.indexOf("listState.scrollToItem"))
    }

    // ---- m-051: the pinned expand restore re-pins ----------------------

    @Test
    fun `m-051 the expand restore re-pins when the reader was pinned`() {
        val src = read(chatScreen)
        assertTrue(src.contains("pendingExpandPinned = pinnedToBottom"))
        val body = funBody(src, "LaunchedEffect(pendingExpandAnchor)")
        assertTrue(body.contains("if (pinned) {"))
        assertTrue(body.contains("listState.scrollToItemBottom("))
        assertTrue(body.contains("expandRestoreLastIndex("))
    }

    // ---- m-054: live tools survive the ghost removal -------------------

    @Test
    fun `m-054 the ghost removal excepts live tools`() {
        val src = read(viewModel)
        assertTrue(src.contains("it.tools.none { t -> chat.liveTools.containsKey(t.id) }"))
    }

    // ---- m-100: one shared horizontal scroll state in the diff block ---

    @Test
    fun `m-100 the diff block shares one horizontal scroll state`() {
        val src = read(toolRow)
        val body = funBody(src, "private fun InlineDiffBlock(")
        assertTrue(body.contains("val horizontalScrollState = rememberScrollState()"))
        assertTrue(body.contains(".horizontalScroll(horizontalScrollState)"))
        assertFalse(body.contains(".horizontalScroll(rememberScrollState())"))
    }

    // ---- m-151: no Fork/Revert pills on optimistic bubbles -------------

    @Test
    fun `m-151 optimistic bubbles get no fork or revert pill`() {
        val src = read(chatScreen)
        assertTrue(src.contains("message.role == Role.USER && !message.id.startsWith(\"local:\")"))
        val vm = read(viewModel)
        assertTrue(funBody(vm, "fun forkMessage(").contains("if (messageId.startsWith(\"local:\")) return"))
        assertTrue(funBody(vm, "fun stageRevert(").contains("if (messageId.startsWith(\"local:\")) return"))
    }

    // ---- m-163: pin first, then cap ------------------------------------

    @Test
    fun `m-163 the drawer caps after pins ride along`() {
        val src = read(chatScreen)
        assertTrue(src.contains("drawerSessions(filteredSessions, DRAWER_SESSIONS)"))
        assertFalse(src.contains("filteredSessions.take(DRAWER_SESSIONS)"))
        val sg = read(sessionGroup)
        assertTrue(sg.contains("fun drawerSessions(rows: List<SessionRow>, cap: Int)"))
    }

    // ---- m-175: search matches the directory basename ------------------

    @Test
    fun `m-175 the session search matches the directory basename only`() {
        val src = read(sessionGroup)
        assertTrue(src.contains("row.directory?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }"))
    }

    // ---- m-192: the drawer resets on a width-class change --------------

    @Test
    fun `m-192 the drawer closes when the layout goes expanded`() {
        val src = read(chatScreen)
        assertTrue(src.contains("LaunchedEffect(isExpanded) {"))
        val body = funBody(src, "LaunchedEffect(isExpanded)")
        assertTrue(body.contains("if (isExpanded) drawerState.close()"))
    }

    // ---- m-199: the header disambiguates colliding names ---------------

    @Test
    fun `m-199 the header shows the parent segment on a name collision`() {
        val src = read(headerTitle)
        assertTrue(src.contains("fun headerTitle(workspacePath: String?, knownWorkspaces: Set<String> = emptySet())"))
        assertTrue(src.contains("val collides = knownWorkspaces.any"))
        assertTrue(src.contains("\"\$parent/\$name\""))
        val screen = read(chatScreen)
        assertTrue(screen.contains("headerTitle(state.workspacePath, knownWorkspacePaths)"))
        // ReadinessStatusRow threads the same set through its own param
        // (its EmptyChat call site has no sessions in scope, so it keeps
        // the default there).
        assertTrue(screen.contains("knownWorkspaces: Set<String> = emptySet()"))
        assertTrue(screen.contains("headerTitle(workspacePath, knownWorkspaces)"))
    }

    // ---- m-256/m-262: DiffScreen scroll hoisting ------------------------

    @Test
    fun `m-262 each diff mode keeps a saved list state`() {
        val src = read(diffScreen)
        assertTrue(src.contains("val unifiedListState = rememberSaveable(saver = LazyListState.Saver)"))
        assertTrue(src.contains("val splitListState = rememberSaveable(saver = LazyListState.Saver)"))
        assertTrue(src.contains("UnifiedDiffView(state = state, listState = unifiedListState)"))
        assertTrue(src.contains("SplitDiffView(state = state, listState = splitListState)"))
        assertTrue(count(src, "state = listState,") == 2)
    }

    @Test
    fun `m-256 the split cells share one horizontal scroll state`() {
        val src = read(diffScreen)
        assertTrue(src.contains("val cellScrollState = rememberScrollState()"))
        assertTrue(src.contains("SplitDiffRow(pair, colors, cellScrollState)"))
        assertTrue(src.contains(".horizontalScroll(horizontalScrollState)"))
    }
}
