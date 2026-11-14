package dev.bidzz.opencode.api

import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.AgentApi
import dev.bidzz.opencode.core.api.ArtifactApi
import dev.bidzz.opencode.core.api.JobApi
import dev.bidzz.opencode.core.api.ChatRepository
import dev.bidzz.opencode.core.api.CommandApi
import dev.bidzz.opencode.core.api.CredentialApi
import dev.bidzz.opencode.core.api.EngineClient
import dev.bidzz.opencode.core.api.EventStream
import dev.bidzz.opencode.core.api.IntegrationApi
import dev.bidzz.opencode.core.api.ModelApi
import dev.bidzz.opencode.core.api.McpApi
import dev.bidzz.opencode.core.api.GenerateApi
import dev.bidzz.opencode.core.api.WebSearchApi
import dev.bidzz.opencode.core.api.PermissionApi
import dev.bidzz.opencode.core.api.ConfigApi
import dev.bidzz.opencode.core.api.PluginApi
import dev.bidzz.opencode.core.api.ProjectApi
import dev.bidzz.opencode.core.api.WorktreeApi
import dev.bidzz.opencode.core.api.ProviderApi
import dev.bidzz.opencode.core.api.SkillApi
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

    companion object {
        /**
         * Call timeout for the generation APIs (audit-200 B7, M-497):
         * 10 minutes. Generations wait for the whole result, and a 120 s
         * cut mid-generation wastes the cost and invites a paid retry.
         */
        const val GENERATE_CALL_TIMEOUT_SECONDS: Long = 600
    }

    class Handle(
        val client: EngineClient,
        val sessions: SessionApi,
        val models: ModelApi,
        val agents: AgentApi,
        val commands: CommandApi,
        val skills: SkillApi,
        val mcp: McpApi,
        val plugins: PluginApi,
        val artifacts: ArtifactApi,
        val jobs: JobApi,
        val generate: GenerateApi,
        val websearch: WebSearchApi,
        val projects: ProjectApi,
        val worktrees: WorktreeApi,
        val config: ConfigApi,
        val providers: ProviderApi,
        val permissions: PermissionApi,
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
        // Audit-200 B7 (M-497): generations are synchronous request/response
        // calls — text, image and session-generate all wait for the whole
        // result — so they get their own client with a 10-minute call
        // timeout. The shared 120 s client used to cut a long generation
        // off mid-way, and the user retrying paid for it again. Every other
        // API keeps the shared client (a chat prompt's run streams via SSE).
        val generateClient = EngineClient(
            credentials.baseUrl,
            credentials.basicAuthHeader,
            callTimeoutSeconds = GENERATE_CALL_TIMEOUT_SECONDS,
        )
        val handle = Handle(
            client = client,
            sessions = SessionApi(client),
            models = ModelApi(client),
            agents = AgentApi(client),
            commands = CommandApi(client),
            skills = SkillApi(client),
            mcp = McpApi(client),
            plugins = PluginApi(client),
            artifacts = ArtifactApi(client),
            jobs = JobApi(client),
            generate = GenerateApi(generateClient),
            websearch = WebSearchApi(client),
            projects = ProjectApi(client),
            worktrees = WorktreeApi(client),
            config = ConfigApi(client),
            providers = ProviderApi(client),
            permissions = PermissionApi(client),
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
    append(reason.name)
    if (code != 0) append(" [HTTP $code]")
    if (tag != null) append(" [").append(tag).append(']')
    if (!message.isNullOrBlank()) append(" ").append(message)
}
