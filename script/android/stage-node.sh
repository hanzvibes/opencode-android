#!/usr/bin/env bash
# Stage Node.js (Termux bionic-native) as Android native libraries.
#
# Why: the in-app agent needs a working JS runtime (`node`, `npm`) but Android
# forbids exec from app-private storage. The proven pattern (same as git/rg) is
# a bionic-native PIE binary staged as `jniLibs/<abi>/lib<tool>.so` and
# symlinked into `files/engine/tools/bin` at startup (EngineBinary).
#
# Node comes from Termux apt, NOT from nodejs.org: the official linux-arm64
# build needs glibc (`libc.so.6`) and fails on Android; the Termux build is
# bionic-native, PIE, PT_INTERP=/system/bin/linker64, 16 KB aligned, no PT_TLS.
# Verified from the real .deb files, see the research in
# `hidden_files/riset-node-termux-bundling.md` (goal workspace).
#
# What it does, per ABI:
#   1. Downloads the pinned .debs from packages.termux.dev and verifies each
#      SHA256 against the Termux `stable` index (abort on mismatch, like the
#      SHA pins in `.github/workflows/ci.yml`).
#   2. Extracts them with `dpkg-deb -x` (Linux build host has xz; the device
#      never needs an xz decoder).
#   3. Picks the trimmed closure (~101.9 MiB/arm64) and copies it to
#      `jniLibs/<abi>/` as `lib<name>.so`.
#   4. Runs `patchelf --set-soname` + `--replace-needed` so every DT_NEEDED
#      uses the unversioned name (libssl.so.3 -> libssl.so, ...). Versioned
#      names do not match the `lib<name>.so` pattern and risk not being
#      extracted by the package manager.
#   5. Verifies the ELF gates from `stage-engine.ps1`: e_machine, PT_LOAD
#      p_align >= 0x4000, and that every NEEDED name equals a staged file
#      (or a system lib).
#   6. Stages the architecture-independent npm tree + CA bundle into
#      `assets/node/` (extracted to `files/engine/node/` at first launch
#      by NodeAssets; see `OpenCodeApplication`).
#
# Usage (CI and local builds):
#   ./script/android/stage-node.sh --abi arm64-v8a --jni-out android/app/src/main/jniLibs/arm64-v8a --assets-out android/app/src/main/assets/node
#   ./script/android/stage-node.sh --abi x86_64  --jni-out android/app/src/main/jniLibs/x86_64 --no-assets
#
# Requirements: curl, dpkg-deb, patchelf, readelf (CI installs patchelf via apt).
# Pinned versions come from the Termux `stable` index fetched 2026-11-11.
# To bump: re-fetch the index, update VERSIONS + SHAS below, and re-verify the
# closure table in `hidden_files/riset-node-termux-bundling.md` §8.
set -euo pipefail

ABI=""
JNI_OUT=""
ASSETS_OUT=""
WITH_ASSETS=1
WORK_DIR=""
PATCHELF="${PATCHELF:-patchelf}"

while [ $# -gt 0 ]; do
    case "$1" in
        --abi) ABI="$2"; shift 2 ;;
        --jni-out) JNI_OUT="$2"; shift 2 ;;
        --assets-out) ASSETS_OUT="$2"; shift 2 ;;
        --no-assets) WITH_ASSETS=0; shift ;;
        --work-dir) WORK_DIR="$2"; shift 2 ;;
        --patchelf) PATCHELF="$2"; shift 2 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

[ "$ABI" = "arm64-v8a" ] || [ "$ABI" = "x86_64" ] || { echo "--abi must be arm64-v8a or x86_64" >&2; exit 2; }
[ -n "$JNI_OUT" ] || { echo "--jni-out is required" >&2; exit 2; }
if [ "$WITH_ASSETS" = "1" ] && [ -z "$ASSETS_OUT" ]; then
    echo "--assets-out is required unless --no-assets" >&2; exit 2
fi

# --- Pinned packages (Termux `stable` index, 2026-11-11) -------------------
# Format: "<package> <version> <deb-filename> <sha256>".
# npm and ca-certificates are `all` (same file for both ABIs).
NODEJS_LTS_VERSION="24.22.3"
NPM_VERSION="12.2.2"
LIBICU_VERSION="79.1"
OPENSSL_VERSION="1:3.6.5"
LIBCPP_VERSION="30"
LIBSQLITE_VERSION="3.54.1-1"
CARES_VERSION="1.34.9"
ZLIB_VERSION="1.4"
CACERT_VERSION="1:2026.11.05-1"

