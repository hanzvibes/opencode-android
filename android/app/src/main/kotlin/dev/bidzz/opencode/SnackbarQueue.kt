package dev.bidzz.opencode

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The in-app snackbar queue (audit-200 D6, m-593): a pure holder for the
 * pending requests, extracted from [OpenCodeApplication] so the buffer
 * contract is unit-testable without an Application instance.
 *
 * One request per snackbar: while one is on screen the collector is
 * suspended in `showSnackbar`, and a burst queues in the buffer instead
 * of overlapping on screen. The buffer holds 16 (65-F8): a burst of 9
 * feedbacks inside ~30s was enough to overflow the old capacity-8
 * buffer, and the drop was silent. A drop past 16 is still possible —
 * it is logged by the caller ([OpenCodeApplication.showSnackbar]) and
 * the offer returns false, so action-bearing call sites can retry
 * (see [OpenCodeApplication.showSnackbarWithRetry]).
 *
 * A [Channel] on purpose, not a SharedFlow: a SharedFlow with replay 0
 * forgets every offer made while no collector is subscribed — and
 * `tryEmit` still returns true, so the caller cannot even tell the
 * request vanished (SharedFlowImpl.tryEmitNoCollectorsLocked: "no need
 * to replay, just forget it now"). A channel buffers offers with no
 * receiver yet and refuses the 17th, which is the contract the retry
 * helper and the tests rely on. There is exactly one collector
 * (OpenCodeApp's LaunchedEffect), so the single-receiver channel fits.
 */
internal class SnackbarQueue(
    private val capacity: Int = BUFFER_CAPACITY,
) {
    /** One snackbar to show: the already-resolved string, an optional action, and its duration. */
    data class Request(
        val message: String,
        val actionLabel: String? = null,
        val long: Boolean = false,
        val onAction: (() -> Unit)? = null,
    )

    private val channel = Channel<Request>(capacity = capacity)

    /** Requests, in arrival order; the root composable shows them one at a time. */
    val requests: Flow<Request> = channel.receiveAsFlow()

    /**
     * Offers a request without suspending. Returns false when the buffer
     * is full — the caller logs the drop and, for action-bearing requests,
     * retries (see [OpenCodeApplication.showSnackbarWithRetry]).
     */
    fun tryOffer(request: Request): Boolean = channel.trySend(request).isSuccess

    companion object {
        /** Pending snackbar requests held while one is on screen (65-F8). */
        const val BUFFER_CAPACITY = 16
    }
}
