# node_modules patch — @effect/platform-node

Not a git patch. The affected file lives in `node_modules`, so it is recreated
on every `bun install` and must be re-applied by script.

Script: [`script/android/patch-effect-http.ts`](../../script/android/patch-effect-http.ts)

```bash
bun script/android/patch-effect-http.ts .cache/upstream
```

The script is idempotent and fails loudly if upstream changes the code it
expects, rather than silently doing nothing.

---

## The bug

`@effect/platform-node/dist/internal/http.js` resolves `node:http` through
`createRequire`:

```js
const require = createRequire(typeof __filename === "string" ? __filename : import.meta.url)
```

Bun's bundler replaces `__filename` with the **build machine's absolute path** as
a string literal. That is
[oven-sh/bun#29066](https://github.com/oven-sh/bun/issues/4216) /
[PR #29066](https://github.com/oven-sh/bun/pull/29066), fixed upstream but not in
any release yet.

The consequence on device is fatal, because every HTTP request in the engine
passes through this module:

```
TypeError: The argument 'filename' must be a file URL object, file URL string,
or absolute path string. Received
'H:\\PUNYA ABID\\PROJECT PRIBADI\\OPENCODE-ANDROID\\.cache\\upstream\\node_modules\\...\\@effect\\platform-node\\dist\\internal\\http.js'
    at createRequire (unknown)
    at .../@effect/platform-node/dist/internal/http.js:5:30
```

The engine starts, prints its version, and then cannot serve a single request.

## What does not work

Two approaches were tried and both failed. Recording them so they are not
retried:

**1. `--define '__filename=import.meta.path'`.** Bun substitutes a `--define`
value as a **string literal**, not as an expression. The bundle ends up
containing the literal text `"import.meta.path"`, and `createRequire` throws:

```
TypeError: ... Received 'import.meta.path'
```

Documented in [oven-sh/bun#4216](https://github.com/oven-sh/bun/issues/4216).

**2. Guarding on the value at runtime.** Wrapping the original expression in a
validity check does not help, because Bun replaces `__filename` with the literal
path *before* any runtime code runs:

```js
const filename =
  typeof __filename === "string" && /^[a-zA-Z]:[\\/]/.test(__filename) ? __filename : import.meta.url
```

The guard's own `__filename` reference is also inlined, so the branch still
evaluates against the build-machine path. On device this produced the same
`TypeError`, only at a different line number — which is how the failure was
confirmed rather than assumed.

## What the patch does

Removes `__filename` from the expression entirely and uses `import.meta.url`
directly:

```js
const require = createRequire(import.meta.url)
```

Inside a compiled executable `import.meta.url` resolves to
`file:///$bunfs/root/...`, which is a valid URL and is where the engine's own
modules actually live. This is the value Bun intends to provide
(`oven-sh/bun#29066`); the patch restores it without waiting for the release.

## Other host-path sites in the binary

The same build-path inlining appears in five places in the engine binary:

| Package | Reached at runtime? |
|---|---|
| `@effect/platform-node` | **yes** — every HTTP request |
| `@npmcli/run-script` | only if npm scripts are run |
| `@npmcli/arborist` | only if dependency tree is walked |
| `write-file-atomic` | only if it writes atomically |
| `@silvia-odwyer/photon-node` | only if image processing runs |

Only the first is on the critical path for a working engine. The others are
latent: they will fail the moment their code path is reached on device. They
should be audited before release, most likely by patching each package the same
way or by replacing the call sites with `import.meta.url` directly.

## Cleaner long-term fix

Pin a Bun release that contains
[PR #29066](https://github.com/oven-sh/bun/pull/29066). Once `--compile` emits
`var __dirname = import.meta.dir`, this patch and the five latent sites all
disappear. Track that PR and drop this script when the fix ships.
