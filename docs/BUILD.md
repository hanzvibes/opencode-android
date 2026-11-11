# Build guide

How to produce the two APKs — debug for testing, signed release for distribution —
including the `git` and `rg` binaries the engine shells out to.

Status: the full pipeline runs on this machine and the result has been executed
on the x86_64 emulator (Android 16 / API 36). The **arm64 APK has since been run
on a physical ARM tablet** — it launched, and a launch crash in the SAF file
picker was found that way and fixed in `main`.

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

## Running the CI pipeline locally

`.github/workflows/ci.yml` does four things: unit tests, staging the native
engine out of the upstream release APKs, `assembleDebug`, and publishing the
result to the `debug-latest` rolling release. The first three run on a
developer machine with one command:

```bash
./script/ci/run-local.sh
```

The script mirrors the workflow step for step, including the pinned SHA-256
digests of the upstream APKs, so what runs locally is what runs on the runner.
Useful flags: `--skip-tests`, `--skip-stage`, `--stage-only`.

Requirements: JDK 21, Android SDK (platform 35, build-tools 35.0.0), `curl`,
`unzip`. Gradle comes from the wrapper.

The APKs are cached in `.cache/ci-apks/`, so the second run does not download
anything again. `android/app/src/main/jniLibs/` is gitignored — the staged
`.so` files never enter a commit (the workflow fails the build if they do).

### Why dependency downloads are forced onto IPv4

`android/gradle.properties` sets
`systemProp.java.net.preferIPv4Stack=true` and
`systemProp.java.net.preferIPv4Addresses=true`. On hosts whose DNS answers
with AAAA records that cannot actually be routed — GitHub Actions runners in
some regions, and most ARM dev boxes — the JVM picks the IPv6 address and the
download stalls until it times out, with an error that names neither the cause
nor the fix. Both properties only affect the build JVM; the app's runtime
networking is untouched, and the engine is reached over `127.0.0.1` anyway.

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

### 2b. Node.js (`node`, `npm`)

Node comes from Termux apt, not from a build script: the official
linux-arm64 build needs glibc and cannot run on Android, while the Termux
build is bionic-native. Staged with the Linux script (CI and local builds
run the same one):

```bash
bash script/android/stage-node.sh --abi arm64-v8a \
  --jni-out android/app/src/main/jniLibs/arm64-v8a \
  --assets-out android/app/src/main/assets/node
bash script/android/stage-node.sh --abi x86_64 \
  --jni-out android/app/src/main/jniLibs/x86_64 --no-assets
```

Requirements: `curl`, `dpkg-deb`, `readelf`, `patchelf` (CI installs it via
apt; on the VM it lives at `~/workspace/local-ci/tools/bin/patchelf`, pass
`--patchelf <path>` or set `PATCHELF`).

The script pins `nodejs-lts` 24.22.3 + `npm` 12.2.2 and their closure
(ICU 79.1, OpenSSL 3.6.5, libc++ 30, SQLite 3.54.1, c-ares 1.34.9,
zlib 1.4, ca-certificates) by version and SHA256 from the Termux `stable`
index, verifies every download, extracts with `dpkg-deb -x`, renames the
versioned `.so` names with `patchelf --set-soname` + `--replace-needed`,
and applies the same ELF gates as §2 (e_machine, `PT_LOAD p_align >=
0x4000`, no surviving versioned `DT_NEEDED`). Staged closure is ~104.5 MiB
per ABI (jniLibs) + ~10.5 MB of npm assets. The npm tree and CA bundle go
to `assets/node/` and are extracted to `files/engine/node/` at first launch
(`NodeAssets`); they are gitignored, like `jniLibs/`.

Runtime contract (see `EngineEnvironment`): child processes get
`LD_LIBRARY_PATH=<nativeLibraryDir>` (Node's RUNPATH points at the dead
Termux prefix), `SSL_CERT_FILE=<files>/engine/node/etc/tls/cert.pem`
(libcrypto hardcodes the Termux CA path), and `SHELL=/system/bin/sh`.
npm is invoked as
`node <files>/engine/node/lib/node_modules/npm/bin/npm-cli.js` — the `.js`
entry points carry a dead Termux shebang and cannot be executed directly.
Bare `npm`/`npx` commands need the ELF shim of the fix list (item 11,
not yet implemented).

