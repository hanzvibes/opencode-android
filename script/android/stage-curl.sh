#!/usr/bin/env bash
# Stage curl (Termux bionic-native) as an Android native library.
#
# Why: the in-app agent needs a working HTTP client (`curl`) for downloads,
# API calls and debugging network issues from the engine shell. Android
# forbids exec from app-private storage, so the proven pattern (same as
# git/rg/node/xz) is a bionic-native PIE binary staged as
# `jniLibs/<abi>/lib<tool>.so` and symlinked into `files/engine/tools/bin`
# at startup (EngineBinary).
#
# curl comes from Termux apt, NOT from a desktop distro: glibc builds fail
# on Android; the Termux build is bionic-native, PIE,
# PT_INTERP=/system/bin/linker64, 16 KB aligned, no PT_TLS. Verified from
# the real .deb files, see the research in
# `hidden_files/riset-tier2-xz-curl.md` (goal workspace).
#
# What it does, per ABI:
#   1. Downloads the pinned .debs from packages.termux.dev and verifies each
#      SHA256 against the Termux `stable` index (abort on mismatch, like the
#      SHA pins in `.github/workflows/ci.yml`).
#   2. Extracts them with `dpkg-deb -x`.
#   3. Picks the trimmed closure (~2.25 MiB new files/arm64, plus the
#      OpenSSL/zlib libs shared with the Node closure) and copies it to
#      `jniLibs/<abi>/` as `lib<name>.so`.
#   4. Runs `patchelf --set-soname` + `--replace-needed` so every DT_NEEDED
#      uses the unversioned name (libssl.so.3 -> libssl.so, ...). Versioned
#      names do not match the `lib<name>.so` pattern and risk not being
#      extracted by the package manager.
#   5. Verifies the ELF gates from `stage-engine.ps1`: e_machine, PT_LOAD
#      p_align >= 0x4000, and that every NEEDED name equals a staged file
#      (or a system lib).
#
# Closure (arm64, extracted sizes):
#   curl 312,088 + libcurl 988,536 + libnghttp2 161,504 + libnghttp3 155,824
#     + libngtcp2 343,008 + libngtcp2_crypto_ossl 44,936 + libssh2 248,232
#   Shared with the Node closure (same .deb pins as stage-node.sh, keep in
#   sync; staged here too so this script stays self-contained -- identical
#   bytes, no duplication in the APK): libssl + libcrypto (openssl 1:3.6.5),
#   libz (zlib 1.4). No libpsl / libidn2: libcurl neither Depends on nor
#   links them.
#
# Notes:
# - The curl CLI binary is staged as `libcurl-cli.so` (tool name `curl`):
#   the name `libcurl.so` is taken by the library itself.
# - TLS verification uses the CA bundle Node already ships:
#   `SSL_CERT_FILE=<files>/engine/node/etc/tls/cert.pem` (EngineEnvironment,
#   from the Node work). No new environment variables, no new assets.
# - This stages the `curl` tool only. It does NOT make `git clone https://`
#   work: the bundled git was built with NO_CURL/NO_OPENSSL, so HTTPS is
#   compiled out of git itself. Rebuilding git needs the Windows-only
#   `build-tools.ps1` plus the upstream APK rebuilt -- a separate step that
#   cannot be verified on this VM or in CI (see the research doc).
#
# Usage (CI and local builds):
#   ./script/android/stage-curl.sh --abi arm64-v8a --jni-out android/app/src/main/jniLibs/arm64-v8a
#   ./script/android/stage-curl.sh --abi x86_64  --jni-out android/app/src/main/jniLibs/x86_64
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
CURL_VERSION="8.23.0"
LIBNGHTTP2_VERSION="1.71.0"
LIBNGHTTP3_VERSION="1.19.0"
LIBNGTCP2_VERSION="1.26.0"
LIBSSH2_VERSION="1.11.1-2"
# Shared with stage-node.sh (same .deb, same SHA): keep in sync on bump.
OPENSSL_VERSION="1:3.6.5"
ZLIB_VERSION="1.4"

if [ "$ABI" = "arm64-v8a" ]; then
    DEB_ARCH="aarch64"
    EXPECTED_MACHINE="b700"
    CURL_SHA="e57a9975a828eb48790bec0da950795af165aa714f6a33d266c45a4e2898aa85"
    LIBCURL_SHA="2011bb9709261bb3d5ecaa6672faa333be14cf48ad4af37031e3e365309557ac"
    LIBNGHTTP2_SHA="ae33e7b21e0636be8686b9b13a20e31267f047f69cb26b4befd70c9a9bae221e"
    LIBNGHTTP3_SHA="8bb6fa31f9281cd48047ec9a571cadf153538f5b38f0203ad7a4e221344b14a6"
    LIBNGTCP2_SHA="8225ecde58104ef5aa234fa54a1bea5051a8a9ef2937e5ba996b3e8dda68c8eb"
    LIBSSH2_SHA="1add4e0a926b848814e7e2f1817ea28b123c54cabd5bbc4e5cfd65291cbad84e"
    OPENSSL_SHA="a2fb7856875e63ef6b90442752e05974813372c3ca86d2513a78ea43c67a0f8b"
    ZLIB_SHA="e3027565ae50bcd8ba5113c988b589754ae192c9cc3e6afbe3e4ed7c06b2598e"
