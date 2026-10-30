#!/usr/bin/env bun
/**
 * Patches @effect/platform-node so a cross-compiled Bun executable can resolve
 * `node:http` on the target device.
 *
 * Why this is not a normal git patch: the affected file lives in node_modules,
 * not in the repository. It is regenerated on every `bun install`, so the fix
 * has to be re-applied after install rather than committed as a diff.
 *
 * The bug
 * -------
 * `@effect/platform-node/dist/internal/http.js` does:
 *
 *   createRequire(typeof __filename === "string" ? __filename : import.meta.url)
 *
 * Bun's bundler replaces `__filename` with the build machine's absolute path as
 * a string literal (oven-sh/bun#29066, fixed upstream but unreleased). When the
 * binary runs on Android, that Windows path is not a valid filename, so
 * createRequire throws on every HTTP request:
 *
 *   TypeError: The argument 'filename' must be a file URL object, file URL
 *   string, or absolute path string. Received 'H:\...\internal\http.js'
 *
 * Attempted fix that does NOT work: `--define '__filename=import.meta.path'`.
 * Bun substitutes the define value as a string literal, so the bundle ends up
 * with the literal text "import.meta.path" (oven-sh/bun#4216).
 *
 * What this patch does: accept `__filename` only when it looks like an absolute
 * path for the platform, otherwise fall back to `import.meta.url`, which Bun
 * resolves to `file:///$bunfs/root/...` inside a compiled executable.
 *
 * Usage: bun script/android/patch-effect-http.ts <upstream-root>
 */
import { readFile, writeFile } from "node:fs/promises"
import { Glob } from "bun"
import path from "node:path"

const root = process.argv[2]
if (!root) throw new Error("usage: patch-effect-http.ts <upstream-root>")

const marker = "Patched for Android cross-compile"

const replacement = `import { createRequire } from "node:module";
// Importing node:http as ESM evaluates its lazy WebSocket exports, which loads
// undici. Load the CommonJS modules instead.
//
// ${marker}: \`import.meta.url\` is used directly instead
// of \`__filename\`. Bun's bundler replaces \`__filename\` with the build machine's
// absolute path as a string literal before any runtime guard can inspect it
// (oven-sh/bun#29066), so guarding on the value does not help. \`import.meta.url\`
// resolves to file:///$bunfs/root/... inside a compiled executable, which is
// valid everywhere.
const require = /*#__PURE__*/createRequire(import.meta.url);
export const http = /*#__PURE__*/require("node:http");
export const https = /*#__PURE__*/require("node:https");
//# sourceMappingURL=http.js.map
`

const targets = Array.from(
  new Glob("node_modules/.bun/@effect+platform-node*/node_modules/@effect/platform-node/dist/internal/http.js").scanSync({
    cwd: root,
    absolute: true,
  }),
)

if (targets.length === 0) throw new Error("no @effect/platform-node internal/http.js found — is the dependency installed?")

for (const file of targets) {
  const source = await readFile(file, "utf8")
  if (source.includes(marker)) {
    console.log(`already patched: ${path.relative(root, file)}`)
    continue
  }
  if (!source.includes("createRequire(typeof __filename")) {
    throw new Error(`unexpected content in ${file} — upstream changed, review before patching`)
  }
  await writeFile(file, replacement)
  console.log(`patched: ${path.relative(root, file)}`)
}
