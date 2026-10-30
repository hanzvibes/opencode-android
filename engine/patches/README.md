# Android engine patches

Patches applied to the upstream OpenCode source (`anomalyco/opencode`) to build
a working engine for Android.

Upstream is never modified in place inside this repository. The workflow is:

1. Clone upstream at the pinned commit into `.cache/upstream` (gitignored).
2. Apply every patch in this directory in numeric order.
3. Build.
4. Re-apply after an upstream bump; a patch that no longer applies is a signal
   that upstream changed under us and needs review, not a silent skip.

Pinned upstream: `92b2ca0` — `@opencode/cli` v2.2.2, branch `dev`.

---

## 0001-android-target.patch

Adds `arm64` and `x64` Android build targets to `packages/cli/script/build.ts`.

**Verified facts that shaped this patch:**

- Bun 1.4.4 ships official Android binaries: `bun-linux-aarch64-android`,
  `bun-linux-x64-android`. They are PIE, linked against bionic, interpreter
  `/system/bin/linker64`, and 16 KB page aligned (`p_align` `0x4000`/`0x10000`).
- Bun accepts `android` only as a libc suffix on a linux target.
  `bun-linux-arm64-android` is accepted; `bun-android-arm64` is rejected with
  `invalid target, android only exists with linux`.

**What the patch changes:** two lines of declaration only.

```diff
-  abi?: "musl"
+  abi?: "musl" | "android"
```

```diff
+  { os: "linux", arch: "arm64", abi: "android" },
+  { os: "linux", arch: "x64", abi: "android" },
```

**What it deliberately does not change:** `targetName()`. Declaring the target
as `{ os: "linux", abi: "android" }` already makes `targetName()` produce
`opencode-linux-arm64-android`, and the existing
`target.replace(binary, "bun")` in the `compile.target` field produces
`bun-linux-arm64-android`. Declaring `{ os: "android" }` instead would produce
the invalid `bun-android-arm64` — that is the mistake this patch avoids.

`compileExecutable()` already builds release asset names as
`["bun", platform, arch === "arm64" ? "aarch64" : arch, abi]`, which yields
`bun-linux-aarch64-android` — the exact published asset name. No change needed.

---

## 0002-pty-android.patch

Stops the build from failing when no Android PTY binary exists.

**The failure this fixes**, observed on the first real engine build:

```
error: Cannot find module '@opencode/pty-linux-arm64-android/bin/opencode-pty'
  from '...node_modules/@opencode/pty/package.json'
```

`resolveOpencodePty()` in `packages/cli/script/opencode-pty.ts` guards on
platform and arch but not on libc. With the Android target declared as
`{ os: "linux", arch: "arm64", abi: "android" }`, the guard passes
(`platform === "linux"`, `arch === "arm64"`), then line 25 builds the suffix
`linux-arm64-android` and looks for `@opencode/pty-linux-arm64-android`, which
does not exist in the registry.

The patch returns `undefined` for `libc === "android"`, which the caller already
handles by emitting `export default undefined` for the PTY binding. The engine
therefore builds without PTY support instead of failing outright.

This is the intended degradation: no PTY on Android, reported honestly rather
than faked. It matches the project rule that unsupported capabilities are
documented, not simulated.

---

## 0003-parcel-watcher-android.patch

Same class of failure as 0002, one layer deeper.

**Observed failure:**

```
building cli-linux-arm64-android
error: Could not resolve: "@parcel/watcher-linux-arm64-android". Maybe you need to "bun install"?
    at packages/core/src/filesystem/watcher-binding.ts:1:30
```

`build.ts` synthesises the native package name from the target:

```ts
const parcelWatcherPackage = `@parcel/watcher-${item.os}-${item.arch}${item.os === "linux" ? `-${item.abi ?? "glibc"}` : ""}`
```

With `abi: "android"` that becomes `@parcel/watcher-linux-arm64-android`, which
does not exist. The plugin injects a literal `require(...)`, so the failure
happens at **bundle time** and aborts the build.

The patch emits a stub that throws only when **called**:

```ts
contents:
  item.abi === "android"
    ? `export default () => { throw new Error("parcel-watcher is not available on android") }`
    : `export default () => require(${JSON.stringify(parcelWatcherPackage)})`
```

