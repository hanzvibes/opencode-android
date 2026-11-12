#!/usr/bin/env bash
# Stage language servers (TypeScript + Python) for the bundled Node runtime.
#
# Why: the in-app agent needs LSP diagnostics for the files it edits, and
# the engine's `lsp` config (opencode.json) can point at servers that ship
# with the app instead of requiring the user to `npm install` them on the
# tablet. The servers run under the bundled Node (PR #11): they are pure JS,
# so there is no ABI, no ELF work and no patchelf here -- just pinned npm
# tarballs, SHA512-verified against the registry metadata, extracted into
# `assets/lsp/lib/node_modules/`, and extracted to `files/engine/lsp/` at
# first launch (LspAssets).
#
# Pinned versions (npm registry, 2026-11-12):
#   typescript-language-server 6.0.3 -- the TS server; resolves the
#     `typescript` package from the same node_modules tree at runtime, so
#     both must ship together.
#   typescript 5.9.3 -- the last JS implementation. typescript 7.x is the
#     native port: its `tsc` is a per-platform binary (glibc Linux), which
#     cannot run on Android. Pin 5.9.x until the engine moves to native TS.
#   pyright 1.1.414 -- the Python server; the npm package IS the Node
#     implementation (dist/pyright.js), it does not need the bundled
#     python3 at runtime.
#
# Layout (mirrors npm):
#   assets/lsp/lib/node_modules/typescript-language-server/lib/cli.mjs
#   assets/lsp/lib/node_modules/typescript/lib/typescript.js
#   assets/lsp/lib/node_modules/pyright/langserver.index.js
#
# Invocation (from the engine shell; `node` is on PATH via tools/bin):
#   node <files>/engine/lsp/lib/node_modules/typescript-language-server/lib/cli.mjs --stdio
#   node <files>/engine/lsp/lib/node_modules/pyright/langserver.index.js --stdio
# And in the workspace opencode.json (shape verified against the fork source,
# anomalyco/opencode @ dev, packages/schema/src/config/lsp.ts -- Server is
# {command: string[], extensions?: string[], disabled?, env?, initialization?}):
#   { "lsp": {
#       "typescript": { "command": ["node", "<files>/engine/lsp/lib/node_modules/typescript-language-server/lib/cli.mjs", "--stdio"],
#                       "extensions": [".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs"] },
#       "python":     { "command": ["node", "<files>/engine/lsp/lib/node_modules/pyright/langserver.index.js", "--stdio"],
#                       "extensions": [".py"] } } }
#
# Notes:
# - `*.map` sourcemaps are dropped (several MiB, only useful for debugging
#   the servers themselves). package.json files are kept: the servers read
#   their own version from them.
# - This script is architecture-independent and runs once (not per ABI).
#
# Usage (CI and local builds):
#   ./script/android/stage-lsp.sh --assets-out android/app/src/main/assets/lsp
#
# Requirements: curl, tar.
# To bump: update VERSIONS + INTEGRITY below from the registry metadata
# (`https://registry.npmjs.org/<pkg>/<version>` -> dist.integrity).
set -euo pipefail

ASSETS_OUT=""
WORK_DIR=""

while [ $# -gt 0 ]; do
    case "$1" in
        --assets-out) ASSETS_OUT="$2"; shift 2 ;;
        --work-dir) WORK_DIR="$2"; shift 2 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

[ -n "$ASSETS_OUT" ] || { echo "--assets-out is required" >&2; exit 2; }

# --- Pinned packages (npm registry, 2026-11-12) ------------------------------
# Format: "<package> <version> <sha512 integrity (base64, without the prefix)>".
TSSERVER_VERSION="6.0.3"
TSSERVER_INTEGRITY="iAxKyLXRNbnxGfCGZ/j4ilAb0PSNommvTvcghF8B8sq5JFGDKEQwzNEFZ0KTYEwzcA7rtRa+2OPeouiFR8L5KA=="
TYPESCRIPT_VERSION="5.9.3"
TYPESCRIPT_INTEGRITY="jl1vZzPDinLr9eUt3J/t7V6FgNEw9QjvBPdysz9KfQDD41fQrC2Y4vKQdiaUpFT4bXlb1RHhLpp8wtm6M5TgSw=="
PYRIGHT_VERSION="1.1.414"
PYRIGHT_INTEGRITY="FPZZb51jepDX4eP7TEYDeNFtmE3WgwkkEcJpvH3/QmUSsj0EAy3LXu+xB4T/FWDejtsXlCfFr/rbWFjwuwuXww=="

