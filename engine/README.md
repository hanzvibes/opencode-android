# Engine

The OpenCode engine is **not** rewritten in Kotlin. The upstream TypeScript/Bun
engine is preserved and cross-compiled to Android.

```
engine/
├── patches/         Android patches applied to upstream source
└── README.md        this file
```

Upstream is never vendored into this repository. It is cloned at a pinned
commit into a gitignored cache directory and patched on demand.

## Pinned upstream

| Field | Value |
|---|---|
| Repository | https://github.com/anomalyco/opencode |
| Branch | `dev` |
| Commit | `fedc6f20` |
| Package version | `2.2.7` (`@opencode/core`, `server`, `cli`, `protocol`, `sdk`) |
| License | MIT |
| Dependency count | 1.777 packages (`bun install --ignore-scripts`, 551 s) |

## Build target

`arm64-v8a` only for the first release. `x86_64` is buildable with the same
patch but is not a shipping target.

## Why the engine is not ported to Kotlin

Upstream v2.2.7 is roughly 45 packages: agent orchestration, provider
integration, session management, tool execution, permission handling,
configuration, plugin/MCP integration, persistence, and an Effect-based HTTP
server with 139 OpenAPI paths. Rewriting that in Kotlin would be a different
product with different behaviour, and it would drift from upstream permanently.

Kotlin is the Android host: lifecycle, UI, process supervision, and the API
client. The engine stays TypeScript.

## How the engine reaches the device

The compiled engine binary is packaged as a native library:

```
app/src/main/jniLibs/arm64-v8a/libopencode.so
```

Android extracts it to the app's `nativeLibraryDir`, which is the only
location where a process may be executed from on Android 10+. Executing a
binary from app-private storage fails with `EACCES` — verified on Android 16,
see `handoff.md` §1.4.

## Supporting binaries

The engine shells out to real tools. These are packaged the same way:

| Tool | Android source | License |
|---|---|---|
| `sh` | `/system/bin/sh` (system) | — |
| `git` | Termux aarch64 build | GPLv2 |
| `rg` | Termux aarch64 build | MIT / Unlicense |
| `node` | Termux aarch64 build (nodejs-lts 24.22.3) | MIT |
| `npm` | Termux `npm` 12.2.2 (JS tree in `assets/node/`) | Artistic License 2.0 |
| `xz` | Termux aarch64 build (xz-utils 5.8.4) | Public domain |
| `tar` | Termux aarch64 build (GNU tar 1.35) | GPLv3 |
| `ar` | Termux aarch64 build (binutils 2.47, binary name `gar`) | GPLv3 |
| `curl` | Termux aarch64 build (curl 8.23.0, staged as `libcurl-cli.so`) | MIT |
| `python3` | Termux aarch64 build (Python 3.14.10, staged as `libpython3.so`; stdlib in `assets/python/`) | PSF-2.0 |
| `pip` | Python 3.14.10's pip 26.3, invoked as `python3 -m pip` (no standalone binary) | MIT |
| `jq` | Termux aarch64 build (jq 1.8.2, staged as `libjq-cli.so`) | MIT |
| `ffmpeg` | Termux aarch64 build (ffmpeg 9.0.2, staged as `libffmpeg.so`) | LGPL-2.1+ (with GPL codecs, see below) |
| `ffprobe` | Termux aarch64 build (ffmpeg 9.0.2, staged as `libffprobe.so`) | LGPL-2.1+ |

Bundling `git` (GPLv2), GNU tar/binutils (GPLv3) and ffmpeg's GPL codecs
(libx264, libx265, plus GPL-licensed support libs such as libvidstab and
libfftw3) makes this distribution subject to GPL obligations, so
`THIRD_PARTY_NOTICES.md` and a written offer for source are required before
the first release.

## Known engine limitations on Android

Derived from dependency analysis, not yet all confirmed on device:

| Capability | Status | Reason |
|---|---|---|
| Chat, sessions, providers, tools | expected to work | pure JS + Effect |
| Shell command execution | expected to work | `/system/bin/sh` verified |
| Git | expected to work | needs `core.fileMode=false` on FUSE |
| ripgrep search | expected to work | needs aarch64 binary |
| File watching | likely unavailable | `@parcel/watcher` has no Android build |
| PTY / interactive terminal | likely unavailable | node-pty has no Android build |
| LSP | expected to work | TypeScript language server 6.0.3 + TypeScript 5.9.3 + Pyright 1.1.414 bundled as npm packages in `assets/lsp/` (`script/android/stage-lsp.sh`); they run under the bundled Node. Configure the engine in `opencode.json` (see below); not yet verified on device |
| Node.js / npm | expected to work | Node 24.22.3 + npm 12.2.2 bundled (`script/android/stage-node.sh`); verify `node --version` and `npm install` on device |
| Python 3 + pip | expected to work | Python 3.14.10 + pip 26.3 bundled (`script/android/stage-python.sh`); the engine sets `PYTHONHOME` to the extracted tree. Verify `python3 --version`, `python3 -m pip --version` and `python3 -c "import ssl, sqlite3"` on device |
| jq | expected to work | jq 1.8.2 bundled (`script/android/stage-jq.sh`); verify `echo '{"a":1}' \| jq .a` on device |
| ffmpeg / ffprobe | expected to work | ffmpeg 9.0.2 bundled (`script/android/stage-ffmpeg.sh`); ~89 MiB of codec libs by construction (Termux's ffmpeg links the full codec set). Verify `ffmpeg -version` and a small transcode on device |
| Extract .deb / .tar.xz on-device | expected to work | xz 5.8.4 + tar 1.35 + ar (binutils 2.47) bundled (`script/android/stage-xz.sh`); verify `ar x`, `xz -d`, `tar -xf` on device |
| HTTP downloads / API calls from the engine shell | expected to work | curl 8.23.0 bundled (`script/android/stage-curl.sh`); verify `curl -I https://example.com` on device. Git over HTTPS is still unavailable (git was built without curl/OpenSSL) |
| MCP over stdio | possibly works | child processes run under the bundled Bun |
| Image processing | likely unavailable | `photon-node` has no Android build |

Each row must be confirmed or refuted by an actual test before it appears in
user-facing documentation as supported.

## Language servers (bundled)

The TypeScript and Python language servers ship inside the APK (see
`script/android/stage-lsp.sh`); no `npm install` on the device is needed.
They run under the bundled Node, so the engine needs their paths in the
project's `opencode.json` (`<files>` is the app's internal files dir,
`files/engine/lsp/...` after extraction):

```json
{
  "lsp": {
    "typescript": {
      "command": ["node", "<files>/engine/lsp/lib/node_modules/typescript-language-server/lib/cli.mjs", "--stdio"],
      "extensions": [".ts", ".tsx", ".js", ".jsx"]
    },
    "python": {
      "command": ["node", "<files>/engine/lsp/lib/node_modules/pyright/langserver.index.js", "--stdio"],
      "extensions": [".py"]
    }
  }
}
```

TypeScript is pinned to 5.9.3 on purpose: TypeScript 7.x is a native port
whose per-platform binaries are built for glibc and cannot run on Android.
Pyright's npm package is a Node implementation (`dist/pyright.js`) and does
not need the Python runtime, but it is bundled alongside it. There is no
LSP settings UI in the app yet; the snippet above is the manual setup.
