#!/usr/bin/env bash
# Stage python3 + pip (Termux bionic-native) as Android native libraries.
#
# Why: the in-app agent needs a working Python runtime (`python3`, `pip`)
# for scripts, data wrangling and installing pure-Python packages from the
# engine shell. Android forbids exec from app-private storage, so the proven
# pattern (same as git/rg/node/xz/curl) is a bionic-native PIE binary staged
# as `jniLibs/<abi>/lib<tool>.so` and symlinked into `files/engine/tools/bin`
# at startup (EngineBinary). The stdlib tree ships as `assets/python/` and
# is extracted to `files/engine/python/` at first launch (PythonAssets),
# with PYTHONHOME pointed at it (EngineEnvironment) because the Termux
# prefix (/data/data/com.termux/files/usr) does not exist on the device.
#
# Closure (arm64, extracted sizes, verified by DT_NEEDED walk from the
# python binary + every lib-dynload module -- extension modules are dlopen'd
# at import time, so their NEEDED libs join the closure even though the
# binary itself does not link them):
#   python3.14 binary 4,632 + libpython3.14 5,940,864 + stdlib tree
#   22,521,478 (incl. pip 5,825,121 + lib-dynload) + support libs
#   libandroid-support, libffi, libsqlite3, libbz2, libexpat, libncursesw,
#   libpanelw, libgdbm(+_compat), libreadline, libandroid-posix-semaphore.
# Shared with the Node/curl closures (same .deb pins, keep in sync; staged
# here too so this script stays self-contained -- identical bytes, no
# duplication in the APK): libssl + libcrypto (openssl 1:3.6.5), libz
# (zlib 1.4), liblzma (5.8.4), libsqlite3 (3.54.1-1), libzstd (1.5.7-1,
# needed by the stdlib `compression.zstd` module).
#
# Notes:
# - The python binary is staged as `libpython3.so` (tool name `python3`):
#   the name `libpython3.14.so` is taken by the interpreter library itself.
#   The 4 KiB `libpython3.so` shim inside the python .deb is NOT staged --
#   nothing references it; the binary links `libpython3.14.so` directly.
# - `pip` has no binary: it is invoked as `python3 -m pip` (the canonical
#   pip invocation; the /usr/bin/pip wrapper script carries a Termux shebang
#   that cannot run on Android). Its code ships inside the stdlib tree.
# - pip's vendored certifi points at the dead Termux CA path via a symlink
#   (`.../pip/_vendor/certifi/cacert.pem` -> the Termux cert.pem). The assets
#   step replaces that symlink with a real copy of the Mozilla CA bundle
#   (same pin as stage-node.sh), so `pip install` over HTTPS works even
#   without SSL_CERT_FILE. The engine also sets SSL_CERT_FILE (Node's bundle).
# - The lib-dynload extension modules ship inside `assets/python/` (not
#   jniLibs), so this script ALSO runs patchelf --replace-needed over them:
#   their DT_NEEDED entries name versioned libs (libssl.so.3, ...), and the
#   staged files carry unversioned names. Without the rename, `import ssl`
#   (and sqlite3, bz2, lzma, curses, readline, ...) would fail at runtime.
#
# Usage (CI and local builds):
#   ./script/android/stage-python.sh --abi arm64-v8a --jni-out android/app/src/main/jniLibs/arm64-v8a --assets-out android/app/src/main/assets/python
#   ./script/android/stage-python.sh --abi x86_64  --jni-out android/app/src/main/jniLibs/x86_64 --no-assets
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
ASSETS_OUT=""
WITH_ASSETS=1

while [ $# -gt 0 ]; do
    case "$1" in
        --abi) ABI="$2"; shift 2 ;;
        --jni-out) JNI_OUT="$2"; shift 2 ;;
        --work-dir) WORK_DIR="$2"; shift 2 ;;
        --patchelf) PATCHELF="$2"; shift 2 ;;
        --assets-out) ASSETS_OUT="$2"; shift 2 ;;
        --no-assets) WITH_ASSETS=0; shift ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

[ "$ABI" = "arm64-v8a" ] || [ "$ABI" = "x86_64" ] || { echo "--abi must be arm64-v8a or x86_64" >&2; exit 2; }
[ -n "$JNI_OUT" ] || { echo "--jni-out is required" >&2; exit 2; }
if [ "$WITH_ASSETS" = "1" ] && [ -z "$ASSETS_OUT" ]; then
    echo "--assets-out is required unless --no-assets" >&2; exit 2
fi

