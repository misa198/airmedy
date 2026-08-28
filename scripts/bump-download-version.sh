#!/usr/bin/env bash
# Bumps the download version in public/static/config.json.
# Usage: bash scripts/bump-download-version.sh <new-version> [desktop|mobile]

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONFIG_JSON="${REPO_ROOT}/public/static/config.json"

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "Usage: bash scripts/bump-download-version.sh <new-version> [desktop|mobile]" >&2
    exit 1
fi

NEW_VERSION="$1"
TARGET="${2:-desktop}"

case "$TARGET" in
    desktop) VERSION_KEY="downloadVersion" ;;
    mobile) VERSION_KEY="mobileDownloadVersion" ;;
    *) echo "Error: target must be desktop or mobile" >&2; exit 1 ;;
esac

if ! [[ "$NEW_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Error: version must match X.Y.Z format (got: $NEW_VERSION)" >&2
    exit 1
fi

OLD_VERSION=$(grep "\"${VERSION_KEY}\"" "$CONFIG_JSON" | sed 's/.*"\([0-9]*\.[0-9]*\.[0-9]*\)".*/\1/')

if [[ -z "$OLD_VERSION" ]]; then
    echo "Error: could not detect current $VERSION_KEY from $CONFIG_JSON" >&2
    exit 1
fi

echo "Bumping $TARGET download version: $OLD_VERSION → $NEW_VERSION"

if [[ "$(uname)" == "Darwin" ]]; then
    sed -i '' "s/\"${VERSION_KEY}\": \"${OLD_VERSION}\"/\"${VERSION_KEY}\": \"${NEW_VERSION}\"/" "$CONFIG_JSON"
else
    sed -i "s/\"${VERSION_KEY}\": \"${OLD_VERSION}\"/\"${VERSION_KEY}\": \"${NEW_VERSION}\"/" "$CONFIG_JSON"
fi

echo "  updated public/static/config.json"
echo ""
echo "Done. Download version bumped to $NEW_VERSION"
