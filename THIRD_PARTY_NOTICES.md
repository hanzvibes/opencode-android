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

### OpenCode engine — `libopencode.so`

The engine is a compiled Bun binary built from the upstream `@opencode/*`
packages (v2.2.2). See `engine/README.md` and `engine/patches/` for the exact
patches applied. Its licence is the upstream project's licence; check
`LICENSE` in the upstream repository at the pinned commit.

### Bun runtime (inside `libopencode.so`)

The engine is a `bun build --compile` output, so the Bun runtime is embedded.
Bun is MIT-licensed. https://github.com/oven-sh/bun

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

---

## Not bundled

Deliberately excluded, to keep the APK small and the licence surface narrow:

- **libcurl, OpenSSL, CA certificates** — git is built with `NO_CURL` and
  `NO_OPENSSL`, so network operations (clone/push/fetch over https) are not
  available. Adding them is a separate, larger decision.
- **pcre2** — ripgrep is built without the pcre2 feature. The engine's search
  paths use the default Rust regex engine.
- **A JavaScript/markdown renderer library** — the chat markdown renderer is
  written in-repo (`ui/chat/Markdown.kt`).
