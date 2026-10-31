# Build guide

How to produce the two debug APKs, including the `git` and `rg` binaries the
engine shells out to.

Status: the full pipeline runs on this machine and the result has been executed
on the x86_64 emulator (Android 16 / API 36). The **arm64 APK has not been run on
a physical device** — see "Known gaps" at the end.

---

## Prerequisites

| Tool | Version used | Notes |
|---|---|---|
| JDK | 21 | APK stage |
| Android SDK | build-tools 36.0.0 | APK stage |
| **Android NDK** | **29.0.13113456** | **Must be 29.x.** NDK 27 and lower default to 4 KB page alignment (see below) |
| Bun | 1.4.4 | host runtime for the engine build; not on `PATH` by default |
| rustup + targets | 1.99.1, `aarch64-linux-android` + `x86_64-linux-android` | builds `rg` |
| Git for Windows | 2.49 | provides `sh`, `sed`, `awk` for git's Makefile |
| Python | 3.11 | optional, for `elfcheck.py` |

Gradle is not installed system-wide; the repository pins its own copy at
`.cache/gradle/gradle-8.14`.

### Why NDK 29 specifically

Android 15+ can run a 16 KB page-size kernel. An ELF whose `PT_LOAD` segments are
aligned to 4 KB either crashes or is forced into compatibility mode on such a
device. Measured on this machine with a trivial object:

| NDK | `PT_LOAD p_align` |
|---|---|
| 27.0.12077973 | `0x1000` → **4 KB, unsafe** |
| 29.0.13113456 | `0x4000` → 16 KB, safe |

Both build scripts additionally pass
`-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384` explicitly, so a
future NDK bump cannot silently regress this.

### `unzip` shim (engine build only)

`packages/cli/script/build.ts` runs `unzip -oq`, which Windows lacks. Provide a
shim on `PATH` that forwards to `tar`. The shim **must live at a path without
spaces** — Bun's shell does not quote `PATH` entries, so `C:\ocbin\unzip.bat` is
fine and `C:\Program Files\...` is not.

```bat
@echo off
setlocal enabledelayedexpansion
set ARCHIVE=
set DEST=
:parse
if "%~1"=="" goto done
if /I "%~1"=="-d" ( set DEST=%~2 & shift & shift & goto parse )
if /I "%~1"=="-o" ( shift & goto parse )
if /I "%~1"=="-q" ( shift & goto parse )
if /I "%~1"=="-oq" ( shift & goto parse )
set ARCHIVE=%~1
shift
goto parse
:done
if not exist "%DEST%" mkdir "%DEST%"
tar -xf "%ARCHIVE%" -C "%DEST%"
```

---

## Full sequence

All commands assume the repository root unless stated otherwise.

### 1. Engine binary

Only needed when upstream code or a patch changes. The `build.ts` script deletes
`dist/` at the start, so **back up the other ABI's binary first**:

```powershell
# back up whatever is already built
Copy-Item .cache\upstream\packages\cli\dist\cli-linux-*-android\bin\opencode .cache\engine-archive\ -Force

$env:PATH="C:\ocbin;$env:PATH"
$env:BUN_COMPILE_RELEASE="bun-v1.4.4"
$env:OPENCODE_TARGET="linux-arm64-android"
cd .cache\upstream\packages\cli
& "C:\Users\User\.bun\bun-windows-x64\bun.exe" run script/build.ts --skip-install --skip-web-ui
```

Output: `.cache/upstream/packages/cli/dist/cli-linux-arm64-android/bin/opencode`.

Repeat with `OPENCODE_TARGET=linux-x64-android` for the emulator ABI.

### 2. Tool binaries (`git`, `rg`)

```powershell
.\script\android\build-tools.ps1              # both ABIs
.\script\android\build-tools.ps1 -Abi arm64-v8a
```

Output: `.cache/tools/<abi>/{git,rg}`.

First run downloads and builds:

- **git 2.56.0** from `mirrors.edge.kernel.org`, verified against upstream
  `sha256sums.asc`. Built with the NDK's own `make.exe` and Git for Windows'
  `sh`, forcing `uname_S=Linux uname_M=<arch>` so the Makefile takes the POSIX
  branch instead of the MINGW one.
- **ripgrep 15.1.0** from GitHub, built with `cargo` against the Android targets.

Both are stripped afterwards: git goes 19 MB → 3.7 MB, rg 31 MB → 4.7 MB.

The script hard-fails if any produced ELF has the wrong `e_machine` or a
`PT_LOAD` segment below `0x4000` alignment.

### 3. Stage into `jniLibs`

