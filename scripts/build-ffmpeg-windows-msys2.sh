#!/usr/bin/env bash
# Build minimal FFmpeg static libraries for Windows AMD64 natively in MSYS2 MINGW64.
# Runs on Windows inside an MSYS2 MINGW64 shell (not cross-compiled from Linux).
# Requires: pacman -S mingw-w64-x86_64-gcc nasm make
#
# Output: internal/infra/audio/ffmpeg_libs/windows/amd64/*.a
#         internal/infra/audio/ffmpeg_libs/include/  (headers, shared across arches)
#
# Usage (from repo root in MSYS2 MINGW64 shell):
#   bash scripts/build-ffmpeg-windows-msys2.sh

set -euo pipefail

FFMPEG_VERSION="8.1"
FFMPEG_URL="https://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.gz"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUT_DIR="${REPO_ROOT}/internal/infra/audio/ffmpeg_libs/windows/amd64"
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/ffmpeg_libs/include"
BUILD_DIR="/tmp/ffmpeg-build-airmedy-windows-msys2"

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
    # Disable optional system libs
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
    # Native Windows build (no cross-compilation)
    --target-os=mingw32
    --arch=x86_64
    --enable-w32threads
    --disable-pthreads
)

LIBS=(libavcodec libavformat libavutil libswresample)

# Preflight checks
for cmd in gcc nasm make curl; do
    if ! command -v "$cmd" &>/dev/null; then
        echo "ERROR: '$cmd' not found. Run: pacman -S mingw-w64-x86_64-gcc nasm make" >&2
        exit 1
    fi
done

INSTALL_DIR="${BUILD_DIR}/install-amd64"
mkdir -p "${BUILD_DIR}" "${INSTALL_DIR}"

# Download source
if [[ ! -f "${BUILD_DIR}/ffmpeg.tar.gz" ]]; then
    echo "==> Downloading FFmpeg ${FFMPEG_VERSION}..."
    curl -L "${FFMPEG_URL}" -o "${BUILD_DIR}/ffmpeg.tar.gz"
fi
echo "==> Extracting..."
rm -rf "${BUILD_DIR}/src"
mkdir -p "${BUILD_DIR}/src"
tar -xzf "${BUILD_DIR}/ffmpeg.tar.gz" -C "${BUILD_DIR}/src" --strip-components=1

echo "==> Configuring FFmpeg ${FFMPEG_VERSION} for Windows AMD64 (native MSYS2)..."
cd "${BUILD_DIR}/src"
./configure \
    "${CONFIGURE_FLAGS[@]}" \
    --prefix="${INSTALL_DIR}"

echo "==> Building..."
make -j"$(nproc 2>/dev/null || echo 4)"
make install

echo "==> Copying libraries..."
mkdir -p "${OUT_DIR}"
for LIB in "${LIBS[@]}"; do
    cp "${INSTALL_DIR}/lib/${LIB}.a" "${OUT_DIR}/${LIB}.a"
    echo "    copied ${LIB}.a -> ffmpeg_libs/windows/amd64/"
done

# Copy headers (shared across arches)
if [[ ! -d "${INCLUDE_OUT}" ]]; then
    echo "==> Copying FFmpeg headers..."
    cp -R "${INSTALL_DIR}/include" "${INCLUDE_OUT}"
fi

cd "${REPO_ROOT}"
echo ""
echo "==> Done. Libraries:"
du -sh "${OUT_DIR}"/* 2>/dev/null || true