if [ "$ABI" = "arm64-v8a" ]; then
    DEB_ARCH="aarch64"
    EXPECTED_MACHINE="b700"
    NODEJS_LTS_SHA="80edcc73db025db0baa09be27e3d65a16e6f2a72ab925f1873a1b808ab857c28"
    LIBICU_SHA="83c980a010e957738e3567127801d66a1b9bd7e404a4fda98788048ee57d606c"
    OPENSSL_SHA="a2fb7856875e63ef6b90442752e05974813372c3ca86d2513a78ea43c67a0f8b"
    LIBCPP_SHA="53d0b84a7ba7459024257cb94d5b136fe13ef858567f65a8064b35950799f2ca"
    LIBSQLITE_SHA="661fb995f7215845007b222a85266ae99d9443e87e0604f8677edfd738e6b81a"
    CARES_SHA="623d84855e176f3f2a415d24c0e1d2f529356f4b5439bb3313e6d7ff0da4585c"
    ZLIB_SHA="e3027565ae50bcd8ba5113c988b589754ae192c9cc3e6afbe3e4ed7c06b2598e"
else
    DEB_ARCH="x86_64"
    EXPECTED_MACHINE="3e00"
    NODEJS_LTS_SHA="d73ab1303aa1a8ecca188795f536279c7ef196c4e3f72b645e443501b03247e4"
    LIBICU_SHA="541410d7b425ebde5d4027c86218081925b15dc1e5cffaa45449bdc4b2256251"
    OPENSSL_SHA="540f144592d23d35672976e175ede1b5d8cf464f153f646df5d4e6dcd8491a15"
    LIBCPP_SHA="c9bcd9a8067a19c9c0e4893a72d314a2e37d6b67ce8dd05c67b9bbacad044b4f"
    LIBSQLITE_SHA="497471806378934fec8a6f3b5db70cf52a3652828786a241e2e37eebc19112b0"
    CARES_SHA="ac96c37fcc9181d1a7095f60485457fe6c3d784a83b0e8e6e7af9b3bfceda237"
    ZLIB_SHA="55ab8f16e492d6ed03c2e2bf701af52cf54a313442dd7adedd3d6f3e3eba2e4f"
fi
NPM_SHA="edb4a1dc3a6967f1e65872c7cb7588bd3672152beab6ae633ba848931371e9e1"
CACERT_SHA="12fc350fc5ad638338b06b04835bce897615f8c93f96d30ac396cdb39b850c6b"

BASE_URL="https://packages.termux.dev/apt/termux-main"

# --- Setup ------------------------------------------------------------------
if [ -z "$WORK_DIR" ]; then
    WORK_DIR="$(mktemp -d)"
    trap 'rm -rf "$WORK_DIR"' EXIT
fi
DEBS_DIR="$WORK_DIR/debs"
EXTRACT_DIR="$WORK_DIR/extract"
PREFIX="$EXTRACT_DIR/data/data/com.termux/files/usr"
mkdir -p "$DEBS_DIR" "$EXTRACT_DIR" "$JNI_OUT"

# 84-F7: clear this script's own outputs before staging, so a library
# a newer .deb no longer ships (or that this script no longer stages)
# does not survive as a stale .so in the shared jniLibs dir. Only the
# names below are removed — the other scripts' libraries stay untouched.
rm -f "$JNI_OUT/libnode.so" "$JNI_OUT/libicudata.so" "$JNI_OUT/libicui18n.so" "$JNI_OUT/libicuuc.so" \
    "$JNI_OUT/libcrypto.so" "$JNI_OUT/libssl.so" "$JNI_OUT/libc++_shared.so" "$JNI_OUT/libcares.so" \
    "$JNI_OUT/libsqlite3.so" "$JNI_OUT/libz.so"
if [ "$WITH_ASSETS" = "1" ]; then
    mkdir -p "$ASSETS_OUT"
fi

command -v "$PATCHELF" >/dev/null || { echo "patchelf not found (set --patchelf or install it)" >&2; exit 1; }
"$PATCHELF" --version

download() { # $1=filename $2=sha256 $3=pool path
    local file="$1"
    local expected="$2"
    # NOTE: do not merge this into the `local` line above: bash expands all
    # right-hand sides before any of the assignments, so $file would be
    # unset here (and `set -u` aborts).
    local path="$DEBS_DIR/$file"
    if [ ! -f "$path" ]; then
        echo "Downloading $file..."
        curl -f -sL --retry 3 --retry-delay 2 -4 "$BASE_URL/$3" -o "$path"
    fi
    echo "$expected  $path" | sha256sum --check --strict -
}

