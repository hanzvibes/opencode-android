#!/bin/sh
#
# Runs the same pipeline as .github/workflows/ci.yml on a developer machine.
#
# Why this exists: the workflow pulls the engine out of the upstream release
# APKs, so "just run CI locally" means downloading ~200 MB and executing it.
# Doing that by hand (or through an agent) is easy to get wrong and easy to
# refuse. Here it is one command, the bytes are pinned to the same digests the
# workflow uses, and every downloaded artifact is verified before it is used.
#
# Usage:
#   ./script/ci/run-local.sh                 # tests + stage + assembleDebug
#   ./script/ci/run-local.sh --skip-tests    # skip the unit test run
#   ./script/ci/run-local.sh --skip-stage    # assemble with whatever is staged
#   ./script/ci/run-local.sh --stage-only    # stage the native libs, stop there
#   ./script/ci/run-local.sh --help
#
# Requirements: JDK 21, Android SDK (platform 35, build-tools 35.0.0 and
# 34.0.0 — AGP still resolves 34.0.0 for the unit-test tasks), curl, unzip,
# dpkg-deb, readelf, patchelf (for the Node.js staging step; pass
# PATCHELF=<path> if it is not on PATH).
# The Gradle wrapper is used, so Gradle itself is not needed.

# Keep this script POSIX so it runs under dash, mksh and bash alike. The few
# conveniences it wants (pipefail, arrays) are opt-in, never assumed.
set -eu
(set -o pipefail) 2>/dev/null && set -o pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

# Force the Gradle wrapper client, daemon and test workers onto IPv4. The
# daemon and workers are also pinned in android/gradle.properties and
# android/build.gradle.kts, but the wrapper client itself only reads these
# environment variables — without them it defaults to IPv6 loopback and the
# daemon connection fails before any task runs.
export GRADLE_OPTS="${GRADLE_OPTS:-} -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv4Addresses=true"
export JAVA_OPTS="${JAVA_OPTS:-} -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv4Addresses=true"

# Keep in sync with .github/workflows/ci.yml — these are the upstream release
# asset digests published by GitHub for BIDZZ-RAWR/OPENCODE-ANDROID v0.1.1.
UPSTREAM_TAG="v0.1.1"
UPSTREAM_BASE="https://github.com/BIDZZ-RAWR/OPENCODE-ANDROID/releases/download/$UPSTREAM_TAG"
ARM64_SHA256="d330c1bd87be41dd3d1edebb73414bdce31dc96d26d54e64bb2887796f53af52"
X86_64_SHA256="9c60f84fb8fbde85b4d3d9a651cbeef2b16827a9784718d784c5f536266579d9"

CACHE_DIR="$REPO_ROOT/.cache/ci-apks"
RUN_TESTS=1
RUN_STAGE=1
RUN_ASSEMBLE=1

log()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }
die()  { printf '\n\033[31merror: %s\033[0m\n' "$*" >&2; exit 1; }

usage() {
    cat <<'EOF'
Runs the same pipeline as .github/workflows/ci.yml on a developer machine.

Usage:
  ./script/ci/run-local.sh                 # tests + stage + assembleDebug
  ./script/ci/run-local.sh --skip-tests    # skip the unit test run
  ./script/ci/run-local.sh --skip-stage    # assemble with whatever is staged
  ./script/ci/run-local.sh --stage-only    # stage the native libs, stop there
  ./script/ci/run-local.sh --help

Requirements: JDK 21, Android SDK (platform 35, build-tools 35.0.0), curl, unzip.
The Gradle wrapper is used, so Gradle itself is not needed.
EOF
    exit 0
}

while [ $# -gt 0 ]; do
    case "$1" in
        --skip-tests)   RUN_TESTS=0 ;;
        --skip-stage)   RUN_STAGE=0 ;;
        --stage-only)   RUN_TESTS=0; RUN_ASSEMBLE=0 ;;
        -h|--help)      usage ;;
        *)              die "unknown option: $1 (try --help)" ;;
    esac
    shift
done

# ---------------------------------------------------------------- preflight --

need() { command -v "$1" >/dev/null 2>&1 || die "missing required tool: $1"; }
need curl
need unzip
need od

# The JDK is only needed for the Gradle steps, not for staging the natives.
if [ "$RUN_TESTS" = "1" ] || [ "$RUN_ASSEMBLE" = "1" ]; then
    need java
fi

# sha256sum on Linux, shasum -a 256 on macOS.
if command -v sha256sum >/dev/null 2>&1; then
    sha256() { sha256sum "$1" | cut -d' ' -f1; }
elif command -v shasum >/dev/null 2>&1; then
    sha256() { shasum -a 256 "$1" | cut -d' ' -f1; }
else
    die "missing sha256sum (Linux) or shasum (macOS)"
fi

if [ ! -x "android/gradlew" ]; then
    die "android/gradlew is missing or not executable"
fi

# -------------------------------------------------------------------- stage --

# check_elf <file> <expected e_machine, little-endian hex>
check_elf() {
    magic=$(od -An -tx1 -N4 "$1" | tr -d ' \n')
    machine=$(od -An -tx1 -j18 -N2 "$1" | tr -d ' \n')
    [ "$magic" = "7f454c46" ] || die "not an ELF file: $1"
    [ "$machine" = "$2" ] || \
        die "architecture mismatch: $1 reports e_machine=0x$machine, expected 0x$2"
}

