#!/usr/bin/env bash
# Bumps the download version in public/config.json.
# Usage: bash scripts/bump-download-version.sh <new-version>
# Example: bash scripts/bump-download-version.sh 0.0.12

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONFIG_JSON="${REPO_ROOT}/public/config.json"

if [[ $# -ne 1 ]]; then
    echo "Error: expected exactly one argument" >&2
    echo "Usage: bash scripts/bump-download-version.sh <new-version>" >&2
    exit 1
fi

NEW_VERSION="$1"

if ! [[ "$NEW_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Error: version must match X.Y.Z format (got: $NEW_VERSION)" >&2
    exit 1
fi

OLD_VERSION=$(grep '"downloadVersion"' "$CONFIG_JSON" | sed 's/.*"\([0-9]*\.[0-9]*\.[0-9]*\)".*/\1/')

if [[ -z "$OLD_VERSION" ]]; then
    echo "Error: could not detect current downloadVersion from $CONFIG_JSON" >&2
    exit 1
fi

echo "Bumping download version: $OLD_VERSION → $NEW_VERSION"

if [[ "$(uname)" == "Darwin" ]]; then
    sed -i '' "s/\"downloadVersion\": \"${OLD_VERSION}\"/\"downloadVersion\": \"${NEW_VERSION}\"/" "$CONFIG_JSON"
else
    sed -i "s/\"downloadVersion\": \"${OLD_VERSION}\"/\"downloadVersion\": \"${NEW_VERSION}\"/" "$CONFIG_JSON"
fi

echo "  updated public/config.json"
echo ""
echo "Done. Download version bumped to $NEW_VERSION"
