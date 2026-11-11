#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
arch="${1:-$(uname -m)}"
case "$arch" in
  arm64) target=arm64 ;;
  amd64|x86_64) target=x86_64 ;;
  *) echo "Unsupported macOS architecture: $arch" >&2; exit 1 ;;
esac

source="$root/internal/infra/wails/tray_panel_darwin.swift"
output="$root/internal/infra/wails/traypanel_lib/libtraypanel.a"
if [[ -f "$output" && "$output" -nt "$source" && "$(cat "${output}.arch")" == "$arch" ]]; then
  exit 0
fi

mkdir -p "$(dirname "$output")"
swiftc -emit-library -static -parse-as-library -target "${target}-apple-macos14.0" "$source" -o "$output"
printf '%s' "$arch" > "${output}.arch"