# $1=pool path, $2=sha256; the .deb filename is the last path segment.
fetch() {
    download "$(basename "$1")" "$2" "$1"
}

fetch "pool/main/n/nodejs-lts/nodejs-lts_${NODEJS_LTS_VERSION}_${DEB_ARCH}.deb" "$NODEJS_LTS_SHA"
fetch "pool/main/n/npm/npm_${NPM_VERSION}_all.deb" "$NPM_SHA"
fetch "pool/main/libi/libicu/libicu_${LIBICU_VERSION}_${DEB_ARCH}.deb" "$LIBICU_SHA"
fetch "pool/main/o/openssl/openssl_${OPENSSL_VERSION}_${DEB_ARCH}.deb" "$OPENSSL_SHA"
fetch "pool/main/libc/libc++/libc++_${LIBCPP_VERSION}_${DEB_ARCH}.deb" "$LIBCPP_SHA"
fetch "pool/main/libs/libsqlite/libsqlite_${LIBSQLITE_VERSION}_${DEB_ARCH}.deb" "$LIBSQLITE_SHA"
fetch "pool/main/c/c-ares/c-ares_${CARES_VERSION}_${DEB_ARCH}.deb" "$CARES_SHA"
fetch "pool/main/z/zlib/zlib_${ZLIB_VERSION}_${DEB_ARCH}.deb" "$ZLIB_SHA"
fetch "pool/main/c/ca-certificates/ca-certificates_${CACERT_VERSION}_all.deb" "$CACERT_SHA"

