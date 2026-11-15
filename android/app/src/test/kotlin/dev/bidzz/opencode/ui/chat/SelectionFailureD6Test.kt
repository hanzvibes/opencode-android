package dev.bidzz.opencode.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D6 (snackbar / banner / toast): the guards that keep this
 * batch's `selectionFailure` state machine in place. The view model cannot
 * be driven by JVM tests for selectAgent / selectModel / createSession
 * (they launch coroutines), so — like the D2/D3/D4/D5 guards — the
 * behaviour that lives in the view model is pinned by scanning the source.
 * The parts that ARE drivable (the 429 guidance, dismiss) are behaviour
 * tests in [ChatViewModelD6Test].
 */
class SelectionFailureD6Test {

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

    private val vm = "src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatViewModel.kt"

    // ---- The split state (m-204 / m-317) ----

    @Test
    fun `the single selectionFailure slot is split per kind with a derived reader`() {
        val src = read(vm)
        assertTrue(src.contains("val agentSelectionFailure: ApiResult.Failure? = null,"))
        assertTrue(src.contains("val modelSelectionFailure: ApiResult.Failure? = null,"))
        assertTrue(src.contains("get() = agentSelectionFailure ?: modelSelectionFailure"))
        // No writer may set the old single slot any more — the derived
        // property is read-only, so a stale `selectionFailure = ...`
        // would not even compile; this guard catches a re-introduction
        // of a writable field by the same name.
        assertFalse(src.contains("selectionFailure = "))
    }

    // ---- selectAgent / selectModel (m-034 / m-383) ----

    @Test
    fun `a rejected agent pick reverts to the confirmed agent, not the stale pick`() {
        val src = read(vm)
        val body = funBody(src, "fun selectAgent(")
        assertTrue(body.contains("activeAgent = confirmedAgent"))
        assertTrue(body.contains("reportSelectionFailure(result, SelectionKind.AGENT)"))
        assertTrue(body.contains("agentSelectionFailure = null"))
        // The rendered failure hands over to the other kind's slot.
        assertTrue(body.contains("picked.modelSelectionFailure"))
        assertFalse(body.contains("activeAgent = previous"))
    }

    @Test
    fun `a rejected model pick reverts to the confirmed model, not the stale pick`() {
        val src = read(vm)
        val body = funBody(src, "fun selectModel(")
        assertTrue(body.contains("activeModel = confirmedModel"))
        assertTrue(body.contains("reportSelectionFailure(result, SelectionKind.MODEL)"))
        assertTrue(body.contains("modelSelectionFailure = null"))
        assertTrue(body.contains("picked.agentSelectionFailure"))
        assertFalse(body.contains("activeModel = previous"))
    }

    @Test
    fun `reportSelectionFailure writes only the slot of its kind`() {
        val src = read(vm)
        val body = funBody(src, "private fun reportSelectionFailure(")
        assertTrue(body.contains("agentSelectionFailure = if (kind == SelectionKind.AGENT) result else _state.value.agentSelectionFailure"))
        assertTrue(body.contains("modelSelectionFailure = if (kind == SelectionKind.MODEL) result else _state.value.modelSelectionFailure"))
        assertTrue(body.contains("rateLimitGuidanceShown = false"))
    }

    @Test
    fun `syncModelFromSession records the confirmed selection from the engine`() {
        val src = read(vm)
        val body = funBody(src, "fun syncModelFromSession(")
        assertTrue(body.contains("confirmedAgent = result.value.agent"))
        assertTrue(body.contains("confirmedModel = result.value.model"))
    }

    // ---- createSession (m-034 / m-383 residual) ----

    @Test
    fun `createSession reports both kinds and keeps the C7 session guard`() {
        val src = read(vm)
        val body = funBody(src, "private suspend fun createSession(")
        assertTrue(body.contains("reportSelectionFailure(it, SelectionKind.AGENT)"))
        assertTrue(body.contains("reportSelectionFailure(it, SelectionKind.MODEL)"))
        // The C7 guard: only report when this repo is still the current
        // one and the created session is installed.
        assertTrue(body.contains("repository === repo && repo.state.value.sessionID == createdId"))
        assertTrue(body.contains("if (agentFailed) confirmedAgent = null"))
        assertTrue(body.contains("if (modelFailed) confirmedModel = null"))
        // The create-FAILURE branch must not report on a superseded repo.
        assertTrue(body.contains("if (repository === repo && repo.state.value.sessionID == null)"))
    }

    // ---- onRepoState / onEngineStatus (m-319) ----

    @Test
    fun `a late repo failure never plants over the engine-down banner`() {
        val src = read(vm)
        assertTrue(src.contains("if (isEngineDown()) null else chat.lastFailure"))
    }

    @Test
    fun `engine death clears both selection slots and the guidance flag`() {
        val src = read(vm)
        val body = funBody(src, "fun onEngineStatus(")
        assertTrue(body.contains("agentSelectionFailure = null"))
        assertTrue(body.contains("modelSelectionFailure = null"))
        assertTrue(body.contains("rateLimitGuidanceShown = false"))
    }

    // ---- The 429 guidance (m-312) ----

    @Test
    fun `the 429 guidance survives a resync success but dies on a fresh non-429 failure`() {
        val src = read(vm)
        assertTrue(src.contains("previous.rateLimitGuidanceShown -> R.string.chat_rate_limited_guidance"))
        // The flag is computed from a captured local — ChatState comes
        // from another module, so the compiler cannot smart-cast
        // `chat.lastFailure` across two reads.
        assertTrue(src.contains("val rateLimitGuidanceKept = previous.rateLimitGuidanceShown &&"))
        assertTrue(src.contains("(lastFailure == null || lastFailure.code == 429)"))
        assertTrue(src.contains("rateLimitGuidanceShown = rateLimitGuidanceKept,"))
    }

    @Test
    fun `retrying a rate limit and dismissing both clear the repo failure`() {
        val src = read(vm)
        // The RATE_LIMITED branch and dismissError both call through to
        // the repo — direction 2 of m-312.
        assertTrue(src.split("repository?.clearLastFailure()").size - 1 >= 2)
        val retry = funBody(src, "fun retryError(")
        assertTrue(retry.contains("rateLimitGuidanceShown = true"))
        val dismiss = funBody(src, "fun dismissError(")
        assertTrue(dismiss.contains("rateLimitGuidanceShown = false"))
        assertTrue(dismiss.contains("repository?.clearLastFailure()"))
    }

    @Test
    fun `the repo exposes clearLastFailure`() {
        val repo = read("../core/api/src/main/kotlin/dev/bidzz/opencode/core/api/ChatRepository.kt")
        assertTrue(repo.contains("fun clearLastFailure()"))
        assertTrue(repo.contains("lastFailure = null, resyncFailed = false"))
    }

    // ---- stageRevert (m-154) ----

    @Test
    fun `a second revert is refused with feedback instead of overwriting the staged one`() {
        val src = read(vm)
        val body = funBody(src, "fun stageRevert(")
        assertTrue(body.contains("stagedRevert?.let"))
        assertTrue(body.contains("staged.messageID == messageId"))
        assertTrue(body.contains("R.string.chat_revert_already_staged"))
        assertTrue(body.contains("rateLimitGuidanceShown = false"))
    }

    // ---- The combined banner (m-317 render) ----

    @Test
    fun `two selection failures render as one two-line banner`() {
        val screen = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt")
        assertTrue(screen.contains("combinedSelectionFailureMessage(state)"))
        assertTrue(screen.contains("agentFailure.message() + \"\\n\" + modelFailure.message()"))
    }
}
