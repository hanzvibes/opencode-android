# Third-party notices

OpenCode Android bundles third-party software. This file lists what is shipped,
under which licence, and where the corresponding source can be obtained.

The app itself is distributed through GitHub Releases and Obtainium, not the Play
Store (see `handoff.md` D2), so the licence obligations below are met by
publishing the source of every bundled component alongside each release.

---

## Bundled executables

These ship inside the APK as `jniLibs/<abi>/lib<name>.so` and are executed as
child processes. They are **not** linked into the app.

### git 2.56.0 — `libgit.so`

| | |
|---|---|
| Component | Git (the `git` binary, C-only build) |
| Version | 2.56.0 |
| Licence | **GPL-2.0** (GNU General Public License, version 2) |
| Upstream | https://git-scm.com/ · https://github.com/git/git |
| Source used | https://mirrors.edge.kernel.org/pub/software/scm/git/git-2.56.0.tar.gz |
| Modifications | **None.** Built from unmodified upstream source. |
| Build | `script/android/build-tools.ps1` — see `docs/BUILD.md` |

Git is GPL-2.0. Because it is shipped as a separate executable that the app
invokes as a subprocess, and the app communicates with it only through its
command-line interface, the app is not a derivative work of git. The obligation
that does apply is to make the corresponding source available:

- The unmodified upstream tarball at the URL above is the complete corresponding
  source, since no patches are applied.
- `script/android/build-tools.ps1` documents the exact configure-equivalent flags
  used (`NO_CURL`, `NO_OPENSSL`, `NO_GETTEXT`, `NO_RUST`, `NO_PTHREADS`, ...), so
  the binary is reproducible from that tarball.

Every release must attach that tarball (or a link to it) next to the APK.

The GPL-2.0 licence text is in `licenses/GPL-2.0.txt`.

### ripgrep 15.1.0 — `librg.so`

| | |
|---|---|
| Component | ripgrep (`rg`) |
| Version | 15.1.0 |
| Licence | **MIT** / **Unlicense** (dual) |
| Upstream | https://github.com/BurntSushi/ripgrep |
| Source used | https://github.com/BurntSushi/ripgrep/archive/refs/tags/15.1.0.tar.gz |
| Modifications | None |
| Build | `script/android/build-tools.ps1` |

ripgrep is dual-licensed MIT/Unlicense, so redistribution only requires keeping
the copyright notice and permission notice. Both are reproduced below.

### Node.js 24.22.2 — `libnode.so` (+ npm 12.2.2, ICU, OpenSSL)

The in-app agent needs a JavaScript runtime. Node.js comes from Termux apt
(bionic-native, PIE, 16 KB aligned) — not from nodejs.org, whose linux-arm64
build needs glibc and cannot run on Android. Staged by
`script/android/stage-node.sh` (pinned versions + SHA256, `patchelf` renames
the versioned `.so` names to `lib<name>.so`).

| Component | Version | Licence | Upstream |
|---|---|---|---|
| Node.js (`node`) | 24.22.2 (nodejs-lts) | MIT | https://nodejs.org/ |
| npm | 12.2.2 | Artistic License 2.0 | https://www.npmjs.com/ |
| corepack | (bundled with Node) | MIT | https://nodejs.org/ |
| ICU | 79.1 | Unicode License v3 | https://icu.unicode.org/ |
| OpenSSL | 3.6.5 | Apache-2.0 | https://www.openssl.org/ |
| libc++ (`libc++_shared.so`) | 30 | Apache-2.0 + LLVM Exception | https://libcxx.llvm.org/ |
| c-ares | 1.34.9 | MIT | https://c-ares.org/ |
| SQLite | 3.54.1 | Public domain | https://www.sqlite.org/ |
| zlib | 1.4 | zlib License | https://zlib.net/ |
| CA certificates bundle | 2026.11.05 | (public data, see below) | https://www.mozilla.org/ |

The npm tree (`lib/node_modules/npm/`) and the CA bundle (`etc/tls/cert.pem`)
ship as `assets/node/` and are extracted to `files/engine/node/` at first
launch (NodeAssets). npm is invoked as
`node <files>/engine/node/lib/node_modules/npm/bin/npm-cli.js` — the `.js`
entry points carry a dead Termux shebang and cannot be executed directly.
The CA bundle is Mozilla's list as shipped by Termux's `ca-certificates`
package; no licence text applies to the certificate data itself.

Node.js MIT licence text (copyright Node.js contributors):

```
Copyright Node.js contributors. All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

npm's Artistic License 2.0 text is in the bundled tree
(`assets/node/lib/node_modules/npm/LICENSE`). The Unicode License v3 text is
at https://www.unicode.org/license.html, the Apache-2.0 text at
https://www.apache.org/licenses/LICENSE-2.0, and the zlib licence text is:

```
Copyright (C) 1995-2024 Jean-loup Gailly and Mark Adler

This software is provided 'as-is', without any express or implied
warranty. In no event will the authors be held liable for any damages
arising from the use of this software.

Permission is granted to anyone to use this software for any purpose,
including commercial applications, and to alter it and redistribute it
freely, subject to the following restrictions:

1. The origin of this software must not be misrepresented; you must not
   claim that you wrote the original software. If you use this software
   in a product, an acknowledgment in the product documentation would be
   appreciated but is not required.
2. Altered source versions must be plainly marked as such, and must not be
   misrepresented as being the original software.