`packages/core/src/filesystem/watcher.ts` already wraps the binding load in
`try/catch` and returns `undefined` on failure, so the engine degrades to
`node:fs` watching instead of crashing. Throwing lazily is what makes that
fallback reachable — throwing at bundle time is not catchable.

---

## 0004-dirname-filename.patch

Fixes the build-machine path leak that breaks the engine at runtime.

**Observed failure** — the engine started on Android and immediately threw:

```
TypeError: The argument 'filename' must be a file URL object, file URL string, or
absolute path string. Received
'H:\\PUNYA ABID\\PROJECT PRIBADI\\OPENCODE-ANDROID\\.cache\\upstream\\node_modules\\.bun\\@effect+platform-node@4.0.6+...\\@effect\\platform-node\\dist\\internal\\http.js'
    at createRequire (unknown)
    at .../@effect/platform-node/dist/internal/http.js:5:30
```

`@effect/platform-node`'s `internal/http.js` does:

```js
const require = createRequire(typeof __filename === "string" ? __filename : import.meta.url)
```

Bun's bundler replaces `__filename` with the **build machine's absolute path** as a
string literal. When the binary runs on Android, that Windows path is not a valid
filename, so `createRequire` throws — and every HTTP request goes through this
module, so the server cannot serve anything.

This is a known Bun bug, fixed by
[oven-sh/bun#29066](https://github.com/oven-sh/bun/pull/29066) and
[#35470](https://github.com/oven-sh/bun/pull/35470), neither of which is in a
release yet.

**The patch** defines the identifiers at bundle time:

```ts
__dirname: "import.meta.dir",
__filename: "import.meta.path",
```

Verified effect: a minimal repro (`createRequire(__filename)` compiled with
`--target=bun-linux-x64-android`) goes from 2 occurrences of the host path to
**0**. `import.meta.path` resolves to `/$bunfs/root/...` inside a compiled
executable, which is a valid absolute path on the device.

**Scope:** the same 5 host-path sites appeared in the engine binary, in
`@npmcli/run-script`, `@npmcli/arborist`, `write-file-atomic`,
`@silvia-odwyer/photon-node`, and `@effect/platform-node`. Only the last one is
on the critical path for serving HTTP; the others would fail if their code paths
were reached. The `--define` fixes all of them at once.

**Note:** `--define` substitutes the *expression*, not a string. Writing
`--define '__filename="import.meta.path"'` (with quotes) produces the literal
string `"import.meta.path"` and does not work — this is the trap described in
[oven-sh/bun#4216](https://github.com/oven-sh/bun/issues/4216).

---

## Known unresolved points

These are not fixed by 0001 and will surface during the first real engine build.

### `NativeArch.verify()` will likely fail

`script/native-arch.ts` scans **every** embedded ELF image in the produced
binary and throws unless all of them match the target architecture. Android has
no builds for these native addons:

| Package | Used for | Android status |
|---|---|---|
| `@opencode/pty`, `@lydell/node-pty`, `bun-pty` | PTY | **confirmed absent** — build fails without 0002 |
| `@parcel/watcher` | file watching | no android variant published |
| `@ff-labs/fff` | native file search | no android variant published |
| `@silvia-odwyer/photon-node` | image processing | no android variant published |
| `@yuuang/ffi-rs` | FFI | no android variant published |
| `web-tree-sitter` | parsing | needs WASM host, unconfirmed |

Options, in order of preference:

1. Make the verify call target-aware so Android only verifies the main image.
2. Exclude the unavailable native packages from the Android build entirely and
   let the engine degrade (no PTY, no native watcher) with a documented status.
3. Ship a separate Android-only entrypoint that omits those modules.

Option 2 matches the project's stated rule: report unsupported features rather
than fake them.

### `OPENCODE_LIBC` / `FFF_LIBC` receive `"android"`

Call sites that branch on `glibc` vs `musl` must tolerate a third value. Needs
an audit before shipping.

### `bun install` on the host

`bunfig.toml` sets `exact = true` and a 3-day `minimumReleaseAge`. The root
`postinstall` runs `bun run --cwd packages/core fix-node-pty`, which patches
node-pty for the host platform. Install with `--ignore-scripts` when preparing
an Android cross-build so host-specific native patching does not interfere.
