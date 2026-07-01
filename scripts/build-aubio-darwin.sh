#!/usr/bin/env bash
# Build a minimal aubio static library for macOS (arm64 + x86_64).
# Output: internal/infra/audio/aubio_libs/darwin/{arm64,amd64}/libaubio.a
#         internal/infra/audio/aubio_libs/include/aubio/  (shared headers)
#
# Used only by the in-process audio-analysis pipeline for tempo (BPM) detection
# via aubio_tempo. FFmpeg decodes the file; the decoded PCM is fed to aubio.
# Built with all external backends disabled, so the archive depends only on libm
# (aubio uses its bundled ooura FFT when fftw is unavailable).
#
# Requirements: Xcode command line tools (clang, make) + python3 (for waf).
#
# Usage:
#   bash scripts/build-aubio-darwin.sh          # build host arch only (default)
#   bash scripts/build-aubio-darwin.sh all      # build both arm64 and amd64
#   bash scripts/build-aubio-darwin.sh arm64    # arm64 only
#   bash scripts/build-aubio-darwin.sh amd64    # amd64 only

set -euo pipefail

AUBIO_VERSION="0.4.9"
AUBIO_URL="https://aubio.org/pub/aubio-${AUBIO_VERSION}.tar.bz2"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUT_BASE="${REPO_ROOT}/internal/infra/audio/aubio_libs/darwin"
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/aubio_libs/include"
BUILD_DIR="${TMPDIR:-/tmp}/aubio-build-airmedy-darwin"
MIN_MACOS="14.0"

HOST_ARCH="$(uname -m)"
case "${HOST_ARCH}" in
    arm64)  HOST_OUT_ARCH="arm64" ;;
    x86_64) HOST_OUT_ARCH="amd64" ;;
    *) echo "ERROR: unsupported host arch ${HOST_ARCH}" >&2; exit 1 ;;
esac

# Disable every optional backend so libaubio.a only needs libm.
WAF_FLAGS=(
    --disable-fftw3
    --disable-fftw3f
    --disable-intelipp
    --disable-accelerate
    --disable-apple-audio
    --disable-sndfile
    --disable-samplerate
    --disable-jack
    --disable-avcodec
    --disable-blas
    --disable-docs
    --disable-tests
    --disable-examples
    --notests
)

build_arch() {
    local OUT_ARCH="$1"   # arm64 or amd64
    local CLANG_ARCH
    case "${OUT_ARCH}" in
        arm64) CLANG_ARCH="arm64" ;;
        amd64) CLANG_ARCH="x86_64" ;;
        *) echo "ERROR: unknown arch ${OUT_ARCH}" >&2; exit 1 ;;
    esac

    local SRC_DIR="${BUILD_DIR}/src"
    local WORK_DIR="${BUILD_DIR}/work-${OUT_ARCH}"

    echo "==> Building aubio ${AUBIO_VERSION} static lib for darwin/${OUT_ARCH}..."
    rm -rf "${WORK_DIR}"
    cp -R "${SRC_DIR}" "${WORK_DIR}"
    cd "${WORK_DIR}"

    local FLAGS="-arch ${CLANG_ARCH} -mmacosx-version-min=${MIN_MACOS} -O2 -fPIC"
    export CC="clang"
    export CFLAGS="${FLAGS}"
    export LINKFLAGS="${FLAGS}"

    # waf is bundled with the aubio source. Configure + build only the library.
    python3 ./waf configure "${WAF_FLAGS[@]}" --prefix="${WORK_DIR}/install"
    # waf re-parses options.py defaults at build time too (not just configure),
    # so --disable-tests/--disable-examples must be repeated here.
    python3 ./waf build "${WAF_FLAGS[@]}"

    # Locate the produced static archive (path varies across waf versions).
    local LIB
    LIB="$(find "${WORK_DIR}/build" -name 'libaubio.a' | head -1)"
    if [[ -z "${LIB}" ]]; then
        echo "    ERROR: libaubio.a not produced" >&2
        exit 1
    fi

    mkdir -p "${OUT_BASE}/${OUT_ARCH}"
    cp "${LIB}" "${OUT_BASE}/${OUT_ARCH}/libaubio.a"
    echo "    copied libaubio.a -> aubio_libs/darwin/${OUT_ARCH}/"

    verify_aubio "${OUT_BASE}/${OUT_ARCH}/libaubio.a"

    if [[ ! -d "${INCLUDE_OUT}" ]]; then
        echo "==> Copying aubio headers to aubio_libs/include/..."
        mkdir -p "${INCLUDE_OUT}/aubio"
        # The umbrella header + all public headers live under src/.
        cp "${SRC_DIR}/src/aubio.h" "${INCLUDE_OUT}/aubio/aubio.h"
        # Preserve the src/ subtree layout the umbrella header includes.
        # (BSD cp on macOS has no --parents, so copy each header individually.)
        ( cd "${SRC_DIR}/src" && find . -name '*.h' -print0 ) | while IFS= read -r -d '' h; do
            mkdir -p "${INCLUDE_OUT}/aubio/$(dirname "${h}")"
            cp "${SRC_DIR}/src/${h}" "${INCLUDE_OUT}/aubio/${h}"
        done
    fi
}

# Asserts the tempo API is present in libaubio.a.
verify_aubio() {
    local LIB="$1"
    echo "==> Verifying aubio_tempo in $(basename "${LIB}")..."
    local SYMS
    SYMS="$(nm "${LIB}" 2>/dev/null || true)"
    grep -q "_new_aubio_tempo" <<<"${SYMS}" || { echo "    ERROR: aubio_tempo missing from libaubio.a"; exit 1; }
    echo "    OK: aubio_tempo present in libaubio.a"
}

mkdir -p "${BUILD_DIR}/src"
if [[ ! -f "${BUILD_DIR}/aubio.tar.bz2" ]]; then
    echo "==> Downloading aubio ${AUBIO_VERSION}..."
    curl -L "${AUBIO_URL}" -o "${BUILD_DIR}/aubio.tar.bz2"
fi
echo "==> Extracting..."
tar -xjf "${BUILD_DIR}/aubio.tar.bz2" -C "${BUILD_DIR}/src" --strip-components=1

# Bundled waf (from 2019) is incompatible with modern python3:
#  - imports the stdlib 'imp' module, removed in Python 3.12+
#  - opens files with the 'U' mode flag, removed in Python 3.11+
# Patch both so waf runs on the host's python3.
sed -i '' \
    -e 's/^import os,re,imp,sys$/import os,re,sys,types/' \
    -e 's/imp\.new_module(WSCRIPT_FILE)/types.ModuleType(WSCRIPT_FILE)/' \
    "${BUILD_DIR}/src/waflib/Context.py"
sed -i '' "s/m='rU'/m='r'/" "${BUILD_DIR}/src/waflib/ConfigSet.py" "${BUILD_DIR}/src/waflib/Context.py"
sed -i '' "s/node.read('rU',encoding)/node.read('r',encoding)/" "${BUILD_DIR}/src/waflib/Context.py"

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
