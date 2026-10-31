# OpenCode Engine API Reference

Generated from `packages/protocol/openapi.json` (OpenAPI 3.1.0, 134 paths, 351 schemas).

**Not generated into Kotlin on purpose.** 77 schemas use `anyOf` without a discriminator,
which generators turn into `JsonElement`; 63 schemas are named `*Encoded` and describe
Effect's internal serialisation rather than a contract worth committing to. The client in
`core:api` models only the shapes verified against a live server.

**Auth is not in the spec.** `securitySchemes` is empty and every operation has `security: []`.
The engine requires `Authorization: Basic base64("opencode:<password>")`, where the password is
printed to stdout at startup.

## Endpoints

| Method | Path | Summary |
|---|---|---|
| GET | `/api/info` | Get server info |
| POST | `/api/pair` | Create pairing code |
| GET | `/auth/connect/{code}` | Redeem pairing code |
| GET | `/api/location` | Get location |
| POST | `/api/location/reload` | Reload configuration |
| GET | `/api/agent` | List agents |
| GET | `/api/agent/{agentID}` | Get agent |
| GET | `/api/plugin` | List plugins |
| GET | `/api/plugin/gui` | Read plugin GUI bundle |
| POST | `/api/plugin/check` | Check plugin updates |
| POST | `/api/plugin/update` | Update plugins |
| GET | `/api/session` | List sessions |
| POST | `/api/session` | Create session |
| GET | `/api/experimental/session/stats` | Get session statistics |
| POST | `/api/experimental/session/import` | Import session |
| GET | `/api/experimental/session/{sessionID}/export` | Export session |
| GET | `/api/session/active` | List active sessions |
| GET | `/api/session/{sessionID}` | Get session |
| DELETE | `/api/session/{sessionID}` | Delete session |
| PATCH | `/api/session/{sessionID}` | Update session |
| POST | `/api/session/{sessionID}/fork` | Fork session |
| POST | `/api/session/{sessionID}/agent` | Switch session agent |
| POST | `/api/session/{sessionID}/model` | Switch session model |
| POST | `/api/session/{sessionID}/move` | Move session |
| POST | `/api/session/{sessionID}/prompt` | Send message |
| POST | `/api/session/{sessionID}/command` | Run command |
| POST | `/api/experimental/session/{sessionID}/skill` | Activate skill |
| POST | `/api/session/{sessionID}/synthetic` | Add synthetic message |
| POST | `/api/session/{sessionID}/shell` | Run shell command |
| POST | `/api/session/{sessionID}/compact` | Compact session |
| POST | `/api/experimental/session/{sessionID}/wait` | Wait for session |
| POST | `/api/session/{sessionID}/revert/stage` | Stage session revert |
| DELETE | `/api/session/{sessionID}/revert` | Clear staged revert |
| POST | `/api/session/{sessionID}/revert/commit` | Commit staged revert |
| GET | `/api/session/{sessionID}/context` | Get session context |
| GET | `/api/session/{sessionID}/diff` | Diff session turns |
| GET | `/api/session/{sessionID}/inbox` | List session inbox |
| DELETE | `/api/session/{sessionID}/inbox/{inboxID}` | Cancel inbox input |
| PATCH | `/api/session/{sessionID}/inbox/{inboxID}` | Update inbox item |
| GET | `/api/experimental/session/{sessionID}/instructions/entries` | List instruction entries |
| PUT | `/api/experimental/session/{sessionID}/instructions/entries/{key}` | Put instruction entry |
| DELETE | `/api/experimental/session/{sessionID}/instructions/entries/{key}` | Remove instruction entry |
| POST | `/api/session/{sessionID}/generate` | Generate text from session context |
| GET | `/api/experimental/session/{sessionID}/log` | Read the session log |
| POST | `/api/session/{sessionID}/interrupt` | Interrupt session execution |
| POST | `/api/session/{sessionID}/background` | Background blocking session tools |
| GET | `/api/session/{sessionID}/message/{messageID}` | Get session message |
| GET | `/api/session/{sessionID}/form` | List session forms |
| POST | `/api/session/{sessionID}/form` | Create session form |
| GET | `/api/session/{sessionID}/form/{formID}` | Get session form |
| DELETE | `/api/session/{sessionID}/form/{formID}` | Cancel form |
| POST | `/api/session/{sessionID}/form/{formID}/reply` | Reply to form |
| PUT | `/api/session/{sessionID}/environment` | Set session environment |
| POST | `/api/session/{sessionID}/view` | View session |
| GET | `/api/session/{sessionID}/message` | Get session messages |
| GET | `/api/model` | List models |
| GET | `/api/model/default` | Get default model |
| POST | `/api/experimental/generate/text` | Generate text |
| POST | `/api/experimental/generate` | Generate text (deprecated path) |
| POST | `/api/experimental/generate/image` | Generate images |
| POST | `/api/experimental/generate/decisions` | Generate decisions |
| GET | `/api/experimental/voice/models` | List voice models |
| POST | `/api/experimental/voice/session` | Create voice session |
| GET | `/api/provider` | List providers |
| GET | `/api/provider/{providerID}` | Get provider |
| GET | `/api/integration` | List integrations |
| GET | `/api/integration/{integrationID}` | Get integration |
| POST | `/api/experimental/integration/wellknown` | Add wellknown integration |
| POST | `/api/integration/{integrationID}/connect/key` | Connect with key |
| POST | `/api/integration/{integrationID}/connect/external` | Connect with external credentials |
| POST | `/api/integration/{integrationID}/connect/oauth` | Begin OAuth connection |
| GET | `/api/integration/{integrationID}/connect/oauth/{attemptID}` | Get OAuth attempt status |
| DELETE | `/api/integration/{integrationID}/connect/oauth/{attemptID}` | Cancel OAuth connection |
| POST | `/api/integration/{integrationID}/connect/oauth/{attemptID}/complete` | Complete OAuth connection |
| POST | `/api/integration/{integrationID}/connect/command` | Begin command connection |
| GET | `/api/integration/{integrationID}/connect/command/{attemptID}` | Get command attempt status |
| DELETE | `/api/integration/{integrationID}/connect/command/{attemptID}` | Cancel command connection |
| GET | `/api/mcp` | List MCP servers |
| PUT | `/api/experimental/mcp/{server}` | Add MCP server |
| DELETE | `/api/experimental/mcp/{server}` | Remove MCP server |
| POST | `/api/experimental/mcp/{server}/connect` | Connect MCP server |
| POST | `/api/experimental/mcp/{server}/disconnect` | Disconnect MCP server |
| GET | `/api/mcp/resource` | List MCP resources |
| POST | `/api/experimental/client-mcp/token` | Create client MCP WebSocket token |
| GET | `/api/experimental/client-mcp` | Attach client MCP server |
| GET | `/api/credential` | List credentials |
| POST | `/api/credential` | Create credential |
| PATCH | `/api/credential/{credentialID}` | Update credential |
| DELETE | `/api/credential/{credentialID}` | Remove credential |
| POST | `/api/credential/{credentialID}/activate` | Activate credential |
| GET | `/api/project` | List projects |
| PATCH | `/api/project/{projectID}` | Update project |
| GET | `/api/form` | List pending forms |
| GET | `/api/permission/request` | List pending permission requests |
| GET | `/api/permission/saved` | List saved permissions |
| PATCH | `/api/permission/saved/{id}` | Update saved permission |
| DELETE | `/api/permission/saved/{id}` | Remove saved permission |
| POST | `/api/session/{sessionID}/permission` | Create permission request |
| GET | `/api/session/{sessionID}/permission` | List session permission requests |
| GET | `/api/session/{sessionID}/permission/{requestID}` | Get permission request |
| POST | `/api/session/{sessionID}/permission/{requestID}/reply` | Reply to pending permission request |
| GET | `/api/fs/read/*` | Read file |
| GET | `/api/fs/list` | List directory |
| GET | `/api/fs/find` | Find files |
| POST | `/api/experimental/fs/write` | Write file |
| POST | `/api/experimental/fs/watch` | Watch file |
| DELETE | `/api/experimental/fs/watch` | Unwatch file |
| GET | `/api/command` | List commands |
| GET | `/api/skill` | List skills |
| POST | `/api/rpc/{rpcID}/{method}` | Call a plugin RPC |
| GET | `/api/event` | Subscribe to events |
| GET | `/api/pty` | List PTY sessions |
| POST | `/api/pty` | Create PTY session |
| GET | `/api/pty/{ptyID}` | Get PTY session |
| PUT | `/api/pty/{ptyID}` | Update PTY session |
| DELETE | `/api/pty/{ptyID}` | Remove PTY session |
| POST | `/api/pty/{ptyID}/connect-token` | Create PTY WebSocket token |
| GET | `/api/pty/{ptyID}/connect` | Connect to PTY session |
| GET | `/api/experimental/session/{sessionID}/terminal/read` | Read the session's most recently controlled terminal |
| GET | `/api/experimental/session/{sessionID}/terminal` |  |
| POST | `/api/experimental/session/{sessionID}/terminal` |  |
| POST | `/api/experimental/persistent-pty/shutdown` |  |
| POST | `/api/experimental/persistent-pty/handoff` |  |
| GET | `/api/experimental/persistent-pty/{ptyID}` |  |
| PUT | `/api/experimental/persistent-pty/{ptyID}` |  |
| DELETE | `/api/experimental/persistent-pty/{ptyID}` |  |
| GET | `/api/experimental/persistent-pty/{ptyID}/snapshot` |  |
| POST | `/api/experimental/persistent-pty/{ptyID}/connect-token` |  |
| GET | `/api/experimental/persistent-pty/{ptyID}/connect` | Connect to a persistent PTY |
| GET | `/api/experimental/media/{hash}` | Read media blob |
| POST | `/api/experimental/schedule` | Create schedule |
| GET | `/api/experimental/schedule` | List schedules |
| GET | `/api/experimental/schedule/{scheduleID}` | Get schedule |
| DELETE | `/api/experimental/schedule/{scheduleID}` | Delete schedule |
| POST | `/api/experimental/schedule/{scheduleID}/pause` | Pause schedule |
| POST | `/api/experimental/schedule/{scheduleID}/resume` | Resume schedule |
| GET | `/api/shell` | List running shell commands |
| POST | `/api/shell` | Run shell command |
| GET | `/api/shell/{id}` | Get shell command |
| DELETE | `/api/shell/{id}` | Remove shell command |
| GET | `/api/shell/{id}/output` | Read shell output |
| GET | `/api/reference` | List references |
| GET | `/api/worktree` | List worktrees |
| POST | `/api/worktree` | Create worktree |
| DELETE | `/api/worktree` | Remove worktree |
| POST | `/api/worktree/refresh` | Refresh worktrees |
| POST | `/api/vcs/init` | Initialize VCS repository |
| GET | `/api/vcs` | VCS info |
| GET | `/api/vcs/base` | VCS review base |
| GET | `/api/vcs/status` | VCS status |
| GET | `/api/vcs/branch` | VCS branches |
| GET | `/api/vcs/diff` | VCS diff |
| POST | `/api/debug/heap-dump` | Write a heap snapshot |
| GET | `/api/debug/location` | List loaded locations |
| DELETE | `/api/debug/location` | Evict a loaded location |
| GET | `/api/experimental/migration/v1` | Get V1 migration status |
| GET | `/api/websearch/provider` | List web search providers |
| POST | `/api/websearch` | Search the web |
| GET | `/api/experimental/usage/limits` | Get usage limits |
| GET | `/api/config` | Get configuration |
| GET | `/api/config/shell` | List available shells |
| PATCH | `/api/experimental/config` | Update global configuration |

