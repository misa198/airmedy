#!/usr/bin/env bash
# Build a minimal aubio static library for Windows (amd64 + arm64).
# Works on Linux (cross-compile) and Windows/MSYS2 (native).
# Requires: gcc-mingw-w64-x86-64 [and llvm-mingw for arm64] + python3.
# Output: internal/infra/audio/aubio_libs/windows/{amd64,arm64}/libaubio.a
#         internal/infra/audio/aubio_libs/include/aubio/  (shared headers)
#
# Usage:
#   bash scripts/build-aubio-windows.sh          # build both amd64 and arm64
#   bash scripts/build-aubio-windows.sh amd64    # amd64 only
#   bash scripts/build-aubio-windows.sh arm64    # arm64 only

set -euo pipefail

AUBIO_VERSION="0.4.9"
AUBIO_URL="https://aubio.org/pub/aubio-${AUBIO_VERSION}.tar.bz2"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_BASE="${REPO_ROOT}/internal/infra/audio/aubio_libs/windows"
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/aubio_libs/include"
BUILD_DIR="/tmp/aubio-build-airmedy-windows"

WAF_FLAGS=(
    --disable-fftw3 --disable-fftw3f --disable-intelipp --disable-accelerate
    --disable-sndfile --disable-samplerate --disable-jack --disable-avcodec
    --disable-blas --disable-docs --disable-tests --disable-examples --notests
    --disable-wavread --disable-wavwrite
)

# aubio 0.4.9 ships waf that uses imp (removed in Python 3.12) and 'rU' mode
# (removed in Python 3.11). Inject an imp shim via PYTHONPATH and patch 'rU'
# directly in the extracted waflib sources.
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

# Resolve amd64 toolchain: returns "CC AR NM WAF_CC_NAME"
resolve_amd64_tools() {
    local CC AR NM
    # Prefer the Debian/Ubuntu apt package's real GNU gcc at its fixed path.
    # llvm-mingw (installed for arm64 support) also ships a binary named
    # x86_64-w64-mingw32-gcc that is actually a Clang wrapper; if its bin dir
    # is earlier in PATH, a plain `command -v` picks that instead and aubio's
    # waf build rejects it ("Could not find gcc/g++ (only Clang)").
    if [[ -x /usr/bin/x86_64-w64-mingw32-gcc ]]; then
        CC="/usr/bin/x86_64-w64-mingw32-gcc"
        [[ -x /usr/bin/x86_64-w64-mingw32-ar ]] && AR="/usr/bin/x86_64-w64-mingw32-ar" || AR="ar"
        [[ -x /usr/bin/x86_64-w64-mingw32-nm ]] && NM="/usr/bin/x86_64-w64-mingw32-nm" || NM="nm"
    elif command -v x86_64-w64-mingw32-gcc &>/dev/null; then
        CC="x86_64-w64-mingw32-gcc"
        command -v x86_64-w64-mingw32-ar &>/dev/null && AR="x86_64-w64-mingw32-ar" || AR="ar"
        command -v x86_64-w64-mingw32-nm &>/dev/null && NM="x86_64-w64-mingw32-nm" || NM="nm"
    else
        # MSYS2 MINGW64: gcc is already the x86_64-w64-mingw32 compiler
        CC="gcc"; AR="ar"; NM="nm"
    fi
    echo "${CC} ${AR} ${NM} gcc"
}