# --- Pinned packages (Termux `stable` index, 2026-11-12) -------------------
# Format: "<package> <version> <deb-filename> <sha256>".
# python-pip and ca-certificates are `all` (same file for both ABIs).
PYTHON_VERSION="3.14.10"
PYTHON_PIP_VERSION="26.3"
LIBANDROID_SUPPORT_VERSION="29-1"
LIBFFI_VERSION="3.8.0"
LIBSQLITE_VERSION="3.54.1-1"
LIBBZ2_VERSION="1.0.8-8"
LIBEXPAT_VERSION="2.10.0"
NCURSES_VERSION="6.6.20260307+really6.5.20250830"
NCURSES_UI_LIBS_VERSION="6.6.20260307+really6.5.20250830"
GDBM_VERSION="1.26-1"
READLINE_VERSION="8.3.6"
LIBANDROID_POSIX_SEMAPHORE_VERSION="0.1-4"
OPENSSL_VERSION="1:3.6.5"
LIBLZMA_VERSION="5.8.4"
ZLIB_VERSION="1.4"
ZSTD_VERSION="1.5.7-1"
CA_CERTIFICATES_VERSION="1:2026.11.05-1"

if [ "$ABI" = "arm64-v8a" ]; then
    DEB_ARCH="aarch64"
    EXPECTED_MACHINE="b700"
    PYTHON_SHA="1a5a300c8d844f2c18a3bb61cafbffb2e93fba9a7106ebe967f06c9628d1e434"
    PYTHON_PIP_SHA="6a2f99ae2e57c636f4e92426884b7c4e074e60d5784c7e74dcf9782b8dca2492"
    LIBANDROID_SUPPORT_SHA="f2f145d6135ad4843ac9670153be3e3944dc1e6f1736d46d2306c28f2b86f517"
    LIBFFI_SHA="4f255badf74cd31f6a2801c17fa1444199c84c834b517b7c843e2fe9ebe91d77"
    LIBSQLITE_SHA="661fb995f7215845007b222a85266ae99d9443e87e0604f8677edfd738e6b81a"
    LIBBZ2_SHA="4335d7f060650b0aabef545d1334c2f9f280223d5962e13c24a00ec934b794ba"
    LIBEXPAT_SHA="0e4411f01f6fb664f3f04f9420289472f9afc30db01ea1c5d1e57fd9fa2354be"
    NCURSES_SHA="f44bbfdc3d42ec0217bffa978309390e59cea5a48a9a83226d4a496c42ad0b99"
    NCURSES_UI_LIBS_SHA="7393f369009be189b3d4ec1f9b16ebd57621d6a1b22949ae07685573950d1f37"
    GDBM_SHA="c378de833aae3cf41f3028491ea11836479d9833ab653edc27d0c0e0e691d5c5"
    READLINE_SHA="616cc73009bc0223f61caed8dd09455714455aa5ea5e180126e2f69c4c775136"
    LIBANDROID_POSIX_SEMAPHORE_SHA="0efa8677a0166315ba4e685863712eba0ca0a1732827492f38226e2723730c7a"
    OPENSSL_SHA="a2fb7856875e63ef6b90442752e05974813372c3ca86d2513a78ea43c67a0f8b"
    LIBLZMA_SHA="44e95e6e60dddb3705e60a344a4f009a1085a210797342b721eaff41b44033c0"
    ZLIB_SHA="e3027565ae50bcd8ba5113c988b589754ae192c9cc3e6afbe3e4ed7c06b2598e"
    ZSTD_SHA="e1b4a5113648da8de189620ba1fce74c48b2d0833d9043391b9a1c91fb606fd3"
    CA_CERTIFICATES_SHA="12fc350fc5ad638338b06b04835bce897615f8c93f96d30ac396cdb39b850c6b"
