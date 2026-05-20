#!/usr/bin/env bash
# verify-lint.sh - Run Go linter locally via Docker.
#
# This script ensures dependencies (secrets, frontend assets) are present
# and runs the linter using the specified Docker image.
#
# Usage: bash scripts/verify-lint.sh [version]

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LINT_VERSION="${1:-v2.5.0}" # Using version specified by user

echo "==> Preparing environment..."

# 1. Ensure dummy secrets exist (avoids typecheck errors)
mkdir -p "${REPO_ROOT}/internal/app/config"
if [[ ! -f "${REPO_ROOT}/internal/app/config/secrets.go" ]]; then
    echo "    Creating dummy secrets.go..."
    cat <<EOF > "${REPO_ROOT}/internal/app/config/secrets.go"
package config
var (
	LastFmAPIKey    = "dummy"
	LastFmAPISecret = "dummy"
)
EOF
fi

# 2. Ensure frontend is built (avoids go:embed errors)
if [[ ! -d "${REPO_ROOT}/frontend/dist" ]]; then
    echo "    Frontend dist not found. Building..."
    if ! command -v pnpm &>/dev/null; then
        echo "Error: pnpm not found. Please install pnpm to build frontend assets."
        exit 1
    fi
    (cd "${REPO_ROOT}/frontend" && pnpm install && pnpm build)
fi

echo "==> Running golangci-lint ${LINT_VERSION} via Docker..."

docker run --rm \
    -v "${REPO_ROOT}:/app" \
    -v "$(go env GOCACHE):/root/.cache/go-build" \
    -v "$(go env GOPATH)/pkg:/go/pkg" \
    -w /app \
    --entrypoint bash \
    "golangci/golangci-lint:${LINT_VERSION}" \
    -c "apt-get update && apt-get install -y libgtk-3-dev libwebkit2gtk-4.1-dev libgtk-4-dev libwebkitgtk-6.0-dev && golangci-lint run -v"