### 2c. xz / tar / ar (`xz`, `tar`, `ar`)

Same source as Node: Termux apt, bionic-native. Staged with the Linux script
(CI and local builds run the same one):

```bash
bash script/android/stage-xz.sh --abi arm64-v8a \
  --jni-out android/app/src/main/jniLibs/arm64-v8a
bash script/android/stage-xz.sh --abi x86_64 \
  --jni-out android/app/src/main/jniLibs/x86_64
```

The script pins `xz-utils` 5.8.4, `tar` 1.35-3, `binutils` 2.47 and their
closure (liblzma 5.8.4, libacl 2.4.0, libandroid-glob 0.6-3,
libandroid-selinux 14.0.0.11-1, libiconv 1.19.1, pcre2 10.49, zstd 1.5.7-1,
zlib 1.4 — the zlib pin is shared with `stage-node.sh`, keep in sync) by
version and SHA256 from the Termux `stable` index, verifies every download,
extracts with `dpkg-deb -x`, renames the versioned `.so` names with
`patchelf --set-soname` + `--replace-needed`, and applies the same ELF gates
as §2 (e_machine, `PT_LOAD p_align >= 0x4000`, no surviving versioned
`DT_NEEDED`). Staged closure is ~5.1 MB per ABI. Only the GNU ar binary
(Termux name `gar`) is staged from binutils, as `libar.so`; the rest of
binutils is not shipped. `libandroid-selinux` needs `liblog.so`, an NDK
system library — it is allowed by the gate, not staged. Files that need no
edit (`tar`, `libacl`, `libiconv`, `libpcre2-8`) are left byte-identical.

Runtime contract: same as Node — the tools are symlinked into
`files/engine/tools/bin` by `EngineBinary.linkTools` and resolve their
shared libs via the `LD_LIBRARY_PATH` `EngineEnvironment` already sets.
No new environment variables. Licence note: GNU tar and binutils are
GPLv3 — see `THIRD_PARTY_NOTICES.md`.

### 2d. curl (`curl`)

Same source as Node/xz: Termux apt, bionic-native. Staged with the Linux
script (CI and local builds run the same one):

```bash
bash script/android/stage-curl.sh --abi arm64-v8a \
  --jni-out android/app/src/main/jniLibs/arm64-v8a
bash script/android/stage-curl.sh --abi x86_64 \
  --jni-out android/app/src/main/jniLibs/x86_64
```

The script pins `curl`/`libcurl` 8.23.0 and its closure (libnghttp2
1.71.0, libnghttp3 1.19.0, libngtcp2 1.26.0, libssh2 1.11.1, OpenSSL
1:3.6.5, zlib 1.4 — the OpenSSL/zlib pins are shared with
`stage-node.sh`, keep in sync) by version and SHA256 from the Termux
`stable` index, verifies every download, extracts with `dpkg-deb -x`,
renames the versioned `.so` names with `patchelf --set-soname` +
`--replace-needed`, and applies the same ELF gates as §2 (e_machine,
`PT_LOAD p_align >= 0x4000`, no surviving versioned `DT_NEEDED`). Staged
closure is ~2.25 MB of new files per ABI plus the shared OpenSSL/zlib
libs (identical bytes to the Node staging, no duplication in the APK).
The CLI binary is staged as `libcurl-cli.so` because `libcurl.so` is the
library itself. TLS uses the CA bundle Node already ships
(`SSL_CERT_FILE=<files>/engine/node/etc/tls/cert.pem`).

This does NOT enable git over HTTPS: the bundled git was built with
`NO_CURL`/`NO_OPENSSL`. Rebuilding git needs the Windows-only
`build-tools.ps1` plus the upstream APK rebuilt — a separate step.

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

### 5. Release build and signing

