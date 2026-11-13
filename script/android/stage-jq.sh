#!/usr/bin/env bash
# Stage jq (Termux bionic-native) as an Android native library.
#
# Why: the in-app agent needs `jq` for JSON wrangling from the engine
# shell (filtering API responses, reshaping tool output). Android forbids
# exec from app-private storage, so the proven pattern (same as
# git/rg/node/xz/curl) is a bionic-native PIE binary staged as
# `jniLibs/<abi>/lib<tool>.so` and symlinked into `files/engine/tools/bin`
# at startup (EngineBinary).
#
# Closure (arm64, extracted sizes): jq binary 23,648 + libjq 322,272 +
# libonig (oniguruma regex engine) 584,168 = ~0.9 MiB. jq is a single small
# C binary with almost no dependencies.
#
# Notes:
# - The jq CLI binary is staged as `libjq-cli.so` (tool name `jq`): the
#   name `libjq.so` is taken by the library itself (same situation as curl).
#
# Usage (CI and local builds):
#   ./script/android/stage-jq.sh --abi arm64-v8a --jni-out android/app/src/main/jniLibs/arm64-v8a
#   ./script/android/stage-jq.sh --abi x86_64  --jni-out android/app/src/main/jniLibs/x86_64
#
# Requirements: curl, dpkg-deb, patchelf, readelf (CI installs patchelf via apt).
# Pinned versions come from the Termux `stable` index fetched 2026-11-12.
# To bump: re-fetch the index, update VERSIONS + SHAS below, and re-verify the
# closure table in `hidden_files/riset-tier2-xz-curl.md` (Batch 6 research notes
# live in the PR body + `script/android/stage-*.sh` headers).
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

# --- Pinned packages (Termux `stable` index, 2026-11-12) -------------------
# Format: "<package> <version> <deb-filename> <sha256>".
# python-pip and ca-certificates are `all` (same file for both ABIs).
JQ_VERSION="1.8.2"
ONIGURUMA_VERSION="6.9.10-1"

if [ "$ABI" = "arm64-v8a" ]; then
    DEB_ARCH="aarch64"
    EXPECTED_MACHINE="b700"
    JQ_SHA="0eac323797c5d70c35f5fb935c58b5fd7528937584a1aebbd5c5b965d1663afa"
    ONIGURUMA_SHA="93675574c560cd2f75b32f0bd2fdaa693419dd6ac0e6c71c26ce5fd928cae1c5"
else
    DEB_ARCH="x86_64"
    EXPECTED_MACHINE="3e00"
    JQ_SHA="b0a35244083a79ff4263b85e9219361d06988d63317eaa52afee92448ae10e1b"
    ONIGURUMA_SHA="af74e1e83551d0a24f24f37f5462e3ec3db12a15588788cb1f54b7628ed8239c"
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
rm -f "$JNI_OUT/libjq-cli.so" "$JNI_OUT/libjq.so" "$JNI_OUT/libonig.so"

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

fetch "pool/main/j/jq/jq_1.8.2_${DEB_ARCH}.deb" "$JQ_SHA"
fetch "pool/main/o/oniguruma/oniguruma_6.9.10-1_${DEB_ARCH}.deb" "$ONIGURUMA_SHA"

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

# The jq binary lives in bin/, not lib/. It is staged as libjq-cli.so
# because the name libjq.so is taken by the library itself.
cp "$PREFIX/bin/jq" "$JNI_OUT/libjq-cli.so"
# Exact real files (the unversioned names in the .debs are symlinks to these).
stage_lib "libjq.so" "libjq.so"
stage_lib "libonig.so" "libonig.so"

# --- patchelf: versioned DT_NEEDED -> unversioned ---------------------------
# Applied to the binary AND to every lib that needs another versioned lib.
# Map (old -> new):
declare -A RENAME=(

)

for lib in "$JNI_OUT"/lib*.so; do
    name="$(basename "$lib")"
    # Batch every patchelf edit for a file into ONE invocation: each
    # invocation rewrites the whole ELF and pads sections (~40-60 KiB per
    # file, measured on xz/liblzma/libzstd; a second call adds nothing).
    # Skip files that need no edit at all so they keep their exact .deb bytes.
    args=()
    # SONAME: the loader records the name the lib was loaded under; keep it
    # equal to the staged filename, otherwise a second load of the same lib
    # under its versioned SONAME can happen. The CLI binaries are
    # executables, not libraries, so they get no SONAME. Only rewrite the
    # SONAME when it differs (or is missing) -- a no-op --set-soname still
    # triggers a full rewrite.
    case "$name" in
        libjq-cli.so) ;;
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
    # or a system library. Besides libc/libm/libdl/liblog (bionic + NDK),
    # libandroid.so and libmediandk.so are NDK system libraries in
    # /system/lib64 (linked by libavcodec, staged by stage-ffmpeg.sh); the
    # gate runs over the whole jniLibs dir, so it must allow them even
    # though this script stages no lib that needs them -- otherwise a later
    # step's gate fails on the ffmpeg step's libs (same lesson as PR #13's
    # liblog case).
    local n
    while IFS= read -r n; do
        case "$n" in
            libc.so|libm.so|libdl.so|liblog.so|libandroid.so|libmediandk.so) ;;
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
echo "$ABI jq closure: $total B (target ~0.9 MiB)"

echo "stage-jq done for $ABI"
