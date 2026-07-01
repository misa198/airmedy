#!/usr/bin/env bash
# Build minimal FFmpeg static libraries for Linux (amd64 + arm64).
# Output: internal/infra/audio/ffmpeg_libs/linux/{amd64,arm64}/*.a
#         internal/infra/audio/ffmpeg_libs/include/  (shared headers)
#
# The libs are linked into the audio package via cgo for two uses:
#   - miniaudio player decode (libavcodec/format/util/swresample)
#   - in-process audio analysis (libavfilter: ebur128,aspectralstats,astats)
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
    --disable-network
    --disable-autodetect
    --enable-static
    --disable-shared
    --enable-avcodec
    --enable-avformat
    --enable-avutil
    --enable-swresample

    # --- avfilter: audio-analysis filter graph (in-process via cgo) ---
    --enable-avfilter
    --enable-filter=ebur128,aspectralstats,astats,aresample,aformat,anull,abuffer,abuffersink

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

LIBS=(libavcodec libavformat libavutil libswresample libavfilter)

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

    # Confirm the analysis filters were compiled into libavfilter.a.
    verify_avfilter "${OUT_BASE}/${OUT_ARCH}/libavfilter.a" "${CROSS_PREFIX:-}"
}

# Asserts that the ebur128/aspectralstats/astats filters are present in
# libavfilter.a (via their *_init/*_options symbols). Works for cross builds too,
# since it inspects the archive rather than running anything.
verify_avfilter() {
    local LIB="$1"; local CROSS_PREFIX="${2:-}"
    echo "==> Verifying analysis filters in $(basename "${LIB}")..."
    local NM="${CROSS_PREFIX}nm"
    command -v "${NM}" &>/dev/null || NM="nm"
    local SYMS
    SYMS="$("${NM}" "${LIB}" 2>/dev/null || true)"
    for F in ebur128 aspectralstats astats; do
        grep -q "ff_af_${F}" <<<"${SYMS}" || { echo "    ERROR: filter '${F}' missing from libavfilter.a"; exit 1; }
    done
    echo "    OK: ebur128 + aspectralstats + astats present in libavfilter.a"
}

# Download source once
mkdir -p "${BUILD_DIR}/src"
if [[ ! -f "${BUILD_DIR}/ffmpeg.tar.gz" ]]; then
    echo "==> Downloading FFmpeg ${FFMPEG_VERSION}..."
    curl -L "${FFMPEG_URL}" -o "${BUILD_DIR}/ffmpeg.tar.gz"
fi
echo "==> Extracting..."
tar -xzf "${BUILD_DIR}/ffmpeg.tar.gz" -C "${BUILD_DIR}/src" --strip-components=1

# Plain `gcc` is always the *host's native* compiler — on an x86_64 host it
# builds x86_64, on an arm64 host it builds arm64. Cross-compiling the other
# arch requires a dedicated cross-compiler toolchain.
HOST_ARCH="$(uname -m)"
case "${HOST_ARCH}" in
    x86_64)
        build_arch "x86_64" "amd64" "gcc"
        NATIVE_INSTALL_ARCH="x86_64"
        if command -v aarch64-linux-gnu-gcc &>/dev/null; then
            build_arch "aarch64" "arm64" "aarch64-linux-gnu-gcc" "aarch64-linux-gnu-"
        else
            echo "==> Skipping arm64: aarch64-linux-gnu-gcc not found (apt install gcc-aarch64-linux-gnu)"
        fi
        ;;
    aarch64|arm64)
        build_arch "aarch64" "arm64" "gcc"
        NATIVE_INSTALL_ARCH="aarch64"
        if command -v x86_64-linux-gnu-gcc &>/dev/null; then
            build_arch "x86_64" "amd64" "x86_64-linux-gnu-gcc" "x86_64-linux-gnu-"
        else
            echo "==> Skipping amd64: x86_64-linux-gnu-gcc not found (apt install gcc-x86-64-linux-gnu)"
        fi
        ;;
    *)
        echo "==> Unsupported host architecture: ${HOST_ARCH}" >&2
        exit 1
        ;;
esac

# Copy headers (shared across arches)
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/ffmpeg_libs/include"
if [[ ! -d "${INCLUDE_OUT}" ]]; then
    echo "==> Copying FFmpeg headers to ffmpeg_libs/include/..."
    cp -R "${BUILD_DIR}/install-${NATIVE_INSTALL_ARCH}/include" "${INCLUDE_OUT}"
fi

echo ""
echo "==> Done. Output:"
du -sh "${OUT_BASE}"/* 2>/dev/null || true
