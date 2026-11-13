#!/bin/sh
# Fails if jniLibs/<abi>/ holds a .so no staging script produces (84-F7).
#
# Each stage script cleans its own outputs before staging, and the engine
# step cleans its destination — but a library stops being staged the moment
# a script drops it from its list, and nothing then removes the file from a
# reused workdir. This gate catches exactly that: every .so in the output
# dirs must be one the scripts stage, or one of the engine's own libraries.
#
# The expected set is derived from the stage scripts themselves (the second
# argument of every `stage_lib` call and the target of every bin copy), so
# it cannot drift from them — adding a library to a script automatically
# allows it here. The engine libraries (libopencode.so, and libgit.so /
# librg.so when the upstream APK carries them) are allowed unconditionally.
#
# Usage: ./script/ci/check-jni-manifest.sh [jniLibs-dir]
# Defaults to android/app/src/main/jniLibs under the repo root.
set -eu

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JNI_ROOT="${1:-$REPO_ROOT/android/app/src/main/jniLibs}"

# Expected names, one per line, from the stage scripts (single source of
# truth) plus the engine's own libraries.
expected=$(
    {
        grep -h 'stage_lib "' "$REPO_ROOT"/script/android/stage-*.sh
        grep -h 'cp "\$PREFIX/bin/' "$REPO_ROOT"/script/android/stage-*.sh
    } | sed -n \
        -e 's/.*"\$JNI_OUT\/\([^"]*\)".*/\1/p' \
        -e 's/^stage_lib "[^"]*" "\([^"]*\)".*/\1/p' |
    sort -u
    printf '%s\n' libopencode.so libgit.so librg.so
)

fail=0
for abi in arm64-v8a x86_64; do
    dir="$JNI_ROOT/$abi"
    [ -d "$dir" ] || continue
    for f in "$dir"/*.so; do
        [ -e "$f" ] || continue
        name=$(basename "$f")
        if ! printf '%s\n' "$expected" | grep -qx "$name"; then
            printf 'unexpected .so in %s: %s (no stage script produces it — stale file from an older staging run?)\n' "$abi" "$name" >&2
            fail=1
        fi
    done
done

if [ "$fail" = "1" ]; then
    printf 'jniLibs manifest check FAILED: remove the files above, or add them to a stage script if they belong.\n' >&2
    exit 1
fi
printf 'jniLibs manifest check OK (%s)\n' "$JNI_ROOT"
