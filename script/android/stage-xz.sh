#!/usr/bin/env bash
# Stage xz / tar / ar (Termux bionic-native) as Android native libraries.
#
# Why: the in-app agent needs to extract .deb archives on-device (the exact
# block from 2026-11-11: a Termux Node .deb was downloaded but its
# data.tar.xz could not be decoded on-device). Android forbids exec from
# app-private storage, so the proven pattern (same as git/rg/node) is a
# bionic-native PIE binary staged as `jniLibs/<abi>/lib<tool>.so` and
# symlinked into `files/engine/tools/bin` at startup (EngineBinary).
#
# The tools come from Termux apt, NOT from a desktop distro: glibc builds
# fail on Android; the Termux builds are bionic-native, PIE,
# PT_INTERP=/system/bin/linker64, 16 KB aligned, no PT_TLS. Verified from
# the real .deb files, see the research in
# `hidden_files/riset-tier2-xz-curl.md` (goal workspace).
#
# What it does, per ABI:
#   1. Downloads the pinned .debs from packages.termux.dev and verifies each
#      SHA256 against the Termux `stable` index (abort on mismatch, like the
#      SHA pins in `.github/workflows/ci.yml`).
#   2. Extracts them with `dpkg-deb -x`.
#   3. Picks the trimmed closure (~4.4 MiB extracted/arm64) and copies it to
#      `jniLibs/<abi>/` as `lib<name>.so`.
#   4. Runs `patchelf --set-soname` + `--replace-needed` so every DT_NEEDED
#      uses the unversioned name (liblzma.so.5 -> liblzma.so, ...). Versioned
#      names do not match the `lib<name>.so` pattern and risk not being
#      extracted by the package manager.
#   5. Verifies the ELF gates from `stage-engine.ps1`: e_machine, PT_LOAD
#      p_align >= 0x4000, and that every NEEDED name equals a staged file
#      (or a system lib).
#
# Closure (arm64, extracted sizes):
#   xz 77,448 + liblzma 159,976
#   tar 467,080 + libacl 35,360 + libandroid-glob 10,536
#     + libandroid-selinux 179,056 + libiconv 1,083,488 + libpcre2-8 479,984
#     (libandroid-selinux also needs liblog.so, an NDK system lib in
#     /system/lib64 -- it is NOT staged, see the gate below.)
#   ar 54,936 + libbfd 1,152,384 + libzstd 820,840 + libsframe 27,552
#     + libz 74,056 (same zlib .deb as stage-node.sh; keep the pin in sync)
#   Total ~4.6 MiB; patchelf adds ~40-60 KiB per edited file (measured).
#
# Notes:
# - Termux names the GNU ar binary `gar` (g-prefixed to avoid clashing with
#   Android's own tools). It is staged as `libar.so` and exposed as `ar`.
#   Only `gar` is staged; the rest of binutils (as/ld/objdump/...) is not.
# - Only `xz` is staged from xz-utils (`unxz`/`xzcat` are the same binary
#   under another argv[0]; `xz -d` covers the use case).
# - `tar` needs libiconv (1.0 MiB) -- GNU tar links iconv for charset
#   conversion; there is no smaller tar in Termux that reads .tar.xz.
#
# Usage (CI and local builds):
#   ./script/android/stage-xz.sh --abi arm64-v8a --jni-out android/app/src/main/jniLibs/arm64-v8a
#   ./script/android/stage-xz.sh --abi x86_64  --jni-out android/app/src/main/jniLibs/x86_64
#
# Requirements: curl, dpkg-deb, patchelf, readelf (CI installs patchelf via apt).
# Pinned versions come from the Termux `stable` index fetched 2026-11-11.
# To bump: re-fetch the index, update VERSIONS + SHAS below, and re-verify the
# closure table in `hidden_files/riset-tier2-xz-curl.md`.
set -euo pipefail

ABI=""
JNI_OUT=""
WORK_DIR=""
PATCHELF="${PATCHELF:-patchelf}"

while [ $# -gt 0 ]; do
    case "$1" in
        --abi) ABI="$2"; shift 2 ;;
        --jni-out) JNI_OUT="$2"; shift 2 ;;
        --work-dir) WORK_DIR="$2"; shift 2 ;;
        --patchelf) PATCHELF="$2"; shift 2 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

[ "$ABI" = "arm64-v8a" ] || [ "$ABI" = "x86_64" ] || { echo "--abi must be arm64-v8a or x86_64" >&2; exit 2; }
[ -n "$JNI_OUT" ] || { echo "--jni-out is required" >&2; exit 2; }

# --- Pinned packages (Termux `stable` index, 2026-11-11) -------------------
# Format: "<package> <version> <deb-filename> <sha256>".
XZ_VERSION="5.8.4"
TAR_VERSION="1.35-3"
BINUTILS_VERSION="2.47"
LIBACL_VERSION="2.4.0"
LIBANDROID_GLOB_VERSION="0.6-3"
LIBANDROID_SELINUX_VERSION="14.0.0.11-1"
LIBICONV_VERSION="1.19.1"
PCRE2_VERSION="10.49"
ZSTD_VERSION="1.5.7-1"
# Shared with stage-node.sh (same .deb, same SHA): keep in sync on bump.
ZLIB_VERSION="1.4"

