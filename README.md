<div align="center">

<img src="logo/icon%20logo.png" alt="OpenCode Android" width="132" />

# OpenCode Android

**The real [OpenCode](https://github.com/anomalyco/opencode) AI coding agent, running on your phone.**<br/>
Not a remote control. Not a WebView. Not a chatbot mockup.

[![CI](https://github.com/BIDZZ-RAWR/OPENCODE-ANDROID/actions/workflows/ci.yml/badge.svg)](https://github.com/BIDZZ-RAWR/OPENCODE-ANDROID/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/BIDZZ-RAWR/OPENCODE-ANDROID?include_prereleases&label=release)](https://github.com/BIDZZ-RAWR/OPENCODE-ANDROID/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-b3f834)](LICENSE)
![Android 10+](https://img.shields.io/badge/Android-10%2B-b3f834)
![ABIs](https://img.shields.io/badge/ABI-arm64--v8a%20%7C%20x86__64-b3f834)

[Download](https://github.com/BIDZZ-RAWR/OPENCODE-ANDROID/releases) ·
[Features](#features) ·
[How it works](#how-it-works) ·
[Build](#building-from-source) ·
[Bahasa Indonesia](#bahasa-indonesia)

</div>

> **Independent project.** Not built, endorsed, or supported by the OpenCode team.
> "OpenCode" is used descriptively to identify the upstream project this port is based on.

---

## What is this?

OpenCode is an open source AI coding agent: you describe a task, it reads your project, runs
tools, edits files, and reports back. This project runs **the actual upstream engine** (the
TypeScript/Bun server) as a child process inside the Android app, and wraps it in a native
Jetpack Compose interface.

Everything happens on the device. Your code stays in your project folder, the engine listens only
on `127.0.0.1`, and the only network traffic is the call to the model provider **you** configure.

## Features

| | |
|---|---|
| **Chat that streams** | Replies appear token by token, with Markdown, tables, and code blocks. Copy a block, or save it straight to a file. |
| **See what the agent does** | Tool calls show as a readable timeline ("Reading index.html"), live while it runs and in history afterwards, with failures marked. |
| **You stay in control** | Permission prompts arrive in the chat: *Allow once*, *Always*, or *Reject*. |
| **Bring your own provider** | Add an API key for a built-in provider, or define a custom OpenAI-compatible endpoint from inside the app. Replace or delete keys at any time. |
| **Modes, models, commands** | Switch mode and model from the composer, and use `/` commands with autocomplete. |
| **Real project folders** | Point the app at any folder on shared storage; the agent works on real POSIX paths. Switch projects and the engine restarts for you. |
| **Files and diffs** | Browse the project, read and edit files with syntax highlighting, and review real `git` diffs with A/M/D badges. |
| **Search that works** | `git` and `ripgrep` are bundled, so the agent's `grep` and `glob` tools function. |
| **Session history** | Reopen any past conversation, for this project or all of them. |
| **Survives the swipe** | A foreground service keeps the engine alive when you leave the app or swipe it from recents. |
| **Two languages, two themes** | English and Indonesian, switchable at runtime without leaving the screen. Dark and light themes. |
| **Built-in diagnostics** | Engine state, log, runtime paths, and `git`/`rg` self-checks, so release builds can be debugged without `adb`. |

### Not yet

MCP servers, an interactive terminal, LSP, session fork/revert, `git` over https (`clone`/`push`/
`fetch`), and adaptive tablet layouts. See [`docs/BUILD.md`](docs/BUILD.md#known-gaps) for the
current list of known gaps.

## Screenshots

<table>
  <tr>
    <td align="center"><img src="logo/screenshots/01-chat.webp" width="270" alt="Empty chat screen showing the Ready status and four suggested prompts" /><br/><sub><b>Chat</b> — one composer, model and mode in reach</sub></td>
    <td align="center"><img src="logo/screenshots/02-files.webp" width="270" alt="File explorer listing a project folder with directories first" /><br/><sub><b>Files</b> — the real project tree, folders first</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="logo/screenshots/03-file.webp" width="270" alt="File viewer showing Python source with syntax highlighting" /><br/><sub><b>Source</b> — read files with syntax highlighting</sub></td>
    <td align="center"><img src="logo/screenshots/04-project.webp" width="270" alt="Project screen showing the current folder and recently used folders" /><br/><sub><b>Project</b> — any folder, recent ones remembered</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="logo/screenshots/05-settings.webp" width="270" alt="Settings screen with project, language, provider and engine sections" /><br/><sub><b>Settings</b> — project, language, provider, engine</sub></td>
    <td align="center"><img src="logo/screenshots/06-diagnostics.webp" width="270" alt="Diagnostics screen reporting engine state, git and ripgrep versions and runtime paths" /><br/><sub><b>Diagnostics</b> — state, <code>git</code>/<code>rg</code> checks, paths</sub></td>
  </tr>
</table>

Captured from the app running on a device, dark theme. A conversation screenshot is deliberately
missing: every screen above needs no API key, and a transcript would have to be staged to look real.

## Install

1. Open the [**Releases**](https://github.com/BIDZZ-RAWR/OPENCODE-ANDROID/releases) page.
2. Download the APK for your device:

   | APK | For |
   |---|---|
   | `app-arm64-v8a-release.apk` | almost every phone made in the last decade |
   | `app-x86_64-release.apk` | emulators and x86 Chromebooks |

3. Install it, open the app, and grant **All-files access** when asked (see below).
4. In **Settings → Connect a provider**, add an API key. Without one the agent is limited to
   OpenCode's free tier.

APKs are large (about 99 MB for arm64) because the engine is a self-contained binary.

### What your phone needs

| | Requirement | Why |
|---|---|---|
| **Android** | 10 (API 29) or newer | `minSdk` 29. Older releases are not supported. |
| **CPU** | **arm64-v8a** | The arm64 APK is the one for phones. `x86_64` exists for emulators and x86 Chromebooks. |
| **32-bit phones** | **Not supported** | There is no `armeabi-v7a` build, so a 32-bit-only device cannot install this at all. |
| **Free space** | **About 350 MB** | 98.5 MB APK plus 225.8 MB of native libraries the installer extracts. The installer needs temporary space on top of that. |
| **RAM** | **Not yet measured** | The engine is a 217 MB binary running a JIT runtime. Small-RAM phones are the likely cause of crashes, but we have no number yet, so we will not invent one. |
| **Storage access** | All-files access | Required only if you want the agent to work on folders outside the app. |

Android 15+ devices with 16 KB memory pages work: every bundled binary is 16 KB aligned.

> **If the install fails,** the usual causes are the wrong APK (`x86_64` instead of `arm64-v8a`), not enough free space, or a custom ROM blocking the package installer. The exact error message is what identifies which one it is — please include it in a bug report.

> **Why not Google Play or F-Droid?** The engine needs real filesystem paths for your project
> folder, which requires `MANAGE_EXTERNAL_STORAGE`. Neither store accepts that permission for this
> kind of app. Install from GitHub Releases, or follow the repo with
> [Obtainium](https://github.com/ImranR98/Obtainium) for updates.

## Permissions and privacy

| Permission | Why |
|---|---|
| `INTERNET` | Reach the model provider you configured, and nothing else. |
| `MANAGE_EXTERNAL_STORAGE` | Give the engine real paths to project folders outside the app. Optional: without it you can still use the app-private workspace. |
| Foreground service (`specialUse`) | Keep the engine running while you are away. Shows one low-priority notification. |
| `POST_NOTIFICATIONS` | Show that notification. The app works if you decline. |

- The engine binds to `127.0.0.1` on a random port, protected by a per-run password.
- Cleartext HTTP is allowed for loopback only.
- Provider keys live in the engine's app-private storage, protected by the Android sandbox and not
  encrypted beyond it. There is no telemetry and no crash reporting.

## How it works

<img src="logo/Arsitektur%20Proses%20Aplikasi%20OpenCode%20Android.webp"
     alt="Android app process: Jetpack Compose UI → core:api → HTTP + SSE on 127.0.0.1 → OpenCode engine (Bun, compiled to android-arm64, plus git and ripgrep), supervised by core:engine ProcessManager through the foreground EngineService"
     width="900" />

- **Engine.** Upstream `@opencode/*` **v2.2.2**, cross-compiled with Bun for
  `linux-arm64-android` and `linux-x64-android`, with a small set of documented
  [patches](engine/patches/README.md). It ships as `libopencode.so` in the APK's native library
  directory, the one place Android lets an app execute code from.
- **Supervisor.** `ProcessManager` runs the engine through an explicit state machine
  (`STOPPED → INITIALIZING → STARTING → RUNNING`, with `DEGRADED`, `FAILED`, and `STOPPING`),
  checks its health, and restarts it on demand.
- **Tools.** `git` 2.56.0 and `ripgrep` 15.1.0 are built with NDK 29 and are 16 KB page aligned,
  so they run on Android 15+ devices too.
- **UI.** Plain Compose with its own small design system (`core:design`) and hand-drawn icons.
  No navigation library and no icon pack.

| Module | Role |
|---|---|
| `app` | Compose UI, foreground service, workspace and language stores |
| `core:engine` | Engine process supervisor and environment |
| `core:api` | Typed HTTP client, SSE event stream, DTOs |
| `core:design` | Colour, type, spacing, and theme tokens |
| `core:common` | Shared utilities |

## Building from source

The short version, for the app only (engine and tool binaries already staged):

```powershell
cd android
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-arm64-v8a-debug.apk
```

Building the engine and the `git`/`rg` binaries from scratch needs Bun, the Android NDK 29, and
Rust. The complete walkthrough, including release signing, is in
[**docs/BUILD.md**](docs/BUILD.md). The full upstream HTTP surface is documented in
[`docs/API-REFERENCE.md`](docs/API-REFERENCE.md).

```
android/        Gradle project (app + core modules)
engine/         Android patches for the upstream engine, with notes
script/android/ Engine staging and tool build scripts
docs/           Build guide and API reference
```

## About the developer

Made by **Muhammad Abid** (*Bidzz*), a student exploring web development, OSINT, digital security,
and reverse engineering.

[Portfolio](https://bidzzofc.my.id/) ·
[GitHub](https://github.com/BIDZZ-RAWR) ·
[Telegram](https://t.me/bidzz7) ·
[Instagram](https://instagram.com/muhammad.abid857)

The same details are inside the app under **Settings → About the developer**.

## Contributing

Issues and pull requests are welcome. For a bug report, include your Android version, device, ABI,
and the output of **Settings → Diagnostics**, which covers the engine log and the `git`/`rg`
checks. Please open an issue before starting on a large change.

## License

The app is [MIT](LICENSE). Upstream copyright (c) 2025 opencode is retained.

Bundled third-party binaries carry their own licenses, listed in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md): **git** (GPL-2.0, run as a separate
executable, source published with each release) and **ripgrep** (MIT).

## Credits

- [OpenCode](https://github.com/anomalyco/opencode), the engine this app is built around.
- [Bun](https://bun.sh), for compiling the engine to a single Android binary.
- [Git](https://git-scm.com/) and [ripgrep](https://github.com/BurntSushi/ripgrep).

---

## Bahasa Indonesia

**OpenCode Android** menjalankan engine agen coding [OpenCode](https://github.com/anomalyco/opencode)
yang asli langsung di dalam aplikasi Android, dengan antarmuka Jetpack Compose. Bukan remote
control desktop, bukan WebView.

- Chat streaming dengan Markdown dan blok kode (salin atau simpan ke file).
- Jejak tool call yang terbaca, dan kartu izin: *Izinkan sekali*, *Selalu*, atau *Tolak*.
- Hubungkan penyedia sendiri: kunci API atau endpoint kustom, langsung dari aplikasi.
- Pilih folder proyek di penyimpanan bersama, jelajahi dan edit file, lihat diff git.
- `git` dan `ripgrep` sudah dibundel. Engine tetap hidup walau aplikasi di-swipe.
- Bahasa Indonesia dan Inggris, tema gelap dan terang.

**Pasang:** unduh APK dari halaman [Releases](https://github.com/BIDZZ-RAWR/OPENCODE-ANDROID/releases)
(`arm64-v8a` untuk hampir semua HP), pasang, lalu berikan *Akses semua file*. Butuh Android 10
atau lebih baru. Tidak tersedia di Google Play atau F-Droid karena izin akses semua file.

Data tetap di perangkat: engine hanya mendengarkan di `127.0.0.1`, dan satu-satunya lalu lintas
jaringan adalah ke penyedia model yang kamu atur sendiri.
