package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.InboxItem
import dev.bidzz.opencode.core.api.dto.InboxResponse

/**
 * Queued work for one session.
 *
 * Read-only: the app lists the inbox. Cancelling (`DELETE
 * /api/session/{id}/inbox/{inboxID}`) and changing delivery (`PATCH ...`)
 * are writes the app deliberately does not call.
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
}