if [ "$ABI" = "arm64-v8a" ]; then
    DEB_ARCH="aarch64"
    EXPECTED_MACHINE="b700"
    XZ_SHA="c26e35d5393d4ad59d7bfbac93570d418996e69bc160458bee0e165844ae56e8"
    LIBLZMA_SHA="44e95e6e60dddb3705e60a344a4f009a1085a210797342b721eaff41b44033c0"
    TAR_SHA="a3847bc259b8654f621d36fa6345b1d6e93932e60e315e526eac2e3ecc5549ec"
    BINUTILS_SHA="332175eb2621c591f4ef80e335ddd42af8fef0d34cccd28123fc19ace8be8f74"
    LIBACL_SHA="cb620e33405e74b566cb05740dca488705cf0c475ec7f795fa333412211b6d92"
    LIBANDROID_GLOB_SHA="2276ae8adedf0db76c2f4ffc94cc4cceb2f4f5d78e021b54e2e046d1233e7826"
    LIBANDROID_SELINUX_SHA="00afd8c34087c2864737b51fd9d104dc5e955f6ec3c0f50c0c7ef5b4a56866b9"
    LIBICONV_SHA="a1c6323eff4f660e16ae6f5832356444b3269c19a4f61146355a49e7c4be5ed1"
    PCRE2_SHA="c27995e5f52b8ecc9b8b3e662eb08e49f82a18bfd3f34343936acc7b23964724"
    ZSTD_SHA="e1b4a5113648da8de189620ba1fce74c48b2d0833d9043391b9a1c91fb606fd3"
    ZLIB_SHA="e3027565ae50bcd8ba5113c988b589754ae192c9cc3e6afbe3e4ed7c06b2598e"
else
    DEB_ARCH="x86_64"
    EXPECTED_MACHINE="3e00"
    XZ_SHA="66c8f91dfbced4357a519381313a6a766d0938b70fc0cf965a82e2f05d8386ef"
    LIBLZMA_SHA="100ea7503a0b9ecffa45e1f168d035651f2eb7aedbebe71dd21fc0da4a84ef16"
    TAR_SHA="7e8c311c04dd4ce66d053a5f55faaf4914cfc34c4c53589c55c162f504788a7f"
    BINUTILS_SHA="c3f173ff177c793073e285907abaae6a9146baa66544b1b0ab537b2ebc2c450b"
    LIBACL_SHA="cfb92789ab1b1aad89d0918cd406e755918f2331276854a2c8a57748fb6c4049"
    LIBANDROID_GLOB_SHA="33b5d2ce934e89d50363e7ea267cea697da937d14de9fcea78cc7271956ef86e"
    LIBANDROID_SELINUX_SHA="99cf96556683ddb53f7d645ca1720e10523c4796ce5b41c583da9f89a47679ce"
    LIBICONV_SHA="3eb0c454676ad24d26c79601721b2eb3f9485c611ad0179fb86b17229344f46d"
    PCRE2_SHA="4a6f66ac12565342897ce9b5cd11ba8ce6fce1fd1a91ae679c3468d7fc2ee541"
    ZSTD_SHA="a76ba6c3d8742819bf4fff7780eb2e022269412700fc8f4471f530cc0d81833f"
    ZLIB_SHA="55ab8f16e492d6ed03c2e2bf701af52cf54a313442dd7adedd3d6f3e3eba2e4f"
fi

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
rm -f "$JNI_OUT/libxz.so" "$JNI_OUT/libtar.so" "$JNI_OUT/libar.so" "$JNI_OUT/liblzma.so" \
    "$JNI_OUT/libacl.so" "$JNI_OUT/libandroid-glob.so" "$JNI_OUT/libandroid-selinux.so" \
    "$JNI_OUT/libiconv.so" "$JNI_OUT/libpcre2-8.so" "$JNI_OUT/libbfd.so" "$JNI_OUT/libzstd.so" \
    "$JNI_OUT/libsframe.so" "$JNI_OUT/libz.so"

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