else
    DEB_ARCH="x86_64"
    EXPECTED_MACHINE="3e00"
    PYTHON_SHA="61e29ea9fb1b2e2f8eed7f9e7f86e4d0c16ec2941242f895ed9983fde8e1d11c"
    PYTHON_PIP_SHA="6a2f99ae2e57c636f4e92426884b7c4e074e60d5784c7e74dcf9782b8dca2492"
    LIBANDROID_SUPPORT_SHA="665900760c05959ec076082a0941f05bd452f301e5a70bdfa7383fd3404d44d5"
    LIBFFI_SHA="59121ca4bc5f5735d7bb603bd99548983064e8fde97d236e97122cb87d3452a3"
    LIBSQLITE_SHA="497471806378934fec8a6f3b5db70cf52a3652828786a241e2e37eebc19112b0"
    LIBBZ2_SHA="f6f81d38a40c1626202da1e87727e5a3e6de296b1c0eb252af8948646b164ca0"
    LIBEXPAT_SHA="2e0f6f7d54ef7d8a732148f0a2ff53594b1a8d410a707f480f143861313a0ecd"
    NCURSES_SHA="3f30f53c6a41c8450d146d4d42611a44be6ba0a8de1e8bb4dc903109a5fc3ff1"
    NCURSES_UI_LIBS_SHA="1193618e18f579eb86064e00648d80648eaec5da70c0a5b944ac1315d209d16e"
    GDBM_SHA="af6c89b403d42a98898c665fc2c48da59a7d20e6e26f651a654790661844001f"
    READLINE_SHA="e9684798d13ede05f785315f68d3ff739563db2abef0e73db4dc9b8e17ded368"
    LIBANDROID_POSIX_SEMAPHORE_SHA="4bde3f8bc00fa59761544593f0606a05e4b449801f1c79038c42ce66988d0eb0"
    OPENSSL_SHA="540f144592d23d35672976e175ede1b5d8cf464f153f646df5d4e6dcd8491a15"
    LIBLZMA_SHA="100ea7503a0b9ecffa45e1f168d035651f2eb7aedbebe71dd21fc0da4a84ef16"
    ZLIB_SHA="55ab8f16e492d6ed03c2e2bf701af52cf54a313442dd7adedd3d6f3e3eba2e4f"
    ZSTD_SHA="a76ba6c3d8742819bf4fff7780eb2e022269412700fc8f4471f530cc0d81833f"
    CA_CERTIFICATES_SHA="12fc350fc5ad638338b06b04835bce897615f8c93f96d30ac396cdb39b850c6b"
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
rm -f "$JNI_OUT/libpython3.so" "$JNI_OUT/libandroid-posix-semaphore.so" \
    "$JNI_OUT/libandroid-support.so" "$JNI_OUT/libbz2.so" "$JNI_OUT/libcrypto.so" \
    "$JNI_OUT/libexpat.so" "$JNI_OUT/libffi.so" "$JNI_OUT/libgdbm.so" "$JNI_OUT/libgdbm_compat.so" \
    "$JNI_OUT/liblzma.so" "$JNI_OUT/libncursesw.so" "$JNI_OUT/libpanelw.so" \
    "$JNI_OUT/libpython3.14.so" "$JNI_OUT/libreadline.so" "$JNI_OUT/libsqlite3.so" \
    "$JNI_OUT/libssl.so" "$JNI_OUT/libz.so" "$JNI_OUT/libzstd.so"
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

fetch "pool/main/p/python/python_3.14.10_${DEB_ARCH}.deb" "$PYTHON_SHA"
fetch "pool/main/p/python-pip/python-pip_26.3_all.deb" "$PYTHON_PIP_SHA"
fetch "pool/main/liba/libandroid-support/libandroid-support_29-1_${DEB_ARCH}.deb" "$LIBANDROID_SUPPORT_SHA"
fetch "pool/main/libf/libffi/libffi_3.8.0_${DEB_ARCH}.deb" "$LIBFFI_SHA"
fetch "pool/main/libs/libsqlite/libsqlite_3.54.1-1_${DEB_ARCH}.deb" "$LIBSQLITE_SHA"
fetch "pool/main/libb/libbz2/libbz2_1.0.8-8_${DEB_ARCH}.deb" "$LIBBZ2_SHA"
fetch "pool/main/libe/libexpat/libexpat_2.10.0_${DEB_ARCH}.deb" "$LIBEXPAT_SHA"
fetch "pool/main/n/ncurses/ncurses_6.6.20260307+really6.5.20250830_${DEB_ARCH}.deb" "$NCURSES_SHA"
fetch "pool/main/n/ncurses-ui-libs/ncurses-ui-libs_6.6.20260307+really6.5.20250830_${DEB_ARCH}.deb" "$NCURSES_UI_LIBS_SHA"
fetch "pool/main/g/gdbm/gdbm_1.26-1_${DEB_ARCH}.deb" "$GDBM_SHA"
fetch "pool/main/r/readline/readline_8.3.6_${DEB_ARCH}.deb" "$READLINE_SHA"
fetch "pool/main/liba/libandroid-posix-semaphore/libandroid-posix-semaphore_0.1-4_${DEB_ARCH}.deb" "$LIBANDROID_POSIX_SEMAPHORE_SHA"
fetch "pool/main/o/openssl/openssl_1:3.6.5_${DEB_ARCH}.deb" "$OPENSSL_SHA"
fetch "pool/main/libl/liblzma/liblzma_5.8.4_${DEB_ARCH}.deb" "$LIBLZMA_SHA"
fetch "pool/main/z/zlib/zlib_1.4_${DEB_ARCH}.deb" "$ZLIB_SHA"
fetch "pool/main/z/zstd/zstd_1.5.7-1_${DEB_ARCH}.deb" "$ZSTD_SHA"
fetch "pool/main/c/ca-certificates/ca-certificates_1:2026.11.05-1_all.deb" "$CA_CERTIFICATES_SHA"

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

