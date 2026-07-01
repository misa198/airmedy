#!/usr/bin/env bash
# Build minimal FFmpeg static libraries for Windows (amd64 + arm64) using gcc-mingw-w64.
# Runs on Linux. Requires: gcc-mingw-w64-x86-64 [and llvm-mingw for arm64]
# Output: internal/infra/audio/ffmpeg_libs/windows/{amd64,arm64}/*.a
#         internal/infra/audio/ffmpeg_libs/include/  (shared headers)
#
# The libs are linked into the audio package via cgo for two uses:
#   - miniaudio player decode (libavcodec/format/util/swresample)
#   - in-process audio analysis (libavfilter: ebur128,aspectralstats,astats)
#
# Usage:
#   bash scripts/build-ffmpeg-windows.sh          # build both amd64 and arm64
#   bash scripts/build-ffmpeg-windows.sh amd64    # amd64 only
#   bash scripts/build-ffmpeg-windows.sh arm64    # arm64 only

set -euo pipefail

FFMPEG_VERSION="8.1"
FFMPEG_URL="https://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.gz"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUT_BASE="${REPO_ROOT}/internal/infra/audio/ffmpeg_libs/windows"
BUILD_DIR="/tmp/ffmpeg-build-airmedy-windows"

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
    # avfilter: audio-analysis filter graph (in-process via cgo)
    --enable-avfilter
    --enable-filter=ebur128,aspectralstats,astats,aresample,aformat,anull,abuffer,abuffersink
    # Decoders
    --enable-decoder=mp3,mp3float
    --enable-decoder=aac,aac_latm
    --enable-decoder=alac
    --enable-decoder=flac
    --enable-decoder=pcm_s16le,pcm_s24le,pcm_s32le,pcm_f32le
    --enable-decoder=pcm_s16be,pcm_s24be,pcm_s32be
    --enable-decoder=pcm_alaw,pcm_mulaw
    --enable-decoder=vorbis
    --enable-decoder=opus
    --enable-decoder=ape
    --enable-decoder=wavpack
    --enable-decoder=dsd_lsbf,dsd_msbf,dsd_lsbf_planar,dsd_msbf_planar
    # Demuxers
    --enable-demuxer=mp3
    --enable-demuxer=aac
    --enable-demuxer=mov,m4v
    --enable-demuxer=flac
    --enable-demuxer=wav
    --enable-demuxer=aiff
    --enable-demuxer=ogg
    --enable-demuxer=ape
    --enable-demuxer=wv
    --enable-demuxer=dsf,dff
    # Parsers
    --enable-parser=mpegaudio
    --enable-parser=aac,aac_latm
    --enable-parser=flac
    --enable-parser=vorbis
    --enable-parser=opus
    # Protocol
    --enable-protocol=file
    # Disable optional system libs to avoid link-time deps on Windows
    --disable-bzlib
    --disable-lzma
    --disable-zlib
    --disable-iconv
    --disable-schannel
    --disable-securetransport
    # Static-only for Wails embedding
    --extra-cflags="-D_WIN32_WINNT=0x0601 -DWINVER=0x0601"
    --extra-ldflags="-static -static-libgcc -static-libstdc++"
    --pkg-config-flags="--static"
    # Common cross-compile target
    --target-os=mingw32
    --enable-cross-compile
    --enable-w32threads
    --disable-pthreads
)

LIBS=(libavcodec libavformat libavutil libswresample libavfilter)

