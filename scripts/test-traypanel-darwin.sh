#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT

swiftc -target "$(uname -m)-apple-macos14.0" \
  "$root/internal/infra/wails/tray_panel_darwin_test.swift" \
  "$root/internal/infra/wails/traypanel_lib/libtraypanel.a" \
  -o "$dir/tray-panel-test"
"$dir/tray-panel-test"
