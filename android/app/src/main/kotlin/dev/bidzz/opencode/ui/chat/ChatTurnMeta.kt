package dev.bidzz.opencode.ui.chat

/**
 * Opsi A ("Tenang, tapi rapi") — pure turn/trace rules, no Compose.
 *
 * The tool timeline defaults to ONE summary line once a run is done:
 * a finished clean run collapses, a run with an error stays expanded so
 * the failure is never hidden behind the summary, and a running trace
 * stays open while it streams (live progress is Fase 2 behaviour, not
 * decoration). A manual expand survives the run finishing — the reader
 * opened it on purpose, so auto-collapse must not close it under them.
 */
internal fun toolTraceInitialOpen(streaming: Boolean, hasError: Boolean): Boolean =
    streaming || hasError

/**
 * True when a finished, error-free trace may collapse itself: only when
 * the reader never expanded it by hand, the stream has ended, and there
 * is no error to surface. Every other combination keeps the trace as it
 * is (open while streaming, open on error, open when the reader opened
 * it).
 */
internal fun shouldAutoCollapseToolTrace(
    userExpanded: Boolean,
    streaming: Boolean,
    hasError: Boolean,
): Boolean = !userExpanded && !streaming && !hasError