# Resolve arm64 toolchain: returns "CC AR NM WAF_CC_NAME", or empty if unavailable.
# On MSYS2/MINGW64, the clangarm64 tools are NOT in PATH. Detect the MSYS2 root
# dynamically (cygpath -w / -> C:\msys64\ -> /c/msys64) and probe that bin dir.
resolve_arm64_tools() {
    local CC AR NM
    local CLANGARM64_BIN=""

    # Detect MSYS2 clangarm64 bin dir when running on MSYS2
    if command -v cygpath &>/dev/null; then
        local MSYS_ROOT
        MSYS_ROOT="$(cygpath -u "$(cygpath -w /)" | sed 's|/$||')"
        CLANGARM64_BIN="${MSYS_ROOT}/clangarm64/bin"
    fi

    # Locate clang
    if command -v aarch64-w64-mingw32-clang &>/dev/null; then
        CC="aarch64-w64-mingw32-clang"
    elif [[ -n "${CLANGARM64_BIN}" && -f "${CLANGARM64_BIN}/aarch64-w64-mingw32-clang.exe" ]]; then
        CC="${CLANGARM64_BIN}/aarch64-w64-mingw32-clang.exe"
    elif command -v aarch64-w64-mingw32-gcc &>/dev/null; then
        CC="aarch64-w64-mingw32-gcc"
    else
        echo ""; return
    fi

    # Locate ar
    if command -v llvm-ar &>/dev/null; then
        AR="llvm-ar"
    elif [[ -n "${CLANGARM64_BIN}" && -f "${CLANGARM64_BIN}/llvm-ar.exe" ]]; then
        AR="${CLANGARM64_BIN}/llvm-ar.exe"
    else
        AR="ar"
    fi

    # Locate nm
    if command -v llvm-nm &>/dev/null; then
        NM="llvm-nm"
    elif [[ -n "${CLANGARM64_BIN}" && -f "${CLANGARM64_BIN}/llvm-nm.exe" ]]; then
        NM="${CLANGARM64_BIN}/llvm-nm.exe"
    else
        NM="nm"
    fi

    local WAF_NAME="clang"
    [[ "${CC}" == *gcc* ]] && WAF_NAME="gcc"
    echo "${CC} ${AR} ${NM} ${WAF_NAME}"
}

build_arch() {
    local ARCH="$1"
    local CC_COMPILER="$2"
    local AR_TOOL="$3"
    local NM_TOOL="$4"
    local WAF_CC_NAME="$5"   # canonical name waf searches for: 'gcc' or 'clang'
    local OUT_DIR="${OUT_BASE}/${ARCH}"
    local WORK_DIR="${BUILD_DIR}/work-${ARCH}"

    echo "==> Building aubio ${AUBIO_VERSION} for Windows ${ARCH}..."
    rm -rf "${WORK_DIR}"
    cp -R "${BUILD_DIR}/src" "${WORK_DIR}"
    cd "${WORK_DIR}"

    # 'rU' mode removed in Python 3.11; 'r' has universal newlines by default.
    find waflib -name '*.py' -exec sed -i "s/'rU'/'r'/g" {} \;

    # waf's find_program searches PATH for a binary by canonical name ('gcc'/'clang').
    # Cross-compilers have a target prefix so they're never found that way.
    # Fix: set CC to the compiler's absolute path so waf's os.path.isabs() branch
    # fires and skips PATH searching. On MSYS2, use cygpath -m (mixed mode, forward
    # slashes) — backslash paths lose their backslashes through env export, and
    # Python on Windows handles C:/... paths via os.path.isfile just fine.
    local CC_POSIX CC_EXPORT AR_POSIX AR_EXPORT
    CC_POSIX="$(command -v "${CC_COMPILER}")"
    AR_POSIX="$(command -v "${AR_TOOL}")"
    if command -v cygpath &>/dev/null; then
        CC_EXPORT="$(cygpath -m "${CC_POSIX}")"
        [[ "${CC_EXPORT}" == *.exe ]] || CC_EXPORT="${CC_EXPORT}.exe"
        AR_EXPORT="$(cygpath -m "${AR_POSIX}")"
        [[ "${AR_EXPORT}" == *.exe ]] || AR_EXPORT="${AR_EXPORT}.exe"
    else
        CC_EXPORT="${CC_POSIX}"
        AR_EXPORT="${AR_POSIX}"
    fi

    export CC="${CC_EXPORT}"
    export AR="${AR_EXPORT}"
    export CFLAGS="-O2"
    python3 ./waf configure "${WAF_FLAGS[@]}" "--check-c-compiler=${WAF_CC_NAME}" --prefix="${WORK_DIR}/install"
    python3 ./waf build "${WAF_FLAGS[@]}"

    local LIB
    LIB="$(find "${WORK_DIR}/build" -name 'libaubio.a' | head -1)"
    [[ -n "${LIB}" ]] || { echo "    ERROR: libaubio.a not produced" >&2; exit 1; }

    mkdir -p "${OUT_DIR}"
    cp "${LIB}" "${OUT_DIR}/libaubio.a"
    echo "    copied libaubio.a -> aubio_libs/windows/${ARCH}/"

    "${NM_TOOL}" "${OUT_DIR}/libaubio.a" 2>/dev/null | grep -q "new_aubio_tempo" \
        || { echo "    ERROR: aubio_tempo missing from libaubio.a"; exit 1; }
    echo "    OK: aubio_tempo present in libaubio.a"

    cd "${REPO_ROOT}"
}

