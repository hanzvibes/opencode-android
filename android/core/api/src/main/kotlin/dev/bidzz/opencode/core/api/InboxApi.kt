package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.InboxItem
import dev.bidzz.opencode.core.api.dto.InboxResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** Body of `PATCH /api/session/{id}/inbox/{inboxID}`: `steer` delivers the
 * item into the running execution at once, `queue` waits for the run to
 * end. Verified against the engine fork source at pin fedc6f20
 * (`packages/protocol/src/groups/session.ts`, `session.inbox.update`;
 * `Session.Inbox.Delivery = "steer" | "queue"` in
 * `packages/schema/src/session-inbox.ts`). Answers **204 No Content**;
 * an item already delivered fails with `ConflictError`. */
@Serializable
internal data class UpdateInboxDeliveryRequest(
    val delivery: String,
)

/**
 * Queued work for one session.
 *
 * Lists the inbox, cancels an item (`DELETE
 * /api/session/{id}/inbox/{inboxID}`) and changes an item's delivery
 * (`PATCH ...` with `{delivery: "steer" | "queue"}`). Cancelling and
 * re-delivering used to be writes the app deliberately did not call
 * (audit-200 C17, M-257): a mistyped prompt queued behind a run could
 * not be stopped from the app at all. Both endpoints are stable, not
 * experimental.
 *
 * Shape verified against the engine fork source at pin 92b2ca0
 * (`packages/protocol/src/groups/session.ts`,
 * `packages/schema/src/session-inbox.ts`): the list answers
 * `{data: Session.Inbox.Info[]}` ordered by enqueue sequence, where
 * `Session.Inbox.Info` is the tagged union `user | synthetic |
 * compaction | move` (see `InboxItem`).
 */
class InboxApi(private val client: EngineClient) {

    suspend fun list(sessionID: String): ApiResult<List<InboxItem>> {
        val request = client.request("/api/session/$sessionID/inbox").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<InboxResponse>(raw).data
        }
    }

    /**
     * Cancels a queued item that has not been delivered yet (`DELETE
     * /api/session/{id}/inbox/{inboxID}`). Answers **204 No Content**;
     * an item that is already gone is a no-op. Verified against the
     * engine fork source at pin fedc6f20 (`session.inbox.cancel`).
     */
    suspend fun delete(sessionID: String, inboxID: String): ApiResult<Unit> {
        val request = client.request("/api/session/$sessionID/inbox/$inboxID").delete().build()
        return client.executeUnit(request)
    }

    /**
     * Changes a pending item's delivery mode (`PATCH
     * /api/session/{id}/inbox/{inboxID}`, `{delivery}`).
     * Answers **204 No Content**.
     */
    suspend fun updateDelivery(
        sessionID: String,
        inboxID: String,
        delivery: String,
    ): ApiResult<Unit> {
        val body = ApiJson.encodeToString(UpdateInboxDeliveryRequest(delivery = delivery))
        val request = client.request("/api/session/$sessionID/inbox/$inboxID")
            .patch(client.jsonBody(body))
            .build()
        return client.executeUnit(request)
    }
}
