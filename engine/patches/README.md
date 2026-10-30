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

## Known unresolved points

These are not fixed by 0001 and will surface during the first real engine build.

### `NativeArch.verify()` will likely fail

`script/native-arch.ts` scans **every** embedded ELF image in the produced
binary and throws unless all of them match the target architecture. Android has
no builds for these native addons:

| Package | Used for |
|---|---|
| `@lydell/node-pty`, `bun-pty`, `@opencode/pty` | PTY |
| `@parcel/watcher` | file watching |
| `@ff-labs/fff` | native file search |
| `@silvia-odwyer/photon-node` | image processing |
| `@yuuang/ffi-rs` | FFI |
| `web-tree-sitter` | parsing |

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
