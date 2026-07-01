#!/usr/bin/env bash
# Build minimal FFmpeg static libraries for macOS (arm64 + x86_64).
# Output: internal/infra/audio/ffmpeg_libs/darwin/{arm64,amd64}/*.a
#         internal/infra/audio/ffmpeg_libs/include/  (shared headers)
#
# macOS plays audio via SFBAudioEngine and does NOT use these libs for playback.
# They exist solely for the in-process audio-analysis pipeline (libavfilter:
# ebur128/aspectralstats/astats), linked into the audio package via cgo
# alongside the SFBAudioEngine framework.
#
# Requirements: Xcode command line tools (clang, make). nasm/yasm optional (SIMD).
#
# Usage:
#   bash scripts/build-ffmpeg-darwin.sh          # build host arch only (default)
#   bash scripts/build-ffmpeg-darwin.sh all      # build both arm64 and amd64
#   bash scripts/build-ffmpeg-darwin.sh arm64    # arm64 only
#   bash scripts/build-ffmpeg-darwin.sh amd64    # amd64 only

set -euo pipefail

FFMPEG_VERSION="8.1"
FFMPEG_URL="https://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.gz"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUT_BASE="${REPO_ROOT}/internal/infra/audio/ffmpeg_libs/darwin"
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/ffmpeg_libs/include"
BUILD_DIR="${TMPDIR:-/tmp}/ffmpeg-build-airmedy-darwin"
MIN_MACOS="14.0"

HOST_ARCH="$(uname -m)"   # arm64 or x86_64
case "${HOST_ARCH}" in
    arm64)  HOST_OUT_ARCH="arm64" ;;
    x86_64) HOST_OUT_ARCH="amd64" ;;
    *) echo "ERROR: unsupported host arch ${HOST_ARCH}" >&2; exit 1 ;;
esac

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

    # --- Decoders (same format set as the Linux/Windows player build) ---
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

    # --- Demuxers ---
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

    # --- Parsers ---
    --enable-parser=mpegaudio
    --enable-parser=aac,aac_latm
    --enable-parser=flac
    --enable-parser=vorbis
    --enable-parser=opus

    # --- Protocol ---
    --enable-protocol=file
)

LIBS=(libavcodec libavformat libavutil libswresample libavfilter)

build_arch() {
    local OUT_ARCH="$1"   # arm64 or amd64 (output dir name)
    local FF_ARCH         # ffmpeg --arch value
    case "${OUT_ARCH}" in
        arm64) FF_ARCH="arm64" ;;
        amd64) FF_ARCH="x86_64" ;;
        *) echo "ERROR: unknown arch ${OUT_ARCH}" >&2; exit 1 ;;
    esac

    local SRC_DIR="${BUILD_DIR}/src"
    local BUILD_ARCH_DIR="${BUILD_DIR}/build-${OUT_ARCH}"
    local INSTALL_DIR="${BUILD_DIR}/install-${OUT_ARCH}"

    echo "==> Building FFmpeg ${FFMPEG_VERSION} static libs for darwin/${OUT_ARCH}..."
    mkdir -p "${BUILD_ARCH_DIR}" "${INSTALL_DIR}"

    local EXTRA_FLAGS=()
    # Cross-compile when target arch != host arch.
    if [[ "${OUT_ARCH}" != "${HOST_OUT_ARCH}" ]]; then
        EXTRA_FLAGS+=(--enable-cross-compile)
    fi

    cd "${BUILD_ARCH_DIR}"
    "${SRC_DIR}/configure" \
        "${CONFIGURE_FLAGS[@]}" \
        ${EXTRA_FLAGS[@]+"${EXTRA_FLAGS[@]}"} \
        --arch="${FF_ARCH}" \
        --target-os=darwin \
        --cc="clang -arch ${FF_ARCH}" \
        --extra-cflags="-mmacosx-version-min=${MIN_MACOS}" \
        --extra-ldflags="-mmacosx-version-min=${MIN_MACOS}" \
        --prefix="${INSTALL_DIR}"

    make -j"$(sysctl -n hw.ncpu)"
    make install

    mkdir -p "${OUT_BASE}/${OUT_ARCH}"
    for LIB in "${LIBS[@]}"; do
        cp "${INSTALL_DIR}/lib/${LIB}.a" "${OUT_BASE}/${OUT_ARCH}/${LIB}.a"
        echo "    copied ${LIB}.a -> ffmpeg_libs/darwin/${OUT_ARCH}/"
    done

    verify_avfilter "${OUT_BASE}/${OUT_ARCH}/libavfilter.a"

    # Copy headers once (identical across arches).
    if [[ ! -d "${INCLUDE_OUT}" ]]; then
        echo "==> Copying FFmpeg headers to ffmpeg_libs/include/..."
        cp -R "${INSTALL_DIR}/include" "${INCLUDE_OUT}"
    fi
}

# Asserts the ebur128/aspectralstats/astats filters are present in libavfilter.a.
# Inspects the archive (nm), so it works for cross-built slices too.
verify_avfilter() {
    local LIB="$1"
    echo "==> Verifying analysis filters in $(basename "${LIB}")..."
    local SYMS
    SYMS="$(nm "${LIB}" 2>/dev/null || true)"
    for F in ebur128 aspectralstats astats; do
        grep -q "ff_af_${F}" <<<"${SYMS}" || { echo "    ERROR: filter '${F}' missing from libavfilter.a"; exit 1; }
    done
    echo "    OK: ebur128 + aspectralstats + astats present in libavfilter.a"
}

# Download + extract source once.
mkdir -p "${BUILD_DIR}/src"
if [[ ! -f "${BUILD_DIR}/ffmpeg.tar.gz" ]]; then
    echo "==> Downloading FFmpeg ${FFMPEG_VERSION}..."
    curl -L "${FFMPEG_URL}" -o "${BUILD_DIR}/ffmpeg.tar.gz"
fi
echo "==> Extracting..."
tar -xzf "${BUILD_DIR}/ffmpeg.tar.gz" -C "${BUILD_DIR}/src" --strip-components=1

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
