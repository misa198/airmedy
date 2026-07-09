#!/usr/bin/env bash
# Build a static FFTW3F (single precision) library for Linux (amd64 + arm64).
# Output: internal/infra/audio/fftw3_libs/linux/{amd64,arm64}/libfftw3f.a
#         internal/infra/audio/fftw3_libs/include/  (fftw3.h)
#
# Used by aubio's tempo/onset pipeline to replace its bundled Ooura FFT path.
#
# Requirements:
#   - gcc, make, curl
#   - arm64 cross-compile: apt install gcc-aarch64-linux-gnu
#
# Usage: bash scripts/build-fftw3-linux.sh [amd64|arm64|all]

set -euo pipefail

FFTW_VERSION="3.3.10"
FFTW_URL="https://www.fftw.org/fftw-${FFTW_VERSION}.tar.gz"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_BASE="${REPO_ROOT}/internal/infra/audio/fftw3_libs/linux"
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/fftw3_libs/include"
PKGCONFIG_BASE="${REPO_ROOT}/internal/infra/audio/fftw3_libs/pkgconfig/linux"
BUILD_DIR="${TMPDIR:-/tmp}/fftw3-build-airmedy-linux"

build_arch() {
    local OUT_ARCH="$1"
    local HOST_TRIPLE="$2"
    local CC="${3:-gcc}"
    local SRC_DIR="${BUILD_DIR}/src"
    local WORK_DIR="${BUILD_DIR}/work-${OUT_ARCH}"

    echo "==> Building FFTW3F ${FFTW_VERSION} for linux/${OUT_ARCH}..."
    rm -rf "${WORK_DIR}"
    cp -R "${SRC_DIR}" "${WORK_DIR}"
    cd "${WORK_DIR}"

    export CC
    export CFLAGS="-O2 -fPIC"

    local CONFIGURE_ARGS=(--prefix="${WORK_DIR}/install" --disable-shared --enable-static --with-pic --enable-single --disable-fortran --disable-doc)
    [[ -n "${HOST_TRIPLE}" ]] && CONFIGURE_ARGS+=(--host="${HOST_TRIPLE}")

    ./configure "${CONFIGURE_ARGS[@]}" >/dev/null
    make -j"$(nproc)" >/dev/null
    make install >/dev/null

    local LIB="${WORK_DIR}/install/lib/libfftw3f.a"
    [[ -f "${LIB}" ]] || { echo "    ERROR: libfftw3f.a not produced" >&2; exit 1; }

    mkdir -p "${OUT_BASE}/${OUT_ARCH}"
    cp "${LIB}" "${OUT_BASE}/${OUT_ARCH}/libfftw3f.a"
    echo "    copied libfftw3f.a -> fftw3_libs/linux/${OUT_ARCH}/"
    verify_fftw3 "${OUT_BASE}/${OUT_ARCH}/libfftw3f.a" "${CC%gcc}"
    write_pkgconfig "${OUT_ARCH}"

    if [[ ! -d "${INCLUDE_OUT}" ]]; then
        echo "==> Copying fftw3.h to fftw3_libs/include/..."
        mkdir -p "${INCLUDE_OUT}"
        cp "${WORK_DIR}/install/include/fftw3.h" "${INCLUDE_OUT}/fftw3.h"
    fi
}

verify_fftw3() {
    local LIB="$1"; local CROSS_PREFIX="${2:-}"
    echo "==> Verifying fftwf_plan_dft_1d in $(basename "${LIB}")..."
    local NM="${CROSS_PREFIX}nm"
    command -v "${NM}" &>/dev/null || NM="nm"
    local SYMS
    SYMS="$("${NM}" "${LIB}" 2>/dev/null || true)"
    grep -q "fftwf_plan_dft_1d" <<<"${SYMS}" || { echo "    ERROR: fftwf_plan_dft_1d missing from libfftw3f.a"; exit 1; }
    echo "    OK: fftwf_plan_dft_1d present in libfftw3f.a"
}

write_pkgconfig() {
    local OUT_ARCH="$1"
    local PC_DIR="${PKGCONFIG_BASE}/${OUT_ARCH}"
    mkdir -p "${PC_DIR}"
    cat > "${PC_DIR}/fftw3f.pc" <<EOF
prefix=${OUT_BASE}/${OUT_ARCH}
exec_prefix=\${prefix}
libdir=\${prefix}
includedir=${INCLUDE_OUT}

Name: FFTW3F
Description: Fast Fourier Transform library
Version: ${FFTW_VERSION}
Libs: -L\${libdir} -lfftw3f
Cflags: -I\${includedir}
EOF
}

TARGET_ARCH="${1:-all}"
case "${TARGET_ARCH}" in
    amd64|arm64|all) ;;
    *) echo "Usage: $0 [amd64|arm64|all]" >&2; exit 1 ;;
esac

mkdir -p "${BUILD_DIR}/src"
if [[ ! -f "${BUILD_DIR}/fftw3.tar.gz" ]]; then
    echo "==> Downloading FFTW3 ${FFTW_VERSION}..."
    curl -fL "${FFTW_URL}" -o "${BUILD_DIR}/fftw3.tar.gz"
fi
echo "==> Extracting..."
rm -rf "${BUILD_DIR}/src"
mkdir -p "${BUILD_DIR}/src"
tar -xzf "${BUILD_DIR}/fftw3.tar.gz" -C "${BUILD_DIR}/src" --strip-components=1

build_arm64() {
    if command -v aarch64-linux-gnu-gcc &>/dev/null; then
        build_arch "arm64" "aarch64-linux-gnu" "aarch64-linux-gnu-gcc"
        return 0
    fi
    return 1
}

case "${TARGET_ARCH}" in
    amd64)
        build_arch "amd64" "" "gcc"
        ;;
    arm64)
        build_arm64 || { echo "    ERROR: aarch64-linux-gnu-gcc not found (apt install gcc-aarch64-linux-gnu)" >&2; exit 1; }
        ;;
    all)
        build_arch "amd64" "" "gcc"
        build_arm64 || echo "==> Skipping arm64: aarch64-linux-gnu-gcc not found (apt install gcc-aarch64-linux-gnu)"
        ;;
esac

echo ""
echo "==> Done. Output:"
du -sh "${OUT_BASE}"/* 2>/dev/null || true