else
    DEB_ARCH="x86_64"
    EXPECTED_MACHINE="3e00"
    CURL_SHA="03a68c5393759f6802a2b256891c2bca93d633d4915cdcb50e6d643c12f84fff"
    LIBCURL_SHA="fa71ce8eca3d0aa48a748fc60e4519351fc9c4d51b20cac5722190cb002cfd1c"
    LIBNGHTTP2_SHA="9bc1791438de6380038835cbd10c9cede716212cc32e37eb54407cd24f6605d6"
    LIBNGHTTP3_SHA="b3fa3f6117a8d2c1e5c6535f733282c077d39760b79bd08eac4ffba8da7bc0e6"
    LIBNGTCP2_SHA="9a4ba347834cf4593cd80df9c9ad4caaf0486946ac0e1a812d10f0d712b5eae2"
    LIBSSH2_SHA="00db1c1497e8703281efded7f1f2a63824b145c980d9ec309ae0ab7ccb9ce1c5"
    OPENSSL_SHA="540f144592d23d35672976e175ede1b5d8cf464f153f646df5d4e6dcd8491a15"
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

fetch "pool/main/c/curl/curl_${CURL_VERSION}_${DEB_ARCH}.deb" "$CURL_SHA"
fetch "pool/main/libc/libcurl/libcurl_${CURL_VERSION}_${DEB_ARCH}.deb" "$LIBCURL_SHA"
fetch "pool/main/libn/libnghttp2/libnghttp2_${LIBNGHTTP2_VERSION}_${DEB_ARCH}.deb" "$LIBNGHTTP2_SHA"
fetch "pool/main/libn/libnghttp3/libnghttp3_${LIBNGHTTP3_VERSION}_${DEB_ARCH}.deb" "$LIBNGHTTP3_SHA"
fetch "pool/main/libn/libngtcp2/libngtcp2_${LIBNGTCP2_VERSION}_${DEB_ARCH}.deb" "$LIBNGTCP2_SHA"
fetch "pool/main/libs/libssh2/libssh2_${LIBSSH2_VERSION}_${DEB_ARCH}.deb" "$LIBSSH2_SHA"
fetch "pool/main/o/openssl/openssl_${OPENSSL_VERSION}_${DEB_ARCH}.deb" "$OPENSSL_SHA"
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

# The curl binary lives in bin/, not lib/. It is staged as libcurl-cli.so
# because the name libcurl.so is taken by the library itself.
cp "$PREFIX/bin/curl" "$JNI_OUT/libcurl-cli.so"
# Exact real files (the unversioned names in the .debs are symlinks to these).
stage_lib "libcurl.so" "libcurl.so"
stage_lib "libnghttp2.so" "libnghttp2.so"
stage_lib "libnghttp3.so" "libnghttp3.so"
stage_lib "libngtcp2.so" "libngtcp2.so"
stage_lib "libngtcp2_crypto_ossl.so" "libngtcp2_crypto_ossl.so"
stage_lib "libssh2.so" "libssh2.so"
stage_lib "libcrypto.so.3" "libcrypto.so"
stage_lib "libssl.so.3" "libssl.so"
stage_lib "libz.so.1.4" "libz.so"

# --- patchelf: versioned DT_NEEDED -> unversioned ---------------------------
# Applied to the binary AND to every lib that needs another versioned lib.
# Map (old -> new):
declare -A RENAME=(
    [libssl.so.3]=libssl.so
    [libcrypto.so.3]=libcrypto.so
    [libz.so.1]=libz.so
)

for lib in "$JNI_OUT"/lib*.so; do
    name="$(basename "$lib")"
    # Batch every patchelf edit for a file into ONE invocation: each
    # invocation rewrites the whole ELF and pads sections (~40-60 KiB per
    # file, measured on xz/liblzma/libzstd; a second call adds nothing).
    # Skip files that need no edit at all (libcurl, libnghttp2, libnghttp3,
    # libngtcp2) so they keep their exact .deb bytes.
    args=()
    # SONAME: the loader records the name the lib was loaded under; keep it
    # equal to the staged filename, otherwise a second load of the same lib
    # under its versioned SONAME can happen. The curl binary is an
    # executable, not a library, so it gets no SONAME. Only rewrite the
    # SONAME when it differs (or is missing) -- a no-op --set-soname still
    # triggers a full rewrite.
    case "$name" in
        libcurl-cli.so) ;;
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
    # or a system library (libc/libm/libdl, provided by bionic). liblog is
    # an NDK system library in /system/lib64 (linked by libandroid-selinux,
    # staged by stage-xz.sh); the gate runs over the whole jniLibs dir, so
    # it must allow it even though this script stages no lib that needs it
    # -- otherwise the curl step fails on the xz step's libs (PR #13 CI).
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
echo "$ABI curl closure: $total B (target ~2.25 MiB new files + shared OpenSSL/zlib)"

echo "stage-curl done for $ABI"
