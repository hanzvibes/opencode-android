#!/bin/sh
# Fails if a version bump in the catalog was committed without regenerating
# the dependency lockfiles (audit-200 Fase B7, M-224).
#
# With dependency locking on, the locked versions act as strict constraints,
# so a bump in gradle/libs.versions.toml WITHOUT regenerating the locks is
# silently IGNORED — the old locked version wins and the build succeeds
# with the old dependency. CI never writes locks; it only enforces them,
# so nothing caught that drift until now.
#
# The check re-runs the canonical regeneration command (the same one
# android/build.gradle.kts documents) with --write-locks, then fails if
# any gradle.lockfile changed. Run it after the build steps: the tasks are
# then up-to-date, so the run only re-resolves and writes locks.
#
# Usage: ./script/ci/check-dependency-locks.sh
# Defaults to the repo root (the script's own location decides it).
set -eu

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT/android"

./gradlew test lint :app:assembleDebug :app:assembleRelease --write-locks --no-daemon

cd "$REPO_ROOT"
changed="$(git status --porcelain -- '*gradle.lockfile' || true)"
if [ -n "$changed" ]; then
    echo "Dependency lockfiles are stale: a catalog version changed without regenerating the locks." >&2
    echo "$changed" >&2
    echo "Regenerate them with ./gradlew test lint :app:assembleDebug :app:assembleRelease --write-locks (from android/) and commit the updated lockfiles." >&2
    exit 1
fi
echo "Dependency lockfiles match the catalog."
