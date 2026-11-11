#!/bin/sh
#
# Patches opencode-antigravity-auth plugin so non-string Request / URL objects
# are properly intercepted instead of leaking to generativelanguage.googleapis.com
# with "API key not valid".
#
# Usage:
#   ./script/android/patch-antigravity-auth.sh [search_root]
#
set -eu

ROOT="${1:-${HOME:-/data/user/0/oc.app/files/home}/.cache/opencode/npm}"

if [ ! -d "$ROOT" ]; then
    echo "Directory $ROOT not found; skipping."
    exit 0
fi

echo "Scanning $ROOT for opencode-antigravity-auth files..."

find "$ROOT" -type f -name "request.js" -path "*/opencode-antigravity-auth/*" | while read -r file; do
    echo "Patching $file..."
    sed -i 's/typeof input === "string" && input.includes("generativelanguage.googleapis.com")/((typeof input === "string" ? input : (input \&\& typeof input === "object" \&\& "url" in input ? input.url : (input ? String(input) : ""))).includes("generativelanguage.googleapis.com"))/g' "$file"
    sed -i 's/const match = input.match(\/\\\/models\\\/([^:]+):(\\w+)\/);/const _inUrl = typeof input === "string" ? input : (input \&\& typeof input === "object" \&\& "url" in input ? input.url : (input ? String(input) : "")); const match = _inUrl.match(\/\\\/models\\\/([^:]+):(\\w+)\/);/g' "$file"
done

find "$ROOT" -type f -name "v2.js" -path "*/opencode-antigravity-auth/*" | while read -r file; do
    echo "Patching $file..."
    sed -i 's/input instanceof URL ? input.toString() : input/typeof input === "string" ? input : (input \&\& typeof input === "object" \&\& "url" in input ? input.url : (input ? String(input) : input))/g' "$file"
done

echo "Done patching opencode-antigravity-auth."