mkdir -p "${BUILD_DIR}/src"
if [[ ! -f "${BUILD_DIR}/aubio.tar.bz2" ]]; then
    echo "==> Downloading aubio ${AUBIO_VERSION}..."
    curl -L "${AUBIO_URL}" -o "${BUILD_DIR}/aubio.tar.bz2"
fi
echo "==> Extracting..."
tar -xjf "${BUILD_DIR}/aubio.tar.bz2" -C "${BUILD_DIR}/src" --strip-components=1

TARGET="${1:-all}"
case "$TARGET" in
    amd64)
        read -r AMD64_CC AMD64_AR AMD64_NM AMD64_WAF <<< "$(resolve_amd64_tools)"
        build_arch "amd64" "${AMD64_CC}" "${AMD64_AR}" "${AMD64_NM}" "${AMD64_WAF}"
        ;;
    arm64)
        ARM64_TOOLS="$(resolve_arm64_tools)"
        [[ -n "${ARM64_TOOLS}" ]] || { echo "ERROR: no aarch64 cross-compiler found (install llvm-mingw)" >&2; exit 1; }
        read -r ARM64_CC ARM64_AR ARM64_NM ARM64_WAF <<< "${ARM64_TOOLS}"
        build_arch "arm64" "${ARM64_CC}" "${ARM64_AR}" "${ARM64_NM}" "${ARM64_WAF}"
        ;;
    all)
        read -r AMD64_CC AMD64_AR AMD64_NM AMD64_WAF <<< "$(resolve_amd64_tools)"
        build_arch "amd64" "${AMD64_CC}" "${AMD64_AR}" "${AMD64_NM}" "${AMD64_WAF}"
        ARM64_TOOLS="$(resolve_arm64_tools)"
        if [[ -n "${ARM64_TOOLS}" ]]; then
            read -r ARM64_CC ARM64_AR ARM64_NM ARM64_WAF <<< "${ARM64_TOOLS}"
            build_arch "arm64" "${ARM64_CC}" "${ARM64_AR}" "${ARM64_NM}" "${ARM64_WAF}"
        else
            echo "==> Skipping arm64: no aarch64 cross-compiler found (install llvm-mingw toolchain)"
        fi
        ;;
    *) echo "Usage: $0 [amd64|arm64|all]" >&2; exit 1 ;;
esac

if [[ ! -d "${INCLUDE_OUT}" ]]; then
    echo "==> Copying aubio headers to aubio_libs/include/..."
    mkdir -p "${INCLUDE_OUT}/aubio"
    ( cd "${BUILD_DIR}/src/src" && find . -name '*.h' -print0 | while IFS= read -r -d '' h; do
        mkdir -p "${INCLUDE_OUT}/aubio/$(dirname "$h")"; cp "$h" "${INCLUDE_OUT}/aubio/$h"; done )
fi

echo ""
echo "==> Done."
du -sh "${OUT_BASE}"/* 2>/dev/null || true