build_arch() {
    local OUT_DIR="$1"       # amd64 or arm64
    local CROSS_PREFIX="$2"  # e.g. x86_64-w64-mingw32- or aarch64-w64-mingw32-
    local FFMPEG_ARCH="$3"   # e.g. x86_64 or aarch64

    local SRC_DIR="${BUILD_DIR}/src"
    local INSTALL_DIR="${BUILD_DIR}/install-${OUT_DIR}"

    echo "==> Building FFmpeg ${FFMPEG_VERSION} for Windows ${OUT_DIR}..."
    mkdir -p "${INSTALL_DIR}"

    # Extract a fresh source tree per arch. Building several arches in one tree
    # leaves stale objects from the previous arch in the static libs (e.g. an
    # amd64 bprint.o inside the arm64 libavutil.a), which the linker rejects as
    # the wrong machine type and reports as undefined symbols.
    rm -rf "${SRC_DIR}"
    mkdir -p "${SRC_DIR}"
    tar -xzf "${BUILD_DIR}/ffmpeg.tar.gz" -C "${SRC_DIR}" --strip-components=1

    # Fall back to LLVM tools when cross-prefixed binutils are not installed
    # (e.g. MSYS2 where gcc and binutils are separate packages)
    local EXTRA_TOOL_FLAGS=()
    if ! command -v "${CROSS_PREFIX}nm" &>/dev/null; then
        echo "    ${CROSS_PREFIX}nm not found — using llvm-nm/llvm-ar/llvm-ranlib/llvm-strip"
        EXTRA_TOOL_FLAGS+=(
            --nm=llvm-nm
            --ar=llvm-ar
            --ranlib=llvm-ranlib
            --strip=llvm-strip
        )
    fi

    cd "${SRC_DIR}"
    ./configure \
        "${CONFIGURE_FLAGS[@]}" \
        ${EXTRA_TOOL_FLAGS[@]+"${EXTRA_TOOL_FLAGS[@]}"} \
        --arch="${FFMPEG_ARCH}" \
        --cross-prefix="${CROSS_PREFIX}" \
        --prefix="${INSTALL_DIR}"

    make -j"$(nproc 2>/dev/null || echo 4)"
    make install

    mkdir -p "${OUT_BASE}/${OUT_DIR}"
    for LIB in "${LIBS[@]}"; do
        cp "${INSTALL_DIR}/lib/${LIB}.a" "${OUT_BASE}/${OUT_DIR}/${LIB}.a"
        echo "    copied ${LIB}.a -> ffmpeg_libs/windows/${OUT_DIR}/"
    done

    # Confirm the analysis filters were compiled into libavfilter.a.
    echo "==> Verifying analysis filters in libavfilter.a (${OUT_DIR})..."
    local NM="${CROSS_PREFIX}nm"
    command -v "${NM}" &>/dev/null || NM="llvm-nm"
    command -v "${NM}" &>/dev/null || NM="nm"
    local SYMS
    SYMS="$("${NM}" "${OUT_BASE}/${OUT_DIR}/libavfilter.a" 2>/dev/null || true)"
    for F in ebur128 aspectralstats astats; do
        grep -q "ff_af_${F}" <<<"${SYMS}" || { echo "    ERROR: filter '${F}' missing from libavfilter.a"; exit 1; }
    done
    echo "    OK: ebur128 + aspectralstats + astats present in libavfilter.a"

    cd "${REPO_ROOT}"
}

# Download and extract source once
mkdir -p "${BUILD_DIR}"
if [[ ! -f "${BUILD_DIR}/ffmpeg.tar.gz" ]]; then
    echo "==> Downloading FFmpeg ${FFMPEG_VERSION}..."
    curl -L "${FFMPEG_URL}" -o "${BUILD_DIR}/ffmpeg.tar.gz"
fi
TARGET="${1:-all}"

case "$TARGET" in
    amd64)
        build_arch "amd64" "x86_64-w64-mingw32-" "x86_64"
        ;;
    arm64)
        build_arch "arm64" "aarch64-w64-mingw32-" "aarch64"
        ;;
    all)
        build_arch "amd64" "x86_64-w64-mingw32-" "x86_64"
        if command -v aarch64-w64-mingw32-gcc &>/dev/null; then
            build_arch "arm64" "aarch64-w64-mingw32-" "aarch64"
        else
            echo "==> Skipping arm64: aarch64-w64-mingw32-gcc not found (install llvm-mingw toolchain)"
        fi
        ;;
    *)
        echo "Usage: $0 [amd64|arm64|all]" >&2
        exit 1
        ;;
esac

# Copy headers if not already present (shared across architectures).
# Use whichever arch was built — both installs have identical headers.
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/ffmpeg_libs/include"
if [[ ! -d "${INCLUDE_OUT}" ]]; then
    echo "==> Copying FFmpeg headers..."
    for _arch in amd64 arm64; do
        if [[ -d "${BUILD_DIR}/install-${_arch}/include" ]]; then
            cp -R "${BUILD_DIR}/install-${_arch}/include" "${INCLUDE_OUT}"
            break
        fi
    done
fi

echo ""
echo "==> Done."
du -sh "${OUT_BASE}"/* 2>/dev/null || true
