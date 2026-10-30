# Build guide — Android engine

How to produce `libopencode.so` for `arm64-v8a`.

Status: **work in progress.** The host-side build has been driven up to the
compile step; the resulting binary has not yet been run on an arm64 device.

---

## Prerequisites

| Tool | Version used | Notes |
|---|---|---|
| Bun | 1.4.4 | host runtime for the build scripts |
| Git | 2.49 | clone upstream, apply patches |
| JDK | 21 | only needed for the APK stage |
| Android SDK | build-tools 36.0.0, NDK 29 | APK stage |
| `unzip` | any | **not present on Windows by default**, see below |

### Windows: `unzip` is required by upstream `build.ts`

`packages/cli/script/build.ts` extracts the Bun release archive with:

```ts
await $`unzip -oq ${archive} -d ${cache}`
```

Windows has no `unzip`. The build fails with
`bun: command not found: unzip`. Provide a shim on `PATH` that forwards to
`tar` (bsdtar, bundled with Windows 10+, handles zip):

```bat
@echo off
REM unzip shim: unzip -oq <archive> -d <dir>
setlocal enabledelayedexpansion
set ARCHIVE=
set DEST=
:parse
if "%~1"=="" goto done
if /I "%~1"=="-d" ( set DEST=%~2 & shift & shift & goto parse )
if "%~1"=="-o" ( shift & goto parse )
if "%~1"=="-q" ( shift & goto parse )
if "%~1"=="-oq" ( shift & goto parse )
set ARCHIVE=%~1
shift
goto parse
:done
if not exist "%DEST%" mkdir "%DEST%"
tar -xf "%ARCHIVE%" -C "%DEST%"
```

---

## Steps

### 1. Clone upstream at the pinned commit

```bash
git clone --depth 1 --branch dev https://github.com/anomalyco/opencode.git .cache/upstream
cd .cache/upstream
git log --oneline -1     # expect 92b2ca0
```

### 2. Install dependencies

```bash
bun install --ignore-scripts
```

`--ignore-scripts` matters: the root `postinstall` runs
`bun run --cwd packages/core fix-node-pty`, which patches node-pty for the
**host** platform. That is meaningless for an Android cross-build and can
interfere.

Expect ~1.777 packages and roughly 9 minutes.

### 3. Apply patches

```bash
for p in ../../engine/patches/*.patch; do
  git apply "$p"
done
git diff --stat
```

A patch that fails to apply means upstream changed underneath it. Review the
change; do not skip the patch silently.

### 4. Build

```bash
cd packages/cli
export BUN_COMPILE_RELEASE=bun-v1.4.4
export OPENCODE_TARGET=linux-arm64-android
bun run script/build.ts --skip-install --skip-web-ui
```

Output: `packages/cli/dist/opencode-linux-arm64/bin/opencode`.

`--skip-web-ui` omits the embedded web UI archive. Keep it for now: the UI is
not used, and it makes the build faster and the binary smaller.

### 5. Verify the ELF

```bash
python ../../.cache/elfcheck.py packages/cli/dist/opencode-linux-arm64/bin/opencode
```

Expected: AArch64, `ET_DYN`, interpreter `/system/bin/linker64`, all `PT_LOAD`
`p_align` >= `0x4000`.

### 6. Package as a native library

The binary must reach the device as a `.so` inside `jniLibs`, because Android
10+ refuses to execute anything from writable app storage.

```bash
cp packages/cli/dist/opencode-linux-arm64/bin/opencode \
   ../../app/src/main/jniLibs/arm64-v8a/libopencode.so
```

`extractNativeLibs` must be `true` in the manifest so the file lands on disk in
`nativeLibraryDir` rather than being mapped from inside the APK.

---

## What is known to fail, and why

| Failure | Cause | Fix |
|---|---|---|
| `Cannot find module '@opencode/pty-linux-arm64-android'` | no Android PTY build exists | patch `0002-pty-android` |
| `bun: command not found: unzip` | Windows lacks unzip | shim above |
| `NativeArch.verify()` arch mismatch | native addons have no Android build | **not yet solved** — see `engine/patches/README.md` |

## Not yet done

- The arm64 binary has not been executed on an arm64 device. The runtime
  primitives were proven on x86_64 Android 16 (`handoff.md` §1.4), but the
  engine itself has not started on any device.
- `OPENCODE_LIBC=android` / `FFF_LIBC=android` call sites are unaudited.
- No Gradle project exists yet; the APK stage is still a shell script.
