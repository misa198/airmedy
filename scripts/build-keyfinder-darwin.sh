#!/usr/bin/env bash
# Build a static libkeyfinder library for macOS (arm64 + x86_64).
# Output: internal/infra/audio/keyfinder_libs/darwin/{arm64,amd64}/libkeyfinder.a
#         internal/infra/audio/keyfinder_libs/include/keyfinder/  (shared headers)
#
# Used by the in-process audio-analysis pipeline for musical key/mode detection
# (keyfinder_bridge.cpp). Requires FFTW3 (see build-fftw3-darwin.sh — run that
# first, or this script builds it on demand).
#
# libkeyfinder is LGPL-3.0; FFTW3 (its FFT backend) is GPLv2+. See NOTICES and
# the license note in build-fftw3-darwin.sh for the accepted compliance scope.
#
# Requirements: Xcode command line tools (clang, cmake, make).
#
# Usage:
#   bash scripts/build-keyfinder-darwin.sh          # build host arch only (default)
#   bash scripts/build-keyfinder-darwin.sh all      # build both arm64 and amd64
#   bash scripts/build-keyfinder-darwin.sh arm64    # arm64 only
#   bash scripts/build-keyfinder-darwin.sh amd64    # amd64 only

set -euo pipefail

KEYFINDER_VERSION="2.2.8"
KEYFINDER_URL="https://github.com/mixxxdj/libkeyfinder/archive/refs/tags/${KEYFINDER_VERSION}.tar.gz"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUT_BASE="${REPO_ROOT}/internal/infra/audio/keyfinder_libs/darwin"
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/keyfinder_libs/include"
FFTW3_BASE="${REPO_ROOT}/internal/infra/audio/fftw3_libs/darwin"
FFTW3_INCLUDE="${REPO_ROOT}/internal/infra/audio/fftw3_libs/include"
BUILD_DIR="${TMPDIR:-/tmp}/keyfinder-build-airmedy-darwin"
MIN_MACOS="14.0"

HOST_ARCH="$(uname -m)"
case "${HOST_ARCH}" in
    arm64)  HOST_OUT_ARCH="arm64" ;;
    x86_64) HOST_OUT_ARCH="amd64" ;;
    *) echo "ERROR: unsupported host arch ${HOST_ARCH}" >&2; exit 1 ;;
esac

build_arch() {
    local OUT_ARCH="$1"   # arm64 or amd64
    local CLANG_ARCH
    case "${OUT_ARCH}" in
        arm64) CLANG_ARCH="arm64" ;;
        amd64) CLANG_ARCH="x86_64" ;;
        *) echo "ERROR: unknown arch ${OUT_ARCH}" >&2; exit 1 ;;
    esac

    local FFTW3_LIB="${FFTW3_BASE}/${OUT_ARCH}/libfftw3.a"
    if [[ ! -f "${FFTW3_LIB}" ]]; then
        echo "==> FFTW3 static lib missing for ${OUT_ARCH}, building it first..."
        bash "${SCRIPT_DIR}/build-fftw3-darwin.sh" "${OUT_ARCH}"
    fi

    local SRC_DIR="${BUILD_DIR}/src"
    local WORK_DIR="${BUILD_DIR}/work-${OUT_ARCH}"
    local BUILD_SUBDIR="${WORK_DIR}/build"

    echo "==> Building libkeyfinder ${KEYFINDER_VERSION} static lib for darwin/${OUT_ARCH}..."
    rm -rf "${WORK_DIR}"
    cp -R "${SRC_DIR}" "${WORK_DIR}"
    mkdir -p "${BUILD_SUBDIR}"
    cd "${BUILD_SUBDIR}"

    cmake .. \
        -DCMAKE_OSX_ARCHITECTURES="${CLANG_ARCH}" \
        -DCMAKE_OSX_DEPLOYMENT_TARGET="${MIN_MACOS}" \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=OFF \
        -DBUILD_TESTING=OFF \
        -DFFTW3_INCLUDE_DIR="${FFTW3_INCLUDE}" \
        -DFFTW3_LIBRARY="${FFTW3_LIB}" \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        >/dev/null
    cmake --build . --config Release -j"$(sysctl -n hw.ncpu)" >/dev/null

    local LIB
    LIB="$(find "${BUILD_SUBDIR}" -name 'libkeyfinder.a' | head -1)"
    if [[ -z "${LIB}" ]]; then
        echo "    ERROR: libkeyfinder.a not produced" >&2
        exit 1
    fi

    mkdir -p "${OUT_BASE}/${OUT_ARCH}"
    cp "${LIB}" "${OUT_BASE}/${OUT_ARCH}/libkeyfinder.a"
    echo "    copied libkeyfinder.a -> keyfinder_libs/darwin/${OUT_ARCH}/"

    verify_keyfinder "${OUT_BASE}/${OUT_ARCH}/libkeyfinder.a"

    if [[ ! -d "${INCLUDE_OUT}" ]]; then
        echo "==> Copying libkeyfinder headers to keyfinder_libs/include/..."
        mkdir -p "${INCLUDE_OUT}/keyfinder"
        for h in audiodata chromagram chromatransform chromatransformfactory \
                 fftadapter keyclassifier keyfinder lowpassfilter \
                 lowpassfilterfactory spectrumanalyser temporalwindowfactory \
                 toneprofiles windowfunctions workspace constants exception binode; do
            cp "${SRC_DIR}/src/${h}.h" "${INCLUDE_OUT}/keyfinder/${h}.h"
        done
    fi
}

# Asserts the KeyFinder class symbol is present in libkeyfinder.a.
verify_keyfinder() {
    local LIB="$1"
    echo "==> Verifying KeyFinder symbols in $(basename "${LIB}")..."
    local SYMS
    SYMS="$(nm "${LIB}" 2>/dev/null || true)"
    grep -q "KeyFinder" <<<"${SYMS}" || { echo "    ERROR: KeyFinder symbols missing from libkeyfinder.a"; exit 1; }
    echo "    OK: KeyFinder symbols present in libkeyfinder.a"
}

mkdir -p "${BUILD_DIR}/src"
if [[ ! -f "${BUILD_DIR}/keyfinder.tar.gz" ]]; then
    echo "==> Downloading libkeyfinder ${KEYFINDER_VERSION}..."
    curl -fL "${KEYFINDER_URL}" -o "${BUILD_DIR}/keyfinder.tar.gz"
fi
echo "==> Extracting..."
rm -rf "${BUILD_DIR}/src"
mkdir -p "${BUILD_DIR}/src"
tar -xzf "${BUILD_DIR}/keyfinder.tar.gz" -C "${BUILD_DIR}/src" --strip-components=1

TARGET="${1:-host}"
case "${TARGET}" in
    host)  build_arch "${HOST_OUT_ARCH}" ;;
    arm64) build_arch "arm64" ;;
    amd64) build_arch "amd64" ;;
    all)
        build_arch "arm64"
        build_arch "amd64"
        ;;
    *)
        echo "Usage: $0 [host|all|arm64|amd64]" >&2
        exit 1
        ;;
esac

echo ""
echo "==> Done. Output:"
du -sh "${OUT_BASE}"/* 2>/dev/null || true
