package dev.bidzz.opencode.api

import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.AgentApi
import dev.bidzz.opencode.core.api.ChatRepository
import dev.bidzz.opencode.core.api.CommandApi
import dev.bidzz.opencode.core.api.CredentialApi
import dev.bidzz.opencode.core.api.EngineClient
import dev.bidzz.opencode.core.api.EventStream
import dev.bidzz.opencode.core.api.IntegrationApi
import dev.bidzz.opencode.core.api.ModelApi
import dev.bidzz.opencode.core.api.ProviderApi
import dev.bidzz.opencode.core.api.SessionApi
import dev.bidzz.opencode.core.engine.EngineCredentials
import java.util.concurrent.atomic.AtomicReference

/**
 * Builds the API stack for a running engine.
 *
 * Kept separate from the Activity and the service so both can share one instance
 * and so the credentials can be swapped when the engine restarts on a new port
 * with a new password.
 */
class EngineApi(private val workspaceDirectory: () -> String) {

    private val current = AtomicReference<EngineApi.Handle?>(null)

    class Handle(
        val client: EngineClient,
        val sessions: SessionApi,
        val models: ModelApi,
        val agents: AgentApi,
        val commands: CommandApi,
        val providers: ProviderApi,
        val integrations: IntegrationApi,
        /** Named to avoid colliding with [credentials], which is the engine's own auth. */
        val credentialStore: CredentialApi,
        val stream: EventStream,
        val credentials: EngineCredentials,
    )

    /** Latest handle, or null before the engine is running. */
    fun handle(): Handle? = current.get()

    /**
     * Creates a handle for [credentials], replacing any previous one.
     *
     * The base URL and password both change on every engine restart (dynamic port,
     * new password printed to stdout), so a cached handle is never reusable across
     * restarts.
     */
    fun attach(credentials: EngineCredentials): Handle {
        val client = EngineClient(credentials.baseUrl, credentials.basicAuthHeader)
        val handle = Handle(
            client = client,
            sessions = SessionApi(client),
            models = ModelApi(client),
            agents = AgentApi(client),
            commands = CommandApi(client),
            providers = ProviderApi(client),
            integrations = IntegrationApi(client),
            credentialStore = CredentialApi(client),
            stream = EventStream(client),
            credentials = credentials,
        )
        current.set(handle)
        return handle
    }

    fun detach() {
        current.set(null)
    }

    /** Convenience for a chat repository bound to the current handle. */
    fun chatRepository(scope: kotlinx.coroutines.CoroutineScope): ChatRepository? {
        val h = handle() ?: return null
        return ChatRepository(h.sessions, h.stream, scope)
    }

    fun workspace(): String = workspaceDirectory()
}

/** Formats an API failure for the diagnostics screen. */
fun ApiResult.Failure.describe(): String = buildString {
    append(display)
    if (code != 0) append(" [HTTP $code]")
    if (tag != null) append(" [").append(tag).append(']')
}