3. This notice may not be removed or altered from any source distribution.
```

### OpenCode engine — `libopencode.so`

The engine is a compiled Bun binary built from the upstream `@opencode/*`
packages (v2.2.2). See `engine/README.md` and `engine/patches/` for the exact
patches applied. Its licence is the upstream project's licence; check
`LICENSE` in the upstream repository at the pinned commit.

### Bun runtime (inside `libopencode.so`)

The engine is a `bun build --compile` output, so the Bun runtime is embedded.
Bun is MIT-licensed. https://github.com/oven-sh/bun

## Bundled libraries and fonts

These are linked into the app or shipped as assets, so they travel inside
the APK. All are Apache-2.0 except the two fonts (OFL-1.1). Versions are
the ones in `android/gradle/libs.versions.toml`.

| Component | Version | Licence | Upstream |
|---|---|---|---|
| OkHttp | 4.12.0 | Apache-2.0 | https://square.github.io/okhttp/ |
| kotlinx-coroutines | 1.9.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| kotlinx-serialization | 1.7.3 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization |
| Jetpack Compose (BOM) | 2024.12.01 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| activity-compose | 1.8.2 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| lifecycle-runtime-compose / lifecycle-viewmodel-compose | 2.8.3 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| Kotlin standard library | 2.1.0 | Apache-2.0 | https://kotlinlang.org/ |
| Inter (bundled TTFs in `core/design/src/main/res/font/`) | — | OFL-1.1 | https://github.com/rsms/inter |
| JetBrains Mono (bundled TTFs in `core/design/src/main/res/font/`) | — | OFL-1.1 | https://github.com/JetBrains/JetBrainsMono |

Apache-2.0 licence text: https://www.apache.org/licenses/LICENSE-2.0.
The OFL-1.1 text is in `licenses/OFL.txt`.

Test-only dependencies (JUnit, `org.json`, `kotlinx-coroutines-test`) are
not shipped in the APK, so they are not listed here.

---

## Licence texts

### ripgrep — MIT

```
Copyright (c) 2015 Andrew Gallant

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

### ripgrep — Unlicense

```
This is free and unencumbered software released into the public domain.

Anyone is free to copy, modify, publish, use, compile, sell, or distribute this
software, either in source code form or as a compiled binary, for any purpose,
commercial or non-commercial, and by any means.
```

The full text is available at https://unlicense.org/.

### xz / tar / ar — `libxz.so`, `libtar.so`, `libar.so` (+ support libs)

The in-app agent needs to extract `.deb` archives on-device (unpacking a
Termux Node `.deb` was the original blocker: its `data.tar.xz` could not be
decoded). The tools come from Termux apt (bionic-native, PIE, 16 KB
aligned). Staged by `script/android/stage-xz.sh` (pinned versions + SHA256,
`patchelf` renames the versioned `.so` names to `lib<name>.so`).

| Component | Version | Licence | Upstream |
|---|---|---|---|
| xz (`xz`) + liblzma | 5.8.4 | Public domain | https://tukaani.org/xz/ |
| GNU tar (`tar`) | 1.35 | GPL-3.0 | https://www.gnu.org/software/tar/ |
| GNU ar (`ar`, Termux binary name `gar`) + libbfd + libsframe | 2.47 (binutils) | GPL-3.0 | https://www.gnu.org/software/binutils/ |
| libacl | 2.4.0 | LGPL-2.1 | https://savannah.nongnu.org/projects/acl/ |
| libandroid-glob | 0.6 | BSD-3-Clause | https://github.com/termux/libandroid-glob |
| libandroid-selinux | 14.0.0.11 | Public domain | https://github.com/termux/libandroid-selinux |
| libiconv | 1.19.1 | LGPL-2.1 | https://www.gnu.org/software/libiconv/ |
| PCRE2 (`libpcre2-8.so`) | 10.49 | BSD-3-Clause | https://www.pcre.org/ |
| zstd (`libzstd.so`) | 1.5.7 | BSD-3-Clause / GPL-2.0 (dual) | https://github.com/facebook/zstd |
| zlib (`libz.so`, shared with the Node closure) | 1.4 | zlib License | https://zlib.net/ |

GPL-3.0 components (GNU tar, binutils/ar/libbfd) make this distribution
subject to GPL obligations: the Corresponding Source for the exact versions
above is the Termux package source
(https://github.com/termux/termux-packages — `packages/tar`,
`packages/binutils`), and the pinned `.deb` files are fetchable from
https://packages.termux.dev/apt/termux-main/ (pool paths and SHA256 pins are
in `script/android/stage-xz.sh`).

---

## Not bundled

Deliberately excluded, to keep the APK small and the licence surface narrow:

- **libcurl** — git is built with `NO_CURL` and `NO_OPENSSL`, so network
  operations (clone/push/fetch over https) are not available. OpenSSL and
  the CA bundle ARE now bundled (for Node.js, see above), but git was built
  without them, so it cannot use them yet; wiring git to them is a separate
  item (Tier 2, item 23 in the runtime fix list).
- **pcre2 for ripgrep** — ripgrep is built without the pcre2 feature. The engine's search
  paths use the default Rust regex engine. (PCRE2 itself IS now bundled, as a
  dependency of `tar` via libandroid-selinux; see above.)
- **A JavaScript/markdown renderer library** — the chat markdown renderer is
  written in-repo (`ui/chat/Markdown.kt`).
