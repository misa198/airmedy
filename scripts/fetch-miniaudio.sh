#!/bin/bash

set -e

# Version and paths
VERSION="0.11.25"
REPO_URL="https://github.com/mackron/miniaudio"
DEST_DIR="internal/infra/audio/miniaudio"
DEST_FILE="${DEST_DIR}/miniaudio.h"

# Create a temporary directory
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

echo "Fetching miniaudio v${VERSION}..."

# Clone the repository
git clone --depth 1 --branch "${VERSION}" "${REPO_URL}" "$TMP_DIR" --quiet

# Ensure destination directory exists
mkdir -p "$DEST_DIR"

# Copy miniaudio.h
cp "$TMP_DIR/miniaudio.h" "$DEST_FILE"

echo "Successfully fetched miniaudio.h to ${DEST_FILE}"