for deb in "$DEBS_DIR"/*.deb; do
    echo "Extracting $(basename "$deb")..."
    dpkg-deb -x "$deb" "$EXTRACT_DIR"
done

# --- Pick the trimmed closure ------------------------------------------------
# Source file (glob, exactly one match each) -> staged lib name.
# Versioned names are renamed by patchelf below; the file on disk already
# carries the final name so the package manager sees only `lib<name>.so`.
stage_lib() { # $1=glob under $PREFIX/lib $2=staged name
    local matches=("$PREFIX"/lib/$1)
    [ "${#matches[@]}" -eq 1 ] || { echo "expected exactly one match for $1, got ${#matches[@]}" >&2; exit 1; }
    cp "${matches[0]}" "$JNI_OUT/$2"
}

# The node binary lives in bin/, not lib/.
cp "$PREFIX/bin/node" "$JNI_OUT/libnode.so"
# Exact real files (the unversioned names in the .debs are symlinks to these).
stage_lib "libicudata.so.79.1" "libicudata.so"
stage_lib "libicui18n.so.79.1" "libicui18n.so"
stage_lib "libicuuc.so.79.1" "libicuuc.so"
stage_lib "libcrypto.so.3" "libcrypto.so"
stage_lib "libssl.so.3" "libssl.so"
stage_lib "libc++_shared.so" "libc++_shared.so"
stage_lib "libcares.so" "libcares.so"
stage_lib "libsqlite3.so.3.54.1" "libsqlite3.so"
stage_lib "libz.so.1.4" "libz.so"

# --- patchelf: versioned DT_NEEDED -> unversioned ---------------------------
# Applied to the binary AND to every lib that needs another versioned lib.
# Map (old -> new):
declare -A RENAME=(
    [libssl.so.3]=libssl.so
    [libcrypto.so.3]=libcrypto.so
    [libicui18n.so.79]=libicui18n.so
    [libicuuc.so.79]=libicuuc.so
    [libicudata.so.79]=libicudata.so
    [libz.so.1]=libz.so
)

for lib in "$JNI_OUT"/lib*.so; do
    name="$(basename "$lib")"
    # Batch every patchelf edit for a file into ONE invocation: each
    # invocation rewrites the whole ELF and pads sections, so one edit per
    # call grew libnode.so by ~2 MB per --replace-needed (~10 MB total).
    # One call for all edits keeps the growth to ~2 MB per file.
    args=()
    # SONAME: the loader records the name the lib was loaded under; keep it
    # equal to the staged filename. The node binary is an executable, not a
    # library, so it gets no SONAME.
    if [ "$name" != "libnode.so" ]; then
        args+=(--set-soname "$name")
    fi
    needed="$("$PATCHELF" --print-needed "$lib" || true)"
    for old in "${!RENAME[@]}"; do
        if printf '%s\n' "$needed" | grep -qx "$old"; then
            args+=(--replace-needed "$old" "${RENAME[$old]}")
        fi
    done
    if [ "${#args[@]}" -gt 0 ]; then
        "$PATCHELF" "${args[@]}" "$lib"
    fi
done

# --- ELF gates (same as stage-engine.ps1) ------------------------------------
check_elf() { # $1=file
    local file="$1" magic machine
    magic="$(od -An -tx1 -N4 "$file" | tr -d ' \n')"
    machine="$(od -An -tx1 -j18 -N2 "$file" | tr -d ' \n')"
    [ "$magic" = "7f454c46" ] || { echo "not an ELF file: $file" >&2; exit 1; }
    [ "$machine" = "$EXPECTED_MACHINE" ] || { echo "architecture mismatch: $file reports e_machine=0x$machine, expected 0x$EXPECTED_MACHINE" >&2; exit 1; }
    # 16 KB gate: every PT_LOAD segment must have p_align >= 0x4000.
    # (readelf -lW prints one line per LOAD segment; the last field is Align.)
    local align
    while IFS= read -r align; do
        [ "$((align))" -ge 16384 ] || { echo "$file has a PT_LOAD segment with p_align=$align; needs 0x4000 for 16 KB page sizes" >&2; exit 1; }
    done < <(readelf -lW "$file" | awk '$1 == "LOAD" { print $NF }')
    # No versioned NEEDED may survive: every needed lib must be a staged file
    # or a system library (libc/libm/libdl, provided by bionic). liblog is
    # an NDK system library in /system/lib64 (linked by libandroid-selinux,
    # staged by stage-xz.sh); the gate runs over the whole jniLibs dir, so
    # it must allow it even though this script stages no lib that needs it
    # -- otherwise a later step's gate fails on the xz step's libs (PR #13
    # CI: stage-curl.sh failed on libandroid-selinux.so needing liblog.so).
    local n
    while IFS= read -r n; do
        case "$n" in
            libc.so|libm.so|libdl.so|liblog.so) ;;
            lib*.so) [ -f "$JNI_OUT/$n" ] || { echo "$file still needs $n, which is not staged" >&2; exit 1; } ;;
            lib*.so.*) base="${n%%.so.*}.so"; [ -f "$JNI_OUT/$base" ] || { echo "$file still needs $n (versioned; staged as $base), which is not staged" >&2; exit 1; } ;;
            *) ;;
        esac
    done < <("$PATCHELF" --print-needed "$file")
}

total=0
for lib in "$JNI_OUT"/lib*.so; do
    check_elf "$lib"
    size="$(wc -c < "$lib")"
    total=$((total + size))
    echo "$ABI staged: $(basename "$lib") ($size B)"
done
echo "$ABI node closure: $total B (target ~101.9 MiB incl. npm assets)"

# --- Assets: npm tree + corepack + CA bundle --------------------------------
if [ "$WITH_ASSETS" = "1" ]; then
    # 84-F7: wipe the whole assets dir, not just lib/ and etc/ — a file a
    # newer npm no longer ships would otherwise linger at the top level.
    rm -rf "$ASSETS_OUT"
    mkdir -p "$ASSETS_OUT/lib/node_modules" "$ASSETS_OUT/etc/tls"
    cp -r "$PREFIX/lib/node_modules/npm" "$ASSETS_OUT/lib/node_modules/npm"
    cp -r "$PREFIX/lib/node_modules/corepack" "$ASSETS_OUT/lib/node_modules/corepack"
    # Trim npm's internal man pages (248,962 B) and docs (1,953,743 B);
    # neither is ever read on-device.
    rm -rf "$ASSETS_OUT/lib/node_modules/npm/man" "$ASSETS_OUT/lib/node_modules/npm/docs"
    cp "$PREFIX/etc/tls/cert.pem" "$ASSETS_OUT/etc/tls/cert.pem"
    # The npm tree must not contain symlinks to Termux paths: npm-cli.js and
    # friends are real files; the bin/ symlinks (npm -> ../lib/...) are dropped
    # because v1 invokes `node <path>/npm-cli.js` explicitly (see BUILD.md).
    echo "assets staged: $(du -sb "$ASSETS_OUT" | cut -f1) B in $ASSETS_OUT"
fi

echo "stage-node done for $ABI"