# fetch <abi> <expected sha256> — prints the verified APK path on stdout
fetch() {
    abi="$1"
    expected="$2"
    apk="$CACHE_DIR/app-$abi-release.apk"

    mkdir -p "$CACHE_DIR"
    if [ -f "$apk" ]; then
        have=$(sha256 "$apk")
        if [ "$have" = "$expected" ]; then
            info "$abi APK already cached and verified" >&2
            printf '%s' "$apk"
            return 0
        fi
        info "$abi APK cache is stale (sha256 $have) — re-downloading" >&2
        rm -f "$apk"
    fi

    info "downloading $abi release APK (~100 MB)..." >&2
    curl -f -sL --retry 3 --retry-delay 2 -4 \
        "$UPSTREAM_BASE/app-$abi-release.apk" -o "$apk.part"
    mv "$apk.part" "$apk"

    have=$(sha256 "$apk")
    [ "$have" = "$expected" ] || die "checksum mismatch for $abi APK: expected $expected, got $have"
    info "$abi APK verified (sha256 $have)" >&2
    printf '%s' "$apk"
}

stage_natives() {
    log "Staging native engine binaries"

    arm64_apk=$(fetch arm64-v8a "$ARM64_SHA256")
    x86_apk=$(fetch x86_64 "$X86_64_SHA256")

    for abi in arm64-v8a x86_64; do
        dest="$REPO_ROOT/android/app/src/main/jniLibs/$abi"
        mkdir -p "$dest"
        rm -f "$dest"/*.so

        case "$abi" in
            arm64-v8a) expected=b700 ;;
            x86_64)    expected=3e00 ;;
        esac

        if [ "$abi" = "arm64-v8a" ]; then
            unzip -q -j "$arm64_apk" "lib/$abi/*" -d "$dest/"
        else
            unzip -q -j "$x86_apk" "lib/$abi/*" -d "$dest/"
        fi

        engine="$dest/libopencode.so"
        [ -f "$engine" ] || die "the engine library is missing from the $abi APK: $engine"

        size_mb=$(( $(wc -c < "$engine") / 1048576 ))
        [ "$size_mb" -ge 50 ] || \
            die "$engine is only ${size_mb} MB — the APK does not contain the engine"

        check_elf "$engine" "$expected"

        for tool in git rg; do
            lib="$dest/lib$tool.so"
            if [ -f "$lib" ]; then
                check_elf "$lib" "$expected"
                info "$abi: lib$tool.so ok"
            else
                info "$abi: lib$tool.so absent — git and ripgrep will be missing from the APK"
            fi
        done

        info "$abi staged ($(ls -1 "$dest" | tr '\n' ' '))"
    done

    info "jniLibs is gitignored, so nothing here lands in a commit"

    # Node.js (Termux): staged from pinned .deb files by the same script CI
    # runs. patchelf is not on PATH by default — pass PATCHELF=<path> (on
    # this VM: ~/workspace/local-ci/tools/bin/patchelf, 0.18.0).
    log "Staging Node.js from Termux"
    bash "$REPO_ROOT/script/android/stage-node.sh" --abi arm64-v8a \
        --jni-out "$REPO_ROOT/android/app/src/main/jniLibs/arm64-v8a" \
        --assets-out "$REPO_ROOT/android/app/src/main/assets/node" \
        --patchelf "${PATCHELF:-patchelf}"
    bash "$REPO_ROOT/script/android/stage-node.sh" --abi x86_64 \
        --jni-out "$REPO_ROOT/android/app/src/main/jniLibs/x86_64" \
        --no-assets \
        --patchelf "${PATCHELF:-patchelf}"

    # xz / tar / ar (Termux): staged from pinned .deb files by the same
    # script CI runs (patchelf via PATCHELF as above).
    log "Staging xz/tar/ar from Termux"
    bash "$REPO_ROOT/script/android/stage-xz.sh" --abi arm64-v8a \
        --jni-out "$REPO_ROOT/android/app/src/main/jniLibs/arm64-v8a" \
        --patchelf "${PATCHELF:-patchelf}"
    bash "$REPO_ROOT/script/android/stage-xz.sh" --abi x86_64 \
        --jni-out "$REPO_ROOT/android/app/src/main/jniLibs/x86_64" \
        --patchelf "${PATCHELF:-patchelf}"

    # curl (Termux): staged from pinned .deb files by the same script CI
    # runs (patchelf via PATCHELF as above).
    log "Staging curl from Termux"
    bash "$REPO_ROOT/script/android/stage-curl.sh" --abi arm64-v8a \
        --jni-out "$REPO_ROOT/android/app/src/main/jniLibs/arm64-v8a" \
        --patchelf "${PATCHELF:-patchelf}"
    bash "$REPO_ROOT/script/android/stage-curl.sh" --abi x86_64 \
        --jni-out "$REPO_ROOT/android/app/src/main/jniLibs/x86_64" \
        --patchelf "${PATCHELF:-patchelf}"
}

# ------------------------------------------------------------------- build --

run_tests() {
    log "Unit tests"
    ( cd android && ./gradlew test --no-daemon )
}

assemble() {
    log "Assemble debug APKs"
    ( cd android && ./gradlew :app:assembleDebug --no-daemon )
    for apk in android/app/build/outputs/apk/debug/*.apk; do
        [ -f "$apk" ] || continue
        info "$apk"
    done
}

# -------------------------------------------------------------------- main --

if [ "$RUN_TESTS" = "1" ]; then
    run_tests
fi
if [ "$RUN_STAGE" = "1" ]; then
    stage_natives
fi
if [ "$RUN_ASSEMBLE" = "1" ]; then
    assemble
fi

log "Done"
