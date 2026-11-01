package dev.bidzz.opencode.core.api.dto

import kotlinx.serialization.Serializable

/**
 * DTOs for the engine's control surface: agents (modes), slash commands, providers.
 *
 * Every list endpoint wraps its payload in `{location, data}`. The `location` describes
 * the engine's working directory rather than the session, so it is captured but never
 * trusted as a session-scoped value — the same caveat [ModelsResponse] carries.
 */

/**
 * One agent, which is what the CLI calls a mode: `build`, `plan`, and any user-defined
 * agent.
 *
 * `id` and `name` are distinct branded strings upstream, and `POST /api/session/{id}/agent`
 * accepts the **id**. Treating the two as interchangeable breaks silently for any agent
 * whose name differs from its id.
 */
@Serializable
data class AgentInfo(
    val id: String,
    val name: String = "",
    val description: String? = null,
    /** `primary` and `all` are user-selectable; `subagent` is spawned by the agent itself. */
    val mode: String? = null,
    val hidden: Boolean? = null,
    val model: ModelRef? = null,
    val color: String? = null,
)

@Serializable
data class AgentsResponse(
    val data: List<AgentInfo> = emptyList(),
    val location: LocationRef? = null,
)

/** Body of `POST /api/session/{id}/agent`. */
@Serializable
data class SetAgentRequest(val agent: String)

/** A slash command as advertised by `GET /api/command`. */
@Serializable
data class CommandInfo(
    val name: String,
    val description: String? = null,
)

@Serializable
data class CommandsResponse(
    val data: List<CommandInfo> = emptyList(),
    val location: LocationRef? = null,
)

/**
 * Body of `POST /api/session/{id}/command`.
 *
 * `name` selects the command; `text` is whatever followed it on the command line. The
 * engine requires `text`, so it carries **no default** — `encodeDefaults = false` would
 * otherwise drop an argument-less command into a 400.
 *
 * Answers **204 No Content**: like a prompt, admission is immediate and the output
 * arrives over the event stream.
 */
@Serializable
data class RunCommandRequest(
    val name: String,
    val text: String,
)

/** One configured model provider. */
@Serializable
data class ProviderInfo(
    val id: String,
    val name: String = "",
    val source: String? = null,
)

@Serializable
data class ProvidersResponse(
    val data: List<ProviderInfo> = emptyList(),
    val location: LocationRef? = null,
)
