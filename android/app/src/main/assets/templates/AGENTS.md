# OpenCode Android — runtime guide

<!-- Placed here by the OpenCode Android app so the agent knows what tools
     exist on this device. Edit it freely: your edits are never overwritten.
     If you delete this file, the app recreates it on next launch. -->

## Environment

- The shell is `/system/bin/sh`. There is NO bash.
- The project directory is the working directory. `HOME` is the engine's
  private home, not the project folder.
- These tools are on `PATH`: `node`, `xz`, `tar`, `ar`, `curl`, `git`, `rg`.

## Tools

| Tool | Version | How to run |
| --- | --- | --- |
| Node.js | 24.22.2 | `node --version` |
| npm | 12.2.2 | `node $HOME/../node/lib/node_modules/npm/bin/npm-cli.js <args>` — there is NO bare `npm` or `npx` command |
| xz | 5.8.4 | `xz --version` |
| tar | 1.35 | `tar --version` |
| ar | 2.47 | `ar --version` (GNU ar, from binutils) |
| curl | 8.23.0 | `curl --version`; HTTPS works, the CA bundle is bundled |
| git | 2.56.0 | Local operations only — `clone`, `fetch` and `push` over HTTPS do NOT work (git was built without curl/OpenSSL) |
| ripgrep | 15.1.0 | `rg --version` |

## Rules

- Prefer the bundled tools above. Do not try to install Node, Python, or
  other runtimes — they are not installed and cannot be installed here.
- `npm install` works for pure-JavaScript packages. Prefer small packages:
  native addons (node-gyp, prebuilt binaries) will not build on this device.
- Remotion: `npm install`, authoring and bundling work. Rendering to MP4
  does NOT work on this device (it needs desktop Chrome) — do not attempt
  it; say so and offer to prepare the project for rendering elsewhere.
- Do not run `git clone https://...` — it will fail. Use local git only.
- There is no interactive PTY: prefer short, bounded commands and do not
  start interactive programs (editors, pagers, REPLs).
- If a bundled tool reports "not found", stop and say so instead of
  inventing a workaround.
