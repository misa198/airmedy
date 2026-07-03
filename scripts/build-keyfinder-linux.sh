#!/usr/bin/env bash
# Build a static libkeyfinder library for Linux (amd64 + arm64).
# Output: internal/infra/audio/keyfinder_libs/linux/{amd64,arm64}/libkeyfinder.a
#         internal/infra/audio/keyfinder_libs/include/keyfinder/  (shared headers)
#
# Used by the in-process audio-analysis pipeline for musical key/mode detection
# (keyfinder_bridge.cpp). Requires FFTW3 (see build-fftw3-linux.sh — run that
# first, or this script builds it on demand).
#
# libkeyfinder is LGPL-3.0; FFTW3 (its FFT backend) is GPLv2+. See NOTICES and
# the license note in build-fftw3-linux.sh for the accepted compliance scope.
#
# Requirements:
#   - gcc, cmake, make, curl
#   - arm64 cross-compile: apt install gcc-aarch64-linux-gnu g++-aarch64-linux-gnu
#
# Usage: bash scripts/build-keyfinder-linux.sh [amd64|arm64|all]

set -euo pipefail

KEYFINDER_VERSION="2.2.8"
KEYFINDER_URL="https://github.com/mixxxdj/libkeyfinder/archive/refs/tags/${KEYFINDER_VERSION}.tar.gz"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_BASE="${REPO_ROOT}/internal/infra/audio/keyfinder_libs/linux"
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/keyfinder_libs/include"
FFTW3_BASE="${REPO_ROOT}/internal/infra/audio/fftw3_libs/linux"
FFTW3_INCLUDE="${REPO_ROOT}/internal/infra/audio/fftw3_libs/include"
BUILD_DIR="${TMPDIR:-/tmp}/keyfinder-build-airmedy-linux"

build_arch() {
    local OUT_ARCH="$1"     # amd64 or arm64
    local TOOLCHAIN_FILE="$2" # optional CMake toolchain file, "" for native

    local FFTW3_LIB="${FFTW3_BASE}/${OUT_ARCH}/libfftw3.a"
    if [[ ! -f "${FFTW3_LIB}" ]]; then
        echo "==> FFTW3 static lib missing for ${OUT_ARCH}, building it first..."
        bash "$(dirname "$0")/build-fftw3-linux.sh" "${OUT_ARCH}"
    fi

    local SRC_DIR="${BUILD_DIR}/src"
    local WORK_DIR="${BUILD_DIR}/work-${OUT_ARCH}"
    local BUILD_SUBDIR="${WORK_DIR}/build"

    echo "==> Building libkeyfinder ${KEYFINDER_VERSION} for linux/${OUT_ARCH}..."
    rm -rf "${WORK_DIR}"
    cp -R "${SRC_DIR}" "${WORK_DIR}"
    mkdir -p "${BUILD_SUBDIR}"
    cd "${BUILD_SUBDIR}"

    local CMAKE_ARGS=(
        -DCMAKE_BUILD_TYPE=Release
        -DBUILD_SHARED_LIBS=OFF
        -DBUILD_TESTING=OFF
        -DFFTW3_INCLUDE_DIR="${FFTW3_INCLUDE}"
        -DFFTW3_LIBRARY="${FFTW3_LIB}"
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON
    )
    [[ -n "${TOOLCHAIN_FILE}" ]] && CMAKE_ARGS+=(-DCMAKE_TOOLCHAIN_FILE="${TOOLCHAIN_FILE}")

    cmake .. "${CMAKE_ARGS[@]}" >/dev/null
    cmake --build . --config Release -j"$(nproc)" >/dev/null

    local LIB
    LIB="$(find "${BUILD_SUBDIR}" -name 'libkeyfinder.a' | head -1)"
    [[ -n "${LIB}" ]] || { echo "    ERROR: libkeyfinder.a not produced" >&2; exit 1; }

    mkdir -p "${OUT_BASE}/${OUT_ARCH}"
    cp "${LIB}" "${OUT_BASE}/${OUT_ARCH}/libkeyfinder.a"
    echo "    copied libkeyfinder.a -> keyfinder_libs/linux/${OUT_ARCH}/"

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

build_arm64() {
    if command -v aarch64-linux-gnu-gcc &>/dev/null && command -v aarch64-linux-gnu-g++ &>/dev/null; then
        local TOOLCHAIN_FILE="${BUILD_DIR}/aarch64-toolchain.cmake"
        cat > "${TOOLCHAIN_FILE}" << 'EOF'
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)
set(CMAKE_C_COMPILER aarch64-linux-gnu-gcc)
set(CMAKE_CXX_COMPILER aarch64-linux-gnu-g++)
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
EOF
        build_arch "arm64" "${TOOLCHAIN_FILE}"
        return 0
    fi
    return 1
}

TARGET_ARCH="${1:-all}"
case "${TARGET_ARCH}" in
    amd64|arm64|all) ;;
    *) echo "Usage: $0 [amd64|arm64|all]" >&2; exit 1 ;;
esac

mkdir -p "${BUILD_DIR}/src"
if [[ ! -f "${BUILD_DIR}/keyfinder.tar.gz" ]]; then
    echo "==> Downloading libkeyfinder ${KEYFINDER_VERSION}..."
    curl -fL "${KEYFINDER_URL}" -o "${BUILD_DIR}/keyfinder.tar.gz"
fi
echo "==> Extracting..."
rm -rf "${BUILD_DIR}/src"
mkdir -p "${BUILD_DIR}/src"
tar -xzf "${BUILD_DIR}/keyfinder.tar.gz" -C "${BUILD_DIR}/src" --strip-components=1

case "${TARGET_ARCH}" in
    amd64)
        build_arch "amd64" ""
        ;;
    arm64)
        build_arm64 || { echo "    ERROR: aarch64-linux-gnu-gcc/g++ not found (apt install gcc-aarch64-linux-gnu g++-aarch64-linux-gnu)" >&2; exit 1; }
        ;;
    all)
        build_arch "amd64" ""
        build_arm64 || echo "==> Skipping arm64: aarch64-linux-gnu-gcc/g++ not found (apt install gcc-aarch64-linux-gnu g++-aarch64-linux-gnu)"
        ;;
esac

echo ""
echo "==> Done. Output:"
du -sh "${OUT_BASE}"/* 2>/dev/null || true
