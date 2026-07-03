#!/usr/bin/env bash
# Build a static FFTW3 (double precision) library for Windows (amd64 + arm64).
# Works on Linux (cross-compile) and Windows/MSYS2 (native).
# Output: internal/infra/audio/fftw3_libs/windows/{amd64,arm64}/libfftw3.a
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
# Requires: gcc-mingw-w64-x86-64 [and llvm-mingw for arm64].
#
# Usage:
#   bash scripts/build-fftw3-windows.sh          # build both amd64 and arm64
#   bash scripts/build-fftw3-windows.sh amd64    # amd64 only
#   bash scripts/build-fftw3-windows.sh arm64    # arm64 only

set -euo pipefail

FFTW_VERSION="3.3.10"
FFTW_URL="https://www.fftw.org/fftw-${FFTW_VERSION}.tar.gz"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_BASE="${REPO_ROOT}/internal/infra/audio/fftw3_libs/windows"
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/fftw3_libs/include"
BUILD_DIR="/tmp/fftw3-build-airmedy-windows"

# Toolchain resolution mirrors scripts/build-aubio-windows.sh (same rationale:
# prefer the apt package's real GNU gcc over llvm-mingw's clang wrapper of the
# same name; probe MSYS2's clangarm64 bin dir directly for arm64 tools).
resolve_amd64_tools() {
    local CC AR
    if [[ -x /usr/bin/x86_64-w64-mingw32-gcc ]]; then
        CC="/usr/bin/x86_64-w64-mingw32-gcc"
        [[ -x /usr/bin/x86_64-w64-mingw32-ar ]] && AR="/usr/bin/x86_64-w64-mingw32-ar" || AR="ar"
    elif command -v x86_64-w64-mingw32-gcc &>/dev/null; then
        CC="x86_64-w64-mingw32-gcc"
        command -v x86_64-w64-mingw32-ar &>/dev/null && AR="x86_64-w64-mingw32-ar" || AR="ar"
    else
        CC="gcc"; AR="ar" # MSYS2 MINGW64
    fi
    echo "${CC} ${AR} x86_64-w64-mingw32"
}

resolve_arm64_tools() {
    local CC AR
    local CLANGARM64_BIN=""
    if command -v cygpath &>/dev/null; then
        local MSYS_ROOT
        MSYS_ROOT="$(cygpath -u "$(cygpath -w /)" | sed 's|/$||')"
        CLANGARM64_BIN="${MSYS_ROOT}/clangarm64/bin"
    fi

    if command -v aarch64-w64-mingw32-clang &>/dev/null; then
        CC="aarch64-w64-mingw32-clang"
    elif [[ -n "${CLANGARM64_BIN}" && -f "${CLANGARM64_BIN}/aarch64-w64-mingw32-clang.exe" ]]; then
        CC="${CLANGARM64_BIN}/aarch64-w64-mingw32-clang.exe"
    elif command -v aarch64-w64-mingw32-gcc &>/dev/null; then
        CC="aarch64-w64-mingw32-gcc"
    else
        echo ""; return
    fi

    if command -v llvm-ar &>/dev/null; then
        AR="llvm-ar"
    elif [[ -n "${CLANGARM64_BIN}" && -f "${CLANGARM64_BIN}/llvm-ar.exe" ]]; then
        AR="${CLANGARM64_BIN}/llvm-ar.exe"
    else
        AR="ar"
    fi
    echo "${CC} ${AR} aarch64-w64-mingw32"
}

build_arch() {
    local ARCH="$1" CC_COMPILER="$2" AR_TOOL="$3" HOST_TRIPLE="$4"
    local OUT_DIR="${OUT_BASE}/${ARCH}"
    local WORK_DIR="${BUILD_DIR}/work-${ARCH}"

    echo "==> Building FFTW3 ${FFTW_VERSION} for Windows ${ARCH}..."
    rm -rf "${WORK_DIR}"
    cp -R "${BUILD_DIR}/src" "${WORK_DIR}"
    cd "${WORK_DIR}"

    export CC="${CC_COMPILER}"
    export AR="${AR_TOOL}"
    export CFLAGS="-O2"

    ./configure --host="${HOST_TRIPLE}" --prefix="${WORK_DIR}/install" \
        --disable-shared --enable-static --disable-fortran --disable-doc >/dev/null
    make -j"$(nproc 2>/dev/null || echo 4)" >/dev/null
    make install >/dev/null

    local LIB="${WORK_DIR}/install/lib/libfftw3.a"
    [[ -f "${LIB}" ]] || { echo "    ERROR: libfftw3.a not produced" >&2; exit 1; }

    mkdir -p "${OUT_DIR}"
    cp "${LIB}" "${OUT_DIR}/libfftw3.a"
    echo "    copied libfftw3.a -> fftw3_libs/windows/${ARCH}/"

    cd "${REPO_ROOT}"
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

TARGET="${1:-all}"
case "$TARGET" in
    amd64)
        read -r AMD64_CC AMD64_AR AMD64_TRIPLE <<< "$(resolve_amd64_tools)"
        build_arch "amd64" "${AMD64_CC}" "${AMD64_AR}" "${AMD64_TRIPLE}"
        ;;
    arm64)
        ARM64_TOOLS="$(resolve_arm64_tools)"
        [[ -n "${ARM64_TOOLS}" ]] || { echo "ERROR: no aarch64 cross-compiler found (install llvm-mingw)" >&2; exit 1; }
        read -r ARM64_CC ARM64_AR ARM64_TRIPLE <<< "${ARM64_TOOLS}"
        build_arch "arm64" "${ARM64_CC}" "${ARM64_AR}" "${ARM64_TRIPLE}"
        ;;
    all)
        read -r AMD64_CC AMD64_AR AMD64_TRIPLE <<< "$(resolve_amd64_tools)"
        build_arch "amd64" "${AMD64_CC}" "${AMD64_AR}" "${AMD64_TRIPLE}"
        ARM64_TOOLS="$(resolve_arm64_tools)"
        if [[ -n "${ARM64_TOOLS}" ]]; then
            read -r ARM64_CC ARM64_AR ARM64_TRIPLE <<< "${ARM64_TOOLS}"
            build_arch "arm64" "${ARM64_CC}" "${ARM64_AR}" "${ARM64_TRIPLE}"
        else
            echo "==> Skipping arm64: no aarch64 cross-compiler found (install llvm-mingw toolchain)"
        fi
        ;;
    *) echo "Usage: $0 [amd64|arm64|all]" >&2; exit 1 ;;
esac

if [[ ! -d "${INCLUDE_OUT}" ]]; then
    echo "==> Copying fftw3.h to fftw3_libs/include/..."
    mkdir -p "${INCLUDE_OUT}"
    cp "${BUILD_DIR}/work-amd64/install/include/fftw3.h" "${INCLUDE_OUT}/fftw3.h" 2>/dev/null || \
    cp "${BUILD_DIR}/work-arm64/install/include/fftw3.h" "${INCLUDE_OUT}/fftw3.h"
fi

echo ""
echo "==> Done."
du -sh "${OUT_BASE}"/* 2>/dev/null || true
