#!/usr/bin/env bash
# Bumps the Android application version and version code.
# Usage: bash scripts/bump-mobile-version.sh <new-version>
# Example: bash scripts/bump-mobile-version.sh 0.0.2

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_BUILD_FILE="${REPO_ROOT}/mobile/androidApp/build.gradle.kts"

if [[ $# -ne 1 ]]; then
    echo "Error: expected exactly one argument" >&2
    echo "Usage: bash scripts/bump-mobile-version.sh <new-version>" >&2
    exit 1
fi

NEW_VERSION="$1"

if ! [[ "$NEW_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Error: version must match X.Y.Z format (got: $NEW_VERSION)" >&2
    exit 1
fi

OLD_VERSION=$(sed -nE 's/^[[:space:]]*versionName = "([0-9]+\.[0-9]+\.[0-9]+)"/\1/p' "$ANDROID_BUILD_FILE")
OLD_VERSION_CODE=$(sed -nE 's/^[[:space:]]*versionCode = ([0-9]+)/\1/p' "$ANDROID_BUILD_FILE")

if [[ -z "$OLD_VERSION" || -z "$OLD_VERSION_CODE" ]]; then
    echo "Error: could not detect Android version from $ANDROID_BUILD_FILE" >&2
    exit 1
fi

NEW_VERSION_CODE=$((OLD_VERSION_CODE + 1))

echo "Bumping Android version: $OLD_VERSION ($OLD_VERSION_CODE) → $NEW_VERSION ($NEW_VERSION_CODE)"

sedi() {
    if [[ "$(uname)" == "Darwin" ]]; then
        sed -i '' "$@"
    else
        sed -i "$@"
    fi
}

sedi "s/versionName = \"${OLD_VERSION}\"/versionName = \"${NEW_VERSION}\"/" "$ANDROID_BUILD_FILE"
sedi "s/versionCode = ${OLD_VERSION_CODE}/versionCode = ${NEW_VERSION_CODE}/" "$ANDROID_BUILD_FILE"

echo "  updated mobile/androidApp/build.gradle.kts"
echo ""
echo "Done. Android version bumped to $NEW_VERSION ($NEW_VERSION_CODE)"
