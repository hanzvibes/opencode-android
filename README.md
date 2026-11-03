# OpenCode Android

Native Android port of [OpenCode](https://github.com/anomalyco/opencode) — the open source AI coding agent.

> **Independent project.** Not built, endorsed, or supported by the OpenCode team.
> "OpenCode" is used descriptively to identify the upstream project this port is based on.

## Status

Working app: the upstream engine runs on-device with a Compose UI (sessions, streaming chat,
providers, files, diffs, diagnostics). Not yet available: MCP, terminal, session fork/revert,
git over https. APKs are published on GitHub Releases; see `handoff.md` for detailed status.

## Goal

Run the real OpenCode engine locally on an Android device — not a remote control for a desktop,
not a WebView wrapper, not a chatbot mockup. The upstream TypeScript/Bun engine is preserved;
Kotlin is the Android host.

- **UI** — Jetpack Compose, Material 3, native navigation for phone / tablet / foldable
- **Engine** — upstream `@opencode/*` v2.2.2, unmodified except documented Android patches
- **Transport** — HTTP + SSE over loopback (`127.0.0.1`), paired via upstream `/api/pair`
- **Target** — Android 10+ (API 29), arm64-v8a and x86_64

## Distribution

Releases will be published as APK on GitHub Releases only.

The app requires `MANAGE_EXTERNAL_STORAGE` so the engine receives real POSIX paths for the
user's project directory. That permission is not accepted by Google Play or F-Droid, so neither
store is a distribution channel for this project. Install via GitHub Releases or Obtainium.

## Requirements

- Android 10 or newer, arm64-v8a
- Network access for cloud model providers
- Provider credentials (Anthropic, OpenAI, OpenRouter, or any compatible endpoint)

## Building

See [docs/BUILD.md](docs/BUILD.md).

## License

MIT — see [LICENSE](LICENSE). Upstream copyright (c) 2025 opencode is retained.
Third-party binaries bundled with the app (git, ripgrep) carry their own licenses;
`THIRD_PARTY_NOTICES.md` will list them at first release.