fetch "pool/main/x/xz-utils/xz-utils_${XZ_VERSION}_${DEB_ARCH}.deb" "$XZ_SHA"
fetch "pool/main/libl/liblzma/liblzma_${XZ_VERSION}_${DEB_ARCH}.deb" "$LIBLZMA_SHA"
fetch "pool/main/t/tar/tar_${TAR_VERSION}_${DEB_ARCH}.deb" "$TAR_SHA"
fetch "pool/main/b/binutils/binutils_${BINUTILS_VERSION}_${DEB_ARCH}.deb" "$BINUTILS_SHA"
fetch "pool/main/liba/libacl/libacl_${LIBACL_VERSION}_${DEB_ARCH}.deb" "$LIBACL_SHA"
fetch "pool/main/liba/libandroid-glob/libandroid-glob_${LIBANDROID_GLOB_VERSION}_${DEB_ARCH}.deb" "$LIBANDROID_GLOB_SHA"
fetch "pool/main/liba/libandroid-selinux/libandroid-selinux_${LIBANDROID_SELINUX_VERSION}_${DEB_ARCH}.deb" "$LIBANDROID_SELINUX_SHA"
fetch "pool/main/libi/libiconv/libiconv_${LIBICONV_VERSION}_${DEB_ARCH}.deb" "$LIBICONV_SHA"
fetch "pool/main/p/pcre2/pcre2_${PCRE2_VERSION}_${DEB_ARCH}.deb" "$PCRE2_SHA"
fetch "pool/main/z/zstd/zstd_${ZSTD_VERSION}_${DEB_ARCH}.deb" "$ZSTD_SHA"
fetch "pool/main/z/zlib/zlib_${ZLIB_VERSION}_${DEB_ARCH}.deb" "$ZLIB_SHA"

for deb in "$DEBS_DIR"/*.deb; do
    echo "Extracting $(basename "$deb")..."
    dpkg-deb -x "$deb" "$EXTRACT_DIR"
done

# --- Pick the trimmed closure ------------------------------------------------
# Source file (exact name under $PREFIX/lib) -> staged lib name.
# Versioned names are renamed by patchelf below; the file on disk already
# carries the final name so the package manager sees only `lib<name>.so`.
stage_lib() { # $1=exact filename under $PREFIX/lib $2=staged name
    local matches=("$PREFIX"/lib/$1)
    [ "${#matches[@]}" -eq 1 ] || { echo "expected exactly one match for $1, got ${#matches[@]}" >&2; exit 1; }
    cp "${matches[0]}" "$JNI_OUT/$2"
}

# The tool binaries live in bin/, not lib/. `gar` is Termux's name for GNU ar.
cp "$PREFIX/bin/xz" "$JNI_OUT/libxz.so"
cp "$PREFIX/bin/tar" "$JNI_OUT/libtar.so"
cp "$PREFIX/bin/gar" "$JNI_OUT/libar.so"
# Exact real files (the unversioned names in the .debs are symlinks to these).
stage_lib "liblzma.so.5.8.4" "liblzma.so"
stage_lib "libacl.so" "libacl.so"
stage_lib "libandroid-glob.so" "libandroid-glob.so"
stage_lib "libandroid-selinux.so" "libandroid-selinux.so"
stage_lib "libiconv.so" "libiconv.so"
stage_lib "libpcre2-8.so" "libpcre2-8.so"
stage_lib "libbfd-2.47.so" "libbfd.so"
stage_lib "libzstd.so.1.5.7" "libzstd.so"
stage_lib "libsframe.so.3.0.0" "libsframe.so"
stage_lib "libz.so.1.4" "libz.so"

# --- patchelf: versioned DT_NEEDED -> unversioned ---------------------------
# Applied to the binaries AND to every lib that needs another versioned lib.
# Map (old -> new):
declare -A RENAME=(
    [liblzma.so.5]=liblzma.so
    [libbfd-2.47.so]=libbfd.so
    [libzstd.so.1]=libzstd.so
    [libsframe.so.3]=libsframe.so
    [libz.so.1]=libz.so
)

for lib in "$JNI_OUT"/lib*.so; do
    name="$(basename "$lib")"
    # Batch every patchelf edit for a file into ONE invocation: each
    # invocation rewrites the whole ELF and pads sections (~40-60 KiB per
    # file, measured on xz/liblzma/libzstd; a second call adds nothing).
    # Skip files that need no edit at all (tar, libacl, libiconv,
    # libpcre2-8 are already clean) so they keep their exact .deb bytes.
    args=()
    # SONAME: the loader records the name the lib was loaded under; keep it
    # equal to the staged filename, otherwise a second load of the same lib
    # under its versioned SONAME can happen. The three tool binaries are
    # executables, not libraries, so they get no SONAME. Only rewrite the
    # SONAME when it differs (or is missing) -- a no-op --set-soname still
    # triggers a full rewrite.
    case "$name" in
        libxz.so|libtar.so|libar.so) ;;
        *)
            current_soname="$("$PATCHELF" --print-soname "$lib" 2>/dev/null || true)"
            if [ "$current_soname" != "$name" ]; then
                args+=(--set-soname "$name")
            fi
            ;;
    esac
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

# --- ELF gates (same as stage-engine.ps1 / stage-node.sh) --------------------
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
    # or a system library (libc/libm/libdl, provided by bionic; liblog is an
    # NDK system library in /system/lib64, linked by libandroid-selinux).
    local n
    while IFS= read -r n; do
        case "$n" in
            libc.so|libm.so|libdl.so|liblog.so) ;;
            lib*.so) [ -f "$JNI_OUT/$n" ] || { echo "$file still needs $n, which is not staged" >&2; exit 1; } ;;
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
echo "$ABI xz/tar/ar closure: $total B (target ~4.4 MiB extracted, ~5 MiB staged)"

echo "stage-xz done for $ABI"
