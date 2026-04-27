#!/usr/bin/env bash
# Build minimal FFmpeg static libraries for Linux (amd64 + arm64).
# Output: internal/infra/audio/ffmpeg_libs/linux/{amd64,arm64}/*.a
#         internal/infra/audio/ffmpeg_libs/include/  (shared headers)
#
# Requirements (amd64 native build):
#   - gcc, make, pkg-config
#   - nasm: apt install nasm  (optional, improves performance)
#   - For arm64 cross-compile: apt install gcc-aarch64-linux-gnu
#
# Usage: bash scripts/build-ffmpeg-linux.sh

set -euo pipefail

FFMPEG_VERSION="8.1"
FFMPEG_URL="https://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.gz"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_BASE="${REPO_ROOT}/internal/infra/audio/ffmpeg_libs/linux"
BUILD_DIR="${TMPDIR:-/tmp}/ffmpeg-build-airmedy-linux"

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
    
    # --- Decoders ---
    --enable-decoder=mp3,mp3float
    --enable-decoder=aac,aac_latm
    --enable-decoder=alac
    --enable-decoder=flac
    --enable-decoder=pcm_s16le,pcm_s24le,pcm_s32le,pcm_f32le # For WAV
    --enable-decoder=pcm_s16be,pcm_s24be,pcm_s32be          # For AIFF
    --enable-decoder=pcm_alaw,pcm_mulaw                      # For AIFF/WAV extended
    --enable-decoder=vorbis
    --enable-decoder=opus
    --enable-decoder=ape
    --enable-decoder=wavpack
    --enable-decoder=dsd_lsbf,dsd_msbf,dsd_lsbf_planar,dsd_msbf_planar
    
    # --- Demuxers
    --enable-demuxer=mp3
    --enable-demuxer=aac
    --enable-demuxer=mov,m4v             # For M4A (AAC)
    --enable-demuxer=flac
    --enable-demuxer=wav
    --enable-demuxer=aiff
    --enable-demuxer=ogg
    --enable-demuxer=ape
    --enable-demuxer=wv
    --enable-demuxer=dsf,dff
    
    # --- Parsers ---
    --enable-parser=mpegaudio            # For MP3
    --enable-parser=aac,aac_latm
    --enable-parser=flac
    --enable-parser=vorbis
    --enable-parser=opus
    
    # --- Protocol ---
    --enable-protocol=file
)

LIBS=(libavcodec libavformat libavutil libswresample)

build_arch() {
    local ARCH="$1"           # x86_64 or aarch64
    local OUT_ARCH="$2"       # amd64 or arm64 (output dir name)
    local CC="${3:-gcc}"      # compiler (cross-compiler for arm64)
    local CROSS_PREFIX="${4:-}" # cross-prefix (e.g. aarch64-linux-gnu-)
    local SRC_DIR="${BUILD_DIR}/src"
    local BUILD_ARCH_DIR="${BUILD_DIR}/build-${ARCH}"
    local INSTALL_DIR="${BUILD_DIR}/install-${ARCH}"

    echo "==> Building FFmpeg ${FFMPEG_VERSION} for ${ARCH}..."

    mkdir -p "${BUILD_ARCH_DIR}" "${INSTALL_DIR}"

    local EXTRA_FLAGS=()
    if [[ "${ARCH}" == "x86_64" ]] && ! command -v nasm &>/dev/null; then
        echo "    nasm not found — building without SIMD (install nasm for best performance)"
        EXTRA_FLAGS+=(--disable-x86asm)
    fi

    if [[ -n "${CROSS_PREFIX}" ]]; then
        EXTRA_FLAGS+=(--enable-cross-compile --cross-prefix="${CROSS_PREFIX}")
    fi

    cd "${BUILD_ARCH_DIR}"
    "${SRC_DIR}/configure" \
        "${CONFIGURE_FLAGS[@]}" \
        ${EXTRA_FLAGS[@]+"${EXTRA_FLAGS[@]}"} \
        --arch="${ARCH}" \
        --target-os=linux \
        --cc="${CC}" \
        --prefix="${INSTALL_DIR}"

    make -j"$(nproc)"
    make install

    mkdir -p "${OUT_BASE}/${OUT_ARCH}"
    for LIB in "${LIBS[@]}"; do
        cp "${INSTALL_DIR}/lib/${LIB}.a" "${OUT_BASE}/${OUT_ARCH}/${LIB}.a"
        echo "    copied ${LIB}.a -> ffmpeg_libs/linux/${OUT_ARCH}/"
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

build_arch "x86_64"  "amd64" "gcc"

# arm64 cross-compile (requires gcc-aarch64-linux-gnu)
if command -v aarch64-linux-gnu-gcc &>/dev/null; then
    build_arch "aarch64" "arm64" "aarch64-linux-gnu-gcc" "aarch64-linux-gnu-"
else
    echo "==> Skipping arm64: aarch64-linux-gnu-gcc not found (apt install gcc-aarch64-linux-gnu)"
fi

# Copy headers (shared across arches)
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/ffmpeg_libs/include"
if [[ ! -d "${INCLUDE_OUT}" ]]; then
    echo "==> Copying FFmpeg headers to ffmpeg_libs/include/..."
    cp -R "${BUILD_DIR}/install-x86_64/include" "${INCLUDE_OUT}"
fi

echo ""
echo "==> Done. Output:"
du -sh "${OUT_BASE}"/* 2>/dev/null || true