```powershell
.\script\android\stage-engine.ps1 -Abi arm64-v8a `
    -Source .cache\upstream\packages\cli\dist\cli-linux-arm64-android\bin\opencode `
    -ToolsDir .cache\tools\arm64-v8a

.\script\android\stage-engine.ps1 -Abi x86_64 `
    -Source .cache\engine-archive\opencode-linux-x64-android `
    -ToolsDir .cache\tools\x86_64
```

This copies `libopencode.so`, `libgit.so`, `librg.so` and verifies all three for
architecture and 16 KB alignment. Omitting `-ToolsDir` is allowed but warns: the
APK then ships without git/rg and VCS plus ripgrep-backed search stay broken.

### 4. Build the APKs

```powershell
cd android
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:GRADLE_USER_HOME="..\.cache\gradle-home"
& "..\.cache\gradle\gradle-8.14\bin\gradle.bat" :app:assembleDebug --no-daemon
```

Two APKs, not one:

```powershell
adb install -r app\build\outputs\apk\debug\app-x86_64-debug.apk    # emulator
adb install -r app\build\outputs\apk\debug\app-arm64-v8a-debug.apk # device
```

---

## Verifying a build

Static, on the host:

```powershell
python .cache\elfcheck.py android\app\src\main\jniLibs\arm64-v8a\libgit.so
```

Expect: `e_machine=0xB7`, `ET_DYN`, interpreter `/system/bin/linker64`, all
`PT_LOAD` `p_align >= 0x4000`.

On the device, after the app has started the engine once:

```powershell
adb shell run-as dev.bidzz.opencode ls -l files/engine/tools/bin
# expect: git -> .../libgit.so  and  rg -> .../librg.so  (symlinks)

adb shell "run-as dev.bidzz.opencode sh -c 'cd /data/user/0/dev.bidzz.opencode/files/engine/tools/bin && ./git --version && ./rg --version | head -1'"
# expect: git version 2.56.0 / ripgrep 15.1.0
```

End-to-end, against a real repository:

```powershell
# port and password are in the app: ⋯ -> Settings -> Diagnostics
$h=@{Authorization="Basic $([Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("opencode:<password>")))"}
(Invoke-WebRequest "http://127.0.0.1:<port>/api/vcs/status" -Headers $h).Content
```

A working build returns populated entries:

```json
{"location":{"directory":"/storage/emulated/0/Projects/demo"},
 "data":[{"file":"main.py","additions":2,"deletions":1,"status":"modified"}]}
```

`"data":[]` on a repository with uncommitted changes means git is not reachable
from the engine — check `tools/bin` and the engine's `PATH`.

---

## Known gaps

- **The arm64 APK has never been executed.** The emulator available here is
  x86_64 only, so arm64 is verified statically (`e_machine`, `PT_LOAD` alignment,
  `DT_NEEDED`, interpreter) but not at runtime. A physical arm64 device is
  required.
- **git has no network support.** Built with `NO_CURL` and `NO_OPENSSL`, so
  clone/push/fetch over https do not work. Local operations (status, diff,
  commit, branch, log) are complete. Adding networking means bundling libcurl,
  OpenSSL and a CA bundle, plus `SSL_CERT_FILE` handling.
- **git prints `warning: templates not found in /share/git-core/templates`.**
  Cosmetic: the hook template directory is not installed, so no sample hooks are
  copied. It does not affect any operation. Silencing it means shipping
  `templates/` or setting `GIT_TEMPLATE_DIR`.
- **The `SDK_INT < 34` foreground-service branch and API 29 storage paths are
  untested.** The only emulator here is API 36.

---

## Reference: what fails, and why

| Failure | Cause | Fix |
|---|---|---|
| `bun: command not found: unzip` | Windows lacks unzip | shim above, at a space-free path |
| `Cannot find module '@opencode/pty-linux-arm64-android'` | no Android PTY build exists | `engine/patches/0002-pty-android.patch` |
| `Could not resolve '@parcel/watcher-linux-arm64-android'` | plugin injects a literal `require()` | `engine/patches/0003-parcel-watcher-android.patch` |
| `call to undeclared function 'pthread_setcancelstate'` | bionic has no pthread cancellation | `NO_PTHREADS=1` (upstream's own fallback) |
| `sh: <path>/git: Permission denied` (exit 126) | hard link inherited the `app_data_file` label, which `untrusted_app` cannot exec | `EngineBinary.linkTools` uses a **symlink** so the target keeps `apk_data_file` |
| `unsupported platform for ripgrep: arm64-android` | `RipgrepBinary` has no Android entry in its platform map | ship `rg` so `which("rg")` resolves before the download path runs |