`assembleRelease` works without any signing setup, but the output is
`app-<abi>-release-unsigned.apk`, which no device will install. Signing is
configured in `android/app/build.gradle.kts`, which reads
`android/keystore.properties` — **gitignored**, along with `*.jks`.

The keystore itself deliberately lives **outside the repository**, at
`H:\PUNYA ABID\PROJECT PRIBADI\OPENCODE-ANDROID-KEYS\opencode-release.jks`, so
deleting the working tree or clearing `.cache/` cannot destroy it.

```powershell
cd android
& "..\.cache\gradle\gradle-8.14\bin\gradle.bat" :app:assembleRelease --no-daemon
# -> app/build/outputs/apk/release/app-{arm64-v8a,x86_64}-release.apk

& "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\apksigner.bat" `
    verify --print-certs app\build\outputs\apk\release\app-arm64-v8a-release.apk
```

Expect `Verifies`, `Verified using v2 scheme: true`, and
`Signer #1 certificate DN: CN=dev.bidzz.opencode, OU=OpenCode Android, O=bidzz, ST=Indonesia, C=ID`.

`keystore.properties` format — note the **forward slashes**, because
`java.util.Properties` treats `\` as an escape character:

```properties
storeFile=H:/PUNYA ABID/PROJECT PRIBADI/OPENCODE-ANDROID-KEYS/opencode-release.jks
storePassword=<from your password manager>
keyAlias=opencode
keyPassword=<same>
```

#### Back up the key before distributing anything

This is the one irreversible artifact in the build. Android refuses to install an
update signed with a different key, so **losing this keystore means every device
that already has the app must uninstall and reinstall, losing app-private state**.
Copy both the `.jks` and its password to a location that survives a disk failure.

Fingerprint of the current key, to confirm a build was signed with it:

```
SHA-256  b37f02de4417d8704ba5fe71d6aa1910cf3a77da16f0faf6759319779f0eeb1f
RSA 4096, self-signed, validity 10950 days (to 2056), PKCS12, alias `opencode`
```

Regenerating is only correct if the app has never been installed anywhere from
the old key.

#### Installing a release build over a debug build

They are signed with different keys, so `adb install -r` fails with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Uninstall first — this clears app data,
including stored sessions and the workspace preference:

```powershell
adb uninstall dev.bidzz.opencode
adb install app\build\outputs\apk\release\app-x86_64-release.apk
```

---

## Verifying a build

Static, on the host:

```powershell
python .cache\elfcheck.py android\app\src\main\jniLibs\arm64-v8a\libgit.so
```

Expect: `e_machine=0xB7`, `ET_DYN`, interpreter `/system/bin/linker64`, all
`PT_LOAD` `p_align >= 0x4000`.

On the device, after the app has started the engine once. **These `run-as` commands
only work on a debug build** — a release APK is not debuggable, so `run-as` fails with
`run-as: package not debuggable: dev.bidzz.opencode`. On a release install, use the
in-app Diagnostics screen instead:

```powershell
adb shell run-as dev.bidzz.opencode ls -l files/engine/tools/bin
# expect: git -> .../libgit.so, rg -> .../librg.so, node -> .../libnode.so,
# xz -> .../libxz.so, tar -> .../libtar.so, ar -> .../libar.so and
# curl -> .../libcurl-cli.so (symlinks)

adb shell "run-as dev.bidzz.opencode sh -c 'cd /data/user/0/dev.bidzz.opencode/files/engine/tools/bin && ./git --version && ./rg --version | head -1 && ./node --version && ./xz --version | head -1 && ./tar --version | head -1 && ./ar --version | head -1 && ./curl --version | head -1'"
# expect: git version 2.56.0 / ripgrep 15.1.0 / v24.22.3 / xz (XZ Utils) 5.8.4 / tar (GNU tar) 1.35 / GNU ar (GNU Binutils) 2.47 / curl 8.23.0
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

- **The arm64 APK has now been executed on a physical device** (ARM tablet,
  November 2026): it launched and ran. The only crash found that way — a
  missing `ActivityResultRegistryOwner` for the SAF file picker — is fixed in
  `main`. Long-session stability on small-RAM phones is still unmeasured.
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
