#!/usr/bin/env bash
# Build minimal FFmpeg static libraries for macOS (arm64 + amd64).
# Output: internal/infra/audio/ffmpeg_libs/darwin/{arm64,amd64}/*.a
#
# Requirements:
#   - Xcode Command Line Tools (clang, lipo)
#   - nasm: brew install nasm
#
# Usage: bash scripts/build-ffmpeg-darwin.sh

set -euo pipefail

FFMPEG_VERSION="8.1"
FFMPEG_URL="https://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.gz"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_BASE="${REPO_ROOT}/internal/infra/audio/ffmpeg_libs/darwin"
BUILD_DIR="${TMPDIR:-/tmp}/ffmpeg-build-airmedy"

CONFIGURE_FLAGS=(
    --disable-everything
    --disable-doc
    --disable-programs
    --disable-debug
    --enable-static
    --disable-shared
    --enable-avcodec
    --enable-avformat
    --enable-avutil
    --enable-swresample
    # Decoders
    --enable-decoder=vorbis
    --enable-decoder=opus
    --enable-decoder=ape
    --enable-decoder=wavpack
    --enable-decoder=dsd_lsbf_planar
    --enable-decoder=dsd_msbf_planar
    --enable-decoder=dsd_lsbf
    --enable-decoder=dsd_msbf
    # Demuxers
    --enable-demuxer=ogg
    --enable-demuxer=ape
    --enable-demuxer=wv
    --enable-demuxer=dsf
    --enable-demuxer=dff
    # Parsers
    --enable-parser=vorbis
    --enable-parser=opus
    # Protocol
    --enable-protocol=file
)

LIBS=(libavcodec libavformat libavutil libswresample)

build_arch() {
    local ARCH="$1"         # arm64 or x86_64
    local OUT_ARCH="$2"     # arm64 or amd64 (output dir name)
    local SRC_DIR="${BUILD_DIR}/src"
    local BUILD_ARCH_DIR="${BUILD_DIR}/build-${ARCH}"
    local INSTALL_DIR="${BUILD_DIR}/install-${ARCH}"

    echo "==> Building FFmpeg ${FFMPEG_VERSION} for ${ARCH}..."

    mkdir -p "${BUILD_ARCH_DIR}" "${INSTALL_DIR}"

    # x86_64 requires nasm for SIMD; disable ASM if nasm is missing.
    local EXTRA_FLAGS=()
    if [[ "${ARCH}" == "x86_64" ]] && ! command -v nasm &>/dev/null; then
        echo "    nasm not found — building x86_64 without SIMD (install nasm for best performance)"
        EXTRA_FLAGS+=(--disable-x86asm)
    fi

    cd "${BUILD_ARCH_DIR}"
    "${SRC_DIR}/configure" \
        "${CONFIGURE_FLAGS[@]}" \
        ${EXTRA_FLAGS[@]+"${EXTRA_FLAGS[@]}"} \
        --arch="${ARCH}" \
        --target-os=darwin \
        --cc="clang -arch ${ARCH}" \
        --extra-cflags="-arch ${ARCH} -mmacosx-version-min=14.0" \
        --extra-ldflags="-arch ${ARCH} -mmacosx-version-min=14.0" \
        --prefix="${INSTALL_DIR}"

    make -j"$(sysctl -n hw.logicalcpu)"
    make install

    mkdir -p "${OUT_BASE}/${OUT_ARCH}"
    for LIB in "${LIBS[@]}"; do
        cp "${INSTALL_DIR}/lib/${LIB}.a" "${OUT_BASE}/${OUT_ARCH}/${LIB}.a"
        echo "    copied ${LIB}.a -> ffmpeg_libs/darwin/${OUT_ARCH}/"
    done
}

# Download source once
mkdir -p "${BUILD_DIR}/src"
if [[ ! -f "${BUILD_DIR}/ffmpeg.tar.gz" ]]; then
    echo "==> Downloading FFmpeg ${FFMPEG_VERSION}..."
    curl -L "${FFMPEG_URL}" -o "${BUILD_DIR}/ffmpeg.tar.gz"
fi
echo "==> Extracting..."
tar -xzf "${BUILD_DIR}/ffmpeg.tar.gz" -C "${BUILD_DIR}/src" --strip-components=1

build_arch "arm64"   "arm64"
build_arch "x86_64"  "amd64"

# Copy headers from arm64 install (identical for both arches)
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/ffmpeg_libs/include"
echo "==> Copying FFmpeg headers to ffmpeg_libs/include/..."
rm -rf "${INCLUDE_OUT}"
cp -R "${BUILD_DIR}/install-arm64/include" "${INCLUDE_OUT}"

echo ""
echo "==> Done. Output:"
echo "    ${OUT_BASE}/arm64/   (static libs)"
echo "    ${OUT_BASE}/amd64/   (static libs)"
echo "    ${INCLUDE_OUT}/      (headers — shared)"
echo ""
echo "Sizes:"
du -sh "${OUT_BASE}/arm64" "${OUT_BASE}/amd64" "${INCLUDE_OUT}"
