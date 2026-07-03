#!/usr/bin/env bash
# Build a static libkeyfinder library for Windows (amd64 + arm64).
# Works on Linux (cross-compile) and Windows/MSYS2 (native).
# Output: internal/infra/audio/keyfinder_libs/windows/{amd64,arm64}/libkeyfinder.a
#         internal/infra/audio/keyfinder_libs/include/keyfinder/  (shared headers)
#
# Used by the in-process audio-analysis pipeline for musical key/mode detection
# (keyfinder_bridge.cpp). Requires FFTW3 (see build-fftw3-windows.sh — run that
# first, or this script builds it on demand).
#
# libkeyfinder is LGPL-3.0; FFTW3 (its FFT backend) is GPLv2+. See NOTICES and
# the license note in build-fftw3-windows.sh for the accepted compliance scope.
#
# Requires: gcc-mingw-w64-x86-64, cmake [and llvm-mingw for arm64].
#
# Usage:
#   bash scripts/build-keyfinder-windows.sh          # build both amd64 and arm64
#   bash scripts/build-keyfinder-windows.sh amd64    # amd64 only
#   bash scripts/build-keyfinder-windows.sh arm64    # arm64 only

set -euo pipefail

KEYFINDER_VERSION="2.2.8"
KEYFINDER_URL="https://github.com/mixxxdj/libkeyfinder/archive/refs/tags/${KEYFINDER_VERSION}.tar.gz"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_BASE="${REPO_ROOT}/internal/infra/audio/keyfinder_libs/windows"
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/keyfinder_libs/include"
FFTW3_BASE="${REPO_ROOT}/internal/infra/audio/fftw3_libs/windows"
FFTW3_INCLUDE="${REPO_ROOT}/internal/infra/audio/fftw3_libs/include"
BUILD_DIR="/tmp/keyfinder-build-airmedy-windows"

write_toolchain_file() {
    # $1 = out path, $2 = C compiler, $3 = C++ compiler, $4 = ar, $5 = system processor
    cat > "$1" << EOF
set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_SYSTEM_PROCESSOR $5)
set(CMAKE_C_COMPILER $2)
set(CMAKE_CXX_COMPILER $3)
set(CMAKE_AR $4)
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
EOF
}

resolve_amd64_tools() {
    local CC CXX AR
    if [[ -x /usr/bin/x86_64-w64-mingw32-gcc ]]; then
        CC="/usr/bin/x86_64-w64-mingw32-gcc"
        CXX="/usr/bin/x86_64-w64-mingw32-g++"
        [[ -x /usr/bin/x86_64-w64-mingw32-ar ]] && AR="/usr/bin/x86_64-w64-mingw32-ar" || AR="ar"
    elif command -v x86_64-w64-mingw32-gcc &>/dev/null; then
        CC="x86_64-w64-mingw32-gcc"
        CXX="x86_64-w64-mingw32-g++"
        command -v x86_64-w64-mingw32-ar &>/dev/null && AR="x86_64-w64-mingw32-ar" || AR="ar"
    else
        CC="gcc"; CXX="g++"; AR="ar" # MSYS2 MINGW64
    fi
    echo "${CC} ${CXX} ${AR}"
}

resolve_arm64_tools() {
    local CC CXX AR
    local CLANGARM64_BIN=""
    if command -v cygpath &>/dev/null; then
        local MSYS_ROOT
        MSYS_ROOT="$(cygpath -u "$(cygpath -w /)" | sed 's|/$||')"
        CLANGARM64_BIN="${MSYS_ROOT}/clangarm64/bin"
    fi

    if command -v aarch64-w64-mingw32-clang &>/dev/null; then
        CC="aarch64-w64-mingw32-clang"; CXX="aarch64-w64-mingw32-clang++"
    elif [[ -n "${CLANGARM64_BIN}" && -f "${CLANGARM64_BIN}/aarch64-w64-mingw32-clang.exe" ]]; then
        CC="${CLANGARM64_BIN}/aarch64-w64-mingw32-clang.exe"
        CXX="${CLANGARM64_BIN}/aarch64-w64-mingw32-clang++.exe"
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
    echo "${CC} ${CXX} ${AR}"
}