# The python binary lives in bin/, not lib/. It is staged as libpython3.so
# because the name libpython3.14.so is taken by the interpreter library.
cp "$PREFIX/bin/python3.14" "$JNI_OUT/libpython3.so"
# Exact real files (the unversioned names in the .debs are symlinks to these).
stage_lib "libandroid-posix-semaphore.so" "libandroid-posix-semaphore.so"
stage_lib "libandroid-support.so" "libandroid-support.so"
stage_lib "libbz2.so.1.0.8" "libbz2.so"
stage_lib "libcrypto.so.3" "libcrypto.so"
stage_lib "libexpat.so.1.13.1" "libexpat.so"
stage_lib "libffi.so" "libffi.so"
stage_lib "libgdbm.so" "libgdbm.so"
stage_lib "libgdbm_compat.so" "libgdbm_compat.so"
stage_lib "liblzma.so.5.8.4" "liblzma.so"
stage_lib "libncursesw.so.6.5" "libncursesw.so"
stage_lib "libpanelw.so.6.5" "libpanelw.so"
stage_lib "libpython3.14.so" "libpython3.14.so"
stage_lib "libreadline.so.8.3" "libreadline.so"
stage_lib "libsqlite3.so.3.54.1" "libsqlite3.so"
stage_lib "libssl.so.3" "libssl.so"
stage_lib "libz.so.1.4" "libz.so"
stage_lib "libzstd.so.1.5.7" "libzstd.so"

# --- patchelf: versioned DT_NEEDED -> unversioned ---------------------------
# Applied to the binary AND to every lib that needs another versioned lib.
# Map (old -> new):
declare -A RENAME=(
    [libbz2.so.1.0]=libbz2.so
    [libcrypto.so.3]=libcrypto.so
    [libexpat.so.1]=libexpat.so
    [liblzma.so.5]=liblzma.so
    [libncursesw.so.6]=libncursesw.so
    [libpanelw.so.6]=libpanelw.so
    [libreadline.so.8]=libreadline.so
    [libssl.so.3]=libssl.so
    [libz.so.1]=libz.so
    [libzstd.so.1]=libzstd.so
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
        libpython3.so) ;;
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
echo "$ABI python closure: $total B (target ~16.4 MiB libs + 22.5 MiB stdlib assets)"

echo "stage-python done for $ABI"

# --- Assets: stdlib tree (+ pip) -------------------------------------------
if [ "$WITH_ASSETS" = "1" ]; then
    # 84-F7: wipe the whole assets dir, not just lib/ — a stale stdlib file
    # from an older python would otherwise linger beside the new tree.
    rm -rf "$ASSETS_OUT"
    mkdir -p "$ASSETS_OUT/lib"
    cp -r "$PREFIX/lib/python3.14" "$ASSETS_OUT/lib/python3.14"
    # The extension modules are dlopen'd at import time from this tree, so
    # their DT_NEEDED entries must use the same unversioned names as the
    # jniLibs files (see the header note). No SONAME edits: dlopen loads
    # them by filename, never by SONAME.
    for mod in "$ASSETS_OUT"/lib/python3.14/lib-dynload/*.so; do
        args=()
        needed="$("$PATCHELF" --print-needed "$mod" || true)"
        for old in "${!RENAME[@]}"; do
            if printf '%s\n' "$needed" | grep -qx "$old"; then
                args+=(--replace-needed "$old" "${RENAME[$old]}")
            fi
        done
        if [ "${#args[@]}" -gt 0 ]; then
            "$PATCHELF" "${args[@]}" "$mod"
        fi
        check_elf "$mod"
        n="$(basename "$mod")"
        while IFS= read -r need; do
            case "$need" in
                libc.so|libm.so|libdl.so|liblog.so|libandroid.so|libmediandk.so) ;;
                lib*.so) [ -f "$JNI_OUT/$need" ] || { echo "$n still needs $need, which is not staged" >&2; exit 1; } ;;
                *) ;;
            esac
        done < <("$PATCHELF" --print-needed "$mod")
    done
    # pip's vendored certifi ships as a symlink to the dead Termux CA path.
    # Replace it with a real copy of the Mozilla bundle (same pin as
    # stage-node.sh) so `pip install` verifies TLS even without SSL_CERT_FILE.
    # aapt2 cannot package a symlink to a path that does not exist at build
    # time, so this replacement is load-bearing for the build itself.
    rm -f "$ASSETS_OUT/lib/python3.14/site-packages/pip/_vendor/certifi/cacert.pem"
    cp "$PREFIX/etc/tls/cert.pem" "$ASSETS_OUT/lib/python3.14/site-packages/pip/_vendor/certifi/cacert.pem"
    echo "assets staged: $(du -sb "$ASSETS_OUT" | cut -f1) B in $ASSETS_OUT"
fi
