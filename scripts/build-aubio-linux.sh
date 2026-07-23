#!/usr/bin/env bash
# Build a minimal aubio static library for Linux (amd64 + arm64).
# Output: internal/infra/audio/aubio_libs/linux/{amd64,arm64}/libaubio.a
#         internal/infra/audio/aubio_libs/include/aubio/  (shared headers)
#
# Used by the in-process audio-analysis pipeline for tempo (BPM) detection via
# aubio_tempo. Built against vendored FFTW3F so aubio does not fall back to its
# bundled Ooura FFT.
#
# Requirements:
#   - gcc, make, python3 (for waf), curl
#   - arm64 cross-compile: apt install gcc-aarch64-linux-gnu
#
# Usage: bash scripts/build-aubio-linux.sh [amd64|arm64]
#   No arch argument builds both (arm64 skipped if the cross-compiler is absent).
#
# Note: aubio 0.4.9 ships waf 2.0.14, which imports the `imp` module removed in
# Python 3.12+ and still uses deprecated 'rU' file mode. Patch the bundled waf
# in place instead of downloading a replacement from the network.

set -euo pipefail

AUBIO_VERSION="0.4.9"
# GitHub's generated tag archive omits aubio's bundled Waf files. Use the
# complete, release-published PyPI source distribution instead.
AUBIO_URL="https://files.pythonhosted.org/packages/cd/80/302d89240603e5347c7f8026c8b02c59f8dfaec66c91a743d82de7c86006/aubio-${AUBIO_VERSION}.tar.gz"
AUBIO_SHA256="df1244f6c4cf5bea382c8c2d35aa43bc31f4cf631fe325ae3992c219546a4202"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_BASE="${REPO_ROOT}/internal/infra/audio/aubio_libs/linux"
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/aubio_libs/include"
FFTW3_BASE="${REPO_ROOT}/internal/infra/audio/fftw3_libs/linux"
FFTW3_INCLUDE="${REPO_ROOT}/internal/infra/audio/fftw3_libs/include"
FFTW3_PKGCONFIG_BASE="${REPO_ROOT}/internal/infra/audio/fftw3_libs/pkgconfig/linux"
BUILD_DIR="${TMPDIR:-/tmp}/aubio-build-airmedy-linux"

# Inject an imp shim for Python 3.12+ so aubio's bundled waf still runs.
IMP_SHIM="${BUILD_DIR}/pyshim"
mkdir -p "${IMP_SHIM}"
cat > "${IMP_SHIM}/imp.py" << 'PYEOF'
"""Shim: imp was removed in Python 3.12; provide what aubio's bundled waf needs."""
import importlib.util as _u
import types

def new_module(name):
    return types.ModuleType(name)

def load_source(name, path):
    spec = _u.spec_from_file_location(name, path)
    mod = _u.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod
PYEOF
export PYTHONPATH="${IMP_SHIM}${PYTHONPATH:+:${PYTHONPATH}}"

WAF_FLAGS=(
    --enable-fftw3f
    --disable-intelipp --disable-accelerate
    --disable-sndfile --disable-samplerate --disable-jack --disable-avcodec
    --disable-blas --disable-docs --disable-tests --disable-examples --notests
)

build_arch() {
    local OUT_ARCH="$1"   # amd64 or arm64
    local CC="${2:-gcc}"  # compiler (cross-compiler for arm64)
    local SRC_DIR="${BUILD_DIR}/src"
    local WORK_DIR="${BUILD_DIR}/work-${OUT_ARCH}"
    local FFTW3_LIB="${FFTW3_BASE}/${OUT_ARCH}/libfftw3f.a"

    if [[ ! -f "${FFTW3_LIB}" ]]; then
        echo "==> FFTW3 static lib missing for ${OUT_ARCH}, building it first..."
        bash "${REPO_ROOT}/scripts/build-fftw3-linux.sh" "${OUT_ARCH}"
    fi
    [[ -f "${FFTW3_LIB}" ]] || { echo "    ERROR: ${FFTW3_LIB} not produced" >&2; exit 1; }

    echo "==> Building aubio ${AUBIO_VERSION} for linux/${OUT_ARCH}..."
    rm -rf "${WORK_DIR}"
    cp -R "${SRC_DIR}" "${WORK_DIR}"
    cd "${WORK_DIR}"

    # 'rU' mode was removed in Python 3.11; plain 'r' keeps universal newline
    # handling. Patch aubio's extracted waflib in place.
    find waflib -name '*.py' -exec sed -i "s/'rU'/'r'/g" {} \;

    export CC
    export AR="${CC%gcc}ar"
    export CFLAGS="-O2 -fPIC -I${FFTW3_INCLUDE}"
    export LDFLAGS="-L${FFTW3_BASE}/${OUT_ARCH}"
    export LIBS="-lfftw3f"
    export PKG_CONFIG_PATH="${FFTW3_PKGCONFIG_BASE}/${OUT_ARCH}${PKG_CONFIG_PATH:+:${PKG_CONFIG_PATH}}"
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
    echo "==> Verifying aubio_tempo + FFTW3F linkage in $(basename "${LIB}")..."
    local NM="${CROSS_PREFIX}nm"
    command -v "${NM}" &>/dev/null || NM="nm"
    # Capture nm output first: piping into `grep -q` makes grep close the pipe on
    # first match, killing nm with SIGPIPE, which trips `set -o pipefail`.
    local SYMS
    SYMS="$("${NM}" "${LIB}" 2>/dev/null || true)"
    grep -q "new_aubio_tempo" <<<"${SYMS}" \
        || { echo "    ERROR: aubio_tempo missing from libaubio.a"; exit 1; }
    grep -q "fftwf_" <<<"${SYMS}" \
        || { echo "    ERROR: FFTW3F symbols missing from libaubio.a"; exit 1; }
    echo "    OK: aubio_tempo present and archive references FFTW3F"
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
if [[ ! -f "${BUILD_DIR}/aubio.tar.gz" ]]; then
    echo "==> Downloading aubio ${AUBIO_VERSION}..."
    curl --fail --location --retry 3 "${AUBIO_URL}" -o "${BUILD_DIR}/aubio.tar.gz"
fi
echo "${AUBIO_SHA256}  ${BUILD_DIR}/aubio.tar.gz" | sha256sum -c -
echo "==> Extracting..."
rm -rf "${BUILD_DIR}/src"
mkdir -p "${BUILD_DIR}/src"
tar -xzf "${BUILD_DIR}/aubio.tar.gz" -C "${BUILD_DIR}/src" --strip-components=1

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
