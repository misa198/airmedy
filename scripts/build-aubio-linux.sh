#!/usr/bin/env bash
# Build a minimal aubio static library for Linux (amd64 + arm64).
# Output: internal/infra/audio/aubio_libs/linux/{amd64,arm64}/libaubio.a
#         internal/infra/audio/aubio_libs/include/aubio/  (shared headers)
#
# Used by the in-process audio-analysis pipeline for tempo (BPM) detection via
# aubio_tempo. Built with every optional backend disabled, so libaubio.a depends
# only on libm (aubio uses its bundled ooura FFT).
#
# Requirements:
#   - gcc, make, python3 (for waf), curl
#   - arm64 cross-compile: apt install gcc-aarch64-linux-gnu
#
# Usage: bash scripts/build-aubio-linux.sh [amd64|arm64]
#   No arch argument builds both (arm64 skipped if the cross-compiler is absent).
#
# Note: aubio 0.4.9 ships waf 2.0.14, which imports the `imp` module removed in
# Python 3.12+. We download waf 2.0.27 (same 2.0 API, modern-Python compatible)
# and use it instead.

set -euo pipefail

AUBIO_VERSION="0.4.9"
AUBIO_URL="https://aubio.org/pub/aubio-${AUBIO_VERSION}.tar.bz2"
WAF_VERSION="2.0.27"
WAF_URL="https://waf.io/waf-${WAF_VERSION}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_BASE="${REPO_ROOT}/internal/infra/audio/aubio_libs/linux"
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/aubio_libs/include"
BUILD_DIR="${TMPDIR:-/tmp}/aubio-build-airmedy-linux"
WAF_BIN="${BUILD_DIR}/waf-${WAF_VERSION}"

WAF_FLAGS=(
    --disable-fftw3 --disable-fftw3f --disable-intelipp --disable-accelerate
    --disable-sndfile --disable-samplerate --disable-jack --disable-avcodec
    --disable-blas --disable-docs --disable-tests --disable-examples --notests
)

build_arch() {
    local OUT_ARCH="$1"   # amd64 or arm64
    local CC="${2:-gcc}"  # compiler (cross-compiler for arm64)
    local SRC_DIR="${BUILD_DIR}/src"
    local WORK_DIR="${BUILD_DIR}/work-${OUT_ARCH}"

    echo "==> Building aubio ${AUBIO_VERSION} for linux/${OUT_ARCH}..."
    rm -rf "${WORK_DIR}"
    cp -R "${SRC_DIR}" "${WORK_DIR}"
    cd "${WORK_DIR}"

    # Replace aubio's bundled waf (2.0.14, incompatible with Python 3.12+) with
    # the downloaded 2.0.27; drop the unpacked waflib so waf re-extracts its own.
    cp "${WAF_BIN}" "${WORK_DIR}/waf"
    chmod +x "${WORK_DIR}/waf"
    rm -rf "${WORK_DIR}/waflib"

    export CC
    export AR="${CC%gcc}ar"
    export CFLAGS="-O2 -fPIC"
    command -v "${AR}" &>/dev/null || unset AR

    # configure and build in one invocation: aubio's build() reads
    # --disable-tests/--disable-examples from the current command line, so they
    # must be present on the build step too (otherwise tests/examples are built,
    # which need a `python` binary and are useless for a static libaubio.a).
    python3 ./waf configure build "${WAF_FLAGS[@]}" --prefix="${WORK_DIR}/install"

    local LIB
    LIB="$(find "${WORK_DIR}/build" -name 'libaubio.a' | head -1)"
    [[ -n "${LIB}" ]] || { echo "    ERROR: libaubio.a not produced" >&2; exit 1; }

    mkdir -p "${OUT_BASE}/${OUT_ARCH}"
    cp "${LIB}" "${OUT_BASE}/${OUT_ARCH}/libaubio.a"
    echo "    copied libaubio.a -> aubio_libs/linux/${OUT_ARCH}/"
    verify_aubio "${OUT_BASE}/${OUT_ARCH}/libaubio.a" "${CC%gcc}"

    if [[ ! -d "${INCLUDE_OUT}" ]]; then
        echo "==> Copying aubio headers to aubio_libs/include/..."
        mkdir -p "${INCLUDE_OUT}/aubio"
        ( cd "${SRC_DIR}/src" && find . -name '*.h' -print0 | while IFS= read -r -d '' h; do
            mkdir -p "${INCLUDE_OUT}/aubio/$(dirname "$h")"; cp "$h" "${INCLUDE_OUT}/aubio/$h"; done )
    fi
}

verify_aubio() {
    local LIB="$1"; local CROSS_PREFIX="${2:-}"
    echo "==> Verifying aubio_tempo in $(basename "${LIB}")..."
    local NM="${CROSS_PREFIX}nm"
    command -v "${NM}" &>/dev/null || NM="nm"
    # Capture nm output first: piping into `grep -q` makes grep close the pipe on
    # first match, killing nm with SIGPIPE, which trips `set -o pipefail`.
    local SYMS
    SYMS="$("${NM}" "${LIB}" 2>/dev/null || true)"
    grep -q "new_aubio_tempo" <<<"${SYMS}" \
        || { echo "    ERROR: aubio_tempo missing from libaubio.a"; exit 1; }
    echo "    OK: aubio_tempo present in libaubio.a"
}

build_arm64() {
    if command -v aarch64-linux-gnu-gcc &>/dev/null; then
        build_arch "arm64" "aarch64-linux-gnu-gcc"
        return 0
    fi
    return 1
}

TARGET_ARCH="${1:-all}"
case "${TARGET_ARCH}" in
    amd64|arm64|all) ;;
    *) echo "Usage: $0 [amd64|arm64]" >&2; exit 1 ;;
esac

mkdir -p "${BUILD_DIR}/src"
if [[ ! -f "${BUILD_DIR}/aubio.tar.bz2" ]]; then
    echo "==> Downloading aubio ${AUBIO_VERSION}..."
    curl -L "${AUBIO_URL}" -o "${BUILD_DIR}/aubio.tar.bz2"
fi
if [[ ! -f "${WAF_BIN}" ]]; then
    echo "==> Downloading waf ${WAF_VERSION}..."
    curl -L "${WAF_URL}" -o "${WAF_BIN}"
fi
echo "==> Extracting..."
tar -xjf "${BUILD_DIR}/aubio.tar.bz2" -C "${BUILD_DIR}/src" --strip-components=1

case "${TARGET_ARCH}" in
    amd64)
        build_arch "amd64" "gcc"
        ;;
    arm64)
        build_arm64 || { echo "    ERROR: aarch64-linux-gnu-gcc not found (apt install gcc-aarch64-linux-gnu)" >&2; exit 1; }
        ;;
    all)
        build_arch "amd64" "gcc"
        build_arm64 || echo "==> Skipping arm64: aarch64-linux-gnu-gcc not found (apt install gcc-aarch64-linux-gnu)"
        ;;
esac

echo ""
echo "==> Done. Output:"
du -sh "${OUT_BASE}"/* 2>/dev/null || true