build_arch() {
    local ARCH="$1" CC="$2" CXX="$3" AR="$4" SYS_PROC="$5"
    local OUT_DIR="${OUT_BASE}/${ARCH}"
    local WORK_DIR="${BUILD_DIR}/work-${ARCH}"
    local BUILD_SUBDIR="${WORK_DIR}/build"

    local FFTW3_LIB="${FFTW3_BASE}/${ARCH}/libfftw3.a"
    if [[ ! -f "${FFTW3_LIB}" ]]; then
        echo "==> FFTW3 static lib missing for ${ARCH}, building it first..."
        bash "${REPO_ROOT}/scripts/build-fftw3-windows.sh" "${ARCH}"
    fi
    # libkeyfinder.a only needs FFTW3's *headers* to compile, so a missing
    # FFTW3 static lib does NOT fail this build — assert it exists.
    [[ -f "${FFTW3_LIB}" ]] || { echo "    ERROR: ${FFTW3_LIB} not produced" >&2; exit 1; }

    echo "==> Building libkeyfinder ${KEYFINDER_VERSION} for Windows ${ARCH}..."
    rm -rf "${WORK_DIR}"
    cp -R "${BUILD_DIR}/src" "${WORK_DIR}"
    mkdir -p "${BUILD_SUBDIR}"
    cd "${BUILD_SUBDIR}"

    local TOOLCHAIN_FILE="${WORK_DIR}/toolchain.cmake"
    write_toolchain_file "${TOOLCHAIN_FILE}" "${CC}" "${CXX}" "${AR}" "${SYS_PROC}"

    cmake .. \
        -DCMAKE_TOOLCHAIN_FILE="${TOOLCHAIN_FILE}" \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=OFF \
        -DBUILD_TESTING=OFF \
        -DFFTW3_INCLUDE_DIR="${FFTW3_INCLUDE}" \
        -DFFTW3_LIBRARY="${FFTW3_LIB}" \
        >/dev/null
    cmake --build . --config Release -j"$(nproc 2>/dev/null || echo 4)" >/dev/null

    local LIB
    LIB="$(find "${BUILD_SUBDIR}" -name 'libkeyfinder.a' | head -1)"
    [[ -n "${LIB}" ]] || { echo "    ERROR: libkeyfinder.a not produced" >&2; exit 1; }

    mkdir -p "${OUT_DIR}"
    cp "${LIB}" "${OUT_DIR}/libkeyfinder.a"
    echo "    copied libkeyfinder.a -> keyfinder_libs/windows/${ARCH}/"

    cd "${REPO_ROOT}"
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

TARGET="${1:-all}"
case "$TARGET" in
    amd64)
        read -r AMD64_CC AMD64_CXX AMD64_AR <<< "$(resolve_amd64_tools)"
        build_arch "amd64" "${AMD64_CC}" "${AMD64_CXX}" "${AMD64_AR}" "AMD64"
        ;;
    arm64)
        ARM64_TOOLS="$(resolve_arm64_tools)"
        [[ -n "${ARM64_TOOLS}" ]] || { echo "ERROR: no aarch64 cross-compiler found (install llvm-mingw)" >&2; exit 1; }
        read -r ARM64_CC ARM64_CXX ARM64_AR <<< "${ARM64_TOOLS}"
        build_arch "arm64" "${ARM64_CC}" "${ARM64_CXX}" "${ARM64_AR}" "ARM64"
        ;;
    all)
        read -r AMD64_CC AMD64_CXX AMD64_AR <<< "$(resolve_amd64_tools)"
        build_arch "amd64" "${AMD64_CC}" "${AMD64_CXX}" "${AMD64_AR}" "AMD64"
        ARM64_TOOLS="$(resolve_arm64_tools)"
        if [[ -n "${ARM64_TOOLS}" ]]; then
            read -r ARM64_CC ARM64_CXX ARM64_AR <<< "${ARM64_TOOLS}"
            build_arch "arm64" "${ARM64_CC}" "${ARM64_CXX}" "${ARM64_AR}" "ARM64"
        else
            echo "==> Skipping arm64: no aarch64 cross-compiler found (install llvm-mingw toolchain)"
        fi
        ;;
    *) echo "Usage: $0 [amd64|arm64|all]" >&2; exit 1 ;;
esac

if [[ ! -d "${INCLUDE_OUT}" ]]; then
    echo "==> Copying libkeyfinder headers to keyfinder_libs/include/..."
    mkdir -p "${INCLUDE_OUT}/keyfinder"
    for h in audiodata chromagram chromatransform chromatransformfactory \
             fftadapter keyclassifier keyfinder lowpassfilter \
             lowpassfilterfactory spectrumanalyser temporalwindowfactory \
             toneprofiles windowfunctions workspace constants exception binode; do
        cp "${BUILD_DIR}/src/src/${h}.h" "${INCLUDE_OUT}/keyfinder/${h}.h"
    done
fi

echo ""
echo "==> Done."
du -sh "${OUT_BASE}"/* 2>/dev/null || true