## Schemas

| Name | Type | Properties |
|---|---|---|
| `AbsolutePath` | string |  |
| `Agent.Color` | string |  |
| `Agent.ID` | string |  |
| `Agent.Info` | object | id, name, model, request, system, description, mode, hidden, color, steps, permissions |
| `AgentNotFoundErrorEncoded` | object | _tag, agentID, message |
| `Command.Info` | object | name, description |
| `CommandExecutionErrorEncoded` | object | _tag, command, message |
| `CommandNotFoundErrorEncoded` | object | _tag, command, message |
| `Config.AgentEncoded` | object | model, request, system, description, mode, hidden, color, steps, disabled, permissions |
| `Config.CommandEncoded` | object | template, description, agent, model, subagent, subtask |
| `Config.DirectoryEncoded` | object | type, path |
| `Config.DocumentEncoded` | object | type, path, info |
| `Config.Entry` | anyOf |  |
| `Config.Formatter.EntryEncoded` | object | disabled, command, environment, extensions |
| `Config.InfoEncoded` | object | $schema, shell, model, default_agent, update, share, enterprise, username, timezone, permissions, policies, agents, snapshots, watcher, formatter, lsp, media, tool_output, mcp, compaction, skills, commands, instructions, references, websearch, plugins, worktree, warming, providers, experimental |
| `Config.LSP.ServerEncoded` | object | command, extensions, disabled, env, initialization |
| `Config.Model.Capabilities` | object | tools, input, output, temperature, reasoning |
| `Config.Model.CostEncoded` | object | tier, input, output, cache |
| `Config.Model.Reasoning` | anyOf |  |
| `Config.Model.Settings` | object | compaction, cache, encoding |
| `Config.Model.VariantSettings` | object | compaction, encoding |
| `Config.ModelEncoded` | object | modelID, type, family, name, releaseDate, compatibility, package, settings, headers, body, capabilities, variants, cost, disabled, limit |
| `Config.Patch` | object | shell |
| `Config.Plugin.EntryEncoded` | object | package, options |
| `Config.Provider.Settings` | object | timeout, headerTimeout, chunkTimeout, compaction, cache, transport, encoding |
| `Config.ProviderEncoded` | object | canonical, name, env, package, compatibility, settings, headers, body, models |
| `Config.Reference.GitEncoded` | object | repository, branch, description, hidden |
| `Config.Reference.LocalEncoded` | object | path, description, hidden |
| `Config.WarmingEncoded` | object | prompt, interval, duration |
| `Config.Worktree` | object | directory |
| `ConfigShell.Option` | object | path, name, acceptable |
| `ConfigWebSearch.InfoEncoded` | object | provider |
| `ConflictErrorEncoded` | object | _tag, message, resource |
| `Connection.CredentialInfo` | object | type, id, label, method, status |
| `Connection.EnvInfo` | object | type, name, status |
| `Connection.Info` | anyOf |  |
| `Connection.Status` | object | status, message, url |
| `Credential.CreateInput` | object | id, integrationID, label, value, activate |
| `Credential.Entry` | object | id, integrationID, label, active, value |
| `Credential.External` | object | type, methodID, metadata |
| `Credential.ID` | string |  |
| `Credential.Key` | object | type, key, metadata |
| `Credential.OAuth` | object | type, methodID, refresh, access, expires, metadata |
| `Credential.Value` | anyOf |  |
| `Event.SeqEncoded` | string |  |
| `FileDiff.Info` | object | file, patch, additions, deletions, status, more |
| `FileNotFoundErrorEncoded` | object | _tag, path, message |
| `FileSystem.Entry` | object | path, type |
| `FileSystem.Write` | object | path |
| `ForbiddenErrorEncoded` | object | _tag, message |
| `Form.Answer` | object |  |
| `Form.BooleanField` | object | key, title, description, required, hidden, when, type, default |
| `Form.CreatePayload` | object | id, title, metadata, fields |
| `Form.Detail` | object | id, sessionID, title, metadata, fields, state |
| `Form.ExternalField` | object | key, type, url, title, description |
| `Form.Field` | anyOf |  |
| `Form.Fields` | array |  |
| `Form.ID` | string |  |
| `Form.Info` | object | id, sessionID, title, metadata, fields |
| `Form.IntegerField` | object | key, title, description, required, hidden, when, type, minimum, maximum, default |
| `Form.Metadata` | object |  |
| `Form.MultiselectField` | object | key, title, description, required, hidden, when, type, options, minItems, maxItems, custom, default |
| `Form.NumberField` | object | key, title, description, required, hidden, when, type, minimum, maximum, default |
| `Form.Option` | object | value, label, description |
| `Form.Reply` | object | answer |
| `Form.State` | anyOf |  |
| `Form.StringField` | object | key, title, description, required, hidden, when, type, format, minLength, maxLength, pattern, placeholder, default, options, custom |
| `Form.Value` | anyOf |  |
| `Form.When` | object | key, op, value |
| `FormAlreadySettledErrorEncoded` | object | _tag, id, message |
| `FormInvalidAnswerErrorEncoded` | object | _tag, id, message |
| `FormNotFoundErrorEncoded` | object | _tag, id, message |
| `Generate.ChoiceAnswer` | object | type, name, choice, probabilities, confidence |
| `Generate.ChoiceQuestion` | object | type, name, instructions, choices |
| `Generate.ChoiceValue` | anyOf |  |
| `Generate.DecisionAnswer` | anyOf |  |
| `Generate.DecisionFilePart` | object | type, data, mime, name |
| `Generate.DecisionJsonPart` | object | type, value |
| `Generate.DecisionPart` | anyOf |  |
| `Generate.DecisionQuestion` | anyOf |  |
| `Generate.DecisionRequest` | object | model, input, questions |
| `Generate.DecisionResult` | object | answers, cost, tokens, native |
| `Generate.DecisionTextPart` | object | type, text |
| `Generate.ErrorAnswer` | object | type, name, reason, message |
| `Generate.File` | object | data, mime, name |
| `Generate.ImageAspect` | string |  |
| `Generate.ImageOutput` | object | type, data, mime, name |
| `Generate.ImageRequest` | object | model, prompt, images, n, aspect, resolution |
| `Generate.ImageResolution` | anyOf |  |
| `Generate.ImageResult` | object | images, cost, tokens, native |
| `Generate.Native` | object |  |
| `Generate.PredicateAnswer` | object | type, name, probability |
| `Generate.PredicateQuestion` | object | type, name, instructions |
| `Generate.ScoreAnswer` | object | type, name, score, probabilities, confidence |
| `Generate.ScoreQuestion` | object | type, name, instructions, levels |
| `Generate.Usage` | object | input, output |
| `GenerateDecisionsResponse` | object | data |
| `GenerateImageResponse` | object | data |
| `GenerateTextResponse` | object | data |
| `InstructionEntry.Info` | object | key, value |
| `InstructionEntry.Key` | string |  |
| `InstructionEntryValueTooLargeErrorEncoded` | object | _tag, actualBytes, maxBytes, message |
| `Integration.AttemptEncoded` | object | attemptID, url, instructions, mode, time |
| `Integration.AttemptID` | string |  |
| `Integration.AttemptStatus` | anyOf |  |
| `Integration.CommandAttempt` | object | attemptID, time |
| `Integration.CommandAttemptStatus` | anyOf |  |
| `Integration.CommandMethod` | object | id, type, label, command |
| `Integration.EnvMethod` | object | type, names |
| `Integration.ExternalMethod` | object | id, type, label, form |
| `Integration.ID` | string |  |
| `Integration.Info` | object | id, name, metadata, methods, connections |
| `Integration.KeyMethod` | object | type, label, form |
| `Integration.Method` | anyOf |  |
| `Integration.MethodID` | string |  |
| `Integration.OAuthMethod` | object | id, type, label, form |
| `IntegrationAttemptNotFoundErrorEncoded` | object | _tag, integrationID, attemptID, message |
| `IntegrationMethodNotFoundErrorEncoded` | object | _tag, integrationID, methodID, message |
| `IntegrationNotFoundErrorEncoded` | object | _tag, integrationID, message |
| `InvalidCursorErrorEncoded` | object | _tag, message |
| `InvalidRequestErrorEncoded` | object | _tag, message, kind, field |
| `Location.PublicInfo` | object | directory, project |
| `Location.PublicRef` | object | directory |
| `LocationNotFoundErrorEncoded` | object | _tag, location, message |
| `Mcp.LocalConfigEncoded` | object | type, command, cwd, environment, disabled, codemode, timeout, protocol |
| `Mcp.OAuthConfigEncoded` | object | client_id, client_secret, scope, callback_port, redirect_uri, auth_server_metadata_url |
| `Mcp.Protocol` | string |  |
| `Mcp.RemoteConfigEncoded` | object | type, url, headers, oauth, disabled, codemode, timeout, protocol |
| `Mcp.Resource` | object | server, name, uri, description, mimeType |
| `Mcp.ResourceCatalog` | object | resources, templates |
| `Mcp.ResourceTemplate` | object | server, name, uriTemplate, description, mimeType |
| `Mcp.Server` | object | name, status, title, integrationID |
| `Mcp.Status.Connected` | object | status |
| `Mcp.Status.Disabled` | object | status |
| `Mcp.Status.Failed` | object | status, error |
| `Mcp.Status.NeedsAuth` | object | status, error |
| `Mcp.Status.Pending` | object | status |
| `McpServerNotFoundErrorEncoded` | object | _tag, server, message |
| `MediaHash` | string |  |
| `MessageNotFoundErrorEncoded` | object | _tag, sessionID, messageID, message |
| `Model.CacheFormat` | string |  |
| `Model.Capabilities` | object | tools, input, output, temperature |
| `Model.Compatibility` | object | reasoningField, requireReasoning, maxTokensField, requireFinishReason, requireAssistantAfterTool, supportsPromptCacheKey, streamUsage, cacheFormat, supportsThinkingBlockBinding, supportsEffortUpdates, mediaInput |
| `Model.Cost` | object | tier, input, output, cache |
| `Model.Info` | object | id, modelID, providerID, canonical, type, family, name, compatibility, package, settings, headers, body, capabilities, variants, time, cost, status, enabled, limit |
| `Model.MaxTokensField` | string |  |
| `Model.MediaInput` | string |  |
| `Model.ReasoningField` | anyOf |  |
| `Model.Ref` | object | id, providerID, variant |
| `Model.Settings` | object | compaction, cache, encoding |
| `Model.Type` | anyOf |  |
| `Model.Variant` | object | id, settings, headers, body |
| `Model.VariantSettings` | object | compaction, encoding |
| `ModelNotFoundErrorEncoded` | object | _tag, providerID, modelID, message |
| `Money.USD` | number |  |
| `Money.USDPerMillionTokens` | number |  |
| `PairingCode` | object | code, expires_in |
| `PairingSession` | object | token |
| `Permission.Effect` | string |  |
| `Permission.ID` | string |  |
| `Permission.Reply` | string |  |
| `Permission.Request` | object | id, sessionID, action, resources, save, metadata, source, message |
| `Permission.Rule` | object | action, resource, effect |
| `Permission.Ruleset` | array |  |
| `Permission.Source` | anyOf |  |
| `PermissionNotFoundErrorEncoded` | object | _tag, requestID, message |
| `PermissionSaved.ID` | string |  |
| `PermissionSaved.Info` | object | id, projectID, action, resource, time |
| `PermissionSavedNotFoundErrorEncoded` | object | _tag, id, message |
| `PersistentPty.CreateInput` | object | command, args, cwd, title, env, size |
| `PersistentPty.Handoff` | object | directory, instanceID, ticket, expiresAt |
| `PersistentPty.Info` | object | id, title, command, args, cwd, status, pid, exitCode, sessionID, foregroundProcess, size, output |
| `PersistentPty.ReadLinesEncoded` | string |  |
| `PersistentPty.ReadResult` | object | ptyID, title, cwd, foregroundProcess, screen |
| `PersistentPty.Snapshot` | object | info, text, checkpoint, cursor |
| `PersistentPty.UpdateInput` | object | attachmentID, size |
| `Plugin.Features` | object | server, tui, rpc, gui |
| `Plugin.GuiBundle` | object | code, revision, options |
| `Plugin.ID` | string |  |
| `Plugin.Info` | object | id, source, features, state |
| `Plugin.Source` | anyOf |  |
| `Plugin.State` | anyOf |  |
| `PluginNotFoundErrorEncoded` | object | _tag, pluginID, message |
| `Project` | object | id, canonical, vcs, name, icon, commands, time, sandboxes |
| `Project.Commands` | object | start |
| `Project.ID` | string |  |
| `Project.Icon` | object | url, override, color |
| `Project.Time` | object | created, updated, active, archived |
| `Project.Vcs` | string |  |
| `ProjectNotFoundErrorEncoded` | object | _tag, projectID, message |
| `Prompt.AgentAttachment` | object | name, mention |
| `Prompt.Base64` | string |  |
| `Prompt.FileAttachment` | object | data, mime, source, name, description, mention |
| `Prompt.FileSource` | anyOf |  |
| `Prompt.Mention` | object | start, end, text |
| `Prompt.SkillAttachment` | object | id, name, text, mention |
| `PromptInput.FileAttachment` | object | uri, source, name, description, mention |
| `PromptInput.SkillAttachment` | object | id, mention |
| `Provider.Cache` | object | ttl |
| `Provider.CacheTTL` | string |  |
| `Provider.Compaction` | anyOf |  |
| `Provider.Compatibility` | object | reasoningField, requireReasoning, maxTokensField, requireFinishReason, requireAssistantAfterTool, supportsPromptCacheKey, streamUsage, cacheFormat, mediaInput |
| `Provider.Encoding` | string |  |
| `Provider.ID` | string |  |
| `Provider.Info` | object | id, canonical, integrationID, name, activation, package, compatibility, settings, headers, body |
| `Provider.Request` | object | settings, headers, body |
| `Provider.Settings` | object | timeout, headerTimeout, chunkTimeout, compaction, cache, transport, encoding |
| `Provider.Transport` | string |  |
| `ProviderNotFoundErrorEncoded` | object | _tag, providerID, message |
| `ProviderRequestErrorEncoded` | object | _tag, providerID, reason, message, status, retryAfterMs |
| `Pty` | object | id, title, command, args, cwd, status, pid, exitCode |
| `Pty.ID` | string |  |
| `PtyNotFoundErrorEncoded` | object | _tag, ptyID, message |
| `PtyTicket.ConnectToken` | object | ticket, expires_in |
| `Reference.GitSource` | object | type, repository, branch |
| `Reference.Info` | object | name, path, description, hidden, source |
| `Reference.LocalSource` | object | type, path |
| `Reference.Source` | anyOf |  |
| `RelativePath` | string |  |
| `Rpc.Input` | object | input |
| `Rpc.Output` | object | output |
| `RpcErrorEncoded` | object | _tag, type, message, data |
| `RpcInternalErrorEncoded` | object | _tag, type, message, data |
| `Schedule.Action` | anyOf |  |
| `Schedule.Action.Prompt` | object | type, prompt |
| `Schedule.Condition` | anyOf |  |
| `Schedule.Condition.Code` | object | type, code |
| `Schedule.Condition.Shell` | object | type, run, is, matches, changes |
| `Schedule.ConditionInput` | anyOf |  |
| `Schedule.CreatePayload` | object | sessionID, name, when, if, until, ends, prompt |
| `Schedule.Duration` | string |  |
| `Schedule.Ends` | object | after, at, times |
| `Schedule.ID` | string |  |
| `Schedule.Info` | object | id, sessionID, name, when, tz, if, until, action, status, nextDueAt, endsAt, remainingRuns, runCount, lastFiredAt, lastOutcome, lastOutput, time |
| `Schedule.Outcome` | string |  |
| `Schedule.Status` | string |  |
| `Schedule.When` | anyOf |  |
| `Schedule.When.At` | object | type, at, tz |
| `Schedule.When.Cron` | object | type, cron, tz |
| `Schedule.When.Every` | object | type, every, tz |
| `Schedule.When.In` | object | type, in, tz |
| `Schedule.WhenInput` | anyOf |  |
| `ScheduleNotFoundErrorEncoded` | object | _tag, scheduleID, message |
| `ServerInfo` | object | version, pid, hostname, urls, paths, capabilities |
| `ServiceUnavailableErrorEncoded` | object | _tag, message, service |
| `Session.ForkBoundary` | anyOf |  |
| `Session.ID` | string |  |
| `Session.Inbox.Compaction` | object | id, sessionID, time, type, payload, delivery |
| `Session.Inbox.CompactionPayload` | - |  |
| `Session.Inbox.Delivery` | string |  |
| `Session.Inbox.Info` | anyOf |  |
| `Session.Inbox.Move` | object | id, sessionID, time, type, delivery, payload |
| `Session.Inbox.MovePayload` | object | projectID, subpath, location |
| `Session.Inbox.Synthetic` | object | id, sessionID, time, type, payload, delivery |
| `Session.Inbox.SyntheticPayload` | object | text, description, metadata |
| `Session.Inbox.User` | object | id, sessionID, time, type, payload, delivery |
| `Session.Inbox.UserPayload` | object | text, files, agents, skills, metadata |
| `Session.Info` | object | id, parentID, fork, projectID, agent, model, cost, tokens, outcome, time, title, subpath, metadata, permissions, revert, location |
| `Session.Message.AgentSelected` | object | id, metadata, time, type, agent, previous |
| `Session.Message.Assistant` | object | id, metadata, time, type, agent, model, content, snapshot, finish, rawFinish, native, cost, tokens, error, retry |
| `Session.Message.Assistant.Reasoning` | object | type, text, native, time |
| `Session.Message.Assistant.Retry` | object | attempt, at, error |
| `Session.Message.Assistant.Text` | object | type, text, native |
| `Session.Message.Assistant.Tool` | object | type, id, name, executed, native, state, time |
| `Session.Message.Compaction` | anyOf |  |
| `Session.Message.Compaction.Completed` | object | type, id, metadata, time, status, reason, model, native, summary, cost, tokens |
| `Session.Message.Compaction.Failed` | object | type, id, metadata, time, status, reason, error, cost, tokens |
| `Session.Message.Compaction.Running` | object | type, id, metadata, time, status, reason, summary |
| `Session.Message.ID` | string |  |
| `Session.Message.Idle` | object | id, metadata, time, type, outcome, error |
| `Session.Message.Info` | anyOf |  |
| `Session.Message.LocationSwitched` | object | id, metadata, time, type, projectID, subpath, location, previous |
| `Session.Message.ModelSelected` | object | id, metadata, time, type, model, previous |
| `Session.Message.Native` | object |  |
| `Session.Message.Shell` | object | id, metadata, time, type, shellID, command, status, exit, output |
| `Session.Message.Skill` | object | id, metadata, time, type, skill, name, text |
| `Session.Message.Synthetic` | object | id, metadata, time, text, description, type |
| `Session.Message.System` | object | id, metadata, time, type, text, description |
| `Session.Message.ToolState.Completed` | object | status, input, content, metadata |
| `Session.Message.ToolState.Error` | object | status, input, error, content, metadata |
| `Session.Message.ToolState.Running` | object | status, input, metadata |
| `Session.Message.ToolState.Streaming` | object | status, input |
| `Session.Message.User` | object | id, metadata, time, text, files, agents, skills, type |
| `Session.Metadata` | object |  |
| `Session.Revert` | object | messageID, partID, snapshot, files |
| `Session.StructuredError` | object | type, message, status, response, retryAfterMs |
| `SessionActive` | object | type |
| `SessionBusyErrorEncoded` | object | _tag, sessionID, message |
| `SessionGenerateResponse` | object | data |
| `SessionInterruptResponse` | object | interrupted |
| `SessionLogItemEncoded` | string |  |
| `SessionMessagesResponse` | object | data, cursor |
| `SessionNotFoundErrorEncoded` | object | _tag, sessionID, message |
| `SessionStats.Activity` | object | date, steps |
| `SessionStats.Info` | object | range, sessions, subagents, prompts, steps, tokens, cost, tools, activeDays, streak, activity, models |
| `SessionStats.ModelUsage` | object | model, steps, tokens, cost |
| `SessionStats.ToolTotals` | object | calls, succeeded, failed, unfinished |
| `SessionStats.ToolUsage` | object | name, calls, succeeded, failed, unfinished, durationP50 |
| `SessionStats.Tools` | anyOf |  |
| `SessionTransfer.Data` | object | info, messages |
| `SessionsCursor` | string |  |
| `SessionsResponse` | object | data, cursor |
| `Shell.ID` | string |  |
| `Shell.Info` | object | id, status, command, cwd, shell, file, pid, exit, signal, metadata, time |
| `ShellNotFoundErrorEncoded` | object | _tag, id, message |
| `Skill.ID` | string |  |
| `Skill.Info` | object | id, name, description, autoinvoke, path, content |
| `Skill.Name` | string |  |
| `SkillNotFoundErrorEncoded` | object | _tag, skill, message |
| `Snapshot.ID` | string |  |
| `TokenUsage.Info` | object | input, output, reasoning, cache |
| `Tool.Content` | anyOf |  |
| `Tool.FileContent` | object | type, uri, mime, name |
| `Tool.TextContent` | object | type, text |
| `UnauthorizedErrorEncoded` | object | _tag, message |
| `UnknownErrorEncoded` | object | _tag, message, ref |
| `UnsupportedModelErrorEncoded` | object | _tag, providerID, modelID, capability, message |
| `UsageLimits.Budget` | object | id, limit, spent, scope, per, coverage, models, vendors, inputPriceAtLeast, picked, shared, resetsAt |
| `UsageLimits.BudgetModel` | object | providerID, modelID, name, spent |
| `UsageLimits.Go` | object | rolling, weekly, monthly |
| `UsageLimits.Info` | object | workspace, go, budgets |
| `UsageLimits.Meter` | object | percent, limited, resetsAt |
| `V2EventEncoded` | string |  |
| `Vcs.Base` | object | name, ref, source |
| `Vcs.Branch` | object | current, default |
| `Vcs.BranchList` | array |  |
| `Vcs.FileStatus` | object | file, additions, deletions, status |
| `Vcs.Info` | object | provider, branch |
| `Vcs.Mode` | string |  |
| `Vcs.Untracked` | string |  |
| `VcsInitNotSupportedErrorEncoded` | object | _tag, providerID, message |
| `VoiceModel` | object | model, provider, transports, available |
| `VoiceModelsResponse` | object | data |
| `VoiceSessionResponse` | object | data |
| `VoiceTransport` | anyOf |  |
| `VoiceTransportInput` | anyOf |  |
| `VoiceTransportType` | string |  |
| `VoiceWebRTCTransport` | object | type, sdp |
| `VoiceWebRTCTransportInput` | object | type, sdp, dcid |
| `VoiceWebSocketTransport` | object | type, url, token, expiresAt |
| `VoiceWebSocketTransportInput` | object | type |
| `WebSearch.ID` | string |  |
| `WebSearch.Provider` | object | id, name |
| `WebSearch.ResponseEncoded` | object | providerID, results |
| `WebSearch.Result` | object | url, title, content, time |
| `Worktree.CreateInput` | object | projectID, from, branch, directory, name |
| `Worktree.Directory` | object | directory, strategy |
| `Worktree.Info` | object | directory |
| `Worktree.List` | array |  |
| `Worktree.RemoveInput` | object | projectID, directory, force |
| `WorktreeErrorEncoded` | object | _tag, name, data |

