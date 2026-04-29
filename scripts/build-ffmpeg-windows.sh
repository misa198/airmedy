#!/usr/bin/env bash
# Build minimal FFmpeg static libraries for Windows (amd64) using gcc-mingw-w64.
# Runs on Linux. Requires: gcc-mingw-w64-x86-64
# Output: internal/infra/audio/ffmpeg_libs/windows/amd64/*.a
#         internal/infra/audio/ffmpeg_libs/include/  (shared headers)
#
# Usage: bash scripts/build-ffmpeg-windows.sh

set -euo pipefail

FFMPEG_VERSION="8.1"
FFMPEG_URL="https://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.gz"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUT_BASE="${REPO_ROOT}/internal/infra/audio/ffmpeg_libs/windows"
BUILD_DIR="/tmp/ffmpeg-build-airmedy-windows"
CROSS_PREFIX="x86_64-w64-mingw32-"

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
    # Cross-compile target
    --target-os=mingw32
    --arch=x86_64
    --enable-cross-compile
    --cross-prefix="${CROSS_PREFIX}"
    --enable-w32threads
    --disable-pthreads
    # Static-only for Wails embedding
    --extra-cflags="-D_WIN32_WINNT=0x0601 -DWINVER=0x0601"
    --extra-ldflags="-static -static-libgcc -static-libstdc++"
    --pkg-config-flags="--static"
)

LIBS=(libavcodec libavformat libavutil libswresample)

SRC_DIR="${BUILD_DIR}/src"
INSTALL_DIR="${BUILD_DIR}/install-amd64"

mkdir -p "${INSTALL_DIR}"

if [[ ! -f "${BUILD_DIR}/ffmpeg.tar.gz" ]]; then
    echo "==> Downloading FFmpeg ${FFMPEG_VERSION}..."
    curl -L "${FFMPEG_URL}" -o "${BUILD_DIR}/ffmpeg.tar.gz"
fi
echo "==> Extracting..."
rm -rf "${SRC_DIR}"
mkdir -p "${SRC_DIR}"
tar -xzf "${BUILD_DIR}/ffmpeg.tar.gz" -C "${SRC_DIR}" --strip-components=1

echo "==> Configuring FFmpeg for Windows amd64..."
cd "${SRC_DIR}"
./configure \
    "${CONFIGURE_FLAGS[@]}" \
    --prefix="${INSTALL_DIR}"

echo "==> Building..."
make -j"$(nproc 2>/dev/null || echo 4)"
make install

mkdir -p "${OUT_BASE}/amd64"
for LIB in "${LIBS[@]}"; do
    cp "${INSTALL_DIR}/lib/${LIB}.a" "${OUT_BASE}/amd64/${LIB}.a"
    echo "    copied ${LIB}.a -> ffmpeg_libs/windows/amd64/"
done

# Copy headers if not already present (shared with other platforms)
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/ffmpeg_libs/include"
if [[ ! -d "${INCLUDE_OUT}" ]]; then
    echo "==> Copying FFmpeg headers..."
    cp -R "${INSTALL_DIR}/include" "${INCLUDE_OUT}"
fi

echo ""
echo "==> Done."
du -sh "${OUT_BASE}/amd64"
