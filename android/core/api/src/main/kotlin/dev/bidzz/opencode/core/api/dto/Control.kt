package dev.bidzz.opencode.core.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
 * A skill as advertised by `GET /api/skill` (`Skill.Info`,
 * docs/API-REFERENCE.md).
 *
 * `content` is the skill file's full text — the palette shows only the
 * name and description and never reads it. `autoinvoke` is true when the
 * agent may use the skill on its own; the app never activates a skill
 * itself (activation is an experimental endpoint), it only lists them so
 * the operator can mention one in a prompt.
 */
@Serializable
data class SkillInfo(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val autoinvoke: Boolean? = null,
    val path: String? = null,
    val content: String? = null,
)

@Serializable
data class SkillsResponse(
    val data: List<SkillInfo> = emptyList(),
    val location: LocationRef? = null,
)

/**
 * One MCP server (`Mcp.Server`, docs/API-REFERENCE.md).
 *
 * `status` is one of the `Mcp.Status.*` variants: an object whose `status`
 * field reads `connected`, `disabled`, `failed`, `needs-auth` or `pending`,
 * with `error` present on `failed` and `needs-auth`. Connect/disconnect are
 * experimental endpoints the app does not call — this surface is list-only.
 */
@Serializable
data class McpStatus(
    val status: String,
    val error: String? = null,
)

@Serializable
data class McpServer(
    val name: String,
    val status: McpStatus,
    val title: String? = null,
    val integrationID: String? = null,
)

@Serializable
data class McpServersResponse(
    val data: List<McpServer> = emptyList(),
    val location: LocationRef? = null,
)

/**
 * One server plugin (`GET /api/plugin`).
 *
 * Shape verified against the engine fork source at pin 92b2ca0
 * (`packages/schema/src/plugin.ts`): `Plugin.Info` is
 * `{ id?, source, features, state }`. `source` is a tagged union —
 * builtin | package `{ target, version?, outdated?, updating? }` |
 * local `{ path }` | sdk — so it is decoded as a [JsonObject] and read
 * through `pluginSourceLabel` instead of a sealed hierarchy: the engine
 * gains source kinds without notice. `state` is
 * `{ status: "active" }` or `{ status: "failed", error, ref? }`.
 */
@Serializable
data class PluginInfo(
    val id: String? = null,
    val source: JsonObject = JsonObject(emptyMap()),
    val features: PluginFeatures = PluginFeatures(),
    val state: PluginState = PluginState(),
)

@Serializable
data class PluginFeatures(
    val server: Boolean? = null,
    val tui: Boolean? = null,
    val rpc: Boolean? = null,
    val gui: Boolean? = null,
)

@Serializable
data class PluginState(
    val status: String = "",
    val error: String? = null,
    val ref: String? = null,
)

@Serializable
data class PluginsResponse(
    val data: List<PluginInfo> = emptyList(),
    val location: LocationRef? = null,
)

/**
 * One project known to the engine (`GET /api/project`).
 *
 * Shape verified against the engine fork source at pin 92b2ca0
 * (`packages/schema/src/project.ts`): `Project.Info` is
 * `{ id, canonical, vcs?, name?, icon?, commands?, time, sandboxes }`.
 * Only the fields the UI shows are modelled; the rest are dropped by
 * `ignoreUnknownKeys`.
 */
@Serializable
data class ProjectInfo(
    val id: String,
    val canonical: String,
    val name: String? = null,
    val vcs: String? = null,
)

/**
 * One saved worktree (`GET /api/worktree`).
 *
 * Shape verified against the engine fork source at pin 92b2ca0
 * (`packages/schema/src/worktree.ts`): the list answers
 * `Worktree.Directory` — `{ directory, strategy? }` — where `strategy`
 * is the discovery strategy that found it, when the engine records one.
 */
@Serializable
data class WorktreeDirectory(
    val directory: String,
    val strategy: String? = null,
)

/**
 * One configuration source (`GET /api/config`).
 *
 * Shape verified against the engine fork source at pin 92b2ca0
 * (`packages/schema/src/config.ts`): `Config.Entry` is a union of
 * `Document { type: "document", path?, info: Config.Info }` and
 * `Directory { type: "directory", path }`. `Config.Info` has ~30 fields;
 * only the ones the screen shows are modelled, the rest are dropped by
 * `ignoreUnknownKeys`. `model` is a `ConfigModel.Selection`, which may be
 * a plain string or an object, so it is decoded as a [JsonElement] and
 * read through `configModelLabel`.
 */
@Serializable
data class ConfigEntry(
    val type: String = "",
    val path: String? = null,
    val info: ConfigInfoSummary? = null,
)

@Serializable
data class ConfigInfoSummary(
    val model: JsonElement? = null,
    val default_agent: String? = null,
    val shell: String? = null,
    val timezone: String? = null,
    val username: String? = null,
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
