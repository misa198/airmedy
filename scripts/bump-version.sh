#!/usr/bin/env bash
# Bumps the application version across all platform build files.
# Usage: bash scripts/bump-version.sh <new-version>
# Example: bash scripts/bump-version.sh 0.0.13

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# --- Validate argument ---
if [[ $# -ne 1 ]]; then
    echo "Error: expected exactly one argument" >&2
    echo "Usage: bash scripts/bump-version.sh <new-version>" >&2
    exit 1
fi

NEW_VERSION="$1"

if ! [[ "$NEW_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Error: version must match X.Y.Z format (got: $NEW_VERSION)" >&2
    exit 1
fi

# --- Detect current version ---
META_GO="${REPO_ROOT}/internal/app/config/meta.go"
OLD_VERSION=$(grep -E 'Version\s+=' "$META_GO" | sed 's/.*"\(.*\)".*/\1/')

if [[ -z "$OLD_VERSION" ]]; then
    echo "Error: could not detect current version from $META_GO" >&2
    exit 1
fi

echo "Bumping version: $OLD_VERSION → $NEW_VERSION"

# --- macOS-safe sed wrapper ---
sedi() {
    if [[ "$(uname)" == "Darwin" ]]; then
        sed -i '' "$@"
    else
        sed -i "$@"
    fi
}

# --- Update each file ---

# build/config.yml
sedi "s/version: \"${OLD_VERSION}\"/version: \"${NEW_VERSION}\"/" \
    "${REPO_ROOT}/build/config.yml"
echo "  updated build/config.yml"

# build/darwin/Info.dev.plist (CFBundleShortVersionString + CFBundleVersion)
sedi "s/<string>${OLD_VERSION}<\/string>/<string>${NEW_VERSION}<\/string>/g" \
    "${REPO_ROOT}/build/darwin/Info.dev.plist"
echo "  updated build/darwin/Info.dev.plist"

# build/darwin/Info.plist
sedi "s/<string>${OLD_VERSION}<\/string>/<string>${NEW_VERSION}<\/string>/g" \
    "${REPO_ROOT}/build/darwin/Info.plist"
echo "  updated build/darwin/Info.plist"

# build/linux/nfpm/nfpm.yaml
sedi "s/^version: \"${OLD_VERSION}\"/version: \"${NEW_VERSION}\"/" \
    "${REPO_ROOT}/build/linux/nfpm/nfpm.yaml"
echo "  updated build/linux/nfpm/nfpm.yaml"

# build/linux/org.wails.airmedy.metainfo.xml (AppStream release entry: version +
# today's date so app centers show an accurate "last updated").
TODAY=$(date +%F)
sedi "s|<release version=\"[^\"]*\" date=\"[^\"]*\" />|<release version=\"${NEW_VERSION}\" date=\"${TODAY}\" />|" \
    "${REPO_ROOT}/build/linux/org.wails.airmedy.metainfo.xml"
echo "  updated build/linux/org.wails.airmedy.metainfo.xml"

# build/windows/info.json
sedi "s/\"${OLD_VERSION}\"/\"${NEW_VERSION}\"/g" \
    "${REPO_ROOT}/build/windows/info.json"
echo "  updated build/windows/info.json"

# build/windows/nsis/wails_tools.nsh
sedi "s/!define INFO_PRODUCTVERSION \"${OLD_VERSION}\"/!define INFO_PRODUCTVERSION \"${NEW_VERSION}\"/" \
    "${REPO_ROOT}/build/windows/nsis/wails_tools.nsh"
echo "  updated build/windows/nsis/wails_tools.nsh"

# build/windows/wails.exe.manifest (version="X.Y.Z" in assemblyIdentity)
sedi "s/version=\"${OLD_VERSION}\"/version=\"${NEW_VERSION}\"/" \
    "${REPO_ROOT}/build/windows/wails.exe.manifest"
echo "  updated build/windows/wails.exe.manifest"

# frontend/package.json
sedi "s/\"version\": \"${OLD_VERSION}\"/\"version\": \"${NEW_VERSION}\"/" \
    "${REPO_ROOT}/frontend/package.json"
echo "  updated frontend/package.json"

# internal/app/config/meta.go
sedi "s/Version    = \"${OLD_VERSION}\"/Version    = \"${NEW_VERSION}\"/" \
    "${REPO_ROOT}/internal/app/config/meta.go"
echo "  updated internal/app/config/meta.go"

echo ""
echo "Done. Version bumped to $NEW_VERSION"
