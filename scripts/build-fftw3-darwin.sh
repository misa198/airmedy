#!/usr/bin/env bash
# Build a static FFTW3 (double precision) library for macOS (arm64 + x86_64).
# Output: internal/infra/audio/fftw3_libs/darwin/{arm64,amd64}/libfftw3.a
#         internal/infra/audio/fftw3_libs/include/  (fftw3.h)
#
# Required by libkeyfinder (musical key/mode detection, keyfinder_bridge.cpp) —
# its CMakeLists.txt has `find_package(FFTW3 REQUIRED)`, no bundled fallback.
#
# NOTE: FFTW3 is licensed under the GNU GPL v2 or later — NOT LGPL. Statically
# linking it into this (MIT-licensed) app's shipped binary GPL-encumbers that
# binary. This is a deliberate, accepted tradeoff (see NOTICES) — do not treat
# this comment as license advice; confirm scope with the project owner before
# distributing a build that includes this library.
#
# Requirements: Xcode command line tools (clang, make).
#
# Usage:
#   bash scripts/build-fftw3-darwin.sh          # build host arch only (default)
#   bash scripts/build-fftw3-darwin.sh all      # build both arm64 and amd64
#   bash scripts/build-fftw3-darwin.sh arm64    # arm64 only
#   bash scripts/build-fftw3-darwin.sh amd64    # amd64 only

set -euo pipefail

FFTW_VERSION="3.3.10"
FFTW_URL="https://www.fftw.org/fftw-${FFTW_VERSION}.tar.gz"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUT_BASE="${REPO_ROOT}/internal/infra/audio/fftw3_libs/darwin"
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/fftw3_libs/include"
BUILD_DIR="${TMPDIR:-/tmp}/fftw3-build-airmedy-darwin"
MIN_MACOS="14.0"

HOST_ARCH="$(uname -m)"
case "${HOST_ARCH}" in
    arm64)  HOST_OUT_ARCH="arm64" ;;
    x86_64) HOST_OUT_ARCH="amd64" ;;
    *) echo "ERROR: unsupported host arch ${HOST_ARCH}" >&2; exit 1 ;;
esac

build_arch() {
    local OUT_ARCH="$1"   # arm64 or amd64
    local CLANG_ARCH HOST_TRIPLE
    case "${OUT_ARCH}" in
        arm64) CLANG_ARCH="arm64"; HOST_TRIPLE="aarch64-apple-darwin" ;;
        amd64) CLANG_ARCH="x86_64"; HOST_TRIPLE="x86_64-apple-darwin" ;;
        *) echo "ERROR: unknown arch ${OUT_ARCH}" >&2; exit 1 ;;
    esac

    local SRC_DIR="${BUILD_DIR}/src"
    local WORK_DIR="${BUILD_DIR}/work-${OUT_ARCH}"
    # NOTE: install prefix must NOT live under WORK_DIR — fftw3's source tree
    # ships its own top-level file named "install" (autotools install-sh
    # helper alias), which collides with --prefix=WORK_DIR/install and makes
    # `mkdir` fail with "Not a directory".
    local INSTALL_DIR="${BUILD_DIR}/install-${OUT_ARCH}"

    echo "==> Building FFTW3 ${FFTW_VERSION} static lib for darwin/${OUT_ARCH}..."
    rm -rf "${WORK_DIR}"
    cp -R "${SRC_DIR}" "${WORK_DIR}"
    cd "${WORK_DIR}"

    local FLAGS="-arch ${CLANG_ARCH} -mmacosx-version-min=${MIN_MACOS} -O2 -fPIC"
    export CC="clang"
    export CFLAGS="${FLAGS}"
    export LDFLAGS="${FLAGS}"

    # --disable-shared: only the static archive is needed. --with-pic matches
    # the -fPIC in CFLAGS. --disable-fortran avoids requiring a Fortran
    # compiler for the (unused) Fortran wrapper.
    rm -rf "${INSTALL_DIR}"
    ./configure --host="${HOST_TRIPLE}" --prefix="${INSTALL_DIR}" \
        --disable-shared --enable-static --with-pic \
        --disable-fortran --disable-doc >/dev/null
    make -j"$(sysctl -n hw.ncpu)" >/dev/null
    make install >/dev/null

    local LIB="${INSTALL_DIR}/lib/libfftw3.a"
    if [[ ! -f "${LIB}" ]]; then
        echo "    ERROR: libfftw3.a not produced" >&2
        exit 1
    fi

    mkdir -p "${OUT_BASE}/${OUT_ARCH}"
    cp "${LIB}" "${OUT_BASE}/${OUT_ARCH}/libfftw3.a"
    echo "    copied libfftw3.a -> fftw3_libs/darwin/${OUT_ARCH}/"

    verify_fftw3 "${OUT_BASE}/${OUT_ARCH}/libfftw3.a"

    if [[ ! -d "${INCLUDE_OUT}" ]]; then
        echo "==> Copying fftw3.h to fftw3_libs/include/..."
        mkdir -p "${INCLUDE_OUT}"
        cp "${INSTALL_DIR}/include/fftw3.h" "${INCLUDE_OUT}/fftw3.h"
    fi
}

verify_fftw3() {
    local LIB="$1"
    echo "==> Verifying fftw_plan_dft_1d in $(basename "${LIB}")..."
    local SYMS
    SYMS="$(nm "${LIB}" 2>/dev/null || true)"
    grep -q "_fftw_plan_dft_1d" <<<"${SYMS}" || { echo "    ERROR: fftw_plan_dft_1d missing from libfftw3.a"; exit 1; }
    echo "    OK: fftw_plan_dft_1d present in libfftw3.a"
}

mkdir -p "${BUILD_DIR}/src"
if [[ ! -f "${BUILD_DIR}/fftw3.tar.gz" ]]; then
    echo "==> Downloading FFTW3 ${FFTW_VERSION}..."
    curl -fL "${FFTW_URL}" -o "${BUILD_DIR}/fftw3.tar.gz"
fi
echo "==> Extracting..."
rm -rf "${BUILD_DIR}/src"
mkdir -p "${BUILD_DIR}/src"
tar -xzf "${BUILD_DIR}/fftw3.tar.gz" -C "${BUILD_DIR}/src" --strip-components=1

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