REGISTRY="https://registry.npmjs.org"

# --- Setup ------------------------------------------------------------------
if [ -z "$WORK_DIR" ]; then
    WORK_DIR="$(mktemp -d)"
    trap 'rm -rf "$WORK_DIR"' EXIT
fi
TARBALLS_DIR="$WORK_DIR/tarballs"
EXTRACT_DIR="$WORK_DIR/extract"
mkdir -p "$TARBALLS_DIR" "$EXTRACT_DIR" "$ASSETS_OUT/lib/node_modules"

# $1=package $2=version $3=base64 sha512 (without the "sha512-" prefix)
fetch() {
    local pkg="$1" version="$2" expected="$3"
    local file="$TARBALLS_DIR/$pkg-$version.tgz"
    if [ ! -f "$file" ]; then
        echo "Downloading $pkg@$version..."
        curl -f -sL --retry 3 --retry-delay 2 -4 "$REGISTRY/$pkg/-/$pkg-$version.tgz" -o "$file"
    fi
    # Registry integrity is base64; sha512sum prints hex. Compare in hex
    # (xxd is not installed on the CI runners, so decode with base64+od).
    local expected_hex actual_hex
    expected_hex="$(printf '%s' "$expected" | base64 -d | od -An -tx1 | tr -d ' \n')"
    actual_hex="$(sha512sum "$file" | cut -d' ' -f1)"
    [ "$actual_hex" = "$expected_hex" ] || { echo "SHA512 mismatch for $pkg@$version" >&2; exit 1; }
    echo "$pkg@$version SHA512 OK"
}

fetch "typescript-language-server" "$TSSERVER_VERSION" "$TSSERVER_INTEGRITY"
fetch "typescript" "$TYPESCRIPT_VERSION" "$TYPESCRIPT_INTEGRITY"
fetch "pyright" "$PYRIGHT_VERSION" "$PYRIGHT_INTEGRITY"

# --- Extract into assets/lsp/lib/node_modules/ ------------------------------
rm -rf "$ASSETS_OUT/lib/node_modules"
mkdir -p "$ASSETS_OUT/lib/node_modules"
# NOTE: match the tarball by exact filename, not by a "$pkg-*.tgz" glob:
# `typescript-*.tgz` also matches typescript-language-server-6.0.3.tgz,
# and tar then treats the second file as a member name ("Not found in
# archive").
for spec in "typescript-language-server $TSSERVER_VERSION" "typescript $TYPESCRIPT_VERSION" "pyright $PYRIGHT_VERSION"; do
    pkg="${spec%% *}"
    version="${spec##* }"
    rm -rf "$EXTRACT_DIR/$pkg"
    mkdir -p "$EXTRACT_DIR/$pkg"
    # The tarball contains a single top-level `package/` dir; strip it.
    tar -xzf "$TARBALLS_DIR/$pkg-$version.tgz" -C "$EXTRACT_DIR/$pkg" --strip-components=1
    cp -r "$EXTRACT_DIR/$pkg" "$ASSETS_OUT/lib/node_modules/$pkg"
done
# Drop sourcemaps (only useful for debugging the servers themselves).
find "$ASSETS_OUT" -name "*.map" -delete
# The entry points the engine config invokes must exist, or the sync
# sentinel in LspAssets (cli.mjs) would never be written and the next
# launch would retry the copy forever.
for entry in \
    "$ASSETS_OUT/lib/node_modules/typescript-language-server/lib/cli.mjs" \
    "$ASSETS_OUT/lib/node_modules/typescript/lib/typescript.js" \
    "$ASSETS_OUT/lib/node_modules/pyright/langserver.index.js" \
; do
    [ -f "$entry" ] || { echo "missing LSP entry point: $entry" >&2; exit 1; }
done
# No symlinks may survive into the APK assets: aapt2 cannot package a
# symlink whose target does not exist at build time.
if find "$ASSETS_OUT" -type l | grep -q .; then
    echo "symlinks found in LSP assets:" >&2
    find "$ASSETS_OUT" -type l >&2
    exit 1
fi

echo "assets staged: $(du -sb "$ASSETS_OUT" | cut -f1) B in $ASSETS_OUT"
echo "stage-lsp done"
